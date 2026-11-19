// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.unit.FireflyWorker;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import java.util.ArrayList;

/** Genuine completed-building repair, not fixture refreshBlocks or a build-speed shortcut. */
@net.minecraftforge.gametest.GameTestHolder(DreamseekersMod.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class FireflyRepairGameTests {
    private static final String OWNER = "FireflyGT_PaidRepair";
    private static final String BATCH = "dreamseekers_firefly_repair";

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        for (var entity : new ArrayList<>(UnitServerEvents.getAllUnits()))
            if (entity instanceof Unit unit && unit.getOwnerName().equals(OWNER)) entity.discard();
        BuildingServerEvents.getBuildings().removeIf(b -> b.ownerName.equals(OWNER));
        PlayerServerEvents.rtsPlayers.removeIf(p -> p.name.equals(OWNER));
        ResourcesServerEvents.resourcesList.removeIf(r -> r.ownerName.equals(OWNER));
    }

    @GameTest(template = "dreamseekers_test_arena", batch = BATCH, timeoutTicks = 1900)
    public static void physicallyApproachesPaidRepairStopsAndResumesWithoutFreeRestoration(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER, DreamseekersMod.DREAMSEEKERS));
        Resources account = new Resources(OWNER, 500, 500, 500);
        ResourcesServerEvents.resourcesList.add(account);
        BlockPos base = BuildingAssetGameTests.outdoorPos(helper, 2, 1, 2);
        for (int x = 0; x < 95; x++) for (int z = 0; z < 60; z++) {
            level.setBlock(base.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 2);
            for (int y = 1; y < 16; y++) level.setBlock(base.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        BuildingAssetGameTests.clearPreexistingFixtureDrops(helper, base, 95, 60);
        install(level, DreamseekersMod.DREAM_ANCHOR, base.offset(3, 0, 3));
        BuildingPlacement depot = install(level, DreamseekersMod.DREAM_DEPOT, base.offset(45, 0, 10));
        helper.assertTrue(depot.isBuilt && depot.getBlocksPlaced() == depot.getBlocksTotal(), "repair fixture begins fully completed");
        helper.assertTrue(depot.blocksPerBuild == 1 && depot.baseMsPerBuild == 500 && !depot.selfBuilding,
                "ordinary one-block repair queue and timing remain untouched");
        depot.destroyRandomBlocks(24);
        helper.assertTrue(depot.isBuilt && depot.getBlocksPlaced() < depot.getBlocksTotal(), "real combat block damage leaves a live completed building");
        FireflyWorker worker = DreamseekersMod.FIREFLY_WORKER.get().create(level);
        helper.assertTrue(worker != null, "real firefly repair worker exists");
        worker.setOwnerName(OWNER);
        worker.moveTo(depot.minCorner.getX() - 15 + .5, depot.minCorner.getY(), depot.centrePos.getZ() + .5, 0, 0);
        helper.assertTrue(level.addFreshEntity(worker), "repair worker enters world with ordinary AI");
        int[] initialPlaced = {0};
        int[] initialMissing = {0};
        int[] stoppedPlaced = {0};
        int[] stoppedWood = {0};
        int[] phase = {0}; // 0 waiting for support updates, 1 first approach, 2 stopped, 3 resumed, 4 done

        helper.runAtTickTime(20, () -> {
            depot.initPlacedBlocks();
            initialPlaced[0] = depot.getBlocksPlaced();
            initialMissing[0] = depot.getBlocksTotal() - initialPlaced[0];
            helper.assertTrue(initialMissing[0] >= 24, "damage and genuine support updates remain missing before any repair order");
            helper.assertTrue(account.wood == 500, "idle damaged buildings do not self-repair or charge resources");
            order(worker, UnitAction.BUILD_REPAIR, depot.originPos);
            phase[0] = 1;
        });
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(depot.getBlocksPlaced() == initialPlaced[0] && account.wood == 500,
                    "distant firefly must approach physically before repairing or paying");
        });
        for (int tick = 31; tick < 1800; tick++) {
            final int now = tick;
            helper.runAtTickTime(tick, () -> {
                if (phase[0] == 1 && depot.getBlocksPlaced() > initialPlaced[0]) {
                    helper.assertTrue(worker.getBuildRepairGoal().isBuilding(), "repair begins only after real building approach");
                    helper.assertTrue(account.wood < 500 && depot.getBlocksPlaced() < depot.getBlocksTotal(),
                            "first completed repair consumes wood and leaves work to interrupt");
                    order(worker, UnitAction.STOP, BlockPos.ZERO);
                    phase[0] = 2;
                    // Allow an already-paid queued block to settle, then test a true idle interval.
                    helper.runAtTickTime(now + 20, () -> {
                        stoppedPlaced[0] = depot.getBlocksPlaced();
                        stoppedWood[0] = account.wood;
                        helper.assertTrue(worker.getBuildRepairGoal().getBuildingTarget() == null && worker.getNavigation().isDone(),
                                "STOP clears the repair target and navigation");
                    });
                    helper.runAtTickTime(now + 100, () -> {
                        helper.assertTrue(depot.getBlocksPlaced() == stoppedPlaced[0] && account.wood == stoppedWood[0],
                                "stopped worker neither restores extra geometry nor debits more repair wood");
                        order(worker, UnitAction.BUILD_REPAIR, depot.originPos);
                        phase[0] = 3;
                    });
                } else if (phase[0] == 3 && depot.getBlocksPlaced() == depot.getBlocksTotal()) {
                    phase[0] = 4;
                    helper.runAtTickTime(now + 20, () -> {
                        depot.initPlacedBlocks();
                        helper.assertTrue(depot.getBlocksPlaced() == depot.getBlocksTotal(), "resumed repair restored real stable structure blocks");
                        helper.assertTrue(account.wood == 500 - initialMissing[0],
                                "one ordinary wood charge per replaced block, including STOP/resume, without free repair or duplicate debit: missing="
                                        + initialMissing[0] + " wood=" + account.wood);
                        helper.assertTrue(account.food == 500 && account.ore == 500, "repair does not grant unrelated income or charge other resources");
                        helper.assertTrue(depot.isBuilt && BuildingServerEvents.getBuildings().contains(depot), "repair retains completed live building identity");
                        System.out.println("DREAMSEEKERS_FIREFLY_PAID_REPAIR_STOP_RESUME_PASS blocks=" + initialMissing[0]
                                + " wood=" + (500 - account.wood) + " elapsedTicks=" + now);
                        helper.succeed();
                    });
                }
            });
        }
        helper.runAtTickTime(1850, () -> helper.assertTrue(phase[0] == 4, "repair must resume and finish at the unchanged rate"));
    }

    private static BuildingPlacement install(ServerLevel level, Building building, BlockPos origin) {
        for (BuildingBlock block : BuildingUtils.getAbsoluteBlockData(building.getRelativeBlockData(level), level, origin, Rotation.NONE))
            level.setBlock(block.getBlockPos(), block.getBlockState(), 2);
        BuildingPlacement placement = BuildingUtils.getNewBuildingPlacement(building, level, origin, Rotation.NONE, OWNER, false);
        BuildingServerEvents.getBuildings().add(placement);
        placement.onBuilt();
        placement.initPlacedBlocks();
        return placement;
    }

    private static void order(FireflyWorker worker, UnitAction action, BlockPos target) {
        UnitServerEvents.addActionItem(OWNER, action, -1, new int[]{worker.getId()}, target, target);
    }
}
