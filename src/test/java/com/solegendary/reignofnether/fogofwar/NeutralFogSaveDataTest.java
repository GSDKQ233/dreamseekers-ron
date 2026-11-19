package com.solegendary.reignofnether.fogofwar;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NeutralFogSaveDataTest {
    private static final UUID UNIT = new UUID(1, 2), ALICE = new UUID(3, 4), BOB = new UUID(5, 6);
    private static final AABB ORIGINAL = new AABB(-2.5, 64, -0.5, -1.5, 66, 0.5);

    private NeutralFogSaveData captured() {
        NeutralFogSaveData data = new NeutralFogSaveData();
        data.capture(UNIT, ORIGINAL);
        data.finishCapture();
        return data;
    }
    private NeutralFogSaveData restored(NeutralFogSaveData data) {
        return NeutralFogSaveData.load(data.save(new CompoundTag()));
    }
    @Test void coldRestartKeepsUuidOriginalBoundsAndCaptureGuard() {
        NeutralFogSaveData data = restored(captured());
        assertTrue(data.isCaptured());
        assertEquals(UNIT, data.markers().iterator().next().id);
        assertEquals(ORIGINAL, data.markers().iterator().next().bounds);
        data.capture(UNIT, ORIGINAL.move(100, 0, 100));
        data.capture(UUID.randomUUID(), ORIGINAL);
        assertEquals(1, data.markers().size());
        assertEquals(ORIGINAL, data.markers().iterator().next().bounds);
    }
    @Test void absenceAndRestartDoNotInventDeath() {
        NeutralFogSaveData data = restored(captured());
        assertFalse(data.markers().iterator().next().isDestroyed());
        assertFalse(data.observeDestroyed(UNIT, ALICE, true));
        assertFalse(data.markers().iterator().next().isRemovedFor(ALICE));
    }
    @Test void deathOnlyRetiresForViewerWhoCanSeeOriginalPositionAndSurvivesRelog() {
        NeutralFogSaveData data = captured();
        data.markDestroyed(UNIT);
        assertFalse(data.observeDestroyed(UNIT, ALICE, false));
        assertTrue(data.observeDestroyed(UNIT, BOB, true));
        data = restored(data);
        var marker = data.markers().iterator().next();
        assertTrue(marker.isDestroyed());
        assertFalse(marker.isRemovedFor(ALICE));
        assertTrue(marker.isRemovedFor(BOB));
        assertFalse(data.observeDestroyed(UNIT, BOB, true));
        assertTrue(data.observeDestroyed(UNIT, ALICE, true));
    }
    @Test void separateWorldInstancesCannotShareMarkersOrViewerHistory() {
        NeutralFogSaveData first = captured();
        first.markDestroyed(UNIT);
        first.observeDestroyed(UNIT, ALICE, true);
        NeutralFogSaveData second = new NeutralFogSaveData();
        assertFalse(second.isCaptured());
        assertTrue(second.markers().isEmpty());
        assertFalse(second.observeDestroyed(UNIT, ALICE, true));
    }
    @Test void emptySnapshotIsPersistedRatherThanRecapturedOnEveryFirstRtsJoin() {
        NeutralFogSaveData data = new NeutralFogSaveData();
        data.finishCapture();
        data = restored(data);
        data.capture(UNIT, ORIGINAL);
        assertTrue(data.markers().isEmpty());
        assertTrue(data.isCaptured());
    }
    @Test void explicitMatchResetClearsTombstonesAndAllowsFreshCapture() {
        NeutralFogSaveData data = captured();
        data.markDestroyed(UNIT);
        data.observeDestroyed(UNIT, ALICE, true);
        data.reset();
        data = restored(data);
        assertFalse(data.isCaptured());
        assertTrue(data.markers().isEmpty());
        data.capture(UNIT, ORIGINAL.move(10, 0, 0));
        data.finishCapture();
        var marker = data.markers().iterator().next();
        assertFalse(marker.isDestroyed());
        assertFalse(marker.isRemovedFor(ALICE));
        assertEquals(ORIGINAL.move(10, 0, 0), marker.bounds);
    }
    @Test void unknownOrUnversionedSchemaIsOpaqueImmutableAndCannotBeResetOrCaptured() {
        for (int version : new int[] {-1, 0, 2, Integer.MAX_VALUE}) {
            CompoundTag source = captured().save(new CompoundTag());
            if (version == -1) source.remove("FormatVersion"); else source.putInt("FormatVersion", version);
            source.putString("FutureField", "must survive downgrade");
            CompoundTag expected = source.copy();
            NeutralFogSaveData data = NeutralFogSaveData.load(source);
            source.putString("FutureField", "mutated input");
            assertFalse(data.isSupported());
            assertTrue(data.isCaptured(), "suppresses automatic migration/recapture");
            assertTrue(data.markers().isEmpty(), "must not interpret unknown fields");
            data.reset(); data.capture(UNIT, ORIGINAL); data.finishCapture(); data.markDestroyed(UNIT);
            assertFalse(data.observeDestroyed(UNIT, ALICE, true));
            assertEquals(expected, data.save(new CompoundTag()));
            data.save(new CompoundTag()).putString("FutureField", "mutated output");
            assertEquals(expected, data.save(new CompoundTag()));
            assertFalse(data.isDirty(), "unsupported data is not scheduled for overwrite");
        }
    }
    @Test void viewerLimitNeverSendsUnrememberedRemovalOrResurrectsAcceptedObservation() {
        NeutralFogSaveData data = captured();
        data.markDestroyed(UNIT);
        for (int i = 0; i < NeutralFogSaveData.MAX_VIEWERS_PER_MARKER; i++)
            assertTrue(data.observeDestroyed(UNIT, new UUID(42, i), true));
        UUID overflow = new UUID(43, 0);
        assertFalse(data.observeDestroyed(UNIT, overflow, true));
        data = restored(data);
        assertFalse(data.observeDestroyed(UNIT, overflow, true));
        assertFalse(data.markers().iterator().next().isRemovedFor(overflow));
        assertTrue(data.markers().iterator().next().isRemovedFor(new UUID(42, 0)));
        assertFalse(data.observeDestroyed(UNIT, new UUID(42, 0), true));
    }
    @Test void malformedAndOversizedSnapshotsAreBounded() {
        NeutralFogSaveData data = new NeutralFogSaveData();
        data.capture(UNIT, new AABB(Double.NaN, 0, 0, 1, 1, 1));
        assertTrue(data.markers().isEmpty());
        for (int i = 0; i <= NeutralFogSaveData.MAX_MARKERS; i++)
            data.capture(new UUID(0, i), ORIGINAL);
        assertEquals(NeutralFogSaveData.MAX_MARKERS, data.markers().size());
        CompoundTag tag = captured().save(new CompoundTag());
        ListTag markers = tag.getList("Markers", 10);
        markers.getCompound(0).putDouble("MinX", Double.NaN);
        markers.add(new CompoundTag()); // missing UUID
        assertTrue(NeutralFogSaveData.load(tag).markers().isEmpty());
    }
    @Test void uuidPacketRoundTripPreservesFullBoundsAndRemoval() {
        for (boolean remove : new boolean[] { false, true }) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                new FogNeutralUnitClientboundPacket(UNIT, ORIGINAL, remove).encode(buffer);
                var decoded = new FogNeutralUnitClientboundPacket(buffer);
                assertEquals(UNIT, decoded.unitId);
                assertEquals(remove, decoded.remove);
                assertEquals(66.0f, decoded.vec3fMax.y);
                assertEquals(-2.5f, decoded.vec3fMin.x);
                assertEquals(0, buffer.readableBytes());
            } finally { buffer.release(); }
        }
    }
    @Test void lifecycleWiringDoesNotInterpretUnloadedEntityAsDeathOrForceChunks() throws Exception {
        String events = Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/fogofwar/FogOfWarServerEvents.java"));
        String lifecycle = events.substring(events.indexOf("public static void onNeutralEntityRemoved"),
                events.indexOf("public static void setPlayerRevealed"));
        assertTrue(lifecycle.contains("reason.shouldDestroy()"));
        assertFalse(lifecycle.contains("getChunk("));
        assertTrue(lifecycle.contains("level.getServer().overworld()"));
        assertTrue(events.contains("onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent"));
        assertFalse(events.contains("serverLevel.getEntity(id) == null"));
        assertTrue(events.contains("net.minecraft.util.Mth.floor(center.x)"));
        assertTrue(events.contains("serverLevel = null;"));
        String packetHandler = Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/registrars/PacketHandler.java"));
        assertTrue(packetHandler.contains(":neutral-fog-uuid-1"));
    }
}
