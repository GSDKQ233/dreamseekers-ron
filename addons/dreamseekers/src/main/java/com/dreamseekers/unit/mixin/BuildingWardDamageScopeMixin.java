// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;

import com.dreamseekers.buildingability.DaisWardEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Brackets the genuine protected calculation in finally, including RoN's injected override.
 * No HEAD/RETURN depth counter, no new damage source and no new final damage handler.
 */
@Mixin(LivingEntity.class)
public abstract class BuildingWardDamageScopeMixin {
    @Shadow protected abstract void actuallyHurt(DamageSource source,float amount);
    @Redirect(method="hurt",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/world/damagesource/DamageSource;F)V"),require=1)
    private void dreamseekers$wardCalculation(LivingEntity victim,DamageSource source,float amount) {
        DaisWardEffects.hurt(victim,source,()->actuallyHurt(source,amount));
    }
}
