package com.solegendary.reignofnether.building.production;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import javax.annotation.Nullable;

/** A structure's spawn-egg NBT belongs to one recipe, not all recipes for this species. */
final class RecipeSpawnTemplate {
    private final EntityType<?> type;
    private final CompoundTag template;

    RecipeSpawnTemplate(@Nullable EntityType<?> type, @Nullable CompoundTag template) {
        this.type = type;
        this.template = template == null ? null : template.copy();
    }

    @Nullable CompoundTag copyFor(EntityType<?> actual) {
        return actual == type && template != null ? template.copy() : null;
    }
}
