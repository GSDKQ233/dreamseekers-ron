package com.solegendary.reignofnether.building.production;

import java.util.OptionalInt;
import java.util.function.IntPredicate;

/** One loaded vertical column. Heights are vanilla's inclusive bottom / exclusive top. */
public final class IndoorSpawnSearch {
    public static final int MAX_PROBES = 128;
    private IndoorSpawnSearch() {}

    public static OptionalInt find(int startY, int bottom, int top, IntPredicate hasFloorAt) {
        if (startY <= bottom || startY >= top) return OptionalInt.empty();
        int y = startY;
        for (int probes = 0; probes < MAX_PROBES && y > bottom; probes++, y--) {
            if (hasFloorAt.test(y - 1)) return OptionalInt.of(y);
        }
        return OptionalInt.empty();
    }
}
