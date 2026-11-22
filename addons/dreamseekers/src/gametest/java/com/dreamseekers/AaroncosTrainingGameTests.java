package com.dreamseekers;

import com.dreamseekers.aaroncos.*;
import com.dreamseekers.building.DreamLaboratoryPlacement;
import com.dreamseekers.function.DreamFunctionLifecycle;
import com.dreamseekers.function.DreamFunctionState;
import com.dreamseekers.production.*;
import com.dreamseekers.progression.*;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.worldtree.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Candidate tests: physical upgraded-tree fixtures are not proof of the tree-upgrade purchase flow.
 * Only naturalFullDurationTraining uses the full training work clock; spawn-failure probes shorten it. */
@GameTestHolder("dreamseekers_hands_training") @PrefixGameTestTemplate(false)
public final class AaroncosTrainingGameTests {
    private static final String BATCH="hands_training";
    private static final List<F> FIXTURES=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level){for(var f:List.copyOf(FIXTURES))f.close();FIXTURES.clear();}
    static final class F implements AutoCloseable {
        final GameTestHelper h;final String owner;final Resources r;final List<BuildingPlacement> hosts=new ArrayList<>();
        final Set<Long> forced=new HashSet<>();final List<Object> listeners=new ArrayList<>();final BlockPos origin;
        F(GameTestHelper h,String suffix,int x){this.h=h;owner="Hands_"+suffix;origin=new BlockPos(x,110,26000);
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));
            r=new Resources(owner,10000,10000,10000);r.extra.put(DreamPassiveEconomy.SHADOW,1000);ResourcesServerEvents.resourcesList.add(r);FIXTURES.add(this);}
        BuildingPlacement install(Building definition,int offset){var level=h.getLevel();var pos=origin.offset(offset,0,0);
            for(int x=(pos.getX()-8)>>4;x<=(pos.getX()+30)>>4;x++)for(int z=(pos.getZ()-8)>>4;z<=(pos.getZ()+30)>>4;z++){
                long key=ChunkPos.asLong(x,z);if(!level.getForcedChunks().contains(key)){level.setChunkForced(x,z,true);forced.add(key);}}
            for(int x=-7;x<29;x++)for(int z=-7;z<29;z++){level.setBlock(pos.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<24;y++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
            for(var block:BuildingUtils.getAbsoluteBlockData(definition.getRelativeBlockData(level),level,pos,Rotation.NONE))level.setBlock(block.getBlockPos(),block.getBlockState(),2);
            var p=BuildingUtils.getNewBuildingPlacement(definition,level,pos,Rotation.NONE,owner,false);BuildingServerEvents.getBuildings().add(p);p.onBuilt();hosts.add(p);return p;}
        DreamAnchorPlacement tree(WorldTreeModel model,int offset){var p=(DreamAnchorPlacement)install(DreamseekersMod.DREAM_ANCHOR,offset);
            for(var block:p.getBlocks())h.getLevel().setBlock(block.getBlockPos(),Blocks.AIR.defaultBlockState(),2);
            var blocks=p.blocks(model);for(var block:blocks)h.getLevel().setBlock(block.getBlockPos(),block.getBlockState(),2);
            var s=p.tree();s.model=model;s.phase=model.branch==WorldTreeModel.Branch.BASE?WorldTreeState.Phase.IDLE:WorldTreeState.Phase.COMPLETE;s.ascended=model.branch==WorldTreeModel.Branch.SHADOW;
            p.commitGeometry(blocks,model.count,0);return p;}
        DreamHallPlacement hall(){return (DreamHallPlacement)install(DreamseekersMod.DREAM_HALL,64);}
        void lab(){install(DreamseekersMod.RESONANCE_LABORATORY,128);}
        long handCount(){return UnitServerEvents.getAllUnits().stream().filter(e->e.isAlive()&&e instanceof Unit u&&u.getOwnerName().equals(owner)
            &&(e.getType()==DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get()||e.getType()==DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get())).count();}
        void at(int tick,Runnable run){h.runAtTickTime(tick,run);}
        public void close(){FIXTURES.remove(this);listeners.forEach(MinecraftForge.EVENT_BUS::unregister);for(var e:List.copyOf(UnitServerEvents.getAllUnits()))if(e instanceof Unit u&&u.getOwnerName().equals(owner))e.discard();
            for(var p:hosts){p.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(p);}
            ResourcesServerEvents.resourcesList.removeIf(a->a.ownerName.equals(owner));PlayerServerEvents.rtsPlayers.removeIf(a->a.name.equals(owner));
            for(long c:forced)h.getLevel().setChunkForced(ChunkPos.getX(c),ChunkPos.getZ(c),false);}
    }
    @GameTest(templateNamespace="dreamseekers_hands_training",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=30)
    public static void baseTrainWorkIsScopedToBothMilitaryBuildings(GameTestHelper h){
        // Each row follows the actual registration / queue menu order, not translated labels or HUD estimates.
        assertBaseTrainSeconds(h,DreamseekersMod.BEETLE_SANCTUARY.productions.get(),
                List.of(16,24,16,20,15,16),"six Sanctuary recipes");
        assertBaseTrainSeconds(h,DreamseekersMod.DREAM_HALL.productions.get(),
                List.of(24,32,20,36,20,24,28,24,24,28,36,36,72,72),"fourteen Hall recipes");
        h.assertTrue(DreamseekersMod.WORKER_PRODUCTION.defaultCost.ticks==300
                &&DreamseekersMod.LEGACY_WORKER_PRODUCTION.defaultCost.ticks==300,
                "capital worker and non-trainable legacy worker retain their original 15s work");
        h.succeed();
    }
    private static void assertBaseTrainSeconds(GameTestHelper h,List<ProductionItem> items,List<Integer> seconds,String label){
        h.assertTrue(items.size()==seconds.size(),label+" count");
        for(int i=0;i<items.size();i++)h.assertTrue(items.get(i).defaultCost.ticks==seconds.get(i)*20,
                label+" "+items.get(i).getItemName()+" should require "+seconds.get(i)+" seconds");
    }
    @GameTest(templateNamespace="dreamseekers_hands_training",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void exactMixedPaymentAndImmutableRefundAfterWind(GameTestHelper h){
        var f=new F(h,"Receipt",26000);f.tree(WorldTreeModel.WIND_V4,0);f.lab();var hall=f.hall();var item=DreamseekersMod.AARONCOS_LEFT_HAND_PRODUCTION;
        f.at(10,()->{
            h.assertTrue(item.defaultCost.food==400&&item.defaultCost.wood==250&&item.defaultCost.ore==350&&item.defaultCost.ticks==1440&&item.defaultCost.population==8,"explicit full standard cost/time/population");
            h.assertTrue(item.extraCost(false,f.owner).get(DreamPassiveEconomy.SHADOW)==100,"fixed100S quote");
            f.r.extra.put(DreamPassiveEconomy.SHADOW,99);long rev=f.r.revision;
            h.assertTrue(!hall.startProductionItem(item)&&f.r.revision==rev&&f.r.food==10000&&hall.productionQueue.isEmpty(),"S insufficient cannot partially charge FWO");
            f.r.extra.put(DreamPassiveEconomy.SHADOW,1000);
            h.assertTrue(hall.startProductionItem(item)&&f.r.food==9600&&f.r.wood==9750&&f.r.ore==9650&&f.r.extra.get(DreamPassiveEconomy.SHADOW)==900,"one atomic mixed payment");
            h.assertTrue(UnitServerEvents.getCurrentPopulation(f.owner)==8,"real queued8 population");
            var receipt=f.r.paidReceipts.values().iterator().next();h.assertTrue(receipt.food()==400&&receipt.extra().get(DreamPassiveEconomy.SHADOW)==100,"immutable receipt includes shadow");
            h.assertTrue(DreamWindProgression.earnWind(h.getLevel(),f.owner,f.r.accountEpoch),"earn wind fixture");
            h.assertTrue(item.getCost(false,f.owner).food==280&&item.extraCost(false,f.owner).get(DreamPassiveEconomy.SHADOW)==100,"newwind quote discounts FWO but not S");
            // Deliberate refund above normal income cap; refunds may not silently discard paid shadow.
            f.r.extra.put(DreamPassiveEconomy.SHADOW,1000);
            h.assertTrue(hall.cancelProductionItem(item,true)&&f.r.food==10000&&f.r.wood==10000&&f.r.ore==10000&&f.r.extra.get(DreamPassiveEconomy.SHADOW)==1100,"refund uses original FWO and full100S beyond cap");
            h.assertTrue(!hall.cancelProductionItem(item,true)&&f.r.paidReceipts.isEmpty()&&UnitServerEvents.getCurrentPopulation(f.owner)==0,"repeatcancel no second refund/population");
            h.succeed();
        });
    }
    @GameTest(templateNamespace="dreamseekers_hands_training",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=110)
    public static void physicalPrerequisiteBothBranchesAndPauseResume(GameTestHelper h){
        var f=new F(h,"Prerequisite",26300);var tree=f.tree(WorldTreeModel.BASE_V3,0);var hall=f.hall();f.lab();var item=DreamseekersMod.AARONCOS_RIGHT_HAND_PRODUCTION;float[] remaining={0};
        f.at(10,()->{
            h.assertTrue(!hall.startProductionItem(item),"unupgraded tree cannot unlock hands");
            DreamWindProgression.earnWind(h.getLevel(),f.owner,f.r.accountEpoch);
            h.assertTrue(!hall.startProductionItem(item),"globalwind flag withoutphysicalupgradedtree is insufficient");
            BuildingServerEvents.getBuildings().remove(tree);var replacement=f.tree(WorldTreeModel.WIND_V4,0);
            h.assertTrue(AaroncosTraining.hasUpgradedAnchor(hall)&&hall.startProductionItem(item),"actualwindbranch meets narrow gate");
            remaining[0]=hall.productionQueue.get(0).ticksLeft;BuildingServerEvents.getBuildings().remove(replacement);
        });
        f.at(30,()->{h.assertTrue(hall.productionQueue.get(0).ticksLeft==remaining[0],"missingphysicaltree pausespaidqueue");f.tree(WorldTreeModel.SHADOW_V3,0);});
        f.at(50,()->{h.assertTrue(AaroncosTraining.hasUpgradedAnchor(hall)&&hall.productionQueue.get(0).ticksLeft<remaining[0],"shadowbranch also resumes samepaidqueue");h.assertTrue(hall.cancelProductionItem(item,true),"cancel paused/resumed original receipt");h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_hands_training",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=1520)
    public static void naturalFullDurationTrainingAndActualPopulation(GameTestHelper h){
        var f=new F(h,"Natural",26600);f.tree(WorldTreeModel.SHADOW_V3,0);f.lab();var hall=f.hall();var item=DreamseekersMod.AARONCOS_LEFT_HAND_PRODUCTION;
        f.at(20,()->{h.assertTrue(ProductionWorkRates.percent(hall,item)==100,"noWind/dais trainingrate boost");h.assertTrue(hall.startProductionItem(item),"natural72second fullprice start");h.assertTrue(hall.productionQueue.get(0).ticksLeft==1440,"actual full1440work");});
        f.at(1450,()->h.assertTrue(f.handCount()==0&&hall.productionQueue.size()==1,"not completed before1440actualticks"));
        f.at(1490,()->{h.assertTrue(f.handCount()==1&&hall.productionQueue.isEmpty()&&f.r.paidReceipts.isEmpty(),"actual hand spawned once andreceipt consumed");h.assertTrue(UnitServerEvents.getCurrentPopulation(f.owner)==8,"queue reservation replaced by actual8population");h.assertTrue(!hall.cancelProductionItem(item,true),"completed unit cannot refund");h.succeed();});
    }
    /** Vanilla spawn emits Join before produceUnit sets owner; match the one fixture's live production window instead. */
    private static boolean matchesTrainingBirth(EntityJoinLevelEvent e,DreamHallPlacement hall,ProductionItem item){
        if(e.getLevel()!=hall.getLevel()||hall.productionQueue.isEmpty()
            ||hall.productionQueue.get(0).item!=item||hall.productionQueue.get(0).ticksLeft>1)return false;
        var p=hall.getUnitSpawnPoint((ServerLevel)hall.getLevel(),false,new net.minecraft.core.Vec3i(0,0,0));
        var entity=e.getEntity();
        // EntityType.spawn centers X/Z and may vertically offset to the floor before Join.
        return Math.abs(entity.getX()-(p.getX()+0.5))<0.001
            &&Math.abs(entity.getZ()-(p.getZ()+0.5))<0.001&&Math.abs(entity.getY()-p.getY())<=2;
    }
    private static final class CancelHandSpawn {
        final DreamHallPlacement hall;int rejected;boolean armed;
        CancelHandSpawn(DreamHallPlacement hall){this.hall=hall;}
        @SubscribeEvent public void join(EntityJoinLevelEvent e){if(armed&&matchesTrainingBirth(e,hall,DreamseekersMod.AARONCOS_RIGHT_HAND_PRODUCTION)
            &&e.getEntity().getType()==DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get()){rejected++;e.setCanceled(true);}}
    }
    @GameTest(templateNamespace="dreamseekers_hands_training",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=140)
    public static void cancelledSpawnKeepsPaidQueueAndRetriesWithoutSecondCharge(GameTestHelper h){
        var f=new F(h,"Spawn",26900);f.tree(WorldTreeModel.WIND_V4,0);f.lab();var hall=f.hall();var item=DreamseekersMod.AARONCOS_RIGHT_HAND_PRODUCTION;
        var cancel=new CancelHandSpawn(hall);MinecraftForge.EVENT_BUS.register(cancel);f.listeners.add(cancel);long[] revision={0};
        f.at(10,()->{h.assertTrue(hall.startProductionItem(item),"paid right hand queue");cancel.armed=true;hall.productionQueue.get(0).ticksLeft=1;revision[0]=f.r.revision;});
        f.at(35,()->{h.assertTrue(cancel.rejected>0&&f.handCount()==0&&hall.productionQueue.size()==1&&f.r.paidReceipts.size()==1,"Forge cancelled genuine spawn retains queueandreceipt");h.assertTrue(f.r.revision==revision[0]&&UnitServerEvents.getCurrentPopulation(f.owner)==8,"retry no charge/population duplication");MinecraftForge.EVENT_BUS.unregister(cancel);});
        f.at(80,()->{h.assertTrue(f.handCount()==1&&hall.productionQueue.isEmpty()&&f.r.paidReceipts.isEmpty(),"successful retrypublishes once");h.assertTrue(f.r.food==9600&&f.r.wood==9750&&f.r.ore==9650&&f.r.extra.get(DreamPassiveEconomy.SHADOW)==900,"one exactmixedpayment only");h.assertTrue(UnitServerEvents.getCurrentPopulation(f.owner)==8,"no stale failed entity inpopulation");h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_hands_training",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void populationGateAndOverflowRefundRetainExactClaim(GameTestHelper h){
        var f=new F(h,"Overflow",27500);f.tree(WorldTreeModel.WIND_V4,0);f.lab();var hall=f.hall();var item=DreamseekersMod.AARONCOS_RIGHT_HAND_PRODUCTION;
        UUID[] receipt={null};
        f.at(10,()->{
            int maximum=UnitServerEvents.maxPopulation;
            try {UnitServerEvents.maxPopulation=7;long rev=f.r.revision;
                h.assertTrue(!hall.startProductionItem(item)&&f.r.revision==rev&&hall.productionQueue.isEmpty(),"eightpop cannot buy belowhardlimit; no partial payment");
            } finally {UnitServerEvents.maxPopulation=maximum;}
            h.assertTrue(hall.startProductionItem(item),"fullmixed purchase after valid population restored");receipt[0]=hall.attachedUnitReceipts().iterator().next();
            f.r.extra.put(DreamPassiveEconomy.SHADOW,Integer.MAX_VALUE);
            h.assertTrue(hall.cancelProductionItem(item,true)&&hall.productionQueue.isEmpty(),"queue cancels even when refund temporarily unrepresentable");
            h.assertTrue(f.r.paidReceipts.containsKey(receipt[0])&&f.r.extra.get(DreamPassiveEconomy.SHADOW)==Integer.MAX_VALUE&&f.r.food==9600,"overflow refund retains whole claim without partial FWO orS");
            f.r.extra.put(DreamPassiveEconomy.SHADOW,1500);
        });
        f.at(50,()->{h.assertTrue(!f.r.paidReceipts.containsKey(receipt[0])&&f.r.food==10000&&f.r.wood==10000&&f.r.ore==10000&&f.r.extra.get(DreamPassiveEconomy.SHADOW)==1600,"natural detached retry returns exact originalfourcurrencies once");h.succeed();});
    }

    private static final class RemovePrerequisiteOnJoin {
        final DreamHallPlacement hall;final BuildingPlacement lab;boolean removed;boolean armed;
        RemovePrerequisiteOnJoin(DreamHallPlacement hall,BuildingPlacement lab){this.hall=hall;this.lab=lab;}
        @SubscribeEvent public void join(EntityJoinLevelEvent e){if(armed&&!removed&&matchesTrainingBirth(e,hall,DreamseekersMod.AARONCOS_LEFT_HAND_PRODUCTION)
            &&e.getEntity().getType()==DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get()){
            removed=true;BuildingServerEvents.getBuildings().remove(lab);
        }}
    }
    @GameTest(templateNamespace="dreamseekers_hands_training",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=140)
    public static void birthCallbackLostPrerequisiteCannotCommitPaidUnit(GameTestHelper h){
        var f=new F(h,"JoinPrerequisite",27800);f.tree(WorldTreeModel.WIND_V4,0);var lab=f.install(DreamseekersMod.RESONANCE_LABORATORY,128);var hall=f.hall();var item=DreamseekersMod.AARONCOS_LEFT_HAND_PRODUCTION;
        var listener=new RemovePrerequisiteOnJoin(hall,lab);MinecraftForge.EVENT_BUS.register(listener);f.listeners.add(listener);
        f.at(10,()->{h.assertTrue(hall.startProductionItem(item),"paidqueue before callback");listener.armed=true;hall.productionQueue.get(0).ticksLeft=1;});
        f.at(35,()->{h.assertTrue(listener.removed&&f.handCount()==0&&hall.productionQueue.size()==1&&f.r.paidReceipts.size()==1,"spawn callback prerequisite loss rolls back body and retains exactpaidqueue");
            h.assertTrue(UnitServerEvents.getCurrentPopulation(f.owner)==8,"no failedbody population leak");BuildingServerEvents.getBuildings().add(lab);});
        f.at(80,()->{h.assertTrue(f.handCount()==1&&hall.productionQueue.isEmpty()&&f.r.paidReceipts.isEmpty(),"restored prerequisite resumes original payment exactlyonce");h.assertTrue(f.r.extra.get(DreamPassiveEconomy.SHADOW)==900&&f.r.food==9600,"no second mixed fee");h.succeed();});
    }

    @GameTest(templateNamespace="dreamseekers_hands_training",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=130)
    public static void unsupportedAndDetachedLaboratoryCannotUnlockOrAdvanceHands(GameTestHelper h){
        var f=new F(h,"LabLifecycle",28100);f.tree(WorldTreeModel.WIND_V4,0);
        var lab=(DreamLaboratoryPlacement)f.install(DreamseekersMod.RESONANCE_LABORATORY,128);
        var hall=f.hall();var left=DreamseekersMod.AARONCOS_LEFT_HAND_PRODUCTION;var right=DreamseekersMod.AARONCOS_RIGHT_HAND_PRODUCTION;
        float[] remaining={0};
        f.at(10,()->{
            h.assertTrue(lab.available()&&AaroncosTraining.hasUpgradedAnchor(hall),"genuine available laboratory enables hand gate");
            var good=lab.getDataStorage().getData(DreamFunctionLifecycle.STATE).write();var unknown=good.copy();
            unknown.putInt("schemaVersion",99);unknown.putString("futurePayload","preserve-hand-gate");
            lab.getDataStorage().setData(DreamFunctionLifecycle.STATE,DreamFunctionState.read(unknown));
            long revision=f.r.revision;
            h.assertTrue(lab.isBuilt&&BuildingServerEvents.getBuildings().contains(lab)&&!lab.available(),"future laboratory remains physically built and registered but unavailable");
            h.assertTrue(!hall.startProductionItem(left)&&!hall.startProductionItem(right)&&f.r.revision==revision&&hall.productionQueue.isEmpty(),"unsupported laboratory unlocks neither hand and cannot charge");
            h.assertTrue(lab.getDataStorage().getData(DreamFunctionLifecycle.STATE).write().equals(unknown),"unknown payload is not repaired or reset by prerequisites");
            lab.getDataStorage().setData(DreamFunctionLifecycle.STATE,DreamFunctionState.read(good));
            h.assertTrue(hall.startProductionItem(left),"restored supported laboratory allows exact paid queue");
            remaining[0]=hall.productionQueue.get(0).ticksLeft;
            lab.onRemoved(BuildingPlacement.RemovalReason.RELOAD); // Intentionally keep stale registered placement.
            h.assertTrue(BuildingServerEvents.getBuildings().contains(lab)&&lab.isBuilt&&!lab.available(),"detached terminal lab still registered for negative probe");
            revision=f.r.revision;
            h.assertTrue(!hall.startProductionItem(right)&&f.r.revision==revision,"terminal lab cannot authorize another purchase");
        });
        f.at(35,()->{
            h.assertTrue(hall.productionQueue.size()==1&&hall.productionQueue.get(0).ticksLeft==remaining[0]&&f.r.paidReceipts.size()==1&&f.handCount()==0,"terminal lab pauses paid work without consuming payment");
            BuildingServerEvents.getBuildings().remove(lab);f.lab();
        });
        f.at(60,()->{
            h.assertTrue(hall.productionQueue.get(0).ticksLeft<remaining[0]&&f.r.paidReceipts.size()==1,"fresh available laboratory resumes original paid work");
            h.assertTrue(hall.cancelProductionItem(left,true)&&f.r.food==10000&&f.r.wood==10000&&f.r.ore==10000&&f.r.extra.get(DreamPassiveEconomy.SHADOW)==1000,"original mixed receipt refunded once after lifecycle pause");h.succeed();
        });
    }

}
