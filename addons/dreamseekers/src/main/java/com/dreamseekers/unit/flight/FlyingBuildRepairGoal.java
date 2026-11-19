package com.dreamseekers.unit.flight;

import com.solegendary.reignofnether.unit.goals.BuildRepairGoal;
import net.minecraft.world.entity.Mob;

public final class FlyingBuildRepairGoal extends BuildRepairGoal {
    public FlyingBuildRepairGoal(Mob mob) { super(mob); }

    @Override public boolean canUse() { return !com.dreamseekers.skill.FireflyRush.active(mob)&&super.canUse(); }
    @Override public boolean canContinueToUse() { return !com.dreamseekers.skill.FireflyRush.active(mob)&&super.canContinueToUse(); }
    @Override public void start() {
        if(com.dreamseekers.skill.FireflyRush.active(mob))return;
        super.start();com.dreamseekers.skill.FireflyRush.workPathStarted(mob,this);
    }
    @Override public void tick() {
        if(!com.dreamseekers.skill.FireflyRush.active(mob))super.tick();
    }

    @Override protected void calcMoveTarget() {
        if (getBuildingTarget() == null) { moveTarget = null; return; }
        if (moveTarget == null || !FlightWorkPositions.isClear(mob, moveTarget))
            moveTarget = FlightWorkPositions.building(mob, getBuildingTarget());
    }

    @Override public double getMinDistToRecalculateSqr() { return 1.5 * 1.5; }

    @Override public boolean isBuilding() {
        return !com.dreamseekers.skill.FireflyRush.active(mob) && super.isBuilding() && (mob.level().isClientSide() || FlightWorkPositions.canWorkAtBuilding(mob, getBuildingTarget(), moveTarget));
    }
}
