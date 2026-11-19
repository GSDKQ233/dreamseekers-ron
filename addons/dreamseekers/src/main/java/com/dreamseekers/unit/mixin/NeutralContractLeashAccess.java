package com.dreamseekers.unit.mixin;
import net.minecraft.world.entity.Mob;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Read-only native pending leash metadata; never restores or consumes player assets. */
@Mixin(Mob.class)
public interface NeutralContractLeashAccess {
    @Accessor("leashInfoTag") CompoundTag dreamseekers$contractPendingLeash();
}
