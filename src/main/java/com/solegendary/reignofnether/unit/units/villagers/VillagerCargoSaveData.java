package com.solegendary.reignofnether.unit.units.villagers;

import com.solegendary.reignofnether.resources.UnitCargoSaveData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Entity-side half of the native villager save. Priced cargo belongs to the account snapshot. */
final class VillagerCargoSaveData {
    private static final String KEY = "RoNVillagerUnvaluedCargo";

    private VillagerCargoSaveData() {}

    static void write(List<ItemStack> cargo, CompoundTag entity) {
        ListTag saved = UnitCargoSaveData.write(cargo);
        if (saved.isEmpty()) entity.remove(KEY);
        else entity.put(KEY, saved);
    }

    static void read(List<ItemStack> cargo, CompoundTag entity, boolean liveEntity) {
        UnitCargoSaveData.read(cargo, entity.getList(KEY, Tag.TAG_COMPOUND), liveEntity);
    }
}
