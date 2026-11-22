// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.OrdinaryAttackGate;
import com.solegendary.reignofnether.unit.goals.UnitBowAttackGoal;
import com.solegendary.reignofnether.unit.goals.UnitRangedAttackGoal;
import com.solegendary.reignofnether.unit.interfaces.RangedAttackerUnit;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** Gate only the ordinary release, preserving original navigation, windup and cooldown rules. */
@Mixin(value={UnitBowAttackGoal.class,UnitRangedAttackGoal.class},remap=false)
public abstract class DreadRoarRangedGoalMixin {
    @Redirect(method="tick",remap=true,at=@At(value="INVOKE",target="Lcom/solegendary/reignofnether/unit/interfaces/RangedAttackerUnit;performUnitRangedAttack(Lnet/minecraft/world/entity/LivingEntity;F)V",remap=false),require=1)
    private void dreamseekers$ordinaryRelease(RangedAttackerUnit source,LivingEntity target,float velocity) {
        OrdinaryAttackGate.ranged((LivingEntity)source,()->{
            // Only this unit's installed goal, at the actual release of its ordinary windup.
            // A direct performUnitRangedAttack, building shot or skill factory is not a trigger.
            if(source instanceof com.dreamseekers.unit.StoneSpiritThrowerUnit stone
                    &&stone.getAttackGoal()==(Object)this)
                stone.releaseGoalShot(target,velocity);
            else source.performUnitRangedAttack(target,velocity);
        });
    }
}
