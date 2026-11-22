package com.dreamseekers.unit.flight;

import com.solegendary.reignofnether.unit.goals.FlyingMoveToTargetGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.packets.UnitPathClientboundPacket;
import com.solegendary.reignofnether.unit.pathfinding.RtsPathfinder;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import javax.annotation.Nullable;
import java.util.Objects;

/** RoN flying classification, but path-followed low flight rather than direct +10 steering. */
public final class LowAltitudeMoveGoal extends FlyingMoveToTargetGoal {
    @Nullable private BlockPos orderedTarget;

    public LowAltitudeMoveGoal(Mob mob) { super(mob, 0); }

    /** The actual RTS click, not one of the nearby air cells probed by endpoint selection. */
    @Nullable BlockPos orderedTarget() { return orderedTarget; }

    @Override public void setMoveTarget(@Nullable BlockPos clicked) {
        if (clicked != null) MiscUtil.addUnitCheckpoint((Unit) mob, clicked, true);
        boolean changed = !Objects.equals(clicked, orderedTarget);
        if (!changed && moveTarget != null && FlightWorkPositions.isClear(mob, moveTarget)) return;
        orderedTarget = clicked;
        moveTarget = FlightWorkPositions.move(mob, clicked);
        recalcCooldown = 0;
        if (!mob.level().isClientSide()) start();
    }

    @Override public boolean isAtDestination() {
        return moveTarget == null || mob.position().distanceToSqr(FlightWorkPositions.feet(moveTarget)) < .8 * .8;
    }

    @Override public void start() {
        isRunning = true;
        if (moveTarget == null) { mob.getNavigation().stop(); return; }
        Path path = mob.getNavigation().createPath(moveTarget, 0);
        float speed = ((Unit) mob).getSpeedModifier();
        if (speed < 1) speed = (float) Math.sqrt(speed);
        mob.getNavigation().moveTo(path, speed);
        recalcCooldown = RECALC_COOLDOWN_MAX;
        if (!mob.level().isClientSide())
            UnitPathClientboundPacket.sendPath(mob, path,
                    path != null && path.canReach() ? RtsPathfinder.TYPE_VANILLA : RtsPathfinder.TYPE_FAILED);
    }

    @Override public boolean canContinueToUse() {
        if (moveTarget == null) return false;
        if (isAtDestination()) {
            mob.getNavigation().stop();
            if (((Unit) mob).getHoldPosition()) return true;
            moveTarget = null;
            orderedTarget = null;
            return false;
        }
        if (recalcCooldown > 0) recalcCooldown--;
        if (mob.getNavigation().isDone() && recalcCooldown <= 0) start();
        return true;
    }

    @Override public void stopMoving() {
        orderedTarget = null;
        moveTarget = null;
        recalcCooldown = 0;
        mob.getNavigation().stop();
        if (mob.isVehicle() && mob.getPassengers().get(0) instanceof Unit passenger)
            passenger.getMoveGoal().stopMoving();
    }
}
