package com.solegendary.reignofnether.fogofwar;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FogSnapshotBoundsTest {
    @Test void alignedMaximumCoversExactly80By80() {
        var b = FogSnapshotBounds.checked(1280, -640, 640, -640, 640);
        assertNotNull(b);
        assertEquals(80, b.maxChunkX() - b.minChunkX() + 1);
        assertEquals(-40, b.minChunkX());
        assertEquals(39, b.maxChunkX());
    }
    @Test void fractionalMaximumMayTouch81By81() {
        var b = FogSnapshotBounds.checked(1280, -639.5, 640.5, -641.5, 638.5);
        assertNotNull(b);
        assertEquals(81, b.maxChunkX() - b.minChunkX() + 1);
        assertEquals(81, b.maxChunkZ() - b.minChunkZ() + 1);
    }
    @Test void negativeAndFractionalBoundaryUsesHalfOpenChunks() {
        var b = FogSnapshotBounds.checked(16, -16.5, -.5, -16, 0);
        assertNotNull(b);
        assertEquals(-2, b.minChunkX());
        assertEquals(-1, b.maxChunkX());
        assertEquals(-1, b.minChunkZ());
        assertEquals(-1, b.maxChunkZ());
        assertTrue(b.contains(-2,-1));
        assertFalse(b.contains(0,0));
    }
    @Test void rejectsHugeInvalidAndOverflowBeforeIteration() {
        for (double size : new double[]{0,-1,1280.0001,59999968,Double.MAX_VALUE,Double.NaN,Double.POSITIVE_INFINITY})
            assertNull(FogSnapshotBounds.checked(size,-8,8,-8,8));
        for (double coordinate : new double[]{Double.NaN,Double.NEGATIVE_INFINITY,-Double.MAX_VALUE,-30000001})
            assertNull(FogSnapshotBounds.checked(16,coordinate,0,-8,8));
        assertNull(FogSnapshotBounds.checked(16,0,30000001,-8,8));
        assertNull(FogSnapshotBounds.checked(16,1,0,-8,8));
        assertNull(FogSnapshotBounds.checked(16,0,0,-8,8));
        assertNull(FogSnapshotBounds.checked(16,-641,640,-8,8));
    }
    @Test void extremeValidCentersStaySafe() {
        assertNotNull(FogSnapshotBounds.checked(1280,29998720,30000000,-30000000,-29998720));
    }
}
