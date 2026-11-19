package com.solegendary.reignofnether.survival;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WaveFactionSequenceTest {
    private static final List<String> ROSTER = List.of("monsters", "piglins", "villagers");

    @Test void restoreAndClientProduceTheSameSequenceFromTheSavedSeed() {
        var original = WaveFactionSequence.generate(ROSTER, 123456789L, 30);
        WaveFactionSequence.generate(ROSTER, 987654321L, 30);
        var restored = WaveFactionSequence.generate(ROSTER, 123456789L, 30);
        assertEquals(original, restored);
        assertEquals(original, WaveFactionSequence.generate(ROSTER, 123456789L, 30));
        assertNotEquals(original, WaveFactionSequence.generate(ROSTER, 987654321L, 30));
        assertEquals(30, original.size());
        for (int number = 1; number <= 30; number++)
            assertEquals(original.get(number - 1), WaveFactionSequence.atWave(restored, number, "none"));
    }

    @Test void allSeedsUseOnlySuppliedSurvivalFactionsWithoutAdjacentRepeats() {
        for (long seed : new long[] {0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE, 42}) {
            var waves = WaveFactionSequence.generate(ROSTER, seed, 300);
            for (int i = 0; i < waves.size(); i++) {
                assertTrue(ROSTER.contains(waves.get(i)));
                if (i > 0) assertNotEquals(waves.get(i - 1), waves.get(i));
            }
        }
        var custom = List.of("addon:survival_only", "core:survival");
        var waves = WaveFactionSequence.generate(custom, 42, 30);
        assertTrue(waves.containsAll(custom));
        assertTrue(custom.containsAll(waves));
    }

    @Test void emptySingleDuplicateAndNullEntriesAreSafe() {
        assertEquals(List.of(), WaveFactionSequence.generate(List.of(), 42, 30));
        assertEquals("none", WaveFactionSequence.atWave(List.of(), 1, "none"));
        var single = WaveFactionSequence.generate(Arrays.asList("only", "only", null), 42, 30);
        assertEquals(30, single.size());
        assertTrue(single.stream().allMatch("only"::equals));
        var two = WaveFactionSequence.generate(Arrays.asList("a", "a", "b", null), 42, 30);
        for (int i = 1; i < two.size(); i++) assertNotEquals(two.get(i - 1), two.get(i));
        assertEquals(List.of(), WaveFactionSequence.generate(ROSTER, 42, 0));
        assertEquals(List.of(), WaveFactionSequence.generate(ROSTER, 42, -1));
    }

    @Test void waveNumbersClampWithoutOverflowAndSnapshotsCannotBeMutated() {
        var waves = WaveFactionSequence.generate(ROSTER, 42, 30);
        for (int number : new int[] {Integer.MIN_VALUE, -1, 0, 1})
            assertEquals(waves.get(0), WaveFactionSequence.atWave(waves, number, "none"));
        for (int number : new int[] {30, 31, Integer.MAX_VALUE})
            assertEquals(waves.get(29), WaveFactionSequence.atWave(waves, number, "none"));
        assertThrows(UnsupportedOperationException.class, () -> waves.add("other"));
    }
}
