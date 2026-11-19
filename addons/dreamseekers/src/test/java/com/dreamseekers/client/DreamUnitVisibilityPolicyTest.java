package com.dreamseekers.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DreamUnitVisibilityPolicyTest {
    @Test void onlyDreamUnitsInRtsViewGainDistanceEligibility() {
        for (boolean rts : new boolean[] {false, true}) {
            for (boolean dream : new boolean[] {false, true}) {
                assertEquals(rts && dream, DreamUnitVisibilityPolicy.renderAtDistance(false, rts, dream, 80 * 80));
                assertTrue(DreamUnitVisibilityPolicy.renderAtDistance(true, rts, dream, 1000 * 1000),
                        "never shorten an existing renderer's larger range");
            }
        }
    }

    @Test void extensionIsFiniteBoundedAndDoesNotGrantInvalidDistances() {
        double max = DreamUnitVisibilityPolicy.RTS_DISTANCE_FLOOR * DreamUnitVisibilityPolicy.RTS_DISTANCE_FLOOR;
        assertTrue(DreamUnitVisibilityPolicy.renderAtDistance(false, true, true, 0));
        assertTrue(DreamUnitVisibilityPolicy.renderAtDistance(false, true, true, Math.nextDown(max)));
        for (double distance : new double[] {max, Math.nextUp(max), -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertFalse(DreamUnitVisibilityPolicy.renderAtDistance(false, true, true, distance), Double.toString(distance));
        }
    }

    @Test void boundCoversSupportedHighShallowCameraGeometry() {
        double highShallowFarEdge = Math.hypot((100 + 90.0 / 2) / Math.sin(Math.toRadians(20)), 90.0 * 4 / 2);
        assertTrue(highShallowFarEdge < DreamUnitVisibilityPolicy.RTS_DISTANCE_FLOOR);
        assertEquals(512.0, DreamUnitVisibilityPolicy.RTS_DISTANCE_FLOOR);
    }

    @Test void geoModelMatchesNativeFogAndBorderTruthTable() {
        for (boolean level : new boolean[] {false, true}) {
            for (boolean border : new boolean[] {false, true}) {
                for (boolean bright : new boolean[] {false, true}) {
                    assertEquals((level && !border) || !bright,
                            DreamUnitVisibilityPolicy.cancelModel(true, level, border, bright));
                    assertFalse(DreamUnitVisibilityPolicy.cancelModel(false, level, border, bright),
                            "natural PasterDream entities are outside this addon hook");
                }
            }
        }
    }

    @Test void greaterDistanceNeverOverridesHiddenOrOutOfBorderModels() {
        assertTrue(DreamUnitVisibilityPolicy.renderAtDistance(false, true, true, 80 * 80));
        assertTrue(DreamUnitVisibilityPolicy.cancelModel(true, true, true, false));
        assertTrue(DreamUnitVisibilityPolicy.cancelModel(true, true, false, true));
        assertFalse(DreamUnitVisibilityPolicy.cancelModel(true, true, true, true));
    }

    @Test void distanceMixinIsClientOnlyAndPreservesOtherNativeGates() throws Exception {
        var config = JsonParser.parseString(Files.readString(Path.of("addons/dreamseekers/src/main/resources/dreamseekers.mixins.json"))).getAsJsonObject();
        assertTrue(config.getAsJsonArray("client").asList().stream().anyMatch(e -> e.getAsString().equals("DreamUnitRenderDistanceMixin")));
        assertFalse(config.getAsJsonArray("mixins").asList().stream().anyMatch(e -> e.getAsString().equals("DreamUnitRenderDistanceMixin")));
        String source = Files.readString(Path.of("addons/dreamseekers/src/main/java/com/dreamseekers/unit/mixin/DreamUnitRenderDistanceMixin.java"));
        assertTrue(source.contains("@At(\"RETURN\")"));
        assertTrue(source.contains("DreamUnitVisibilitySubject.matches(this)"));
        assertTrue(source.contains("!OrthoviewClientEvents.isEnabled()"));
        assertTrue(source.contains("callback.getReturnValue()"));
        assertFalse(source.contains("setBoundingBox("));
        assertFalse(source.contains("noCulling ="));
    }

    @Test void actualGeoHookUsesExistingVisibilityAndBorderChecks() throws Exception {
        String source = Files.readString(Path.of("addons/dreamseekers/src/main/java/com/dreamseekers/client/DreamUnitVisibility.java"));
        assertTrue(source.contains("value = Dist.CLIENT"));
        assertTrue(source.contains("bus = Mod.EventBusSubscriber.Bus.FORGE"));
        assertTrue(source.contains("GeoRenderEvent.Entity.Pre event"));
        assertTrue(source.contains("if (!DreamUnitVisibilitySubject.matches(entity)) return;"));
        assertTrue(source.contains("getWorldBorder().isWithinBounds(entity.getOnPos())"));
        assertTrue(source.contains("FogOfWarClientEvents.isInBrightChunk(entity)"));
        assertTrue(source.contains("event.setCanceled(true)"));
    }
}
