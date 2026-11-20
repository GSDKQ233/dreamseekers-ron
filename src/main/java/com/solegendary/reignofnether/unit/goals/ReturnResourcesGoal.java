package com.solegendary.reignofnether.unit.goals;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.player.RTSPlayerScoresEnum;
import com.solegendary.reignofnether.resources.ExtendedResourcesSync;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesClientboundPacket;
import com.solegendary.reignofnether.unit.Relationship;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.packets.UnitSyncClientboundPacket;
import com.solegendary.reignofnether.util.MiscUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import com.solegendary.reignofnether.resources.UnitCargoSaveData;

import javax.annotation.Nullable;

/** Keeps a return order separate from its current destination: losing a depot must
 * neither lose unpaid cargo nor consume the worker's suspended gather assignment. */
public class ReturnResourcesGoal extends MoveToTargetBlockGoal {
    private static final int RETRY_TICKS = 20;
    @Nullable private BuildingPlacement buildingTarget;
    @Nullable private Level orderLevel;
    private long nextSearchTick;
    private long nextDepositTick;

    public ReturnResourcesGoal(Mob mob) {
        super(mob, true, 0);
    }

    public void depositItems() {
        if (!(mob instanceof Unit unit) || mob.level().isClientSide() || !hasValidDepositTarget()) return;
        Resources account = ExtendedResourcesSync.account(unit.getOwnerName());
        if (account == null) return; // No unique recipient: neither consume nor report the cargo.
        Resources credit = account.depositCarriedItems(unit.getItems());
        if (credit == null) return;

        RTSPlayer rtsPlayer = PlayerServerEvents.getRTSPlayer(account.ownerName);
        if (rtsPlayer != null) {
            rtsPlayer.scores.addToScore(RTSPlayerScoresEnum.TOTAL_RESOURCES_HARVESTED, credit.getTotalValue());
            if (credit.food > 0) rtsPlayer.scores.addToScore(RTSPlayerScoresEnum.FOOD_HARVESTED, credit.food);
            if (credit.wood > 0) rtsPlayer.scores.addToScore(RTSPlayerScoresEnum.WOOD_HARVESTED, credit.wood);
            if (credit.ore > 0) rtsPlayer.scores.addToScore(RTSPlayerScoresEnum.ORES_HARVESTED, credit.ore);
        }
        ResourcesClientboundPacket.addSubtractResources(credit);
        ResourcesClientboundPacket.showFloatingText(credit, moveTarget != null ? moveTarget : mob.getOnPos());
        UnitSyncClientboundPacket.sendSyncResourcesPacket(unit);
        stopReturning();
    }

    public boolean hasReturnOrder() { return orderLevel != null; }

    @Override public boolean canUse() {
        // Primarily needed when there was NO destination: GoalSelector does not tick an
        // inactive MOVE goal. A pending order takes no navigation slot while it waits.
        refreshTarget(false);
        return super.canUse();
    }

    @Override public boolean canContinueToUse() {
        refreshTarget(true);
        return moveTarget != null && super.canContinueToUse();
    }

    @Override public void tick() {
        refreshTarget(true);
        if (buildingTarget == null || moveTarget == null) return;
        calcMoveTarget();
        if (mob.level().isClientSide() || !canDropOff() || !(mob instanceof Unit unit)) return;
        long now = mob.level().getGameTime();
        if (now < nextDepositTick) return;
        nextDepositTick = now + RETRY_TICKS;
        depositItems();
        if (hasReturnOrder()) {
            // No unique account or no room for a credit: keep both the cargo and the
            // suspended gather job. An explicitly empty/unpriced delivery needs no retry.
            if (unit.getItems().stream().anyMatch(UnitCargoSaveData::isLedgerValued)) return;
            stopReturning();
        }
        if (unit instanceof WorkerUnit worker) {
            unit.resetBehaviours();
            WorkerUnit.resetBehaviours(worker);
            GatherResourcesGoal gather = worker.getGatherResourceGoal();
            if (gather != null && gather.saveData.hasData()) {
                gather.loadState();
                gather.clearReturnState();
            }
        }
    }

    protected void calcMoveTarget() {
        if (this.buildingTarget != null)
            this.moveTarget = this.buildingTarget.getClosestGroundPos(mob.getOnPos(), 1);
    }

