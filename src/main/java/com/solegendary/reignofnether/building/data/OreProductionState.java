package com.solegendary.reignofnether.building.data;

/** Per-building production credit in quarter ticks; server eligibility/awards live outside this class. */
public final class OreProductionState {
    public static final int INTERVAL_TICKS = 40;
    public static final int QUARTER_TICKS_PER_CYCLE = INTERVAL_TICKS * 4;
    public static final int MAX_CREDIT_PER_TICK = 12;
    private int quarterTicks;
    private String owner = "";

    /** Existing saves count whole effective ticks. Clamp before multiplying, never after overflow. */
    public static OreProductionState fromSaved(int elapsedTicks, String owner) {
        return fromSavedQuarterTicks(clamp(elapsedTicks, INTERVAL_TICKS - 1) * 4, owner);
    }
    /** The unshipped half-credit prototype can be migrated without losing its half tick. */
    public static OreProductionState fromSavedHalfTicks(int halfTicks, String owner) {
        return fromSavedQuarterTicks(clamp(halfTicks, INTERVAL_TICKS * 2 - 1) * 2, owner);
    }
    public static OreProductionState fromSavedQuarterTicks(int quarterTicks, String owner) {
        OreProductionState state = new OreProductionState();
        state.owner = normalizeOwner(owner);
        state.quarterTicks = state.owner.isEmpty() ? 0 : clamp(quarterTicks, QUARTER_TICKS_PER_CYCLE - 1);
        return state;
    }
    public int tick(String currentOwner, boolean active) { return advance(currentOwner, active, 4); }
    /** Return completed cycles (0 or 1). Inactivity pauses credit, but owner changes always clear it. */
    public int advance(String currentOwner, boolean active, int credit) {
        String next = normalizeOwner(currentOwner);
        if (!owner.equals(next)) { owner = next; quarterTicks = 0; }
        if (!active || owner.isEmpty()) return 0;
        quarterTicks += clamp(credit, MAX_CREDIT_PER_TICK);
        if (quarterTicks >= QUARTER_TICKS_PER_CYCLE) {
            quarterTicks -= QUARTER_TICKS_PER_CYCLE;
            return 1;
        }
        return 0;
    }
    public void reset() { quarterTicks = 0; }
    public int getQuarterTicks() { return quarterTicks; }
    /** Legacy diagnostic field only. New saves use quarterTicks to retain the fractional part. */
    public int getElapsedTicks() { return quarterTicks / 4; }
    public int getHalfTicks() { return quarterTicks / 2; }
    public String getOwner() { return owner; }
    private static int clamp(int value, int upper) { return Math.max(0, Math.min(value, upper)); }
    private static String normalizeOwner(String value) { return value == null || value.isBlank() ? "" : value; }
}
