// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.economy;

import com.dreamseekers.economy.ResourceResearchPackets.Action;
import com.dreamseekers.economy.ResourceResearchPackets.Progress;
import com.dreamseekers.economy.ResourceResearchPackets.Request;
import com.dreamseekers.economy.ResourceResearchPackets.Snapshot;
import com.dreamseekers.economy.ResourceResearchPackets.Target;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static com.dreamseekers.economy.ResourceResearchPackets.ZERO;
import static org.junit.jupiter.api.Assertions.*;

/** Real request/reply wire without initializing a Forge connection, mapping a client world, or simulating the domain clock. */
final class ResourceResearchWireTest {
    private final ResourceLocation dimension = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    private final BlockPos pos = new BlockPos(-124, 70, 230);
    private final UUID instance = UUID.randomUUID(), view = UUID.randomUUID(), session = UUID.randomUUID(),
            epoch = UUID.randomUUID(), stream = UUID.randomUUID();
    private final Target selected = target(8, 12);

    private Target target(long capture, long revision) {
        return new Target(dimension, pos, instance, view, capture, revision);
    }
    private Snapshot response(Target host, long seq, String status, Progress progress) {
        return new Snapshot(stream, 21, session, epoch, "Own Player", seq, status, host, progress);
    }
    private static <T> T roundTrip(T value, BiConsumer<T, FriendlyByteBuf> encode, Function<FriendlyByteBuf, T> decode) {
        var wire = new FriendlyByteBuf(Unpooled.buffer());
        try {
            encode.accept(value, wire);
            T copy = decode.apply(wire);
            assertEquals(value, copy);
            assertEquals(0, wire.readableBytes(), "decoder must consume the exact wire representation");
            return copy;
        } finally { wire.release(); }
    }

    @Test void queriesBootstrapOrUseAnIntactContextButCannotStandInForAPurchase() {
        var initial = new Request(Action.QUERY, target(0, 0), ZERO, ZERO, 0);
        assertTrue(roundTrip(initial, Request::encode, Request::new).valid());
        assertTrue(new Request(Action.QUERY, initial.target(), session, epoch, 0).valid());
        assertFalse(new Request(Action.QUERY, initial.target(), session, ZERO, 0).valid());
        assertFalse(new Request(Action.QUERY, initial.target(), ZERO, epoch, 0).valid());
        assertFalse(new Request(Action.QUERY, initial.target(), session, epoch, 1).valid());
        assertFalse(new Request(Action.QUERY, selected, ZERO, ZERO, 0).valid());
        assertFalse(new Request(Action.START, initial.target(), ZERO, ZERO, 1).valid());
        assertFalse(new Request(Action.CANCEL, selected, session, epoch, 0).valid());
        assertFalse(new Request(Action.CANCEL, target(-1, 12), session, epoch, 2).valid());
        for (var action : new Action[]{Action.START, Action.CANCEL}) {
            assertTrue(roundTrip(new Request(action, selected, session, epoch, 2), Request::encode, Request::new).valid());
        }
    }

    @Test void sameCoordinatesAreNotAnIdentityOrAnOpenPanel() {
        var origin = new BlockPos.MutableBlockPos(pos.getX(), pos.getY(), pos.getZ());
        var pinned = new Target(dimension, origin, instance, view, 8, 12);
        origin.move(1, 0, 0);
        assertEquals(selected, pinned);
        assertTrue(pinned.matchesView(dimension, pos, instance, view));
        assertFalse(pinned.matchesView(dimension, origin, instance, view));
        assertFalse(pinned.matchesView(dimension, pos, UUID.randomUUID(), view), "same-position replacement");
        assertFalse(pinned.matchesView(dimension, pos, instance, UUID.randomUUID()), "panel closed and reopened");
        assertFalse(pinned.matchesView(ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether"), pos, instance, view));
        assertFalse(new Target(dimension, pos, ZERO, view, 8, 12).valid());
        assertFalse(new Target(dimension, pos, instance, ZERO, 8, 12).valid());
        var first = ResourceResearchPackets.fingerprint(Action.CANCEL, selected);
        assertEquals(first, ResourceResearchPackets.fingerprint(Action.CANCEL, pinned));
        assertNotEquals(first, ResourceResearchPackets.fingerprint(Action.START, selected));
        assertNotEquals(first, ResourceResearchPackets.fingerprint(Action.CANCEL, target(9, 12)), "capture and return");
        assertNotEquals(first, ResourceResearchPackets.fingerprint(Action.CANCEL, target(8, 13)), "cancel/restart");
    }

    @Test void privateSnapshotsAndCompletionOnlyAcksHaveDistinctRepresentationsAndBounds() {
        for (var progress : new Progress[]{new Progress(0, false), new Progress(ResourceBuildingResearch.DURATION, false),
                new Progress(1, false), new Progress(0, true)}) {
            assertTrue(roundTrip(response(selected, 0, "ready", progress), Snapshot::encode, Snapshot::new).valid());
        }
        var ack = roundTrip(response(selected, 3, "stale_session", null), Snapshot::encode, Snapshot::new);
        assertTrue(ack.valid());
        assertNull(ack.progress(), "a rejection/replay has no authoritative building state");
        assertFalse(response(selected, 0, "ready", null).valid(), "a bare ACK cannot establish a context");
        assertFalse(response(selected, 0, "started", new Progress(600, false)).valid());
        assertFalse(response(selected, -1, "started", new Progress(600, false)).valid());
        for (var invalid : new Progress[]{new Progress(-1, false), new Progress(ResourceBuildingResearch.DURATION + 1, false),
                new Progress(1, true)}) {
            assertFalse(roundTrip(response(selected, 2, "started", invalid), Snapshot::encode, Snapshot::new).valid());
        }
    }
}
