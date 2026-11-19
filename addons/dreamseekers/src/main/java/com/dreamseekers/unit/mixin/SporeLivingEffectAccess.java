// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(LivingEntity.class)
public interface SporeLivingEffectAccess {
    @Invoker("onEffectRemoved") void dreamseekers$effectRemoved(MobEffectInstance effect);
}
