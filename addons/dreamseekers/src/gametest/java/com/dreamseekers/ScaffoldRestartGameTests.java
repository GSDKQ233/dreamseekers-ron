// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.building.DreamScaffoldBuilding;
import com.dreamseekers.function.DreamFunctionalView;
import com.dreamseekers.function.DreamFunctionRequest;
import com.dreamseekers.function.DreamFunctionResult;
import com.dreamseekers.function.DreamFunctionService;
import com.dreamseekers.function.DreamFunctionState;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

/**
 * Dedicated normal-stop / next-JVM persistence check for all eight scaffolds in
 * all four rotations. Completed blocks are fixture setup, not a construction
 * test. This test never calls a RoN save/load method or manually reads world
 * NBT: normal server shutdown/startup must persist/restore the fixture.
 * No worker, capitol, training queue, or valued cargo is involved, so this does
 * not replace the pre-existing full-faction restart test or its known limits.
 */
@GameTestHolder("dreamseekers_scaffold_restart")
@PrefixGameTestTemplate(false)
public final class ScaffoldRestartGameTests {
    private static final String OWNER = "DreamScaffoldRestart_Owner";
    private static final File MANIFEST = new File("dreamseekers-scaffold-restart-expectations.nbt");
    private static final int X = 3072, Y = 140, Z = 3072, SPACING = 32;
    private static final String[] IDS = {"dream_depot", "shadow_mine", "shadow_furnace", "dream_hall",
            "resonance_laboratory", "contract_ecology", "resonance_dais", "shadow_watchtower"};

    @GameTest(template = "unit_isolation_arena", batch = "dreamseekers_scaffold_restart", timeoutTicks = 840)
    public static void allEightAndAllRotationsSurviveOrdinaryProcessRestart(GameTestHelper helper) throws IOException {
        switch (System.getProperty("dreamseekers.scaffold.test.phase", "")) {
            case "prepare" -> prepare(helper);
            case "verify" -> verify(helper);
            default -> throw new IllegalArgumentException("Run with dreamseekersScaffoldTestPhase=prepare or verify");
        }
    }

