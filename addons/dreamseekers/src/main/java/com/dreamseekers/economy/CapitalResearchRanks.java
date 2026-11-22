// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.economy;

import java.util.function.IntPredicate;

/** The capital's two numeric advancements. No building count or tick ever grants a rank. */
public final class CapitalResearchRanks {
    public static final int MAX_RANK = 3;

    private CapitalResearchRanks() {}

    /** A legacy completion key is rank one. Duplicates and keys above a gap add no effect. */
    public static int completed(IntPredicate ownsRank) {
        int rank = 0;
        while (rank < MAX_RANK && ownsRank.test(rank + 1)) rank++;
        return rank;
    }

    public static boolean isNext(int completed, int rank) {
        return completed >= 0 && completed < MAX_RANK && rank == completed + 1;
    }

    /** At the cap keep the last rank in its original slot, visibly disabled. */
    public static boolean displayed(int completed, int rank) {
        return isNext(completed, rank) || completed == MAX_RANK && rank == MAX_RANK;
    }

    public static int conversionPercent(int completed) {
        return 5 + 5 * checked(completed);
    }

    public static int ascendancyPercent(int completed) {
        return 15 * checked(completed);
    }

    public static double ascendancyBonus(int completed) {
        return ascendancyPercent(completed) / 100d;
    }

    private static int checked(int completed) {
        if (completed < 0 || completed > MAX_RANK) throw new IllegalArgumentException("Invalid capital research rank: " + completed);
        return completed;
    }
}
