package com.solegendary.reignofnether.startpos;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.blocks.RTSStartBlock;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.ReadiedStart;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.rtsmap.RTSMapInfoServerEvents;
import com.solegendary.reignofnether.sounds.SoundAction;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import com.solegendary.reignofnether.faction.Factions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;

// manages start block and readied start (startRTSEveryone) actions

public class StartPosServerEvents {

    public static final int MAX_START_POSES = 16;

    public static ArrayList<StartPos> startPoses = new ArrayList<>();

    private static int ticksToStart = StartPosCountdown.DURATION;
    private static boolean startingGame = false;
    private static List<ReadiedStart.Seat> frozenSeats = List.of();
    // Observe actual post-event block state at END tick. A cancelled Forge event is not a seat edit.
    private static final Set<BlockPos> pendingChecks = new LinkedHashSet<>();
    private static final Set<BlockPos> pendingAdditions = new LinkedHashSet<>();

    private static int cullTicksMax = 100;
    private static int cullTicks = 0;

    public static boolean isStartingGame() {
        return startingGame;
    }

    public static void reset(ServerLevel serverLevel) {
        cancelStartGameCountdown(true);
        for (StartPos startPos : startPoses) {
            startPos.reset();
        }
        savePositions(serverLevel);
        broadcastSnapshot();
    }

    public static void setPlayerReady(String playerName, boolean ready) {
        boolean foundPlayer = false;
        boolean actualReady = false;
        for (StartPos startPos : startPoses) {
            if (startPos.enabled && !playerName.isBlank() && startPos.playerName.equals(playerName)) {
                startPos.ready = ready && startPos.faction != null && startPos.faction != Factions.NONE;
                foundPlayer = true;
                actualReady = startPos.ready;
            }
        }
        if (ready && !foundPlayer) return;
        recalculateCountdown(false);
        if (actualReady)
            StartPosClientboundPacket.readyPlayer(playerName);
        else
            StartPosClientboundPacket.unreadyPlayer(playerName);
    }

    public static void setPosEnabled(BlockPos pos, boolean enable) {
        if (startingGame)
            return;
        boolean changed = false;
        for (StartPos startPos : startPoses) {
            if (startPos.pos.equals(pos) && startPos.enabled != enable) {
                changed = true;
                startPos.enabled = enable;
                if (!startPos.enabled) {
                    startPos.playerName = "";
                    startPos.ready = false;
                    startPos.faction = Factions.NONE;
                }
            }
        }
        if (!changed) return;
        if (enable)
            StartPosClientboundPacket.enablePos(pos);
        else
            StartPosClientboundPacket.disablePos(pos);
        savePositions(PlayerServerEvents.serverLevel);
        recalculateCountdown(true);
    }

    private static boolean usePhysicalPositions(ServerLevel level) {
        // StartPosSaveData is an Overworld-only catalogue; map JSON (including disabled/unsupported
        // modes) owns its own roster. A decoration in another dimension is never a lobby marker.
        return level == level.getServer().overworld() && RTSMapInfoServerEvents.rtsMapInfo == null;
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent evt) {
        if (!(evt.getLevel() instanceof ServerLevel level) || !(evt.getPlacedBlock().getBlock() instanceof RTSStartBlock)) return;
        if (!usePhysicalPositions(level)) {
            if (level == level.getServer().overworld() && evt.getEntity() instanceof ServerPlayer player)
                player.sendSystemMessage(Component.translatable("startpos.reignofnether.rts_map_info_used"));
            return;
        }
        BlockPos pos = evt.getPos().immutable();
        boolean known = startPoses.stream().anyMatch(p -> p.pos.equals(pos));
        if (!known && !pendingAdditions.contains(pos) && startPoses.size() + pendingAdditions.size() >= MAX_START_POSES) {
            evt.setCanceled(true);
            if (evt.getEntity() instanceof ServerPlayer player)
                player.sendSystemMessage(Component.translatable("startpos.reignofnether.max_positions"));
            return;
        }
        if (!known) pendingAdditions.add(pos);
        pendingChecks.add(pos);
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent evt) {
        if (evt.getLevel() instanceof ServerLevel level && usePhysicalPositions(level)
                && evt.getState().getBlock() instanceof RTSStartBlock
                && startPoses.stream().anyMatch(p -> p.isFromStartBlock && p.pos.equals(evt.getPos())))
            pendingChecks.add(evt.getPos().immutable());
    }