    // only count as building if in range of the target - building is actioned in Building.tick()
    public boolean canDropOff() {
        return hasValidDepositTarget() && moveTarget != null
                && (buildingTarget.isPosInsideBuilding(mob.getOnPos()) || MiscUtil.isMobInRangeOfPos(moveTarget,mob,1.5f));
    }

    private boolean eligible(BuildingPlacement building) {
        return building.getLevel() == mob.level() && building.isBuilt && !building.isDestroyedServerside
                && building.getBuilding().canAcceptResources && mob instanceof Unit unit
                && BuildingServerEvents.getUnitToBuildingRelationship(unit, building) == Relationship.OWNED;
    }

    protected final boolean hasValidDepositTarget() {
        return buildingTarget != null && eligible(buildingTarget)
                && BuildingServerEvents.getBuildings().contains(buildingTarget);
    }

    private void refreshTarget(boolean startPath) {
        if (orderLevel == null) return;
        if (orderLevel != mob.level()) {
            cancelReturning();
            return;
        }
        if (mob.level().isClientSide()) return;
        if (buildingTarget != null && !hasValidDepositTarget()) {
            buildingTarget = null;
            super.stopMoving(); // retire navigation and any asynchronous result for the stale target
        }
        if (buildingTarget != null && moveTarget != null) return;
        long now = mob.level().getGameTime();
        if (now < nextSearchTick) return;
        nextSearchTick = now + RETRY_TICKS;
        if (buildingTarget == null) {
            BlockPos pos = mob.getOnPos();
            double closest = Double.POSITIVE_INFINITY;
            for (BuildingPlacement candidate : BuildingServerEvents.getBuildings()) {
                if (!eligible(candidate)) continue;
                // Rank by footprint, not centre (large buildings) or a block scan at EVERY
                // distant candidate. Only the winner needs terrain/collision/path queries.
                int x = Math.max(candidate.minCorner.getX() - 1, Math.min(pos.getX(), candidate.maxCorner.getX() + 1));
                int z = Math.max(candidate.minCorner.getZ() - 1, Math.min(pos.getZ(), candidate.maxCorner.getZ() + 1));
                double distance = pos.distSqr(new BlockPos(x, candidate.minCorner.getY(), z));
                if (distance < closest) {
                    closest = distance;
                    buildingTarget = candidate;
                }
            }
            if (buildingTarget == null) return; // keep orderLevel: a later depot can accept this cargo
            MiscUtil.addUnitCheckpoint((Unit) mob, buildingTarget.centrePos, true);
            resetRecalcBackoff();
        }
        // A blocked flight approach may produce no move target. Try again on the same
        // bounded cadence rather than planning a new air path on every selector poll.
        calcMoveTarget();
        if (startPath && moveTarget != null) start();
    }

    public void returnToClosestBuilding() {
        if (mob.level().isClientSide()) return;
        if (orderLevel == null) {
            orderLevel = mob.level();
            nextSearchTick = 0;
            nextDepositTick = 0;
        }
        refreshTarget(true);
    }

    public void setBuildingTarget(@Nullable BuildingPlacement target) {
        if (target == null || target.getLevel() != mob.level()) {
            cancelReturning();
            return;
        }
        // Do not stop an unrelated movement when assigning a previously idle goal
        // (for example, an explicit return intent accepted during Firefly Rush).
        if (buildingTarget != null || moveTarget != null) super.stopMoving();
        orderLevel = mob.level();
        nextSearchTick = 0;
        nextDepositTick = 0;
        buildingTarget = target;
        // Preserve client-side previews, but never start an authoritative route to a
        // stale/foreign/incomplete host. An invalidated same-world trip may be redirected.
        if (!mob.level().isClientSide() && !hasValidDepositTarget()) {
            buildingTarget = null;
            refreshTarget(true);
            return;
        }
        MiscUtil.addUnitCheckpoint((Unit) mob, target.centrePos, true);
        resetRecalcBackoff();
        calcMoveTarget();
        if (moveTarget != null) start();
    }

    @Nullable public BuildingPlacement getBuildingTarget() { return buildingTarget; }

    /** A new authoritative order also supersedes the suspended work in the save projection. */
    public void cancelReturning() {
        if (hasReturnOrder() && mob instanceof WorkerUnit worker && worker.getGatherResourceGoal() != null)
            worker.getGatherResourceGoal().clearReturnState();
        stopReturning();
    }

    public void stopReturning() {
        orderLevel = null;
        buildingTarget = null;
        super.stopMoving();
    }
}
