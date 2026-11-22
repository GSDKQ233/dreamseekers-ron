package com.dreamseekers.unit.flight;

import com.dreamseekers.skill.FireflyRush;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.unit.goals.ReturnResourcesGoal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public final class FlyingReturnResourcesGoal extends ReturnResourcesGoal {
    // Never persist or spend again merely because a long/stalled journey outlasts the cooldown.
    private boolean spentReturnBoost;
    public FlyingReturnResourcesGoal(Mob mob) { super(mob); }

    @Override public boolean canUse() { return !FireflyRush.active(mob)&&super.canUse(); }
    @Override public boolean canContinueToUse() { return !FireflyRush.active(mob)&&super.canContinueToUse(); }
    @Override public void start() {
        if(FireflyRush.active(mob))return;
        super.start();FireflyRush.workPathStarted(mob,this);
    }
    @Override public void tick() {
        if(FireflyRush.active(mob))return;
        super.tick(); // Native deposit, cancellation and restoration of the gather job have priority.
        if(!spentReturnBoost && FireflyRush.afterReturnStep(mob,this))spentReturnBoost=true;
    }
    @Override public void stop() {
        super.stop();
        FireflyRush.endReturn(mob);
    }
    @Override public void stopReturning() {
        super.stopReturning();
        spentReturnBoost=false;
        FireflyRush.endReturn(mob);
    }
    /** A live native route to the exact owned depot approach, not a pending order, idle stockpile
     * callback, failed path, or a different goal's movement. Read-only: no endpoint/path searches. */
    public boolean travellingToDepot() {
        var path=mob.getNavigation().getPath();
        return isRunning && hasReturnOrder() && moveTarget!=null && path!=null && !path.isDone()
                && path.canReach() && moveTarget.equals(path.getTarget())
                && mob.position().distanceToSqr(Vec3.atBottomCenterOf(moveTarget))>1.5*1.5
                && mob.level().hasChunkAt(moveTarget) && mob.level().getWorldBorder().isWithinBounds(moveTarget)
                && hasValidDepositTarget();
    }

    @Override protected void calcMoveTarget() {
        if (getBuildingTarget() == null) { moveTarget = null; return; }
        if (moveTarget == null || !FlightWorkPositions.isClear(mob, moveTarget))
            moveTarget = FlightWorkPositions.building(mob, getBuildingTarget());
    }

    /** Stockpile completion can call this directly, without either goal tick or canDropOff. */
    @Override public void depositItems() {
        if(!FireflyRush.active(mob))super.depositItems();
    }

    @Override public boolean canDropOff() {
        BuildingPlacement building = getBuildingTarget();
        return !FireflyRush.active(mob) && hasValidDepositTarget()
                && FlightWorkPositions.canWorkAtBuilding(mob, building, moveTarget);
    }
}
