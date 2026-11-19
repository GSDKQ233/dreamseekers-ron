package com.dreamseekers.unit.flight;

import com.solegendary.reignofnether.unit.goals.GatherResourcesGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/** Inherits all economy/farm validation, timing, cargo and replant transactions unchanged. */
public final class FlyingGatherResourcesGoal extends GatherResourcesGoal {
    private record CachedApproach(@Nullable BlockPos pos, int untilTick, BlockPos origin) { }
    private final Map<BlockPos, CachedApproach> approaches = new HashMap<>();

    public FlyingGatherResourcesGoal(Mob mob) { super(mob); }

    @Override public boolean canUse() { return !com.dreamseekers.skill.FireflyRush.active(mob)&&super.canUse(); }
    @Override public boolean canContinueToUse() { return !com.dreamseekers.skill.FireflyRush.active(mob)&&super.canContinueToUse(); }
    @Override public void start() {
        if(com.dreamseekers.skill.FireflyRush.active(mob))return;
        super.start();com.dreamseekers.skill.FireflyRush.workPathStarted(mob,this);
    }
    @Override public void tick() {
        if(!com.dreamseekers.skill.FireflyRush.pausesGatherTick(mob))super.tick();
    }

    @Override public boolean isGathering() { return !com.dreamseekers.skill.FireflyRush.active(mob)&&super.isGathering(); }

    @Override protected boolean hasReachableStandSpot(BlockPos resource) {
        return getApproachTarget(resource) != null;
    }

    @Override protected int getApproachReachRange() { return 0; }
    @Override public double getMinDistToRecalculateSqr() { return 1.5 * 1.5; }

    @Override @Nullable protected BlockPos getApproachTarget(@Nullable BlockPos resource) {
        if (resource == null) return null;
        CachedApproach cached = approaches.get(resource);
        if (cached != null && mob.tickCount < cached.untilTick()
                && (cached.pos() != null ? FlightWorkPositions.isClear(mob, cached.pos())
                        : mob.blockPosition().distSqr(cached.origin()) < 16))
            return cached.pos();
        BlockPos approach = FlightWorkPositions.resource(mob, resource);
        if (approaches.size() > 128) approaches.clear();
        approaches.put(resource.immutable(), new CachedApproach(approach, mob.tickCount + 40, mob.blockPosition()));
        return approach;
    }

    @Override protected boolean isBlockInRange(BlockPos resource) {
        // Unlike ground farm's whole-footprint shortcut, a flying worker must
        // physically reach its exposed work face, at a useful height, with LOS.
        return FlightWorkPositions.atApproach(mob, getApproachTarget(resource))
                && resource.distToCenterSqr(mob.getX(), mob.getEyeY(), mob.getZ()) <= 25
                && FlightWorkPositions.clearLine(mob, mob.getEyePosition(), resource.getCenter(), resource);
    }
}
