package com.solegendary.reignofnether.particles;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Actual particle constructor/tick, without a renderer, window, sprite or running ClientLevel. */
class BigVibrationParticleLifetimeTest {
    private static final class Probe extends BigVibrationParticle {
        Probe(BlockPositionSource target, int lifetime) { super(null, 0, 64, 0, target, lifetime); }
        double[] coordinates() { return new double[]{x, y, z, xo, yo, zo}; }
        int duration() { return lifetime; }
    }

    private static void finite(Probe particle) {
        for (double value : particle.coordinates()) assertTrue(Double.isFinite(value), "actual particle coordinates remain finite");
        for (float partial : new float[]{0, .5f, 1})
            assertTrue(Float.isFinite(particle.getQuadSize(partial)), "actual quad size remains finite");
        var bounds = particle.getBoundingBox();
        for (double value : new double[]{bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ})
            assertTrue(Double.isFinite(value), "actual particle bounding box remains finite");
    }

    @Test void registeredPositiveLifetimesNeverProduceNanAtTheArrivalTick() {
        for (int ticks : new int[]{1, 2, 40}) {
            Probe particle = new Probe(new BlockPositionSource(new BlockPos(10, 64, 10)), ticks);
            finite(particle);
            for (int i=0; i<ticks+2; i++) { particle.tick(); finite(particle); }
            assertFalse(particle.isAlive(), "finished particle is retired");
        }
    }

    @Test void nonPositiveExternalLifetimesRemainFiniteAndTerminate() {
        for (int requested : new int[]{0, -1, Integer.MIN_VALUE}) {
            Probe particle = new Probe(new BlockPositionSource(new BlockPos(10, 64, 10)), requested);
            assertTrue(particle.duration() > 0, "render lifetime normalized before first frame");
            finite(particle);
            for (int i=0; i<3; i++) { particle.tick(); finite(particle); }
            assertFalse(particle.isAlive());
        }
    }

    @Test void missingTargetStillRetiresWithoutAccessingAClientWorld() {
        Probe particle = new Probe(new BlockPositionSource(BlockPos.ZERO) {
            @Override public Optional<Vec3> getPosition(Level level) { return Optional.empty(); }
        }, 40);
        particle.tick(); finite(particle);
        assertFalse(particle.isAlive());
    }
}
