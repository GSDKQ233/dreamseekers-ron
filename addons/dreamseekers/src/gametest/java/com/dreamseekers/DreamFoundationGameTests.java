package com.dreamseekers;
import com.dreamseekers.worldtree.*;
import com.dreamseekers.unit.FireflyWorker;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Native readied MatchStart and paid placement, no template prebuild/onBuilt/selfBuilding/warpten. */
@GameTestHolder("dreamseekers_foundation") @PrefixGameTestTemplate(false)
public final class DreamFoundationGameTests {
 private static Run active;
 @AfterBatch(batch="dream_foundation") public static void cleanup(ServerLevel ignored){if(active!=null){active.close();active=null;}}
 @GameTest(template="branch_test_arena",batch="dream_foundation",timeoutTicks=6500)
 public static void nativeReadiedStartBuildAndPaidFoundation(GameTestHelper h){active=new Run(h);active.prepare();h.onEachTick(active::tick);}
 static final class Run {
  final GameTestHelper h;final ServerLevel level;final String owner="DreamStartFoundation";
  final Set<Long> chunks=new HashSet<>();final List<LivingEntity> units=new ArrayList<>();
  PassiveIncomePeer peer;DreamAnchorPlacement anchor,paidAnchor;boolean addedRtsPeer,fallbackSent,finished;int ticks,completedTick=-1;BlockPos site;
  Run(GameTestHelper h){this.h=h;level=h.getLevel();}
  void check(boolean pass,String reason){h.assertTrue(pass,reason);}
  void prepare(){
   check(!PlayerServerEvents.rtsLocked&&PlayerServerEvents.getRTSPlayer(owner)==null,"unlocked normal fresh owner");
   var buildings=List.of(DreamseekersMod.DREAM_ANCHOR,DreamseekersMod.REVERIE_GARDEN,DreamseekersMod.BEETLE_SANCTUARY,DreamseekersMod.DREAM_DEPOT,DreamseekersMod.SHADOW_MINE,DreamseekersMod.SHADOW_FURNACE,DreamseekersMod.DREAM_HALL,DreamseekersMod.RESONANCE_LABORATORY,DreamseekersMod.CONTRACT_ECOLOGY,DreamseekersMod.RESONANCE_DAIS,DreamseekersMod.SHADOW_WATCHTOWER);
   for(var building:buildings)for(var rotation:Rotation.values()){
    var blocks=BuildingUtils.getAbsoluteBlockData(building.getRelativeBlockData(level),level,BlockPos.ZERO,rotation);
    int low=blocks.stream().mapToInt(b->b.getBlockPos().getY()).min().orElseThrow();
    long eligible=blocks.stream().filter(b->b.getBlockPos().getY()<=low+building.foundationYLayers-1&&building.startingBlockTypes.contains(b.getBlockState().getBlock())&&!b.getBlockState().isAir()).count();
    check(eligible>0,"nonempty real starting whitelist/template intersection: "+building.structureName+" / "+rotation);
    if(building==DreamseekersMod.DREAM_ANCHOR)check(eligible==33,"precise PD brick foundation count33, not all99floorcells");
   }
   check(DreamseekersMod.DREAM_ANCHOR.startingBlockTypes.contains(Blocks.STONE_BRICKS),"legacy stone foundation compatibility retained");
   site=h.absolutePos(new BlockPos(60,1,60));
   for(int x=(site.getX()-30)>>4;x<=(site.getX()+90)>>4;x++)for(int z=(site.getZ()-30)>>4;z<=(site.getZ()+35)>>4;z++){
    long c=ChunkPos.asLong(x,z);if(!level.getForcedChunks().contains(c)){level.setChunkForced(x,z,true);chunks.add(c);}level.getChunk(x,z);}
   // Isolate this underground test arena from natural falling gravel above the cleared volume.
   // Production placement validation and real worker construction assertions stay unchanged.
   for(int x=-25;x<=85;x++)for(int z=-25;z<=30;z++)level.setBlock(site.offset(x,23,z),Blocks.STONE.defaultBlockState(),2);
   for(int x=-25;x<=85;x++)for(int z=-25;z<=30;z++){level.setBlock(site.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<=22;y++)level.setBlock(site.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
   peer=new PassiveIncomePeer(level,owner);peer.actor.moveTo(Vec3.atCenterOf(site.above()));
   addedRtsPeer=!PlayerServerEvents.players.contains(peer.actor);if(addedRtsPeer)PlayerServerEvents.players.add(peer.actor);
   PlayerServerEvents.startRTS(peer.actor.getId(),new Vec3(site.getX(),site.getY(),site.getZ()),DreamseekersMod.DREAMSEEKERS,1);
   var rts=PlayerServerEvents.getRTSPlayer(owner);check(rts!=null&&rts.faction==DreamseekersMod.DREAMSEEKERS,"native readied start creates correct RTS player");
   anchor=(DreamAnchorPlacement)BuildingServerEvents.getBuildings().stream().filter(b->b.ownerName.equals(owner)&&b.getBuilding()==DreamseekersMod.DREAM_ANCHOR).findFirst().orElseThrow();
   check(anchor.model()==WorldTreeModel.BASE_V5&&!anchor.isBuilt&&!anchor.selfBuilding,"native newBASEv5 remains unfinished and not editor-self-building");
   System.out.println("DREAM_FOUNDATION_INITIAL placed="+anchor.getBlocksPlaced()+" queued="+DreamFoundationQueueProbe.queued(anchor)+" origin="+anchor.originPos+" min="+anchor.minCorner+" high="+anchor.getHighestBlockCountReached()+" scaffold="+anchor.getScaffoldBlocks().size()+" site="+site+" floor="+level.getBlockState(anchor.minCorner.below()));
   check(anchor.getBlocksPlaced()==0&&DreamFoundationQueueProbe.queued(anchor)==33,"native placement queues33foundations while zero blocks prebuilt; placed="+anchor.getBlocksPlaced()+" queued="+DreamFoundationQueueProbe.queued(anchor));
   check(!anchor.shouldBeDestroyed(),"queued foundations prevent immediate empty-building destruction");
   var r=ExtendedResourcesSync.account(owner);check(r.food==ResourcesServerEvents.STARTING_FOOD_READIED&&r.wood==ResourcesServerEvents.STARTING_WOOD_READIED&&r.ore==ResourcesServerEvents.STARTING_ORE_READIED,"readied starting capitol is free; exact standard starting economy");
  }
  List<LivingEntity> workers(){return UnitServerEvents.getAllUnits().stream().filter(e->e instanceof FireflyWorker&&e instanceof Unit u&&u.getOwnerName().equals(owner)).toList();}
  void tick(){if(finished)return;ticks++;
   check(PlayerServerEvents.getRTSPlayer(owner)!=null&&BuildingServerEvents.getBuildings().contains(anchor)&&!anchor.isDestroyedServerside&&!anchor.shouldBeDestroyed(),"native starting anchor/player survive, no autodefeat at tick"+ticks);
   var workers=workers();for(var worker:workers)if(!units.contains(worker))units.add(worker);
   if(ticks>=5)check(workers.size()==5,"normal MatchStart creates five original Firefly workers");
   if(!anchor.isBuilt&&!fallbackSent&&workers.size()==5&&workers.stream().allMatch(w->level.getEntity(w.getId())==w&&level.isPositionEntityTicking(w.blockPosition())&&w.tickCount>0)){
    boolean alreadyAssigned=workers.stream().anyMatch(w->((WorkerUnit)w).getBuildRepairGoal().getBuildingTarget()==anchor);
    if(!alreadyAssigned){fallbackSent=true;new UnitActionItem(owner,UnitAction.BUILD_REPAIR,-1,workers.stream().mapToInt(LivingEntity::getId).toArray(),anchor.originPos,BlockPos.ZERO).action(level);
     check(workers.stream().anyMatch(w->((WorkerUnit)w).getBuildRepairGoal().getBuildingTarget()==anchor),"one visibility-gated native fallback build command admitted");}
   }
   if(ticks==60)check(anchor.getBlocksPlaced()>0,"real queued foundation blocks placed beyond old2second defeat window");
   if(anchor.isBuilt&&completedTick<0){
    check(anchor.getBlocksTotal()==376&&anchor.getBlocksPlaced()==376&&anchor.getHealth()==380&&anchor.partialBlocksDestroyed==0,"actual worker completes all376blocks at380HP");
    check(BuildingServerEvents.getTotalPopulationSupply(owner)==DreamseekersMod.DREAM_ANCHOR.cost.population,"native completion grants exact capitol population");completedTick=ticks;
    paidPlacementAndCancel();
   }
   if(completedTick>=0&&ticks-completedTick>=100&&paidAnchor!=null&&paidAnchor.isBuilt){
    check(paidAnchor.getBlocksPlaced()==376&&paidAnchor.getHealth()==380&&BuildingServerEvents.getTotalPopulationSupply(owner)==30,"ordinary paid second Anchor really worker-completes at fullHP and adds population");
    System.out.println("DREAM_FOUNDATION_NATIVE_PASS native_readied_start queued33 no_autodefeat five_workers complete376_hp380 population paid_400_100_refund paid_worker_complete376_pop30 all11_rotations");finished=true;h.succeed();}
  }
  void paidPlacementAndCancel(){
   // Explicit fixture funding through the real ledger; no editor placement/cheat path.
   var r=ExtendedResourcesSync.account(owner);check(ResourceTransaction.creditIncome(level.getServer(),r,r.accountEpoch,r.revision,new ResourceTransaction.Cost(1000,1000,1000)).applied(),"fund ordinary second-building purchase");
   int food=r.food,wood=r.wood,ore=r.ore;var paidSite=site.offset(45,0,0);
   check(level.hasChunksAt(paidSite.offset(-12,-1,-12),paidSite.offset(12,14,12)),"paid target actual geometry chunks loaded before validation");
   System.out.println("DREAM_FOUNDATION_PAID_PREFLIGHT site="+paidSite+" validity="+BuildingValidators.getPlacementValidityError(level,DreamseekersMod.DREAM_ANCHOR,paidSite,owner,Rotation.CLOCKWISE_90,false,false,true)+" resources="+food+","+wood+","+ore+" ground="+level.getBlockState(paidSite)+" firstCompleteTicks="+ticks);
   var paid=BuildingServerEvents.placeBuilding(DreamseekersMod.DREAM_ANCHOR,paidSite,Rotation.CLOCKWISE_90,owner,new int[0],false,false,false,true);
   System.out.println("DREAM_FOUNDATION_PAID_ACTUAL "+(paid==null?"null":"placed="+paid.getBlocksPlaced()+" queued="+DreamFoundationQueueProbe.queued(paid)+" built="+paid.isBuilt+" self="+paid.selfBuilding+" origin="+paid.originPos));
   check(paid!=null&&!paid.isBuilt&&!paid.selfBuilding&&DreamFoundationQueueProbe.queued(paid)==33,"ordinary paid rotated anchor queues33 real foundations");
   check(r.food==food&&r.wood==wood-400&&r.ore==ore-100,"ordinary paid placement exact400wood100ore");
   check(BuildingServerEvents.placeBuilding(DreamseekersMod.DREAM_ANCHOR,paidSite,Rotation.CLOCKWISE_90,owner,new int[0],false,false,false,true)==null,"duplicate ordinary placement rejected");
   check(r.wood==wood-400&&r.ore==ore-100,"rejected duplicate no second charge");
   check(paid.getBlocksPlaced()==0,"cancel at genuine zero-progress boundary");BuildingServerEvents.cancelBuilding(paid,owner);
   check(!BuildingServerEvents.getBuildings().contains(paid)&&r.food==food&&r.wood==wood&&r.ore==ore,"native unbuilt cancel refunds actual purchase exactly");
   paidAnchor=(DreamAnchorPlacement)BuildingServerEvents.placeBuilding(DreamseekersMod.DREAM_ANCHOR,paidSite,Rotation.CLOCKWISE_90,owner,workers().stream().mapToInt(LivingEntity::getId).toArray(),false,false,false,true);
   check(paidAnchor!=null&&!paidAnchor.selfBuilding&&r.wood==wood-400&&r.ore==ore-100,"re-purchase starts ordinary actual-worker construction with exact quote");
  }
  void close(){
   for(var e:new ArrayList<>(UnitServerEvents.getAllUnits()))if(e instanceof Unit u&&owner.equals(u.getOwnerName())){Unit.fullResetBehaviours(u);e.discard();}
   BuildingServerEvents.getBuildings().removeIf(p->owner.equals(p.ownerName));PlayerServerEvents.rtsPlayers.removeIf(p->owner.equals(p.name));ResourcesServerEvents.removeAccount(owner);
   if(peer!=null){if(addedRtsPeer)PlayerServerEvents.players.remove(peer.actor);peer.close();}
   for(long c:chunks){var p=new ChunkPos(c);level.setChunkForced(p.x,p.z,false);}
  }
 }
}
