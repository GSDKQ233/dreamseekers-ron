// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesSaveData;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Genuine two-JVM test, not an in-memory load/save roundtrip.
 * Run -PdreamseekersTestPhase=prepare, await normal server shutdown, then run
 * -PdreamseekersTestPhase=verify using the same isolated run directory/world.
 * The expectation manifest stores identifiers only; verification never calls
 * RoN save-data load functions or EntityType.loadEntityRecursive.
 */
// Keep genuine process restart separate from tests that intentionally reload
// every building in-process and therefore replace pending production queues.
@GameTestHolder("dreamseekers_restart")
@PrefixGameTestTemplate(false)
public final class DreamPersistenceGameTests {
    private static final String OWNER = "DreamPersist_Owner";
    private static final String QUEUE_OWNER = "DreamPersist_QueuePeer";
    private static final File MANIFEST = new File("dreamseekers-restart-expectations.nbt");
    private static final int X = 2048, Y = 90, Z = 2048;
    private static final int FOOD = 1234, WOOD = 2345, ORE = 3456, EMERALD = 4567;
    private static final int QUEUE_BUDGET = 2222;

    // Forge 47.4.0 uses the same namespace for filtering and template lookup;
    // the original empty fixture has a test-resource-only alias in this namespace.
    @GameTest(template = "unit_isolation_arena", batch = "dreamseekers_process_restart", timeoutTicks = 240)
    public static void ordinaryWorldReloadPreservesNewRegistryIds(GameTestHelper helper) throws Exception {
        String phase = System.getProperty("dreamseekers.test.phase", "");
        if (phase.equals("prepare")) {
            prepare(helper);
        } else if (phase.equals("verify")) {
            verify(helper);
        } else {
            throw new IllegalArgumentException("Unknown dreamseekers.test.phase=" + phase);
        }
    }

