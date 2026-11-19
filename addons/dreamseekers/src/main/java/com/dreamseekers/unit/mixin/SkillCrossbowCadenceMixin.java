// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;

import com.dreamseekers.skill.SkillEffects;
import com.solegendary.reignofnether.unit.goals.UnitCrossbowAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Only live addon temporary APS effects adapt the core's constructor-cached crossbow cooldown.
 * The original cached maximum, item charge time and random windup are never modified.
 */
@Mixin(UnitCrossbowAttackGoal.class)
public abstract class SkillCrossbowCadenceMixin {
    @Shadow(remap=false) @Final private Monster mob;
    @Shadow(remap=false) private int attackCooldownMax;

    @Redirect(method="setToMaxAttackCooldown",remap=false,at=@At(value="FIELD",
            target="Lcom/solegendary/reignofnether/unit/goals/UnitCrossbowAttackGoal;attackCooldownMax:I",opcode=180,remap=false),require=1)
    private int dreamseekers$resetCooldown(UnitCrossbowAttackGoal<?> goal) {
        return SkillEffects.fixedAttackCooldown(mob,attackCooldownMax);
    }
    @Redirect(method="tickChargeCrossbow",remap=false,at=@At(value="FIELD",
            target="Lcom/solegendary/reignofnether/unit/goals/UnitCrossbowAttackGoal;attackCooldownMax:I",opcode=180,remap=false),require=1)
    private int dreamseekers$chargedCooldown(UnitCrossbowAttackGoal<?> goal) {
        return SkillEffects.fixedAttackCooldown(mob,attackCooldownMax);
    }
    // stop overrides Minecraft Goal.stop and must retain normal method remapping in production.
    @Redirect(method="stop",at=@At(value="FIELD",
            target="Lcom/solegendary/reignofnether/unit/goals/UnitCrossbowAttackGoal;attackCooldownMax:I",opcode=180,remap=false),require=1)
    private int dreamseekers$stoppedCooldown(UnitCrossbowAttackGoal<?> goal) {
        return SkillEffects.fixedAttackCooldown(mob,attackCooldownMax);
    }
}
