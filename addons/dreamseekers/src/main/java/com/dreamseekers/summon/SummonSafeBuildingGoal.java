package com.dreamseekers.summon;

import com.solegendary.reignofnether.unit.goals.MeleeAttackBuildingGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;

/** Exact temporary Beetle guard. Non-summoned Beetles retain the original native goal semantics. */
public final class SummonSafeBuildingGoal extends MeleeAttackBuildingGoal {
    public SummonSafeBuildingGoal(Mob mob){super(mob);}
    @Override public void setBuildingTarget(BlockPos pos){if(!BoundedSummons.canAct(mob)){stopAttacking();return;}super.setBuildingTarget(pos);}
    @Override public boolean canUse(){return BoundedSummons.canAct(mob)&&super.canUse();}
    @Override public boolean canContinueToUse(){return BoundedSummons.canAct(mob)&&super.canContinueToUse();}
    @Override public void tick(){if(!BoundedSummons.canAct(mob)){stopAttacking();return;}super.tick();}
    @Override protected void doBuildingAttack(){
        if(!BoundedSummons.canAct(mob)){stopAttacking();return;}
        if(!BoundedSummons.isTemporary(mob)){super.doBuildingAttack();return;}
        if(BoundedSummons.attackBuilding(mob,buildingTarget))
            ticksToNextBlockBreak=(int)((com.solegendary.reignofnether.unit.interfaces.AttackerUnit)mob).getAttackCooldown();
    }
}
