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
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;
/** Normal stop/load: no restored entity, building, account or task is injected in verify. */
@GameTestHolder("dreamseekers_tree_restart") @PrefixGameTestTemplate(false)
public final class WorldTreeRestartGameTests {
 static final String PREFIX="WorldTreeRestart_";static final File MANIFEST=new File("world-tree-restart-expectations.nbt");
 static final BlockPos ORIGIN=new BlockPos(10240,100,10240);
 static UUID refundReceipt;
 static final List<DreamAnchorPlacement> extraPrepared=new ArrayList<>();
 static final List<DreamAnchorPlacement> prepared=new ArrayList<>();static final List<PassiveIncomePeer> peers=new ArrayList<>();static final List<Long> chunks=new ArrayList<>();
 static Resources account(String owner){return ExtendedResourcesSync.account(owner);}
 static CompoundTag accountTag(Resources r){var t=new CompoundTag();t.putInt("food",r.food);t.putInt("wood",r.wood);t.putInt("ore",r.ore);t.putInt("emerald",r.emerald);r.writeExtended(t);return t;}
 static long[] missing(DreamAnchorPlacement p){return p.getBlocks().stream().filter(b->!b.getBlockState().isAir()&&!b.isPlaced(p.getLevel())).mapToLong(b->b.getBlockPos().asLong()).sorted().toArray();}
 static CompoundTag extraWitness(DreamAnchorPlacement p){
  var t=new CompoundTag();t.putString("owner",p.ownerName);t.putLong("origin",p.originPos.asLong());t.putString("rotation",p.rotation.name());
  t.put("state",p.tree().write());t.putUUID("accountEpoch",account(p.ownerName).accountEpoch);
  t.putBoolean("windUnlocked",com.dreamseekers.progression.DreamWindProgression.server(p.ownerName).wind());
  t.putInt("hp",p.getHealth());t.putInt("highest",p.getHighestBlockCountReached());t.putDouble("partial",p.partialBlocksDestroyed);t.putLongArray("missing",missing(p));return t;
 }
 static DreamAnchorPlacement extraFixture(GameTestHelper h,int offset,WorldTreeModel model){
  var origin=ORIGIN.offset(offset,0,0);var rotation=Rotation.CLOCKWISE_180;
  for(int x=-15;x<26;x++)for(int z=-15;z<26;z++){h.getLevel().setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<21;y++)h.getLevel().setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var blocks=BuildingUtils.getAbsoluteBlockData(model.blocks(),h.getLevel(),origin,rotation);for(var b:blocks)h.getLevel().setBlock(b.getBlockPos(),b.getBlockState(),2);
  var p=new DreamAnchorPlacement(DreamseekersMod.DREAM_ANCHOR,h.getLevel(),origin,rotation,PREFIX+1,blocks,true);
  p.tree().model=model;p.isBuilt=true;BuildingServerEvents.getBuildings().add(p);extraPrepared.add(p);return p;
 }
 static final class StopWitness {
  /** Core resource save is HIGHEST; building save is NORMAL. Capture the same unmutated final tick. */
  @SubscribeEvent(priority=EventPriority.HIGH) public void stop(ServerStoppingEvent event){
   var root=new CompoundTag();root.putLong("jvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());root.putString("incomeContract","F5/W5/O5/E1/S1-per-100-effective-ticks");root.putBoolean("normalStopWitness",true);root.putLong("stopGameTime",event.getServer().overworld().getGameTime());root.putLongArray("chunks",chunks);var list=new ListTag();
   for(var p:prepared){var t=new CompoundTag();t.putString("owner",p.ownerName);t.putLong("origin",p.originPos.asLong());t.putString("rotation",p.rotation.name());t.put("state",p.tree().write());t.put("account",accountTag(account(p.ownerName)));t.putInt("hp",p.getHealth());t.putInt("highest",p.getHighestBlockCountReached());t.putDouble("partial",p.partialBlocksDestroyed);t.putLongArray("missing",missing(p));t.putLong("lastTick",p.tree().lastTick);list.add(t);}
   var extras=new ListTag();for(var p:extraPrepared)extras.add(extraWitness(p));root.put("extraBranches",extras);
   root.put("trees",list);root.putUUID("refundReceipt",refundReceipt);root.put("refundAccount",accountTag(account(PREFIX+3)));try{NbtIo.writeCompressed(root,MANIFEST);}catch(Exception e){throw new RuntimeException(e);}
   for(var peer:peers)peer.close();peers.clear();System.out.println("WORLD_TREE_RESTART_STOP_WITNESS trees="+prepared.size());
  }
 }
 @GameTest(templateNamespace="dreamseekers_tree_restart",template="ore_mine_test_arena",batch="tree_restart",timeoutTicks=6500)
 public static void realSavedTaskDamagedGeometryAndNativeRepair(GameTestHelper h)throws Exception{
  switch(System.getProperty("dreamseekers.tree.restart.phase","")){case "prepare"->prepare(h);case "verify"->verify(h);default->throw new IllegalArgumentException("dreamseekers.tree.restart.phase prepare|verify required");}
 }
 static void prepare(GameTestHelper h){
  h.assertTrue(!MANIFEST.exists(),"fresh isolated manifest");prepared.clear();extraPrepared.clear();peers.clear();chunks.clear();
  for(int x=639;x<=655;x++)for(int z=639;z<=642;z++){long c=ChunkPos.asLong(x,z);h.assertTrue(!h.getLevel().getForcedChunks().contains(c),"fresh forcedchunk");h.getLevel().setChunkForced(x,z,true);chunks.add(c);}
  for(int n=0;n<4;n++){
   String owner=PREFIX+n;h.assertTrue(PlayerServerEvents.getRTSPlayer(owner)==null,"fresh owner");PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(owner,DreamseekersMod.DREAMSEEKERS,94320+n,0,false,0L,0L));
   var r=new Resources(owner,10000,10000,10000,37);r.extra.put(DreamPassiveEconomy.SHADOW,1000);ResourcesServerEvents.resourcesList.add(r);var peer=new PassiveIncomePeer(h.getLevel(),owner);peers.add(peer);
   var rotation=Rotation.values()[n];var origin=ORIGIN.offset(n*40,0,0);for(int x=-15;x<26;x++)for(int z=-15;z<26;z++){h.getLevel().setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<20;y++)h.getLevel().setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
   for(var b:BuildingUtils.getAbsoluteBlockData(DreamseekersMod.DREAM_ANCHOR.getRelativeBlockData(h.getLevel()),h.getLevel(),origin,rotation))h.getLevel().setBlock(b.getBlockPos(),b.getBlockState(),2);
   var p=(DreamAnchorPlacement)BuildingUtils.getNewBuildingPlacement(DreamseekersMod.DREAM_ANCHOR,h.getLevel(),origin,rotation,owner,false);p.isBuilt=true;BuildingServerEvents.getBuildings().add(p);if(n<3)prepared.add(p);
   if(n>0)p.destroyRandomBlocks(n==1?95:189.5);
   h.assertTrue(p.getHealth()>0,"real damaged tree alive before payment");h.assertTrue(WorldTreeMorph.plan(p).valid(),"rotated physical geometry preflight: "+rotation);h.assertTrue(DreamWorldTreeService.start(peer.actor,p,p.tree().instance).equals("started"),"actual paid upgrade start");
   if(n==3){refundReceipt=p.tree().receipt;r.food=Integer.MAX_VALUE;h.assertTrue(DreamWorldTreeService.cancel(peer.actor,p,p.tree().instance).equals("refund_pending"),"overflow retains earnedrefund for restart");BuildingServerEvents.getBuildings().remove(p);p.onRemoved(BuildingPlacement.RemovalReason.REPLACED);}
   else p.tree().remaining=n==0?1200:1; // explicit timer acceleration; separate full2400tick test covers full duration
  }
  var wind=extraFixture(h,160,WorldTreeModel.BASE_V3);wind.destroyRandomBlocks(95.25);
  h.assertTrue(DreamWorldTreeService.start(peers.get(1).actor,wind,wind.tree().instance,WorldTreeModel.WIND_V5).equals("started"),"same owner pays for Wind while owning Shadow upgrade");
  wind.tree().remaining=1; // Explicit completion acceleration; separate passed Wind suite covers real2400ticks.
  var legacyBase=extraFixture(h,200,WorldTreeModel.LEGACY_BASE);legacyBase.destroyRandomBlocks(22.5);
  MinecraftForge.EVENT_BUS.register(new StopWitness());
  h.runAtTickTime(120,()->{
   h.assertTrue(!prepared.get(0).ascended()&&prepared.get(0).tree().remaining>0,"unfinished real receipt remains active");
   h.assertTrue(prepared.get(1).ascended()&&prepared.get(2).ascended(),"two actual physical upgrades complete");
   h.assertTrue(account(PREFIX+1).emerald==38&&account(PREFIX+2).emerald==38,"one ordinary E cycle was earned before real disk persistence");
   h.assertTrue(prepared.get(1).getHealth()>1&&prepared.get(1).getHealth()<380&&prepared.get(2).getHealth()<=2,"half and lowHP survived genuine morph");
   h.assertTrue(wind.model()==WorldTreeModel.WIND_V5&&wind.getHealth()>1&&wind.getHealth()<380&&wind.getBlocksPlaced()<393,"paid Wind completed with real repair holes before save");
   h.assertTrue(com.dreamseekers.progression.DreamWindProgression.server(PREFIX+1).wind(),"original paid Wind entitlement earned before save");
   h.assertTrue(legacyBase.model()==WorldTreeModel.LEGACY_BASE&&legacyBase.getBlocksTotal()==324&&legacyBase.getHealth()<380,"unupgraded legacy base remains damaged");
   PlayerServerEvents.saveRTSPlayers();BuildingServerEvents.saveBuildings(h.getLevel());ResourcesServerEvents.saveResources(h.getLevel());
   System.out.println("WORLD_TREE_RESTART_PREPARED wait_normal_stop_for_exact_witness");h.succeed();
  });
 }
 static void assertRotatedBounds(GameTestHelper h,DreamAnchorPlacement p){
  var target=p.upgradedBlocks();
  int minX=target.stream().mapToInt(b->b.getBlockPos().getX()).min().orElseThrow(),maxX=target.stream().mapToInt(b->b.getBlockPos().getX()).max().orElseThrow();
  int minY=target.stream().mapToInt(b->b.getBlockPos().getY()).min().orElseThrow(),maxY=target.stream().mapToInt(b->b.getBlockPos().getY()).max().orElseThrow();
  int minZ=target.stream().mapToInt(b->b.getBlockPos().getZ()).min().orElseThrow(),maxZ=target.stream().mapToInt(b->b.getBlockPos().getZ()).max().orElseThrow();
  h.assertTrue(p.minCorner.equals(new BlockPos(minX,minY,minZ))&&p.maxCorner.equals(new BlockPos(maxX,maxY,maxZ)),"actual rotated placement bounds match full upgraded template");
 }
 static void repairDiagnostic(GameTestHelper h,String stage,DreamAnchorPlacement p,Mob worker){
  var goal=((WorkerUnit)worker).getBuildRepairGoal();var r=account(p.ownerName);
  System.out.println("TREE_RESTART_REPAIR_DIAG stage="+stage+" owner="+p.ownerName+" rotation="+p.rotation+" entity="+worker.getId()+" lookupSame="+(h.getLevel().getEntity(worker.getId())==worker)+" tickCount="+worker.tickCount+" alive="+worker.isAlive()+" removed="+worker.isRemoved()+" pos="+worker.position()+" goalSame="+(goal.getBuildingTarget()==p)+" building="+goal.isBuilding()+" navDone="+worker.getNavigation().isDone()+" path="+worker.getNavigation().getPath()+" hp="+p.getHealth()+" placed="+p.getBlocksPlaced()+" partial="+p.partialBlocksDestroyed+" wood="+r.wood+" loaded="+h.getLevel().isLoaded(worker.blockPosition())+" entityTicking="+h.getLevel().isPositionEntityTicking(worker.blockPosition()));
 }
 static void verify(GameTestHelper h)throws Exception{
  h.assertTrue(MANIFEST.isFile(),"normal prior stop witness exists");var expected=NbtIo.readCompressed(MANIFEST);
  h.assertTrue(expected.getString("incomeContract").equals("F5/W5/O5/E1/S1-per-100-effective-ticks"),"fresh five-resource restart witness, not an older E-free manifest");
  h.assertTrue(expected.getLong("jvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"independent process verify");
  for(long c:expected.getLongArray("chunks")){h.assertTrue(h.getLevel().getForcedChunks().contains(c),"forcedchunks ordinarily restored");var pos=new ChunkPos(c);h.getLevel().getChunk(pos.x,pos.z);}
  h.assertTrue(expected.getList("extraBranches",Tag.TAG_COMPOUND).size()==2,"new manifest records Wind and unchanged legacy base");
  var extraRestored=new ArrayList<DreamAnchorPlacement>();
  for(var raw:expected.getList("extraBranches",Tag.TAG_COMPOUND)){
   var t=(CompoundTag)raw;var saved=WorldTreeState.read(t.getCompound("state"));String owner=t.getString("owner");
   var p=(DreamAnchorPlacement)BuildingServerEvents.getBuildings().stream().filter(b->b.ownerName.equals(owner)&&b.originPos.asLong()==t.getLong("origin")).findFirst().orElseThrow();extraRestored.add(p);
   h.assertTrue(saved.supported&&p.tree().supported&&p.tree().instance.equals(saved.instance)&&p.model()==saved.model&&p.rotation.name().equals(t.getString("rotation")),"ordinary loader restores exact extra branch descriptor/instance/rotation");
   h.assertTrue(p.getBlocksTotal()==saved.model.count&&p.getHealth()==t.getInt("hp")&&p.getHighestBlockCountReached()==t.getInt("highest")&&Double.compare(p.partialBlocksDestroyed,t.getDouble("partial"))==0&&Arrays.equals(missing(p),t.getLongArray("missing")),"Wind/legacy base restart does not heal, move holes, change full count or partial damage");
   h.assertTrue(account(owner).accountEpoch.equals(t.getUUID("accountEpoch"))&&t.getBoolean("windUnlocked")&&com.dreamseekers.progression.DreamWindProgression.server(owner).wind(),"same owner epoch keeps earned Wind unlock across JVM restart");
   h.assertTrue(p.getUpgradeLevel()==0&&!p.ascended(),"Wind and legacy base never become Shadow level1");
   if(p.model()==WorldTreeModel.WIND_V5){h.assertTrue(p.maxCorner.getY()-p.minCorner.getY()+1==18&&p.tree().phase==WorldTreeState.Phase.COMPLETE&&p.tree().receipt==null,"completed Wind geometry18high no second receipt");assertRotatedBounds(h,p);}
   else h.assertTrue(p.model()==WorldTreeModel.LEGACY_BASE&&p.maxCorner.getY()-p.minCorner.getY()+1==10&&p.tree().phase==WorldTreeState.Phase.IDLE,"legacy base remains original10high idle model");
  }
  h.assertTrue(BuildingServerEvents.getBuildings().stream().anyMatch(b->b instanceof DreamAnchorPlacement p&&p.ownerName.equals(PREFIX+1)&&p.model()==WorldTreeModel.SHADOW_V5),"same owner Shadow and Wind coexist after ordinary reload");
  var refund=account(PREFIX+3);UUID refundId=expected.getUUID("refundReceipt");
  h.assertTrue(refund!=null&&accountTag(refund).equals(expected.getCompound("refundAccount"))&&refund.paidReceipts.get(refundId).purpose().equals(DreamWorldTreeService.purpose(WorldTreeModel.SHADOW_V5)+":refund"),"ordinary account loader restores exact orphan refund intent and originalamounts despite no surviving placement");
  refund.food=9400; // room returns; ordinary server retry must perform the original refund once
  var trees=new ArrayList<DreamAnchorPlacement>();var workers=new ArrayList<Mob>();var woodBefore=new HashMap<String,Integer>();var woodHarvestBefore=new HashMap<String,Integer>();var repairCost=new HashMap<String,Integer>();var commandCounts=new HashMap<String,Integer>();
  for(var raw:expected.getList("trees",Tag.TAG_COMPOUND)){
   var t=(CompoundTag)raw;String owner=t.getString("owner");var p=(DreamAnchorPlacement)BuildingServerEvents.getBuildings().stream().filter(b->b.ownerName.equals(owner)&&b.originPos.asLong()==t.getLong("origin")).findFirst().orElseThrow();trees.add(p);h.assertTrue(p.rotation.name().equals(t.getString("rotation")),"ordinary loader preserves exact rotation");
   h.assertTrue(PlayerServerEvents.getRTSPlayer(owner)!=null&&p.getBlocksPlaced()>0,"ordinary RTS/building/world loaders restored real state");var old=WorldTreeState.read(t.getCompound("state"));var s=p.tree();var r=account(owner);
   h.assertTrue(s.supported&&s.instance.equals(old.instance)&&r.accountEpoch.equals(old.ascended?old.incomeEpoch:old.payerEpoch),"exact persistent building and account identities");
   h.assertTrue(p.getHealth()==t.getInt("hp")&&p.getHighestBlockCountReached()==t.getInt("highest")&&Double.compare(p.partialBlocksDestroyed,t.getDouble("partial"))==0&&Arrays.equals(missing(p),t.getLongArray("missing")),"reload does not heal, fill holes or reset highest health denominator");
   long elapsed=s.lastTick-t.getLong("lastTick");h.assertTrue(elapsed>=0&&elapsed<1000,"only real running ticks elapsed across normal shutdown and startup");
   if(!old.ascended){h.assertTrue(!s.ascended&&s.receipt.equals(old.receipt)&&s.payerEpoch.equals(old.payerEpoch)&&r.paidReceipts.containsKey(s.receipt),"unfinished task and original paid receipt restored without projectionrefund");
    h.assertTrue(accountTag(r).equals(t.getCompound("account")),"pending upgrade balance/epoch/revision exactly retained");h.assertTrue(old.remaining-s.remaining==elapsed,"remaining advances only actual server ticks, no offline catchup");
   }else{
    h.assertTrue(s.ascended&&p.getBlocksTotal()==351&&p.maxCorner.getY()-p.minCorner.getY()+1==17,"upgradedvariant bounds load without rebuilding");assertRotatedBounds(h,p);
    long cycles=(old.incomeElapsed+elapsed)/100;h.assertTrue(r.food-t.getCompound("account").getInt("food")==cycles*5&&r.wood-t.getCompound("account").getInt("wood")==cycles*5&&r.ore-t.getCompound("account").getInt("ore")==cycles*5&&r.emerald-t.getCompound("account").getInt("emerald")==cycles,"saved FWOE production credit follows normal tick count, with no restart bonus");
    int oldShadow=t.getCompound("account").getCompound(ResourceAccountCodec.KEY).getCompound("extra").getInt(DreamPassiveEconomy.SHADOW.toString());
    h.assertTrue(r.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0)==Math.min(DreamPassiveEconomy.SHADOW_CAP,oldShadow+cycles),"saved shadow income retains its original independently capped rate");
    h.assertTrue(s.incomeElapsed==(old.incomeElapsed+elapsed)%100,"fractional cycle preserved");
    var worker=DreamseekersMod.FIREFLY_WORKER.get().create(h.getLevel());((Unit)worker).setOwnerName(owner);var approach=new BlockPos(5,2,-7).rotate(p.rotation).offset(p.originPos);worker.moveTo(approach.getX()+0.5,approach.getY(),approach.getZ()+0.5);h.assertTrue(h.getLevel().addFreshEntity(worker),"real new repairworker");workers.add(worker);woodBefore.put(owner,r.wood);
    woodHarvestBefore.put(owner,PlayerServerEvents.getRTSPlayer(owner).scores.scoreList.getOrDefault(RTSPlayerScoresEnum.WOOD_HARVESTED,0));
    h.assertTrue(p.blocksPerBuild==1,"native repair charges one wood per missing solid block");repairCost.put(owner,missing(p).length);h.assertTrue(repairCost.get(owner)>0,"real positive physical repair cost");
    repairDiagnostic(h,"spawned_waiting_for_entity_visibility",p,worker);
    boolean[] commandSent={false};
    h.onEachTick(()->{
     if(commandSent[0]||h.getLevel().getEntity(worker.getId())!=worker||!h.getLevel().isPositionEntityTicking(worker.blockPosition())||worker.tickCount<=0)return;
     commandSent[0]=true; // One original command only; never retry a rejected/admitted command.
     h.assertTrue(commandCounts.merge(owner,1,Integer::sum)==1,"exactly one original repair command sent");
     repairDiagnostic(h,"ready_before_command_gameTime="+h.getLevel().getGameTime(),p,worker);
     new UnitActionItem(owner,UnitAction.BUILD_REPAIR,-1,new int[]{worker.getId()},p.originPos,BlockPos.ZERO).action(h.getLevel());
     repairDiagnostic(h,"after_command",p,worker);
     h.assertTrue(((WorkerUnit)worker).getBuildRepairGoal().getBuildingTarget()==p,"single native repair command admitted after ordinary entity visibility");
    });
    for(long at:new long[]{20,100,1000,6000})h.runAtTickTime(at,()->repairDiagnostic(h,"tick"+at,p,worker));
   }
  }
  h.startSequence().thenWaitUntil(()->{
   h.assertTrue(!refund.paidReceipts.containsKey(refundId)&&refund.food==10000&&refund.wood==10000&&refund.ore==10000&&refund.emerald==37&&refund.extra.get(DreamPassiveEconomy.SHADOW)==1000,"ordinary retry restores orphan refund once after newJVM capacityreturns");
   h.assertTrue(trees.get(0).ascended(),"restored pending upgrade completes by ordinary remaining ticks");assertRotatedBounds(h,trees.get(0));
   for(int n=1;n<3;n++){var p=trees.get(n);h.assertTrue(p.getHealth()==380&&p.getBlocksPlaced()==351&&p.partialBlocksDestroyed==0,"normal native worker repairs reloaded physical holes to fullHP");}
  }).thenExecute(()->{
   for(int n=1;n<3;n++){var p=trees.get(n);int actualIncome=PlayerServerEvents.getRTSPlayer(p.ownerName).scores.scoreList.getOrDefault(RTSPlayerScoresEnum.WOOD_HARVESTED,0)-woodHarvestBefore.get(p.ownerName);
    int cost=repairCost.get(p.ownerName);h.assertTrue(actualIncome>=0&&cost>0,"actual credited income and positive native repair expense");
    h.assertTrue(account(p.ownerName).wood==woodBefore.get(p.ownerName)+actualIncome-cost,"exact wood balance = original + actual credited harvest - physical block repair cost");
    System.out.println("TREE_RESTART_REPAIR_ACCOUNTING owner="+p.ownerName+" original="+woodBefore.get(p.ownerName)+" actualIncome="+actualIncome+" repairCost="+cost+" final="+account(p.ownerName).wood);h.assertTrue(commandCounts.getOrDefault(p.ownerName,0)==1,"native repair completed with exactly one command");}
   PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));for(var worker:workers){Unit.fullResetBehaviours((Unit)worker);worker.discard();}BuildingServerEvents.getBuildings().removeAll(trees);BuildingServerEvents.getBuildings().removeAll(extraRestored);for(long c:expected.getLongArray("chunks")){var pos=new ChunkPos(c);h.getLevel().setChunkForced(pos.x,pos.z,false);}System.out.println("WORLD_TREE_PROCESS_RESTART_PASS real_task receipt shadow_wind_coexist legacy_base geometry health holes native_repair earned_wind_epoch");
  }).thenSucceed();
 }
}
