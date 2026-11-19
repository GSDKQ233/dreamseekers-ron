package com.dreamseekers.unit.flight;

import com.solegendary.reignofnether.unit.goals.ExploreBuildLocationGoal;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.core.BlockPos;
import javax.annotation.Nullable;
import java.util.Objects;

/** Only replaces movement; inherited fog visibility and server placement validation still gate construction. */
public final class FlyingExploreBuildLocationGoal extends ExploreBuildLocationGoal {
    @Nullable private BlockPos ordered;
    public FlyingExploreBuildLocationGoal(WorkerUnit worker) { super(worker); }

    @Override public boolean canUse() { return !com.dreamseekers.skill.FireflyRush.active(mob)&&super.canUse(); }
    @Override public boolean canContinueToUse() { return !com.dreamseekers.skill.FireflyRush.active(mob)&&super.canContinueToUse(); }
    @Override public void start() {
        if(com.dreamseekers.skill.FireflyRush.active(mob))return;
        super.start();com.dreamseekers.skill.FireflyRush.workPathStarted(mob,this);
    }
    @Override public void tick() {
        if(!com.dreamseekers.skill.FireflyRush.active(mob))super.tick();
    }

    @Override public void setMoveTarget(@Nullable BlockPos target) {
        if (Objects.equals(ordered, target) && moveTarget != null && FlightWorkPositions.isClear(mob, moveTarget)) return;
        ordered = target;
        super.setMoveTarget(FlightWorkPositions.move(mob, target));
    }

    @Override public void reset() { ordered = null; super.reset(); }
}
