package com.solegendary.reignofnether.fogofwar;

/** The timer is saved; visibility is derived from it and live map state, not a second saved bit. */
public final class CapitolFogRule {
    private CapitolFogRule() {}

    public static int nextTicks(int ticks, int threshold, boolean ownsBuilding, boolean ownsCapitol) {
        if (!ownsBuilding || ownsCapitol) return 0;
        return ticks >= threshold ? threshold : Math.max(0, ticks) + 1;
    }

    public static boolean reveals(int ticks, int threshold, boolean ownsBuilding, boolean ownsCapitol, boolean npc) {
        return ticks >= threshold && ownsBuilding && !ownsCapitol && !npc;
    }
}
