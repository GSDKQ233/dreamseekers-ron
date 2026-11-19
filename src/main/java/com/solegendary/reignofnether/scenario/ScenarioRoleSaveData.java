package com.solegendary.reignofnether.scenario;

import com.solegendary.reignofnether.ReignOfNether;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nonnull;
import java.util.ArrayList;

public class ScenarioRoleSaveData extends SavedData {

    public final ArrayList<ScenarioRole> scenarioRoleSaves = ScenarioRole.defaults();

    private boolean restoredAnyRole;

    boolean hadSavedRoles() { return restoredAnyRole; }

    private static ScenarioRoleSaveData create() {
        return new ScenarioRoleSaveData();
    }

    @Nonnull
    public static ScenarioRoleSaveData getInstance(LevelAccessor level) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return create();
        }
        return fromStorage(server.overworld().getDataStorage());
    }

    static ScenarioRoleSaveData fromStorage(DimensionDataStorage storage) {
        return storage.computeIfAbsent(ScenarioRoleSaveData::load, ScenarioRoleSaveData::create, "saved-scenario-data");
    }

    public static ScenarioRoleSaveData load(CompoundTag tag) {
        ReignOfNether.LOGGER.info("ScenarioSaveData.load");

        ScenarioRoleSaveData data = create();
        ListTag saved = tag.getList("scenarioRoles", Tag.TAG_COMPOUND);
        boolean[] restored = new boolean[ScenarioRole.COUNT];
        for (int i = 0; i < saved.size(); i++) {
            CompoundTag record = saved.getCompound(i);
            if (!record.contains("index", Tag.TAG_INT)) continue;
            int index = record.getInt("index");
            if (index < 0 || index >= restored.length || restored[index]) continue;
            // Index is identity, not position in an on-disk list. Retain the first valid
            // record (the old lookup also used the first match); absent slots stay fresh.
            data.scenarioRoleSaves.set(index, ScenarioRole.getFromSave(index, record));
            restored[index] = true;
            data.restoredAnyRole = true;
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        this.scenarioRoleSaves.forEach(r -> {
            r.packNbt();
            list.add(r.nbt.copy());
        });
        tag.put("scenarioRoles", list);
        return tag;
    }

    public void save() {
        this.setDirty();
    }
}
