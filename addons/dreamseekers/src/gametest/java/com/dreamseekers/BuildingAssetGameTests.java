// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.building.DreamBuildingSupport;
import com.dreamseekers.unit.FireflyWorker;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.FarmPlacement;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.TargetResourcesSave;
import com.solegendary.reignofnether.unit.TargetResourcesSaveData;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.GatherResourcesGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Runtime checks, not a claim that the current source has been compiled or run. */
@net.minecraftforge.gametest.GameTestHolder(DreamseekersMod.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class BuildingAssetGameTests {
    private static final String PREFIX = "BuildingAssetGT_";
    private static final String BATCH = "dreamseekers_building_assets";
    private static final String TEMPLATE = "dreamseekers_test_arena";
    private static final String[] ASSETS = {"dream_anchor", "reverie_garden", "dream_depot",
            "shadow_mine", "shadow_furnace", "beetle_sanctuary", "dream_hall",
            "resonance_laboratory", "contract_ecology", "resonance_dais", "shadow_watchtower"};

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        PlayerServerEvents.rtsPlayers.removeIf(p -> p.name.startsWith(PREFIX));
        ResourcesServerEvents.resourcesList.removeIf(r -> r.ownerName.startsWith(PREFIX));
        for (var entity : new ArrayList<>(UnitServerEvents.getAllUnits()))
            if (entity instanceof Unit unit && unit.getOwnerName().startsWith(PREFIX)) entity.discard();
        synchronized (UnitServerEvents.savedTargetResources) {
            UnitServerEvents.savedTargetResources.removeIf(s -> s.targetFarm != null && s.targetFarm.ownerName.startsWith(PREFIX));
        }
        BuildingServerEvents.getBuildings().removeIf(b -> b.ownerName.startsWith(PREFIX));
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void allElevenPalettesAndRotationsUseActualRegistries(GameTestHelper helper) throws Exception {
        checkPalettesAndRotations(helper, ASSETS);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void allThirteenReviewedPalettesAndRotationsUseActualRegistries(GameTestHelper helper) throws Exception {
        checkPalettesAndRotations(helper, new String[]{
                "dream_anchor_base_v5", "dream_anchor_shadow_v5", "dream_anchor_wind_v5",
                "shadow_watchtower_garrison_v3", "reverie_garden_rework_v1",
                "dream_depot_rework_v1", "shadow_mine_rework_v1", "shadow_furnace_rework_v1",
                "beetle_sanctuary_rework_v1", "dream_hall_rework_v1",
                "resonance_laboratory_rework_v1", "contract_ecology_rework_v1", "resonance_dais_rework_v1"
        });
    }

    private static void checkPalettesAndRotations(GameTestHelper helper, String[] assets) throws Exception {
        for (String id : assets) {
            byte[] server;
            try (InputStream stream = BuildingAssetGameTests.class.getResourceAsStream("/data/dreamseekers/structures/" + id + ".nbt")) {
                helper.assertTrue(stream != null, "server asset present: " + id);
                server = stream.readAllBytes();
            }
            try (InputStream stream = BuildingAssetGameTests.class.getResourceAsStream("/assets/dreamseekers/structures/" + id + ".nbt")) {
                helper.assertTrue(stream != null && Arrays.equals(server, stream.readAllBytes()), "both asset copies identical: " + id);
            }
            CompoundTag nbt = NbtIo.readCompressed(new java.io.ByteArrayInputStream(server));
            helper.assertTrue(nbt.getList("entities", 10).isEmpty(), "no entity payload: " + id);
            var palette = nbt.getList("palette", 10);
            for (int i = 0; i < palette.size(); i++) {
                CompoundTag entry = palette.getCompound(i);
                ResourceLocation key = ResourceLocation.tryParse(entry.getString("Name"));
                helper.assertTrue(key != null && BuiltInRegistries.BLOCK.containsKey(key), "actual registry block: " + id + "/" + key);
                var block = BuiltInRegistries.BLOCK.get(key);
                CompoundTag props = entry.getCompound("Properties");
                for (String name : props.getAllKeys()) {
                    Property<?> property = block.getStateDefinition().getProperty(name);
                    helper.assertTrue(property != null && property.getValue(props.getString(name)).isPresent(),
                            "serialized property must be valid, not silently defaulted: " + key + "/" + name);
                }
            }
            var positions = nbt.getList("blocks", 10);
            for (int i = 0; i < positions.size(); i++)
                helper.assertTrue(!positions.getCompound(i).contains("nbt"), "no block-entity payload: " + id);
            var relative = DreamBuildingSupport.load(id);
            long expectedCount = relative.stream().filter(b -> !b.getBlockState().isAir()).count();
            for (Rotation rotation : Rotation.values()) {
                var absolute = BuildingUtils.getAbsoluteBlockData(relative, helper.getLevel(), BlockPos.ZERO, rotation);
                helper.assertTrue(absolute.size() == relative.size(), "rotation keeps complete air volume: " + id);
                helper.assertTrue(absolute.stream().map(BuildingBlock::getBlockPos).distinct().count() == absolute.size(), "unique rotated coordinates: " + id);
                helper.assertTrue(absolute.stream().filter(b -> !b.getBlockState().isAir()).count() == expectedCount, "non-air count preserved: " + id);
                for (int i = 0; i < relative.size(); i++) {
                    var from = relative.get(i);
                    var to = absolute.get(i);
                    helper.assertTrue(to.getBlockPos().equals(from.getBlockPos().rotate(rotation).above()), "RoN origin and rotation: " + id);
                    helper.assertTrue(to.getBlockState().equals(from.getBlockState().rotate(helper.getLevel(), from.getBlockPos(), rotation)), "actual state rotation: " + id);
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 6500)
    public static void actualWorkerCompletesDyedreamSoilGarden(GameTestHelper helper) {
        String owner = PREFIX + "Construction";
        registerOwner(helper, owner);
        flat(helper, 1, 1, 65, 30);
        installFixture(helper, DreamseekersMod.DREAM_ANCHOR, owner, 4, 4);
        BlockPos origin = outdoorPos(helper, 33, 1, 4);
        BuildingPlacement garden = BuildingServerEvents.placeBuilding(DreamseekersMod.REVERIE_GARDEN,
                origin, Rotation.NONE, owner, new int[0], false, false, false, true);
        helper.assertTrue(garden instanceof FarmPlacement && !garden.isBuilt && !garden.selfBuilding,
                "normal paid unfinished farm, not direct blocks, self-build or sandbox fixture");
        FireflyWorker worker = worker(helper, owner, 31, 8);
        UnitServerEvents.addActionItem(owner, UnitAction.BUILD_REPAIR, -1, new int[]{worker.getId()}, origin, origin);
        var soil = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("pasterdream", "dyedream_dirt"));
        List<BlockPos> beds = garden.getBlocks().stream().filter(b -> b.getBlockState().is(soil))
                .map(BuildingBlock::getBlockPos).toList();
        helper.assertTrue(beds.size() == 16, "sixteen actual dyedream dirt template beds");
        final boolean[] finished = {false};
        // Poll completion, do not inject building blocks or accelerate build rates.
        for (int tick = 1; tick < 6400; tick++) {
            helper.runAtTickTime(tick, () -> {
                if (finished[0] || !garden.isBuilt) return;
                finished[0] = true;
                worker.setNoAi(true); // Freeze only after real completion, without changing the construction outcome.
                helper.assertTrue(beds.stream().allMatch(p -> helper.getLevel().getBlockState(p).is(soil)),
                        "ordinary buildNextBlock preserved all dyedream soil, no farmland-to-dirt workaround");
                helper.assertTrue(beds.stream().allMatch(p -> helper.getLevel().getBlockState(p.above()).is(DreamPassiveEconomy.DECORATIVE_WHEAT.get())),
                        "ordinary worker queue constructed all sixteen non-harvestable decorative wheat plants");
                garden.initPlacedBlocks();
                helper.assertTrue(garden.getBlocksPlaced() == garden.getBlocksTotal(), "completion count remains true after actual world rescan");
                helper.assertTrue(garden.getBlocks().stream().noneMatch(b -> !b.getBlockState().getFluidState().isEmpty()), "water-free planting design");
                helper.succeed();
            });
        }
        for (int diagnosticTick : new int[]{100, 1000, 6000}) {
            helper.runAtTickTime(diagnosticTick, () -> {
                if (!finished[0]) System.out.println("DREAMSEEKERS_CONSTRUCTION_DIAGNOSTIC tick=" + diagnosticTick
                        + " placed=" + garden.getBlocksPlaced() + "/" + garden.getBlocksTotal()
                        + " builders=" + garden.getBuilders().size() + " worker=" + worker.position()
                        + " alive=" + worker.isAlive() + " buildTarget=" + (worker.getBuildRepairGoal().getBuildingTarget() == garden)
                        + " isBuilding=" + worker.getBuildRepairGoal().isBuilding()
                        + " navDone=" + worker.getNavigation().isDone()
                        + " missing=" + garden.getBlocks().stream().filter(b -> !b.getBlockState().isAir() && !b.isPlaced(helper.getLevel()))
                            .collect(java.util.stream.Collectors.groupingBy(b -> b.getBlockState().toString(), java.util.stream.Collectors.counting()))
                        + " bed=" + soilDiagnostic((FarmPlacement) garden, worker, beds.get(0)));
            });
        }
        helper.runAtTickTime(6400, () -> helper.assertTrue(finished[0], "worker must finish normal complete construction: placed="
                + garden.getBlocksPlaced() + "/" + garden.getBlocksTotal() + "; " + soilDiagnostic((FarmPlacement) garden, worker, beds.get(0))));
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    // Historical entry name retained for existing suite wrappers; the current contract is passive decoration.
    public static void extraSoilIsScopedToOwnedAssignedCompletedTemplateBeds(GameTestHelper helper) throws Exception {
        String owner = PREFIX + "SoilScope";
        registerOwner(helper, owner);
        flat(helper, 1, 1, 45, 25);
        FarmPlacement garden = (FarmPlacement) installFixture(helper, DreamseekersMod.REVERIE_GARDEN, owner, 5, 5);
        var soil = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("pasterdream", "dyedream_dirt"));
        BlockPos bed = garden.getBlocks().stream().filter(b -> b.getBlockState().is(soil)).findFirst().orElseThrow().getBlockPos();
        helper.getLevel().setBlockAndUpdate(bed.above(), Blocks.AIR.defaultBlockState());
        BlockPos wild = outdoorPos(helper, 32, 2, 5);
        helper.getLevel().setBlockAndUpdate(wild, soil.defaultBlockState());
        helper.assertTrue(ResourceSources.getFromBlockPos(bed, helper.getLevel()) == null
                && ResourceSources.getFromBlockPos(wild, helper.getLevel()) == null
                && ResourceSources.getFromItem(soil.asItem()) == null,
                "no global terrain or item resource registration for dyedream dirt");
        FireflyWorker worker = worker(helper, owner, 4, 8);
        GatherResourcesGoal goal = worker.getGatherResourceGoal();
        Method lookup = GatherResourcesGoal.class.getDeclaredMethod("getGatherResourceSource", BlockPos.class);
        lookup.setAccessible(true);
        helper.assertTrue(lookup.invoke(goal, bed) == null, "unassigned worker cannot treat soil as work resource");
        goal.setTargetFarm(garden);
        ResourceSource descriptor = (ResourceSource) lookup.invoke(goal, bed);
        helper.assertTrue(descriptor == null && garden.getAdditionalCropSoilResource(bed) == null,
                "passive garden never turns assigned decorative soil into a manual planting target: " + soilDiagnostic(garden, worker, bed));
        helper.assertTrue(!DreamseekersMod.REVERIE_GARDEN.allowsManualFarming()
                        && !DreamseekersMod.REVERIE_GARDEN.allowsCropReplant()
                        && !DreamseekersMod.REVERIE_GARDEN.allowsCropGrowth(),
                "garden opts out of all three manual-farm policies without changing native farms");
        helper.assertTrue(Buildings.WHEAT_FARM.allowsManualFarming()
                        && Buildings.WHEAT_FARM.allowsCropReplant() && Buildings.WHEAT_FARM.allowsCropGrowth(),
                "ordinary native wheat farm retains manual farming, planting and growth");
        helper.assertTrue(lookup.invoke(goal, wild) == null, "wild soil excluded even while assigned");
        var trackedBeds = FarmPlacement.class.getDeclaredField("farmBlocks");
        trackedBeds.setAccessible(true);
        helper.assertTrue(((List<?>) trackedBeds.get(garden)).size() == 16,
                "all sixteen template beds remain recorded for placement geometry, although crop growth is disabled");
        helper.getLevel().setBlockAndUpdate(bed.above(), Blocks.WHEAT.defaultBlockState());
        helper.assertTrue(lookup.invoke(goal, bed) == null, "occupied crop bed is not charged or replanted again");
        helper.getLevel().setBlockAndUpdate(bed.above(), Blocks.AIR.defaultBlockState());
        helper.getLevel().setBlockAndUpdate(bed, Blocks.STONE.defaultBlockState());
        helper.assertTrue(lookup.invoke(goal, bed) == null, "replaced template soil does not retain stale work eligibility");
        helper.getLevel().setBlockAndUpdate(bed, soil.defaultBlockState());
        ServerLevel otherDimension = helper.getLevel().getServer().getLevel(Level.NETHER);
        helper.assertTrue(otherDimension != null && otherDimension != helper.getLevel(), "second real dimension exists for negative scope test");
        var previousOtherSoil = otherDimension.getBlockState(bed);
        var previousOtherAbove = otherDimension.getBlockState(bed.above());
        try {
            // Same coordinates and legal soil in both worlds must still not bridge dimensions.
            otherDimension.setBlock(bed, soil.defaultBlockState(), 2);
            otherDimension.setBlock(bed.above(), Blocks.AIR.defaultBlockState(), 2);
            helper.assertTrue(otherDimension.getBlockState(bed).is(soil), "foreign-dimension test bed is inside that world's build height");
            garden.setLevel(otherDimension);
            helper.assertTrue(lookup.invoke(goal, bed) == null, "assigned foreign-dimension farm cannot authorize local planting");
        } finally {
            garden.setLevel(helper.getLevel());
            otherDimension.setBlock(bed, previousOtherSoil, 2);
            otherDimension.setBlock(bed.above(), previousOtherAbove, 2);
        }
        worker.setOwnerName(PREFIX + "Foreign");
        helper.assertTrue(lookup.invoke(goal, bed) == null, "foreign worker cannot receive addon-soil fallback");
        worker.setOwnerName(owner);
        garden.isBuilt = false;
        helper.assertTrue(lookup.invoke(goal, bed) == null, "unfinished farm excluded");
        garden.isBuilt = true;
        BuildingServerEvents.getBuildings().remove(garden);
        helper.assertTrue(lookup.invoke(goal, bed) == null, "stale removed farm excluded");
        helper.succeed();
    }

    /** SavedData codec + ordinary deferred restoration, including an entity joining first; not a full restart. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 250)
    public static void savedGatherTargetCannotReplantPassiveGarden(GameTestHelper helper) {
        String owner = PREFIX + "SavedSoil";
        registerOwner(helper, owner);
        Resources account = ResourcesServerEvents.resourcesList.stream()
                .filter(r -> r.ownerName.equals(owner)).findFirst().orElseThrow();
        flat(helper, 2, 2, 30, 24);
        FarmPlacement garden = (FarmPlacement) installFixture(helper, DreamseekersMod.REVERIE_GARDEN, owner, 5, 5);
        List<BlockPos> beds = garden.getBlocks().stream()
                .filter(b -> DreamseekersMod.REVERIE_GARDEN.isCropSoil(b.getBlockState()))
                .map(BuildingBlock::getBlockPos).toList();
        helper.assertTrue(beds.size() == 16 && beds.stream().allMatch(p ->
                        helper.getLevel().getBlockState(p.above()).is(DreamPassiveEconomy.DECORATIVE_WHEAT.get())),
                "saved-work regression starts from a real current decorative garden");
        BlockPos bed = beds.get(0);
        helper.getLevel().setBlock(bed.above(), Blocks.AIR.defaultBlockState(), 2);
        FireflyWorker worker = DreamseekersMod.FIREFLY_WORKER.get().create(helper.getLevel());
        helper.assertTrue(worker != null, "actual worker exists before entity-join restore");
        helper.assertTrue(worker.getItems().isEmpty(), "fresh restored-worker fixture starts with no carried items");
        worker.setOwnerName(owner);
        worker.moveTo(bed.getX() + 0.5, bed.getY() + 1, bed.getZ() + 0.5, 0, 0);
        TargetResourcesSave savedTarget = new TargetResourcesSave();
        savedTarget.unitUUID = worker.getStringUUID();
        savedTarget.targetFarm = garden;
        savedTarget.targetResourceName = ResourceName.FOOD;
        savedTarget.gatherTarget = bed;
        TargetResourcesSaveData saved = new TargetResourcesSaveData();
        saved.targetData.add(savedTarget);
        TargetResourcesSaveData restored = TargetResourcesSaveData.load(saved.save(new CompoundTag()));
        helper.assertTrue(restored.targetData.size() == 1 && restored.targetData.get(0).targetFarm == null
                        && garden.originPos.equals(restored.targetData.get(0).getFarmOrigin()),
                "SavedData retains farm origin without requiring eager building lookup");
        helper.assertTrue(restored.targetData.get(0).targetResourceSource == null,
                "descriptor is deliberately not serialized; no new global soil resource or save schema");
        // Deliberately join before the pending save arrives, as can happen during world startup.
        helper.assertTrue(helper.getLevel().addFreshEntity(worker), "normal entity join precedes pending saved task");
        synchronized (UnitServerEvents.savedTargetResources) {
            UnitServerEvents.savedTargetResources.add(restored.targetData.get(0));
        }
        FireflyWorker cancelledWorker = DreamseekersMod.FIREFLY_WORKER.get().create(helper.getLevel());
        helper.assertTrue(cancelledWorker != null, "second real worker tests command-before-restore cancellation");
        cancelledWorker.setOwnerName(owner);
        cancelledWorker.moveTo(bed.getX() + 2.5, bed.getY() + 1, bed.getZ() + .5, 0, 0);
        helper.assertTrue(helper.getLevel().addFreshEntity(cancelledWorker), "cancellation worker joins normally");
        TargetResourcesSave cancelledTask = TargetResourcesSaveData.load(saved.save(new CompoundTag())).targetData.get(0);
        cancelledTask.unitUUID = cancelledWorker.getStringUUID();
        synchronized (UnitServerEvents.savedTargetResources) {
            UnitServerEvents.savedTargetResources.add(cancelledTask);
        }
        // Use the normal ownership-checked action dispatcher before the next END tick.
        new UnitActionItem(owner, UnitAction.STOP, -1, new int[] {cancelledWorker.getId()}).action(helper.getLevel());
        synchronized (UnitServerEvents.savedTargetResources) {
            helper.assertTrue(UnitServerEvents.savedTargetResources.stream()
                            .noneMatch(s -> s.unitUUID.equals(cancelledWorker.getStringUUID())),
                    "normal STOP immediately invalidates the pending startup snapshot before END tick");
        }
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(!cancelledWorker.getGatherResourceGoal().data.hasData()
                            && !cancelledWorker.getGatherResourceGoal().saveData.hasData()
                            && cancelledWorker.getItems().isEmpty(),
                    "STOP before deferred restore keeps cancelled worker idle with no crop cargo");
            helper.assertTrue(worker.getGatherResourceGoal().getTargetFarm() == garden,
                    "ordinary END-tick restoration assigns the exact saved garden to an early-joining worker");
            helper.assertTrue(!worker.getGatherResourceGoal().saveData.hasData(),
                    "startup snapshot is consumed after copying active work, not retained as stale return checkpoint");
            synchronized (UnitServerEvents.savedTargetResources) {
                helper.assertTrue(UnitServerEvents.savedTargetResources.stream()
                                .noneMatch(s -> s.unitUUID.equals(worker.getStringUUID())
                                        || s.unitUUID.equals(cancelledWorker.getStringUUID())),
                        "active task is consumed once and cancelled task cannot return on a later tick");
            }
        });
        int initialWood = account.wood;
        int initialFood = account.food;
        helper.runAtTickTime(180, () -> {
            helper.assertTrue(!cancelledWorker.getGatherResourceGoal().data.hasData()
                            && !cancelledWorker.getGatherResourceGoal().saveData.hasData()
                            && cancelledWorker.getItems().isEmpty(),
                    "cancelled worker remains idle without gathering/replanting or new cargo");
            var soil = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("pasterdream", "dyedream_dirt"));
            helper.assertTrue(helper.getLevel().getBlockState(bed).is(soil)
                            && helper.getLevel().getBlockState(bed.above()).isAir(),
                    "restored old gather order cannot plant a crop in the passive garden: " + soilDiagnostic(garden, worker, bed));
            helper.assertTrue(account.wood == initialWood,
                    "restored obsolete planting order never charges replant wood");
            helper.assertTrue(account.food >= initialFood && Resources.getTotalResourcesFromItems(worker.getItems()).food == 0,
                    "no crop cargo or food debit from restored manual work; passive income may continue: account=" + account.food + "/initial=" + initialFood
                            + ", carried=" + Resources.getTotalResourcesFromItems(worker.getItems()).food
                            + "; " + soilDiagnostic(garden, worker, bed));
            new UnitActionItem(owner, UnitAction.STOP, -1, new int[] {worker.getId()}).action(helper.getLevel());
            UnitServerEvents.saveGatherTargets(helper.getLevel());
            helper.assertTrue(TargetResourcesSaveData.getInstance(helper.getLevel()).targetData.stream()
                            .noneMatch(s -> s.unitUUID.equals(worker.getStringUUID())
                                    || s.unitUUID.equals(cancelledWorker.getStringUUID())),
                    "ordinary save after STOP contains neither consumed nor cancelled farm task for a future restart");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 450)
    public static void actualUnitsNavigateThreeWideTrainingEntries(GameTestHelper helper) {
        String owner = PREFIX + "Entry";
        registerOwner(helper, owner);
        flat(helper, 1, 1, 75, 25);
        EntityType<?>[] types = {DreamseekersMod.FIREFLY_WORKER.get(),
                DreamseekersMod.BEETLE.get(), DreamseekersMod.BASALT_SNAIL.get()};
        float[][] sizes = {{0.5F, 0.5F}, {0.9F, 0.65F}, {1.1F, 1.1F}};
        for (int i = 0; i < types.length; i++) {
            BuildingPlacement court = installFixture(helper, DreamseekersMod.BEETLE_SANCTUARY, owner, 5 + 22 * i, 6);
            var created = types[i].create(helper.getLevel());
            helper.assertTrue(created instanceof Mob && created instanceof Unit, "genuine addon unit instance");
            Mob mob = (Mob) created;
            ((Unit) mob).setOwnerName(owner);
            mob.refreshDimensions();
            helper.assertTrue(Math.abs(mob.getBbWidth() - sizes[i][0]) < 0.001F
                    && Math.abs(mob.getBbHeight() - sizes[i][1]) < 0.001F, "actual instance dimensions match approved passage contract");
            BlockPos start = court.originPos.offset(5, 1, -2);
            BlockPos goal = court.originPos.offset(5, 2, 8);
            mob.moveTo(start.getX() + 0.5, start.getY(), start.getZ() + 0.5, 0, 0);
            helper.assertTrue(helper.getLevel().addFreshEntity(mob), "real navigation entity spawned outside entry");
            UnitServerEvents.addActionItem(owner, UnitAction.MOVE, -1, new int[]{mob.getId()}, goal, BlockPos.ZERO);
            helper.runAtTickTime(350, () -> {
                helper.assertTrue(mob.isAlive() && mob.distanceToSqr(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5) < 4,
                        "real MOVE traverses the finished three-wide court entrance");
                helper.assertTrue(helper.getLevel().noCollision(mob), "arrived unit has no block collision");
            });
        }
        helper.runAtTickTime(360, helper::succeed);
    }

    static String soilDiagnostic(FarmPlacement farm, FireflyWorker worker, BlockPos bed) {
        var level = worker.level();
        var replant = worker.getReplantBlockState();
        ResourceSource direct = farm.getAdditionalCropSoilResource(bed);
        return "pos=" + bed + ", soil=" + level.getBlockState(bed) + ", above=" + level.getBlockState(bed.above())
                + ", assigned=" + (worker.getGatherResourceGoal().getTargetFarm() == farm)
                + ", built=" + farm.isBuilt + ", sameLevel=" + (farm.getLevel() == level)
                + ", sameOwner=" + worker.getOwnerName().equals(farm.ownerName)
                + ", live=" + BuildingUtils.getBuildingsList(level.isClientSide()).contains(farm)
                + ", inside=" + farm.isPosInsideBuilding(bed)
                + ", direct=" + (direct == null ? "null" : direct.name + "/" + direct.resourceName + "/" + direct.resourceValue
                    + "/items=" + direct.items.size() + "/blocks=" + direct.validBlocks.size())
                + ", replant=" + replant + ", canSurvive=" + replant.canSurvive(level, bed.above())
                + ", light=" + level.getRawBrightness(bed.above(), 0) + ", sky=" + level.canSeeSky(bed.above())
                + ", ceiling=" + level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, bed.getX(), bed.getZ());
    }

    private static void registerOwner(GameTestHelper helper, String owner) {
        helper.assertTrue(PlayerServerEvents.getRTSPlayer(owner) == null, "isolated test owner");
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner, DreamseekersMod.DREAMSEEKERS));
        ResourcesServerEvents.resourcesList.add(new Resources(owner, 10000, 10000, 10000));
    }

    // GameTest's arena is below ground in RoN's normal world (y=-60, surface y=63).
    // Keep the original layout on a genuinely outdoor platform, within Nether build height
    // as well so the same-coordinate foreign-dimension negative case remains meaningful.
    static BlockPos outdoorPos(GameTestHelper helper, int x, int y, int z) {
        BlockPos horizontal = helper.absolutePos(new BlockPos(x, 0, z));
        return new BlockPos(horizontal.getX(), Math.min(160, helper.getLevel().getMaxBuildHeight() - 24) + y, horizontal.getZ());
    }

    private static void flat(GameTestHelper helper, int x, int z, int width, int depth) {
        BlockPos origin = outdoorPos(helper, x, 1, z);
        for (int dx = 0; dx < width; dx++) for (int dz = 0; dz < depth; dz++) {
            helper.getLevel().setBlock(origin.offset(dx, 0, dz), Blocks.STONE.defaultBlockState(), 2);
            for (int y = 1; y < 16; y++) helper.getLevel().setBlock(origin.offset(dx, y, dz), Blocks.AIR.defaultBlockState(), 2);
        }
        clearPreexistingFixtureDrops(helper, origin, width, depth);
        helper.assertTrue(helper.getLevel().canSeeSky(origin.above(2)), "outdoor test platform has real skylight before crop placement");
    }

    /** Initialization only: old outdoor fixtures are outside the vanilla arena cleanup box.
     * Clearing their soil can drop old crops. Remove those pre-existing local drops before
     * any new structure/worker is created, never while testing harvest/replant behavior. */
    static void clearPreexistingFixtureDrops(GameTestHelper helper, BlockPos origin, int width, int depth) {
        AABB area = new AABB(origin.getX(), origin.getY() - 1, origin.getZ(),
                origin.getX() + width, origin.getY() + 17, origin.getZ() + depth);
        var stale = helper.getLevel().getEntitiesOfClass(ItemEntity.class, area);
        if (!stale.isEmpty()) System.out.println("DREAMSEEKERS_FIXTURE_OLD_DROPS_REMOVED origin=" + origin
                + " drops=" + stale.stream().map(e -> e.getItem().toString()).toList());
        stale.forEach(ItemEntity::discard);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).isEmpty(),
                "local outdoor platform contains no pre-existing item drops before new fixtures");
    }

    private static BuildingPlacement installFixture(GameTestHelper helper, Building building, String owner, int x, int z) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = outdoorPos(helper, x, 1, z);
        for (BuildingBlock block : BuildingUtils.getAbsoluteBlockData(building.getRelativeBlockData(level), level, origin, Rotation.NONE))
            level.setBlock(block.getBlockPos(), block.getBlockState(), 2);
        BuildingPlacement result = BuildingUtils.getNewBuildingPlacement(building, level, origin, Rotation.NONE, owner, false);
        BuildingServerEvents.getBuildings().add(result);
        result.onBuilt();
        return result;
    }

    private static FireflyWorker worker(GameTestHelper helper, String owner, int x, int z) {
        FireflyWorker worker = DreamseekersMod.FIREFLY_WORKER.get().create(helper.getLevel());
        helper.assertTrue(worker != null, "actual worker type exists");
        worker.setOwnerName(owner);
        BlockPos p = outdoorPos(helper, x, 2, z);
        worker.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
        helper.assertTrue(helper.getLevel().addFreshEntity(worker), "actual worker spawned");
        return worker;
    }
}
