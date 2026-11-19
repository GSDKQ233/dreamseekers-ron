// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.unit.FireflyWorker;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.FarmPlacement;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Separate real two-JVM firefly task/cargo probe; it does not overwrite or reinterpret the old food+4 fixture. */
@GameTestHolder("dreamseekers_firefly_restart")
@PrefixGameTestTemplate(false)
public final class FireflyRestartGameTests {
    private static final String OWNER="FireflyPersist_Owner";
    private static final BlockPos ORIGIN=new BlockPos(4096,90,4096);
    private static final File MANIFEST=new File("firefly-restart-expectations.nbt");

    @GameTest(template="unit_isolation_arena",batch="dreamseekers_firefly_restart",timeoutTicks=650)
    public static void ordinaryRestartSettlesCargoOnceAndResumesActiveGardenTask(GameTestHelper helper) throws Exception {
        String phase=System.getProperty("dreamseekers.firefly.test.phase","");
        if(phase.equals("prepare")) prepare(helper);
        else if(phase.equals("verify")) verify(helper);
        else throw new IllegalArgumentException("Unknown firefly phase "+phase);
    }

    private static void prepare(GameTestHelper helper) throws Exception {
        ServerLevel level=helper.getLevel();
        helper.assertTrue(PlayerServerEvents.getRTSPlayer(OWNER)==null,"isolated fixture has no previous owner");
        CompoundTag expected=new CompoundTag();
        expected.putLong("prepareJvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        List<Long> chunks=new ArrayList<>();
        for(int cx=(ORIGIN.getX()-2)>>4;cx<=(ORIGIN.getX()+54)>>4;cx++) for(int cz=(ORIGIN.getZ()-2)>>4;cz<=(ORIGIN.getZ()+25)>>4;cz++) {
            long key=ChunkPos.asLong(cx,cz);
            helper.assertTrue(!level.getForcedChunks().contains(key),"new fixture does not adopt someone else's forced chunk");
            level.setChunkForced(cx,cz,true);chunks.add(key);
        }
        expected.putLongArray("forcedChunks",chunks);
        for(int x=-2;x<=54;x++) for(int z=-2;z<=25;z++) {
            level.setBlock(ORIGIN.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=1;y<=12;y++)level.setBlock(ORIGIN.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER,DreamseekersMod.DREAMSEEKERS));
        Resources account=new Resources(OWNER,1000,2000,3000,4000);
        ResourcesServerEvents.resourcesList.add(account);
        // No capitol animals or preexisting dropped crop items in this focused storage fixture.
        install(level,DreamseekersMod.RESONANCE_DAIS,ORIGIN.offset(30,0,0));
        FarmPlacement garden=(FarmPlacement)install(level,DreamseekersMod.REVERIE_GARDEN,ORIGIN);
        List<BlockPos> soils=soils(garden);
        for(BlockPos soil:soils)level.setBlock(soil.above(),Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7,0),2);
        BlockPos crop=soils.get(0).above();
        expected.putLong("crop",crop.asLong());
        expected.putLong("garden",garden.originPos.asLong());
        FireflyWorker worker=DreamseekersMod.FIREFLY_WORKER.get().create(level);
        helper.assertTrue(worker!=null,"real firefly created");
        worker.setOwnerName(OWNER);worker.setScenarioRoleIndex(73);
        worker.moveTo(crop.getX()+.5,crop.getY()+.5,crop.getZ()+.5,0,0);
        helper.assertTrue(level.addFreshEntity(worker),"active farmer joins ordinary world storage");
        worker.getItems().add(new ItemStack(Items.WHEAT,1));
        worker.getItems().add(new ItemStack(Items.OAK_LOG,1));
        worker.getItems().add(new ItemStack(Items.RAW_IRON,1));
        worker.getItems().add(new ItemStack(Items.EMERALD,1));
        worker.getItems().add(new ItemStack(Items.AMETHYST_SHARD,5));
        Resources cargo=Resources.getTotalResourcesFromItems(worker.getItems());
        helper.assertTrue(cargo.food>0 && cargo.wood>0 && cargo.ore>0 && cargo.emerald>0,"all four genuine valued cargo types");
        expected.putUUID("uuid",worker.getUUID());
        expected.putInt("food",account.food+cargo.food);expected.putInt("wood",account.wood+cargo.wood);
        expected.putInt("ore",account.ore+cargo.ore);expected.putInt("emerald",account.emerald+cargo.emerald);
        UnitServerEvents.addActionItem(OWNER,UnitAction.FARM,-1,new int[]{worker.getId()},crop,garden.originPos);
        helper.runAtTickTime(50,()->{
            helper.assertTrue(worker.getGatherResourceGoal().getTargetFarm()==garden,"active FARM task exists before real shutdown");
            helper.assertTrue(account.food==1000 && account.wood==2000 && account.ore==3000 && account.emerald==4000,"no incidental fixture resource grant before shutdown");
            for(int i=0;i<2;i++) {
                ResourcesServerEvents.saveResources(level);
                Resources snapshot=ResourcesSaveData.getInstance(level).resources.stream().filter(r->r.ownerName.equals(OWNER)).findFirst().orElseThrow();
                assertLedger(helper,snapshot,expected,"repeated save snapshot");
            }
            try { NbtIo.writeCompressed(expected,MANIFEST); } catch(Exception e) {throw new RuntimeException(e);}
            System.out.println("DREAMSEEKERS_FIREFLY_RESTART_PREPARED uuid="+worker.getUUID()+" task="+garden.originPos);
            helper.succeed(); // ordinary server stopping, not a manual deserialize, owns persisted task/entity state.
        });
    }

