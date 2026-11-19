package com.dreamseekers.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class NativeContractVisibilityPolicyTest {
    @Test void exactNativeWhitelistRequiresMatchingClassAndFullRegistryIdentity() {
        assertTrue(NativeContractVisibilityPolicy.isExactNativeTarget("pasterdream", "black_beetle", true, false));
        assertTrue(NativeContractVisibilityPolicy.isExactNativeTarget("pasterdream", "basalt_snail", false, true));
        assertFalse(NativeContractVisibilityPolicy.isExactNativeTarget("pasterdream", "black_beetle", false, true));
        assertFalse(NativeContractVisibilityPolicy.isExactNativeTarget("pasterdream", "basalt_snail", true, false));
        for (String namespace : new String[] {null, "", "minecraft", "dreamseekers", "othermod"}) {
            assertFalse(NativeContractVisibilityPolicy.isExactNativeTarget(namespace, "black_beetle", true, false));
            assertFalse(NativeContractVisibilityPolicy.isExactNativeTarget(namespace, "basalt_snail", false, true));
        }
        for (String path : new String[] {null, "", "beetle", "black_beetle_mother", "pig", "firefly_worker"}) {
            assertFalse(NativeContractVisibilityPolicy.isExactNativeTarget("pasterdream", path, true, true));
        }
        assertFalse(NativeContractVisibilityPolicy.isExactNativeTarget("pasterdream", "black_beetle", false, false));
        assertFalse(NativeContractVisibilityPolicy.isExactNativeTarget("pasterdream", "basalt_snail", false, false));
    }

    @Test void onlyVisibleNativeTargetsInRtsGainDistanceEligibility() {
        for (boolean rts : new boolean[] {false, true})
            for (boolean nativeTarget : new boolean[] {false, true})
                for (boolean hidden : new boolean[] {false, true})
                    for (boolean border : new boolean[] {false, true})
                        for (boolean bright : new boolean[] {false, true})
                            assertEquals(rts && nativeTarget && !hidden && border && bright,
                                    NativeContractVisibilityPolicy.renderAtDistance(false, rts, nativeTarget,
                                            hidden, border, bright, 80 * 80));
    }

    @Test void firstPersonAndOtherMobsRetainOriginalResultAtEveryDistance() {
        for (double distance : new double[] {0, 45 * 45, 511 * 511, 512 * 512, Double.NaN}) {
            assertFalse(NativeContractVisibilityPolicy.renderAtDistance(false, false, true, false, true, true, distance));
            assertFalse(NativeContractVisibilityPolicy.renderAtDistance(false, true, false, false, true, true, distance));
            assertTrue(NativeContractVisibilityPolicy.renderAtDistance(true, false, true, false, true, true, distance));
            assertTrue(NativeContractVisibilityPolicy.renderAtDistance(true, true, false, false, true, true, distance));
        }
    }

    @Test void extensionReusesFiniteExclusive512Bound() {
        double max = 512.0 * 512.0;
        assertEquals(512.0, DreamUnitVisibilityPolicy.RTS_DISTANCE_FLOOR);
        assertTrue(NativeContractVisibilityPolicy.renderAtDistance(false, true, true, false, true, true, 0));
        assertTrue(NativeContractVisibilityPolicy.renderAtDistance(false, true, true, false, true, true, Math.nextDown(max)));
        for (double distance : new double[] {max, Math.nextUp(max), -1, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertFalse(NativeContractVisibilityPolicy.renderAtDistance(false, true, true, false, true, true, distance));
        }
    }

    @Test void preexistingTrueResultIsNotNarrowedEvenIfAnotherGateHidesTheModel() {
        assertTrue(NativeContractVisibilityPolicy.renderAtDistance(true, true, true, true, false, false, 1000 * 1000));
    }

    @Test void vanillaSmallBodyThresholdExplainsBeetleVersusSnailAtOrdinaryRtsCenter() {
        double beetleRange = (0.6 + 0.5 + 0.6) / 3.0 * 64.0;
        double snailRange = (0.6 + 1.8 + 0.6) / 3.0 * 64.0;
        double rtsCenterRange = (30.0 + 1.62) / Math.sin(Math.toRadians(45));
        assertTrue(beetleRange < rtsCenterRange && rtsCenterRange < snailRange);
        assertTrue(NativeContractVisibilityPolicy.renderAtDistance(false, true, true, false, true, true,
                rtsCenterRange * rtsCenterRange));
    }

    @Test void actualMixinIsClientOnlyReturnHookWithExactTypeAndVisibilityChecks() throws Exception {
        Path base = Path.of("addons/dreamseekers/src/main");
        var config = JsonParser.parseString(Files.readString(base.resolve("resources/dreamseekers.mixins.json"))).getAsJsonObject();
        String mixin = "NativeContractRenderDistanceMixin";
        assertTrue(config.getAsJsonArray("client").asList().stream().anyMatch(e -> e.getAsString().equals(mixin)));
        assertFalse(config.getAsJsonArray("mixins").asList().stream().anyMatch(e -> e.getAsString().equals(mixin)));
        assertEquals("dreamseekers.refmap.json", config.get("refmap").getAsString());
        String source = Files.readString(base.resolve("java/com/dreamseekers/unit/mixin/" + mixin + ".java"));
        for (String required : new String[] {"@Mixin(Entity.class)", "shouldRenderAtSqrDistance(D)Z", "@At(\"RETURN\")",
                "callback.getReturnValue() || !OrthoviewClientEvents.isEnabled()", "entity.getClass() == BlackBeetleEntity.class",
                "entity.getClass() == BasaltSnailEntity.class", "BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())",
                "isExactNativeTarget(id.getNamespace(), id.getPath(), beetle, snail)", "!entity.isAlive() || entity.isInvisible()",
                "getWorldBorder().isWithinBounds(entity.getOnPos())", "FogOfWarClientEvents.isInBrightChunk(entity)"}) {
            assertTrue(source.contains(required), required);
        }
        for (String forbidden : new String[] {"instanceof BlackBeetleEntity", "instanceof BasaltSnailEntity", "getPersistentData(",
                "setBoundingBox(", "setDimensions(", "noCulling", "setViewScale(", "setTrackingRange(", "setInvisible(",
                "setNoAi(", "setPos(", "teleportTo(", "addFreshEntity(", "RenderLivingEvent", "GeoRenderEvent"}) {
            assertFalse(source.contains(forbidden), forbidden);
        }
    }
}
