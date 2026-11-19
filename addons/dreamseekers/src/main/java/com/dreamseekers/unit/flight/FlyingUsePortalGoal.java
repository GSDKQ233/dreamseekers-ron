package com.dreamseekers.unit.flight;

import com.solegendary.reignofnether.unit.goals.UsePortalGoal;
import net.minecraft.world.entity.Mob;
import net.minecraft.core.BlockPos;

/** Approach uses flight paths; the explicit existing RTS portal transfer itself remains unchanged. */
public final class FlyingUsePortalGoal extends UsePortalGoal {
    public FlyingUsePortalGoal(Mob mob) { super(mob); }
    @Override public void setBuildingTarget(BlockPos target) {
        moveTarget = null;
        super.setBuildingTarget(target);
    }
    @Override protected void calcMoveTarget() {
        if (getBuildingTarget() == null) { moveTarget = null; return; }
        if (moveTarget == null || !FlightWorkPositions.isClear(mob, moveTarget))
            moveTarget = FlightWorkPositions.move(mob, getBuildingTarget().centrePos);
    }
}
