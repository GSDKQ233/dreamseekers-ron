// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.WindDash;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** The frame is cleared by the caller's finally, not a fragile HEAD/RETURN depth counter.
 * Only recursive reuse of this active skill's exact DamageSource and victim is rejected.
 */
@Mixin(value=LivingEntity.class,priority=1500)
public abstract class WindDashHurtEntryMixin {
    @Inject(method="hurt",at=@At("HEAD"),cancellable=true,require=1)
    private void dreamseekers$oneEntry(DamageSource source,float amount,CallbackInfoReturnable<Boolean> callback) {
        if(com.dreamseekers.skill.AaroncosCombat.isStrike(source)) {
            if(com.dreamseekers.skill.AaroncosCombat.rejectRepeatedHurt((LivingEntity)(Object)this,source))callback.setReturnValue(false);
            return;
        }
        if(com.dreamseekers.skill.ShadowGolemStrike.isStrike(source)) {
            if(com.dreamseekers.skill.ShadowGolemStrike.rejectRepeatedHurt((LivingEntity)(Object)this,source))callback.setReturnValue(false);
            return; // do not bind this skill DS to an outer ordinary melee receipt
        }
        com.dreamseekers.skill.SnailShellFormation.observeHurt((LivingEntity)(Object)this,source);
        com.dreamseekers.skill.OrdinarySkillHit.observeHurt((LivingEntity)(Object)this,source);
        com.dreamseekers.skill.FriendlyDebtContract.observeHurt((LivingEntity)(Object)this,source);
        com.dreamseekers.skill.ShadowHandGrip.observeHurt((LivingEntity)(Object)this,source);
        com.dreamseekers.skill.BeetleAdvance.observeHurt((LivingEntity)(Object)this,source);
        com.dreamseekers.skill.ShadowCorrosionTouch.observeHurt((LivingEntity)(Object)this,source);
        if(com.dreamseekers.skill.AaroncosVortex.rejectRepeatedHurt((LivingEntity)(Object)this,source)
                ||com.dreamseekers.skill.AshEmberLanding.rejectRepeatedHurt((LivingEntity)(Object)this,source)
                ||com.dreamseekers.skill.FriendlyDebtContract.rejectSettlement((LivingEntity)(Object)this,source)
                ||com.dreamseekers.skill.SquealSuppressionWave.rejectRepeatedHurt((LivingEntity)(Object)this,source)
                ||com.dreamseekers.skill.StoneHeavyShot.rejectRepeatedHurt((LivingEntity)(Object)this,source)
                ||WindDash.rejectRepeatedHurt((LivingEntity)(Object)this,source)
                ||com.dreamseekers.skill.WindFangDamage.rejectRepeatedHurt((LivingEntity)(Object)this,source))callback.setReturnValue(false);
    }
    // Vanilla hurt applies an ordinary impulse for MOB_ATTACK. Preserve all other damage
    // behavior while suppressing ONLY this independent skill's own knockback call.
    @org.spongepowered.asm.mixin.injection.Redirect(method="hurt",at=@At(value="INVOKE",
            target="Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"),require=1)
    private void dreamseekers$noStrikeImpulse(LivingEntity victim,double strength,double x,double z,
            DamageSource source,float amount) {
        if(!com.dreamseekers.skill.AaroncosVortex.isPulse(source)&&!com.dreamseekers.skill.ShadowGolemStrike.isStrike(source)&&!com.dreamseekers.skill.AaroncosCombat.isStrike(source))victim.knockback(strength,x,z);
    }
}
