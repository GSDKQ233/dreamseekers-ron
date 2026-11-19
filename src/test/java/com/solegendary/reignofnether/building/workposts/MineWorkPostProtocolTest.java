package com.solegendary.reignofnether.building.workposts;

import com.solegendary.reignofnether.research.economy.EconomyActionClient;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MineWorkPostProtocolTest {
    private final UUID stream = UUID.randomUUID(), session = UUID.randomUUID(), epoch = UUID.randomUUID(), view = UUID.randomUUID(), instance = UUID.randomUUID();
    private MineWorkPostStatusPacket packet(int capacity, List<MineWorkPostStatusPacket.Worker> workers) {
        return new MineWorkPostStatusPacket(stream, 10, session, epoch, "Owner", ResourceLocation.parse("minecraft:overworld"),
                new BlockPos(-12, 70, 24), view, instance, 3, capacity, true, workers, "ready", 0);
    }
    @Test void roundTripContainsExactServerIdentityAndRealWorkerIdentity() {
        var original = packet(6, List.of(new MineWorkPostStatusPacket.Worker(21, UUID.randomUUID(), 0, true),
                new MineWorkPostStatusPacket.Worker(22, UUID.randomUUID(), 5, false)));
        var b = new FriendlyByteBuf(Unpooled.buffer());
        try { original.encode(b); assertEquals(original, new MineWorkPostStatusPacket(b)); assertFalse(b.isReadable()); }
        finally { b.release(); }
        assertTrue(original.validOccupancy()); assertEquals(1, original.activeCount());
    }
    @Test void requestContainsNoOccupancyOrPaymentClaims() {
        var names = Arrays.stream(MineWorkPostServerboundPacket.class.getRecordComponents()).map(c -> c.getName()).toList();
        assertEquals(List.of("action", "dimension", "origin", "view", "instance", "capture", "epoch", "session", "sequence"), names);
        for (var action : MineWorkPostServerboundPacket.Action.values()) {
            var packet = new MineWorkPostServerboundPacket(action, ResourceLocation.parse("minecraft:overworld"), BlockPos.ZERO,
                    view, instance, 5, epoch, session, 12);
            var b = new FriendlyByteBuf(Unpooled.buffer());
            try { packet.encode(b); assertEquals(packet, new MineWorkPostServerboundPacket(b)); assertFalse(b.isReadable()); }
            finally { b.release(); }
        }
    }
    @Test void impossibleOrDuplicatedOccupancyIsRejected() {
        var uuid = UUID.randomUUID(); var a = new MineWorkPostStatusPacket.Worker(1, uuid, 0, true);
        assertFalse(packet(3, List.of(a)).validOccupancy());
        assertFalse(packet(2, List.of(new MineWorkPostStatusPacket.Worker(1, uuid, 2, true))).validOccupancy());
        assertFalse(packet(6, List.of(a, new MineWorkPostStatusPacket.Worker(2, UUID.randomUUID(), 0, false))).validOccupancy());
        assertFalse(packet(6, List.of(a, new MineWorkPostStatusPacket.Worker(1, UUID.randomUUID(), 1, false))).validOccupancy());
        assertFalse(packet(6, List.of(a, new MineWorkPostStatusPacket.Worker(2, uuid, 1, false))).validOccupancy());
        assertEquals(0, packet(2, List.of()).activeCount());
    }
    @Test void decodeBoundsWorkerListBeforeAllocation() {
        var original = packet(6, Collections.nCopies(7, new MineWorkPostStatusPacket.Worker(1, UUID.randomUUID(), 0, false)));
        var b = new FriendlyByteBuf(Unpooled.buffer());
        try { original.encode(b); assertThrows(IllegalArgumentException.class, () -> new MineWorkPostStatusPacket(b)); }
        finally { b.release(); }
    }
    @Test void sharedSequenceRejectsOldStatusAndReconnectRetiresOldStream() {
        EconomyActionClient.clear();
        try {
            assertTrue(EconomyActionClient.accept(stream, 20, session, epoch, 0));
            assertFalse(EconomyActionClient.accept(stream, 19, session, epoch, 0));
            assertFalse(EconomyActionClient.accept(UUID.randomUUID(), 21, session, epoch, 0));
            EconomyActionClient.clear(); assertFalse(EconomyActionClient.ready(epoch));
            var newEpoch = UUID.randomUUID();
            assertTrue(EconomyActionClient.accept(UUID.randomUUID(), 1, UUID.randomUUID(), newEpoch, 0));
            assertFalse(EconomyActionClient.ready(epoch)); assertTrue(EconomyActionClient.ready(newEpoch));
        } finally { EconomyActionClient.clear(); }
    }
    @Test void clientCompositionAndCacheLifecycleAreWiredToProductionCode() throws Exception {
        var base = Path.of("src/main/java/com/solegendary/reignofnether");
        String hud = Files.readString(base.resolve("hud/HudClientEvents.java"));
        String client = Files.readString(base.resolve("building/workposts/MineWorkPostClient.java"));
        String posts = Files.readString(base.resolve("building/buildings/shared/MineWorkPosts.java"));
        assertTrue(hud.contains("MineWorkPostClient.append("), "actual HUD must call the component, not only tests");
        assertTrue(posts.contains("return MineWorkPostClient.hasAssignment(worker)"));
        assertTrue(client.contains("views.get(p).nonce.equals(packet.view())"), "replaced same-origin client placement must reject old response");
        assertTrue(client.contains("CLOCK.context(mc.level, currentConnection)"));
        assertTrue(client.contains("LoggingOut event) { clear(); }") && client.contains("LoggingIn event) { clear(); }"));
        assertTrue(client.contains("entry.uuid().equals(worker.getUUID())"), "recycled entity ID is insufficient");
        assertTrue(client.contains("MAX_STATE_AGE"));
    }
}
