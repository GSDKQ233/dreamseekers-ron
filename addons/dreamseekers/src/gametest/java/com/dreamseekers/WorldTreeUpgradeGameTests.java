package com.dreamseekers;
import com.dreamseekers.worldtree.*;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;
/** Real world writes/native worker repair. Author only compiled; parent must run Forge tests. */
@GameTestHolder("dreamseekers_tree_e") @PrefixGameTestTemplate(false)
public final class WorldTreeUpgradeGameTests {
 private static Run active;
 @AfterBatch(batch="tree_e") public static void cleanup(ServerLevel ignored){if(active!=null){active.close();active=null;}}
 @GameTest(templateNamespace="dreamseekers_tree_e",template="ore_mine_test_arena",batch="tree_e",timeoutTicks=6500)
 public static void fullTimerBlockedRefundDamagedUpgradeAndRepair(GameTestHelper h){active=new Run(h);active.prepare();h.onEachTick(active::tick);}
 private static final class Run {
  final GameTestHelper h;final ServerLevel level;final String owner="WorldTreeE_Timer";
  final Set<Long> chunks=new HashSet<>();DreamAnchorPlacement p;Resources r;PassiveIncomePeer peer;Mob worker;BlockPos obstacle;int ticks,phase,entered,hpBefore,woodBefore;UUID receipt;long food,wood,ore,emerald,shadow;boolean repairCommandSent;
  Run(GameTestHelper helper){h=helper;level=h.getLevel();}
  void check(boolean yes,String reason){h.assertTrue(yes,reason);}
  void prepare(){
   check(PlayerServerEvents.getRTSPlayer(owner)==null,"isolated fresh owner");PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));r=new Resources(owner,10000,10000,10000);r.extra.put(DreamPassiveEconomy.SHADOW,1000);ResourcesServerEvents.resourcesList.add(r);peer=new PassiveIncomePeer(level,owner);
   var origin=h.absolutePos(new BlockPos(30,1,30));
   for(int x=(origin.getX()-20)>>4;x<=(origin.getX()+25)>>4;x++)for(int z=(origin.getZ()-20)>>4;z<=(origin.getZ()+25)>>4;z++){long c=ChunkPos.asLong(x,z);if(!level.getForcedChunks().contains(c)){level.setChunkForced(x,z,true);chunks.add(c);}}
   // Isolate this underground test cavity from natural falling terrain before clearing it.
   // The non-gravity roof is above WIND's highest model block (origin.y + 18).
   for(int x=-20;x<26;x++)for(int z=-20;z<26;z++)level.setBlock(origin.offset(x,20,z),Blocks.STONE.defaultBlockState(),2);
   for(int x=-20;x<26;x++)for(int z=-20;z<26;z++){level.setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<20;y++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
   for(var b:BuildingUtils.getAbsoluteBlockData(DreamseekersMod.DREAM_ANCHOR.getRelativeBlockData(level),level,origin,Rotation.NONE))level.setBlock(b.getBlockPos(),b.getBlockState(),2);
   p=(DreamAnchorPlacement)BuildingUtils.getNewBuildingPlacement(DreamseekersMod.DREAM_ANCHOR,level,origin,Rotation.NONE,owner,false);p.isBuilt=true;BuildingServerEvents.getBuildings().add(p);
   check(p.getHealth()==380,"base tree is genuinely complete380HP");
   obstacle=p.upgradedBlocks().stream().filter(b->!b.getBlockState().isAir()&&b.getBlockPos().getY()>p.maxCorner.getY()).findFirst().orElseThrow().getBlockPos();
   level.setBlock(obstacle,Blocks.GOLD_BLOCK.defaultBlockState(),2);
   check(DreamWorldTreeService.start(peer.actor,p,p.tree().instance).equals("foreign_block"),"prepayment obstacle rejects");check(r.food==10000&&r.wood==10000&&r.ore==10000&&r.extra.get(DreamPassiveEconomy.SHADOW)==1000,"rejectedstart nocharge");level.setBlock(obstacle,Blocks.AIR.defaultBlockState(),2);
   check(DreamWorldTreeService.start(peer.actor,p,p.tree().instance).equals("started"),"normalpaidstart");receipt=p.tree().receipt;
   check(r.food==9400&&r.wood==9200&&r.ore==9400&&r.extra.get(DreamPassiveEconomy.SHADOW)==850,"exact FWO/S debit");
  }
  void tick(){ticks++;
   if(phase==0){
    if(ticks==100)level.setBlock(obstacle,Blocks.GOLD_BLOCK.defaultBlockState(),2);
    if(ticks==2300){check(!p.ascended()&&p.tree().remaining>0,"full2400effective timer notshortened");check(r.food==9400&&r.wood==9200&&r.ore==9400&&r.emerald==0,"uncompletedtree no FWO or emerald income");}
    if(p.tree().phase==WorldTreeState.Phase.BLOCKED){check(ticks>=2399,"blocked only afterfulltimer");check(level.getBlockState(obstacle).is(Blocks.GOLD_BLOCK),"foreignblock untouched");check(!p.ascended()&&p.getUpgradeLevel()==0,"blocked nolevel/unlock");
     check(DreamWorldTreeService.cancel(peer.actor,p,p.tree().instance).equals("cancelled_refunded"),"blockedcancel fullrefund");check(r.food==10000&&r.wood==10000&&r.ore==10000&&r.extra.get(DreamPassiveEconomy.SHADOW)==1000,"allreceiptamounts refundedexact");
     check(!r.paidReceipts.containsKey(receipt),"receipt removedonce");check(!DreamWorldTreeService.cancel(peer.actor,p,p.tree().instance).equals("cancelled_refunded"),"repeatcancel nosecondrefund");level.setBlock(obstacle,Blocks.AIR.defaultBlockState(),2);
     WorldTreeBranchGameTests.checkReviewedWall(h,p,WorldTreeModel.SHADOW_V5);
     p.destroyRandomBlocks(95);hpBefore=p.getHealth();check(hpBefore>0&&hpBefore<380,"realphysical building damage beforeupgrade");
     String restartStatus=DreamWorldTreeService.start(peer.actor,p,p.tree().instance);
     if(!restartStatus.equals("started")) {
      System.out.println("WORLD_TREE_RESTART_REJECT status="+restartStatus+" plan="+WorldTreeMorph.plan(p).error()+" phase="+p.tree().phase+" hp="+p.getHealth()+" built="+p.isBuilt+" destroyed="+p.shouldBeDestroyed()+" owns="+DreamWorldTreeService.owns(peer.actor,p)+" FWO="+r.food+","+r.wood+","+r.ore+" S="+r.extra.get(DreamPassiveEconomy.SHADOW));
      for(var b:p.getBlocks()){var current=level.getBlockState(b.getBlockPos());if(!current.isAir()&&!current.equals(b.getBlockState()))System.out.println("WORLD_TREE_STATE_MISMATCH pos="+b.getBlockPos()+" expected="+b.getBlockState()+" actual="+current+" claims="+p.claims(b.getBlockPos())+" placed="+b.isPlaced(level));}
     }
     check(restartStatus.equals("started"),"restartupgrade realpayment: "+restartStatus);p.tree().remaining=1; // second transition only accelerated; full first2400 above is real
     phase=1;entered=ticks;
    }
   }else if(phase==1&&p.ascended()){
    check(Math.abs(p.getHealth()-hpBefore)<=1,"sameHP preserved throughphysicalmorph");check(p.getBlocksTotal()==351&&p.maxCorner.getY()-p.minCorner.getY()+1==17,"new fullgeometryboundstotal");check(p.getBlocksPlaced()<351,"realrepairableholes ratherthanphantomHP");check(!DreamWorldTreeService.cancel(peer.actor,p,p.tree().instance).equals("cancelled_refunded"),"complete cannotcancel");
    worker=DreamseekersMod.FIREFLY_WORKER.get().create(level);((Unit)worker).setOwnerName(owner);worker.moveTo(p.originPos.getX()+5.5,p.originPos.getY()+2,p.originPos.getZ()-6.5);check(level.addFreshEntity(worker),"actualworkercreated");
    woodBefore=r.wood;phase=2;entered=ticks;
   }else if(phase==2){
    if(!repairCommandSent&&level.getEntity(worker.getId())==worker&&level.isPositionEntityTicking(worker.blockPosition())&&worker.tickCount>0){
     repairCommandSent=true; // Exactly one original command after ordinary entity registration/ticking.
     new UnitActionItem(owner,UnitAction.BUILD_REPAIR,-1,new int[]{worker.getId()},p.originPos,BlockPos.ZERO).action(level);
     check(((WorkerUnit)worker).getBuildRepairGoal().getBuildingTarget()==p,"one native repair command admitted after entity visibility");
    }
    if(p.getHealth()==p.getMaxHealth()){
    check(repairCommandSent,"repair completed only after one admitted native command");
    check(p.getBlocksPlaced()==351&&p.partialBlocksDestroyed==0,"nativeworker restoresallphysicalholes");check(r.wood<woodBefore,"normalrepair consumeswood despitepassiveincome");
    Unit.fullResetBehaviours((Unit)worker);food=r.food;wood=r.wood;ore=r.ore;emerald=r.emerald;shadow=r.extra.get(DreamPassiveEconomy.SHADOW);phase=3;entered=ticks;
   }}else if(phase==3&&ticks-entered==200){check(r.food-food==10&&r.wood-wood==10&&r.ore-ore==10&&r.emerald-emerald==2&&r.extra.get(DreamPassiveEconomy.SHADOW)-shadow==2,"exact two100tick F5/W5/O5/E1/S1 resource cycles");System.out.println("WORLD_TREE_E_PASS realTimer2400,blockedrefund,physicaldamage,actualworkerrepair,income");h.succeed();phase=4;}
   if(ticks>6200&&phase<4)check(false,"realworker or upgrade timeout; do not teleport or injectrepairedHP");
  }
  void close(){if(peer!=null)peer.close();PlayerServerEvents.rtsPlayers.removeIf(x->x.name.equals(owner));ResourcesServerEvents.removeAccount(owner);if(worker!=null){Unit.fullResetBehaviours((Unit)worker);worker.discard();}if(p!=null)BuildingServerEvents.getBuildings().remove(p);for(long c:chunks){var pos=new ChunkPos(c);level.setChunkForced(pos.x,pos.z,false);}}
 }
}
