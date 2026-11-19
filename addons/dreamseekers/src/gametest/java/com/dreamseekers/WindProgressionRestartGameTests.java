package com.dreamseekers;
import com.dreamseekers.progression.*;
import com.dreamseekers.building.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.research.economy.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;
/** Actual independent-JVM normal stop/load, no account/building/queue injection in verify. */
@GameTestHolder("dreamseekers_wind_restart") @PrefixGameTestTemplate(false)
public final class WindProgressionRestartGameTests {
 private static final String OWNER="WindProgressRestart_Account";
 private static final File EXPECTED=new File("wind-progression-restart-expectations.nbt");
 private static final BlockPos ORIGIN=new BlockPos(12000,100,12000);
 private static final List<ProductionPlacement> HOSTS=new ArrayList<>();
 private static final Set<Long> CHUNKS=new HashSet<>();
 private static Resources account;private static PassiveIncomePeer peer;private static UUID pendingReceipt;
 private static final class WindRestartStopWitness {
  @SubscribeEvent(priority=EventPriority.HIGH) public void stop(ServerStoppingEvent event){
   if(pendingReceipt==null||!account.paidReceipts.containsKey(pendingReceipt)||HOSTS.get(1).productionQueue.size()!=1||DreamWindProgression.read(account).level(0)!=1)throw new AssertionError("stop witness requires original unfinished paid next-tier queue");
   var t=new CompoundTag();t.putLong("jvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());t.putUUID("epoch",account.accountEpoch);t.putUUID("receipt",pendingReceipt);t.putLong("lab",HOSTS.get(1).originPos.asLong());t.putUUID("placement",ResearchPlacementIdentity.serverIdentity(HOSTS.get(1)));t.putLongArray("chunks",new ArrayList<>(CHUNKS));t.putInt("food",account.food);t.putInt("wood",account.wood);t.putInt("ore",account.ore);t.putInt("shadow",account.extra.get(com.dreamseekers.economy.DreamPassiveEconomy.SHADOW));
   var saved=new CompoundTag();account.writeExtended(saved);t.put("liveAccountExtension",saved);
   try{NbtIo.writeCompressed(t,EXPECTED);}catch(Exception error){throw new RuntimeException(error);}peer.close();System.out.println("DREAM_WIND_RESTART_STOP_WITNESS");
  }
 }
 @GameTest(template="dreamseekers_test_arena",batch="wind_progression_restart",timeoutTicks=200)
 public static void earnedEpochCompletedTiersAndOriginalPaidQueueRestart(GameTestHelper h)throws Exception {
  switch(System.getProperty("dreamseekers.wind.restart.phase","")){case "prepare"->prepare(h);case "verify"->verify(h);default->throw new IllegalArgumentException("prepare|verify phase required");}
 }
 private static ProductionPlacement install(GameTestHelper h,Building definition,int x){
  var level=h.getLevel();var pos=ORIGIN.offset(x,0,0);
  for(int cx=(pos.getX()-4)>>4;cx<=(pos.getX()+30)>>4;cx++)for(int cz=(pos.getZ()-4)>>4;cz<=(pos.getZ()+30)>>4;cz++){long key=ChunkPos.asLong(cx,cz);if(!level.getForcedChunks().contains(key)){level.setChunkForced(cx,cz,true);CHUNKS.add(key);}}
  for(int dx=-3;dx<28;dx++)for(int dz=-3;dz<28;dz++){level.setBlock(pos.offset(dx,0,dz),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<22;y++)level.setBlock(pos.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),2);}
  for(var b:BuildingUtils.getAbsoluteBlockData(definition.getRelativeBlockData(level),level,pos,Rotation.NONE))level.setBlock(b.getBlockPos(),b.getBlockState(),2);
  var p=(ProductionPlacement)BuildingUtils.getNewBuildingPlacement(definition,level,pos,Rotation.NONE,OWNER,false);BuildingServerEvents.getBuildings().add(p);p.onBuilt();HOSTS.add(p);return p;
 }
 private static String start(ProductionPlacement host,DreamWindResearch.Tier tier){return ReceiptResearchService.start(peer.actor,host,tier,ResearchPlacementIdentity.serverIdentity(host),host.getCaptureRevision(),account.accountEpoch,account.revision);}
 private static void prepare(GameTestHelper h){
  h.assertTrue(!EXPECTED.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null,"fresh isolated restart fixture");
  PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(OWNER,DreamseekersMod.DREAMSEEKERS,97310,0,false,0L,0L));account=new Resources(OWNER,50000,50000,50000);account.extra.put(com.dreamseekers.economy.DreamPassiveEconomy.SHADOW,1000);ResourcesServerEvents.resourcesList.add(account);peer=new PassiveIncomePeer(h.getLevel(),OWNER);
  install(h,DreamseekersMod.DREAM_ANCHOR,0);for(int n=0;n<3;n++)install(h,DreamseekersMod.RESONANCE_LABORATORY,40+n*40);
  h.assertTrue(DreamWindProgression.earnWind(h.getLevel(),OWNER,account.accountEpoch),"earned entitlement fixture hook; branch payment verified separately");
  for(int family=0;family<3;family++){var host=HOSTS.get(family+1);h.assertTrue(start(host,DreamWindResearch.TIERS[family][0]).equals("started"),"real first-tier paid admission");host.productionQueue.get(0).ticksLeft=1;}
  MinecraftForge.EVENT_BUS.register(new WindRestartStopWitness());
  h.runAtTickTime(5,()->{
   for(int family=0;family<3;family++)h.assertTrue(DreamWindProgression.read(account).level(family)==1,"first tier genuinely completed before saved next purchase");
   var lab=HOSTS.get(1);h.assertTrue(start(lab,DreamWindResearch.TIERS[0][1]).equals("started"),"next-tier paid queue");pendingReceipt=lab.productionQueue.get(0).receiptResearch.receiptId();
  });
  h.runAtTickTime(12,()->{
   h.assertTrue(HOSTS.get(1).productionQueue.size()==1&&HOSTS.get(1).productionQueue.get(0).ticksLeft>0,"unfinished queue will be canceled by native restart policy");
   PlayerServerEvents.saveRTSPlayers();BuildingServerEvents.saveBuildings(h.getLevel());ResourcesServerEvents.saveResources(h.getLevel());
   h.assertTrue(account.food==49580&&account.wood==49520&&account.ore==49600&&account.extra.get(com.dreamseekers.economy.DreamPassiveEconomy.SHADOW)==945,"live debit unchanged by saved-copy projection");System.out.println("DREAM_WIND_RESTART_PREPARED");h.succeed();
  });
 }
 private static void verify(GameTestHelper h)throws Exception {
  var expected=NbtIo.readCompressed(EXPECTED);h.assertTrue(expected.getLong("jvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"independent JVM, not in-memory roundtrip");
  var r=ExtendedResourcesSync.account(OWNER);h.assertTrue(r!=null&&r.accountEpoch.equals(expected.getUUID("epoch")),"original epoch loaded by native account loader");var s=DreamWindProgression.read(r);h.assertTrue(s.supported()&&s.wind()&&s.level(0)==1&&s.level(1)==1&&s.level(2)==1,"earned flag and completed paid tiers survived restart");
  var lab=BuildingServerEvents.getBuildings().stream().filter(p->p.originPos.equals(BlockPos.of(expected.getLong("lab")))&&p.ownerName.equals(OWNER)).map(p->(ProductionPlacement)p).findFirst().orElseThrow();
  h.assertTrue(lab instanceof DreamLaboratoryPlacement&&lab.productionQueue.isEmpty()&&ResearchPlacementIdentity.serverIdentity(lab).equals(expected.getUUID("placement")),"native restored lab retains identity with empty queue");
  h.assertTrue(r.food==49740&&r.wood==49720&&r.ore==49720&&r.extra.get(com.dreamseekers.economy.DreamPassiveEconomy.SHADOW)==965,"exact original pending tier2 FWO/S refund once");h.assertTrue(!r.paidReceipts.containsKey(expected.getUUID("receipt")),"projected refund removed consumed claim in saved account");
  try(var wire=new PassiveIncomePeer(h.getLevel(),OWNER)) {
   String cancelled=ReceiptResearchService.cancel(wire.actor,lab,expected.getUUID("receipt"),ResearchPlacementIdentity.serverIdentity(lab),lab.getCaptureRevision(),r.accountEpoch,r.revision);
   h.assertTrue(cancelled.equals("unknown_receipt")&&r.food==49740,"old receipt replay cannot refund twice");
   var tier=DreamWindResearch.TIERS[0][1];h.assertTrue(ReceiptResearchService.start(wire.actor,lab,tier,ResearchPlacementIdentity.serverIdentity(lab),lab.getCaptureRevision(),r.accountEpoch,r.revision).equals("started"),"restart released next-level reservation");
   var a=lab.productionQueue.get(0);h.assertTrue(ReceiptResearchService.cancel(wire.actor,lab,a.receiptResearch.receiptId(),ResearchPlacementIdentity.serverIdentity(lab),lab.getCaptureRevision(),r.accountEpoch,r.revision).equals("cancelled_refunded"),"new paid next tier cancels once");
  }
  h.assertTrue(r.food==49740&&DreamWindProgression.read(r).level(0)==1,"no free repeat progress or extra refund");
  for(var p:List.copyOf(BuildingServerEvents.getBuildings()))if(p.ownerName.equals(OWNER)){p.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(p);}PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(OWNER));ResourcesServerEvents.resourcesList.removeIf(a->a.ownerName.equals(OWNER));for(long c:expected.getLongArray("chunks")){var pos=new ChunkPos(c);h.getLevel().setChunkForced(pos.x,pos.z,false);}System.out.println("DREAM_WIND_PROCESS_RESTART_PASS");h.succeed();
 }
}
