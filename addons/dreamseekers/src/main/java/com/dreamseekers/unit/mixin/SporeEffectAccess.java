// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(MobEffectInstance.class)
public interface SporeEffectAccess {
    @Accessor("hiddenEffect") MobEffectInstance dreamseekers$hiddenEffect();
    @Accessor(value="curativeItems",remap=false) java.util.List<net.minecraft.world.item.ItemStack> dreamseekers$curativeItems();
}
