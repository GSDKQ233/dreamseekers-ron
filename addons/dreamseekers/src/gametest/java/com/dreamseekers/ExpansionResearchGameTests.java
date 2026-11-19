package com.dreamseekers;

import com.dreamseekers.building.DreamLaboratoryPlacement;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.resonance.*;
import com.dreamseekers.unitprogression.AdvancedUnitResearch;
import com.dreamseekers.unitprogression.ExpansionResearch;
import com.dreamseekers.worldtree.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.research.economy.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Real server paid queue; consumer tests live in their respective scope suites. */
@GameTestHolder("api61_expansion_research") @PrefixGameTestTemplate(false)
public final class ExpansionResearchGameTests {
    private static final String BATCH="api61_expansion_research";
    private static final List<ResonanceNetworkGameTests.F> FIXTURES=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level){for(var f:FIXTURES)f.close();FIXTURES.clear();}
    private static ResonanceNetworkGameTests.F fixture(GameTestHelper h,String name,int index){
        var f=new ResonanceNetworkGameTests.F(h,"Expansion"+name,240+index);FIXTURES.add(f);return f;
    }
    private static DreamLaboratoryPlacement lab(ResonanceNetworkGameTests.F f){
        return (DreamLaboratoryPlacement)f.install(DreamseekersMod.RESONANCE_LABORATORY,f.origin.offset(0,0,40));
    }
    private static String start(ResonanceNetworkGameTests.F f,DreamLaboratoryPlacement p,ReceiptBackedResearchItem item){
        return ReceiptResearchService.start(f.peer.actor,p,item,ResearchPlacementIdentity.serverIdentity(p),p.getCaptureRevision(),f.account.accountEpoch,f.account.revision);
    }
    private static int[] balances(ResonanceNetworkGameTests.F f){return new int[]{f.account.food,f.account.wood,f.account.ore,f.shadow()};}
    @GameTest(templateNamespace="api61_expansion_research",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=100)
    public static void appendOnlyExpansionAndMissingUpgradeDenyWithoutDebit(GameTestHelper h){
        var f=fixture(h,"Gates",0);var p=lab(f);
        h.startSequence().thenWaitUntil(()->h.assertTrue(p.available(),"actual lab ready")).thenExecute(()->{
            var all=DreamseekersMod.RESONANCE_LABORATORY.productions.get();
            h.assertTrue(all.size()==33&&all.subList(23,28).equals(AdvancedUnitResearch.items())&&all.subList(28,32).equals(List.of(ExpansionResearch.AIR_SUPERIORITY,ExpansionResearch.BREACHING_STRUCTURE,ExpansionResearch.EMBER_INHIBITION,ExpansionResearch.AARONCOS_VORTEX))&&all.get(32)==ExpansionResearch.HAND_PAIRING,"old32 slots unchanged and explicit hand pairing appended at32");
            int[] before=balances(f);
            for(var item:ExpansionResearch.items()){
                h.assertTrue(!ExpansionResearch.unlocked(f.owner,item.id(),false),"old save remains locked");
                h.assertTrue(start(f,p,item).equals("forbidden"),"base tree cannot research expansion");
                h.assertTrue(Arrays.equals(before,balances(f))&&p.productionQueue.isEmpty(),"denial cannot debit or enqueue");
            }
            h.succeed();
        });
    }
    @GameTest(templateNamespace="api61_expansion_research",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=200)
    public static void fourRealPaymentsDuplicateDenialAndExactCancellation(GameTestHelper h){
        var f=fixture(h,"Quotes",1);var p=lab(f);var root=(DreamAnchorPlacement)f.root;
        h.startSequence().thenWaitUntil(()->h.assertTrue(p.available(),"actual lab ready")).thenExecute(()->{
            h.assertTrue(DreamWorldTreeService.start(f.peer.actor,root,root.tree().instance,WorldTreeModel.WIND_V4).equals("started"),"real paid upgrade");root.tree().remaining=1;
        }).thenWaitUntil(()->h.assertTrue(root.model()==WorldTreeModel.WIND_V4&&root.tree().phase==WorldTreeState.Phase.COMPLETE,"physical upgraded tree"))
        .thenExecute(()->{
            h.assertTrue(start(f,p,ExpansionResearch.AARONCOS_VORTEX).equals("forbidden"),"vortex also needs harmony");
            ResearchServerEvents.addResearch(f.owner,AdvancedUnitResearch.HAND_HARMONY_ID);
            int[][] costs={{100,0,125,25},{150,0,175,40},{125,0,125,35},{200,0,200,60}};
            for(int i=0;i<4;i++){
                var item=ExpansionResearch.items().get(i);int[] before=balances(f),cost=costs[i];
                h.assertTrue(start(f,p,item).equals("started"),"real receipt start "+item.id());
                int[] after=balances(f);for(int n=0;n<4;n++)h.assertTrue(after[n]==before[n]-cost[n],"exact resource debit "+item.id());
                h.assertTrue(!start(f,p,item).equals("started")&&Arrays.equals(after,balances(f)),"duplicate does not charge");
                var receipt=p.productionQueue.get(0).receiptResearch.receiptId();
                h.assertTrue(ReceiptResearchService.cancel(f.peer.actor,p,receipt,ResearchPlacementIdentity.serverIdentity(p),p.getCaptureRevision(),f.account.accountEpoch,f.account.revision).equals("cancelled_refunded"),"refund exact receipt");
                h.assertTrue(Arrays.equals(before,balances(f))&&p.productionQueue.isEmpty(),"all resources restored");
                ReceiptResearchService.cancel(f.peer.actor,p,receipt,ResearchPlacementIdentity.serverIdentity(p),p.getCaptureRevision(),f.account.accountEpoch,f.account.revision);
                h.assertTrue(Arrays.equals(before,balances(f)),"repeat cancellation cannot mint refund");
            }
            h.succeed();
        });
    }
    @GameTest(templateNamespace="api61_expansion_research",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1700)
    public static void naturalPaidAirSuperiorityCompletionCannotRepurchase(GameTestHelper h){
        var f=fixture(h,"Complete",2);var p=lab(f);var root=(DreamAnchorPlacement)f.root;final long[] started={0};
        h.startSequence().thenWaitUntil(()->h.assertTrue(p.available(),"actual lab ready")).thenExecute(()->{
            h.assertTrue(DreamWorldTreeService.start(f.peer.actor,root,root.tree().instance,WorldTreeModel.WIND_V4).equals("started"),"real paid upgrade");root.tree().remaining=1;
        }).thenWaitUntil(()->h.assertTrue(root.model()==WorldTreeModel.WIND_V4&&root.tree().phase==WorldTreeState.Phase.COMPLETE,"physical upgrade complete"))
        .thenExecute(()->{h.assertTrue(com.solegendary.reignofnether.building.production.ProductionWorkRates.percent(p,ExpansionResearch.AIR_SUPERIORITY)==125,"actual upgraded wind research speed");h.assertTrue(start(f,p,ExpansionResearch.AIR_SUPERIORITY).equals("started"),"real paid research");started[0]=f.level.getGameTime();})
        .thenExecuteAfter(700,()->h.assertTrue(!ExpansionResearch.unlocked(f.owner,ExpansionResearch.AIR_SUPERIORITY_ID,false),"no early unlock"))
        .thenWaitUntil(()->h.assertTrue(ExpansionResearch.unlocked(f.owner,ExpansionResearch.AIR_SUPERIORITY_ID,false),"natural queue completion publishes authority"))
        .thenExecute(()->{int[] before=balances(f);h.assertTrue(f.level.getGameTime()-started[0]>=719&&p.productionQueue.isEmpty(),"900 base work ticks at actual125percent wind rate:720 elapsed ticks");
            h.assertTrue(!start(f,p,ExpansionResearch.AIR_SUPERIORITY).equals("started")&&Arrays.equals(before,balances(f)),"finished item cannot repurchase");h.succeed();});
    }
    @GameTest(templateNamespace="api61_expansion_research",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1800)
    public static void handPairingPaidGateRefundAndNaturalCompletion(GameTestHelper h){
        var f=fixture(h,"HandPairing",3);var p=lab(f);var root=(DreamAnchorPlacement)f.root;long[] started={0};
        h.startSequence().thenWaitUntil(()->h.assertTrue(p.available(),"actual lab ready"))
        .thenExecute(()->{int[] before=balances(f);h.assertTrue(start(f,p,ExpansionResearch.HAND_PAIRING).equals("forbidden")&&Arrays.equals(before,balances(f)),"base tree denies hand pairing without debit");
            h.assertTrue(DreamWorldTreeService.start(f.peer.actor,root,root.tree().instance,WorldTreeModel.WIND_V4).equals("started"),"real paid wind prerequisite");root.tree().remaining=1;
        }).thenWaitUntil(()->h.assertTrue(root.model()==WorldTreeModel.WIND_V4&&root.tree().phase==WorldTreeState.Phase.COMPLETE,"physical upgraded tree complete"))
        .thenExecute(()->{int[] before=balances(f);h.assertTrue(start(f,p,ExpansionResearch.HAND_PAIRING).equals("forbidden")&&Arrays.equals(before,balances(f))&&p.productionQueue.isEmpty(),"upgraded tree alone cannot bypass harmony prerequisite");
            ResearchServerEvents.addResearch(f.owner,AdvancedUnitResearch.HAND_HARMONY_ID); // Existing-tech fixture only.
            h.assertTrue(start(f,p,ExpansionResearch.HAND_PAIRING).equals("started"),"actual paid250F200O75S start");int[] after=balances(f);
            h.assertTrue(after[0]==before[0]-250&&after[1]==before[1]&&after[2]==before[2]-200&&after[3]==before[3]-75,"exact research quote debit; no hidden wood");
            h.assertTrue(!start(f,p,ExpansionResearch.HAND_PAIRING).equals("started")&&Arrays.equals(after,balances(f)),"pending duplicate neither enqueues nor charges");
            var receipt=p.productionQueue.get(0).receiptResearch.receiptId();
            h.assertTrue(ReceiptResearchService.cancel(f.peer.actor,p,receipt,ResearchPlacementIdentity.serverIdentity(p),p.getCaptureRevision(),f.account.accountEpoch,f.account.revision).equals("cancelled_refunded"),"real pending receipt cancellation refunds");
            ReceiptResearchService.cancel(f.peer.actor,p,receipt,ResearchPlacementIdentity.serverIdentity(p),p.getCaptureRevision(),f.account.accountEpoch,f.account.revision);
            h.assertTrue(Arrays.equals(before,balances(f))&&p.productionQueue.isEmpty()&&!ExpansionResearch.unlocked(f.owner,ExpansionResearch.HAND_PAIRING_ID,false),"once-only full refund never grants hand pairing");
            h.assertTrue(start(f,p,ExpansionResearch.HAND_PAIRING).equals("started")&&!p.productionQueue.get(0).receiptResearch.receiptId().equals(receipt),"explicit repurchase has distinct receipt");
            h.assertTrue(p.productionQueue.get(0).ticksLeft==1500&&com.solegendary.reignofnether.building.production.ProductionWorkRates.percent(p,ExpansionResearch.HAND_PAIRING)==125,"75second base work at existing wind125percent");started[0]=f.level.getGameTime();
        }).thenExecuteAfter(1180,()->h.assertTrue(!ExpansionResearch.unlocked(f.owner,ExpansionResearch.HAND_PAIRING_ID,false),"no early unlock"))
        .thenWaitUntil(()->h.assertTrue(ExpansionResearch.unlocked(f.owner,ExpansionResearch.HAND_PAIRING_ID,false),"real natural paid completion grants hand pairing"))
        .thenExecute(()->{int[] before=balances(f);h.assertTrue(f.level.getGameTime()-started[0]>=1199&&p.productionQueue.isEmpty()&&f.account.paidReceipts.isEmpty(),"natural1200elapsed and consumed receipt");
            h.assertTrue(!start(f,p,ExpansionResearch.HAND_PAIRING).equals("started")&&Arrays.equals(before,balances(f)),"completed one-time technology cannot charge again");h.succeed();});
    }

}
