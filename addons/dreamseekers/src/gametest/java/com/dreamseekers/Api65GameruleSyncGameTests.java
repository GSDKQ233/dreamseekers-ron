package com.dreamseekers;

import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.gamerules.GameruleAction;
import com.solegendary.reignofnether.gamerules.GameruleClientboundPacket;
import com.solegendary.reignofnether.gamerules.GameruleServerEvents;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.NetworkDirection;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Real encoded S2C output from existing handlers, not a real socket/client login. API64-compatible. */
@GameTestHolder("api65_addon")
@PrefixGameTestTemplate(false)
public final class Api65GameruleSyncGameTests {
    @GameTest(template="unit_isolation_arena", batch="api65_addon", timeoutTicks=100)
    public static void loginSynchronizesIndependentAnimalAndAllFourDropModes(GameTestHelper h) throws Exception {
        var server = h.getLevel().getServer();
        var rules = server.getGameRules();
        int oldAnimal = rules.getInt(GameRuleRegistrar.ANIMAL_SPAWN_Y_DIFF);
        int oldDrops = rules.getInt(GameRuleRegistrar.RANDOM_ITEM_DROPS);
        try (var recipient = new Recipient(h, "Api65RuleJoin")) {
            rules.getRule(GameRuleRegistrar.ANIMAL_SPAWN_Y_DIFF).set(17, server);
            for (int mode=0; mode<=3; mode++) {
                rules.getRule(GameRuleRegistrar.RANDOM_ITEM_DROPS).set(mode, server);
                recipient.wire.values.clear();
                GameruleServerEvents.onPlayerJoin(new PlayerEvent.PlayerLoggedInEvent(recipient.player));
                h.assertTrue(Long.valueOf(17).equals(recipient.wire.values.get(GameruleAction.SET_ANIMAL_SPAWN_Y_DIFF)),
                        "login animal height stays 17 for drop mode " + mode);
                h.assertTrue(Long.valueOf(mode).equals(recipient.wire.values.get(GameruleAction.SET_RANDOM_ITEM_DROPS)),
                        "login emits independent actual random-item rule " + mode);
            }
        } finally {
            rules.getRule(GameRuleRegistrar.ANIMAL_SPAWN_Y_DIFF).set(oldAnimal, server);
            rules.getRule(GameRuleRegistrar.RANDOM_ITEM_DROPS).set(oldDrops, server);
        }
        h.succeed();
    }

    @GameTest(template="unit_isolation_arena", batch="api65_addon", timeoutTicks=100)
    public static void registeredGameruleCommandPublishesLegalModeThree(GameTestHelper h) throws Exception {
        var server = h.getLevel().getServer();
        int old = server.getGameRules().getInt(GameRuleRegistrar.RANDOM_ITEM_DROPS);
        try (var recipient = new Recipient(h, "Api65RuleCmd")) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "gamerule randomItemDrops 3");
            h.assertTrue(server.getGameRules().getInt(GameRuleRegistrar.RANDOM_ITEM_DROPS) == 3, "real command accepts mode 3");
            h.assertTrue(Long.valueOf(3).equals(recipient.wire.values.get(GameruleAction.SET_RANDOM_ITEM_DROPS)),
                    "registered CommandEvent path sends 3, not 2");
        } finally { server.getGameRules().getRule(GameRuleRegistrar.RANDOM_ITEM_DROPS).set(old, server); }
        h.succeed();
    }

    private static final class Recipient implements AutoCloseable {
        final FakePlayer player;
        final Wire wire;
        final List<ServerPlayer> players;
        final Map<UUID,ServerPlayer> byId;
        @SuppressWarnings("unchecked") Recipient(GameTestHelper h, String name) throws ReflectiveOperationException {
            player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
            wire = new Wire();
            player.connection = new ServerGamePacketListenerImpl(h.getLevel().getServer(), wire, player) {
                @Override public void tick() { }
                @Override public void send(Packet<?> packet) { wire.send(packet); }
                @Override public void send(Packet<?> packet, PacketSendListener listener) { wire.send(packet, listener); }
            };
            var list = h.getLevel().getServer().getPlayerList();
            var all = PlayerList.class.getDeclaredField("players"); all.setAccessible(true);
            var ids = PlayerList.class.getDeclaredField("playersByUUID"); ids.setAccessible(true);
            players = (List<ServerPlayer>)all.get(list); byId = (Map<UUID,ServerPlayer>)ids.get(list);
            players.add(player); byId.put(player.getUUID(), player);
        }
        @Override public void close() { players.remove(player); byId.remove(player.getUUID(), player); }
    }

    private static final class Wire extends Connection {
        final Map<GameruleAction,Long> values = new EnumMap<>(GameruleAction.class);
        final net.minecraft.resources.ResourceLocation channel;
        final int discriminator;
        Wire() {
            super(PacketFlow.SERVERBOUND);
            var probe = (ClientboundCustomPayloadPacket)PacketHandler.INSTANCE.toVanillaPacket(
                    new GameruleClientboundPacket(GameruleAction.SET_RANDOM_ITEM_DROPS, "", 3L), NetworkDirection.PLAY_TO_CLIENT);
            channel = probe.getIdentifier();
            FriendlyByteBuf bytes = new FriendlyByteBuf(probe.getData().copy());
            try { discriminator = bytes.readVarInt(); } finally { bytes.release(); }
        }
        @Override public void tick() { }
        @Override public void send(Packet<?> packet) { record(packet); }
        @Override public void send(Packet<?> packet, PacketSendListener listener) { record(packet); }
        private void record(Packet<?> packet) {
            if (!(packet instanceof ClientboundCustomPayloadPacket payload) || !channel.equals(payload.getIdentifier())) return;
            FriendlyByteBuf bytes = new FriendlyByteBuf(payload.getData().copy());
            try {
                if (bytes.readVarInt() != discriminator) return;
                GameruleAction action = bytes.readEnum(GameruleAction.class);
                bytes.readUtf();
                values.put(action, bytes.readLong());
                if (bytes.readableBytes() != 0) throw new IllegalStateException("Trailing gamerule payload");
            } finally { bytes.release(); }
        }
    }
}
