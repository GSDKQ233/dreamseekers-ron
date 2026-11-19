// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.scout;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.unit.PinkChickenScout;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

/** Dedicated strict-direction channel. No target IDs/positions are ever broadcast. */
public final class ScoutMarkNetwork {
    private static final String VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            DreamseekersMod.id("scout_mark"), () -> VERSION, VERSION::equals, VERSION::equals);
    private static boolean registered;
    private ScoutMarkNetwork() { }

    public static void init() {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(Cast.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(Cast::encode).decoder(Cast::decode).consumerMainThread(Cast::handle).add();
        CHANNEL.messageBuilder(Sync.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(Sync::encode).decoder(Sync::decode).consumerMainThread(Sync::handle).add();
    }
    public static void request(Cast packet) { CHANNEL.sendToServer(packet); }
    static void send(ServerPlayer recipient, Sync packet) {
        if (recipient.connection != null) CHANNEL.send(PacketDistributor.PLAYER.with(() -> recipient), packet);
    }

    public record Cast(int scoutId, UUID scoutUuid, int targetId, UUID targetUuid, long revision) {
        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(scoutId); b.writeUUID(scoutUuid); b.writeVarInt(targetId); b.writeUUID(targetUuid); b.writeLong(revision);
        }
        public static Cast decode(FriendlyByteBuf b) { return new Cast(b.readVarInt(), b.readUUID(), b.readVarInt(), b.readUUID(), b.readLong()); }
        public void handle(Supplier<NetworkEvent.Context> context) {
            ServerPlayer sender = context.get().getSender();
            context.get().setPacketHandled(true);
            if (sender == null) return;
            ScoutMarkState.Result result = execute(sender);
            Entity entity = sender.serverLevel().getEntity(scoutId);
            if (entity instanceof PinkChickenScout scout && scout.getUUID().equals(scoutUuid)
                    && scout.getOwnerName().equals(sender.getGameProfile().getName()) && scout.scoutMark().allowFeedback())
                sender.displayClientMessage(Component.translatable("ability.dreamseekers.scout_mark.result." + result.name().toLowerCase(Locale.ROOT)), true);
        }
        /** Same path used by codec/ownership runtime tests; no caller-supplied owner name. */
        public ScoutMarkState.Result execute(ServerPlayer sender) {
            if (sender == null) return ScoutMarkState.Result.OWNER;
            Entity entity = sender.serverLevel().getEntity(scoutId);
            if (!(entity instanceof PinkChickenScout scout) || !scout.getUUID().equals(scoutUuid)) return ScoutMarkState.Result.INVALID;
            Entity target = sender.serverLevel().getEntity(targetId);
            if (target == null || !target.getUUID().equals(targetUuid)) return ScoutMarkState.Result.INVALID;
            // Successful casts sync once; failures use the normal 10-tick owner heartbeat.
            // Spam/replays cannot amplify outbound state packets or reveal state to another sender.
            return scout.scoutMark().tryCast(sender, target, revision);
        }
    }

    public record Sync(int scoutId, UUID scoutUuid, long revision, int cooldownTicks,
                       int targetId, @Nullable UUID targetUuid, int remainingTicks) {
        static Sync clear(PinkChickenScout scout) { return new Sync(scout.getId(), scout.getUUID(), -1, 0, -1, null, 0); }
        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(scoutId); b.writeUUID(scoutUuid); b.writeLong(revision); b.writeVarInt(cooldownTicks);
            b.writeVarInt(targetId); b.writeBoolean(targetUuid != null);
            if (targetUuid != null) b.writeUUID(targetUuid);
            b.writeVarInt(remainingTicks);
        }
        public static Sync decode(FriendlyByteBuf b) {
            int id = b.readVarInt(); UUID uuid = b.readUUID(); long revision = b.readLong(); int cooldown = b.readVarInt();
            int target = b.readVarInt(); UUID targetUuid = b.readBoolean() ? b.readUUID() : null;
            return new Sync(id, uuid, revision, cooldown, target, targetUuid, b.readVarInt());
        }
        public void handle(Supplier<NetworkEvent.Context> context) {
            context.get().setPacketHandled(true);
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ScoutMarkClient.accept(this));
        }
    }
}
