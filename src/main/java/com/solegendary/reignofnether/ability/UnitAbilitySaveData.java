package com.solegendary.reignofnether.ability;

import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;


/** Native ability counters use remaining ticks, not wall-clock time. Short casting effects are not saved. */
public final class UnitAbilitySaveData {
    public static final String KEY = "RoNUnitAbilities";
    private static final int VERSION = 1;
    // Preserve unrecognised data for this exact body without adding ForgeData copies or retaining worlds.
    private static final ReferenceQueue<Entity> COLLECTED = new ReferenceQueue<>();
    private static final Map<BodyKey, Tag> PRESERVED = new HashMap<>();
    private static final class BodyKey extends WeakReference<Entity> {
        private final int hash;
        BodyKey(Entity body) { super(body, COLLECTED); hash = System.identityHashCode(body); }
        @Override public int hashCode() { return hash; }
        @Override public boolean equals(Object other) {
            return this == other || other instanceof BodyKey key && get() != null && get() == key.get();
        }
    }
    private static synchronized Tag preserved(Entity body) {
        prune(); return PRESERVED.get(new BodyKey(body));
    }
    private static synchronized void preserve(Entity body, Tag tag) {
        prune();
        if (tag == null) PRESERVED.remove(new BodyKey(body));
        else PRESERVED.put(new BodyKey(body), tag.copy());
    }
    private static void prune() {
        for (var key = COLLECTED.poll(); key != null; key = COLLECTED.poll()) PRESERVED.remove(key);
    }
    private UnitAbilitySaveData() { }

    private static Map<String, Ability> abilities(Unit unit) {
        Map<String, Ability> result = new LinkedHashMap<>();
        for (Ability ability : unit.getAbilities().get()) {
            if (ability.action == null) continue;
            String key = ability.action.name();
            if (result.containsKey(key)) result.put(key, null); // Ambiguous identities are never guessed.
            else result.put(key, ability);
        }
        result.values().removeIf(java.util.Objects::isNull);
        return result;
    }
    private static boolean current(Tag raw) {
        return raw instanceof CompoundTag tag && tag.contains("Version", Tag.TAG_INT)
                && tag.getInt("Version") == VERSION;
    }
    private static float maxCooldown(Ability ability) {
        return Float.isFinite(ability.cooldownMax) ? Math.max(0, ability.cooldownMax) : 0;
    }
    private static float cooldown(float value, Ability ability) {
        float max = maxCooldown(ability);
        return Float.isNaN(value) ? max : Math.max(0, Math.min(max, value));
    }
    private static int charges(int value, Ability ability) {
        return Math.max(0, Math.min(Math.max(0, ability.maxCharges), value));
    }

    public static void save(Unit unit, CompoundTag root) {
        Tag preserved = preserved((Entity) unit);
        if (preserved != null && !current(preserved)) {
            root.put(KEY, preserved.copy()); // Future schemas and malformed outer payloads are opaque.
            return;
        }
        CompoundTag tag = preserved instanceof CompoundTag previous ? previous.copy() : new CompoundTag();
        tag.putInt("Version", VERSION);
        CompoundTag entries = tag.getCompound("Entries").copy();
        Map<String, Ability> known = abilities(unit);
        String selected = "";
        for (var entry : known.entrySet()) {
            Ability ability = entry.getValue();
            CompoundTag state = entries.getCompound(entry.getKey()).copy();
            state.putFloat("Cooldown", cooldown(unit.getCooldown(ability), ability));
            if (ability.usesCharges()) state.putInt("Charges", charges(unit.getAbilityCharges(ability), ability));
            entries.put(entry.getKey(), state);
            if (unit.hasAutocast(ability)) selected = entry.getKey();
        }
        tag.put("Entries", entries);
        // Unknown future selections remain opaque; a newer version can interpret them again.
        String previousSelection = tag.getString("Autocast");
        if (previousSelection.isEmpty() || known.containsKey(previousSelection)) tag.putString("Autocast", selected);
        root.put(KEY, tag);
    }

    /** Actual public ability toggles supersede an opaque selection only in the understood schema.
     * Loading uses Unit.setAutocast directly, so reading old data is never mistaken for user intent. */
    public static void autocastChanged(Unit unit) {
        Tag raw = preserved((Entity) unit);
        if (!current(raw)) return;
        CompoundTag tag = ((CompoundTag) raw).copy();
        String selected = "";
        for (var entry : abilities(unit).entrySet())
            if (unit.hasAutocast(entry.getValue())) selected = entry.getKey();
        tag.putString("Autocast", selected);
        preserve((Entity) unit, tag);
    }

    /** Invoked after hero rank/stat restoration, so current rank limits are authoritative. */
    public static void load(Unit unit, CompoundTag root) {
        Entity body = (Entity) unit;
        Tag raw = root.get(KEY);
        if (raw == null) { preserve(body, null); return; } // Old saves retain constructor defaults.
        preserve(body, raw);
        if (!current(raw)) return;
        CompoundTag tag = (CompoundTag) raw;
        CompoundTag entries = tag.getCompound("Entries");
        Map<String, Ability> known = abilities(unit);
        for (var entry : known.entrySet()) {
            if (!entries.contains(entry.getKey(), Tag.TAG_COMPOUND)) continue;
            CompoundTag state = entries.getCompound(entry.getKey());
            Ability ability = entry.getValue();
            if (state.contains("Cooldown", Tag.TAG_ANY_NUMERIC))
                unit.setCooldown(ability, cooldown(state.getFloat("Cooldown"), ability));
            if (ability.usesCharges() && state.contains("Charges", Tag.TAG_INT))
                unit.setCharges(ability, charges(state.getInt("Charges"), ability));
        }
        if (tag.contains("Autocast", Tag.TAG_STRING)) {
            String selected = tag.getString("Autocast");
            if (selected.isEmpty() || known.containsKey(selected)) unit.setAutocast(known.get(selected));
        }
    }
}
