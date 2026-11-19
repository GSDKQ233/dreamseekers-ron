// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.WindDash;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** Scope the real melee hurt call, after the core's original evasion check. No active skill is a strict pass-through. */
@Mixin(Mob.class)
public abstract class WindDashMeleeScopeMixin {
    @Redirect(method="doHurtTarget",at=@At(value="INVOKE",
            target="Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"),require=1)
    private boolean dreamseekers$actualMeleeHit(Entity target,DamageSource source,float amount) {
        return com.dreamseekers.skill.OrdinaryAttackGate.finalMelee((Mob)(Object)this,target,()->com.dreamseekers.skill.ShadowCorrosionTouch.hurtFromMob((Mob)(Object)this,target,source,amount,
                ()->com.dreamseekers.skill.BeetleAdvance.hurtFromMob((Mob)(Object)this,target,source,amount,
                ()->WindDash.hurtFromMob((Mob)(Object)this,target,source,amount))));
    }
}
