package com.dreamseekers;
import com.dreamseekers.progression.*;
import com.dreamseekers.building.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.research.economy.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("dreamseekers_wind_progression")
@PrefixGameTestTemplate(false)
public final class WindProgressionGameTests {
 private static final String BATCH="dreamseekers_wind_progression";
 private static final List<WindProgressionCase> CASES=new ArrayList<>();
 @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level){for(var c:List.copyOf(CASES))c.close();CASES.clear();}
 private static final class WindProgressionCase implements AutoCloseable {
  final GameTestHelper h;final ServerLevel level;final String owner;final Resources r;final PassiveIncomePeer peer;final List<BuildingPlacement> buildings=new ArrayList<>();final Set<Long> forced=new HashSet<>();
  WindProgressionCase(GameTestHelper h,String suffix){this.h=h;level=h.getLevel();owner="WindProgressGT_"+suffix;PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));r=new Resources(owner,50000,50000,50000);r.extra.put(com.dreamseekers.economy.DreamPassiveEconomy.SHADOW,1000);ResourcesServerEvents.resourcesList.add(r);peer=new PassiveIncomePeer(level,owner);CASES.add(this);install(DreamseekersMod.DREAM_ANCHOR,4);h.assertTrue(DreamWindProgression.earnWind(level,owner,r.accountEpoch),"fixture paid-entitlement hook setup, not branch completion claim");}
  ProductionPlacement install(Building definition,int x){var origin=BuildingAssetGameTests.outdoorPos(h,x,1,4);for(int cx=(origin.getX()-16)>>4;cx<=(origin.getX()+40)>>4;cx++)for(int cz=(origin.getZ()-16)>>4;cz<=(origin.getZ()+40)>>4;cz++){long chunk=net.minecraft.world.level.ChunkPos.asLong(cx,cz);if(!level.getForcedChunks().contains(chunk)){level.setChunkForced(cx,cz,true);forced.add(chunk);}level.getChunk(cx,cz);}for(int dx=-3;dx<28;dx++)for(int dz=-3;dz<28;dz++){level.setBlock(origin.offset(dx,0,dz),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<20;y++)level.setBlock(origin.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),2);}for(var b:BuildingUtils.getAbsoluteBlockData(definition.getRelativeBlockData(level),level,origin,Rotation.NONE))level.setBlock(b.getBlockPos(),b.getBlockState(),2);var p=BuildingUtils.getNewBuildingPlacement(definition,level,origin,Rotation.NONE,owner,false);BuildingServerEvents.getBuildings().add(p);p.onBuilt();buildings.add(p);return (ProductionPlacement)p;}
  String start(ProductionPlacement p,DreamWindResearch.Tier item){return ReceiptResearchService.start(peer.actor,p,item,ResearchPlacementIdentity.serverIdentity(p),p.getCaptureRevision(),r.accountEpoch,r.revision);}
  public void close(){for(long c:forced){var cp=new net.minecraft.world.level.ChunkPos(c);level.setChunkForced(cp.x,cp.z,false);}peer.close();for(var p:buildings){p.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(p);}PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(owner));ResourcesServerEvents.resourcesList.removeIf(a->a.ownerName.equals(owner));}
 }
 @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=120)
 public static void threeFamiliesFiveSequentialPaidTiersAndCrossLabReservation(GameTestHelper h){
  var f=new WindProgressionCase(h,"Tiers");var labs=new ProductionPlacement[]{f.install(DreamseekersMod.RESONANCE_LABORATORY,38),f.install(DreamseekersMod.RESONANCE_LABORATORY,60),f.install(DreamseekersMod.RESONANCE_LABORATORY,82)};
  for(int n=1;n<=5;n++){final int tier=n;h.runAtTickTime(40+n*4,()->{
   for(int family=0;family<3;family++){var item=DreamWindResearch.TIERS[family][tier-1];String outcome=f.start(labs[family],item);h.assertTrue(outcome.equals("started"),"canonical next paid tier starts family="+family+" tier="+tier+" outcome="+outcome+" built="+labs[family].isBuilt+" hp="+labs[family].getHealth()+" live="+BuildingServerEvents.getBuildings().contains(labs[family])+" loaded="+f.level.hasChunksAt(labs[family].minCorner,labs[family].maxCorner)+" available="+((DreamLaboratoryPlacement)labs[family]).available()+" anchor="+DreamWindResearch.hasAnchor(labs[family],false)+" valid="+item.validHostAndPrerequisite(labs[family])+" progress="+DreamWindProgression.read(f.r));int food=f.r.food;h.assertTrue(!f.start(labs[(family+1)%3],item).equals("started")&&f.r.food==food,"same-family second lab never double pays");labs[family].productionQueue.get(0).ticksLeft=1;}
  });h.runAtTickTime(40+n*4+2,()->{for(int family=0;family<3;family++)h.assertTrue(DreamWindProgression.read(f.r).level(family)==tier,"real native queue tick commits exactly next tier");});}
  h.runAtTickTime(64,()->{h.assertTrue(f.r.food==46100&&f.r.wood==45800&&f.r.ore==45800&&f.r.extra.get(com.dreamseekers.economy.DreamPassiveEconomy.SHADOW)==475,"all15 exact escalating debit totals");for(int family=0;family<3;family++){int food=f.r.food;h.assertTrue(!f.start(labs[family],DreamWindResearch.TIERS[family][4]).equals("started")&&f.r.food==food,"max cap cannot repurchase tier5");}h.assertTrue(f.r.paidReceipts.isEmpty(),"all15 receipts consumed once");System.out.println("DREAM_WIND_PROGRESS_PASS fifteen_paid_tiers_race_cap");h.succeed();});
 }
 @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=120)
 public static void fixedPointGlobalResearchIncludingStructuralResearch(GameTestHelper h){
  var f=new WindProgressionCase(h,"Rate");var lab=f.install(DreamseekersMod.RESONANCE_LABORATORY,38);var item=DreamWindResearch.TIERS[0][0];h.assertTrue(f.start(lab,item).equals("started"),"paid rate test queue");var active=lab.productionQueue.get(0);active.ticksLeft=100;
  h.assertTrue(ProductionWorkRates.percent(lab,item)==125,"owner global research125 percent");h.assertTrue(ProductionWorkRates.percent(lab,ProductionItems.RESEARCH_RESOURCE_CAPACITY)==125,"explicit ordinary legacy research receives global rate");h.assertTrue(ProductionWorkRates.percent(lab,ProductionItems.RESEARCH_GRAND_LIBRARY)==125,"explicit structure-changing research receives global rate");
  h.runAtTickTime(20,()->{h.assertTrue(active.workHundredths>=7375&&active.workHundredths<=7625,"twenty real ticks consume integer hundredths at1.25x without truncation");h.assertTrue(f.r.paidReceipts.size()==1,"work never charges again");System.out.println("DREAM_WIND_PROGRESS_PASS fixed_point_global_all_research");h.succeed();});
 }

 /** Explicit same-epoch maintenance faults, not a claim that ordinary concurrent purchases bypass admission. */
 @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=120)
 public static void obsoletePaidTierMaintenanceRefundsButMissingAnchorPauses(GameTestHelper h) {
  var f=new WindProgressionCase(h,"Maintenance");
  var lab=f.install(DreamseekersMod.RESONANCE_LABORATORY,38);
  var otherLab=f.install(DreamseekersMod.RESONANCE_LABORATORY,60);
  var anchor=f.buildings.get(0);
  var first=DreamWindResearch.TIERS[0][0];
  var second=DreamWindResearch.TIERS[0][1];
  var shadow=com.dreamseekers.economy.DreamPassiveEconomy.SHADOW;
  final UUID epoch=f.r.accountEpoch;
  final UUID[] pending={null};
  final ActiveProduction[] paused={null};
  final long[] pausedWork={0};
  final float[] pausedTicks={0};
  // Malformed/non-contiguous, already advanced, and lost predecessor are separately exercised.
  int[] masks={5,1,0};
  for(int index=0;index<3;index++) {
   final int fault=index;
   h.runAtTickTime(40+index*4,()->{
    f.r.addonData.getCompound(DreamWindProgression.KEY).putInt("cost",fault==2?1:0);
    var item=fault==2?second:first;
    h.assertTrue(f.start(lab,item).equals("started"),"maintenance fixture starts a legitimately paid canonical tier "+fault);
    h.assertTrue(f.r.paidReceipts.size()==1,"one original payment before maintenance fault");
    f.r.addonData.getCompound(DreamWindProgression.KEY).putInt("cost",masks[fault]);
   });
   h.runAtTickTime(42+index*4,()->{
    h.assertTrue(f.r.accountEpoch.equals(epoch),"maintenance keeps the original account epoch");
    h.assertTrue(lab.productionQueue.isEmpty()&&f.r.paidReceipts.isEmpty(),"native validation retires obsolete paid tier "+fault);
    h.assertTrue(f.r.food==50000&&f.r.wood==50000&&f.r.ore==50000&&f.r.extra.get(shadow)==1000,"obsolete tier refunds all original components once "+fault);
    // Restore valid maintained data; the same family must be admitted in another laboratory.
    f.r.addonData.getCompound(DreamWindProgression.KEY).putInt("cost",0);
    h.assertTrue(f.start(otherLab,first).equals("started"),"obsolete tier releases owner-global family reservation");
    var probe=otherLab.productionQueue.get(0);
    ReceiptResearchService.detachAndRefund(otherLab,probe);
    ReceiptResearchService.detachAndRefund(otherLab,probe);
    h.assertTrue(f.r.food==50000&&f.r.wood==50000&&f.r.ore==50000&&f.r.extra.get(shadow)==1000&&f.r.paidReceipts.isEmpty(),"reservation probe cancellation refunds once");
   });
  }
  h.runAtTickTime(54,()->{
   h.assertTrue(f.start(lab,first).equals("started"),"overflow fixture starts paid tier");
   pending[0]=lab.productionQueue.get(0).receiptResearch.receiptId();
   f.r.food=Integer.MAX_VALUE;
   f.r.addonData.getCompound(DreamWindProgression.KEY).putInt("cost",1);
  });
  h.runAtTickTime(56,()->{
   h.assertTrue(lab.productionQueue.isEmpty()&&f.r.paidReceipts.containsKey(pending[0]),"obsolete tier detaches but overflow retains original claim");
   h.assertTrue(f.r.food==Integer.MAX_VALUE&&f.r.wood==49900&&f.r.ore==49940&&f.r.extra.get(shadow)==990,"overflow refund has no partial credit");
   f.r.food=Integer.MAX_VALUE-80;
   ReceiptResearchService.retryDetachedRefunds(f.level.getServer());
   ReceiptResearchService.retryDetachedRefunds(f.level.getServer());
   h.assertTrue(f.r.food==Integer.MAX_VALUE&&f.r.wood==50000&&f.r.ore==50000&&f.r.extra.get(shadow)==1000&&f.r.paidReceipts.isEmpty(),"retained claim retries all original components exactly once");
   // Restore a valid affordable account for the independent temporary-prerequisite case.
   f.r.food=50000;f.r.addonData.getCompound(DreamWindProgression.KEY).putInt("cost",0);
   h.assertTrue(f.start(lab,first).equals("started"),"temporary-anchor case starts paid canonical tier");
   paused[0]=lab.productionQueue.get(0);paused[0].ticksLeft=100;
   pausedTicks[0]=paused[0].ticksLeft;pausedWork[0]=paused[0].workHundredths;
   // Controlled prerequisite absence only: do not terminally detach or replace the Anchor.
   BuildingServerEvents.getBuildings().remove(anchor);
  });
  h.runAtTickTime(64,()->{
   h.assertTrue(lab.productionQueue.size()==1&&lab.productionQueue.get(0)==paused[0]&&f.r.paidReceipts.size()==1,"missing Anchor retains exact paid queue binding");
   h.assertTrue(paused[0].ticksLeft==pausedTicks[0]&&paused[0].workHundredths==pausedWork[0],"missing Anchor pauses all remaining work");
   h.assertTrue(f.r.food==49920&&f.r.wood==49900&&f.r.ore==49940&&f.r.extra.get(shadow)==990,"pause neither refunds nor debits again");
   BuildingServerEvents.getBuildings().add(anchor);
  });
  h.runAtTickTime(68,()->{
   h.assertTrue(lab.productionQueue.size()==1&&lab.productionQueue.get(0)==paused[0]&&paused[0].ticksLeft<100&&f.r.paidReceipts.size()==1,"restored Anchor resumes the same paid work");
   ReceiptResearchService.detachAndRefund(lab,paused[0]);
   h.assertTrue(f.r.food==50000&&f.r.wood==50000&&f.r.ore==50000&&f.r.extra.get(shadow)==1000&&f.r.paidReceipts.isEmpty(),"resumed job still cancels at original paid quote");
   System.out.println("DREAM_WIND_PROGRESS_PASS maintenance_obsolete_refund_overflow_anchor_pause");h.succeed();
  });
 }
}
