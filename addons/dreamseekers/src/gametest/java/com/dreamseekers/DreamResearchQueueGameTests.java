package com.dreamseekers;

import com.dreamseekers.economy.*;
import com.dreamseekers.worldtree.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.*;
import com.solegendary.reignofnether.faction.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.research.ResearchSaveData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import com.solegendary.reignofnether.research.economy.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Direct authenticated server-service calls exercise the real addon registry, ledger and queue.
 * Packet/session/codec coverage belongs to the B2 packet fixtures, not to this FakePlayer test. */
@GameTestHolder("dreamseekers_research_economy")
@PrefixGameTestTemplate(false)
public final class DreamResearchQueueGameTests {
 private static final String OWNER="DreamResearchQueue_Dream",OTHER="DreamResearchQueue_Other",LOSS="DreamResearchQueue_Loss";
 private static Resources account(String owner,Faction faction){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,faction));var r=new Resources(owner,5000,5000,5000);r.extra.put(DreamPassiveEconomy.SHADOW,500);ResourcesServerEvents.resourcesList.add(r);return r;}
 private static ServerPlayer actor(ServerLevel level,String owner){return FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes(owner.getBytes(StandardCharsets.UTF_8)),owner));}
 private static ProductionPlacement install(ServerLevel level,Building building,String owner,BlockPos origin){
  for(int x=-2;x<25;x++)for(int z=-2;z<25;z++){level.setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<20;y++)level.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  for(var b:BuildingUtils.getAbsoluteBlockData(building.getRelativeBlockData(level),level,origin,Rotation.NONE))level.setBlock(b.getBlockPos(),b.getBlockState(),2);
  var p=(ProductionPlacement)BuildingUtils.getNewBuildingPlacement(building,level,origin,Rotation.NONE,owner,false);BuildingServerEvents.getBuildings().add(p);return p;
 }
 private static String start(ServerPlayer actor,ProductionPlacement p,ReceiptBackedResearchItem item,Resources r){return ReceiptResearchService.start(actor,p,item,ResearchPlacementIdentity.serverIdentity(p),p.getCaptureRevision(),r.accountEpoch,r.revision);}
 private static String cancel(ServerPlayer actor,ProductionPlacement p,UUID receipt,Resources r){return ReceiptResearchService.cancel(actor,p,receipt,ResearchPlacementIdentity.serverIdentity(p),p.getCaptureRevision(),r.accountEpoch,r.revision);}
 private static void balances(GameTestHelper h,Resources r,int f,int w,int o,int s,String why){h.assertTrue(r.food==f&&r.wood==w&&r.ore==o&&r.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0)==s,why+": "+r.food+","+r.wood+","+r.ore+","+r.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0));}
 @GameTest(templateNamespace="dreamseekers_research_economy",template="ore_mine_test_arena",batch="dream_research_queue",timeoutTicks=1320)
 public static void registeredAddonPaidQueuesPrerequisitesAndRefunds(GameTestHelper h){
  var level=h.getLevel();var a=account(OWNER,DreamseekersMod.DREAMSEEKERS);var b=account(OTHER,Factions.VILLAGERS);var loss=account(LOSS,DreamseekersMod.DREAMSEEKERS);
  var owned=actor(level,OWNER);var other=actor(level,OTHER);var losing=actor(level,LOSS);
  BlockPos origin=new BlockPos(16000,100,16000);List<Long> forced=new ArrayList<>();
  for(int x=999;x<=1010;x++)for(int z=999;z<=1002;z++){long key=net.minecraft.world.level.ChunkPos.asLong(x,z);if(!level.getForcedChunks().contains(key)){level.setChunkForced(x,z,true);forced.add(key);}}
  var tree=(DreamAnchorPlacement)install(level,DreamseekersMod.DREAM_ANCHOR,OWNER,origin);
  var foreign=install(level,DreamseekersMod.DREAM_ANCHOR,OTHER,origin.offset(40,0,0));
  var wrong=install(level,Buildings.BLACKSMITH,OWNER,origin.offset(80,0,0));
  var lossTree=install(level,DreamseekersMod.DREAM_ANCHOR,LOSS,origin.offset(120,0,0));
  Runnable cleanup=()->{for(String name:List.of(OWNER,OTHER,LOSS))ResearchServerEvents.removeAllResearchFor(name);
   PlayerServerEvents.rtsPlayers.removeIf(p->List.of(OWNER,OTHER,LOSS).contains(p.name));ResourcesServerEvents.resourcesList.removeIf(r->List.of(OWNER,OTHER,LOSS).contains(r.ownerName));
   BuildingServerEvents.getBuildings().removeIf(p->List.of(OWNER,OTHER,LOSS).contains(p.ownerName));for(long c:forced)level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),false);};
  java.util.function.BiConsumer<Integer,Runnable> at=(tick,action)->h.runAtTickTime(tick,()->{try{action.run();}catch(RuntimeException|Error failure){cleanup.run();throw failure;}});
  long[] paidRevision={0},lossRevision={0};float[] lossTicks={0};int[] score={0};
  at.accept(20,()->{
   h.assertTrue(tree.isBuilt&&foreign.isBuilt&&wrong.isBuilt&&lossTree.isBuilt,"all real model placements complete");
   h.assertTrue(ReignOfNetherRegistries.PRODUCTION_ITEM.get(DreamEconomyResearch.CONVERSION_ID)==DreamEconomyResearch.CONVERSION&&ReignOfNetherRegistries.PRODUCTION_ITEM.get(DreamEconomyResearch.ASCENDANCY_ID)==DreamEconomyResearch.ASCENDANCY,"actual addon registry identities, not substitute testitems");
   h.assertTrue(DreamEconomyResearch.CONVERSION.getCost(false,OWNER).ticks==1200&&DreamEconomyResearch.ASCENDANCY.getCost(false,OWNER).ticks==1800,"configured native research durations");
   long revision=a.revision;
   h.assertTrue(start(owned,tree,DreamEconomyResearch.ASCENDANCY,a).equals("forbidden")&&a.revision==revision&&a.paidReceipts.isEmpty(),"base tree cannot buy advanced research or debit");
   h.assertTrue(start(owned,wrong,DreamEconomyResearch.CONVERSION,a).equals("forbidden")&&a.revision==revision,"wrong registered host rejected");
   long otherRevision=b.revision;
   h.assertTrue(start(other,foreign,DreamEconomyResearch.CONVERSION,b).equals("forbidden")&&b.revision==otherRevision&&b.paidReceipts.isEmpty(),"valid nonDream owner cannot buy Dream research");
   h.assertTrue(start(other,tree,DreamEconomyResearch.CONVERSION,a).equals("forbidden")&&a.revision==revision,"different actor cannot buy owner's research");
   h.assertTrue(start(owned,tree,DreamEconomyResearch.CONVERSION,a).equals("started"),"real registered conversion queues after actual payment");
   balances(h,a,5000,4900,4850,470,"actual conversion original cost");h.assertTrue(tree.productionQueue.size()==1&&a.paidReceipts.size()==1,"one queue andreceipt");
   UUID receipt=tree.productionQueue.get(0).receiptResearch.receiptId();int original=DreamEconomyResearch.CONVERSION.defaultCost.wood;
   try{DreamEconomyResearch.CONVERSION.defaultCost.wood=777;h.assertTrue(cancel(owned,tree,receipt,a).equals("cancelled_refunded"),"real cancel successful despite changed quote");}finally{DreamEconomyResearch.CONVERSION.defaultCost.wood=original;}
   balances(h,a,5000,5000,5000,500,"refund exact original FWO andshadow, not newquote");h.assertTrue(tree.productionQueue.isEmpty()&&a.paidReceipts.isEmpty(),"refund consumesreceipt andqueue once");
   revision=a.revision;h.assertTrue(cancel(owned,tree,receipt,a).equals("unknown_receipt")&&a.revision==revision,"repeat cancel no mint");
   h.assertTrue(start(losing,lossTree,DreamEconomyResearch.CONVERSION,loss).equals("started"),"second real Dream ownerpaidqueue");
   lossTicks[0]=lossTree.productionQueue.get(0).ticksLeft;lossRevision[0]=loss.revision;
   PlayerServerEvents.getRTSPlayer(LOSS).faction=Factions.VILLAGERS;
  });
  at.accept(24,()->{
   h.assertTrue(lossTree.productionQueue.size()==1&&loss.paidReceipts.size()==1&&lossTree.productionQueue.get(0).ticksLeft==lossTicks[0]&&loss.revision==lossRevision[0]&&!ResearchServerEvents.playerHasResearch(LOSS,DreamEconomyResearch.CONVERSION_ID),"temporary Dreamprerequisite loss pauses timer withoutgrant refund ornewpayment");
   balances(h,loss,5000,4900,4850,470,"paused research retains originalpaidclaim");
   UUID pausedReceipt=lossTree.productionQueue.get(0).receiptResearch.receiptId();
   h.assertTrue(cancel(losing,lossTree,pausedReceipt,loss).equals("cancelled_refunded"),"now nonDream originalpayer can explicitlycancel pausedresearch");
   h.assertTrue(lossTree.productionQueue.isEmpty()&&loss.paidReceipts.isEmpty(),"explicitcancel consumes claim andqueue");balances(h,loss,5000,5000,5000,500,"originalpayer refund is not factiongated");
   int originalS=a.extra.get(DreamPassiveEconomy.SHADOW);a.extra.put(DreamPassiveEconomy.SHADOW,29);long revision=a.revision;
   h.assertTrue(start(owned,tree,DreamEconomyResearch.CONVERSION,a).equals("insufficient_resources")&&a.revision==revision&&tree.productionQueue.isEmpty(),"insufficientshadow does not partiallydebit FWO");balances(h,a,5000,5000,5000,29,"failedpayment leavesallbalances");a.extra.put(DreamPassiveEconomy.SHADOW,originalS);
   score[0]=PlayerServerEvents.getRTSPlayer(OWNER).scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED);
   h.assertTrue(start(owned,tree,DreamEconomyResearch.CONVERSION,a).equals("started"),"realqueue forfull1200tickduration");paidRevision[0]=a.revision;
   h.assertTrue(start(owned,tree,DreamEconomyResearch.CONVERSION,a).equals("duplicate")&&a.revision==paidRevision[0],"same research cannot pay twice");
  });
  at.accept(1210,()->h.assertTrue(!ResearchServerEvents.playerHasResearch(OWNER,DreamEconomyResearch.CONVERSION_ID)&&tree.productionQueue.size()==1,"full native1200tick research cannot complete early; no ticksLeft shortening"));
  at.accept(1235,()->{
   h.assertTrue(tree.productionQueue.isEmpty()&&a.paidReceipts.isEmpty()&&ResearchServerEvents.playerHasResearch(OWNER,DreamEconomyResearch.CONVERSION_ID),"actual native duration completed and original receiptconsumed");balances(h,a,5000,4900,4850,470,"completion consumespaidcost withoutseconddebit orrefund");
   h.assertTrue(PlayerServerEvents.getRTSPlayer(OWNER).scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED)==score[0],"research doesnot countunit production");
   h.assertTrue(DreamWorldTreeService.start(owned,tree,tree.tree().instance).equals("started"),"realworldtree payment andstate transition");
   balances(h,a,4400,4100,4250,320,"treeupgrade originalcost debited");
   tree.tree().remaining=1; // Explicit testonly acceleration AFTER actual upgradepayment; E separately tests full2400ticks.
  });
  at.accept(1240,()->{
   h.assertTrue(tree.ascended()&&tree.getBuilding().getUpgradeLevel(tree)==1,"real morphcommit enablesadvanced prerequisite");
   h.assertTrue(start(owned,tree,DreamEconomyResearch.ASCENDANCY,a).equals("started"),"advancedregisteredresearch accepted onlyafterrealupgrade");
   balances(h,a,4100,4100,3950,220,"advanced exact300F300O100S payment");UUID receipt=tree.productionQueue.get(0).receiptResearch.receiptId();
   h.assertTrue(cancel(owned,tree,receipt,a).equals("cancelled_refunded"),"advanced actualcancel");balances(h,a,4400,4100,4250,320,"advanced fulloriginalrefund");
   h.assertTrue(start(owned,tree,DreamEconomyResearch.ASCENDANCY,a).equals("started"),"advancedstartagain");tree.productionQueue.get(0).ticksLeft=3; // This branch testsnativecompletion, notfull1800tickduration.
  });
  at.accept(1250,()->{
   h.assertTrue(tree.productionQueue.isEmpty()&&a.paidReceipts.isEmpty()&&ResearchServerEvents.playerHasResearch(OWNER,DreamEconomyResearch.ASCENDANCY_ID),"advanced native completion grant andreceiptconsume");balances(h,a,4100,4100,3950,220,"advanced completion doesnot debit again");
   long revision=a.revision;h.assertTrue(start(owned,tree,DreamEconomyResearch.ASCENDANCY,a).equals("duplicate")&&a.revision==revision,"completed advancedresearch cannotrepurchase");
   System.out.println("DREAM_ADDON_RESEARCH_QUEUE_PASS actual_registry strict_faction host prerequisite payment original_refund full_conversion_duration real_tree_morph advanced_completion");cleanup.run();h.succeed();
  });
 }

 private static ReceiptBackedResearchItem rankItem(DreamEconomyResearch.Line line,int rank) {
  return (ReceiptBackedResearchItem)ReignOfNetherRegistries.PRODUCTION_ITEM.get(line.id(rank));
 }

 @GameTest(templateNamespace="dreamseekers_research_economy",template="ore_mine_test_arena",batch="dream_capital_ranks",timeoutTicks=80)
 public static void twoCapitalLinesPayAllRanksOnceAndRoundtrip(GameTestHelper h) {
  String owner="CapitalRankOwner";var level=h.getLevel();var r=account(owner,DreamseekersMod.DREAMSEEKERS);r.extra.put(DreamPassiveEconomy.SHADOW,1000);
  var payer=actor(level,owner);var conversion=DreamEconomyResearch.Line.CONVERSION;var ascendancy=DreamEconomyResearch.Line.ASCENDANCY;
  var origin=new BlockPos(22400,100,22400);var forced=new ArrayList<Long>();
  for(int x=(origin.getX()-16)>>4;x<=(origin.getX()+72)>>4;x++)
   for(int z=(origin.getZ()-16)>>4;z<=(origin.getZ()+32)>>4;z++){
    long chunk=net.minecraft.world.level.ChunkPos.asLong(x,z);
    if(!level.getForcedChunks().contains(chunk)){level.setChunkForced(x,z,true);forced.add(chunk);}
    level.getChunk(x,z);
   }
  var base=install(level,DreamseekersMod.DREAM_ANCHOR,owner,origin);
  var shadow=(DreamAnchorPlacement)install(level,DreamseekersMod.DREAM_ANCHOR,owner,origin.offset(40,0,0));
  Runnable cleanup=()->{ResearchServerEvents.removeAllResearchFor(owner);
   PlayerServerEvents.rtsPlayers.removeIf(p->owner.equals(p.name));ResourcesServerEvents.resourcesList.remove(r);
   for(var p:List.of(base,shadow)){p.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(p);}
   for(long c:forced)level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(c),net.minecraft.world.level.ChunkPos.getZ(c),false);};
  java.util.function.BiConsumer<Integer,Runnable> at=(tick,action)->h.runAtTickTime(tick,()->{try{action.run();}catch(RuntimeException|Error failure){cleanup.run();throw failure;}});
  at.accept(20,()->{
   h.assertTrue(base.isBuilt&&shadow.isBuilt,"both ordinary capital constructions complete");
   // A native pre-update record uses the two original keys. Duplicate historic records do not stack.
   var oldRows=new ListTag();
   for(String key:List.of("dreamseekers:shadow_conversion_efficiency","dreamseekers:shadow_conversion_efficiency","dreamseekers:world_tree_ascendancy")){
    var row=new CompoundTag();row.putString("ownerName",owner);row.putString("researchKey",key);oldRows.add(row);
   }
   var oldTag=new CompoundTag();oldTag.put("researchItems",oldRows);var old=ResearchSaveData.load(oldTag);
   for(var line:DreamEconomyResearch.Line.values())h.assertTrue(line.level(id->old.researchItems.stream().anyMatch(x->x.getFirst().equals(owner)&&x.getSecond().equals(id)))==1,
       "legacy native research rows grant exactly one completion per line");
   long rev=r.revision;
   h.assertTrue(!start(payer,base,rankItem(conversion,2),r).equals("started")&&!start(payer,base,rankItem(ascendancy,1),r).equals("started")&&r.revision==rev,
       "no free skip or Ascendancy at a base capital");
   h.assertTrue(DreamWorldTreeService.start(payer,shadow,shadow.tree().instance).equals("started"),"pay the actual shadow branch first");
   shadow.tree().remaining=1;
  });
  var receipts=new HashSet<UUID>();var completed=new ActiveProduction[2];
  for(int n=1;n<=3;n++) {
   final int rank=n;
   at.accept(24+4*n,()->{
    h.assertTrue(shadow.ascended(),"real shadow morph completed");
    var c=rankItem(conversion,rank);var a=rankItem(ascendancy,rank);
    h.assertTrue(c.getCost(false,owner).ticks==1200*rank&&a.getCost(false,owner).ticks==1800*rank,"per-rank durations");
    h.assertTrue(start(payer,base,c,r).equals("started"),"next conversion rank starts at base: "+rank);
    long rev=r.revision;
    h.assertTrue(start(payer,shadow,c,r).equals("duplicate")&&r.revision==rev,"second capital cannot reserve/pay same rank");
    if(rank==2){
     var id=base.productionQueue.get(0).receiptResearch.receiptId();
     h.assertTrue(cancel(payer,base,id,r).equals("cancelled_refunded")&&cancel(payer,base,id,r).equals("unknown_receipt"),"refund rank-two quote once");
     h.assertTrue(start(payer,base,c,r).equals("started"),"cancel releases reservation and allows new paid receipt");
    }
    h.assertTrue(start(payer,shadow,a,r).equals("started"),"other family stays independent while conversion is queued");
    completed[0]=base.productionQueue.get(0);completed[1]=shadow.productionQueue.get(0);
    for(var job:completed){h.assertTrue(receipts.add(job.receiptResearch.receiptId()),"each rank carries a new receipt");job.ticksLeft=1;}
   });
   at.accept(26+4*n,()->{
    h.assertTrue(conversion.serverLevel(owner)==rank&&ascendancy.serverLevel(owner)==rank&&r.paidReceipts.isEmpty()
        &&base.productionQueue.isEmpty()&&shadow.productionQueue.isEmpty(),"both native queue ticks commit exact next rank");
    long rev=r.revision;
    h.assertTrue(!ReceiptResearchService.complete(base,completed[0])&&!ReceiptResearchService.complete(shadow,completed[1])&&r.revision==rev,
        "replaying consumed completions grants/debits nothing");
   });
  }
  at.accept(40,()->{
   h.assertTrue(receipts.size()==6&&r.paidReceipts.isEmpty(),"six unique successful rank receipts");
   balances(h,r,2600,3600,1700,70,"original branch plus exactly six rank prices; cancellation produces no surplus");
   long rev=r.revision;
   h.assertTrue(start(payer,base,rankItem(conversion,3),r).equals("duplicate")&&start(payer,shadow,rankItem(ascendancy,3),r).equals("duplicate")
       &&r.revision==rev,"capped lines cannot debit again");
   var restored=ResearchSaveData.load(ResearchSaveData.getInstance(level).save(new CompoundTag()));
   var rows=restored.researchItems.stream().filter(x->x.getFirst().equals(owner)).toList();
   h.assertTrue(rows.size()==6,"native saved owner has exactly six rank identities");
   for(var line:DreamEconomyResearch.Line.values())h.assertTrue(line.level(id->rows.stream().anyMatch(x->x.getSecond().equals(id)))==3,"native NBT roundtrip preserves each independent cap");
   System.out.println("DREAM_CAPITAL_RANKS_PASS paid_two_lines cross_capital_cancel_replay exact_legacy_save capped");cleanup.run();h.succeed();
  });
 }
}
