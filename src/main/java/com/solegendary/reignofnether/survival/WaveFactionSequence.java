package com.solegendary.reignofnether.survival;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** Pure seed-driven selection from a stable, ordered survival roster. */
final class WaveFactionSequence {
    private WaveFactionSequence() {}

    static <T> List<T> generate(List<T> roster, long seed, int count) {
        List<T> choices = roster.stream().filter(Objects::nonNull).distinct().toList();
        if (choices.isEmpty() || count <= 0)
            return List.of();
        Random random = new Random(seed);
        List<T> sequence = new ArrayList<>(count);
        int previous = -1;
        for (int i = 0; i < count; i++) {
            int next = random.nextInt(choices.size() - (previous >= 0 && choices.size() > 1 ? 1 : 0));
            if (choices.size() > 1 && previous >= 0 && next >= previous)
                next++;
            sequence.add(choices.get(next));
            previous = next;
        }
        return List.copyOf(sequence);
    }

    static <T> T atWave(List<T> sequence, int number, T emptyValue) {
        if (sequence.isEmpty())
            return emptyValue;
        // Clamp before subtracting, including Integer.MIN_VALUE from malformed input.
        return sequence.get(Math.max(1, Math.min(number, sequence.size())) - 1);
    }
}
