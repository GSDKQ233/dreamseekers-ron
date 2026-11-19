package com.solegendary.reignofnether.alliance;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** World-scoped committed alliances. Pending invitations and control grants are session-only. */
public final class AllianceSaveData extends SavedData {
    private final Map<String, Set<String>> alliances = new HashMap<>();

    public static AllianceSaveData getInstance(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                AllianceSaveData::load, AllianceSaveData::new, "saved-alliance-data");
    }

    public static AllianceSaveData load(CompoundTag tag) {
        AllianceSaveData data = new AllianceSaveData();
        ListTag pairs = tag.getList("alliances", Tag.TAG_COMPOUND);
        for (Tag entry : pairs) {
            CompoundTag pair = (CompoundTag) entry;
            String first = pair.getString("first"), second = pair.getString("second");
            if (first.isEmpty() || second.isEmpty() || first.equals(second)) continue;
            data.alliances.computeIfAbsent(first, key -> new HashSet<>()).add(second);
            data.alliances.computeIfAbsent(second, key -> new HashSet<>()).add(first);
        }
        return data;
    }

    /** Defensive snapshot: no caller can mutate persisted state without marking it dirty. */
    public Map<String, Set<String>> snapshot() {
        Map<String, Set<String>> result = new HashMap<>();
        alliances.forEach((owner, allies) -> result.put(owner, Set.copyOf(allies)));
        return Map.copyOf(result);
    }

    void replace(Map<String, Set<String>> current) {
        if (alliances.equals(current)) return;
        alliances.clear();
        current.forEach((owner, allies) -> alliances.put(owner, new HashSet<>(allies)));
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag pairs = new ListTag();
        // Stable undirected encoding: write each committed pair exactly once.
        for (String first : new TreeSet<>(alliances.keySet())) {
            for (String second : new TreeSet<>(alliances.get(first))) {
                if (first.compareTo(second) >= 0) continue;
                CompoundTag pair = new CompoundTag();
                pair.putString("first", first);
                pair.putString("second", second);
                pairs.add(pair);
            }
        }
        tag.put("alliances", pairs);
        return tag;
    }
}
