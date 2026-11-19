package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.resources.ResourcesSaveData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.File;

/** Opt-in integration test: no test code is included in release jars. */
@GameTestHolder("reignofnether_pending_construction")
@PrefixGameTestTemplate(false)
public final class PendingConstructionGameTests {
    private static final String OWNER = "PendingConstructionTest_Owner";

    @GameTest(templateNamespace = "reignofnether_pending_construction", template = "ore_mine_test_arena", timeoutTicks = 100)
    public static void sameTickShutdownRetainsOnlyQueuedWork(GameTestHelper h) throws Exception {
        String phase = System.getProperty("building.pending.restart.phase", "");
        if (phase.equals("verify")) {
            verifyProcessRestart(h);
            return;
        }
        if (!phase.isEmpty() && !phase.equals("prepare")) throw new IllegalArgumentException("Unknown phase: " + phase);
        var level = h.getLevel();
        var server = level.getServer();
        h.assertTrue(BuildingServerEvents.getBuildings().isEmpty(), "isolated test world starts without live buildings");
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER, Factions.VILLAGERS));
        Resources account = new Resources(OWNER, 5000, 5000, 5000);
        ResourcesServerEvents.resourcesList.add(account);
        BlockPos origin = h.absolutePos(new BlockPos(phase.equals("prepare") ? 128 : 4, 1, 4));
        for (int x = -1; x < 22; x++) for (int z = -1; z < 19; z++) {
            level.setBlock(origin.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 2);
            for (int y = 1; y < 14; y++) level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        // The queue test is not an availability bypass: supply the newly authoritative native prerequisite.
        BlockPos prerequisitePos = origin.offset(-30, 1, 0);
        BuildingPlacement prerequisite = new BuildingPlacement(Buildings.TOWN_CENTRE, level, prerequisitePos,
                Rotation.NONE, OWNER, new java.util.ArrayList<>(java.util.List.of(
                        new BuildingBlock(prerequisitePos, Blocks.STONE.defaultBlockState()))), true);
        prerequisite.isBuilt = true;
        BuildingServerEvents.getBuildings().add(prerequisite);
        BuildingPlacement purchased;
        try {
            purchased = BuildingServerEvents.placeBuilding(Buildings.VILLAGER_HOUSE,
                    origin, Rotation.NONE, OWNER, new int[0], false, false, false, true);
        } finally { BuildingServerEvents.getBuildings().remove(prerequisite); }
        h.assertTrue(purchased != null, "real funded placement accepted");
        h.assertTrue(purchased.getBlocksPlaced() == 0 && !purchased.blockPlaceQueue.isEmpty(),
                "purchase has zero physical blocks and actual pending foundation before its first tick");
        h.assertTrue(account.wood == 5000 - Buildings.VILLAGER_HOUSE.cost.wood
                        && account.ore == 5000 - Buildings.VILLAGER_HOUSE.cost.ore,
                "real purchase charged once");
        int paidWood = account.wood, paidOre = account.ore;
        CompoundTag pending = purchased.savePendingConstruction();
        int queued = purchased.blockPlaceQueue.size();

        if (phase.equals("prepare")) {
            // Test-only scheduling gate: keep the actual paid queue untouched while the test runner
            // winds down. The field is transient, so the new JVM uses normal queue consumption.
            purchased.maxBlocksPerTick = 0;
            h.runAtTickTime(5, () -> {
                h.assertTrue(purchased.getBlocksPlaced() == 0 && purchased.savePendingConstruction().equals(pending),
                        "zero physical foundation and exact paid queue remain until normal shutdown");
                System.out.println("PENDING_CONSTRUCTION_RESTART_PREPARED zero_blocks normal_shutdown_required");
                h.succeed();
            });
            return;
        }

        // Exercise the actual stopping save, NBT disk codec, and ordinary production loader in one tick.
        BuildingServerEvents.onServerStopping(new ServerStoppingEvent(server));
        File file = new File("pending-construction-roundtrip.nbt");
        NbtIo.writeCompressed(BuildingSaveData.getInstance(level).save(new CompoundTag()), file);
        BuildingSaveData decoded = BuildingSaveData.load(NbtIo.readCompressed(file), server);
        level.getDataStorage().set("saved-building-data", decoded);
        BuildingServerEvents.loadBuildingsAndNetherZones(new ServerStartedEvent(server));
        BuildingPlacement restored = BuildingServerEvents.getBuildings().stream()
                .filter(b -> b.ownerName.equals(OWNER)).findFirst().orElseThrow();
        h.assertTrue(restored.getBlocksPlaced() == 0 && restored.blockPlaceQueue.size() == queued
                && restored.savePendingConstruction().equals(pending) && !restored.shouldBeDestroyed(),
                "normal loader restores exact pending work without inventing physical blocks");
        h.assertTrue(account.wood == paidWood && account.ore == paidOre,
                "shutdown and reload neither recharge nor refund resources");

        BuildingPlacement probe = BuildingUtils.getNewBuildingPlacement(Buildings.VILLAGER_HOUSE,
                level, origin, Rotation.NONE, OWNER, false);
        probe.restorePendingConstruction(new CompoundTag());
        h.assertTrue(probe.shouldBeDestroyed(), "legacy zero-block building is not resurrected");
        CompoundTag empty = pending.copy();
        empty.put("blocks", new ListTag());
        probe.restorePendingConstruction(empty);
        h.assertTrue(probe.shouldBeDestroyed(), "empty pending list does not retain destroyed building");
        CompoundTag invalid = pending.copy();
        invalid.getList("blocks", Tag.TAG_COMPOUND).getCompound(0).putLong("pos", origin.offset(999, 0, 999).asLong());
        probe.restorePendingConstruction(invalid);
        h.assertTrue(probe.blockPlaceQueue.isEmpty() && probe.shouldBeDestroyed(), "out-of-geometry reference rejects whole queue");
        CompoundTag wrongState = pending.copy();
        wrongState.getList("blocks", Tag.TAG_COMPOUND).getCompound(0).put("state", new CompoundTag());
        probe.restorePendingConstruction(wrongState);
        h.assertTrue(probe.blockPlaceQueue.isEmpty(), "invalid state cannot manufacture a block");
        CompoundTag oversized = pending.copy();
        ListTag tooMany = new ListTag();
        for (int i = 0; i <= probe.getBlocks().size() * 5; i++)
            tooMany.add(pending.getList("blocks", Tag.TAG_COMPOUND).getCompound(0).copy());
        oversized.put("blocks", tooMany);
        probe.restorePendingConstruction(oversized);
        h.assertTrue(probe.blockPlaceQueue.isEmpty(), "oversized input is rejected before queue reconstruction");
        CompoundTag future = pending.copy();
        future.putInt("version", 999);
        probe.restorePendingConstruction(future);
        h.assertTrue(probe.blockPlaceQueue.isEmpty() && probe.savePendingConstruction().equals(future),
                "unknown schema is retained opaquely without executing it");
        probe.isBuilt = true;
        probe.restorePendingConstruction(empty);
        h.assertTrue(probe.shouldBeDestroyed() && probe.blockPlaceQueue.isEmpty(),
                "completed destroyed buildings without pending work cannot be revived");
        probe.restorePendingConstruction(pending);
        h.assertTrue(probe.blockPlaceQueue.size() == queued && !probe.shouldBeDestroyed(),
                "completed buildings retain legitimate already queued upgrade or repair work");
        probe.isBuilt = false;
        probe.restorePendingConstruction(pending);
        probe.restorePendingConstruction(pending);
        h.assertTrue(probe.blockPlaceQueue.size() == queued, "repeated restore cannot duplicate queued work");

        h.runAtTickTime(5, () -> {
            h.assertTrue(BuildingServerEvents.getBuildings().contains(restored) && restored.getBlocksPlaced() > 0,
                    "actual server ticks place saved foundation instead of deleting purchase");
            h.assertTrue(account.wood == paidWood && account.ore == paidOre, "queue consumption does not recharge or refund");
            BuildingServerEvents.getBuildings().remove(restored);
            restored.onRemoved(BuildingPlacement.RemovalReason.RELOAD);
            PlayerServerEvents.rtsPlayers.removeIf(p -> p.name.equals(OWNER));
            ResourcesServerEvents.resourcesList.remove(account);
            System.out.println("PENDING_CONSTRUCTION_GAMETEST_PASS same_tick_stop_disk_loader_queue invalid empty destroyed future");
            h.succeed();
        });
    }
    private static void verifyProcessRestart(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        // Parent runner copies these only after the prepare JVM exits cleanly, before verify starts.
        BuildingSaveData saved = BuildingSaveData.load(NbtIo.readCompressed(
                new File("restart-baseline-buildings.dat")).getCompound("data"), level.getServer());
        ResourcesSaveData savedResources = ResourcesSaveData.load(NbtIo.readCompressed(
                new File("restart-baseline-resources.dat")).getCompound("data"));
        BuildingSave building = saved.buildings.stream().filter(b -> b.ownerName.equals(OWNER)).findFirst().orElseThrow();
        Resources paid = savedResources.resources.stream().filter(r -> r.ownerName.equals(OWNER)).findFirst().orElseThrow();
        BuildingPlacement live = BuildingServerEvents.getBuildings().stream()
                .filter(b -> b.ownerName.equals(OWNER)).findFirst().orElseThrow();
        Resources account = ResourcesServerEvents.resourcesList.stream()
                .filter(r -> r.ownerName.equals(OWNER)).findFirst().orElseThrow();
        h.assertTrue(!building.isBuilt && !building.pendingConstruction.getList("blocks", Tag.TAG_COMPOUND).isEmpty(),
                "actual stopped JVM saved unfinished purchase and its physical pending work");
        h.assertTrue(live.getBuilding() == Buildings.VILLAGER_HOUSE && live.originPos.equals(building.originPos)
                && live.ownerName.equals(building.ownerName), "ordinary startup retains building identity and owner");
        h.assertTrue(paid.wood == 5000 - Buildings.VILLAGER_HOUSE.cost.wood
                && paid.ore == 5000 - Buildings.VILLAGER_HOUSE.cost.ore
                && account.wood == paid.wood && account.ore == paid.ore && account.accountEpoch.equals(paid.accountEpoch),
                "actual payment and account identity survive process restart without recharge or refund");
        h.runAtTickTime(10, () -> {
            h.assertTrue(BuildingServerEvents.getBuildings().contains(live) && live.getBlocksPlaced() > 0,
                    "new JVM consumes saved zero-foundation queue through ordinary server ticks");
            h.assertTrue(account.wood == paid.wood && account.ore == paid.ore, "resumption has no resource mutation");
            System.out.println("PENDING_CONSTRUCTION_PROCESS_RESTART_PASS zero_foundation payment_identity resumed");
            h.succeed();
        });
    }

}