    private static void applyBlockChanges(ServerLevel level) {
        if (pendingChecks.isEmpty()) return;
        var checks = List.copyOf(pendingChecks);
        pendingChecks.clear(); pendingAdditions.clear();
        if (!usePhysicalPositions(level)) return;
        boolean changed = false;
        for (BlockPos pos : checks) {
            var old = startPoses.stream().filter(p -> p.pos.equals(pos)).findFirst().orElse(null);
            if (old != null && !old.isFromStartBlock) continue;
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof RTSStartBlock marker) {
                int color = marker.getMapColor(state, level, pos, marker.defaultMapColor()).id;
                if (old != null && old.colorId == color) continue;
                if (old == null && startPoses.size() >= MAX_START_POSES) continue;
                if (old != null) startPoses.remove(old);
                startPoses.add(new StartPos(pos, color));
                changed = true;
            } else if (old != null) {
                startPoses.remove(old);
                changed = true;
            }
        }
        if (changed) rosterChanged(level);
    }

    private static void rosterChanged(ServerLevel level) {
        recalculateCountdown(true);
        savePositions(level);
        broadcastSnapshot();
    }

    private static void broadcastSnapshot() {
        try {
            PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new StartPosSnapshotPacket(startPoses,
                    startingGame ? ticksToStart : StartPosCountdown.INACTIVE));
        } catch (IllegalArgumentException unsupported) {
            ReignOfNether.LOGGER.warn("Cannot broadcast complete lobby snapshot: {}", unsupported.getMessage());
            PlayerServerEvents.sendMessageToAllPlayers("startpos.reignofnether.snapshot_unavailable");
        }
    }

    private static List<ReadiedStart.Seat> enabledSeats() {
        return startPoses.stream().filter(p -> p.enabled).map(ReadiedStart.Seat::new).toList();
    }

    /** A synchronous Forge callback may change the lobby during tentative unit publication. */
    public static boolean isPendingStart(List<ReadiedStart.Seat> seats) {
        return startingGame && frozenSeats.equals(seats) && frozenSeats.equals(enabledSeats());
    }

    private static void recalculateCountdown(boolean rosterChanged) {
        if (rosterChanged && startingGame) cancelStartGameCountdown(false);
        var seats = enabledSeats();
        if (!seats.isEmpty() && seats.stream().allMatch(s -> s.ready() && !s.owner().isBlank()
                && s.faction() != null && s.faction() != Factions.NONE)) startGameCountdown();
        else if (startingGame) cancelStartGameCountdown(false);
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (!(evt.getEntity() instanceof ServerPlayer player)) return;
        if (mapModeExceedsLimit())
            player.sendSystemMessage(Component.translatable("startpos.reignofnether.snapshot_unavailable"));
        StartPosSnapshotPacket snapshot;
        try {
            snapshot = new StartPosSnapshotPacket(startPoses, startingGame ? ticksToStart : StartPosCountdown.INACTIVE);
        } catch (IllegalArgumentException unsupportedRoster) {
            // Never truncate a custom/legacy roster or let a snapshot failure change server state.
            ReignOfNether.LOGGER.warn("Cannot send a complete lobby snapshot: {}", unsupportedRoster.getMessage());
            player.sendSystemMessage(Component.translatable("startpos.reignofnether.snapshot_unavailable"));
            return;
        }
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), snapshot);
    }

    private static void cullInvalidPoses(ServerLevel serverLevel) {
        if (!usePhysicalPositions(serverLevel)) return;
        if (startPoses.removeIf(sp -> sp.isFromStartBlock && serverLevel.hasChunkAt(sp.pos)
                && !(serverLevel.getBlockState(sp.pos).getBlock() instanceof RTSStartBlock))) {
            rosterChanged(serverLevel);
        }
    }

    public static void startGameCountdown() {
        if (!startingGame) {
            frozenSeats = enabledSeats();
            if (frozenSeats.isEmpty()) return;
            ticksToStart = StartPosCountdown.DURATION;
            startingGame = true;
            StartPosClientboundPacket.startGameCountdown();
        }
    }

    public static void cancelStartGameCountdown(boolean noMsg) {
        if (startingGame) {
            ticksToStart = StartPosCountdown.DURATION;
            startingGame = false;
            frozenSeats = List.of();
            StartPosClientboundPacket.cancelStartGameCountdown();
            if (!noMsg)
                PlayerServerEvents.sendMessageToAllPlayers("startpos.reignofnether.cancelled_start_game", true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;

        ServerLevel level = evt.getServer().overworld();
        applyBlockChanges(level);
        if (startingGame) {
            if (ticksToStart % 20 == 0) {
                int secondsLeft = ticksToStart / 20;
                if (secondsLeft > 0) {
                    PlayerServerEvents.sendMessageToAllPlayersNoNewlines("startpos.reignofnether.starting_game", false, secondsLeft);
                    SoundClientboundPacket.playSoundForAllPlayers(SoundAction.CHAT);
                } else {
                    var failure = frozenSeats.equals(enabledSeats()) ? ReadiedStart.tryStart(level, frozenSeats)
                            : new ReadiedStart.Failure("?", "startpos.reignofnether.preflight_position");
                    if (failure != null) {
                        cancelStartGameCountdown(true);
                        PlayerServerEvents.sendMessageToAllPlayers("startpos.reignofnether.preflight_failed", true,
                                failure.player(), Component.translatable(failure.reasonKey()));
                        broadcastSnapshot(); // keep reservations and enabled settings; a fresh ready click can retry
                        return;
                    }
                    PlayerServerEvents.setRTSLock(true, true);
                    reset(level);
                    PlayerServerEvents.sendMessageToAllPlayers("startpos.reignofnether.started_game", true);
                    SoundClientboundPacket.playSoundForAllPlayers(SoundAction.ALLY);
                    return;
                }
            }
            if (ticksToStart >= 0)
                ticksToStart -= 1;
        }
        else if (!(PlayerServerEvents.isGameActive())) {
            cullTicks += 1;
            if (cullTicks >= cullTicksMax) {
                cullTicks = 0;
                cullInvalidPoses(evt.getServer().getLevel(Level.OVERWORLD));
            }
        }
    }

    private static boolean mapModeExceedsLimit() {
        var info = RTSMapInfoServerEvents.rtsMapInfo;
        return info != null && info.modeExceedsLobbyLimit(info.getDefaultMode());
    }

    public static void savePositions(ServerLevel serverLevel) {
        // A map with an unsupported active mode must not overwrite an older block-based save.
        if (serverLevel == null || !usePhysicalPositions(serverLevel)
                || startPoses.stream().anyMatch(sp -> !sp.isFromStartBlock)) return;
        StartPosSaveData data = StartPosSaveData.getInstance(serverLevel);
        if (data.startPoses.size() > StartPosSnapshotPacket.MAX_SEATS) return;
        data.startPoses.clear();
        data.startPoses.addAll(startPoses);
        data.setDirty();
        serverLevel.getDataStorage().save();
    }

    @SubscribeEvent
    public static void loadPositions(ServerStartedEvent evt) {
        ServerLevel level = evt.getServer().getLevel(Level.OVERWORLD);

        // rtsMapInfo is read in RTSMapInfoServerEvents.loadInfo
        if (level != null) {
            // A fresh world in the same JVM cannot inherit the previous roster or live clock.
            startPoses.clear();
            pendingAdditions.clear(); pendingChecks.clear(); frozenSeats = List.of(); cullTicks = 0;
            startingGame = false;
            ticksToStart = StartPosCountdown.DURATION;
            if (RTSMapInfoServerEvents.rtsMapInfo == null) {
                StartPosSaveData startPosData = StartPosSaveData.getInstance(level);
                startPoses.addAll(startPosData.startPoses);
                if (startPoses.size() > StartPosSnapshotPacket.MAX_SEATS)
                    ReignOfNether.LOGGER.warn("Saved lobby has more than {} seats; complete login snapshots are unavailable and the saved roster will not be overwritten.", StartPosSnapshotPacket.MAX_SEATS);
                ReignOfNether.LOGGER.info("loaded " + startPoses.size() + " start positions in serverevents from save");
            } else if (mapModeExceedsLimit()) {
                ReignOfNether.LOGGER.warn("Map lobby mode exceeds {} seats and was not loaded; source files were not changed.", StartPosSnapshotPacket.MAX_SEATS);
            } else {
                loadPositionsFromMapInfo();
                ReignOfNether.LOGGER.info("loaded " + startPoses.size() + " start positions from json file");
            }
        }
    }

    public static void loadPositionsFromMapInfo() {
        if (!RTSMapInfoServerEvents.usingMapInfoStartPositions())
            return;
        int playerColorIndex = 0;
        cancelStartGameCountdown(true);
        pendingChecks.clear(); pendingAdditions.clear();
        startPoses.clear();
        for (List<BlockPos> teamStartPoses : RTSMapInfoServerEvents.rtsMapInfo.getTeams()) {
            for (BlockPos startBlockPos : teamStartPoses) {
                StartPos startPos = new StartPos(startBlockPos, PlayerColors.colors[playerColorIndex].hexCode);
                startPos.isFromStartBlock = false;
                startPoses.add(startPos);
            }
            playerColorIndex += 1;
            if (playerColorIndex >= PlayerColors.colors.length)
                playerColorIndex = 0;
        }
        broadcastSnapshot();
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent evt) {
        boolean shouldStopCountdown = false;
        for (StartPos startPos : startPoses) {
            if (evt.getEntity() instanceof ServerPlayer player &&
                    startPos.playerName.equals(player.getName().getString())) {
                StartPosClientboundPacket.unreservePos(startPos.pos);
                startPos.reset();
                shouldStopCountdown = true;
            }
        }
        if (shouldStopCountdown && startingGame) {
            cancelStartGameCountdown(false);
        }
    }
}
