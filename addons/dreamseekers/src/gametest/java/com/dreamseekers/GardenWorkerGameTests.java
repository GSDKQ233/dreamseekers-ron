// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.unit.FireflyWorker;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.buildings.placements.FarmPlacement;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;

/** A real crop cycle, separate from fixture construction and the ordinary linked-log test. */
@net.minecraftforge.gametest.GameTestHolder(DreamseekersMod.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class GardenWorkerGameTests {
    private static final String OWNER = "GardenGT_CropCycle";
    private static final String BATCH = "dreamseekers_garden_worker";

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        PlayerServerEvents.rtsPlayers.removeIf(p -> p.name.equals(OWNER));
        ResourcesServerEvents.resourcesList.removeIf(r -> r.ownerName.equals(OWNER));
        for (var entity : new ArrayList<>(UnitServerEvents.getAllUnits()))
            if (entity instanceof Unit unit && unit.getOwnerName().equals(OWNER)) entity.discard();
        BuildingServerEvents.getBuildings().removeIf(b -> b.ownerName.equals(OWNER));
    }

    @GameTest(template = "dreamseekers_test_arena", batch = BATCH, timeoutTicks = 1500)
    public static void gardenWorkerHarvestsReplantsAndDeliversFood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(PlayerServerEvents.getRTSPlayer(OWNER) == null, "crop-cycle owner is isolated");
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER, DreamseekersMod.DREAMSEEKERS));
        Resources account = new Resources(OWNER, 0, 500, 0);
        ResourcesServerEvents.resourcesList.add(account);
        // Completed buildings are fixtures. The worker alone performs harvesting, replanting,
        // walking and delivery; no harvest shortcut, resource injection or build-speed cheat.
        flat(helper, 2, 2, 90, 55);
        BuildingPlacement anchor = install(helper, DreamseekersMod.DREAM_ANCHOR, 4, 4);
        BuildingPlacement installed = install(helper, DreamseekersMod.REVERIE_GARDEN, 40, 4);
        helper.assertTrue(installed instanceof FarmPlacement, "real Reverie Garden creates the normal FarmPlacement");
        FarmPlacement garden = (FarmPlacement) installed;
        List<BlockPos> soils = garden.getBlocks().stream()
                .filter(b -> DreamseekersMod.REVERIE_GARDEN.isCropSoil(b.getBlockState()))
                .map(BuildingBlock::getBlockPos).toList();
        helper.assertTrue(soils.size() == 16, "final garden contains exactly sixteen declared crop beds");
        var dyedreamDirt = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("pasterdream", "dyedream_dirt"));
        helper.assertTrue(soils.stream().allMatch(p -> level.getBlockState(p).is(dyedreamDirt)),
                "real dyedream dirt survives fixture installation; no vanilla farmland substitution");
        // Seed one controlled mature crop and young crops elsewhere. This avoids waiting the
        // full 91-second farm growth cycle and keeps the first replant debit deterministic.
        for (BlockPos soil : soils) {
            level.setBlock(soil.above(), Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, 0), 2);
        }
        BlockPos crop = soils.get(0).above();
        level.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, 7), 2);
        FireflyWorker worker = DreamseekersMod.FIREFLY_WORKER.get().create(level);
        helper.assertTrue(worker != null, "genuine PasterDream-derived worker exists");
        helper.assertTrue(worker.getItems().isEmpty(), "harvest worker begins without carried items from an earlier fixture");
        worker.setOwnerName(OWNER);
        worker.moveTo(crop.getX() + 0.5, crop.getY(), crop.getZ() + 0.5, 0, 0);
        helper.assertTrue(level.addFreshEntity(worker), "worker enters real server world with AI enabled");
        UnitServerEvents.addActionItem(OWNER, UnitAction.FARM, -1, new int[]{worker.getId()}, crop, garden.originPos);
        final int[] carriedAtReturn = {0};
        final int[] foodBeforeReturn = {0};
        final int[] woodAfterReplant = {0};

        helper.runAtTickTime(20, () -> {
            helper.assertTrue(worker.getGatherResourceGoal().getTargetFarm() == garden,
                    "real FARM command assigns worker to this specific Reverie Garden");
            helper.assertTrue(worker.getGatherResourceGoal().isFarming(), "ordinary RoN farming goal is active");
        });
        helper.runAtTickTime(220, () -> {
            BlockState replanted = level.getBlockState(crop);
            helper.assertTrue(level.getBlockState(crop.below()).is(dyedreamDirt),
                    "harvest and replant preserve the actual dyedream dirt bed");
            helper.assertTrue(replanted.is(Blocks.WHEAT) && replanted.getValue(BlockStateProperties.AGE_7) < 7,
                    "worker harvested the mature crop and replanted young wheat without a helper refill: "
                            + BuildingAssetGameTests.soilDiagnostic(garden, worker, crop.below()));
            carriedAtReturn[0] = Resources.getTotalResourcesFromItems(worker.getItems()).food;
            helper.assertTrue(carriedAtReturn[0] >= 4, "real wheat harvest created carried RTS food tokens");
            helper.assertTrue(account.wood == 500 - ResourceCosts.REPLANT_WOOD_COST,
                    "one completed wheat replant debits the ordinary wood cost exactly once");
            helper.assertTrue(account.food == 0, "food is carried, not silently credited before delivery");
            foodBeforeReturn[0] = account.food;
            woodAfterReplant[0] = account.wood;
            UnitServerEvents.addActionItem(OWNER, UnitAction.RETURN_RESOURCES, -1,
                    new int[]{worker.getId()}, anchor.originPos, anchor.originPos);
        });
        for (int diagnosticTick : new int[]{221, 300, 450, 679, 1000, 1399}) helper.runAtTickTime(diagnosticTick, () ->
                System.out.println("FIREFLY_GARDEN_RETURN_DIAGNOSTIC tick=" + diagnosticTick + " " + FireflyNavigationTestDiagnostics.describe(worker, account)));
        // Native flight at unchanged .25 attributes measures about .05 blocks/tick.
        // Preserve the early gather/replant check, but allow this 33-block delivery route.
        helper.runAtTickTime(1400, () -> {
            helper.assertTrue(account.food == foodBeforeReturn[0] + carriedAtReturn[0],
                    "worker's actual walk and drop-off credit the exact carried food to its owner");
            helper.assertTrue(Resources.getTotalResourcesFromItems(worker.getItems()).food == 0,
                    "delivered food leaves worker inventory rather than duplicating");
            helper.assertTrue(account.wood == woodAfterReplant[0], "return command ends farming and further replant costs");
            helper.assertTrue(soils.stream().filter(p -> !p.equals(crop.below()))
                    .anyMatch(p -> level.getBlockState(p.above()).is(Blocks.WHEAT)
                            && level.getBlockState(p.above()).getValue(BlockStateProperties.AGE_7) > 0),
                    "other young crops on dyedream beds continue growing during the unchanged farm cycle");
            System.out.println("DREAMSEEKERS_GARDEN_WORKER_CYCLE_PASS food=" + account.food
                    + " replantWood=" + (500 - account.wood));
            helper.succeed();
        });
    }

    private static void flat(GameTestHelper helper, int x, int z, int width, int depth) {
        BlockPos origin = BuildingAssetGameTests.outdoorPos(helper, x, 1, z);
        for (int dx = 0; dx < width; dx++) for (int dz = 0; dz < depth; dz++) {
            helper.getLevel().setBlock(origin.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 2);
            for (int dy = 1; dy < 12; dy++) helper.getLevel().setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
        }
        BuildingAssetGameTests.clearPreexistingFixtureDrops(helper, origin, width, depth);
        helper.assertTrue(helper.getLevel().canSeeSky(origin.above(2)), "garden test fixture is outdoors before crop placement");
    }

    private static BuildingPlacement install(GameTestHelper helper, Building building, int x, int z) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = BuildingAssetGameTests.outdoorPos(helper, x, 1, z);
        for (BuildingBlock block : BuildingUtils.getAbsoluteBlockData(building.getRelativeBlockData(level), level, origin, Rotation.NONE))
            level.setBlock(block.getBlockPos(), block.getBlockState(), 2);
        BuildingPlacement placement = BuildingUtils.getNewBuildingPlacement(building, level, origin, Rotation.NONE, OWNER, false);
        BuildingServerEvents.getBuildings().add(placement);
        placement.onBuilt();
        return placement;
    }
}