    private static void prepare(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(PlayerServerEvents.getRTSPlayer(OWNER) == null, "new isolated owner, never overwrite a prior fixture");
        helper.assertTrue(owned().isEmpty(), "new isolated fixture has no existing buildings");
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER, DreamseekersMod.DREAMSEEKERS));
        ResourcesServerEvents.resourcesList.add(new Resources(OWNER, 0, 0, 0, 0));
        CompoundTag expected = new CompoundTag();
        expected.putInt("format", 1);
        expected.putLong("preparePid", ProcessHandle.current().pid());
        expected.putLong("prepareJvmStart", ManagementFactory.getRuntimeMXBean().getStartTime());
        expected.putString("faction", DreamseekersMod.FACTION_ID.toString());
        ArrayList<Long> forced = new ArrayList<>();
        // Only the fixture footprint is forced, and only chunks that were not
        // already forced. The same persistent Minecraft tickets are checked in
        // the second JVM and released during successful verification cleanup.
        for (int cx = (X - 18) >> 4; cx <= (X + 7 * SPACING + 18) >> 4; cx++) {
            for (int cz = (Z - 18) >> 4; cz <= (Z + 3 * SPACING + 18) >> 4; cz++) {
                long chunk = ChunkPos.asLong(cx, cz);
                helper.assertTrue(!level.getForcedChunks().contains(chunk), "fixture does not take over a pre-existing forced chunk");
                level.setChunkForced(cx, cz, true);
                forced.add(chunk);
            }
        }
        expected.putLongArray("forcedChunks", forced);
        for (int i = 0; i < IDS.length; i++) {
            Building definition = ReignOfNetherRegistries.BUILDING.get(DreamseekersMod.id(IDS[i]));
            helper.assertTrue((definition instanceof DreamScaffoldBuilding || definition==DreamseekersMod.DREAM_HALL), "exact new registered scaffold " + IDS[i]);
            for (Rotation rotation : Rotation.values()) {
                BlockPos origin = new BlockPos(X + i * SPACING, Y, Z + rotation.ordinal() * SPACING);
                ArrayList<BuildingBlock> blocks = BuildingUtils.getAbsoluteBlockData(definition.getRelativeBlockData(level), level, origin, rotation);
                for (BuildingBlock block : blocks) {
                    BlockPos pos = block.getBlockPos();
                    level.setBlock(new BlockPos(pos.getX(), Y, pos.getZ()), Blocks.STONE.defaultBlockState(), 2);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
                // These are controlled completed fixtures. Ordinary paid worker
                // construction is covered separately by ScaffoldBuildingGameTests.
                for (BuildingBlock block : blocks) level.setBlock(block.getBlockPos(), block.getBlockState(), 2);
                BuildingPlacement placement = BuildingUtils.getNewBuildingPlacement(definition, level, origin, rotation, OWNER, false);
                helper.assertTrue(placement != null, "factory produces a placement " + IDS[i] + "/" + rotation);
                BuildingServerEvents.getBuildings().add(placement);
                placement.onBuilt();
            }
        }
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(owned().size() == 32, "all 32 completed fixtures remain live before shutdown");
            assertZeroEconomy(helper);
            ListTag saved = new ListTag();
            for (BuildingPlacement placement : owned()) {
                placement.initPlacedBlocks(); // rescan actual world after neighbor/shape updates, not cached fixture counts.
                helper.assertTrue(placement.isBuilt && placement.getBlocksPlaced() == placement.getBlocksTotal(),
                        "complete fixture retained its full non-air template after neighbor updates " + placement.getBuilding().structureName);
                CompoundTag record = new CompoundTag();
                record.putString("id", ReignOfNetherRegistries.BUILDING.getKey(placement.getBuilding()).toString());
                record.putLong("origin", placement.originPos.asLong());
                record.putString("rotation", placement.rotation.name());
                record.putString("class", placement.getClass().getName());
                record.putInt("blocks", placement.getBlocksTotal());
                assertLifecycle(helper, placement, false);
                assertDisabledRequest(helper, placement);
                record.put("functionalState", functionalState(helper, placement));
                ListTag actualBlocks = new ListTag();
                for (BuildingBlock block : placement.getBlocks()) {
                    if (block.getBlockState().isAir()) continue;
                    CompoundTag blockRecord = new CompoundTag();
                    blockRecord.putLong("pos", block.getBlockPos().asLong());
                    blockRecord.put("state", NbtUtils.writeBlockState(level.getBlockState(block.getBlockPos())));
                    actualBlocks.add(blockRecord);
                }
                record.put("actualBlocks", actualBlocks);
                saved.add(record);
            }
            helper.assertTrue(owned().stream().map(p -> ((DreamFunctionalView) p).stateSnapshot().instanceId()).distinct().count() == 32,
                    "each placement has a unique persistent functional instance UUID");
            assertZeroEconomy(helper);
            expected.put("buildings", saved);
            try { NbtIo.writeCompressed(expected, MANIFEST); }
            catch (IOException error) { throw new IllegalStateException("Cannot write fixture expectation manifest", error); }
            ReignOfNether.LOGGER.info("DREAMSEEKERS_SCAFFOLD_RESTART_PREPARED pid={} buildings=32 rotations=4 (await ordinary shutdown; no manual world save/load)", ProcessHandle.current().pid());
            helper.succeed();
        });
    }

    private static void verify(GameTestHelper helper) throws IOException {
        helper.assertTrue(MANIFEST.isFile(), "prepare manifest exists in same isolated world directory");
        CompoundTag expected = NbtIo.readCompressed(MANIFEST);
        helper.assertTrue(expected.getInt("format") == 1, "known scaffold probe manifest schema");
        helper.assertTrue(expected.getLong("prepareJvmStart") != ManagementFactory.getRuntimeMXBean().getStartTime(),
                "a genuine different JVM, never an in-memory reload");
        ServerLevel level = helper.getLevel();
        for (long key : expected.getLongArray("forcedChunks")) {
            helper.assertTrue(level.getForcedChunks().contains(key), "ordinary Minecraft startup restored forced fixture chunk");
            ChunkPos chunk = new ChunkPos(key);
            level.getChunk(chunk.x, chunk.z);
        }
        // Ordinary first server ticks, not manual tick/attach calls, must attach
        // loaded placements to the addon lifecycle before these assertions.
        helper.runAtTickTime(40, () -> {
            RTSPlayer owner = PlayerServerEvents.getRTSPlayer(OWNER);
            helper.assertTrue(owner != null && owner.faction == DreamseekersMod.DREAMSEEKERS
                    && owner.faction.key.toString().equals(expected.getString("faction")), "ordinary player loader retained faction");
            helper.assertTrue(owned().size() == 32, "ordinary building loader restores exactly 32 unique owned fixtures");
            assertZeroEconomy(helper);
            ListTag records = expected.getList("buildings", 10);
            helper.assertTrue(records.size() == 32, "expectation contains all eight buildings and rotations");
            for (int i = 0; i < records.size(); i++) {
                CompoundTag record = records.getCompound(i);
                BlockPos origin = BlockPos.of(record.getLong("origin"));
                Building definition = ReignOfNetherRegistries.BUILDING.get(ResourceLocation.parse(record.getString("id")));
                BuildingPlacement placement = owned().stream().filter(p -> p.originPos.equals(origin)).findFirst().orElseThrow();
                helper.assertTrue(placement.getBuilding() == definition && compatibleClass(placement,record.getString("class"))
                        && placement.rotation.name().equals(record.getString("rotation")) && placement.isBuilt
                        && placement.getBlocksTotal() == record.getInt("blocks") && placement.getBlocksPlaced() == placement.getBlocksTotal(),
                        "registry/factory/owner/rotation/completion all survive normal startup: " + record.getString("id"));
                assertLifecycle(helper, placement, true);
                if(placement instanceof com.dreamseekers.production.DreamHallPlacement hall)
                    helper.assertTrue(hall.productionQueue.isEmpty(),"old dormant Hall never acquires a free paid queue on migration");
                assertDisabledRequest(helper, placement);
                helper.assertTrue(functionalState(helper, placement).equals(record.getCompound("functionalState")),
                        "versioned functional identity and dormant state survive actual process restart " + record.getString("id"));
                ListTag actualBlocks = record.getList("actualBlocks", 10);
                for (int j = 0; j < actualBlocks.size(); j++) {
                    CompoundTag block = actualBlocks.getCompound(j);
                    BlockPos pos = BlockPos.of(block.getLong("pos"));
                    helper.assertTrue(NbtUtils.writeBlockState(level.getBlockState(pos)).equals(block.getCompound("state")),
                            "ordinary chunk storage retains exact actual block state " + record.getString("id") + " " + pos);
                }
            }
            assertZeroEconomy(helper);
            ReignOfNether.LOGGER.info("DREAMSEEKERS_SCAFFOLD_PROCESS_RESTART_PASS preparePid={} verifyPid={} buildings=32 rotations=4 zeroEconomy=true (ordinary world/player/resource/building loaders)",
                    expected.getLong("preparePid"), ProcessHandle.current().pid());
            if(Boolean.getBoolean("dreamseekers.hall.migration"))verifyLoadedHallTraining(helper,expected);
            else {cleanup(level, expected);helper.succeed();}
        });
    }

    private static void verifyLoadedHallTraining(GameTestHelper h,CompoundTag expected) {
        ServerLevel level=h.getLevel();
        var hall=(com.dreamseekers.production.DreamHallPlacement)owned().stream().filter(p->p.getBuilding()==DreamseekersMod.DREAM_HALL&&p.rotation==Rotation.NONE).findFirst().orElseThrow();
        h.assertTrue(!hall.startProductionItem(DreamseekersMod.BONE_WING_PRODUCTION),"loaded old Hall still cannot train for free with zero ledger/population");
        BlockPos anchorPos=new BlockPos(X+8*SPACING,Y,Z);
        var definition=DreamseekersMod.DREAM_ANCHOR;
        var blocks=BuildingUtils.getAbsoluteBlockData(definition.getRelativeBlockData(level),level,anchorPos,Rotation.NONE);
        for(BuildingBlock b:blocks)level.setBlock(b.getBlockPos(),b.getBlockState(),2);
        BuildingPlacement anchor=BuildingUtils.getNewBuildingPlacement(definition,level,anchorPos,Rotation.NONE,OWNER,false);
        BuildingServerEvents.getBuildings().add(anchor);anchor.onBuilt();
        ResourcesServerEvents.addSubtractResources(new Resources(OWNER,500,0,300,0)); // Explicit new fixture funds only after zero-economy migration assertions.
        // A dormant save's fallback rally can be its origin: an edge exit's 3x3 preparation would touch the Hall.
        // Issue an ordinary new exterior rally first; never rewrite the already-verified old building blocks.
        hall.setRallyPoint(hall.minCorner.offset(-20,0,-20));
        BlockPos exit=hall.getUnitSpawnPoint(level,false,net.minecraft.core.Vec3i.ZERO);
        System.out.println("BONE_OLD_HALL_CONTROLLED_EXIT min="+hall.minCorner+" exit="+exit+" rally="+hall.getFinalRallyPoint());
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.setBlock(exit.offset(x,6,z),Blocks.STONE.defaultBlockState(),2);
        level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new net.minecraft.world.phys.AABB(exit.offset(-1,1,-1),exit.offset(2,7,2))).forEach(net.minecraft.world.entity.Entity::discard);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++) {
            BlockPos p=exit.offset(x,0,z);
            h.assertTrue(!hall.isPosInsideBuilding(p),"new controlled exit does not rewrite old Hall geometry");
            level.setBlock(p,Blocks.STONE.defaultBlockState(),2);
            for(int y=1;y<=4;y++)level.setBlock(p.above(y),Blocks.AIR.defaultBlockState(),2);
        }
        h.assertTrue(hall.startProductionItem(DreamseekersMod.BONE_WING_PRODUCTION),"same normally restored old Hall starts actual paid production after explicit funding and supply");
        h.assertTrue(UnitServerEvents.getCurrentPopulation(OWNER)==2,"loaded Hall reserves real population");
        h.runAtTickTime(700,()->{
            var produced=UnitServerEvents.getAllUnits().stream().filter(e->e instanceof com.dreamseekers.unit.BoneWingUnit u&&u.getOwnerName().equals(OWNER)).toList();
            h.assertTrue(hall.productionQueue.isEmpty()&&produced.size()==1,"full training timer on migrated Hall produces exactly one light BoneWing");
            Resources ledger=ResourcesServerEvents.resourcesList.stream().filter(r->r.ownerName.equals(OWNER)).findFirst().orElseThrow();
            h.assertTrue(ledger.food==400&&ledger.wood==0&&ledger.ore==240&&ledger.emerald==0,"migrated Hall charges exactly provisional price once");
            System.out.println("DREAMSEEKERS_OLD_HALL_REAL_PAID_TRAINING_PASS units=1 fullTimer=true");
            produced.forEach(net.minecraft.world.entity.Entity::discard);cleanup(level,expected);h.succeed();
        });
    }

    private static boolean compatibleClass(BuildingPlacement placement,String previous) {
        if(placement.getClass().getName().equals(previous))return true;
        // Only the explicitly requested frozen-water -> BoneWing Hall migration permits this class change.
        return Boolean.getBoolean("dreamseekers.hall.migration") && placement.getBuilding()==DreamseekersMod.DREAM_HALL
                && placement instanceof com.dreamseekers.production.DreamHallPlacement
                && previous.equals("com.dreamseekers.function.DreamFunctionalPlacement");
    }

    private static CompoundTag functionalState(GameTestHelper helper, BuildingPlacement placement) {
        // The registry-backed codec is production data: this reads the already
        // attached placement, never replaces its state or loads SavedData.
        ListTag storage = placement.getDataStorage().write();
        CompoundTag state = null;
        for (int i = 0; i < storage.size(); i++) {
            CompoundTag entry = storage.getCompound(i);
            if (entry.getString("key").equals("dreamseekers:function_state")) {
                helper.assertTrue(state == null, "one explicit addon functional state, not accidental duplicate stores");
                state = entry.getCompound("data").copy();
            }
        }
        helper.assertTrue(state != null && !state.isEmpty(), "attached scaffold has nonempty versioned persisted function state");
        return state;
    }

    private static void assertLifecycle(GameTestHelper helper, BuildingPlacement placement, boolean restored) {
        helper.assertTrue(placement instanceof DreamFunctionalView, "factory restores typed functional placement");
        DreamFunctionalView functional = (DreamFunctionalView) placement;
        // Check counters BEFORE getDataStorage(): that accessor also synchronizes
        // an immediate save, but this test requires genuine preceding server ticks.
        helper.assertTrue(functional.lifecycleCount(DreamFunctionService.Lifecycle.ATTACHED) == (restored ? 0 : 1)
                && functional.lifecycleCount(DreamFunctionService.Lifecycle.RESTORED) == (restored ? 1 : 0)
                && functional.lifecycleCount(DreamFunctionService.Lifecycle.COMPLETED) == (restored ? 0 : 1),
                "real server lifecycle emits attach/completed once, or restore once without duplicate completion");
        DreamFunctionState.Snapshot state = functional.stateSnapshot();
        helper.assertTrue(state.schemaVersion() == DreamFunctionState.SCHEMA_VERSION && state.supported()
                && state.instanceId() != null && OWNER.equals(state.owner()) && state.built() && state.ownerRevision() == 0,
                "supported versioned identity/owner/completion is attached before test reads storage");
        helper.assertTrue(functional.lifecycleCount(DreamFunctionService.Lifecycle.OWNER_CHANGED) == 0
                && functional.lifecycleCount(DreamFunctionService.Lifecycle.DESTROYED) == 0,
                "restart does not fabricate owner-transfer or destroy events");
    }

    private static void assertDisabledRequest(GameTestHelper helper, BuildingPlacement placement) {
        DreamFunctionalView functional = (DreamFunctionalView) placement;
        DreamFunctionalView definition = (DreamFunctionalView)placement;
        UUID token = UUID.nameUUIDFromBytes((OWNER + definition.spec().structureName()).getBytes(StandardCharsets.UTF_8));
        ResourceLocation reserved = DreamseekersMod.id("test_disabled_capability");
        DreamFunctionRequest request = switch (definition.spec().role()) {
            case LOGISTICS -> new DreamFunctionRequest.Logistics(token, placement.originPos, reserved, 1,
                    DreamFunctionRequest.LogisticsOperation.DROPOFF);
            case ORE_EXTRACTION -> new DreamFunctionRequest.OreExtraction(placement.originPos, 1);
            case SHADOW_ENERGY -> new DreamFunctionRequest.ShadowAtomic(token, 0, 1);
            case PRODUCTION -> new DreamFunctionRequest.Production(reserved);
            case RESEARCH -> new DreamFunctionRequest.Research(reserved);
            case CONTRACTS -> new DreamFunctionRequest.Contract(token, reserved);
            case RESONANCE -> new DreamFunctionRequest.Resonance(placement.originPos, reserved);
            case DEFENSE -> new DreamFunctionRequest.Defense(token);
        };
        var actor = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
                UUID.nameUUIDFromBytes(OWNER.getBytes(StandardCharsets.UTF_8)), OWNER));
        helper.assertTrue(actor.getActiveEffects().isEmpty(), "isolated requesting actor has no pre-existing effects");
        var result = functional.request(actor, request);
        helper.assertTrue(result.status() == DreamFunctionResult.Status.UNAVAILABLE && !result.available(),
                "same authorized role remains explicitly unavailable after attachment/restart: " + definition.spec().structureName());
        helper.assertTrue(actor.getActiveEffects().isEmpty(), "disabled capability adds no requesting-actor effects");
    }

    private static List<BuildingPlacement> owned() {
        return BuildingServerEvents.getBuildings().stream().filter(p -> OWNER.equals(p.ownerName)).toList();
    }

    private static void assertZeroEconomy(GameTestHelper helper) {
        Resources resources = ResourcesServerEvents.resourcesList.stream().filter(r -> OWNER.equals(r.ownerName)).findFirst().orElseThrow();
        helper.assertTrue(resources.food == 0 && resources.wood == 0 && resources.ore == 0 && resources.emerald == 0
                && resources.foodToAdd == 0 && resources.woodToAdd == 0 && resources.oreToAdd == 0 && resources.emeraldToAdd == 0,
                "dormant scaffolds neither generate nor settle any currency during shutdown/startup");
        helper.assertTrue(UnitServerEvents.getAllUnits().stream().noneMatch(e -> e instanceof Unit u && OWNER.equals(u.getOwnerName())),
                "dormant scaffolds do not spawn any owned units");
    }

    private static void cleanup(ServerLevel level, CompoundTag expected) {
        for (BuildingPlacement placement : owned()) {
            for (BuildingBlock block : placement.getBlocks()) {
                level.setBlock(block.getBlockPos(), Blocks.AIR.defaultBlockState(), 2);
                BlockPos pos = block.getBlockPos();
                level.setBlock(new BlockPos(pos.getX(), Y, pos.getZ()), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        BuildingServerEvents.getBuildings().removeIf(p -> OWNER.equals(p.ownerName));
        PlayerServerEvents.rtsPlayers.removeIf(p -> OWNER.equals(p.name));
        ResourcesServerEvents.resourcesList.removeIf(r -> OWNER.equals(r.ownerName));
        for (long key : expected.getLongArray("forcedChunks")) {
            ChunkPos chunk = new ChunkPos(key);
            level.setChunkForced(chunk.x, chunk.z, false);
        }
    }
}
