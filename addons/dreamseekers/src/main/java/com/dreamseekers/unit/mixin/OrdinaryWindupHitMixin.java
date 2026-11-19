// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.OrdinarySkillHit;
import com.solegendary.reignofnether.unit.goals.MeleeWindupAttackUnitGoal;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
@Mixin(value=MeleeWindupAttackUnitGoal.class,remap=false)
public abstract class OrdinaryWindupHitMixin {
    @Redirect(method="checkAndPerformAttackWithWindup",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Mob;doHurtTarget(Lnet/minecraft/world/entity/Entity;)Z",remap=true),require=1)
    private boolean dreamseekers$ordinaryPrimary(Mob attacker,Entity target) { return OrdinarySkillHit.melee(attacker,target); }
}
