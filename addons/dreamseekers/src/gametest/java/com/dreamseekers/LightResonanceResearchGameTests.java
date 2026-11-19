package com.dreamseekers;

import com.dreamseekers.building.DreamLaboratoryPlacement;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.progression.*;
import com.dreamseekers.resonance.*;
import com.dreamseekers.skill.*;
import com.dreamseekers.unit.PinkSlimeUnit;
import com.dreamseekers.unitprogression.DreamSpecialistResearch;
import com.dreamseekers.worldtree.*;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.production.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.research.economy.*;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Real paid queue and authenticated private-channel tests. Embedded peers are not a rendered client. */
@GameTestHolder("dreamseekers_luminous_research") @PrefixGameTestTemplate(false)
public final class LightResonanceResearchGameTests {
    private static final String BATCH="luminous_research";
    private static final List<ResonanceNetworkGameTests.F> FIXTURES=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level){for(var f:FIXTURES)f.close();FIXTURES.clear();}
    private static ResonanceNetworkGameTests.F fixture(GameTestHelper h,String name,int index){
        var f=new ResonanceNetworkGameTests.F(h,"Light"+name,80+index);FIXTURES.add(f);return f;
    }
    private static DreamLaboratoryPlacement lab(ResonanceNetworkGameTests.F f){return (DreamLaboratoryPlacement)f.install(DreamseekersMod.RESONANCE_LABORATORY,f.origin.offset(0,0,32));}
    private static String start(ResonanceNetworkGameTests.F f,DreamLaboratoryPlacement p,ReceiptBackedResearchItem item){
        return ReceiptResearchService.start(f.peer.actor,p,item,ResearchPlacementIdentity.serverIdentity(p),p.getCaptureRevision(),f.account.accountEpoch,f.account.revision);
    }
    private static String cancel(ResonanceNetworkGameTests.F f,DreamLaboratoryPlacement p,UUID receipt){
        return ReceiptResearchService.cancel(f.peer.actor,p,receipt,ResearchPlacementIdentity.serverIdentity(p),p.getCaptureRevision(),f.account.accountEpoch,f.account.revision);
    }
    private static int[] balances(ResonanceNetworkGameTests.F f){return new int[]{f.account.food,f.account.wood,f.account.ore,f.shadow()};}
    private static void equalBalances(GameTestHelper h,ResonanceNetworkGameTests.F f,int[] expected,String why){h.assertTrue(Arrays.equals(balances(f),expected),why+" "+Arrays.toString(balances(f)));}
    private static void afterPaid(GameTestHelper h,ResonanceNetworkGameTests.F f,int[] before,int tier){
        equalBalances(h,f,new int[]{before[0]-(tier==1?120:240),before[1]-(tier==1?120:240),before[2]-(tier==1?100:200),before[3]-(tier==1?30:80)},"exact actual tier "+tier+" debit");
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=100)
    public static void originalTwentyOneLabItemsRetainedAndActualPaidCancelIsExact(GameTestHelper h){
        var f=fixture(h,"Registry",0);var p=lab(f);
        h.startSequence().thenWaitUntil(()->h.assertTrue(p.available(),"normal owned laboratory ready")).thenExecute(()->{
            var expected=new ArrayList<ProductionItem>();for(int family=0;family<3;family++)for(int tier=0;tier<5;tier++)expected.add(DreamWindResearch.TIERS[family][tier]);
            expected.addAll(List.of(DreamSpecialistResearch.PRECISION_TOOLS,DreamSpecialistResearch.SHELL_WEAVING,DreamSpecialistResearch.CONTRACT_CARE,DreamSpecialistResearch.STONE_SIEGE,ResonanceResearch.ITEM,com.dreamseekers.neutralcontract.ContractResearch.CAPACITY));
            var actual=DreamseekersMod.RESONANCE_LABORATORY.productions.get();h.assertTrue(actual.size()==33&&actual.subList(23,28).equals(com.dreamseekers.unitprogression.AdvancedUnitResearch.items())&&actual.subList(0,21).equals(expected)&&actual.get(21)==LightResonanceResearch.FIRST&&actual.get(22)==LightResonanceResearch.FINAL,"all original 21 registry identities and order retained; luminous prefix retained, five advanced items appended");
            h.assertTrue(ReignOfNetherRegistries.PRODUCTION_ITEM.get(LightResonanceResearch.FIRST_ID)==LightResonanceResearch.FIRST&&ReignOfNetherRegistries.PRODUCTION_ITEM.get(LightResonanceResearch.FINAL_ID)==LightResonanceResearch.FINAL,"actual registered identities");
            h.assertTrue(LightResonanceResearch.FIRST.defaultCost.ticks==900&&LightResonanceResearch.FINAL.defaultCost.ticks==1500,"approved base durations");
            int[] before=balances(f);h.assertTrue(start(f,p,LightResonanceResearch.FINAL).equals("forbidden"),"second tier cannot skip predecessor");equalBalances(h,f,before,"no skipped-tier debit");
            h.assertTrue(start(f,p,LightResonanceResearch.FIRST).equals("started"),"first real receipt-backed queue");afterPaid(h,f,before,1);
            UUID receipt=p.productionQueue.get(0).receiptResearch.receiptId();long revision=f.account.revision;
            h.assertTrue(start(f,p,LightResonanceResearch.FIRST).equals("duplicate")&&f.account.revision==revision,"queued duplicate rejects without extra payment");
            int prior=LightResonanceResearch.FIRST.defaultCost.food;
            try{LightResonanceResearch.FIRST.defaultCost.food=999;h.assertTrue(cancel(f,p,receipt).equals("cancelled_refunded"),"original paid receipt refunded independently of changed quote");}finally{LightResonanceResearch.FIRST.defaultCost.food=prior;}
            equalBalances(h,f,before,"exact original refund");revision=f.account.revision;
            h.assertTrue(cancel(f,p,receipt).equals("unknown_receipt")&&f.account.revision==revision&&p.productionQueue.isEmpty()&&f.account.paidReceipts.isEmpty(),"repeat cancel cannot mint");
            ResearchServerEvents.addResearch(f.owner,LightResonanceResearch.FINAL_ID);
            h.assertTrue(LightResonanceResearch.tier(f.owner,false)==0,"a forged final-only research record cannot skip tier one for casts");
            h.assertTrue(!LightResonanceResearch.FIRST.validHostAndPrerequisite((DreamAnchorPlacement)f.root),"actual wrong host rejects the new research");
            var original=p.getDataStorage().getData(com.dreamseekers.function.DreamFunctionalPlacement.STATE);
            var opaque=new net.minecraft.nbt.CompoundTag();opaque.putInt("schemaVersion",99);opaque.putString("future","preserve");
            p.getDataStorage().setData(com.dreamseekers.function.DreamFunctionalPlacement.STATE,com.dreamseekers.function.DreamFunctionState.read(opaque));
            h.assertTrue(start(f,p,LightResonanceResearch.FIRST).equals("forbidden"),"unsupported laboratory lifecycle cannot debit");equalBalances(h,f,before,"unsupported host no debit");
            p.getDataStorage().setData(com.dreamseekers.function.DreamFunctionalPlacement.STATE,original);
            f.close();h.succeed();});
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1000)
    public static void firstResearchUsesFullNaturalWorkAndPermanentCompletion(GameTestHelper h){
        var f=fixture(h,"Natural",1);var p=lab(f);long[] paid={0};UUID[] receipt={null};
        h.startSequence().thenWaitUntil(()->h.assertTrue(p.available(),"owned lab ready")).thenExecute(()->{
            h.assertTrue(ProductionWorkRates.percent(p,LightResonanceResearch.FIRST)==100,"non-wind remote lab has baseline work rate");
            h.assertTrue(start(f,p,LightResonanceResearch.FIRST).equals("started"),"actual first payment");paid[0]=f.level.getGameTime();receipt[0]=p.productionQueue.get(0).receiptResearch.receiptId();
        }).thenExecuteAfter(880,()->h.assertTrue(LightResonanceResearch.tier(f.owner,false)==0&&!p.productionQueue.isEmpty(),"no early grant or test-only shortening"))
        .thenWaitUntil(()->h.assertTrue(LightResonanceResearch.tier(f.owner,false)==1&&p.productionQueue.isEmpty(),"native 900 work ticks completed"))
        .thenExecute(()->{
            h.assertTrue(f.level.getGameTime()-paid[0]>=899&&!f.account.paidReceipts.containsKey(receipt[0]),"full paid work consumed receipt once");long rev=f.account.revision;
            h.assertTrue(start(f,p,LightResonanceResearch.FIRST).equals("duplicate")&&f.account.revision==rev,"completed tier cannot repurchase");
            BuildingServerEvents.getBuildings().remove(f.root);h.assertTrue(LightResonanceResearch.tier(f.owner,false)==1&&!LightResonanceResearch.prerequisites(p,false,1),"research remains permanent when dynamic network disappears");
            BuildingServerEvents.getBuildings().add(f.root);f.close();h.succeed();});
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=140)
    public static void networkLossPausesSamePaidJobThenResumesOrRefunds(GameTestHelper h){
        var f=fixture(h,"Pause",2);var p=lab(f);float[] left={0};UUID[] receipt={null};
        h.startSequence().thenWaitUntil(()->h.assertTrue(p.available(),"lab ready")).thenExecute(()->{
            h.assertTrue(start(f,p,LightResonanceResearch.FIRST).equals("started"),"real paid task");
            left[0]=p.productionQueue.get(0).ticksLeft;receipt[0]=p.productionQueue.get(0).receiptResearch.receiptId();BuildingServerEvents.getBuildings().remove(f.dais);
        }).thenExecuteAfter(15,()->{
            h.assertTrue(p.productionQueue.size()==1&&p.productionQueue.get(0).ticksLeft==left[0]&&f.account.paidReceipts.containsKey(receipt[0])&&LightResonanceResearch.tier(f.owner,false)==0,"temporary prerequisite loss pauses, neither completes nor discards paid claim");
            BuildingServerEvents.getBuildings().add(f.dais);
        }).thenExecuteAfter(8,()->{
            h.assertTrue(p.productionQueue.get(0).ticksLeft<left[0]&&p.productionQueue.get(0).receiptResearch.receiptId().equals(receipt[0]),"restoration resumes original receipt without repaying");
            BuildingServerEvents.getBuildings().remove(f.root);int[] before=balances(f);h.assertTrue(cancel(f,p,receipt[0]).equals("cancelled_refunded"),"explicit cancel works while dependency missing");
            equalBalances(h,f,new int[]{before[0]+120,before[1]+120,before[2]+100,before[3]+30},"original refund during pause");BuildingServerEvents.getBuildings().add(f.root);f.close();h.succeed();});
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=160)
    public static void actualWindWorldTreePermitsFinalAndExistingRateQuotesStayTruthful(GameTestHelper h){
        var f=fixture(h,"Wind",3);var p=lab(f);var root=(DreamAnchorPlacement)f.root;
        h.startSequence().thenWaitUntil(()->h.assertTrue(p.available(),"lab ready")).thenExecute(()->{
            // This fixture isolates FINAL prerequisites. Full paid FIRST and both natural completions are separate tests/restart.
            ResearchServerEvents.addResearch(f.owner,LightResonanceResearch.FIRST_ID);
            h.assertTrue(!LightResonanceResearch.prerequisites(p,false,2)&&start(f,p,LightResonanceResearch.FINAL).equals("forbidden"),"base tree cannot qualify via research flag");
            h.assertTrue(DreamWorldTreeService.start(f.peer.actor,root,root.tree().instance,WorldTreeModel.WIND_V4).equals("started"),"actual paid wind morph starts");
            root.tree().remaining=1; // Explicit geometry prerequisite fixture acceleration, never a research-duration assertion.
        }).thenWaitUntil(()->h.assertTrue(root.model()==WorldTreeModel.WIND_V4&&root.tree().phase==WorldTreeState.Phase.COMPLETE,"native paid morph committed actual wind geometry"))
        .thenExecute(()->{
            h.assertTrue(!root.ascended()&&LightResonanceResearch.prerequisites(p,false,2),"legal high wind branch accepted without shadow-only ascended() trap");
            h.assertTrue(ProductionWorkRates.percent(p,LightResonanceResearch.FINAL)==125&&ProductionWorkRates.estimatedSeconds(1500,125)==60,"earned wind modifies actual research time, not hardcoded 75 seconds");
            var local=f.install(DreamseekersMod.RESONANCE_DAIS,p.originPos.offset(12,0,0));
            h.assertTrue(com.dreamseekers.building.DreamResonanceAura.active(p)&&ProductionWorkRates.percent(p,LightResonanceResearch.FINAL)==135
                &&ProductionWorkRates.estimatedSeconds(1500,135)==56,"existing local ten-percent aura combines with wind in live time quote");
            var quote=LightResonanceResearch.FINAL.quote(p);h.assertTrue(quote.food()==240&&quote.wood()==240&&quote.ore()==200&&quote.extra().get(DreamPassiveEconomy.SHADOW)==80,"existing wind training discount does not silently discount research");
            int[] before=balances(f);h.assertTrue(start(f,p,LightResonanceResearch.FINAL).equals("started"),"second real payment through wind branch");afterPaid(h,f,before,2);
            var active=p.productionQueue.get(0);LightResonanceResearch.FINAL.tick(p,active);h.assertTrue(Math.abs(active.ticksLeft-1498.65)<.001,"actual 135 percent work matches quoted time");
            h.assertTrue(cancel(f,p,active.receiptResearch.receiptId()).equals("cancelled_refunded"),"second tier cancel refunds actual original quote");equalBalances(h,f,before,"second refund exact");f.close();h.succeed();});
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=60)
    public static void boundedPrivateCodecAndStrictVersionRejectLegacyClients(GameTestHelper h){
        h.assertTrue(SkillNetwork.acceptsProtocol("19")&&!SkillNetwork.acceptsProtocol("18")&&!SkillNetwork.acceptsProtocol("ABSENT")&&!SkillNetwork.acceptsProtocol("ACCEPTVANILLA"),"explicit bidirectional handshake refuses old/absent clients");
        for(var skill:List.of(UnitSkill.GEL_GUARD,UnitSkill.JELLY_FLOW,UnitSkill.FOX_BLESSING))for(int tier=0;tier<=2;tier++){
            var sync=new SkillNetwork.Sync(skill,7,UUID.randomUUID(),12,skill.cooldownTicks(),skill.supportsAutocast(),tier);var b=new FriendlyByteBuf(Unpooled.buffer());
            try{sync.encode(b);h.assertTrue(sync.equals(SkillNetwork.Sync.decode(b))&&b.readableBytes()==0,"tier, identity, revision, cooldown and autocast exact roundtrip");}finally{b.release();}
        }
        for(int tier:new int[]{-1,3,Integer.MAX_VALUE}){boolean denied=false;try{new SkillNetwork.Sync(UnitSkill.GEL_GUARD,7,UUID.randomUUID(),12,0,false,tier);}catch(IllegalArgumentException expected){denied=true;}h.assertTrue(denied,"invalid tier rejected");}
        boolean denied=false;try{new SkillNetwork.Sync(UnitSkill.WIND_DASH,7,UUID.randomUUID(),12,0,false,2);}catch(IllegalArgumentException expected){denied=true;}h.assertTrue(denied,"unsupported skills cannot display amplification");h.succeed();
    }
    private static PinkSlimeUnit slime(ResonanceNetworkGameTests.F f,int dx){
        var u=DreamseekersMod.PINK_SLIME_UNIT.get().create(f.level);var pos=f.dais.originPos.offset(dx,1,-4);
        f.level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<4;y++)f.level.setBlock(pos.above(y),Blocks.AIR.defaultBlockState(),2);
        u.setOwnerName(f.owner);u.setNoAi(true);u.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);f.h.assertTrue(f.level.addFreshEntity(u),"real skill source/target");f.units.add(u);return u;
    }
    private static List<SkillNetwork.Sync> syncs(PassiveIncomePeer peer){
        var result=new ArrayList<SkillNetwork.Sync>();for(Object p:peer.drain())if(p instanceof ClientboundCustomPayloadPacket c&&c.getIdentifier().equals(DreamseekersMod.id("unit_skills"))){
            var b=new FriendlyByteBuf(c.getData().copy());try{if(b.readVarInt()==1){result.add(SkillNetwork.Sync.decode(b));if(b.readableBytes()!=0)throw new AssertionError("trailing private sync bytes");}}finally{b.release();}}
        return result;
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=160)
    public static void realPrivateSyncRecomputesTierAndForgedClientSuffixCannotAmplifyCast(GameTestHelper h){
        var f=fixture(h,"Wire",4);var source=slime(f,2);var target=slime(f,5);
        h.startSequence().thenWaitUntil(()->{f.ready(source);f.ready(target);}).thenExecute(()->{
            syncs(f.peer);var forged=new SkillNetwork.Sync(UnitSkill.GEL_GUARD,source.getId(),source.getUUID(),source.rts().skills().revision(),0,false,2);
            SkillNetwork.send(f.peer.actor,forged);var observed=syncs(f.peer);h.assertTrue(observed.size()==1&&observed.get(0).amplificationTier()==0,"real private encoded send recomputes server tier instead of accepting even internal claimed tier");
            try(var foreign=new PassiveIncomePeer(f.level,f.owner+"Foreign")){
                SkillNetwork.send(foreign.actor,forged);h.assertTrue(syncs(foreign).isEmpty(),"real private channel refuses different recipient even with a valid source identity");
            }
            var request=new SkillNetwork.Cast(UnitSkill.GEL_GUARD,source.getId(),source.getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID());var b=new FriendlyByteBuf(Unpooled.buffer());
            try{request.encode(b);b.writeVarInt(2);var decoded=SkillNetwork.Cast.decode(b);h.assertTrue(decoded.equals(request)&&b.readableBytes()==1,"C2S codec has no tier or potency field; forged suffix is not input authority");h.assertTrue(decoded.execute(f.peer.actor)==SkillResult.SUCCESS,"unchanged authenticated cast path");}finally{b.release();}
            ResonanceNetworkGameTests.near(h,SkillEffects.shieldRemaining(target),12,"forged tier cannot change server baseline shield");
            ResearchServerEvents.addResearch(f.owner,LightResonanceResearch.FIRST_ID);source.rts().skills().sync(f.peer.actor);observed=syncs(f.peer);h.assertTrue(observed.stream().anyMatch(v->v.sourceUuid().equals(source.getUUID())&&v.amplificationTier()==1),"real SkillState private publication displays freshly eligible tier1");
            BuildingServerEvents.getBuildings().remove(f.root);source.rts().skills().sync(f.peer.actor);observed=syncs(f.peer);h.assertTrue(observed.stream().filter(v->v.sourceUuid().equals(source.getUUID())).allMatch(v->v.amplificationTier()==0),"current private values return to baseline when network disappears");
            BuildingServerEvents.getBuildings().add(f.root);f.close();h.succeed();});
    }
}
