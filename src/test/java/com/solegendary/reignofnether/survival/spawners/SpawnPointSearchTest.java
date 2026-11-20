package com.solegendary.reignofnether.survival.spawners;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** Attempt budget of the wave spawn point search. Pure arithmetic; no Forge server or terrain. */
class SpawnPointSearchTest {
    private static final int ATTEMPTS = WaveSpawner.SAMPLE_POINTS_PER_BUILDING;
    private static final int FAILED = WaveSpawner.MAX_FAILED_BUILDINGS;

    /** Counts samples; a fixed seed makes every run identical. */
    private static final class Terrain implements Supplier<Integer> {
        private final Random random;
        private final double validShare;
        int samples;
        Terrain(double validShare, long seed) { this.validShare = validShare; this.random = new Random(seed); }
        @Override public Integer get() { samples++; return random.nextDouble() < validShare ? samples : null; }
    }

    /** The loop getValidSpawnPoints had before: one attempt counter for the whole call, never reset. */
    private static List<Integer> sharedCounterSearch(int amount, Terrain terrain) {
        List<Integer> points = new ArrayList<>();
        int attempts = 0, failed = 0;
        outer:
        do {
            Integer point;
            do {
                point = terrain.get();
                if (++attempts > ATTEMPTS) {
                    if (++failed > FAILED) break outer;
                    continue; // skipped the distance checks and went straight to the loop condition
                }
            } while (point == null);
            points.add(point);
            amount--;
        } while (amount > 0);
        return points;
    }

    @Test void sharedCounterCappedEveryCallNearOneHundredAndTenPoints() {
        // Baseline of the replaced loop: wave 30 asks for 610 points per player.
        for (int players = 1; players <= 4; players++) {
            Terrain open = new Terrain(1.0, 65);
            assertEquals(ATTEMPTS + FAILED, sharedCounterSearch(610 * players, open).size());
            assertEquals(ATTEMPTS + FAILED + 1, open.samples);
        }
        Terrain half = new Terrain(0.5, 65);
        assertTrue(sharedCounterSearch(610, half).size() < ATTEMPTS + FAILED);
        assertEquals(ATTEMPTS + FAILED + 1, half.samples);
    }

    @Test void everyRequestedPointGetsItsOwnAttempts() {
        for (int players = 1; players <= 4; players++) {
            for (double validShare : new double[] { 1.0, 0.5, 0.2 }) {
                Terrain terrain = new Terrain(validShare, 65);
                int amount = 610 * players;
                assertEquals(amount, SpawnPointSearch.collect(amount, ATTEMPTS, FAILED, terrain).size());
                // repeatable cost baseline: about 1/validShare samples per point
                assertTrue(terrain.samples <= amount / validShare * 1.25, terrain.samples + " samples");
            }
        }
    }

    @Test void searchStaysBoundedWhenNothingIsValid() {
        Terrain none = new Terrain(0.0, 65);
        assertTrue(SpawnPointSearch.collect(2440, ATTEMPTS, FAILED, none).isEmpty());
        assertEquals(ATTEMPTS * (FAILED + 1), none.samples);
        assertTrue(SpawnPointSearch.collect(0, ATTEMPTS, FAILED, none).isEmpty());
        assertEquals(ATTEMPTS * (FAILED + 1), none.samples, "no request, no sample");
    }

    @Test void partialResultKeepsThePointsFoundBeforeTheSearchGaveUp() {
        int[] calls = { 0 };
        List<Integer> points = SpawnPointSearch.collect(50, ATTEMPTS, FAILED, () -> ++calls[0] <= 7 ? calls[0] : null);
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), points);
        assertEquals(7 + ATTEMPTS * (FAILED + 1), calls[0]);
    }

    @Test void spawnersUseTheSearchAndDoNotRetryAnExhaustedOne() throws Exception {
        Path dir = Path.of("src/main/java/com/solegendary/reignofnether/survival/spawners");
        String wave = Files.readString(dir.resolve("WaveSpawner.java"));
        assertTrue(wave.contains("SpawnPointSearch.collect(amount, SAMPLE_POINTS_PER_BUILDING, MAX_FAILED_BUILDINGS,"));
        assertFalse(wave.contains("spawnAttemptsThisBuilding"));
        String piglin = Files.readString(dir.resolve("PiglinWaveSpawner.java"));
        int search = piglin.indexOf("WaveSpawner.getValidSpawnPoints(1, level, false, 8)");
        int exhausted = piglin.indexOf("if (spawnBps.isEmpty()) {", search);
        assertTrue(search >= 0 && exhausted > search && piglin.indexOf("break;", exhausted) < piglin.indexOf("attempts += 1", exhausted));
    }
}
