// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.OrdinaryAttackGate;
import com.solegendary.reignofnether.unit.goals.RangedAttackBuildingGoal;
import com.solegendary.reignofnether.unit.goals.RangedAttackGroundGoal;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** Existing ordinary coordinate attack only; unrelated ground-targeted spells do not enter. */
@Mixin(value={RangedAttackBuildingGoal.class,RangedAttackGroundGoal.class},remap=false)
public abstract class DreadRoarGroundGoalMixin {
    @Redirect(method="tick",remap=true,at=@At(value="INVOKE",target="Lcom/solegendary/reignofnether/unit/interfaces/RangedAttackerUnit;performUnitRangedAttack(DDDF)V",remap=false),require=1)
    private void dreamseekers$ordinaryRelease(RangedAttackerUnit source,double x,double y,double z,float velocity) {
        OrdinaryAttackGate.ranged((LivingEntity)source,()->source.performUnitRangedAttack(x,y,z,velocity));
    }
}
