// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;

import com.dreamseekers.skill.SkillEffects;
import com.solegendary.reignofnether.unit.goals.MeleeAttackSlimeUnitGoal;
import com.solegendary.reignofnether.unit.units.monsters.SlimeUnit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The landing attack and the collision attack share the same temporary-APS cached-CD adapter. */
@Mixin(value=MeleeAttackSlimeUnitGoal.class,remap=false)
public abstract class SkillSlimeLandingCadenceMixin {
    @Redirect(method="checkAndPerformAttack",at=@At(value="FIELD",
            target="Lcom/solegendary/reignofnether/unit/units/monsters/SlimeUnit;PUSH_ATTACK_CD_MAX:I",opcode=180,remap=false),require=1)
    private int dreamseekers$landingCooldown(SlimeUnit slime) {
        return SkillEffects.fixedAttackCooldown(slime,slime.PUSH_ATTACK_CD_MAX);
    }
}
