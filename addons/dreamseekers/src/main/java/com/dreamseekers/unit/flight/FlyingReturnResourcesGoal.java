package com.dreamseekers.unit.flight;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.unit.goals.ReturnResourcesGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.world.entity.Mob;
import javax.annotation.Nullable;

public final class FlyingReturnResourcesGoal extends ReturnResourcesGoal {
    public FlyingReturnResourcesGoal(Mob mob) { super(mob); }

    @Override public boolean canUse() { return !com.dreamseekers.skill.FireflyRush.active(mob)&&super.canUse(); }
    @Override public boolean canContinueToUse() { return !com.dreamseekers.skill.FireflyRush.active(mob)&&super.canContinueToUse(); }
    @Override public void start() {
        if(com.dreamseekers.skill.FireflyRush.active(mob))return;
        super.start();com.dreamseekers.skill.FireflyRush.workPathStarted(mob,this);
    }
    @Override public void tick() {
        if(!com.dreamseekers.skill.FireflyRush.active(mob))super.tick();
    }

    @Override public void setBuildingTarget(@Nullable BuildingPlacement building) {
        moveTarget = null;
        super.setBuildingTarget(building);
    }

    @Override protected void calcMoveTarget() {
        if (getBuildingTarget() == null) { moveTarget = null; return; }
        if (moveTarget == null || !FlightWorkPositions.isClear(mob, moveTarget))
            moveTarget = FlightWorkPositions.building(mob, getBuildingTarget());
    }

    /** Stockpile completion can call this directly, without either goal tick or canDropOff. */
    @Override public void depositItems() {
        if(!com.dreamseekers.skill.FireflyRush.active(mob))super.depositItems();
    }

    @Override public boolean canDropOff() {
        BuildingPlacement building = getBuildingTarget();
        return !com.dreamseekers.skill.FireflyRush.active(mob) && building != null && building.getLevel() == mob.level()
                && building.isBuilt && building.getBuilding().canAcceptResources
                && building.ownerName.equals(((Unit) mob).getOwnerName())
                && BuildingServerEvents.getBuildings().contains(building)
                && FlightWorkPositions.canWorkAtBuilding(mob, building, moveTarget);
    }
}
