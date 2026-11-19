package com.solegendary.reignofnether.hud;

import java.util.List;
import java.util.function.Predicate;

/** Renderer-independent selected HUD identity. Render callbacks are never selection events. */
public final class HudUnitSelection {
    private HudUnitSelection() {}

    public static boolean sameIdentityOrder(List<?> previous, List<?> current) {
        if (previous.size() != current.size()) return false;
        for (int i = 0; i < current.size(); i++) {
            if (previous.get(i) != current.get(i)) return false;
        }
        return true;
    }

    public static <T> T resolve(List<T> sortedSelection, T current, Predicate<T> liveInCurrentWorld) {
        T first = null;
        for (T candidate : sortedSelection) {
            if (candidate == null || !liveInCurrentWorld.test(candidate)) continue;
            if (candidate == current) return current;
            if (first == null) first = candidate;
        }
        return first;
    }
}
