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

    private static void markerSources(GameTestHelper h, ServerPlayer actor) {
        var level = h.getLevel();
        var other = level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        h.assertTrue(other != null, "fixture needs the independent Nether level");
        var roster = StartPosServerEvents.startPoses;
        var pos = h.absolutePos(new BlockPos(3, 2, 3));
        var previousState = level.getBlockState(pos);
        var blue = com.solegendary.reignofnether.registrars.BlockRegistrar.RTS_START_BLOCK_BLUE.get().defaultBlockState();
        var stone = net.minecraft.world.level.block.Blocks.STONE.defaultBlockState();
        var tick = new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, level.getServer());
        try {
            level.setBlock(pos, blue, 2);
            roster.clear();
            var physical = new StartPos(pos, -17); // makes an unintended re-read of this marker detectable
            roster.add(physical);
            StartPosServerEvents.onBlockBreak(new net.minecraftforge.event.level.BlockEvent.BreakEvent(other, pos, blue,
                    net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(other)));
            StartPosServerEvents.onBlockBreak(new net.minecraftforge.event.level.BlockEvent.BreakEvent(level, pos, stone, actor));
            StartPosServerEvents.onServerTick(tick);
            h.assertTrue(roster.size() == 1 && roster.get(0) == physical, "other dimension and non-marker break do not edit the roster");
            physical.isFromStartBlock = false;
            StartPosServerEvents.onBlockBreak(new net.minecraftforge.event.level.BlockEvent.BreakEvent(level, pos, blue, actor));
            level.setBlock(pos, stone, 2); StartPosServerEvents.onServerTick(tick);
            h.assertTrue(roster.size() == 1 && roster.get(0) == physical, "virtual seat survives a physical break at the same block");
            physical.isFromStartBlock = true;
            level.setBlock(pos, blue, 2);
            StartPosServerEvents.onBlockBreak(new net.minecraftforge.event.level.BlockEvent.BreakEvent(level, pos, blue, actor));
            level.setBlock(pos, stone, 2); StartPosServerEvents.onServerTick(tick);
            h.assertTrue(roster.isEmpty(), "confirmed actual Overworld marker break removes its physical seat");
        } finally {
            level.setBlock(pos, previousState, 2);
        }
    }

    /** A border changes between ready and GO. Neither member receives a seat, building or account;
     * restore the border, retry the same reservations, then clear a lobby inside and outside countdown. */
    private static void batchBoundaryAndReset(GameTestHelper h, String a, String b, Peer peer) {
        var level = h.getLevel();
        var roster = StartPosServerEvents.startPoses;
        var border = level.getWorldBorder();
        double size = border.getSize(), x = border.getCenterX(), z = border.getCenterZ();
        var buildings = com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings();
        var accounts = com.solegendary.reignofnether.resources.ResourcesServerEvents.resourcesList;
        var active = com.solegendary.reignofnether.player.PlayerServerEvents.rtsPlayers;
        h.assertTrue(active.isEmpty() && !com.solegendary.reignofnether.player.PlayerServerEvents.rtsLocked,
                "dedicated lobby regression starts in an empty, open match");
        var pos = h.absolutePos(new BlockPos(8, 2, 8));
        var one = new StartPos(pos, Factions.VILLAGERS, a, 5);
        var two = new StartPos(pos.east(64), Factions.PIGLINS, b, 8);
        one.isFromStartBlock = false; two.isFromStartBlock = false;
        var disabled = new StartPos(pos.south(16), 1); disabled.enabled = false; disabled.isFromStartBlock = false;
        roster.clear(); roster.addAll(List.of(one,two,disabled));
        var tick = new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, level.getServer());
        try {
            border.setCenter(pos.getX(), pos.getZ()); border.setSize(512);
            toggleReady(h, a, true); toggleReady(h, b, true);
            h.assertTrue(StartPosServerEvents.isStartingGame(), "both peers ready before border changes");
            border.setSize(64);
            for (int i = 0; i <= StartPosCountdown.DURATION; i++) StartPosServerEvents.onServerTick(tick);
            h.assertTrue(!StartPosServerEvents.isStartingGame() && active.isEmpty()
                    && !com.solegendary.reignofnether.player.PlayerServerEvents.rtsLocked
                    && accounts.stream().noneMatch(acc -> a.equals(acc.ownerName) || b.equals(acc.ownerName))
                    && buildings.stream().noneMatch(bp -> a.equals(bp.ownerName) || b.equals(bp.ownerName))
                    && com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits().stream().noneMatch(e ->
                    e instanceof com.solegendary.reignofnether.unit.interfaces.Unit u && (a.equals(u.getOwnerName()) || b.equals(u.getOwnerName())))
                    && one.ready && two.ready && a.equals(one.playerName) && b.equals(two.playerName),
                    "late boundary rejection is atomic and preserves both reservations");

            border.setSize(512);
            toggleReady(h, a, false); toggleReady(h, a, true);
            for (int i = 0; i <= StartPosCountdown.DURATION; i++) StartPosServerEvents.onServerTick(tick);
            h.assertTrue(active.size() == 2 && com.solegendary.reignofnether.player.PlayerServerEvents.rtsLocked
                    && buildings.stream().filter(bp -> a.equals(bp.ownerName) || b.equals(bp.ownerName)).count() == 2
                    && accounts.stream().filter(acc -> a.equals(acc.ownerName) || b.equals(acc.ownerName)).count() == 2,
                    "valid retry commits exactly two seats, capitols and accounts before locking");
            var workers = com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits().stream()
                    .filter(entity -> entity instanceof com.solegendary.reignofnether.unit.interfaces.WorkerUnit
                            && entity instanceof com.solegendary.reignofnether.unit.interfaces.Unit u
                            && (a.equals(u.getOwnerName()) || b.equals(u.getOwnerName()))).toList();
            h.assertTrue(workers.size() == 10 && workers.stream().allMatch(entity -> {
                var worker = (com.solegendary.reignofnether.unit.interfaces.WorkerUnit)entity;
                var cap = worker.getBuildRepairGoal().getBuildingTarget();
                return cap != null && buildings.contains(cap) && cap.ownerName.equals(
                        ((com.solegendary.reignofnether.unit.interfaces.Unit)entity).getOwnerName());
            }), "every staged worker has its own committed capitol order, including remote starting areas");
            // Clear our match with the actual normal command path in this dedicated fixture world.
            com.solegendary.reignofnether.player.PlayerServerEvents.resetRTS(false);
            for (boolean countdown : List.of(false, true)) {
                one.playerName = a; one.faction = Factions.VILLAGERS;
                two.playerName = b; two.faction = Factions.PIGLINS;
                toggleReady(h, a, true); if (countdown) toggleReady(h, b, true);
                h.assertTrue(StartPosServerEvents.isStartingGame() == countdown, "reset precondition");
                com.solegendary.reignofnether.player.PlayerServerEvents.resetRTS(false);
                output(peer); // discard the live reset, then inspect a fresh login image
                var seats = new ArrayList<StartPos>();
                var snapshot = login(h, b, peer); snapshot.replaceSeats(seats);
                h.assertTrue(seats.size() == 3 && !seats.get(2).enabled && snapshot.countdownTicks() == -1
                        && seats.stream().allMatch(s -> s.playerName.isEmpty() && !s.ready && s.faction == Factions.NONE),
                        "reset retains positions and disabled settings, but not occupants or clock");
            }
        } finally {
            border.setCenter(x, z); border.setSize(size);
            // In a failing run, do not leak this fixture's match to the next batch.
            if (active.stream().anyMatch(p -> p.name.equals(a) || p.name.equals(b)))
                com.solegendary.reignofnether.player.PlayerServerEvents.resetRTS(false);
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
                markerSources(h, player(h, aName));
                batchBoundaryAndReset(h, aName, bName, b);
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
