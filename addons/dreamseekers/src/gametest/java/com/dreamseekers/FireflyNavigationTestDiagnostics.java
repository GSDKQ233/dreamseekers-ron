package com.dreamseekers;

import com.dreamseekers.unit.FireflyWorker;
import com.dreamseekers.unit.flight.FlightWorkPositions;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourceSources;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import net.minecraft.core.BlockPos;

/** Diagnostic output only: never changes the worker, goals, cargo, buildings or resources. */
final class FireflyNavigationTestDiagnostics {
    private FireflyNavigationTestDiagnostics() { }
    static String describeMove(FireflyWorker worker, BlockPos clicked) {
        var building = BuildingUtils.findBuilding(false, clicked);
        var owner = PlayerServerEvents.getRTSPlayer(worker.getOwnerName());
        return "owner=" + worker.getOwnerName() + "/rts=" + (owner == null ? "null" : owner.id + "/noCapitolTicks=" + owner.ticksWithoutCapitol)
                + " clicked=" + clicked + " block=" + worker.level().getBlockState(clicked)
                + " clickedResource=" + ResourceSources.getBlockResourceName(clicked, worker.level())
                + " clickedBuilding=" + (building == null ? "null" : building.getBuilding().structureName
                    + "@" + building.originPos + "/owner=" + building.ownerName)
                + " " + describe(worker, null);
    }
    static String describe(FireflyWorker worker, Resources account) {
        var gather = worker.getGatherResourceGoal();
        var returning = worker.getReturnResourcesGoal();
        var path = worker.getNavigation().getPath();
        var building = returning.getBuildingTarget();
        var endpoint = returning.getMoveTarget();
        var control = worker.getMoveControl();
        return "pos=" + worker.position() + " velocity=" + worker.getDeltaMovement()
                + " noCollision=" + worker.level().noCollision(worker)
                + " move=" + worker.getMoveGoal().getMoveTarget()
                + " navDone=" + worker.getNavigation().isDone()
                + " path=" + (path == null ? "null" : "reach=" + path.canReach() + ",next=" + path.getNextNodeIndex()
                    + "/" + path.getNodeCount() + ",target=" + path.getTarget() + ",nodes=" + path.nodes.stream().map(n -> n.asBlockPos().toShortString()).toList())
                + " control=" + control.operation + ":" + control.getWantedX() + "," + control.getWantedY() + "," + control.getWantedZ()
                + " returnBuilding=" + (building == null ? "null" : building.getBuilding().structureName + "@" + building.originPos)
                + " returnEndpoint=" + endpoint + " canDrop=" + returning.canDropOff()
                + " atReturnEndpoint=" + FlightWorkPositions.atApproach(worker, endpoint)
                + " gather=" + gather.getGatherTarget() + " approach=" + gather.getMoveTarget()
                + " gathering=" + gather.isGathering() + " name=" + gather.getTargetResourceName()
                + " farm=" + (gather.getTargetFarm() == null ? "null" : gather.getTargetFarm().originPos)
                + " savedFarm=" + (gather.saveData.targetFarm == null ? "null" : gather.saveData.targetFarm.originPos)
                + " cargo=" + Resources.getTotalResourcesFromItems(worker.getItems()).getTotalValue()
                + " ledger=" + (account == null ? "not-sampled" : account.food + "/" + account.wood + "/" + account.ore);
    }
}
