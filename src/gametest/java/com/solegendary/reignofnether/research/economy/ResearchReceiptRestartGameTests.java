package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.resources.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;

/** Ordinary stop/reload: successful saved refund and retained-overflow receipt use separate accounts. */
@GameTestHolder("reignofnether_research_restart")
@PrefixGameTestTemplate(false)
public final class ResearchReceiptRestartGameTests {
    static final String A="ResearchRestart_Projected",B="ResearchRestart_Overflow";
    static final BlockPos ORIGIN=new BlockPos(12288,90,12288);
    static final File MANIFEST=new File("research-receipt-restart-expectations.nbt");
    static Resources account(String n){return ResourcesServerEvents.resourcesList.stream().filter(r->r.ownerName.equals(n)).findFirst().orElseThrow();}
    @GameTest(templateNamespace="reignofnether_research_restart",template="ore_mine_test_arena",batch="research_receipt_restart",timeoutTicks=180)
    public static void normalStopProjectionAndOrphanReceiptNeverRefundTwice(GameTestHelper h)throws Exception{
        ResearchEconomyFixtures.register();switch(System.getProperty("resource.research.restart.phase","")){
            case "prepare"->prepare(h);case "verify"->verify(h);case "verify_settled"->verifySettled(h);default->throw new IllegalArgumentException("researchReceiptRestartPhase prepare|verify|verify_settled");
        }
    }
    static void prepare(GameTestHelper h)throws Exception{
        h.assertTrue(!MANIFEST.exists()&&PlayerServerEvents.getRTSPlayer(A)==null&&PlayerServerEvents.getRTSPlayer(B)==null,"fresh isolated research restart world");
        CompoundTag expected=new CompoundTag();expected.putLong("prepareJvm",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());List<Long> chunks=new ArrayList<>();for(int x=768;x<=777;x++)for(int z=768;z<=770;z++){long key=ChunkPos.asLong(x,z);h.assertTrue(!h.getLevel().getForcedChunks().contains(key),"only new fixture chunks forced");h.getLevel().setChunkForced(x,z,true);chunks.add(key);}expected.putLongArray("chunks",chunks);
        Resources a=ResearchEconomyFixtures.account(A,1000),b=ResearchEconomyFixtures.account(B,1000);
        ProductionPlacement p=ResearchEconomyFixtures.install(h.getLevel(),A,ORIGIN),q=ResearchEconomyFixtures.install(h.getLevel(),B,ORIGIN.offset(40,0,0));ResearchEconomyFixtures.install(h.getLevel(),B,ORIGIN.offset(80,0,0));
        var pa=new ResearchEconomyFixtures.Peer(h.getLevel(),A);var pb=new ResearchEconomyFixtures.Peer(h.getLevel(),B);
        h.runAtTickTime(30,()->{
            try{
                h.assertTrue(p.isBuilt&&q.isBuilt,"real restart hosts completed");
                pa.send(ResearchEconomyFixtures.query(p));var ta=ResearchEconomyFixtures.response(pa.drain(),p,a);pa.send(ResearchEconomyFixtures.request(p,ta,1,ResearchEconomyServerboundPacket.Action.START,ResearchEconomyFixtures.MIXED_ID,ResearchEconomyServerboundPacket.ZERO));h.assertTrue(p.productionQueue.size()==1&&a.paidReceipts.size()==1,"real dedicated packet bought mixed research");
                h.assertTrue(p.startProductionItem(ProductionItems.RESEARCH_MILITIA_BOWS),"original legacy job coexists after new receipt job");h.assertTrue(p.productionQueue.size()==2,"mixed legacy/new queue exists before save");
                pb.send(ResearchEconomyFixtures.query(q));var tb=ResearchEconomyFixtures.response(pb.drain(),q,b);pb.send(ResearchEconomyFixtures.request(q,tb,1,ResearchEconomyServerboundPacket.Action.START,ResearchEconomyFixtures.MIXED_ID,ResearchEconomyServerboundPacket.ZERO));UUID overflow=q.productionQueue.get(0).receiptResearch.receiptId();
                h.assertTrue(ResourceTransaction.creditIncome(h.getLevel().getServer(),b,b.accountEpoch,b.revision,new ResourceTransaction.Cost(Integer.MAX_VALUE,0,0)).applied(),"real income saturates food while refund remains unpaid");q.destroy(h.getLevel());BuildingServerEvents.getBuildings().remove(q);h.assertTrue(q.productionQueue.isEmpty()&&b.paidReceipts.containsKey(overflow),"destroy detached genuine overflow claim");
                CompoundTag liveA=ResearchEconomyFixtures.snapshot(a),liveB=ResearchEconomyFixtures.snapshot(b);ResourcesServerEvents.saveResources(h.getLevel());var data=ResourcesSaveData.getInstance(h.getLevel());Resources savedA=data.resources.stream().filter(r->r.ownerName.equals(A)).findFirst().orElseThrow(),savedB=data.resources.stream().filter(r->r.ownerName.equals(B)).findFirst().orElseThrow();
                h.assertTrue(savedA.food==1000&&savedA.wood==1000&&savedA.ore==1000&&savedA.extra.get(ResearchEconomyFixtures.EXTRA)==1000&&savedA.paidReceipts.isEmpty(),"projection fully refunds mixed receipt and legacy job once");h.assertTrue(ResearchEconomyFixtures.snapshot(savedB).equals(liveB)&&savedB.paidReceipts.containsKey(overflow),"overflow projection preserves complete copied claim without partial refund");h.assertTrue(ResearchEconomyFixtures.snapshot(a).equals(liveA)&&ResearchEconomyFixtures.snapshot(b).equals(liveB)&&p.productionQueue.size()==2,"ordinary projection leaves both live accounts and queue unchanged");
                expected.put("projectedA",ResearchEconomyFixtures.snapshot(savedA));expected.put("retainedB",ResearchEconomyFixtures.snapshot(savedB));expected.putUUID("overflowReceipt",overflow);expected.putUUID("projectedEpoch",a.accountEpoch);expected.putUUID("retainedEpoch",b.accountEpoch);
                PlayerServerEvents.saveRTSPlayers();BuildingServerEvents.saveBuildings(h.getLevel());NbtIo.writeCompressed(expected,MANIFEST);pa.close();pb.close();System.out.println("RECEIPT_RESEARCH_RESTART_PREPARED projected_refund_and_retained_overflow");h.succeed();
            }catch(Exception|Error e){pa.close();pb.close();throw new RuntimeException(e);}
        });
    }
    static void loadChunks(GameTestHelper h,CompoundTag expected){for(long key:expected.getLongArray("chunks")){h.assertTrue(h.getLevel().getForcedChunks().contains(key),"normal world restoration retains fixture chunks");ChunkPos c=new ChunkPos(key);h.getLevel().getChunk(c.x,c.z);}}
    static void verify(GameTestHelper h)throws Exception{
        CompoundTag expected=NbtIo.readCompressed(MANIFEST);h.assertTrue(expected.getLong("prepareJvm")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"verify is independent JVM");loadChunks(h,expected);
        h.assertTrue(PlayerServerEvents.getRTSPlayer(A)!=null&&PlayerServerEvents.getRTSPlayer(B)!=null,"ordinary startup restored both original payers");Resources a=account(A),b=account(B);
        h.assertTrue(ResearchEconomyFixtures.snapshot(a).equals(expected.getCompound("projectedA")),"normal loader restores already-projected full refund exactly once");h.assertTrue(ResearchEconomyFixtures.snapshot(b).equals(expected.getCompound("retainedB")),"normal loader restores original overflow receipt and all balances");
        for(var placement:BuildingServerEvents.getBuildings())if(placement.ownerName.equals(A)||placement.ownerName.equals(B))h.assertTrue(!(placement instanceof ProductionPlacement p)||p.productionQueue.isEmpty(),"native building loader does not pretend old queue persisted");
        h.runAtTickTime(25,()->{
            h.assertTrue(ResearchEconomyFixtures.snapshot(a).equals(expected.getCompound("projectedA"))&&ResearchEconomyFixtures.snapshot(b).equals(expected.getCompound("retainedB")),"periodic retry cannot double saved refund or partially settle overflow");
            h.assertTrue(ResourceTransaction.exchange(h.getLevel().getServer(),b,b.accountEpoch,b.revision,new ResourceTransaction.Cost(11,0,0),ResourceTransaction.Cost.ZERO).applied(),"genuine transaction frees exactly refund food space");
        });
        h.runAtTickTime(55,()->{
            UUID receipt=expected.getUUID("overflowReceipt");h.assertTrue(!b.paidReceipts.containsKey(receipt)&&b.food==Integer.MAX_VALUE&&b.wood==1000&&b.ore==1000&&b.extra.get(ResearchEconomyFixtures.EXTRA)==1000,"normal periodic orphan retry refunds full mixed receipt exactly once after space recovery");h.assertTrue(a.accountEpoch.equals(expected.getUUID("projectedEpoch"))&&b.accountEpoch.equals(expected.getUUID("retainedEpoch")),"refund/reload preserve original payer epochs");
            CompoundTag settled=ResearchEconomyFixtures.snapshot(b);ReceiptResearchService.retryDetachedRefunds(h.getLevel().getServer());h.assertTrue(ResearchEconomyFixtures.snapshot(b).equals(settled),"repeated retry does not replay settled receipt");
            expected.put("settledB",settled);expected.putLong("verifyJvm",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
            try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception e){throw new RuntimeException(e);}System.out.println("RECEIPT_RESEARCH_PROCESS_RESTART_PASS projected_once retained_claim_refunded_once");h.succeed();
        });
    }
    static void verifySettled(GameTestHelper h)throws Exception{
        CompoundTag expected=NbtIo.readCompressed(MANIFEST);h.assertTrue(expected.contains("settledB")&&expected.getLong("verifyJvm")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"optional third JVM follows completed settlement");loadChunks(h,expected);
        h.runAtTickTime(25,()->{h.assertTrue(ResearchEconomyFixtures.snapshot(account(A)).equals(expected.getCompound("projectedA"))&&ResearchEconomyFixtures.snapshot(account(B)).equals(expected.getCompound("settledB")),"second ordinary reload and retries cannot recreate refunded money or receipts");PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(A)||p.name.equals(B));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(A)||r.ownerName.equals(B));BuildingServerEvents.getBuildings().removeIf(p->p.ownerName.equals(A)||p.ownerName.equals(B));for(long key:expected.getLongArray("chunks")){ChunkPos c=new ChunkPos(key);h.getLevel().setChunkForced(c.x,c.z,false);}System.out.println("RECEIPT_RESEARCH_SECOND_RELOAD_PASS no_receipt_replay");h.succeed();});
    }
}
