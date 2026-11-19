// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.solegendary.reignofnether.api.FinalUnitDamageHooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** Observe the exact native branch without changing its amplifier, arithmetic, effects or callbacks. */
@Mixin(LivingEntity.class)
public abstract class SkillNativeResistanceMixin {
    @Redirect(method="getDamageAfterMagicAbsorb",at=@At(value="INVOKE",target="Lnet/minecraft/world/effect/MobEffectInstance;getAmplifier()I"),require=1)
    private int dreamseekers$usedResistance(MobEffectInstance effect,DamageSource source,float amount) {
        int amplifier=effect.getAmplifier();
        FinalUnitDamageHooks.observeMagicResistance((LivingEntity)(Object)this,source,.2*(amplifier+1));
        return amplifier;
    }
}
