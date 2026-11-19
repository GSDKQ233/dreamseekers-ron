package com.solegendary.reignofnether.sandbox;

import java.util.TreeSet;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

final class SandboxEditorSaveData extends SavedData {
    final SandboxEditorPolicy policy = new SandboxEditorPolicy();

    static SandboxEditorSaveData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                SandboxEditorSaveData::load, SandboxEditorSaveData::new, "reignofnether-sandbox-editors");
    }

    static SandboxEditorSaveData load(CompoundTag tag) {
        SandboxEditorSaveData data = new SandboxEditorSaveData();
        ListTag entries = tag.getList("editors", Tag.TAG_COMPOUND);
        for (int i = 0; i < entries.size() && i < SandboxEditorPolicy.MAX_GRANTS; i++) {
            CompoundTag entry = entries.getCompound(i);
            if (entry.hasUUID("uuid")) data.policy.grant(entry.getUUID("uuid"));
        }
        return data;
    }

    boolean change(UUID id, boolean grant) {
        boolean changed = grant ? policy.grant(id) : policy.revoke(id);
        if (changed) setDirty();
        return changed;
    }

    @Override public CompoundTag save(CompoundTag tag) {
        ListTag entries = new ListTag();
        for (UUID id : new TreeSet<>(policy.snapshot())) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("uuid", id);
            entries.add(entry);
        }
        tag.put("editors", entries);
        return tag;
    }
}
