// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;

import com.dreamseekers.unit.NativePinkChickenHooks;
import net.minecraft.world.level.LevelAccessor;
import net.pasterdream.entity.PinkChickenEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The original egg procedure receives only coordinates, not its source entity.
 * Guard its native call site instead of suppressing unrelated wild eggs nearby.
 */
@Mixin(PinkChickenEntity.class)
public abstract class PinkChickenEggMixin {
    @Redirect(method = "baseTick", at = @At(value = "INVOKE",
            target = "Lnet/pasterdream/procedures/PinkChickenPr0Procedure;execute(Lnet/minecraft/world/level/LevelAccessor;DDD)V",
            remap = false))
    private void dreamseekers$onlyWildEggs(LevelAccessor world, double x, double y, double z) {
        NativePinkChickenHooks.invokeNativeEgg((PinkChickenEntity) (Object) this, world, x, y, z);
    }
}
