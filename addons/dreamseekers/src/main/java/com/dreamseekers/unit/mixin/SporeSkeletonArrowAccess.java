// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
/** Access the real declaration while retaining ordinary virtual dispatch on the actual skeleton. */
@Mixin(AbstractSkeleton.class)
public interface SporeSkeletonArrowAccess {
    @Invoker("getArrow") AbstractArrow dreamseekers$sporeArrow(ItemStack ammunition,float power);
}
