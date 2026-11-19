package com.dreamseekers;

import com.dreamseekers.building.DreamLaboratoryPlacement;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.resonance.*;
import com.dreamseekers.unitprogression.AdvancedUnitResearch;
import com.dreamseekers.worldtree.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.research.economy.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Real server paid queue; consumer tests live in their respective scope suites. */
@GameTestHolder("dreamseekers_advanced_research") @PrefixGameTestTemplate(false)
public final class AdvancedUnitResearchGameTests {
    private static final String BATCH="advanced_research";
    private static final List<ResonanceNetworkGameTests.F> FIXTURES=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level){for(var f:FIXTURES)f.close();FIXTURES.clear();}
    private static ResonanceNetworkGameTests.F fixture(GameTestHelper h,String name,int index){
        var f=new ResonanceNetworkGameTests.F(h,"Advanced"+name,170+index);FIXTURES.add(f);return f;
    }
    private static DreamLaboratoryPlacement lab(ResonanceNetworkGameTests.F f){
        return (DreamLaboratoryPlacement)f.install(DreamseekersMod.RESONANCE_LABORATORY,f.origin.offset(0,0,40));
    }
    private static String start(ResonanceNetworkGameTests.F f,DreamLaboratoryPlacement p,ReceiptBackedResearchItem item){
        return ReceiptResearchService.start(f.peer.actor,p,item,ResearchPlacementIdentity.serverIdentity(p),p.getCaptureRevision(),f.account.accountEpoch,f.account.revision);
    }
    private static int[] balances(ResonanceNetworkGameTests.F f){return new int[]{f.account.food,f.account.wood,f.account.ore,f.shadow()};}
    @GameTest(templateNamespace="dreamseekers_advanced_research",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=100)
    public static void appendOnlyRegistrationFreshAccountAndStrictPrerequisites(GameTestHelper h){
        var f=fixture(h,"Gates",0);var p=lab(f);
        h.startSequence().thenWaitUntil(()->h.assertTrue(p.available(),"actual lab ready")).thenExecute(()->{
            var all=DreamseekersMod.RESONANCE_LABORATORY.productions.get();
            h.assertTrue(all.size()==33&&all.get(21)==LightResonanceResearch.FIRST&&all.get(22)==LightResonanceResearch.FINAL
                &&all.subList(23,28).equals(AdvancedUnitResearch.items()),"old23 order and append-only five real whitelisted items");
            for(var item:AdvancedUnitResearch.items())h.assertTrue(!AdvancedUnitResearch.unlocked(f.owner,item.id(),false),"absent old-save research must remain locked");
            int[] before=balances(f);
            for(var item:List.of(AdvancedUnitResearch.PULSE_RANGE,AdvancedUnitResearch.MOTHER_NEST,AdvancedUnitResearch.HAND_HARMONY,AdvancedUnitResearch.LIGHT_EXTENSION)){
                h.assertTrue(!AdvancedUnitResearch.prerequisites(p,false,item.id())&&start(f,p,item).equals("forbidden"),"missing real predecessor/structure denied");
                h.assertTrue(Arrays.equals(before,balances(f))&&p.productionQueue.isEmpty(),"denial creates no debit/job");
            }
            h.assertTrue(AdvancedUnitResearch.prerequisites(p,false,AdvancedUnitResearch.STONE_RANGE_ID),"completed base IDLE tree qualifies ordinary research");
            var root=(DreamAnchorPlacement)f.root;root.tree().supported=false;
            h.assertTrue(!AdvancedUnitResearch.prerequisites(p,false,AdvancedUnitResearch.STONE_RANGE_ID),"unsupported geometry denies");root.tree().supported=true;
            h.succeed();
        });
    }
    @GameTest(templateNamespace="dreamseekers_advanced_research",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=200)
    public static void allFiveRealDebitsDuplicateRejectionAndExactRefunds(GameTestHelper h){
        var f=fixture(h,"Quotes",1);var p=lab(f);var root=(DreamAnchorPlacement)f.root;
        f.install(DreamseekersMod.CONTRACT_ECOLOGY,f.origin.offset(40,0,40));
        h.startSequence().thenWaitUntil(()->h.assertTrue(p.available(),"actual lab ready")).thenExecute(()->{
            ResearchServerEvents.addResearch(f.owner,ResonanceResearch.ID);
            ResearchServerEvents.addResearch(f.owner,LightResonanceResearch.FIRST_ID);
            h.assertTrue(DreamWorldTreeService.start(f.peer.actor,root,root.tree().instance,WorldTreeModel.WIND_V4).equals("started"),"real paid wind tree morph prerequisite");
            root.tree().remaining=1; // Explicit geometry fixture acceleration, never a research-duration assertion.
        }).thenWaitUntil(()->h.assertTrue(root.model()==WorldTreeModel.WIND_V4&&root.tree().phase==WorldTreeState.Phase.COMPLETE,"completed upgraded physical tree"))
        .thenExecute(()->{
            int[][] costs={{0,100,125,0},{0,150,125,25},{125,100,100,30},{200,0,150,50},{150,150,125,40}};
            for(int i=0;i<5;i++){
                var item=AdvancedUnitResearch.items().get(i);int[] before=balances(f),cost=costs[i];
                h.assertTrue(start(f,p,item).equals("started"),"real paid start "+item.id());
                int[] after=balances(f);for(int n=0;n<4;n++)h.assertTrue(after[n]==before[n]-cost[n],"exact undiscounted debit "+item.id());
                h.assertTrue(!start(f,p,item).equals("started")&&Arrays.equals(after,balances(f)),"duplicate cannot charge");
                var active=p.productionQueue.get(0);
                h.assertTrue(ReceiptResearchService.cancel(f.peer.actor,p,active.receiptResearch.receiptId(),ResearchPlacementIdentity.serverIdentity(p),p.getCaptureRevision(),f.account.accountEpoch,f.account.revision).equals("cancelled_refunded"),"exact receipt cancel");
                h.assertTrue(Arrays.equals(before,balances(f))&&p.productionQueue.isEmpty(),"all resources restored once");
            }
            h.succeed();
        });
    }
    @GameTest(templateNamespace="dreamseekers_advanced_research",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1000)
    public static void naturalPaidCompletionAndSinglePurchaseAuthority(GameTestHelper h){
        var f=fixture(h,"Natural",2);var p=lab(f);final long[] started={0};
        h.startSequence().thenWaitUntil(()->h.assertTrue(p.available(),"actual lab ready")).thenExecute(()->{
            h.assertTrue(start(f,p,AdvancedUnitResearch.STONE_RANGE).equals("started"),"paid40second research begins");started[0]=f.level.getGameTime();
        }).thenExecuteAfter(790,()->h.assertTrue(!AdvancedUnitResearch.unlocked(f.owner,AdvancedUnitResearch.STONE_RANGE_ID,false),"cannot complete before800 unaccelerated workticks"))
        .thenWaitUntil(()->h.assertTrue(AdvancedUnitResearch.unlocked(f.owner,AdvancedUnitResearch.STONE_RANGE_ID,false),"natural real queue completion"))
        .thenExecute(()->{
            h.assertTrue(f.level.getGameTime()-started[0]>=799&&p.productionQueue.isEmpty(),"work completed without direct completion injection");
            int[] before=balances(f);h.assertTrue(!start(f,p,AdvancedUnitResearch.STONE_RANGE).equals("started")&&Arrays.equals(before,balances(f)),"one-time finished identity cannot repurchase");
            h.assertTrue(ResearchServerEvents.playerHasResearch(f.owner,AdvancedUnitResearch.STONE_RANGE_ID),"existing research registry authority, not transient helper flag");h.succeed();
        });
    }
}
