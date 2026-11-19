package com.solegendary.reignofnether.startpos;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.blocks.RTSStartBlock;
import com.solegendary.reignofnether.player.PlayerColors;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.rtsmap.RTSMapInfoServerEvents;
import com.solegendary.reignofnether.sounds.SoundAction;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import com.solegendary.reignofnether.faction.Factions;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// manages start block and readied start (startRTSEveryone) actions

public class StartPosServerEvents {

    public static final int MAX_START_POSES = 16;

    public static ArrayList<StartPos> startPoses = new ArrayList<>();

    private static int ticksToStart = StartPosCountdown.DURATION;
    private static boolean startingGame = false;

    private static int cullTicksMax = 100;
    private static int cullTicks = 0;

    public static boolean isStartingGame() {
        return startingGame;
    }

    public static void reset(ServerLevel serverLevel) {
        for (StartPos startPos : startPoses) {
            startPos.reset();
        }
        savePositions(serverLevel);
    }

    public static void setPlayerReady(String playerName, boolean ready) {
        boolean shouldStartGame = startPoses.stream().anyMatch(p -> p.enabled);
        boolean foundPlayer = false;
        for (StartPos startPos : startPoses) {
            if (startPos.enabled && !playerName.isBlank() && startPos.playerName.equals(playerName)) {
                startPos.ready = ready;
                foundPlayer = true;
            }
            if (startPos.enabled && (!startPos.ready || startPos.playerName.isBlank()))
                shouldStartGame = false;
        }
        if (ready && !foundPlayer) return;
        if (shouldStartGame) {
            startGameCountdown();
        } else if (startingGame) {
            cancelStartGameCountdown(false);
        }
        if (ready)
            StartPosClientboundPacket.readyPlayer(playerName);
        else
            StartPosClientboundPacket.unreadyPlayer(playerName);
    }

