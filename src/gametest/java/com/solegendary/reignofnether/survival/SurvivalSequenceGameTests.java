package com.solegendary.reignofnether.survival;

import com.solegendary.reignofnether.faction.Factions;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Runs against registered core factions and the actual server restore handler; spawns no enemies. */
@GameTestHolder("reignofnether_survival_sequence")
@PrefixGameTestTemplate(false)
public final class SurvivalSequenceGameTests {
    private static final String NAMESPACE = "reignofnether_survival_sequence";

    private static List<ResourceLocation> snapshot() {
        List<ResourceLocation> keys = new ArrayList<>();
        for (int number = 1; number <= 30; number++) keys.add(Wave.getWave(number).faction.key);
        return keys;
    }

    private static List<ResourceLocation> reseed(long seed) {
        Wave.randomSeed = seed;
        Wave.reseedWaves();
        return snapshot();
    }

    @GameTest(templateNamespace=NAMESPACE, template="ore_mine_test_arena", batch="survival_sequence", timeoutTicks=80)
    public static void realWavesConsumeSeededRegisteredSurvivalRoster(GameTestHelper h) {
        long originalSeed = Wave.randomSeed;
        try {
            var roster = Factions.SURVIVAL_FACTIONS.stream().distinct()
                    .filter(key -> key != null && Factions.getFaction(key) != Factions.NONE
                            && Factions.getFaction(key).spawnWave != null)
                    .sorted(Comparator.comparing(ResourceLocation::toString)).toList();
            h.assertTrue(roster.size() >= 2, "registered core provides multiple survival factions");
            var actual = reseed(123456789L);
            Random expectedRandom = new Random(123456789L);
            ResourceLocation previous = null;
            for (int number = 1; number <= 30; number++) {
                var candidates = new ArrayList<>(roster);
                candidates.remove(previous);
                ResourceLocation expected = candidates.get(expectedRandom.nextInt(candidates.size()));
                Wave wave = Wave.getWave(number);
                h.assertTrue(wave.number == number && wave.faction == Factions.getFaction(expected),
                        "actual wave " + number + " consumes seed-driven registered faction");
                h.assertTrue(roster.contains(wave.faction.key) && wave.faction.spawnWave != null,
                        "actual wave has a valid registered survival spawner");
                h.assertTrue(!wave.faction.key.equals(previous), "adjacent actual waves do not repeat");
                previous = expected;
            }
            h.assertTrue(!actual.equals(reseed(987654321L)), "different seed changes actual wave sequence");
            h.assertTrue(actual.equals(reseed(123456789L)), "restoring seed reproduces all actual waves");
            h.assertTrue(Wave.getWave(Integer.MIN_VALUE) == Wave.getWave(1), "low wave number clamps safely");
            h.assertTrue(Wave.getWave(Integer.MAX_VALUE) == Wave.getWave(30), "high wave number clamps safely");
            System.out.println("SURVIVAL_SEQUENCE_PASS actualWaves=30 deterministic=true validRoster=true noAdjacentRepeat=true");
        } finally {
            Wave.randomSeed = originalSeed;
            Wave.reseedWaves();
        }
        h.succeed();
    }

    @GameTest(templateNamespace=NAMESPACE, template="ore_mine_test_arena", batch="survival_sequence", timeoutTicks=80)
    public static void actualServerRestoreSelectsNextWaveAfterApplyingSavedSeed(GameTestHelper h) {
        h.assertTrue(!SurvivalServerEvents.isEnabled(), "isolated test world has survival disabled");
        var data = SurvivalSaveData.getInstance(h.getLevel());
        boolean savedEnabled = data.isEnabled;
        int savedNumber = data.waveNumber;
        long savedSeed = data.randomSeed;
        WaveDifficulty savedDifficulty = data.difficulty;
        long originalSeed = Wave.randomSeed;
        Wave originalNext = SurvivalServerEvents.nextWave;
        WaveDifficulty originalDifficulty = SurvivalServerEvents.getDifficulty();
        try {
            var expected = reseed(123456789L);
            var stale = reseed(987654321L);
            int differentWave = -1;
            for (int i = 0; i < 30; i++) {
                if (!expected.get(i).equals(stale.get(i))) { differentWave = i + 1; break; }
            }
            h.assertTrue(differentWave > 0, "fixture discriminates stale and restored seed");
            // No save()/setDirty(): only the isolated world's in-memory record is temporarily changed.
            data.isEnabled = false;
            data.waveNumber = differentWave;
            data.randomSeed = 123456789L;
            data.difficulty = WaveDifficulty.HARD;
            SurvivalServerEvents.loadWaveData(new ServerStartedEvent(h.getLevel().getServer()));
            h.assertTrue(Wave.randomSeed == data.randomSeed, "actual restore applies saved seed");
            h.assertTrue(SurvivalServerEvents.nextWave == Wave.getWave(differentWave),
                    "actual restore stores a wave from the newly seeded snapshot, not stale snapshot");
            h.assertTrue(SurvivalServerEvents.nextWave.faction.key.equals(expected.get(differentWave - 1)),
                    "restored next wave faction matches saved seed");
            h.assertTrue(SurvivalServerEvents.getDifficulty() == WaveDifficulty.HARD,
                    "ordinary restore also restores difficulty");
            System.out.println("SURVIVAL_RESTORE_ORDER_PASS actualHandler=true nextWave=" + differentWave);
        } finally {
            try {
                data.isEnabled = false;
                data.waveNumber = originalNext.number;
                data.randomSeed = originalSeed;
                data.difficulty = originalDifficulty;
                SurvivalServerEvents.loadWaveData(new ServerStartedEvent(h.getLevel().getServer()));
                SurvivalServerEvents.nextWave = originalNext;
            } finally {
                data.isEnabled = savedEnabled;
                data.waveNumber = savedNumber;
                data.randomSeed = savedSeed;
                data.difficulty = savedDifficulty;
                Wave.randomSeed = originalSeed;
                Wave.reseedWaves();
            }
        }
        h.succeed();
    }
}
