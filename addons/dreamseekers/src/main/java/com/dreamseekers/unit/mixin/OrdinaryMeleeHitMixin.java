// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.OrdinarySkillHit;
import com.solegendary.reignofnether.unit.goals.AbstractMeleeAttackUnitGoal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(value=AbstractMeleeAttackUnitGoal.class,remap=false)
public abstract class OrdinaryMeleeHitMixin {
    @Redirect(method="checkAndPerformAttack",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Mob;doHurtTarget(Lnet/minecraft/world/entity/Entity;)Z",remap=true),require=1)
    private boolean dreamseekers$ordinaryPrimary(Mob attacker,Entity target) { return com.dreamseekers.skill.NativeOrdinaryDebtHit.goal((AbstractMeleeAttackUnitGoal)(Object)this,attacker,target,()->OrdinarySkillHit.melee(attacker,target)); }
}
