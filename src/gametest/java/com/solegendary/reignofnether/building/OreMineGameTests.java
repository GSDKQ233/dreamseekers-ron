package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.buildings.shared.PassiveOreBuilding;
import com.solegendary.reignofnether.building.data.DataStorage;
import com.solegendary.reignofnether.building.data.OreProductionState;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.player.RTSPlayerScoresEnum;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesSaveData;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Opt-in headless Minecraft/Forge integration checks; excluded from the release jar. */
@GameTestHolder(ReignOfNether.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OreMineGameTests {
    private static final String PREFIX = "OreTest_";
    private static final String BATCH = "ore_mine_runtime";

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        // Prepare intentionally leaves the probe for the normal server-stopping save path.
        if (!System.getProperty("ore.mine.restart.phase", "").equals("prepare")) cleanupTestState();
    }

    private static void cleanupTestState() {
        BuildingServerEvents.getBuildings().removeIf(b -> b.ownerName.startsWith(PREFIX) || b.ownerName.isEmpty());
        PlayerServerEvents.rtsPlayers.removeIf(p -> p.name.startsWith(PREFIX));
        ResourcesServerEvents.resourcesList.removeIf(r -> r.ownerName.startsWith(PREFIX));
    }

    @GameTest(template = "ore_mine_test_arena", batch = BATCH, timeoutTicks = 900)
    public static void serverIncomePersistenceAndValidation(GameTestHelper helper) {
        String restartPhase = System.getProperty("ore.mine.restart.phase", "");
        if (!restartPhase.isEmpty()) {
            runSeparateProcessRestart(helper, restartPhase);
            return;
        }
        ServerLevel level = helper.getLevel();
        cleanupTestState();
        TestContext t = new TestContext(helper);
        testStructurePalettesAndRotations(t);
        Resources a = account("A", Factions.VILLAGERS, 0);
        Resources m = account("M", Factions.MONSTERS, 0);
        Resources p = account("P", Factions.PIGLINS, 0);
        Resources incomplete = account("Incomplete", Factions.VILLAGERS, 0);
        Resources nextOwner = account("NewOwner", Factions.VILLAGERS, 0);
        Resources max = account("Max", Factions.VILLAGERS, Integer.MAX_VALUE - 1);
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(PREFIX + "NoAccount", Factions.VILLAGERS));
        Resources orphan = new Resources(PREFIX + "NoRts", 0, 0, 0);
        ResourcesServerEvents.resourcesList.add(orphan);

        BuildingPlacement mineA = t.install(Buildings.VILLAGER_ORE_MINE, "A", 4, 4, true);
        BuildingPlacement mineA2 = t.install(Buildings.VILLAGER_ORE_MINE, "A", 28, 4, true);
        BuildingPlacement mineM = t.install(Buildings.MONSTER_ORE_MINE, "M", 52, 4, true);
        BuildingPlacement mineP = t.install(Buildings.PIGLIN_ORE_MINE, "P", 76, 4, true);
        BuildingPlacement unfinished = t.install(Buildings.VILLAGER_ORE_MINE, "Incomplete", 4, 28, false);
        BuildingPlacement missingAccount = t.install(Buildings.VILLAGER_ORE_MINE, "NoAccount", 28, 28, true);
        BuildingPlacement missingPlayer = t.install(Buildings.VILLAGER_ORE_MINE, "NoRts", 52, 28, true);
        BuildingPlacement capped = t.install(Buildings.VILLAGER_ORE_MINE, "Max", 76, 28, true);
        BuildingPlacement unowned = t.install(Buildings.MONSTER_ORE_MINE, "", 4, 52, true);
        // Keeps A alive when its mine is destroyed; this is the actual prerequisite structure.
        t.install(Buildings.BLACKSMITH, "A", 28, 52, true);

        helper.runAtTickTime(85, () -> {
            for (BuildingPlacement mine : List.of(mineA, mineA2, mineM, mineP))
                t.check(mine.isBuilt, "fully placed structure must complete through the real level tick");
            t.equal(a.ore, completed(mineA) + completed(mineA2), "two placements have independent server income");
            t.equal(m.ore, completed(mineM), "monster income uses actual server ticks");
            t.equal(p.ore, completed(mineP), "piglin income uses actual server ticks");
            t.check(!unfinished.isBuilt, "partial foundation remains incomplete without workers");
            t.equal(incomplete.ore, 0, "unfinished building produces nothing");
            t.equal(orphan.ore, 0, "resource account without RTS owner cannot produce");
            for (BuildingPlacement inactive : List.of(unfinished, missingAccount, missingPlayer, unowned))
                t.equal(state(inactive).getElapsedTicks(), 0, "inactive building cannot accumulate progress");
            t.equal(max.ore, Integer.MAX_VALUE, "ore saturates without integer overflow");
            RTSPlayer playerA = PlayerServerEvents.getRTSPlayer(a.ownerName);
            t.equal(playerA.scores.scoreList.get(RTSPlayerScoresEnum.ORES_HARVESTED), a.ore, "ore score updated");
            t.equal(playerA.scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_RESOURCES_HARVESTED), a.ore, "total score updated");
            testPlacementValidation(t);
        });

        final int[] ownerBaseline = new int[1];
        helper.runAtTickTime(100, () -> {
            mineA2.getDataStorage().setData(PassiveOreBuilding.PRODUCTION_STATE,
                    OreProductionState.fromSaved(39, a.ownerName));
            mineA2.ownerName = nextOwner.ownerName;
            ownerBaseline[0] = a.ore;
        });
        helper.runAtTickTime(101, () -> {
            t.equal(nextOwner.ore, 0, "ownership transfer cannot award the old owner's nearly completed cycle");
            t.equal(state(mineA2).getElapsedTicks(), 1, "new owner starts a fresh cycle on the next server tick");
        });
        helper.runAtTickTime(141, () -> {
            t.equal(nextOwner.ore, 1, "new owner receives one cycle after 40 eligible ticks");
            t.check(a.ore >= ownerBaseline[0], "old owner retains its independently operating mine");
        });

        final BuildingPlacement[] restored = {mineM};
        final int[] saveBaseline = new int[2];
        helper.runAtTickTime(160, () -> {
            try {
                mineM.getDataStorage().setData(PassiveOreBuilding.PRODUCTION_STATE,
                        OreProductionState.fromSaved(23, m.ownerName));
                BuildingServerEvents.saveBuildings(level);
                ResourcesServerEvents.saveResources(level);
                CompoundTag saved = BuildingSaveData.getInstance(level).save(new CompoundTag());
                File snapshot = new File("ore-mine-runtime-building-snapshot.nbt");
                NbtIo.writeCompressed(saved, snapshot);
                BuildingSaveData loaded = BuildingSaveData.load(NbtIo.readCompressed(snapshot), level.getServer());
                BuildingSave b = loaded.buildings.stream().filter(x -> x.originPos.equals(mineM.originPos)).findFirst().orElseThrow();
                t.check(b.building == Buildings.MONSTER_ORE_MINE, "registry identity survives BuildingSaveData disk roundtrip");
                t.check(b.isBuilt && b.ownerName.equals(m.ownerName), "completion and ownership survive disk roundtrip");
                saveBaseline[0] = m.ore;
                saveBaseline[1] = state(mineM).getElapsedTicks();
                t.equal(saveBaseline[1], 23, "fixture must save a genuinely partial cycle");
                t.equal(b.dataStorage.getData(PassiveOreBuilding.PRODUCTION_STATE).getElapsedTicks(), saveBaseline[1], "partial cycle persisted");
                DataStorage secondRoundtrip = DataStorage.read(b.dataStorage.write(), level.getServer());
                t.equal(secondRoundtrip.getData(PassiveOreBuilding.PRODUCTION_STATE).getElapsedTicks(), saveBaseline[1], "registered DataType rehydrates");
                BuildingPlacement fresh = BuildingUtils.getNewBuildingPlacement(b.building, level, b.originPos, b.rotation, b.ownerName, b.isDiagonalBridge);
                fresh.dataStorage = secondRoundtrip;
                fresh.isBuilt = b.isBuilt;
                BuildingServerEvents.getBuildings().remove(mineM);
                BuildingServerEvents.getBuildings().add(fresh);
                restored[0] = fresh;
                t.equal(m.ore, saveBaseline[0], "rehydration itself must not grant ore");
                ResourcesSaveData resourceData = ResourcesSaveData.load(ResourcesSaveData.getInstance(level).save(new CompoundTag()));
                Resources restoredAccount = resourceData.resources.stream().filter(x -> x.ownerName.equals(m.ownerName)).findFirst().orElseThrow();
                t.equal(restoredAccount.ore, m.ore, "resource ledger roundtrip preserves credited ore");
            } catch (Exception e) {
                throw new RuntimeException("real BuildingSaveData NBT disk roundtrip failed", e);
            }
        });
        helper.runAtTickTime(205, () -> {
            t.equal(m.ore, saveBaseline[0] + (saveBaseline[1] + (int) restored[0].getTickAgeAfterBuilt()) / OreProductionState.INTERVAL_TICKS,
                    "restored placement resumes partial cycle without duplicate or offline payout");
        });

        final int[] pausedBalance = new int[1];
        helper.runAtTickTime(210, () -> {
            restored[0].getDataStorage().setData(PassiveOreBuilding.PRODUCTION_STATE,
                    OreProductionState.fromSaved(39, m.ownerName));
            pausedBalance[0] = m.ore;
            ResourcesServerEvents.resourcesList.remove(m);
        });
        helper.runAtTickTime(250, () -> {
            t.equal(state(restored[0]).getElapsedTicks(), 39, "missing account pauses an existing partial cycle");
            t.equal(m.ore, pausedBalance[0], "missing account does not receive hidden income");
            ResourcesServerEvents.resourcesList.add(m);
        });
        helper.runAtTickTime(251, () -> {
            t.equal(m.ore, pausedBalance[0] + 1, "restored account resumes exactly the remaining eligible tick");
        });

        final int[] destroyedBalance = new int[1];
        helper.runAtTickTime(230, () -> {
            destroyedBalance[0] = a.ore;
            for (BuildingBlock block : mineA.getBlocks())
                level.setBlock(block.getBlockPos(), Blocks.AIR.defaultBlockState(), 2);
            mineA.initPlacedBlocks();
        });
        helper.runAtTickTime(280, () -> {
            t.check(!BuildingServerEvents.getBuildings().contains(mineA), "destroyed mine is removed by actual BuildingServerEvents");
            t.equal(a.ore, destroyedBalance[0], "destroyed mine cannot keep granting ore");
            t.equal(incomplete.ore, 0, "incomplete building still has no income after 280 ticks");
            t.equal(max.ore, Integer.MAX_VALUE, "full account remains non-overflowing");
        });
        final int[] normalReloadBaseline = new int[2];
        helper.runAtTickTime(700, () -> {
            t.check(t.queuedMine != null && t.queuedMine.isBuilt, "actual self-building queue completes the purchased structure");
            t.equal(t.queueAccount.ore, t.queueOreBaseline + completed(t.queuedMine), "queued building only earns after real onBuilt");
            t.check(t.queueAccount.ore > t.queueOreBaseline, "queued building eventually produces server income");
            try {
                restored[0].getDataStorage().setData(PassiveOreBuilding.PRODUCTION_STATE,
                        OreProductionState.fromSaved(23, m.ownerName));
                BuildingServerEvents.saveBuildings(level);
                normalReloadBaseline[0] = m.ore;
                normalReloadBaseline[1] = 23;
                File snapshot = new File("ore-mine-runtime-normal-loader-snapshot.nbt");
                NbtIo.writeCompressed(BuildingSaveData.getInstance(level).save(new CompoundTag()), snapshot);
                BuildingSaveData decoded = BuildingSaveData.load(NbtIo.readCompressed(snapshot), level.getServer());
                level.getDataStorage().set("saved-building-data", decoded);
                BuildingServerEvents.loadBuildingsAndNetherZones(new ServerStartedEvent(level.getServer()));
                restored[0] = BuildingServerEvents.getBuildings().stream()
                        .filter(b -> b.originPos.equals(mineM.originPos)).findFirst().orElseThrow();
                t.check(restored[0].isBuilt && restored[0].getBuilding() == Buildings.MONSTER_ORE_MINE,
                        "normal production loader reconstructs completed registered mine");
                t.equal(state(restored[0]).getElapsedTicks(), 23, "normal loader preserves decoded nonzero cycle");
                t.equal(m.ore, normalReloadBaseline[0], "normal loader does not immediately award ore");
            } catch (Exception e) {
                throw new RuntimeException("normal building loader disk roundtrip failed", e);
            }
        });
        helper.runAtTickTime(745, () -> {
            t.equal(m.ore, normalReloadBaseline[0] + (normalReloadBaseline[1] + (int) restored[0].getTickAgeAfterBuilt()) / OreProductionState.INTERVAL_TICKS,
                    "normal production loader resumes persisted income exactly");
            ReignOfNether.LOGGER.info("ORE_MINE_RUNTIME_PASS assertions={} (real server ticks, construction queue and normal-loader NBT disk roundtrip; not worker AI, process restart or client runtime)", t.assertions);
            helper.succeed();
        });
    }

    private static void runSeparateProcessRestart(GameTestHelper helper, String phase) {
        TestContext t = new TestContext(helper);
        String owner = PREFIX + "Restart";
        if (phase.equals("prepare")) {
            cleanupTestState();
            account("Restart", Factions.MONSTERS, 0);
            // Outside the empty arena, so the next process's GameTest arena reset cannot erase it.
            BuildingPlacement probe = t.install(Buildings.MONSTER_ORE_MINE, "Restart", 128, 4, true);
            helper.runAtTickTime(45, () -> {
                t.check(probe.isBuilt, "restart probe completes by real server tick");
                t.check(probe.getBlocksPlaced() > 0, "restart probe exists in actual world chunks");
                probe.getDataStorage().setData(PassiveOreBuilding.PRODUCTION_STATE,
                        OreProductionState.fromSaved(23, owner));
                ReignOfNether.LOGGER.info("ORE_MINE_RESTART_PREPARED (normal shutdown must finish before copying disk baselines)");
                helper.succeed();
            });
        } else if (phase.equals("verify")) {
            try {
                BuildingSaveData before = BuildingSaveData.load(
                        NbtIo.readCompressed(new File("restart-baseline-buildings.dat")).getCompound("data"), t.level.getServer());
                ResourcesSaveData balances = ResourcesSaveData.load(
                        NbtIo.readCompressed(new File("restart-baseline-resources.dat")).getCompound("data"));
                BuildingSave saved = before.buildings.stream().filter(b -> b.ownerName.equals(owner)).findFirst().orElseThrow();
                Resources savedBalance = balances.resources.stream().filter(r -> r.ownerName.equals(owner)).findFirst().orElseThrow();
                BuildingPlacement probe = BuildingServerEvents.getBuildings().stream()
                        .filter(b -> b.ownerName.equals(owner)).findFirst().orElseThrow();
                Resources liveBalance = ResourcesServerEvents.resourcesList.stream()
                        .filter(r -> r.ownerName.equals(owner)).findFirst().orElseThrow();
                int elapsed = saved.dataStorage.getData(PassiveOreBuilding.PRODUCTION_STATE).getElapsedTicks();
                t.check(elapsed > 0 && elapsed < OreProductionState.INTERVAL_TICKS, "actual stopped-process disk contains a nonzero partial cycle");
                t.check(PlayerServerEvents.getRTSPlayer(owner) != null, "RTS owner loaded by ordinary server startup");
                t.check(probe.isBuilt && probe.getBuilding() == Buildings.MONSTER_ORE_MINE, "completed registered building loaded by ordinary startup");
                t.check(probe.originPos.equals(saved.originPos), "building coordinates survive full process restart");
                t.check(probe.getBlocksPlaced() > 0, "world chunk blocks survive full process restart");
                t.equal(liveBalance.ore, savedBalance.ore + (elapsed + (int) probe.getTickAgeAfterBuilt()) / OreProductionState.INTERVAL_TICKS,
                        "startup grants no offline bonus or duplicate partial cycle");
                helper.runAtTickTime(45, () -> {
                    t.equal(liveBalance.ore, savedBalance.ore + (elapsed + (int) probe.getTickAgeAfterBuilt()) / OreProductionState.INTERVAL_TICKS,
                            "actual new JVM continues saved cycle through real server ticks");
                    t.equal(state(probe).getElapsedTicks(), (elapsed + (int) probe.getTickAgeAfterBuilt()) % OreProductionState.INTERVAL_TICKS,
                            "post-restart partial progress remains exact");
                    ReignOfNether.LOGGER.info("ORE_MINE_PROCESS_RESTART_PASS assertions={} savedOre={} savedElapsed={} age={}",
                            t.assertions, savedBalance.ore, elapsed, probe.getTickAgeAfterBuilt());
                    helper.succeed();
                });
            } catch (Exception e) {
                throw new RuntimeException("separate-process ordinary saved-world reload failed", e);
            }
        } else {
            throw new IllegalArgumentException("Unknown oreRestartPhase: " + phase);
        }
    }

    private static void testPlacementValidation(TestContext t) {
        Resources funded = account("Build", Factions.VILLAGERS, 5000);
        funded.wood = 5000;
        BlockPos first = t.prepareSite(52, 52, false);
        int wood = funded.wood, ore = funded.ore;
        t.check(t.place(Buildings.VILLAGER_ORE_MINE, first, funded.ownerName) == null, "missing prerequisite denied by real placement entry point");
        t.equal(funded.wood, wood, "rejected build does not charge wood");
        t.equal(funded.ore, ore, "rejected build does not charge ore");
        BuildingPlacement prerequisite = t.install(Buildings.BLACKSMITH, "Build", 4, 80, true);
        prerequisite.onBuilt();
        t.check(t.place(Buildings.MONSTER_ORE_MINE, first, funded.ownerName) == null, "wrong faction denied serverside");
        BuildingPlacement one = t.place(Buildings.VILLAGER_ORE_MINE, first, funded.ownerName);
        t.check(one != null && !one.isBuilt, "valid purchase creates unfinished building");
        t.equal(funded.wood, wood - Buildings.VILLAGER_ORE_MINE.cost.wood, "purchase charges wood once");
        t.equal(funded.ore, ore - Buildings.VILLAGER_ORE_MINE.cost.ore, "purchase charges ore once");
        t.check(t.place(Buildings.VILLAGER_ORE_MINE, first, funded.ownerName) == null, "duplicate placement denied");
        t.equal(funded.ore, ore - Buildings.VILLAGER_ORE_MINE.cost.ore, "duplicate does not charge again");
        BuildingPlacement two = t.place(Buildings.VILLAGER_ORE_MINE, t.prepareSite(76, 52, false), funded.ownerName);
        t.check(two != null && !two.isBuilt, "second unfinished mine is allowed");
        int oreBeforeThird = funded.ore;
        t.check(t.place(Buildings.VILLAGER_ORE_MINE, t.prepareSite(52, 80, false), funded.ownerName) == null, "two unfinished mines already meet cap");
        t.equal(funded.ore, oreBeforeThird, "cap rejection does not charge");
        // Accelerate through the real queue/buildNextBlock path, not direct isBuilt mutation.
        one.selfBuilding = true;
        t.queuedMine = one;
        t.queueAccount = funded;
        t.queueOreBaseline = funded.ore;
        Resources poor = account("Poor", Factions.VILLAGERS, 0);
        BuildingPlacement poorPrereq = t.install(Buildings.BLACKSMITH, "Poor", 28, 80, true);
        poorPrereq.onBuilt();
        t.check(t.place(Buildings.VILLAGER_ORE_MINE, t.prepareSite(76, 80, false), poor.ownerName) == null, "unaffordable purchase denied");
        t.equal(poor.ore, 0, "unaffordable request never creates negative balance");
        Resources piglin = account("Terrain", Factions.PIGLINS, 5000);
        piglin.wood = 5000;
        BuildingPlacement basalt = t.install(Buildings.BASALT_SPRINGS, "Terrain", 4, 104, true);
        basalt.onBuilt();
        BlockPos terrain = t.prepareSite(28, 104, false);
        t.check(t.place(Buildings.PIGLIN_ORE_MINE, terrain, piglin.ownerName) == null, "piglin mine rejects ordinary stone ground");
        t.equal(piglin.ore, 5000, "wrong terrain never charges resources");
        t.prepareSite(28, 104, true);
        t.check(t.place(Buildings.PIGLIN_ORE_MINE, terrain, piglin.ownerName) != null, "piglin mine accepts fully nether ground");
        t.equal(piglin.ore, 5000 - Buildings.PIGLIN_ORE_MINE.cost.ore, "piglin purchase charges exactly once");
        BlockPos unsupported = t.prepareSite(52, 104, true);
        for (int x = -1; x < 22; x++) for (int z = -1; z < 19; z++)
            t.level.setBlock(unsupported.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 2);
        int beforeUnsupported = piglin.ore;
        t.check(t.place(Buildings.PIGLIN_ORE_MINE, unsupported, piglin.ownerName) == null, "unsupported foundation is denied");
        t.equal(piglin.ore, beforeUnsupported, "unsupported request never charges resources");
    }

    private static void testStructurePalettesAndRotations(TestContext t) {
        for (Building mine : List.of(Buildings.VILLAGER_ORE_MINE, Buildings.MONSTER_ORE_MINE, Buildings.PIGLIN_ORE_MINE)) {
            CompoundTag nbt = BuildingBlockData.getBuildingNbt(mine.structureName, t.level.getServer().getResourceManager());
            t.check(nbt != null, "structure resource exists: " + mine.structureName);
            ListTag palette = nbt.getList("palette", 10);
            t.check(!palette.isEmpty(), "structure palette is nonempty");
            for (int i = 0; i < palette.size(); i++) {
                CompoundTag entry = palette.getCompound(i);
                ResourceLocation id = ResourceLocation.tryParse(entry.getString("Name"));
                t.check(id != null && BuiltInRegistries.BLOCK.containsKey(id), "block registry contains " + entry.getString("Name"));
                Block block = BuiltInRegistries.BLOCK.get(id);
                CompoundTag properties = entry.getCompound("Properties");
                for (String key : properties.getAllKeys()) {
                    Property<?> property = block.getStateDefinition().getProperty(key);
                    t.check(property != null, "registered block property " + key);
                    t.check(property.getValue(properties.getString(key)).isPresent(), "legal property value " + key + "=" + properties.getString(key));
                }
            }
            for (Rotation rotation : Rotation.values()) {
                ArrayList<BuildingBlock> relative = mine.getRelativeBlockData(t.level);
                ArrayList<BuildingBlock> absolute = BuildingUtils.getAbsoluteBlockData(relative, t.level, BlockPos.ZERO, rotation);
                t.equal(absolute.size(), relative.size(), "rotation preserves block count");
                for (int i = 0; i < absolute.size(); i++) {
                    t.check(absolute.get(i).getBlockPos().equals(relative.get(i).getBlockPos().rotate(rotation).above()), "rotation preserves expected footprint coordinate");
                    t.check(absolute.get(i).getBlockState().getBlock() == relative.get(i).getBlockState().getBlock(), "rotation preserves registered block identity");
                }
            }
        }
    }

    private static int completed(BuildingPlacement placement) {
        return (int) (placement.getTickAgeAfterBuilt() / OreProductionState.INTERVAL_TICKS);
    }

    private static OreProductionState state(BuildingPlacement placement) {
        return placement.getDataStorage().getData(PassiveOreBuilding.PRODUCTION_STATE);
    }

    private static Resources account(String suffix, Faction faction, int ore) {
        String owner = PREFIX + suffix;
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner, faction));
        Resources resources = new Resources(owner, 0, 0, ore);
        ResourcesServerEvents.resourcesList.add(resources);
        return resources;
    }

    private static final class TestContext {
        final GameTestHelper helper;
        final ServerLevel level;
        int assertions;
        BuildingPlacement queuedMine;
        Resources queueAccount;
        int queueOreBaseline;
        TestContext(GameTestHelper helper) { this.helper = helper; this.level = helper.getLevel(); }
        void check(boolean condition, String message) { assertions++; helper.assertTrue(condition, message); }
        void equal(int actual, int expected, String message) { check(actual == expected, message + " expected=" + expected + " actual=" + actual); }
        BlockPos prepareSite(int x, int z, boolean nether) {
            BlockPos origin = helper.absolutePos(new BlockPos(x, 1, z));
            for (int dx = -1; dx < 22; dx++) for (int dz = -1; dz < 19; dz++) {
                level.setBlock(origin.offset(dx, 0, dz), (nether ? Blocks.NETHERRACK : Blocks.STONE).defaultBlockState(), 2);
                for (int dy = 1; dy < 14; dy++) level.setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
            }
            return origin;
        }
        BuildingPlacement install(Building building, String suffix, int x, int z, boolean complete) {
            BlockPos origin = prepareSite(x, z, building.getFaction().equals(Factions.PIGLINS));
            ArrayList<BuildingBlock> blocks = BuildingUtils.getAbsoluteBlockData(building.getRelativeBlockData(level), level, origin, Rotation.NONE);
            int placed = 0;
            for (BuildingBlock block : blocks) {
                if (complete || (placed < 3 && !block.getBlockState().isAir())) {
                    level.setBlock(block.getBlockPos(), block.getBlockState(), 2);
                    if (!block.getBlockState().isAir()) placed++;
                }
            }
            String owner = suffix.isEmpty() ? "" : PREFIX + suffix;
            BuildingPlacement result = BuildingUtils.getNewBuildingPlacement(building, level, origin, Rotation.NONE, owner, false);
            BuildingServerEvents.getBuildings().add(result);
            return result;
        }
        BuildingPlacement place(Building building, BlockPos origin, String owner) {
            return BuildingServerEvents.placeBuilding(building, origin, Rotation.NONE, owner, new int[0], false, false, false, true);
        }
    }
}
