// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;

import com.dreamseekers.skill.SkillEffects;
import com.solegendary.reignofnether.unit.units.villagers.MilitiaUnit;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Militia's sword/bow method uses constants rather than the APS attribute. Adapt its final
 * legacy cooldown only for active addon temporary APS; the native getter already includes the
 * managed/native slow maximum. Never send that return through a cached-baseline slow adapter.
 */
@Mixin(value=MilitiaUnit.class,remap=false)
public abstract class SkillMilitiaCadenceMixin {
    @Inject(method="getAttackCooldown",at=@At("RETURN"),cancellable=true,require=1)
    private void dreamseekers$modeCooldown(CallbackInfoReturnable<Float> callback) {
        float legacy=callback.getReturnValueF();
        float adjusted=SkillEffects.militiaAttackCooldown((LivingEntity)(Object)this,legacy);
        if(adjusted!=legacy)callback.setReturnValue(adjusted);
    }
}
