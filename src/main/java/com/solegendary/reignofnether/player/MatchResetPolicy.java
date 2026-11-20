package com.solegendary.reignofnether.player;

import java.util.function.BooleanSupplier;

/** Snapshot of the authoritative mode BEFORE the seats are released. "Hard" never erases an active editor map's buildings. */
public record MatchResetPolicy(boolean hard, boolean preserveMap) {
    private static final long ORDINARY = 1L, PRESERVE_MAP = 2L;

    public boolean removeUnit(boolean unanchoredUnit) { return hard || (!preserveMap && unanchoredUnit); }
    public boolean removeBuilding(boolean destroyOnReset) { return !preserveMap && (hard || destroyOnReset); }
    public long wireValue() { return preserveMap ? PRESERVE_MAP : ORDINARY; }

    // Zero is the pre-repair3 packet. Only that compatibility path needs the local viewer's mode.
    public static MatchResetPolicy fromWire(boolean hard, long value, BooleanSupplier legacyMode) {
        // An unknown future instruction must not erase the client's map view.
        return new MatchResetPolicy(hard, value == 0L ? legacyMode.getAsBoolean() : value != ORDINARY);
    }
}