    public static void setPosEnabled(BlockPos pos, boolean enable) {
        if (startingGame)
            return;
        for (StartPos startPos : startPoses) {
            if (startPos.pos.equals(pos)) {
                startPos.enabled = enable;
                if (!startPos.enabled) {
                    startPos.playerName = "";
                    startPos.ready = false;
                    startPos.faction = Factions.NONE;
                }
            }
        }
        if (enable)
            StartPosClientboundPacket.enablePos(pos);
        else
            StartPosClientboundPacket.disablePos(pos);
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent evt) {
        if (evt.getPlacedBlock().getBlock() instanceof RTSStartBlock rtsStartBlock) {
            if (RTSMapInfoServerEvents.usingMapInfoStartPositions() && evt.getLevel().getServer() != null) {
                evt.getLevel().getServer().sendSystemMessage(Component.translatable("startpos.reignofnether.max_positions"));
            }
            if (startPoses.size() < MAX_START_POSES) {
                StartPos newStartPos = new StartPos(evt.getPos(),
                        rtsStartBlock.getMapColor(rtsStartBlock.defaultBlockState(),
                            evt.getLevel(), evt.getPos(),
                            rtsStartBlock.defaultMapColor()).id
                );
                startPoses.add(newStartPos);
                StartPosClientboundPacket.addPos(newStartPos);
            } else {
                evt.setCanceled(true);
                for (Player player : PlayerServerEvents.players)
                    if (player.distanceToSqr(Vec3.atCenterOf(evt.getPos())) < 100)
                        player.sendSystemMessage(Component.translatable("startpos.reignofnether.max_positions"));
            }
            if (evt.getLevel() instanceof ServerLevel serverLevel)
                savePositions(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent evt) {
        if (startPoses.removeIf(sp -> {
            if (sp.pos.equals(evt.getPos())) {
                StartPosClientboundPacket.removePos(evt.getPos());
                return true;
            }
            return false;
        }) && (evt.getLevel() instanceof ServerLevel serverLevel)) {
            savePositions(serverLevel);
        }
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
        if (startPoses.removeIf(sp -> {
            if (sp.isFromStartBlock && !(serverLevel.getBlockState(sp.pos).getBlock() instanceof RTSStartBlock)) {
                StartPosClientboundPacket.removePos(sp.pos);
                return true;
            }
            return false;
        })) {
            savePositions(serverLevel);
        }
    }

    public static void startGameCountdown() {
        if (!startingGame) {
            ticksToStart = StartPosCountdown.DURATION;
            startingGame = true;
            StartPosClientboundPacket.startGameCountdown();
        }
    }

    public static void cancelStartGameCountdown(boolean noMsg) {
        if (startingGame) {
            ticksToStart = StartPosCountdown.DURATION;
            startingGame = false;
            StartPosClientboundPacket.cancelStartGameCountdown();
            if (!noMsg)
                PlayerServerEvents.sendMessageToAllPlayers("startpos.reignofnether.cancelled_start_game", true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END)
            return;

        if (startingGame) {
            if (ticksToStart % 20 == 0) {
                int secondsLeft = ticksToStart / 20;
                if (secondsLeft > 0) {
                    PlayerServerEvents.sendMessageToAllPlayersNoNewlines("startpos.reignofnether.starting_game", false, secondsLeft);
                    SoundClientboundPacket.playSoundForAllPlayers(SoundAction.CHAT);
                } else {
                    PlayerServerEvents.sendMessageToAllPlayers("startpos.reignofnether.started_game", true);
                    SoundClientboundPacket.playSoundForAllPlayers(SoundAction.ALLY);
                    for (ServerPlayer serverPlayer : PlayerServerEvents.players) {
                        for (StartPos startPos : startPoses) {
                            if (startPos.playerName.equals(serverPlayer.getName().getString()) && startPos.faction != Factions.NONE) {
                                PlayerServerEvents.startRTS(
                                        serverPlayer.getId(),
                                        new Vec3(startPos.pos.getX(), startPos.pos.getY(), startPos.pos.getZ()),
                                        startPos.faction,
                                        startPos.colorId
                                );
                                break;
                            }
                        }
                    }
                    // ally all players who start at the same color of start block
                    for (int i = 0; i < PlayerServerEvents.rtsPlayers.size(); i++) {
                        RTSPlayer p1 = PlayerServerEvents.rtsPlayers.get(i);
                        for (int j = i + 1; j < PlayerServerEvents.rtsPlayers.size(); j++) {
                            RTSPlayer p2 = PlayerServerEvents.rtsPlayers.get(j);
                            if (p1.startPosColorId == p2.startPosColorId) {
                                AlliancesServerEvents.addAlliance(p1.name, p2.name);
                            }
                        }
                    }
                    PlayerServerEvents.setRTSLock(true, true);
                    StartPosServerEvents.reset(evt.getServer().getLevel(Level.OVERWORLD));
                    StartPosClientboundPacket.reset();
                    startingGame = false;
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
        if (mapModeExceedsLimit()) return;
        if (!RTSMapInfoServerEvents.usingMapInfoStartPositions()) {
            StartPosSaveData startPosData = StartPosSaveData.getInstance(serverLevel);
            if (startPosData.startPoses.size() > StartPosSnapshotPacket.MAX_SEATS) return;
            startPosData.startPoses.clear();
            startPosData.startPoses.addAll(startPoses);
            startPosData.save();
            serverLevel.getDataStorage().save();
            //ReignOfNether.LOGGER.info("saved " + startPoses.size() + " start positions in serverevents");
        }
    }

    @SubscribeEvent
    public static void loadPositions(ServerStartedEvent evt) {
        ServerLevel level = evt.getServer().getLevel(Level.OVERWORLD);

        // rtsMapInfo is read in RTSMapInfoServerEvents.loadInfo
        if (level != null) {
            // A fresh world in the same JVM cannot inherit the previous roster or live clock.
            startPoses.clear();
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
        List<StartPos> oldStartPoses = new ArrayList<>(startPoses);
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
        Set<BlockPos> newBlockPoses = startPoses.stream()
                .map(sp -> sp.pos)
                .collect(Collectors.toSet());
        for (StartPos oldStartPos : oldStartPoses)
            if (!newBlockPoses.contains(oldStartPos.pos))
                StartPosClientboundPacket.removePos(oldStartPos.pos);
        for (StartPos startPos : startPoses)
            StartPosClientboundPacket.addPos(startPos);
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
