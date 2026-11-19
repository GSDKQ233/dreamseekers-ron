// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;

import com.dreamseekers.skill.SkillEffects;
import com.solegendary.reignofnether.unit.units.monsters.SlimeUnit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Opt-in collision-attack cooldown only; no vanilla/global slime mechanics or cached stat changes. */
@Mixin(SlimeUnit.class)
public abstract class SkillSlimePushCadenceMixin {
    @Redirect(method="push(Lnet/minecraft/world/entity/Entity;)V",at=@At(value="FIELD",
            target="Lcom/solegendary/reignofnether/unit/units/monsters/SlimeUnit;PUSH_ATTACK_CD_MAX:I",opcode=180,remap=false),require=1)
    private int dreamseekers$pushCooldown(SlimeUnit slime) {
        return SkillEffects.fixedAttackCooldown(slime,slime.PUSH_ATTACK_CD_MAX);
    }
}
