package com.solegendary.reignofnether.startpos;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.PacketHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class StartPosServerboundPacket {

    StartPosAction action;
    BlockPos blockPos;
    Faction faction;
    String playerName;

    public static void reservePos(BlockPos pos, Faction faction, String playerName) {
        PacketHandler.INSTANCE.sendToServer(new StartPosServerboundPacket(StartPosAction.RESERVE, pos, faction, playerName));
    }

    public static void unreservePos(BlockPos pos) {
        PacketHandler.INSTANCE.sendToServer(new StartPosServerboundPacket(StartPosAction.UNRESERVE, pos, Factions.NONE, ""));
    }

    public static void readyPlayer(String playerName) {
        PacketHandler.INSTANCE.sendToServer(new StartPosServerboundPacket(StartPosAction.PLAYER_READY, new BlockPos(0,0,0), Factions.NONE, playerName));
    }

    public static void unreadyPlayer(String playerName) {
        PacketHandler.INSTANCE.sendToServer(new StartPosServerboundPacket(StartPosAction.PLAYER_UNREADY, new BlockPos(0,0,0), Factions.NONE, playerName));
    }

    public static void enablePos(BlockPos pos) {
        PacketHandler.INSTANCE.sendToServer(new StartPosServerboundPacket(StartPosAction.ENABLE, pos, Factions.NONE, ""));
    }

    public static void disablePos(BlockPos pos) {
        PacketHandler.INSTANCE.sendToServer(new StartPosServerboundPacket(StartPosAction.DISABLE, pos, Factions.NONE, ""));
    }

    public StartPosServerboundPacket(StartPosAction action, BlockPos pos, Faction faction, String playerName) {
        this.action = action;
        this.blockPos = pos;
        this.faction = faction;
        this.playerName = playerName;
    }

    public StartPosServerboundPacket(FriendlyByteBuf buffer) {
        this.action = buffer.readEnum(StartPosAction.class);
        this.blockPos = buffer.readBlockPos();
        this.faction = com.solegendary.reignofnether.api.ReignOfNetherRegistries.FACTIONS.get(buffer.readResourceLocation());
        this.playerName = buffer.readUtf();
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeEnum(this.action);
        buffer.writeBlockPos(this.blockPos);
        buffer.writeResourceLocation(this.faction.key);
        buffer.writeUtf(this.playerName);
    }

    // server-side packet-consuming functions
    public boolean handle(Supplier<NetworkEvent.Context> ctx) {
        final var success = new AtomicBoolean(false);
        ctx.get().enqueueWork(() -> {

            ServerPlayer player = ctx.get().getSender();
            if (!com.solegendary.reignofnether.player.ServerPlayerRequest.isCurrent(ctx.get())
                    || player.serverLevel().dimension() != net.minecraft.world.level.Level.OVERWORLD) {
                ReignOfNether.LOGGER.warn("GameruleServerboundPacket: Sender was null");
                success.set(false);
                return;
            }
            else if ((action == StartPosAction.ENABLE || action == StartPosAction.DISABLE) &&
                    !player.hasPermissions(4)) {
                ReignOfNether.LOGGER.warn("GameruleServerboundPacket: Tried to process packet from " + player.getName() + " with insufficient permissions");
                success.set(false);
                return;
            }

            String actorName = player.getGameProfile().getName();
            boolean management = action == StartPosAction.ENABLE || action == StartPosAction.DISABLE;
            if (!management && (com.solegendary.reignofnether.player.PlayerServerEvents.rtsLocked
                    || com.solegendary.reignofnether.player.PlayerServerEvents.isRTSPlayer(actorName)
                    || com.solegendary.reignofnether.gamemode.GameModeServerEvents.getGameMode(player.serverLevel())
                        != com.solegendary.reignofnether.gamemode.GameMode.CLASSIC)) return;
            if ((action == StartPosAction.RESERVE || action == StartPosAction.PLAYER_READY
                    || action == StartPosAction.PLAYER_UNREADY) && !actorName.equals(playerName)) return;
            switch (action) {
                case RESERVE -> {
                    if (StartPosServerEvents.isStartingGame() || faction == null
                            || (faction != Factions.NONE && faction != Factions.RANDOM && !Factions.PLAYABLE_FACTIONS.contains(faction.key))) return;
                    StartPos target = StartPosServerEvents.startPoses.stream()
                            .filter(p -> p.pos.equals(blockPos) && p.enabled).findFirst().orElse(null);
                    if (target == null || (!target.playerName.isBlank() && !target.playerName.equals(actorName))) return;
                    for (StartPos old : StartPosServerEvents.startPoses) {
                        if (old != target && old.playerName.equals(actorName)) {
                            old.reset(); StartPosClientboundPacket.unreservePos(old.pos);
                        }
                    }
                    target.reset(); target.faction=faction; target.playerName=actorName;
                    StartPosClientboundPacket.reservePos(blockPos,faction,actorName);
                    StartPosServerEvents.setPlayerReady(actorName,false);
                }
                case UNRESERVE -> {
                    if (StartPosServerEvents.isStartingGame()) return;
                    StartPos target = StartPosServerEvents.startPoses.stream()
                            .filter(p -> p.pos.equals(blockPos) && p.playerName.equals(actorName)).findFirst().orElse(null);
                    if (target == null) return;
                    target.reset(); StartPosClientboundPacket.unreservePos(blockPos);
                    StartPosServerEvents.setPlayerReady(actorName,false);
                }
                case PLAYER_READY, PLAYER_UNREADY -> {
                    if (StartPosServerEvents.startPoses.stream().noneMatch(p -> p.enabled
                            && p.playerName.equals(actorName) && p.faction!=Factions.NONE)) return;
                    // A participant may still withdraw readiness to cancel an in-progress countdown.
                    StartPosServerEvents.setPlayerReady(actorName,action==StartPosAction.PLAYER_READY);
                }
                case ENABLE -> StartPosServerEvents.setPosEnabled(blockPos,true);
                case DISABLE -> StartPosServerEvents.setPosEnabled(blockPos,false);
            }
            success.set(true);
        });
        ctx.get().setPacketHandled(true);
        return success.get();
    }
}