    private static void verify(GameTestHelper helper) throws Exception {
        ServerLevel level=helper.getLevel();
        helper.assertTrue(MANIFEST.isFile(),"prepare manifest exists");
        CompoundTag expected=NbtIo.readCompressed(MANIFEST);
        helper.assertTrue(expected.getLong("prepareJvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"different genuine JVM");
        for(long key:expected.getLongArray("forcedChunks")) {
            helper.assertTrue(level.getForcedChunks().contains(key),"ordinary startup restored forced chunk");
            ChunkPos chunk=new ChunkPos(key);level.getChunk(chunk.x,chunk.z);
        }
        helper.startSequence().thenWaitUntil(()->{
            Entity entity=level.getEntity(expected.getUUID("uuid"));
            helper.assertTrue(entity instanceof FireflyWorker,"wait for normal asynchronous entity storage");
        }).thenIdle(2).thenExecute(()->{ // allow ordinary END-tick saved-task attachment; never reissue FARM
            FireflyWorker worker=(FireflyWorker)level.getEntity(expected.getUUID("uuid"));
            Resources account=ResourcesServerEvents.resourcesList.stream().filter(r->r.ownerName.equals(OWNER)).findFirst().orElseThrow();
            FarmPlacement garden=(FarmPlacement)BuildingServerEvents.getBuildings().stream()
                    .filter(b->b.originPos.equals(BlockPos.of(expected.getLong("garden"))) && b.ownerName.equals(OWNER)).findFirst().orElseThrow();
            helper.assertTrue(worker.getOwnerName().equals(OWNER) && worker.getScenarioRoleIndex()==73 && Factions.getFaction(worker)==DreamseekersMod.DREAMSEEKERS,"same UUID/owner/scenario/faction");
            helper.assertTrue(worker.isFlyingUnit() && worker.isNoGravity(),"flight remains after normal loading");
            helper.assertTrue(worker.getItems().size()==1 && worker.getItems().get(0).is(Items.AMETHYST_SHARD)
                    && worker.getItems().get(0).getCount()==5,"priced cargo settled only to ledger, non-resource stack preserved");
            assertLedger(helper,account,expected,"ordinary restart settled cargo exactly once");
            helper.assertTrue(worker.getGatherResourceGoal().getTargetFarm()==garden,
                    "normal saved-target loader resumed exact farm, no manually reapplied order: target="+worker.getGatherResourceGoal().getTargetFarm());
            BlockPos crop=BlockPos.of(expected.getLong("crop"));
            helper.assertTrue(DreamseekersMod.REVERIE_GARDEN.isCropSoil(level.getBlockState(crop.below())),"real dyedream soil remains");
            level.setBlock(crop,Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7,7),2);
            long due=helper.getTick()+350;
            helper.runAtTickTime(due,()->{
                helper.assertTrue(level.getBlockState(crop).is(Blocks.WHEAT)
                        && level.getBlockState(crop).getValue(BlockStateProperties.AGE_7)<7,"resumed active worker actually harvests and replants in second JVM");
                helper.assertTrue(account.wood==expected.getInt("wood")-ResourceCosts.REPLANT_WOOD_COST,"second-JVM replant charged once");
                helper.assertTrue(account.food==expected.getInt("food") && Resources.getTotalResourcesFromItems(worker.getItems()).food>=4,"new crop remains carried, old saved cargo not paid twice");
                helper.assertTrue(account.ore==expected.getInt("ore") && account.emerald==expected.getInt("emerald"),"other currencies stable");
                worker.discard();
                BuildingServerEvents.getBuildings().removeIf(b->b.ownerName.equals(OWNER));
                PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(OWNER));
                ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(OWNER));
                for(long key:expected.getLongArray("forcedChunks")){ChunkPos chunk=new ChunkPos(key);level.setChunkForced(chunk.x,chunk.z,false);}
                System.out.println("DREAMSEEKERS_FIREFLY_RESTART_VERIFIED_ACTIVE_FARM_CARGO_PASS uuid="+worker.getUUID());
                helper.succeed();
            });
        });
    }
    private static void assertLedger(GameTestHelper helper,Resources account,CompoundTag expected,String context) {
        helper.assertTrue(account.food==expected.getInt("food") && account.wood==expected.getInt("wood")
                && account.ore==expected.getInt("ore") && account.emerald==expected.getInt("emerald"),context+" actual="+account.food+","+account.wood+","+account.ore+","+account.emerald);
    }
    private static List<BlockPos> soils(FarmPlacement garden) {
        return garden.getBlocks().stream().filter(b->DreamseekersMod.REVERIE_GARDEN.isCropSoil(b.getBlockState())).map(BuildingBlock::getBlockPos).toList();
    }
    private static BuildingPlacement install(ServerLevel level,Building building,BlockPos origin) {
        for(BuildingBlock block:BuildingUtils.getAbsoluteBlockData(building.getRelativeBlockData(level),level,origin,Rotation.NONE))
            level.setBlock(block.getBlockPos(),block.getBlockState(),2);
        BuildingPlacement placement=BuildingUtils.getNewBuildingPlacement(building,level,origin,Rotation.NONE,OWNER,false);
        BuildingServerEvents.getBuildings().add(placement);placement.onBuilt();return placement;
    }
}