    private static void prepare(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(PlayerServerEvents.getRTSPlayer(OWNER) == null, "restart probe must not overwrite an existing owner");
        helper.assertTrue(PlayerServerEvents.getRTSPlayer(QUEUE_OWNER) == null, "restart queue probe must not overwrite an existing owner");
        helper.assertTrue(BuildingServerEvents.getBuildings().stream().noneMatch(b -> OWNER.equals(b.ownerName)), "restart probe starts without old buildings");
        CompoundTag expected = new CompoundTag();
        expected.putInt("format", 2);
        expected.putLong("preparePid", ProcessHandle.current().pid());
        expected.putLong("prepareJvmStart", java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        expected.putString("faction", DreamseekersMod.FACTION_ID.toString());
        ArrayList<Long> addedChunks = new ArrayList<>();
        // Keep every fixture chunk loaded across ordinary shutdown/startup, using
        // Minecraft's persistent forced-chunk data, not an in-memory test ticket.
        for (int cx = (X - 6) >> 4; cx <= (X + 119) >> 4; cx++)
            for (int cz = (Z - 6) >> 4; cz <= (Z + 25) >> 4; cz++) {
                long key = ChunkPos.asLong(cx, cz);
                helper.assertTrue(!level.getForcedChunks().contains(key), "isolated probe chunk was not already forced");
                level.setChunkForced(cx, cz, true);
                addedChunks.add(key);
            }
        expected.putLongArray("forcedChunks", addedChunks);
        for (int x = X - 6; x <= X + 119; x++) for (int z = Z - 6; z <= Z + 25; z++) {
            level.setBlock(new BlockPos(x, Y, z), Blocks.STONE.defaultBlockState(), 2);
            for (int y = Y + 1; y <= Y + 13; y++)
                level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER, DreamseekersMod.DREAMSEEKERS));
        ResourcesServerEvents.resourcesList.add(new Resources(OWNER, FOOD, WOOD, ORE, EMERALD));
        ListTag buildings = new ListTag();
        List<Building> definitions = List.of(DreamseekersMod.DREAM_ANCHOR, DreamseekersMod.REVERIE_GARDEN, DreamseekersMod.BEETLE_SANCTUARY);
        for (int i = 0; i < definitions.size(); i++) {
            Building building = definitions.get(i);
            BlockPos origin = new BlockPos(X + i * 30, Y, Z);
            for (BuildingBlock block : BuildingUtils.getAbsoluteBlockData(building.getRelativeBlockData(level), level, origin, Rotation.NONE))
                level.setBlock(block.getBlockPos(), block.getBlockState(), 2);
            BuildingPlacement placement = BuildingUtils.getNewBuildingPlacement(building, level, origin, Rotation.NONE, OWNER, false);
            BuildingServerEvents.getBuildings().add(placement);
            placement.onBuilt();
            CompoundTag record = new CompoundTag();
            record.putString("id", ReignOfNetherRegistries.BUILDING.getKey(building).toString());
            record.putString("class", building.getClass().getName());
            record.putLong("origin", origin.asLong());
            buildings.add(record);
        }
        expected.put("buildings", buildings);
        // A second account has an unfinished paid queue. Saving must refund it
        // only to that account, never to every RTS player on the server.
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(QUEUE_OWNER, DreamseekersMod.DREAMSEEKERS));
        ResourcesServerEvents.resourcesList.add(new Resources(QUEUE_OWNER, QUEUE_BUDGET, QUEUE_BUDGET, QUEUE_BUDGET, QUEUE_BUDGET));
        BlockPos queueOrigin = new BlockPos(X + 90, Y, Z);
        for (BuildingBlock block : BuildingUtils.getAbsoluteBlockData(DreamseekersMod.DREAM_ANCHOR.getRelativeBlockData(level), level, queueOrigin, Rotation.NONE))
            level.setBlock(block.getBlockPos(), block.getBlockState(), 2);
        ProductionPlacement queuedBase = (ProductionPlacement) BuildingUtils.getNewBuildingPlacement(DreamseekersMod.DREAM_ANCHOR,
                level, queueOrigin, Rotation.NONE, QUEUE_OWNER, false);
        BuildingServerEvents.getBuildings().add(queuedBase);
        queuedBase.onBuilt();
        helper.assertTrue(queuedBase.startProductionItem(DreamseekersMod.WORKER_PRODUCTION), "second owner enters a genuine paid production queue");
        queuedBase.productionQueue.get(0).ticksLeft = 1_000_000;
        expected.putLong("queueOrigin", queueOrigin.asLong());
        ListTag entities = new ListTag();
        Resources settlement = new Resources("", 0, 0, 0, 0);
        List<EntityType<? extends Mob>> types = List.of(DreamseekersMod.FIREFLY_WORKER.get(), DreamseekersMod.BEETLE.get(), DreamseekersMod.BASALT_SNAIL.get());
        for (int i = 0; i < types.size(); i++) {
            Mob mob = types.get(i).create(level);
            helper.assertTrue(mob instanceof Unit, "restart fixture is a real RTS creature");
            Unit unit = (Unit) mob;
            unit.setOwnerName(OWNER);
            unit.setScenarioRoleIndex(30 + i);
            unit.getItems().add(new ItemStack(Items.AMETHYST_SHARD, i + 1));
            // Root RoN saves ledger + held RTS resources, not duplicate physical
            // resource cargo. Exercise all four settlement currencies and keep
            // one non-resource stack to independently verify entity inventory.
            unit.getItems().add(new ItemStack(Items.WHEAT, i + 1));
            unit.getItems().add(new ItemStack(Items.OAK_LOG, i + 1));
            unit.getItems().add(new ItemStack(Items.RAW_IRON, i + 1));
            unit.getItems().add(new ItemStack(Items.EMERALD, i + 1));
            Resources cargo = Resources.getTotalResourcesFromItems(unit.getItems());
            helper.assertTrue(cargo.food > 0 && cargo.wood > 0 && cargo.ore > 0 && cargo.emerald > 0,
                    "restart fixture contains genuinely priced cargo in every resource currency");
            settlement.food += cargo.food;
            settlement.wood += cargo.wood;
            settlement.ore += cargo.ore;
            settlement.emerald += cargo.emerald;
            mob.setNoAi(true); // isolate storage, not combat/pathfinding (tested elsewhere).
            mob.setPersistenceRequired();
            mob.moveTo(X - 3.5, Y + 1, Z + 3.5 + i * 4, 0, 0);
            helper.assertTrue(level.addFreshEntity(mob), "restart entity enters ordinary world storage");
            CompoundTag record = new CompoundTag();
            record.putUUID("uuid", mob.getUUID());
            record.putString("id", EntityType.getKey(mob.getType()).toString());
            record.putString("class", mob.getClass().getName());
            record.putLong("chunk", mob.chunkPosition().toLong());
            record.putInt("scenario", 30 + i);
            record.putInt("items", i + 1);
            entities.add(record);
        }
        expected.put("entities", entities);
        expected.putInt("food", FOOD + settlement.food);
        expected.putInt("wood", WOOD + settlement.wood);
        expected.putInt("ore", ORE + settlement.ore);
        expected.putInt("emerald", EMERALD + settlement.emerald);
        NbtIo.writeCompressed(expected, MANIFEST);
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(BuildingServerEvents.getBuildings().stream().filter(b -> OWNER.equals(b.ownerName) && b.isBuilt).count() == 3,
                    "all three completed probe buildings remain present before shutdown");
            // These are real repeated snapshot writes, not a load/restart. The
            // subsequent verify JVM still relies exclusively on normal loaders.
            for (int pass = 0; pass < 2; pass++) {
                ResourcesServerEvents.saveResources(level);
                Resources snapshot = ResourcesSaveData.getInstance(level).resources.stream()
                        .filter(r -> OWNER.equals(r.ownerName)).findFirst().orElseThrow();
                assertSettledLedger(helper, snapshot, expected, "repeated snapshot " + pass);
                Resources peer = ResourcesSaveData.getInstance(level).resources.stream()
                        .filter(r -> QUEUE_OWNER.equals(r.ownerName)).findFirst().orElseThrow();
                assertQueueBudget(helper, peer, "pending refund is owner-specific and repeated snapshots are idempotent");
            }
            helper.assertTrue(queuedBase.productionQueue.size() == 1, "saving snapshots does not cancel the live queue");
            ReignOfNether.LOGGER.info("DREAMSEEKERS_RESTART_PREPARED pid={} (await normal server-stopping saves; no manual save/load roundtrip)", ProcessHandle.current().pid());
            // Deliberately leave fixture intact. The normal server shutdown owns saving.
            helper.succeed();
        });
    }

    private static void verify(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(MANIFEST.isFile(), "prepare expectation manifest exists");
        CompoundTag expected = NbtIo.readCompressed(MANIFEST);
        helper.assertTrue(expected.getInt("format") == 2, "manifest includes real valued-cargo settlement expectations");
        // PIDs can repeat in a new container/PID namespace. The JVM start time
        // cannot change within a running JVM, so it is the lifecycle identity.
        helper.assertTrue(expected.getLong("prepareJvmStart") != java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),
                "verification executes in a different JVM, not a simulated reload");
        for (long chunk : expected.getLongArray("forcedChunks")) {
            helper.assertTrue(level.getForcedChunks().contains(chunk), "ordinary startup restored persistent forced chunk");
            ChunkPos pos = new ChunkPos(chunk);
            level.getChunk(pos.x, pos.z); // ordinary chunk loading; never manually deserialize fixture entities.
        }
        // Block chunks can be available before their asynchronous entity storage
        // finishes loading. Wait for that actual prerequisite, not an arbitrary
        // fast GameTest tick count; retain the same overall 240-tick deadline.
        helper.startSequence().thenWaitUntil(() -> {
            ListTag savedEntities = expected.getList("entities", 10);
            for (int i = 0; i < savedEntities.size(); i++) {
                long key = savedEntities.getCompound(i).getLong("chunk");
                ChunkPos chunk = new ChunkPos(key);
                boolean loaded = level.areEntitiesLoaded(key);
                String state = "chunk=" + chunk + " forced=" + level.getForcedChunks().contains(key)
                        + " fullStatus=" + level.getChunk(chunk.x, chunk.z).getFullStatus()
                        + " storageLoaded=" + loaded;
                if (!loaded && helper.getTick() % 40 == 0)
                    ReignOfNether.LOGGER.info("DREAMSEEKERS_RESTART_ENTITY_LOADING tick={} {}", helper.getTick(), state);
                helper.assertTrue(loaded, "ordinary asynchronous entity storage must finish loading; " + state);
            }
        }).thenExecute(() -> {
            ReignOfNether.LOGGER.info("DREAMSEEKERS_RESTART_ENTITY_LOAD_READY tick={}", helper.getTick());
            RTSPlayer player = PlayerServerEvents.getRTSPlayer(OWNER);
            helper.assertTrue(player != null && player.faction == DreamseekersMod.DREAMSEEKERS
                            && player.faction.key.toString().equals(expected.getString("faction")),
                    "normal RTSPlayerSaveData startup restores new faction identity");
            Resources account = ResourcesServerEvents.resourcesList.stream().filter(r -> OWNER.equals(r.ownerName)).findFirst().orElseThrow();
            assertSettledLedger(helper, account, expected, "normal resource startup settles all four cargo currencies exactly once");
            Resources peer = ResourcesServerEvents.resourcesList.stream().filter(r -> QUEUE_OWNER.equals(r.ownerName)).findFirst().orElseThrow();
            assertQueueBudget(helper, peer, "normal shutdown refunds the pending queue only to its real owner");
            BlockPos queueOrigin = BlockPos.of(expected.getLong("queueOrigin"));
            BuildingPlacement peerBase = BuildingServerEvents.getBuildings().stream()
                    .filter(b -> b.originPos.equals(queueOrigin) && QUEUE_OWNER.equals(b.ownerName)).findFirst().orElseThrow();
            helper.assertTrue(peerBase instanceof ProductionPlacement production && production.productionQueue.isEmpty(),
                    "normal shutdown cancels and refunds pending training rather than claiming queue resume");
            ListTag buildings = expected.getList("buildings", 10);
            for (int i = 0; i < buildings.size(); i++) {
                CompoundTag record = buildings.getCompound(i);
                BlockPos origin = BlockPos.of(record.getLong("origin"));
                Building definition = ReignOfNetherRegistries.BUILDING.get(ResourceLocation.parse(record.getString("id")));
                BuildingPlacement placement = BuildingServerEvents.getBuildings().stream()
                        .filter(b -> b.originPos.equals(origin) && OWNER.equals(b.ownerName)).findFirst().orElseThrow();
                helper.assertTrue(placement.getBuilding() == definition && definition.getClass().getName().equals(record.getString("class"))
                                && placement.isBuilt && placement.getBlocksPlaced() > 0,
                        "normal building loader restores registered identity, class, ownership, completion and chunk blocks");
                if (placement instanceof ProductionPlacement production)
                    helper.assertTrue(production.productionQueue.isEmpty(), "probe has no pending production; queue resume is not claimed");
                for (BuildingBlock block : placement.getBlocks())
                    if (!block.getBlockState().isAir())
                        helper.assertTrue(level.getBlockState(block.getBlockPos()).getBlock() == block.getBlockState().getBlock(),
                                "ordinary world save restores placed block " + block.getBlockPos());
            }
            ListTag entities = expected.getList("entities", 10);
            StringBuilder entityLoadState = new StringBuilder();
            for (int i = 0; i < entities.size(); i++) {
                CompoundTag record = entities.getCompound(i);
                ChunkPos chunk = new ChunkPos(record.getLong("chunk"));
                Entity current = level.getEntity(record.getUUID("uuid"));
                entityLoadState.append(" [").append(record.getString("id"))
                        .append(" uuid=").append(record.getUUID("uuid"))
                        .append(" current=").append(current)
                        .append(" chunk=").append(chunk)
                        .append(" storageLoaded=").append(level.areEntitiesLoaded(chunk.toLong()))
                        .append(" entityTicking=").append(level.isPositionEntityTicking(new BlockPos(chunk.getMinBlockX(), Y, chunk.getMinBlockZ())))
                        .append(']');
            }
            for (int i = 0; i < entities.size(); i++) {
                CompoundTag record = entities.getCompound(i);
                Entity entity = level.getEntity(record.getUUID("uuid"));
                helper.assertTrue(entity instanceof Unit && entity.isAlive(), "same saved UUID loads as live RTS entity through ordinary chunk storage; tick=" + helper.getTick() + entityLoadState);
                Unit unit = (Unit) entity;
                helper.assertTrue(EntityType.getKey(entity.getType()).toString().equals(record.getString("id"))
                                && entity.getClass().getName().equals(record.getString("class")) && OWNER.equals(unit.getOwnerName()),
                        "all three addon registry IDs/classes/owners survive genuine process restart");
                helper.assertTrue(unit.getScenarioRoleIndex() == record.getInt("scenario") && unit.getItems().size() == 1
                                && unit.getItems().get(0).is(Items.AMETHYST_SHARD)
                                && unit.getItems().get(0).getCount() == record.getInt("items"),
                        "custom RTS state and non-resource inventory survive; already-settled valued cargo is absent after real restart");
            }
            ReignOfNether.LOGGER.info("DREAMSEEKERS_PROCESS_RESTART_PASS preparePid={} verifyPid={} buildings=3 entities=3 peerQueueRefund=ownerOnly settledLedger={},{},{},{} (ordinary world/player/resource/building loaders)",
                    expected.getLong("preparePid"), ProcessHandle.current().pid(), account.food, account.wood, account.ore, account.emerald);
            cleanup(level, expected);
            helper.succeed();
        });
    }

    private static void assertSettledLedger(GameTestHelper helper, Resources account, CompoundTag expected, String context) {
        helper.assertTrue(account.food == expected.getInt("food") && account.wood == expected.getInt("wood")
                        && account.ore == expected.getInt("ore") && account.emerald == expected.getInt("emerald")
                        && account.foodToAdd == 0 && account.woodToAdd == 0 && account.oreToAdd == 0 && account.emeraldToAdd == 0,
                context + "; actual=" + account.food + "," + account.wood + "," + account.ore + "," + account.emerald);
    }

    private static void assertQueueBudget(GameTestHelper helper, Resources account, String context) {
        helper.assertTrue(account.food == QUEUE_BUDGET && account.wood == QUEUE_BUDGET && account.ore == QUEUE_BUDGET
                        && account.emerald == QUEUE_BUDGET && account.foodToAdd == 0 && account.woodToAdd == 0
                        && account.oreToAdd == 0 && account.emeraldToAdd == 0,
                context + "; actual=" + account.food + "," + account.wood + "," + account.ore + "," + account.emerald);
    }

    private static void cleanup(ServerLevel level, CompoundTag expected) {
        PlayerServerEvents.rtsPlayers.removeIf(p -> OWNER.equals(p.name) || QUEUE_OWNER.equals(p.name));
        ResourcesServerEvents.resourcesList.removeIf(r -> OWNER.equals(r.ownerName) || QUEUE_OWNER.equals(r.ownerName));
        ListTag entities = expected.getList("entities", 10);
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = level.getEntity(entities.getCompound(i).getUUID("uuid"));
            if (entity != null) entity.discard();
        }
        BuildingServerEvents.getBuildings().removeIf(b -> OWNER.equals(b.ownerName) || QUEUE_OWNER.equals(b.ownerName));
        for (int x = X - 6; x <= X + 119; x++) for (int z = Z - 6; z <= Z + 25; z++)
            for (int y = Y; y <= Y + 13; y++)
                level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        for (long chunk : expected.getLongArray("forcedChunks")) {
            ChunkPos pos = new ChunkPos(chunk);
            level.setChunkForced(pos.x, pos.z, false);
        }
    }
}
