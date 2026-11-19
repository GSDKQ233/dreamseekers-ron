// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.unit.goals.MeleeAttackBuildingGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import java.util.List;
/** Guard assignment and every damage tick against alliance, capture, dimension and lifecycle changes. */
public class TeamSafeBuildingAttackGoal extends MeleeAttackBuildingGoal {
    private final TeamSafeCombatUnit unit;
    public TeamSafeBuildingAttackGoal(Mob mob) { super(mob); unit=(TeamSafeCombatUnit)mob; }
    private List<BuildingPlacement> active() {
        return mob.level().isClientSide() ? BuildingClientEvents.getBuildings() : BuildingServerEvents.getBuildings();
    }
    protected final boolean valid(BuildingPlacement building) {
        return building != null && mob.isAlive() && building.getLevel()==mob.level()
                && active().contains(building) && !building.isDestroyedServerside
                && building.isAttackable() && unit.canHarmOwner(building.ownerName);
    }
    @Override public void setBuildingTarget(BlockPos pos) {
        // The legacy global-coordinate lookup does not distinguish dimensions. Do not use it here.
        BuildingPlacement found = pos == null ? null : active().stream().filter(this::valid)
                .filter(b->b.originPos.equals(pos)||b.isPosInsideBuilding(pos)).findFirst().orElse(null);
        stopAttacking();
        if(found==null)return;
        buildingTarget=found;
        currentMeleeRecalcCooldown=RECALC_COOLDOWN_MAX;
        moveTarget=found.getClosestGroundPos(mob.getOnPos(),1);
        start();
    }
    @Override public void start() {
        if(unit.getHoldPosition()) { super.stopMoving(); return; }
        super.start();
    }
    @Override public boolean canUse() { return unit.getHoldPosition() ? valid(buildingTarget) : super.canUse(); }
    @Override public boolean canContinueToUse() {
        if(unit.getHoldPosition()) { mob.getNavigation().stop(); return valid(buildingTarget); }
        return super.canContinueToUse();
    }
    @Override public void tick() {
        if (buildingTarget != null && !valid(buildingTarget)) { stopAttacking(); return; }
        if(unit.getHoldPosition()) {
            mob.getNavigation().stop();
            if(buildingTarget!=null && isAttacking() && --ticksToNextBlockBreak<=0) doBuildingAttack();
            return;
        }
        super.tick();
    }
}
