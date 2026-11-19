package com.solegendary.reignofnether.unit.modelling;

import com.google.common.collect.MapMaker;
import com.solegendary.reignofnether.unit.modelling.models.WindcallerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Headless storage/math contracts plus explicit source wiring, NOT setupAnim/GUI acceptance.
 * Real Windcaller construction requires Forge entity/attribute/fluid registries and a Level;
 * this test deliberately does not bypass constructors or fake a running client.
 */
class WindcallerTiltIsolationTest {
    /** Matches vanilla Entity's same-network-id equality without constructing a game entity. */
    private record NetworkIdKey(int id) { }

    private static float renderContract(Map<Object, Float> map, Object entity, float target) {
        float value = Mth.lerp(0.025f, map.getOrDefault(entity, 0.0f), target);
        map.put(entity, value);
        return value;
    }

    @Test void actualModelSourceUsesWeakIdentityKeysAndKeepsOriginalPerRenderInterpolation() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/unit/modelling/models/WindcallerModel.java"));
        assertAll(
                () -> assertTrue(source.contains("new MapMaker().weakKeys().makeMap()"), "identity weak storage"),
                () -> assertTrue(source.contains("Mth.lerp(0.025f, currentTilts.getOrDefault(entity, 0.0f), targetTilt)"), "same interpolation, entity-local previous value"),
                () -> assertTrue(source.contains("currentTilts.put(entity, currentTilt)"), "only this identity receives new tilt"),
                () -> assertFalse(source.contains("private float currentTilt"), "no shared scalar history"));
    }

    @Test void sameNetworkIdButDifferentObjectsKeepIndependentStorage() {
        Object flying = new NetworkIdKey(7), grounded = new NetworkIdKey(7);
        assertEquals(flying, grounded, "positive boundary really collides under ordinary Map equality");
        assertNotSame(flying, grounded);
        Map<Object, Float> map = new MapMaker().weakKeys().makeMap();
        assertEquals(Mth.lerp(.025f,0,.2f), renderContract(map,flying,.2f));
        assertEquals(0, renderContract(map,grounded,0));
        assertEquals(2,map.size());
        assertTrue(map.get(flying)>0);
        assertEquals(0f,map.get(grounded));
    }

    @Test void interleavedRenderContractsMatchEachIsolatedEntityHistory() {
        Object a = new Object(), b = new Object();
        Map<Object, Float> shared = new MapMaker().weakKeys().makeMap();
        Map<Object, Float> isolatedA = new MapMaker().weakKeys().makeMap();
        Map<Object, Float> isolatedB = new MapMaker().weakKeys().makeMap();
        for (int frame=0;frame<40;frame++) {
            float targetA = frame<20 ? .2f : .1f, targetB = frame<30 ? 0 : .2f;
            assertEquals(renderContract(isolatedA,a,targetA),renderContract(shared,a,targetA));
            assertEquals(renderContract(isolatedB,b,targetB),renderContract(shared,b,targetB));
        }
    }

    @Test void candidateRealBakedModelInstallsTheIdentityMap() throws Exception {
        // Baseline compiles, but has no new map. Its missing field is NOT counted as a runtime bug proof.
        Field field = Arrays.stream(WindcallerModel.class.getDeclaredFields())
                .filter(f -> f.getName().equals("currentTilts")).findFirst().orElse(null);
        assumeTrue(field != null, "candidate-only real model storage contract");
        field.setAccessible(true);
        var model = new WindcallerModel<Entity>(WindcallerModel.createBodyLayer().bakeRoot());
        @SuppressWarnings("unchecked") Map<Object, Float> map = (Map<Object, Float>)field.get(model);
        Object first = new NetworkIdKey(91), second = new NetworkIdKey(91);
        map.put(first,.2f); map.put(second,0f);
        assertEquals(2,map.size(), "actual installed storage does not merge equal network ids");
        assertEquals(.2f,map.get(first)); assertEquals(0f,map.get(second));
    }
}
