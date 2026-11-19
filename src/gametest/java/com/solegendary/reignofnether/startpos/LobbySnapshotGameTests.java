package com.solegendary.reignofnether.startpos;

import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.research.economy.ResearchEconomyFixtures.Peer;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.NetworkDirection;

import java.util.ArrayList;
import java.util.List;

/** Server login handler, real registered packet channel, two embedded peers; not graphical clients. */
@GameTestHolder("api65_repair2_lobby")
@PrefixGameTestTemplate(false)
public final class LobbySnapshotGameTests {
    private static final String NS = "api65_repair2_lobby";
    private static final ResourceLocation CHANNEL = ResourceLocation.parse("reignofnether:main");

    private record Output(List<StartPosSnapshotPacket> snapshots, List<StartPosClientboundPacket> deltas) {}

    private static int id(Object packet) {
        var b = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PacketHandler.INSTANCE.encodeMessage(packet, b);
            return b.readVarInt();
        } finally { b.release(); }
    }

    private static Output output(Peer peer) {
        int snapshotId = id(new StartPosSnapshotPacket(List.of(), -1));
        int deltaId = id(new StartPosClientboundPacket(StartPosAction.RESET, BlockPos.ZERO, Factions.NONE, "", 0));
        var snapshots = new ArrayList<StartPosSnapshotPacket>();
        var deltas = new ArrayList<StartPosClientboundPacket>();
        for (Object packet : peer.drain()) {
            if (!(packet instanceof ClientboundCustomPayloadPacket payload) || !CHANNEL.equals(payload.getIdentifier())) continue;
            var b = payload.getData();
            try {
                int type = b.readVarInt();
                if (type == snapshotId) snapshots.add(new StartPosSnapshotPacket(b));
                else if (type == deltaId) deltas.add(new StartPosClientboundPacket(b));
                else continue;
                if (b.isReadable()) throw new AssertionError("lobby codec left trailing bytes");
            } finally { b.release(); }
        }
        return new Output(snapshots, deltas);
    }

    private static ServerPlayer player(GameTestHelper h, String name) {
        return h.getLevel().getServer().getPlayerList().getPlayerByName(name);
    }

    private static StartPosSnapshotPacket login(GameTestHelper h, String name, Peer peer) {
        StartPosServerEvents.onPlayerJoin(new PlayerEvent.PlayerLoggedInEvent(player(h, name)));
        var packets = output(peer);
        h.assertTrue(packets.snapshots().size() == 1 && packets.deltas().isEmpty(), "one full targeted image, no synthetic live changes");
        return packets.snapshots().get(0);
    }

    private static void toggleReady(GameTestHelper h, String name, boolean ready) {
        // The normal ready-button request through the real channel and this peer's registered listener.
        var request = new StartPosServerboundPacket(ready ? StartPosAction.PLAYER_READY : StartPosAction.PLAYER_UNREADY,
                BlockPos.ZERO, Factions.NONE, name);
        var payload = (ServerboundCustomPayloadPacket) PacketHandler.INSTANCE.toVanillaPacket(request, NetworkDirection.PLAY_TO_SERVER);
        payload.handle(player(h, name).connection);
    }

    private static void noLobbyPackets(GameTestHelper h, Peer peer) {
        var packets = output(peer);
        h.assertTrue(packets.snapshots().isEmpty() && packets.deltas().isEmpty(), "existing peer must not receive another player's join traffic");
    }

    private static void checkRoster(GameTestHelper h, List<StartPos> display, List<StartPos> authoritative) {
        h.assertTrue(display.size() == authoritative.size(), "full replacement drops stale seats");
        for (int i = 0; i < display.size(); i++) {
            var a = authoritative.get(i);
            var p = display.get(i);
            h.assertTrue(a != p && p.pos.equals(a.pos) && p.faction == a.faction && p.playerName.equals(a.playerName)
                    && p.colorId == a.colorId && p.enabled == a.enabled && p.ready == a.ready
                    && p.isFromStartBlock == a.isFromStartBlock, "ordered seat " + i + " agrees with the independent server object");
        }
    }

    @GameTest(templateNamespace=NS, template="ore_mine_test_arena", batch="repair2_ready_login")
    public static void readyPeerUnaffectedBySecondLogin(GameTestHelper h) throws Exception {
        String aName = "LobbyReadyA", bName = "LobbyJoinB";
        var previous = List.copyOf(StartPosServerEvents.startPoses);
        h.assertTrue(!StartPosServerEvents.isStartingGame(), "isolated lobby starts idle");
        try (var a = new Peer(h.getLevel(), aName)) {
            var roster = StartPosServerEvents.startPoses;
            roster.clear();
            var own = new StartPos(h.absolutePos(new BlockPos(1, 2, 1)), Factions.VILLAGERS, aName, 0x44aa66);
            own.isFromStartBlock = false;
            roster.add(own);
            roster.add(new StartPos(own.pos.east(), 3));
            var disabled = new StartPos(own.pos.south(), 5);
            disabled.enabled = false;
            roster.add(disabled);
            toggleReady(h, aName, true);
            output(a);
            var aDisplay = new ArrayList<StartPos>();
            login(h, aName, a).replaceSeats(aDisplay);
            try (var b = new Peer(h.getLevel(), bName)) {
                var image = login(h, bName, b);
                noLobbyPackets(h, a);
                var bDisplay = new ArrayList<>(List.of(new StartPos(BlockPos.ZERO, 1)));
                for (int i = 0; i < 2; i++) {
                    image.replaceSeats(bDisplay);
                    checkRoster(h, aDisplay, roster);
                    checkRoster(h, bDisplay, roster);
                }
                h.assertTrue(own.ready && image.countdownTicks() == -1, "A remains ready while another enabled seat is empty");
                toggleReady(h, aName, false);
                h.assertTrue(!own.ready, "A can withdraw after B joins");
                for (Peer peer : List.of(a, b)) {
                    var update = output(peer);
                    h.assertTrue(update.snapshots().isEmpty() && update.deltas().size() == 1
                            && update.deltas().get(0).action == StartPosAction.PLAYER_UNREADY
                            && update.deltas().get(0).playerName.equals(aName), "only the actual withdrawal is broadcast to both peers");
                }
            }
        } finally {
            StartPosServerEvents.cancelStartGameCountdown(true);
            StartPosServerEvents.startPoses.clear();
            StartPosServerEvents.startPoses.addAll(previous);
        }
        h.succeed();
    }

    @GameTest(templateNamespace=NS, template="ore_mine_test_arena", batch="repair2_countdown_rejoin")
    public static void observerReconnectsDuringCountdown(GameTestHelper h) throws Exception {
        String owner = "LobbyCountdown", observer = "LobbyWatch";
        var previous = List.copyOf(StartPosServerEvents.startPoses);
        h.assertTrue(!StartPosServerEvents.isStartingGame(), "isolated lobby starts idle");
        try (var a = new Peer(h.getLevel(), owner)) {
            var roster = StartPosServerEvents.startPoses;
            roster.clear();
            var own = new StartPos(h.absolutePos(new BlockPos(1, 2, 1)), Factions.PIGLINS, owner, 0x123456);
            roster.add(own);
            try (var firstConnection = new Peer(h.getLevel(), observer)) {
                var idle = login(h, observer, firstConnection);
                h.assertTrue(idle.countdownTicks() == -1, "first connection saw idle lobby");
                noLobbyPackets(h, a);
                toggleReady(h, owner, true);
                var ticks = new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, h.getLevel().getServer());
                for (int i = 0; i < 37; i++) StartPosServerEvents.onServerTick(ticks);
                StartPosServerEvents.onPlayerLeave(new PlayerEvent.PlayerLoggedOutEvent(player(h, observer)));
                output(a);
                output(firstConnection);
            }
            try (var secondConnection = new Peer(h.getLevel(), observer)) {
                var image = login(h, observer, secondConnection);
                var display = new ArrayList<StartPos>();
                image.replaceSeats(display);
                checkRoster(h, display, roster);
                h.assertTrue(own.ready && StartPosServerEvents.isStartingGame() && image.countdownTicks() == 63,
                        "observer reconnected at the actual server tick, not a fresh five seconds");
                noLobbyPackets(h, a);
            }
        } finally {
            StartPosServerEvents.cancelStartGameCountdown(true);
            StartPosServerEvents.startPoses.clear();
            StartPosServerEvents.startPoses.addAll(previous);
        }
        h.succeed();
    }
}
