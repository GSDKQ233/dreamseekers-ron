package com.dreamseekers;

import com.dreamseekers.resonance.*;
import com.dreamseekers.skill.*;
import com.dreamseekers.function.*;
import com.dreamseekers.building.DreamResonanceAura;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.research.economy.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.*;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("dreamseekers_resonance") @PrefixGameTestTemplate(false)
public final class ResonanceNetworkGameTests {
    private static final String BATCH="resonance";
    private static final List<F> FIXTURES=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level){for(var f:List.copyOf(FIXTURES))f.close();FIXTURES.clear();}
    static final class F implements AutoCloseable {
        final GameTestHelper h;final ServerLevel level;final String owner;final Resources account;final PassiveIncomePeer peer;final BlockPos origin;
        final List<BuildingPlacement> buildings=new ArrayList<>();final List<LivingEntity> units=new ArrayList<>();final Set<Long> forced=new HashSet<>();
        final BuildingPlacement root,dais;boolean closed;
        F(GameTestHelper h,String label,int index){this.h=h;level=h.getLevel();owner="ResonanceGT_"+label;origin=new BlockPos(30000+index*256,100,30000);
            FIXTURES.add(this);h.assertTrue(PlayerServerEvents.getRTSPlayer(owner)==null,"unique owner");
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));
            account=new Resources(owner,5000,5000,5000);account.extra.put(DreamPassiveEconomy.SHADOW,500);ResourcesServerEvents.resourcesList.add(account);peer=new PassiveIncomePeer(level,owner);
            root=install(DreamseekersMod.DREAM_ANCHOR,origin);dais=install(DreamseekersMod.RESONANCE_DAIS,origin.offset(24,0,0));}
        void load(BlockPos p){for(int x=(p.getX()-32)>>4;x<=(p.getX()+48)>>4;x++)for(int z=(p.getZ()-32)>>4;z<=(p.getZ()+48)>>4;z++){
            long c=ChunkPos.asLong(x,z);if(!level.getForcedChunks().contains(c)){level.setChunkForced(x,z,true);forced.add(c);}level.getChunk(x,z);}}
        BuildingPlacement install(Building type,BlockPos p){load(p);for(int x=0;x<22;x++)for(int z=0;z<22;z++){
            level.setBlock(p.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<20;y++)level.setBlock(p.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
            for(var b:BuildingUtils.getAbsoluteBlockData(type.getRelativeBlockData(level),level,p,Rotation.NONE))level.setBlock(b.getBlockPos(),b.getBlockState(),2);
            var b=BuildingUtils.getNewBuildingPlacement(type,level,p,Rotation.NONE,owner,false);BuildingServerEvents.getBuildings().add(b);buildings.add(b);b.onBuilt();return b;}
        VillagerUnit unit(BuildingPlacement p,int x,int z){load(p.originPos.offset(x,0,z));var u=EntityRegistrar.VILLAGER_UNIT.get().create(level);u.setOwnerName(owner);u.setNoAi(true);
            var pos=p.originPos.offset(x,1,z);for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){level.setBlock(pos.offset(dx,-1,dz),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<4;y++)level.setBlock(pos.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),2);}
            u.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);h.assertTrue(level.addFreshEntity(u),"actual unit added");units.add(u);return u;}
        void ready(LivingEntity u){h.assertTrue(u.tickCount>2&&level.getEntity(u.getUUID())==u&&level.getEntity(u.getId())==u&&UnitServerEvents.getAllUnits().contains(u),"native exact entity publication");}
        String pulse(BuildingPlacement p){var v=ResonanceNetwork.view(p);return ResonanceNetwork.pulse(peer.actor,p,v.instance(),v.ownerRevision(),v.capture(),v.generation());}
        int shadow(){return account.extra.get(DreamPassiveEconomy.SHADOW);}
        void later(int ticks,Runnable action){h.runAtTickTime(ticks,action);}
        @Override public void close(){if(closed)return;closed=true;peer.close();for(var u:units)if(!u.isRemoved())u.discard();
            for(var b:buildings){b.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(b);}
            ResearchServerEvents.removeAllResearchFor(owner);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(owner));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(owner));
            for(long c:forced)level.setChunkForced(ChunkPos.getX(c),ChunkPos.getZ(c),false);}
    }
    static void near(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Math.abs(actual-expected)<1e-6,why+" actual="+actual+" expected="+expected);}
    @GameTest(templateNamespace="dreamseekers_resonance",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=120)
    public static void exactThreeDimensionalRootedBreadthFirstAndCapacity(GameTestHelper h){var f=new F(h,"Network",0);
        var second=f.install(DreamseekersMod.RESONANCE_DAIS,f.origin.offset(48,0,0));var third=f.install(DreamseekersMod.RESONANCE_DAIS,f.origin.offset(72,0,0));
        var fourth=f.install(DreamseekersMod.RESONANCE_DAIS,f.origin.offset(96,0,0));var fifth=f.install(DreamseekersMod.RESONANCE_DAIS,f.origin.offset(120,0,0));
        f.later(10,()->{var n=ResonanceNetwork.network(f.level,f.owner);h.assertTrue(n.active().equals(List.of(f.dais,second,third,fourth))&&n.total()==5,"only first four rooted BFS nodes relay");
            h.assertTrue(ResonanceNetwork.linked(BlockPos.ZERO,new BlockPos(24,0,0))&&!ResonanceNetwork.linked(BlockPos.ZERO,new BlockPos(24,1,0))
                &&!ResonanceNetwork.linked(new BlockPos(Integer.MIN_VALUE,0,0),new BlockPos(Integer.MAX_VALUE,0,0)),"3D exact radius and overflow-safe far coordinates");
            BuildingServerEvents.getBuildings().remove(second);h.assertTrue(ResonanceNetwork.network(f.level,f.owner).active().equals(List.of(f.dais)),"severed bridge removes downstream nodes");
            BuildingServerEvents.getBuildings().add(second);ResearchServerEvents.addResearch(f.owner,ResonanceResearch.ID);
            h.assertTrue(ResonanceNetwork.network(f.level,f.owner).active().equals(List.of(f.dais,second,third,fourth,fifth)),"tuning raises cap without bypassing graph");
            BuildingServerEvents.getBuildings().remove(f.root);h.assertTrue(ResonanceNetwork.network(f.level,f.owner).active().isEmpty(),"no root means no active islands");f.root.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().add(f.root);
            h.assertTrue(ResonanceNetwork.network(f.level,f.owner).active().isEmpty(),"terminal oldroot reinserted inlist remains unavailable");h.succeed();});}
    @GameTest(templateNamespace="dreamseekers_resonance",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=180)
    public static void realAttributePaymentNaturalExpiryAndPaidCooldown(GameTestHelper h){var f=new F(h,"Pulse",1);var u=f.unit(f.dais,3,-4);double[] base={0};long[] ready={0};
        h.startSequence().thenWaitUntil(()->f.ready(u)).thenExecute(()->{base[0]=u.getNonBaseAttacksPerSecond();h.assertTrue(f.pulse(f.dais).equals("success")&&f.shadow()==485,"one actual15S debit");
            near(h,u.getNonBaseAttacksPerSecond(),base[0]*1.1,"actual same-category APS attribute");ready[0]=f.dais.getDataStorage().getData(ResonanceState.TYPE).readyAt();
            h.assertTrue(f.pulse(f.dais).equals("cooldown")&&f.shadow()==485,"repeated direct cast cannot charge");})
        .thenExecuteAfter(95,()->near(h,u.getNonBaseAttacksPerSecond(),base[0]*1.1,"effect remains before100tick deadline"))
        .thenExecuteAfter(10,()->{near(h,u.getNonBaseAttacksPerSecond(),base[0],"natural expiry removes multiplier");h.assertTrue(f.shadow()==485&&f.dais.getDataStorage().getData(ResonanceState.TYPE).readyAt()==ready[0],"expiry neither refunds nor resets cooldown");h.succeed();});}
    @GameTest(templateNamespace="dreamseekers_resonance",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=140)
    public static void sameTickSeveredNetworkCannotLeakOrReviveCadence(GameTestHelper h){var f=new F(h,"Sever",2);var u=f.unit(f.dais,3,-4);double[] base={0};
        h.startSequence().thenWaitUntil(()->f.ready(u)).thenExecute(()->{base[0]=u.getNonBaseAttacksPerSecond();h.assertTrue(f.pulse(f.dais).equals("success"),"cast valid before cut");near(h,u.getNonBaseAttacksPerSecond(),base[0]*1.1,"buff positive control");
            BuildingServerEvents.getBuildings().remove(f.root);near(h,u.getNonBaseAttacksPerSecond(),base[0],"same-tick actual APS getter prunes severed lease before next effecttick");
            BuildingServerEvents.getBuildings().add(f.root);near(h,u.getNonBaseAttacksPerSecond(),base[0],"reconnection cannot resurrect invalidated paid pulse");
            h.assertTrue(f.shadow()==485,"invalidation no refund");h.succeed();});}
    @GameTest(templateNamespace="dreamseekers_resonance",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=140)
    public static void actualCaptureAndTargetOwnerAbaInvalidateWithoutCadenceCarry(GameTestHelper h){var f=new F(h,"Capture",3);var a=f.unit(f.dais,3,-4);var b=f.unit(f.dais,6,-4);double[] base={0};
        h.startSequence().thenWaitUntil(()->{f.ready(a);f.ready(b);}).thenExecute(()->{base[0]=a.getNonBaseAttacksPerSecond();h.assertTrue(f.pulse(f.dais).equals("success"),"paid effect");
            a.setOwnerName("ResonanceForeign");a.setOwnerName(f.owner);near(h,a.getNonBaseAttacksPerSecond(),base[0],"target ownerABA removes original effect");
            near(h,b.getNonBaseAttacksPerSecond(),base[0]*1.1,"unrelated recipient still valid");
            f.dais.setOwnerNameFromServerEditor("ResonanceForeign");f.dais.setOwnerNameFromServerEditor(f.owner);
            near(h,b.getNonBaseAttacksPerSecond(),base[0],"native buildingcaptureABA invalidates even after restoration");h.assertTrue(f.shadow()==485,"capture no fee/refund");h.succeed();});}
    @GameTest(templateNamespace="dreamseekers_resonance",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=140)
    public static void opaqueStateAndStaleSnapshotCannotEnableAction(GameTestHelper h){var f=new F(h,"Opaque",4);var u=f.unit(f.dais,3,-4);
        h.startSequence().thenWaitUntil(()->f.ready(u)).thenExecute(()->{var old=ResonanceNetwork.view(f.dais);var prior=f.dais.getDataStorage().getData(ResonanceState.TYPE);
            CompoundTag opaque=new CompoundTag();opaque.putInt("schema",999);opaque.putString("future","preserve verbatim");
            f.dais.getDataStorage().setData(ResonanceState.TYPE,ResonanceState.read(opaque));h.assertTrue(!ResonanceNetwork.view(f.dais).supported()&&f.pulse(f.dais).equals("forbidden")&&f.shadow()==500,"unknown schema denies without charges");
            h.assertTrue(f.dais.getDataStorage().getData(ResonanceState.TYPE).write().equals(opaque),"unknown raw not downgraded or rewritten");f.dais.getDataStorage().setData(ResonanceState.TYPE,prior);
            h.assertTrue(ResonanceNetwork.pulse(f.peer.actor,f.dais,old.instance(),old.ownerRevision(),old.capture(),old.generation()).equals("stale_building"),"observed opaque replacement invalidates oldview even restoredold object");
            h.assertTrue(f.pulse(f.dais).equals("success"),"fresh supported view cancast");
            var function=f.dais.getDataStorage().getData(DreamFunctionalPlacement.STATE);CompoundTag future=new CompoundTag();future.putInt("schemaVersion",99);future.putString("future","function raw");
            f.dais.getDataStorage().setData(DreamFunctionalPlacement.STATE,DreamFunctionState.read(future));near(h,SkillEffects.bonus(u,BuffKind.ATTACK_SPEED),0,"opaque lifecycle invalidates paid pulse");
            f.dais.getDataStorage().setData(DreamFunctionalPlacement.STATE,function);near(h,SkillEffects.bonus(u,BuffKind.ATTACK_SPEED),0,"restoringold raw neverrevives invalidated effect");h.succeed();});}
    @GameTest(templateNamespace="dreamseekers_resonance",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=140)
    public static void accountReplacementAndExistingStrongerBuffDoNotStack(GameTestHelper h){var f=new F(h,"Account",5);var u=f.unit(f.dais,3,-4);
        var fox=DreamseekersMod.FRIENDLY_GHOST_UNIT.get().create(f.level);fox.setOwnerName(f.owner);fox.setNoAi(true);fox.moveTo(f.dais.originPos.getX()+2.5,f.dais.originPos.getY()+1,f.dais.originPos.getZ()-6.5);h.assertTrue(f.level.addFreshEntity(fox),"owned real skill source");f.units.add(fox);
        h.startSequence().thenWaitUntil(()->{f.ready(u);f.ready(fox);}).thenExecute(()->{
            h.assertTrue(SkillEffects.addBuff((SkillUnit)fox,u,"test_existing",BuffKind.ATTACK_SPEED,.20,140),"existing category positive effect");
            h.assertTrue(f.pulse(f.dais).equals("success"),"building pulse can coexist as weaker fallback");near(h,SkillEffects.bonus(u,BuffKind.ATTACK_SPEED),.20,"maximum not multiplicative20+10");
            ResourcesServerEvents.resourcesList.remove(f.account);var replacement=new Resources(f.owner,5000,5000,5000);replacement.extra.put(DreamPassiveEconomy.SHADOW,500);ResourcesServerEvents.resourcesList.add(replacement);
            SkillEffects.clear(fox);near(h,u.getNonBaseAttacksPerSecond(),u.getBaseAttacksPerSecond(),"new account cannot inherit old purchased buildingbuff");
            h.assertTrue(replacement.extra.get(DreamPassiveEconomy.SHADOW)==500,"replacement account neither charged nor refunded");h.succeed();});}
    @GameTest(templateNamespace="dreamseekers_resonance",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=180)
    public static void boundedDeterministicOwnRecipientsNoForeignEffects(GameTestHelper h){var f=new F(h,"Cap",6);var list=new ArrayList<VillagerUnit>();
        for(int i=0;i<18;i++)list.add(f.unit(f.dais,-3+(i%6),-3-(i/6)*2));var foreign=f.unit(f.dais,4,-5);foreign.setOwnerName("ResonanceForeign");
        h.startSequence().thenWaitUntil(()->{for(var u:list)f.ready(u);f.ready(foreign);}).thenExecute(()->{h.assertTrue(f.pulse(f.dais).equals("success")&&f.shadow()==485,"one bounded cast");
            h.assertTrue(list.stream().filter(u->SkillEffects.hasNamedBuff(u,ResonanceNetwork.EFFECT_KEY,BuffKind.ATTACK_SPEED)).count()==16,"exact16recipient cap");
            near(h,SkillEffects.bonus(foreign,BuffKind.ATTACK_SPEED),0,"foreign unit not affected");h.succeed();});}
    @GameTest(templateNamespace="dreamseekers_resonance",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=160)
    public static void emptyInsufficientAndStaleAuthorityDoNotChargeOrCooldown(GameTestHelper h){var f=new F(h,"Deny",7);
        f.later(8,()->{h.assertTrue(f.pulse(f.dais).equals("no_target")&&f.shadow()==500&&ResonanceNetwork.view(f.dais).cooldown()==0,"no target no cost or cooldown");});
        f.later(12,()->f.unit(f.dais,3,-4));
        f.later(25,()->{var u=f.units.get(0);f.ready(u);var v=ResonanceNetwork.view(f.dais);
            h.assertTrue(ResonanceNetwork.pulse(f.peer.actor,f.dais,UUID.randomUUID(),v.ownerRevision(),v.capture(),v.generation()).equals("stale_building"),"wrong identity");
            var foreign=net.minecraftforge.common.util.FakePlayerFactory.get(f.level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"ResonanceUnowned"));
            h.assertTrue(ResonanceNetwork.pulse(foreign,f.dais,v.instance(),v.ownerRevision(),v.capture(),v.generation()).equals("forbidden")&&f.shadow()==500,"different actual sender cannot pulse this owner");
            var nether=f.level.getServer().getLevel(net.minecraft.world.level.Level.NETHER);h.assertTrue(nether!=null,"real second dimension");
            var dimensionActor=net.minecraftforge.common.util.FakePlayerFactory.get(nether,new com.mojang.authlib.GameProfile(UUID.randomUUID(),f.owner));
            h.assertTrue(ResonanceNetwork.pulse(dimensionActor,f.dais,v.instance(),v.ownerRevision(),v.capture(),v.generation()).equals("forbidden"),"same name in wrong dimension rejected");
            f.account.extra.put(DreamPassiveEconomy.SHADOW,14);h.assertTrue(f.pulse(f.dais).equals("insufficient_resources")&&f.shadow()==14&&ResonanceNetwork.view(f.dais).cooldown()==0,"insufficient15S atomic reject");
            f.account.extra.put(DreamPassiveEconomy.SHADOW,500);BuildingServerEvents.getBuildings().remove(f.root);
            h.assertTrue(f.pulse(f.dais).equals("disconnected")&&f.shadow()==500,"disconnected no debit");h.succeed();});}
    private static void packet(PassiveIncomePeer peer,ResonancePackets.Request request)throws Exception{var b=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{request.encode(b);var decoded=new ResonancePackets.Request(b);
        if(!decoded.equals(request)||b.readableBytes()!=0)throw new AssertionError("exact wire roundtrip");var ctor=net.minecraftforge.network.NetworkEvent.Context.class.getDeclaredConstructor(net.minecraft.network.Connection.class,net.minecraftforge.network.NetworkDirection.class,int.class);
        ctor.setAccessible(true);var c=ctor.newInstance(peer.connection,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER,-1);decoded.handle(()->c);}finally{b.release();}peer.channel.runPendingTasks();}
    @GameTest(templateNamespace="dreamseekers_resonance",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=180)
    public static void actualAuthenticatedPacketDuplicateAndChangedPayloadFailClosed(GameTestHelper h){var f=new F(h,"Packet",8);var u=f.unit(f.dais,3,-4);
        h.startSequence().thenWaitUntil(()->f.ready(u)).thenExecute(()->{try{var token=EconomyActionGate.query(f.peer.actor,f.peer.connection);var v=ResonanceNetwork.view(f.dais);
            var payload=new ResonancePackets.Payload(ResonancePackets.Action.PULSE,f.level.dimension().location(),f.dais.originPos,UUID.randomUUID(),v.instance(),v.ownerRevision(),v.capture(),v.generation());
            var request=new ResonancePackets.Request(payload,token.epoch(),token.session(),1);packet(f.peer,request);h.assertTrue(f.shadow()==485&&SkillEffects.bonus(u,BuffKind.ATTACK_SPEED)==.10,"authenticated decoded handler actual payment andeffect");
            long revision=f.account.revision;packet(f.peer,request);h.assertTrue(f.shadow()==485&&f.account.revision==revision,"same nonce cached no extra payment");
            var changed=new ResonancePackets.Payload(payload.action(),payload.dimension(),payload.origin(),UUID.randomUUID(),payload.instance(),payload.ownerRevision(),payload.capture(),payload.generation());
            packet(f.peer,new ResonancePackets.Request(changed,token.epoch(),token.session(),1));packet(f.peer,new ResonancePackets.Request(payload,UUID.randomUUID(),token.session(),2));
            h.assertTrue(f.shadow()==485&&f.account.revision==revision,"changedpayload and staleepoch no mutation");h.succeed();}catch(Exception ex){throw new RuntimeException(ex);}});}
    @GameTest(templateNamespace="dreamseekers_resonance",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1100)
    public static void realPaidTuningQueueRefundNaturalTimeAndExpandedEffect(GameTestHelper h){var f=new F(h,"Research",9);var u=f.unit(f.dais,3,-4);
        var lab=(ProductionPlacement)f.install(DreamseekersMod.RESONANCE_LABORATORY,f.origin.offset(0,0,64));var id=ResearchPlacementIdentity.serverIdentity(lab);
        f.later(10,()->{h.assertTrue(!DreamResonanceAura.active(lab),"research clock not accelerated by local aura");
            var item=ResonanceResearch.ITEM;h.assertTrue(item.defaultCost.ticks==900&&DreamseekersMod.RESONANCE_LABORATORY.productions.get().contains(item),"registered actual45s research");
            h.assertTrue(ReceiptResearchService.start(f.peer.actor,lab,item,id,lab.getCaptureRevision(),f.account.accountEpoch,f.account.revision).equals("started"),"real paid research start");
            h.assertTrue(f.account.wood==4850&&f.account.ore==4900&&f.shadow()==475,"exact150W100O25S");var receipt=lab.productionQueue.get(0).receiptResearch.receiptId();
            h.assertTrue(ReceiptResearchService.cancel(f.peer.actor,lab,receipt,id,lab.getCaptureRevision(),f.account.accountEpoch,f.account.revision).equals("cancelled_refunded"),"real refund");
            h.assertTrue(f.account.wood==5000&&f.account.ore==5000&&f.shadow()==500,"exact originalcostrefund");
            h.assertTrue(ReceiptResearchService.start(f.peer.actor,lab,item,id,lab.getCaptureRevision(),f.account.accountEpoch,f.account.revision).equals("started"),"new genuine paid queue");});
        f.later(900,()->h.assertTrue(!ResonanceResearch.unlocked(f.owner,false),"cannot finish before900nativeworkticks"));
        f.later(930,()->{h.assertTrue(ResonanceResearch.unlocked(f.owner,false)&&lab.productionQueue.isEmpty()&&f.account.paidReceipts.isEmpty(),"native research completion consumesreceipt");
            h.assertTrue(ResonanceNetwork.view(f.dais).limit()==6&&f.pulse(f.dais).equals("success")&&f.shadow()==460,"research changeslivecap andpaidpulse");
            near(h,SkillEffects.bonus(u,BuffKind.ATTACK_SPEED),.15,"actual researched15percent");});
        f.later(1080,()->near(h,SkillEffects.bonus(u,BuffKind.ATTACK_SPEED),.15,"researchedpulse remains after150ticks"));
        f.later(1095,()->{near(h,SkillEffects.bonus(u,BuffKind.ATTACK_SPEED),0,"researchedpulse naturally expires after160ticks");h.succeed();});}
    @GameTest(templateNamespace="dreamseekers_resonance",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=120)
    public static void existingLocalProductionAuraIndependentOfNetwork(GameTestHelper h){var f=new F(h,"Aura",10);
        var lab=(ProductionPlacement)f.install(DreamseekersMod.RESONANCE_LABORATORY,f.dais.originPos.offset(0,0,10));
        f.later(10,()->{h.assertTrue(DreamResonanceAura.active(lab),"old12blockproductionaura retained");BuildingServerEvents.getBuildings().remove(f.root);
            h.assertTrue(ResonanceNetwork.view(f.dais).rank()==0&&DreamResonanceAura.active(lab),"newnetwork does not remove previouslydeliveredlocalbonus");h.succeed();});}
    @GameTest(templateNamespace="dreamseekers_resonance",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=160)
    public static void opaqueGetterObservedOwnerAbaCannotReviveOldPulse(GameTestHelper h){var f=new F(h,"GetterABA",11);var u=f.unit(f.dais,3,-4);
        h.startSequence().thenWaitUntil(()->f.ready(u)).thenExecute(()->{h.assertTrue(f.pulse(f.dais).equals("success"),"real paid buff before opaque ownerchange");
            var raw=f.dais.getDataStorage().getData(DreamFunctionalPlacement.STATE);CompoundTag future=new CompoundTag();future.putInt("schemaVersion",99);future.putString("future","untouched");
            f.dais.getDataStorage().setData(DreamFunctionalPlacement.STATE,DreamFunctionState.read(future));
            f.dais.ownerName="ResonanceOther";f.dais.getDataStorage();f.dais.ownerName=f.owner;f.dais.getDataStorage().setData(DreamFunctionalPlacement.STATE,raw);
            ResonanceNetworkGameTests.near(h,u.getNonBaseAttacksPerSecond(),u.getBaseAttacksPerSecond(),"getter-observed opaque ABA changes shared runtime generation despite restoredraw");
            h.assertTrue(f.shadow()==485,"no refund during source invalidation");h.succeed();});}
    @GameTest(templateNamespace="dreamseekers_resonance",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=160)
    public static void originalRootCaptureAbaRevokesEvenWhenCurrentGraphReconnected(GameTestHelper h){var f=new F(h,"RootABA",12);var u=f.unit(f.dais,3,-4);
        h.startSequence().thenWaitUntil(()->f.ready(u)).thenExecute(()->{h.assertTrue(f.pulse(f.dais).equals("success"),"paid pulse linked through actual root");
            f.root.setOwnerNameFromServerEditor("ResonanceOther");f.root.setOwnerNameFromServerEditor(f.owner);
            h.assertTrue(ResonanceNetwork.view(f.dais).rank()>0,"current graph is reconnected, isolates captured original path guard");
            near(h,u.getNonBaseAttacksPerSecond(),u.getBaseAttacksPerSecond(),"original rootcapture token still rejects old contribution on same-tick actualAPSread");h.succeed();});}

    @GameTest(templateNamespace="dreamseekers_resonance",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=160)
    public static void originalBridgeCaptureAbaCannotReplaceCastPath(GameTestHelper h){var f=new F(h,"BridgeABA",13);var second=f.install(DreamseekersMod.RESONANCE_DAIS,f.origin.offset(48,0,0));var u=f.unit(second,3,-4);
        h.startSequence().thenWaitUntil(()->f.ready(u)).thenExecute(()->{h.assertTrue(f.pulse(second).equals("success"),"paid pulse uses explicit firstnode bridge");
            f.dais.setOwnerNameFromServerEditor("ResonanceOther");f.dais.setOwnerNameFromServerEditor(f.owner);
            h.assertTrue(ResonanceNetwork.view(second).rank()==2,"network restored now");
            near(h,u.getNonBaseAttacksPerSecond(),u.getBaseAttacksPerSecond(),"captured originalbridge cannot revive pulse even withgraphconnected");h.succeed();});}

    @GameTest(templateNamespace="dreamseekers_resonance",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=160)
    public static void opaqueAuthorizedRequestObservedAbaCannotRevivePulse(GameTestHelper h){var f=new F(h,"RequestABA",14);var u=f.unit(f.dais,3,-4);
        h.startSequence().thenWaitUntil(()->f.ready(u)).thenExecute(()->{h.assertTrue(f.pulse(f.dais).equals("success"),"paid source before request-observed change");
            String other=f.owner+"_B";PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(other,DreamseekersMod.DREAMSEEKERS));var account=new Resources(other,100,100,100);ResourcesServerEvents.resourcesList.add(account);
            try(var peer=new PassiveIncomePeer(f.level,other)){var original=f.dais.getDataStorage().getData(DreamFunctionalPlacement.STATE);CompoundTag future=new CompoundTag();future.putInt("schemaVersion",99);future.putString("opaque","request");
                f.dais.getDataStorage().setData(DreamFunctionalPlacement.STATE,DreamFunctionState.read(future));f.dais.ownerName=other;
                var result=((DreamFunctionalPlacement)f.dais).request(peer.actor,new DreamFunctionRequest.Resonance(BlockPos.ZERO,null,DreamFunctionRequest.ResonanceOperation.JOIN));
                h.assertTrue(result.status()==DreamFunctionResult.Status.UNSUPPORTED_SCHEMA,"actual ownerB authenticated request observes opaquehost");
                f.dais.ownerName=f.owner;f.dais.getDataStorage().setData(DreamFunctionalPlacement.STATE,original);
                near(h,u.getNonBaseAttacksPerSecond(),u.getBaseAttacksPerSecond(),"request-observed ABA can't restore oldpaidbuff");
            }finally{PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(other));ResourcesServerEvents.resourcesList.remove(account);f.dais.ownerName=f.owner;}
            h.succeed();});}

}
