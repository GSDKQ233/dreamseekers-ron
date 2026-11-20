package com.solegendary.reignofnether.survival.spawners;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Attempt budget of one spawn point search. World-free so the budget can be tested without a server. */
final class SpawnPointSearch {
    private SpawnPointSearch() {}

    /**
     * Collects up to {@code amount} points. Every point gets {@code attemptsPerPoint} samples of its own;
     * the search ends early once more than {@code maxFailedPoints} points have used theirs up.
     *
     * @param sample one candidate, or null when the candidate is not a valid spawn point
     */
    static <T> List<T> collect(int amount, int attemptsPerPoint, int maxFailedPoints, Supplier<T> sample) {
        List<T> points = new ArrayList<>();
        int failedPoints = 0;
        while (points.size() < amount && failedPoints <= maxFailedPoints) {
            T point = null;
            for (int attempt = 0; attempt < attemptsPerPoint && point == null; attempt++)
                point = sample.get();
            if (point == null)
                failedPoints++;
            else
                points.add(point);
        }
        return points;
    }
}
