package com.dreamseekers;
import com.dreamseekers.economy.*;
import com.dreamseekers.shadow.*;
import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.resources.kill.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

/** Staged candidates; real service and Forge publication, no socket/GUI claims. */
@GameTestHolder("dreamseekers_terrorbeak_upgrade")
@PrefixGameTestTemplate(false)
public final class TerrorbeakUpgradeGameTests {
    private static final String BATCH="terrorbeak_upgrade";
    private static final List<F> FIXTURES=new ArrayList<>();
    private static final class F implements AutoCloseable {
        final GameTestHelper h;final String owner;final Resources account;final PassiveIncomePeer peer;final BlockPos center;
        final List<Consumer<EntityJoinLevelEvent>> listeners=new ArrayList<>();
        final List<Entity> entities=new ArrayList<>();
        final List<Runnable> cleanups=new ArrayList<>();
        boolean closed;
        TerrorbeakUnit upgradingSource;
        F(GameTestHelper h,String name){this.h=h;owner="Crazy_"+name;PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));
            account=new Resources(owner,1000,1000,1000);account.extra.put(DreamPassiveEconomy.SHADOW,500);ResourcesServerEvents.resourcesList.add(account);peer=new PassiveIncomePeer(h.getLevel(),owner);
            var at=h.absolutePos(new BlockPos(12,2,12));center=new BlockPos(at.getX(),100,at.getZ());
            for(var p:BlockPos.betweenClosed(center.offset(-8,-1,-8),center.offset(8,6,8)))h.getLevel().setBlock(p,p.getY()==99?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            FIXTURES.add(this);
        }
        TerrorbeakUnit source(int offset){var unit=DreamseekersMod.TERRORBEAK_UNIT.get().create(h.getLevel());unit.setOwnerName(owner);unit.setNoAi(true);unit.moveTo(center.getX()+offset+.5,100,center.getZ()+.5);h.assertTrue(h.getLevel().addFreshEntity(unit),"source published");entities.add(unit);return unit;}
        TerrorbeakUpgradeRequest request(TerrorbeakUnit source){return new TerrorbeakUpgradeRequest(1,UUID.randomUUID(),new TerrorbeakUpgradeRequest.Source(source.getId(),source.getUUID(),source.rts().ownershipRevision,h.getLevel().dimension().location()),TerrorbeakUpgradeRequest.UpgradeTarget.CRAZY_TERRORBEAK);}
        TerrorbeakUpgradeService.Result upgrade(TerrorbeakUnit source){
            var previous=upgradingSource;upgradingSource=source;
            try{return TerrorbeakUpgradeService.request(peer.actor,request(source));}finally{upgradingSource=previous;}
        }
        List<CrazyTerrorbeakUnit> upgraded(){return UnitServerEvents.getAllUnits().stream().filter(e->e instanceof CrazyTerrorbeakUnit u&&u.getOwnerName().equals(owner)).map(e->(CrazyTerrorbeakUnit)e).toList();}
        private boolean ownPendingJoin(EntityJoinLevelEvent event){return !closed&&upgradingSource!=null&&upgradingSource.isAlive()&&!upgradingSource.isRemoved()
            &&h.getLevel().getEntity(upgradingSource.getUUID())==upgradingSource&&upgradingSource.getOwnerName().equals(owner)
            &&event.getLevel()==h.getLevel()
            &&event.getEntity() instanceof CrazyTerrorbeakUnit candidate&&candidate.getOwnerName().equals(owner)
            &&TerrorbeakUpgradeService.pending(candidate);}
        void guard(Consumer<EntityJoinLevelEvent> fn){
            Consumer<EntityJoinLevelEvent> scoped=event->{if(ownPendingJoin(event))fn.accept(event);};
            listeners.add(scoped);MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,scoped);
        }
        /** Publication probes are local to one attempted replacement, not the five-tick observation window. */
        void guardOnce(Consumer<EntityJoinLevelEvent> fn){
            Consumer<EntityJoinLevelEvent> scoped=new Consumer<>(){
                private boolean armed=true;
                @Override public void accept(EntityJoinLevelEvent event){
                    if(!armed||!ownPendingJoin(event))return;
                    armed=false;MinecraftForge.EVENT_BUS.unregister(this);listeners.remove(this);
                    fn.accept(event);
                }
            };
            listeners.add(scoped);MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,scoped);
        }
        void at(int tick,Runnable run){h.runAtTickTime(tick,()->{try{run.run();}catch(RuntimeException|Error e){close();throw e;}});}
        void unchanged(){h.assertTrue(account.food==1000&&account.wood==1000&&account.ore==1000&&account.extra.get(DreamPassiveEconomy.SHADOW)==500,"failure preserves all balances");}
        @Override public void close(){if(closed)return;closed=true;listeners.forEach(MinecraftForge.EVENT_BUS::unregister);listeners.clear();cleanups.forEach(Runnable::run);cleanups.clear();for(var e:new ArrayList<>(UnitServerEvents.getAllUnits()))if(e instanceof Unit u&&u.getOwnerName().equals(owner))e.discard();entities.forEach(Entity::discard);ResourcesServerEvents.resourcesList.removeIf(r->r==account);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(owner));peer.close();FIXTURES.remove(this);}
    }
    @AfterBatch(batch=BATCH) public static void cleanup(net.minecraft.server.level.ServerLevel level){for(var f:List.copyOf(FIXTURES))f.close();}
    @GameTest(templateNamespace="dreamseekers_terrorbeak_upgrade",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
    public static void paidTransformationConservesPopulationRatioOriginAndCooldowns(GameTestHelper h){
        F f=new F(h,"paid");var source=f.source(0);source.setHealth(50);var value=RtsKillValueClaims.copyValue(source);UUID origin=RtsKillValueClaims.valueId(source);
        CompoundTag skills=new CompoundTag(),inner=new CompoundTag();inner.putLong("Revision",10);inner.putLong("TERRORBEAK_DREAD_ROAR",h.getLevel().getGameTime()+300);skills.put("DreamseekersUnitSkills",inner);source.rts().skills().load(skills);
        ((TeamSafeMeleeAttackGoal)source.getAttackGoal()).inheritTransformationCooldown(20);int pop=UnitServerEvents.getCurrentPopulation(f.owner);var request=f.request(source);
        h.assertTrue(TerrorbeakUpgradeService.request(f.peer.actor,request)==TerrorbeakUpgradeService.Result.SUCCESS,"paid real publication commits");
        h.assertTrue(source.isRemoved()&&f.upgraded().size()==1&&UnitServerEvents.getCurrentPopulation(f.owner)==pop,"one replacement and same3population");
        var next=f.upgraded().get(0);h.assertTrue(Math.abs(next.getHealth()/next.getMaxHealth()-.5)<.001,"half health preserved, not healed");
        h.assertTrue(next.rts().skills().cooldownTicks(UnitSkill.TERRORBEAK_DREAD_ROAR)==300&&((TeamSafeMeleeAttackGoal)next.getAttackGoal()).transformationCooldown()>=20,"both cooldowns preserved");
        h.assertTrue(RtsKillValueClaims.valueId(next).equals(origin)&&next.getPersistentData().get(RtsKillValueClaims.KEY).equals(value),"single economic lineage unchanged");
        h.assertTrue(next.getCost().food==210&&next.getCost().ore==145&&next.getCost().population==3,"standard cumulative cost excludesS");
        h.assertTrue(f.account.food==920&&f.account.ore==940&&f.account.extra.get(DreamPassiveEconomy.SHADOW)==470,"exact80F60O30S debit");
        h.assertTrue(TerrorbeakUpgradeService.request(f.peer.actor,request)==TerrorbeakUpgradeService.Result.INVALID_SOURCE,"old source cannot purchase again");
        h.assertTrue(SkillService.supports(next,UnitSkill.TERRORBEAK_DREAD_ROAR),"controlled roar retained");
        f.at(5,()->{h.assertTrue(next.rts().skills().cooldownTicks(UnitSkill.TERRORBEAK_DREAD_ROAR)<300&&next.rts().skills().cooldownTicks(UnitSkill.TERRORBEAK_DREAD_ROAR)>290,"cooldown natural time continues");f.close();h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_terrorbeak_upgrade",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
    public static void lateJoinCancellationAndReentrantPurchaseLeaveNoSpendOrDuplicate(GameTestHelper h){
        F f=new F(h,"cancel");var source=f.source(0);int population=UnitServerEvents.getCurrentPopulation(f.owner);int[] observed={0};
        f.guard(e->{if(e.getEntity() instanceof CrazyTerrorbeakUnit u&&u.getOwnerName().equals(f.owner)&&TerrorbeakUpgradeService.pending(u)){
            observed[0]++;h.assertTrue(f.upgrade(source)==TerrorbeakUpgradeService.Result.BUSY,"same owner/source reentry cannot mint");e.setCanceled(true);
        }});
        h.assertTrue(f.upgrade(source)==TerrorbeakUpgradeService.Result.SPAWN_FAILED&&observed[0]==1,"real Forge late cancellation observed");
        f.unchanged();h.assertTrue(source.isAlive()&&!source.isRemoved()&&f.upgraded().isEmpty()&&UnitServerEvents.getCurrentPopulation(f.owner)==population,"original survives exact replacement rollback");f.close();h.succeed();
    }
    @GameTest(templateNamespace="dreamseekers_terrorbeak_upgrade",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
    public static void joinCommandOwnerAndCandidateMutationCannotCommit(GameTestHelper h){
        F f=new F(h,"mutate");var source=f.source(0);int[] mode={0};
        f.guard(e->{if(e.getEntity() instanceof CrazyTerrorbeakUnit u&&u.getOwnerName().equals(f.owner)&&TerrorbeakUpgradeService.pending(u)){
            if(mode[0]==0)Unit.fullResetBehaviours(source);
            else if(mode[0]==1){source.setOwnerName("Crazy_other");source.setOwnerName(f.owner);}
            else if(mode[0]==2){u.setOwnerName("Crazy_other");u.setOwnerName(f.owner);}
            else u.setHealth(1);
        }});
        for(int i=0;i<4;i++){mode[0]=i;h.assertTrue(f.upgrade(source)==TerrorbeakUpgradeService.Result.INVALID_SOURCE,"real publication mutation mode"+i+" rejects");f.unchanged();h.assertTrue(f.upgraded().isEmpty()&&source.isAlive(),"no replacement remains");}
        f.close();h.succeed();
    }
    @GameTest(templateNamespace="dreamseekers_terrorbeak_upgrade",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
    public static void effectFundsFreeClaimAndSpaceGatesDoNotCleanseOrSpend(GameTestHelper h){
        F f=new F(h,"gates");var source=f.source(0);
        source.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,200));h.assertTrue(f.upgrade(source)==TerrorbeakUpgradeService.Result.ACTIVE_EFFECT&&source.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),"cannot cleanse by transforming");source.removeAllEffects();
        f.account.extra.put(DreamPassiveEconomy.SHADOW,29);h.assertTrue(f.upgrade(source)==TerrorbeakUpgradeService.Result.INSUFFICIENT_RESOURCES,"needsactual30S");f.account.extra.put(DreamPassiveEconomy.SHADOW,500);
        BlockPos obstacle=source.blockPosition().above(2);h.getLevel().setBlock(obstacle,Blocks.STONE.defaultBlockState(),2);h.assertTrue(f.upgrade(source)==TerrorbeakUpgradeService.Result.UNSAFE_POSITION,"actual body collision denies");h.getLevel().setBlock(obstacle,Blocks.AIR.defaultBlockState(),2);
        RtsKillValueClaims.markFree(source,RtsKillValueClaims.FreeOrigin.NATIVE_SUMMON);h.assertTrue(f.upgrade(source)==TerrorbeakUpgradeService.Result.INVALID_SOURCE,"free lineage remainsunpaid");
        var claimed=f.source(5);CompoundTag value=new CompoundTag();value.putInt("version",1);value.putUUID("origin",claimed.getUUID());value.put("claim",new CompoundTag());claimed.getPersistentData().put(RtsKillValueClaims.KEY,value);
        h.assertTrue(f.upgrade(claimed)==TerrorbeakUpgradeService.Result.INVALID_SOURCE,"claimed lineage cannot refresh");f.unchanged();h.assertTrue(f.upgraded().isEmpty(),"no candidate survivesfailedgates");f.close();h.succeed();
    }
    @GameTest(templateNamespace="dreamseekers_terrorbeak_upgrade",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
    public static void pendingSavedCopyCannotEnterWorldAndCompletedNbtRetainsLineage(GameTestHelper h){
        F f=new F(h,"nbt");var source=f.source(0);UUID origin=RtsKillValueClaims.valueId(source);
        var pending=DreamseekersMod.CRAZY_TERRORBEAK_UNIT.get().create(h.getLevel());pending.setOwnerName(f.owner);pending.getPersistentData().putBoolean(TerrorbeakUpgradeService.PENDING_MARKER,true);
        h.assertTrue(!h.getLevel().addFreshEntity(pending),"uncommitted savedcandidate cannot republish");
        h.assertTrue(f.upgrade(source)==TerrorbeakUpgradeService.Result.SUCCESS,"legitimateupgrade works");var next=f.upgraded().get(0);var saved=new CompoundTag();next.saveWithoutId(saved);
        var clone=DreamseekersMod.CRAZY_TERRORBEAK_UNIT.get().create(h.getLevel());clone.load(saved);
        h.assertTrue(!TerrorbeakUpgradeService.pending(clone)&&RtsKillValueClaims.valueId(clone).equals(origin)&&clone.getOwnerName().equals(f.owner),"normal NBT preserves committedform/lineage without pendingmarker");
        // This is codec only: the same UUID clone is deliberately never published. Real two-JVM probe remains required.
        f.close();h.succeed();
    }
    @GameTest(templateNamespace="dreamseekers_terrorbeak_upgrade",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
    public static void committedLeaveCallbacksCannotBeOverwrittenOrResurrected(GameTestHelper h){
        F f=new F(h,"leave");var source=f.source(0);CrazyTerrorbeakUnit[] captured={null};
        Consumer<net.minecraftforge.event.entity.EntityLeaveLevelEvent> listener=e->{
            if(e.getEntity()!=source)return;
            var next=f.upgraded().get(0);captured[0]=next;f.entities.add(next);
            h.assertTrue(!TerrorbeakUpgradeService.pending(next),"leave callback sees a genuinely committed replacement");
            h.assertTrue(!UnitServerEvents.getAllUnits().contains(source),"old population removed before callback");
            next.setOwnerName("Crazy_leave_capture");next.setNoAi(true);
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,listener);
        try {
            h.assertTrue(f.upgrade(source)==TerrorbeakUpgradeService.Result.SUCCESS,"transformation commits before ordinary leave observer");
            h.assertTrue(captured[0]!=null&&captured[0].getOwnerName().equals("Crazy_leave_capture")&&captured[0].isNoAi(),"no late owner/NoAI rewrite after observer");
            h.assertTrue(f.account.food==920&&f.account.ore==940&&f.account.extra.get(DreamPassiveEconomy.SHADOW)==470,"one committed charge");
        } finally {MinecraftForge.EVENT_BUS.unregister(listener);f.close();}
        h.succeed();
    }
    @GameTest(templateNamespace="dreamseekers_terrorbeak_upgrade",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
    public static void committedLeaveDeathIsNotRolledBackOrReactivated(GameTestHelper h){
        F f=new F(h,"leave_death");var source=f.source(0);CrazyTerrorbeakUnit[] dead={null};final int[] actualDeaths={0};
        Consumer<net.minecraftforge.event.entity.living.LivingDeathEvent> death=e->{if(e.getEntity()==dead[0])actualDeaths[0]++;};MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,death);f.cleanups.add(()->MinecraftForge.EVENT_BUS.unregister(death));
        Consumer<net.minecraftforge.event.entity.EntityLeaveLevelEvent> listener=e->{if(e.getEntity()==source){var next=f.upgraded().get(0);dead[0]=next;f.entities.add(next);next.setNoAi(true);next.setHealth(1);h.assertTrue(next.hurt(h.getLevel().damageSources().generic(),10000),"real lethal hurt during leave callback");}};
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,listener);
        try {
            h.assertTrue(f.upgrade(source)==TerrorbeakUpgradeService.Result.SUCCESS,"committed transformation remains spent after external death");
            h.assertTrue(dead[0]!=null&&actualDeaths[0]==1&&!dead[0].isAlive()&&dead[0].isNoAi()&&source.isRemoved(),"no resurrection or reactivation after leave death");
            h.assertTrue(f.account.food==920&&f.account.ore==940&&f.account.extra.get(DreamPassiveEconomy.SHADOW)==470,"no post-commit refund exploit");
        } finally {MinecraftForge.EVENT_BUS.unregister(listener);f.close();}
        h.succeed();
    }

    private static TerrorbeakUpgradePackets.Snapshot wire(F f,TerrorbeakUpgradePackets.Request packet)throws Exception {
        return wire(f.h,f.peer,packet);
    }
    private static TerrorbeakUpgradePackets.Snapshot wire(GameTestHelper h,PassiveIncomePeer peer,TerrorbeakUpgradePackets.Request packet)throws Exception {
        var ctor=net.minecraftforge.network.NetworkEvent.Context.class.getDeclaredConstructor(net.minecraft.network.Connection.class,net.minecraftforge.network.NetworkDirection.class,int.class);ctor.setAccessible(true);
        var context=ctor.newInstance(peer.connection,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER,-1);
        h.assertTrue(context.getSender()==peer.actor&&peer.connection.isConnected(),"real exact attached sender/connection");
        var b=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{packet.encode(b);var decoded=new TerrorbeakUpgradePackets.Request(b);h.assertTrue(decoded.equals(packet)&&b.readableBytes()==0,"real request codec roundtrip");decoded.handle(()->context);}finally{b.release();}
        TerrorbeakUpgradePackets.Snapshot result=null;
        for(var o:peer.drain())if(o instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket c&&c.getIdentifier().equals(DreamseekersMod.id("terrorbeak_upgrade"))){var bytes=new net.minecraft.network.FriendlyByteBuf(c.getData().copy());try{h.assertTrue(bytes.readVarInt()==1,"actual upgrade response discriminator");result=new TerrorbeakUpgradePackets.Snapshot(bytes);h.assertTrue(bytes.readableBytes()==0,"response exact codec");}finally{bytes.release();}}
        h.assertTrue(result!=null,"actual private upgrade reply on connected channel");return result;
    }
    @GameTest(templateNamespace="dreamseekers_terrorbeak_upgrade",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
    public static void authenticatedPacketReplayMutationAndEpochCannotRepay(GameTestHelper h)throws Exception {
        F f=new F(h,"wire");try{
            var source=f.source(0);UUID view=UUID.randomUUID();var dim=h.getLevel().dimension().location();
            var query=new TerrorbeakUpgradePackets.Payload(true,dim,source.getId(),source.getUUID(),0,view);
            var token=wire(f,new TerrorbeakUpgradePackets.Request(query,TerrorbeakUpgradePackets.ZERO,TerrorbeakUpgradePackets.ZERO,0));
            h.assertTrue(token.available()&&token.epoch().equals(f.account.accountEpoch),"real server quote is available and same ledger epoch");
            var buy=new TerrorbeakUpgradePackets.Payload(false,dim,source.getId(),source.getUUID(),token.ownerRevision(),view);
            var request=new TerrorbeakUpgradePackets.Request(buy,token.epoch(),token.session(),1);
            var ok=wire(f,request);h.assertTrue(ok.status().equals("success")&&ok.request()==1&&f.upgraded().size()==1,"actual handle/Gate transaction committed; status="+ok.status()+" request="+ok.request()+" food="+f.account.food+" ore="+f.account.ore+" shadow="+f.account.extra.get(DreamPassiveEconomy.SHADOW)+" sameEpoch="+ok.epoch().equals(token.epoch()));
            var duplicate=wire(f,request);h.assertTrue(duplicate.status().equals("success")&&f.account.food==920&&f.account.ore==940&&f.account.extra.get(DreamPassiveEconomy.SHADOW)==470&&f.upgraded().size()==1,"same sequence exact replay returns cached success but no second debit/spawn");
            var other=f.source(5);var altered=new TerrorbeakUpgradePackets.Payload(false,dim,other.getId(),other.getUUID(),other.rts().ownershipRevision,UUID.randomUUID());
            var mutation=wire(f,new TerrorbeakUpgradePackets.Request(altered,token.epoch(),token.session(),1));h.assertTrue(!mutation.status().equals("success")&&!other.isRemoved()&&f.account.food==920,"same sequence new payload cannot buy a second body");
            var stale=wire(f,new TerrorbeakUpgradePackets.Request(altered,UUID.randomUUID(),token.session(),2));h.assertTrue(stale.status().equals("stale_session")&&!other.isRemoved()&&f.account.food==920,"forged epoch rejected before economic action");
            try(var reconnect=new PassiveIncomePeer(h.getLevel(),f.owner)){
                var freshQuery=new TerrorbeakUpgradePackets.Payload(true,dim,other.getId(),other.getUUID(),0,UUID.randomUUID());
                var fresh=wire(h,reconnect,new TerrorbeakUpgradePackets.Request(freshQuery,TerrorbeakUpgradePackets.ZERO,TerrorbeakUpgradePackets.ZERO,0));
                h.assertTrue(fresh.epoch().equals(token.epoch())&&!fresh.session().equals(token.session()),"same account epoch but different real connected peer receives distinct action session");
                var previousConnection=wire(h,reconnect,new TerrorbeakUpgradePackets.Request(altered,token.epoch(),token.session(),2));
                h.assertTrue(previousConnection.status().equals("stale_session")&&!other.isRemoved()&&f.account.food==920,"same-epoch previous connection request cannot replay through new connection");
            }
            UUID previous=f.account.accountEpoch;f.account.accountEpoch=UUID.randomUUID();
            var old=wire(f,new TerrorbeakUpgradePackets.Request(altered,previous,token.session(),2));h.assertTrue(old.status().equals("stale_session")&&old.epoch().equals(f.account.accountEpoch)&&!other.isRemoved()&&f.account.food==920,"actual same-owner epoch rotation invalidates old sequence/session, fresh reply uses new epoch");
            h.succeed();
        }finally{f.close();}
    }
    @GameTest(templateNamespace="dreamseekers_terrorbeak_upgrade",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
    public static void upgradedBodyRealDeathPaysStandardCostOnceFromOriginalLineage(GameTestHelper h) {
        F victim=new F(h,"value_v"),killer=new F(h,"value_k");var source=victim.source(0);victim.source(-5);UUID origin=RtsKillValueClaims.valueId(source);
        h.assertTrue(victim.upgrade(source)==TerrorbeakUpgradeService.Result.SUCCESS,"real old paid body upgraded");var next=victim.upgraded().get(0);var attacker=killer.source(5);next.setNoAi(true);
        final int[] events={0};Consumer<ConfirmedRtsKillEvent> twice=e->{if(e.snapshot().victimId().equals(next.getUUID())){events[0]++;h.assertTrue(e.snapshot().food()==210&&e.snapshot().wood()==0&&e.snapshot().ore()==145&&e.snapshot().economicValueId().equals(origin),"actual death captures standard upgrade cost and original single lineage");DreamShadowKillIncome.onConfirmedKill(e);DreamShadowKillIncome.onConfirmedKill(e);}};
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST,twice);killer.cleanups.add(()->MinecraftForge.EVENT_BUS.unregister(twice));
        try{next.setHealth(1);h.assertTrue(next.hurt(h.getLevel().damageSources().mobAttack(attacker),1000),"real attributed lethal attack accepted");}catch(RuntimeException|Error e){MinecraftForge.EVENT_BUS.unregister(twice);victim.close();killer.close();throw e;}
        killer.at(5,()->{try{h.assertTrue(events[0]==1&&RtsKillValueClaims.isClaimed(next)&&killer.account.extra.get(DreamPassiveEconomy.SHADOW)==517&&killer.account.conversionRemainders.get(DreamShadowKillIncome.REMAINDER)==7500L,"355 standard cost at5percent=17 plus7500remainder, duplicate consumer no second payout");next.hurt(h.getLevel().damageSources().mobAttack(attacker),1000);h.assertTrue(killer.account.extra.get(DreamPassiveEconomy.SHADOW)==517,"dead-body repeat has no extra award");h.succeed();}finally{MinecraftForge.EVENT_BUS.unregister(twice);victim.close();killer.close();}});
    }
    @GameTest(templateNamespace="dreamseekers_terrorbeak_upgrade",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
    public static void nativeSandboxEnemyCrazyGetsTrustedFixtureAndActualStandardReward(GameTestHelper h)throws Exception {
        F f=new F(h,"native");var seat=PlayerServerEvents.getRTSPlayer(f.owner);seat.faction=com.solegendary.reignofnether.faction.Factions.NONE;
        var editorGrant=new com.solegendary.reignofnether.sandbox.SandboxEditorTestGrant(f.peer.actor,seat);f.cleanups.add(editorGrant::close);
        h.assertTrue(DreamSandboxEconomy.initialize(h.getLevel().getServer(),f.owner),"real native sandbox epoch grant settled");
        int balance=f.account.extra.get(DreamPassiveEconomy.SHADOW);h.assertTrue(ResourceTransaction.exchange(h.getLevel().getServer(),f.account,f.account.accountEpoch,f.account.revision,new ResourceTransaction.Cost(0,0,0,Map.of(DreamPassiveEconomy.SHADOW,balance)),ResourceTransaction.Cost.ZERO).applied(),"declared zero-shadow test fixture spends grant without clearing initialization marker");
        var attacker=f.source(0);var before=new HashSet<>(UnitServerEvents.getAllUnits());
        var packet=new com.solegendary.reignofnether.sandbox.SandboxServerboundPacket(com.solegendary.reignofnether.sandbox.SandboxAction.SPAWN_UNIT,"Enemy",EntityType.getKey(DreamseekersMod.CRAZY_TERRORBEAK_UNIT.get()).toString(),f.center.offset(5,-1,0),new int[0]);
        var ctor=net.minecraftforge.network.NetworkEvent.Context.class.getDeclaredConstructor(net.minecraft.network.Connection.class,net.minecraftforge.network.NetworkDirection.class,int.class);ctor.setAccessible(true);var context=ctor.newInstance(f.peer.connection,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER,-1);
        var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());try{packet.encode(buf);new com.solegendary.reignofnether.sandbox.SandboxServerboundPacket(buf).handle(()->context);}finally{buf.release();}
        var added=UnitServerEvents.getAllUnits().stream().filter(e->!before.contains(e)).toList();h.assertTrue(added.size()==1&&added.get(0) instanceof CrazyTerrorbeakUnit,"actual native Enemy request creates controlled Crazy");var enemy=(CrazyTerrorbeakUnit)added.get(0);f.entities.add(enemy);enemy.setNoAi(true);
        h.assertTrue(com.solegendary.reignofnether.sandbox.SandboxEconomyFixture.has(enemy)&&enemy.getCost().food==210&&enemy.getCost().ore==145,"native entry minted trusted fixture for real standard cost, no test remint");
        enemy.setHealth(1);h.assertTrue(enemy.hurt(h.getLevel().damageSources().mobAttack(attacker),1000),"real native Enemy lethal attack");
        f.at(5,()->{h.assertTrue(RtsKillValueClaims.isClaimed(enemy)&&f.account.extra.get(DreamPassiveEconomy.SHADOW)==17&&f.account.conversionRemainders.get(DreamShadowKillIncome.REMAINDER)==7500L,"actual native Enemy reward is17 with7500remainder, no fake zero-cost fixture");f.close();h.succeed();});
    }
    private static BoneWingUnit markCaster(F enemy,TerrorbeakUnit target){
        var caster=DreamseekersMod.BONE_WING_UNIT.get().create(enemy.h.getLevel());caster.setOwnerName(enemy.owner);caster.setNoAi(true);caster.moveTo(target.getX()+3,target.getY(),target.getZ());enemy.h.assertTrue(enemy.h.getLevel().addFreshEntity(caster),"actual hostile BoneWing caster published");enemy.entities.add(caster);return caster;
    }
    private static SkillResult mark(F enemy,BoneWingUnit caster,TerrorbeakUnit target){return SkillService.request(enemy.peer.actor,new SkillRequest(UnitSkill.COORDINATED_MARK,caster.getId(),caster.getUUID(),caster.rts().skills().revision(),target.getId(),target.getUUID()));}
    @GameTest(templateNamespace="dreamseekers_terrorbeak_upgrade",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
    public static void hostileMarkBlocksUpgradeWithoutCleaningOrRefreshingItsRealDeadline(GameTestHelper h){
        F owner=new F(h,"mark_owner"),enemy=new F(h,"mark_enemy");var source=owner.source(0);var caster=markCaster(enemy,source);
        h.assertTrue(mark(enemy,caster,source)==SkillResult.SUCCESS&&CoordinatedMark.ticksRemaining(source,enemy.owner)==120,"actual hostile mark installs original120tick vulnerability");long deadline=h.getLevel().getGameTime()+120;
        h.assertTrue(CoordinatedMark.hasActiveTargetMark(source)&&owner.upgrade(source)==TerrorbeakUpgradeService.Result.ACTIVE_EFFECT,"any-owner read-only mark query blocks initial upgrade");owner.unchanged();h.assertTrue(!source.isRemoved()&&owner.upgraded().isEmpty()&&CoordinatedMark.ticksRemaining(source,enemy.owner)==120,"refusal neither removes nor refreshes existing mark");
        owner.at(5,()->{try{int remaining=(int)(deadline-h.getLevel().getGameTime());h.assertTrue(remaining>0&&CoordinatedMark.ticksRemaining(source,enemy.owner)==remaining&&owner.upgrade(source)==TerrorbeakUpgradeService.Result.ACTIVE_EFFECT,"actual mark naturally ticks through repeated upgrade refusal");owner.unchanged();h.assertTrue(CoordinatedMark.ticksRemaining(source,enemy.owner)==remaining,"same remaining deadline after repeated refusal");h.succeed();}finally{owner.close();enemy.close();}});
    }
    @GameTest(templateNamespace="dreamseekers_terrorbeak_upgrade",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
    public static void markInstalledDuringJoinFailsSecondValidationWithoutSpendOrCleanse(GameTestHelper h){
        F owner=new F(h,"mark_join"),enemy=new F(h,"mark_join_enemy");var source=owner.source(0);var caster=markCaster(enemy,source);final long[] deadline={-1};
        owner.guardOnce(e->{if(e.getEntity() instanceof CrazyTerrorbeakUnit candidate&&candidate.getOwnerName().equals(owner.owner)&&TerrorbeakUpgradeService.pending(candidate)){var result=mark(enemy,caster,source);h.assertTrue(result==SkillResult.SUCCESS,"real hostile mark installed by synchronous publication callback; result="+result);deadline[0]=h.getLevel().getGameTime()+120;}});
        h.assertTrue(owner.upgrade(source)==TerrorbeakUpgradeService.Result.INVALID_SOURCE,"post-Join effectFree revalidation rejects newly marked source");owner.unchanged();
        h.assertTrue(!source.isRemoved()&&owner.upgraded().isEmpty()&&CoordinatedMark.hasActiveTargetMark(source)&&CoordinatedMark.ticksRemaining(source,enemy.owner)==120,"candidate rollback leaves original active mark and body intact");
        owner.at(5,()->{try{h.assertTrue(CoordinatedMark.ticksRemaining(source,enemy.owner)==deadline[0]-h.getLevel().getGameTime(),"rollback neither refreshes nor clears real mark deadline");owner.unchanged();h.succeed();}finally{owner.close();enemy.close();}});
    }
    @SuppressWarnings("unchecked")
    private static Map<Integer,net.minecraft.world.level.chunk.ChunkAccess> trackedTickets()throws Exception {
        var field=UnitServerEvents.class.getDeclaredField("forcedUnitChunks");field.setAccessible(true);return (Map<Integer,net.minecraft.world.level.chunk.ChunkAccess>)field.get(null);
    }
    @GameTest(templateNamespace="dreamseekers_terrorbeak_upgrade",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
    public static void ascendancyRatioAndExactRetirementTrackedTicketsAreConserved(GameTestHelper h)throws Exception {
        F f=new F(h,"ascend");var source=f.source(0);var unrelated=f.source(5);
        com.solegendary.reignofnether.research.ResearchServerEvents.addResearch(f.owner,DreamEconomyResearch.ASCENDANCY_ID);f.cleanups.add(()->com.solegendary.reignofnether.research.ResearchServerEvents.removeAllResearchFor(f.owner));
        DreamAscendancyAttributes.reconcile(source,DreamAscendancyAttributes.enabledFor(source));source.setHealth(source.getMaxHealth()*.37f);
        h.assertTrue(source.getMaxHealth()>100&&source.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getModifier(DreamAscendancyAttributes.HEALTH_ID)!=null,"real owner research installed permanent global modifier before transformation");
        var tickets=trackedTickets();var original=tickets.get(source.getId());var retained=tickets.get(unrelated.getId());h.assertTrue(original!=null&&retained!=null,"real core Join registered both original tracked native tickets");int count=tickets.size(),pop=UnitServerEvents.getCurrentPopulation(f.owner);
        h.assertTrue(f.upgrade(source)==TerrorbeakUpgradeService.Result.SUCCESS,"researched form transition commits");var next=f.upgraded().get(0);
        h.assertTrue(next.getMaxHealth()>120&&Math.abs(next.getHealth()/next.getMaxHealth()-.37)<.001&&next.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getModifier(DreamAscendancyAttributes.HEALTH_ID)!=null,"new maxHP includes same real research before37percent transfer");
        h.assertTrue(!tickets.containsKey(source.getId())&&tickets.get(next.getId())!=null&&tickets.get(unrelated.getId())==retained&&tickets.size()==count,"only old exact tracked ticket retired; new and unrelated ticket preserved once");
        h.assertTrue(UnitServerEvents.getCurrentPopulation(f.owner)==pop&&UnitServerEvents.getAllUnits().stream().filter(e->e==next).count()==1&&UnitServerEvents.getAllUnits().stream().noneMatch(e->e==source),"single old-to-new replacement, no population duplicate");
        f.at(5,()->{h.assertTrue(Math.abs(next.getHealth()/next.getMaxHealth()-.37)<.001,"natural living attribute reconciliation cannot heal a second time");f.close();h.succeed();});
    }
    @GameTest(templateNamespace="dreamseekers_terrorbeak_upgrade",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
    public static void inheritedOrdinaryCooldownBlocksRealGoalFinisherUntilOriginalDelayElapses(GameTestHelper h){
        F owner=new F(h,"attack_owner"),enemy=new F(h,"attack_enemy");try{
            var source=owner.source(0);var victim=enemy.source(5);enemy.source(-5);var before=(TeamSafeMeleeAttackGoal)source.getAttackGoal();before.spendAttackCooldown();int remaining=before.transformationCooldown();h.assertTrue(remaining>0,"real installed ordinary goal has spent remaining cooldown");
            h.assertTrue(owner.upgrade(source)==TerrorbeakUpgradeService.Result.SUCCESS,"real replacement with existing ordinary cooldown");var next=owner.upgraded().get(0);next.setNoAi(true);victim.moveTo(next.getX()+3,next.getY(),next.getZ());victim.setHealth(1);next.setUnitAttackTargetForced(victim);next.setHoldPosition(true);var goal=(TeamSafeMeleeAttackGoal)next.getAttackGoal();
            h.assertTrue(next.getTarget()==victim&&!next.getBoundingBox().intersects(victim.getBoundingBox())&&next.hasLineOfSight(victim)&&victim.invulnerableTime==0,"actual in-range non-overlapping visible target with no hurt immunity");
            goal.start();for(int tick=0;tick<remaining;tick++){goal.tick();h.assertTrue(victim.isAlive()&&victim.getHealth()==1,"actual installed melee goal cannot upgrade-finisher before inherited delay tick="+tick);goal.tickAttackCooldown();}
            goal.tick();goal.stop();h.assertTrue(!victim.isAlive(),"same actual goal delivers lethal melee after exact inherited ordinary delay, no direct injected hurt");h.succeed();
        }finally{owner.close();enemy.close();}
    }
    @GameTest(templateNamespace="dreamseekers_terrorbeak_upgrade",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=80)
    public static void pendingPublicationRejectsRealDamageAndCannotProduceKillValue(GameTestHelper h){
        F owner=new F(h,"pending_owner"),enemy=new F(h,"pending_enemy");var source=owner.source(0);var attacker=enemy.source(5);final int[] deaths={0};final CrazyTerrorbeakUnit[] candidate={null};
        Consumer<net.minecraftforge.event.entity.living.LivingDeathEvent> observed=e->{if(e.getEntity()==candidate[0])deaths[0]++;};MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,observed);owner.cleanups.add(()->MinecraftForge.EVENT_BUS.unregister(observed));
        owner.guardOnce(e->{if(e.getEntity() instanceof CrazyTerrorbeakUnit next&&next.getOwnerName().equals(owner.owner)&&TerrorbeakUpgradeService.pending(next)){candidate[0]=next;float hp=next.getHealth(),targetHp=attacker.getHealth();
            h.assertTrue(TerrorbeakUpgradeService.pending(next)&&!next.hurt(h.getLevel().damageSources().mobAttack(attacker),1000)&&next.getHealth()==hp,"real pending target hurt canceled, HP unchanged");
            h.assertTrue(!attacker.hurt(h.getLevel().damageSources().mobAttack(next),1000)&&attacker.getHealth()==targetHp,"real damage attributed to pending attacker canceled");
            // A separate actual death entry bypasses hurt cancellation, as another server mod could do.
            next.setHealth(0);next.die(h.getLevel().damageSources().mobAttack(attacker));
        }});
        h.assertTrue(owner.upgrade(source)!=TerrorbeakUpgradeService.Result.SUCCESS&&candidate[0]!=null&&deaths[0]==1,"actual LivingDeath during publication aborts commit");owner.unchanged();
        owner.at(5,()->{try{h.assertTrue(!source.isRemoved()&&owner.upgraded().isEmpty()&&enemy.account.extra.get(DreamPassiveEconomy.SHADOW)==500&&!RtsKillValueClaims.isClaimed(candidate[0]),"pending real death never yields value, no paid replacement or debit");owner.unchanged();h.succeed();}finally{owner.close();enemy.close();}});
    }
    static List<SkillNetwork.Sync> ownerSkillViews(List<Object> packets){
        var result=new ArrayList<SkillNetwork.Sync>();
        for(var packet:packets)if(packet instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket payload&&payload.getIdentifier().equals(DreamseekersMod.id("unit_skills"))){
            var bytes=new net.minecraft.network.FriendlyByteBuf(payload.getData().copy());try{if(bytes.readVarInt()==1){var view=SkillNetwork.Sync.decode(bytes);if(bytes.readableBytes()!=0)throw new AssertionError("trailing skill view bytes");result.add(view);}}finally{bytes.release();}
        }
        return result;
    }
    @GameTest(templateNamespace="dreamseekers_terrorbeak_upgrade",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=140)
    public static void crazyNaturalTickRefreshesPrivateViewsPastExpiryAndOnNewConnection(GameTestHelper h){
        F f=new F(h,"view_owner"),outsider=new F(h,"view_other");var source=f.source(0);
        h.assertTrue(f.upgrade(source)==TerrorbeakUpgradeService.Result.SUCCESS,"real upgrade initial sync not sufficient evidence");var next=f.upgraded().get(0);next.setNoAi(true);f.peer.drain();outsider.peer.drain();
        f.at(41,()->{f.peer.drain();outsider.peer.drain();}); // discard every packet inside the original40tick view lifetime
        f.at(55,()->{
            var views=ownerSkillViews(f.peer.drain());h.assertTrue(views.stream().anyMatch(v->v.sourceUuid().equals(next.getUUID())&&v.sourceId()==next.getId()&&v.skill()==UnitSkill.TERRORBEAK_DREAD_ROAR&&v.revision()==next.rts().skills().revision()&&v.cooldownTicks()==next.rts().skills().cooldownTicks(UnitSkill.TERRORBEAK_DREAD_ROAR)),"natural alive server tick emitted fresh real private roar view after initial40tick expiry");
            h.assertTrue(ownerSkillViews(outsider.peer.drain()).stream().noneMatch(v->v.sourceUuid().equals(next.getUUID())),"unrelated connected owner receives no private Crazy view");
            var reconnect=new PassiveIncomePeer(h.getLevel(),f.owner);f.cleanups.add(reconnect::close);reconnect.drain();
            f.at(70,()->{try{h.assertTrue(ownerSkillViews(reconnect.drain()).stream().anyMatch(v->v.sourceUuid().equals(next.getUUID())&&v.skill()==UnitSkill.TERRORBEAK_DREAD_ROAR&&v.revision()>=0),"same-player new real connection obtains fresh view by ordinary tick without manual sync");h.succeed();}finally{f.close();outsider.close();}});
        });
    }
}
