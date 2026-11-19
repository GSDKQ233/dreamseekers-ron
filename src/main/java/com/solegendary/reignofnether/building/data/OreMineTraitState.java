package com.solegendary.reignofnether.building.data;

/** Placement-local overload budget. There is deliberately no monster soul pool. No fee authority here. */
public final class OreMineTraitState {
    public static final int MAX_WORKERS = 6;
    public static final int OVERLOAD_FOOD_COST = 20;
    public static final int OVERLOAD_TICKS = 400;
    public static final int OVERLOAD_COOLDOWN_TICKS = 1200;
    private String owner = "";
    private int overloadTicks, cooldownTicks;
    // Placement capture revision is a runtime guard, not a saved revision from another JVM.
    private transient boolean revisionBound;
    private transient long revision;

    public static OreMineTraitState fromSaved(String owner, int overloadTicks, int cooldownTicks) {
        OreMineTraitState state = new OreMineTraitState();
        state.owner = normalize(owner);
        state.overloadTicks = state.owner.isEmpty() ? 0 : clamp(overloadTicks, OVERLOAD_TICKS);
        state.cooldownTicks = clamp(cooldownTicks, OVERLOAD_COOLDOWN_TICKS);
        return state;
    }
    /** True means the caller must also discard production credit. First bind preserves legacy credit.
     * A loaded nonempty saved owner is still checked, while a fresh trait state does not erase an old cycle.
     * BuildingPlacement currently starts its nonpersistent revision at zero. A nonzero first revision
     * therefore proves a capture already occurred before binding, including A-B-A. If that revision
     * becomes persistent in future, this load rule must be migrated with it. */
    public boolean observe(String currentOwner, long captureRevision) {
        String next = normalize(currentOwner);
        boolean changed = ((revisionBound || !owner.isEmpty()) && !owner.equals(next))
                || (revisionBound && revision != captureRevision)
                || (!revisionBound && captureRevision != 0);
        if (changed) overloadTicks = 0;
        owner = next; revision = captureRevision; revisionBound = true;
        return changed;
    }
    public static int workerQuarterCredit(int workers) {
        int count = clamp(workers, MAX_WORKERS);
        return 4 + 2 * Math.min(count, 2) + Math.max(0, count - 2);
    }
    /** Called once per server placement tick. Cooldown advances even while production is ineligible. */
    public int tick(boolean active, int workers, boolean piglin) {
        if (cooldownTicks > 0) cooldownTicks--;
        if (!active || owner.isEmpty() || !revisionBound || revision < 0) return 0;
        if (piglin) {
            if (overloadTicks > 0) { overloadTicks--; return 8; }
            return 4;
        }
        return workerQuarterCredit(workers);
    }
    /** Caller must enforce authoritative sender/building/research/resource checks in the same server task. */
    public boolean canActivateOverload() {
        return !owner.isEmpty() && revisionBound && revision >= 0 && cooldownTicks == 0 && overloadTicks == 0;
    }
    /** State commit only, never deducts food. No stacking or refreshing an existing effect. */
    public boolean activateOverload() {
        if (!canActivateOverload()) return false;
        overloadTicks = OVERLOAD_TICKS; cooldownTicks = OVERLOAD_COOLDOWN_TICKS;
        return true;
    }
    public String getOwner() { return owner; }
    public int getOverloadTicks() { return overloadTicks; }
    public int getCooldownTicks() { return cooldownTicks; }
    private static int clamp(int value, int maximum) { return Math.max(0, Math.min(value, maximum)); }
    private static String normalize(String name) { return name == null || name.isBlank() ? "" : name; }
}
