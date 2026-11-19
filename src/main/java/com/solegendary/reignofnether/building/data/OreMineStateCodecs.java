package com.solegendary.reignofnether.building.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** NBT only: no building registry/class initialization needed to test migration and invalid payloads. */
public final class OreMineStateCodecs {
    private OreMineStateCodecs() {}
    /** quarterTicks takes precedence over halfTicks, which takes precedence over elapsedTicks.
     * Presence of an invalid canonical field restores zero, never falls back to a larger obsolete value. */
    public static OreProductionState readProduction(CompoundTag tag) {
        String owner = tag.getString("owner");
        if (tag.contains("quarterTicks"))
            return OreProductionState.fromSavedQuarterTicks(counter(tag, "quarterTicks", OreProductionState.QUARTER_TICKS_PER_CYCLE - 1), owner);
        if (tag.contains("halfTicks"))
            return OreProductionState.fromSavedHalfTicks(counter(tag, "halfTicks", OreProductionState.INTERVAL_TICKS * 2 - 1), owner);
        return OreProductionState.fromSaved(counter(tag, "elapsedTicks", OreProductionState.INTERVAL_TICKS - 1), owner);
    }
    public static CompoundTag writeProduction(OreProductionState state) {
        CompoundTag tag = new CompoundTag();
        tag.putString("owner", state.getOwner());
        tag.putInt("quarterTicks", state.getQuarterTicks());
        tag.putInt("elapsedTicks", state.getElapsedTicks()); // legacy diagnostic, not authoritative fractional state
        return tag;
    }
    /** Obsolete soul fields, if any, are ignored: monster-local acceleration was explicitly removed. */
    public static OreMineTraitState readTrait(CompoundTag tag) {
        return OreMineTraitState.fromSaved(tag.getString("owner"),
                counter(tag, "overloadTicks", OreMineTraitState.OVERLOAD_TICKS),
                counter(tag, "cooldownTicks", OreMineTraitState.OVERLOAD_COOLDOWN_TICKS));
    }
    public static CompoundTag writeTrait(OreMineTraitState state) {
        CompoundTag tag = new CompoundTag();
        tag.putString("owner", state.getOwner());
        tag.putInt("overloadTicks", state.getOverloadTicks());
        tag.putInt("cooldownTicks", state.getCooldownTicks());
        return tag;
    }
    private static int counter(CompoundTag tag, String key, int max) {
        Tag value = tag.get(key);
        if (value == null || value.getId() < Tag.TAG_BYTE || value.getId() > Tag.TAG_LONG) return 0;
        return (int) Math.max(0L, Math.min((long) max, tag.getLong(key)));
    }
}
