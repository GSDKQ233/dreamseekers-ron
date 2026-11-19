// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.unit.FireflyWorker;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.FarmPlacement;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.ArrayList;

@net.minecraftforge.gametest.GameTestHolder(DreamseekersMod.MOD_ID)
@net.minecraftforge.gametest.PrefixGameTestTemplate(false)
public final class FireflyEconomyGameTests {
    private static final String OWNER="FireflyGT_AutoReturn";
    private static final String BATCH="dreamseekers_firefly_economy";
    private static final java.util.Set<net.minecraft.world.level.ChunkPos> ADDED_FORCED_CHUNKS=new java.util.HashSet<>();
    @AfterBatch(batch=BATCH)
    public static void cleanup(ServerLevel level) {
        for(var entity:new ArrayList<>(UnitServerEvents.getAllUnits()))
            if(entity instanceof Unit unit && unit.getOwnerName().equals(OWNER))entity.discard();
        BuildingServerEvents.getBuildings().removeIf(b->b.ownerName.equals(OWNER));
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(OWNER));
        ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(OWNER));
        for(var chunk:ADDED_FORCED_CHUNKS)level.setChunkForced(chunk.x,chunk.z,false);
        ADDED_FORCED_CHUNKS.clear();
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=3000)
    public static void thresholdAutomaticallyReturnsThenResumesFarmWithoutDuplicateCredit(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER,DreamseekersMod.DREAMSEEKERS));
        Resources account=new Resources(OWNER,0,500,0);ResourcesServerEvents.resourcesList.add(account);
        BlockPos floor=BuildingAssetGameTests.outdoorPos(helper,2,1,2);
        // Ordinary selected-unit fixture, not an unpublished production probe. Preserve existing tickets.
        for(int cx=floor.getX()>>4;cx<=(floor.getX()+84)>>4;cx++)for(int cz=floor.getZ()>>4;cz<=(floor.getZ()+34)>>4;cz++){
            var chunk=new net.minecraft.world.level.ChunkPos(cx,cz);
            if(!level.getForcedChunks().contains(chunk.toLong())){ADDED_FORCED_CHUNKS.add(chunk);level.setChunkForced(cx,cz,true);}
        }
        for(int x=0;x<85;x++)for(int z=0;z<35;z++) {
            level.setBlock(floor.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=1;y<12;y++)level.setBlock(floor.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        BuildingAssetGameTests.clearPreexistingFixtureDrops(helper,floor,85,35);
        BuildingPlacement anchor=install(level,DreamseekersMod.DREAM_ANCHOR,floor.offset(2,0,2));
        FarmPlacement garden=(FarmPlacement)install(level,DreamseekersMod.REVERIE_GARDEN,floor.offset(38,0,2));
        var soils=garden.getBlocks().stream().filter(b->DreamseekersMod.REVERIE_GARDEN.isCropSoil(b.getBlockState())).map(BuildingBlock::getBlockPos).toList();
        for(BlockPos soil:soils)level.setBlock(soil.above(),Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7,0),2);
        BlockPos crop=soils.get(0).above();level.setBlock(crop,Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7,7),2);
        FireflyWorker worker=DreamseekersMod.FIREFLY_WORKER.get().create(level);
        helper.assertTrue(worker!=null,"actual firefly exists");worker.setOwnerName(OWNER);
        worker.moveTo(crop.getX()+.5,crop.getY(),crop.getZ()+.5,0,0);
        helper.assertTrue(level.addFreshEntity(worker),"active worker spawned");
        // Initial cargo is a declared fixture just below the unchanged 50-resource threshold.
        // Only an actual mature-crop harvest may cross it; no test calls the return/deposit method.
        worker.getItems().add(new ItemStack(Items.WHEAT,12));
        int initialFood=Resources.getTotalResourcesFromItems(worker.getItems()).food;
        helper.assertTrue(initialFood==48 && !Unit.atThresholdResources(worker),"real source valuation has 48 food before harvest");
        int[] issuedTick={-1};
        System.out.println("FIREFLY_FARM_PUBLICATION spawned worldTick="+helper.getTick()+" entityTick="+worker.tickCount+" published="+(level.getEntity(worker.getId())==worker));
        helper.startSequence().thenWaitUntil(()->helper.assertTrue(worker.tickCount>2&&level.getEntity(worker.getId())==worker&&level.getEntity(worker.getUUID())==worker,
                "ordinary FARM requires actual selected unit publication and simulation"))
            .thenExecute(()->{
                helper.assertTrue(Resources.getTotalResourcesFromItems(worker.getItems()).food==48&&worker.getGatherResourceGoal().getTargetResourceName()==ResourceName.NONE,
                        "ready fixture still has exactly declared cargo48 and no hidden work; stacks="+worker.getItems());
                helper.assertTrue(level.getBlockState(crop).is(Blocks.WHEAT)&&level.getBlockState(crop).getValue(BlockStateProperties.AGE_7)==7
                        &&BuildingUtils.findBuilding(false,crop)==garden,"ready fixture has one mature target in the exact live garden");
                issuedTick[0]=worker.tickCount;
                UnitServerEvents.addActionItem(OWNER,UnitAction.FARM,-1,new int[]{worker.getId()},crop,garden.originPos);
                System.out.println("FIREFLY_FARM_PUBLICATION issued worldTick="+helper.getTick()+" entityTick="+worker.tickCount+" id="+worker.getId()+" uuid="+worker.getUUID());
            }).thenIdle(1).thenExecute(()->System.out.println("FIREFLY_FARM_PUBLICATION after_dispatch "+FireflyNavigationTestDiagnostics.describe(worker,account)));

        boolean[] autoReturn={false};boolean[] delivered={false};boolean[] finished={false};
        // Native .25-attribute flight measures about .05 blocks/tick; this is a
        // bounded 66-block round trip plus unchanged harvest/replant work, not a speed buff.
        var checks=helper.startSequence().thenWaitUntil(()->helper.assertTrue(issuedTick[0]>=0,"real FARM issued before work clock"));
        for(int tick=10;tick<=1750;tick+=10) {
            final int elapsed=tick;
            checks.thenWaitUntil(()->helper.assertTrue(worker.tickCount>=issuedTick[0]+elapsed,"wait unchanged actual work budget "+elapsed))
                .thenExecute(()->{
            if(finished[0])return;
            helper.assertTrue(level.noCollision(worker),"working/delivery flight never clips solids");
            if(worker.getReturnResourcesGoal().getBuildingTarget()==anchor)autoReturn[0]=true;
            if(account.food>0) {
                delivered[0]=true;
                helper.assertTrue(account.food==52,"exact initial cargo plus single genuine harvest credited once: "+account.food);
                helper.assertTrue(Resources.getTotalResourcesFromItems(worker.getItems()).food==0,"delivered resource stacks removed");
            }
            if(delivered[0] && worker.getGatherResourceGoal().getTargetFarm()==garden
                    && level.getBlockState(crop).is(Blocks.WHEAT) && level.getBlockState(crop).getValue(BlockStateProperties.AGE_7)<7) {
                helper.assertTrue(autoReturn[0],"threshold triggered real automatic return, not explicit command");
                helper.assertTrue(account.wood==500-ResourceCosts.REPLANT_WOOD_COST,"saved farm task resumed and replanted once");
                helper.assertTrue(DreamseekersMod.REVERIE_GARDEN.isCropSoil(level.getBlockState(crop.below())),"original dyedream soil survives round trip");
                finished[0]=true;
                System.out.println("DREAMSEEKERS_FIREFLY_AUTORETURN_RESUME_PASS food="+account.food+" pos="+worker.position());
                helper.succeed();
            }
            });
            if(java.util.Set.of(100,300,500,700,950,1200,1500,1750).contains(elapsed))checks.thenExecute(()->
                System.out.println("FIREFLY_AUTO_RETURN_DIAGNOSTIC workTick="+elapsed+" worldTick="+helper.getTick()+" "+FireflyNavigationTestDiagnostics.describe(worker,account)
                        +" entityTick="+worker.tickCount+" published="+(level.getEntity(worker.getId())==worker)+" stacks="+worker.getItems()
                        +" nearby="+level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,worker.getBoundingBox().inflate(2)).stream().map(e->e.getItem()+"@"+e.position()).toList()));
        }
        checks.thenWaitUntil(()->helper.assertTrue(worker.tickCount>=issuedTick[0]+1760,"final actual-work budget"))
            .thenExecute(()->helper.assertTrue(finished[0],"auto return and resumed replant must complete: food="+account.food+" wood="+account.wood+" return="+autoReturn[0]+" pos="+worker.position()));
    }
    private static BuildingPlacement install(ServerLevel level,Building building,BlockPos origin) {
        for(BuildingBlock block:BuildingUtils.getAbsoluteBlockData(building.getRelativeBlockData(level),level,origin,Rotation.NONE))level.setBlock(block.getBlockPos(),block.getBlockState(),2);
        BuildingPlacement placement=BuildingUtils.getNewBuildingPlacement(building,level,origin,Rotation.NONE,OWNER,false);
        BuildingServerEvents.getBuildings().add(placement);placement.onBuilt();return placement;
    }
}
