package com.dreamseekers;

import com.dreamseekers.aaroncos.ControlledAaroncosHand;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

/** Full natural skill windups/cooldowns; real hurt and registered actor, not socket/GUI proof. */
@GameTestHolder("dreamseekers_aaroncos_skills")
@PrefixGameTestTemplate(false)
public final class AaroncosSkillCombatGameTests {
    private static final String BATCH="aaroncos_skills";
    private static int arenaIndex;
    private static final List<F> FIXTURES=new ArrayList<>();
    static final class F implements AutoCloseable {
        final GameTestHelper h;final String owner,enemy;final Resources account;final PassiveIncomePeer peer;
        final BlockPos center;final List<Mob> bodies=new ArrayList<>();final List<Object> listeners=new ArrayList<>();
        F(GameTestHelper h,String name) {
            this.h=h;owner="Hand_"+name;enemy=owner+"_enemy";
            for(String n:List.of(owner,enemy))PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(n,DreamseekersMod.DREAMSEEKERS));
            account=new Resources(owner,1000,1000,1000);account.extra.put(DreamPassiveEconomy.SHADOW,500);ResourcesServerEvents.resourcesList.add(account);
            ResourcesServerEvents.resourcesList.add(new Resources(enemy,1000,1000,1000));peer=new PassiveIncomePeer(h.getLevel(),owner);
            center=new BlockPos(151552+128*arenaIndex++,100,151552);
            for(int x=(center.getX()-16)>>4;x<=(center.getX()+16)>>4;x++)for(int z=(center.getZ()-16)>>4;z<=(center.getZ()+16)>>4;z++)h.getLevel().setChunkForced(x,z,true);
            for(BlockPos at:BlockPos.betweenClosed(center.offset(-16,-1,-16),center.offset(16,7,16)))h.getLevel().setBlock(at,at.getY()==99?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
            FIXTURES.add(this);
        }
        <T extends Mob> T add(T body,String name,double x,double z) {
            ((Unit)body).setOwnerName(name);body.setNoAi(true);body.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);body.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);body.moveTo(center.getX()+x+.5,100,center.getZ()+z+.5);
            h.assertTrue(h.getLevel().addFreshEntity(body),"real entity published");bodies.add(body);return body;
        }
        ControlledAaroncosHand hand(boolean left) {
            ControlledAaroncosHand body=left?DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get().create(h.getLevel()):DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get().create(h.getLevel());
            h.assertTrue(body.getAbilities().get().equals(left?List.of(UnitSkillAbility.AARONCOS_LEFT_PRESS,com.dreamseekers.aaroncos.HandBondAbility.INSTANCE):List.of(UnitSkillAbility.AARONCOS_RIGHT_SHOCK,com.dreamseekers.aaroncos.HandBondAbility.INSTANCE,UnitSkillAbility.AARONCOS_RIGHT_VORTEX)),"fresh constructor exposes exactly its own skill before publication");
            add(body.mob(),owner,0,0);return body;
        }
        GoldenFoxUnit target(double x,double z) {
            var t=add(DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel()),enemy,x,z);
            t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);t.setHealth(1000);
            t.getAttribute(Attributes.ARMOR).setBaseValue(0);t.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
            t.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);t.setAbsorptionAmount(0);
            for(var a:List.of(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(),AttributeRegistrar.RANGED_DAMAGE_RESIST.get()))if(t.getAttribute(a)!=null)t.getAttribute(a).setBaseValue(0);
            return t;
        }
        SkillRequest request(ControlledAaroncosHand s,LivingEntity target){return new SkillRequest(SkillService.skillFor(s),s.mob().getId(),s.mob().getUUID(),s.rts().skills().revision(),target.getId(),target.getUUID());}
        SkillResult cast(ControlledAaroncosHand s,LivingEntity target){
            var packet=new SkillNetwork.Cast(request(s,target));var bytes=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try{packet.encode(bytes);var decoded=SkillNetwork.Cast.decode(bytes);h.assertTrue(decoded.equals(packet)&&bytes.readableBytes()==0,"real request codec exact roundtrip");return decoded.execute(peer.actor);}finally{bytes.release();}
        }
        void guard(Consumer<LivingDamageEvent> listener){listeners.add(listener);MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,listener);}
        int shadow(){return account.extra.get(DreamPassiveEconomy.SHADOW);}
        void hp(LivingEntity body,float expected,String why){h.assertTrue(Math.abs(body.getHealth()-expected)<.001,why+" actual="+body.getHealth()+" expected="+expected);}
        @Override public void close(){listeners.forEach(MinecraftForge.EVENT_BUS::unregister);listeners.clear();bodies.forEach(Entity::discard);peer.close();
            PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(owner)||p.name.equals(enemy));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(owner)||r.ownerName.equals(enemy));for(int x=(center.getX()-16)>>4;x<=(center.getX()+16)>>4;x++)for(int z=(center.getZ()-16)>>4;z<=(center.getZ()+16)>>4;z++)h.getLevel().setChunkForced(x,z,false);FIXTURES.remove(this);}
        void done(){close();h.succeed();}
    }
    @AfterBatch(batch=BATCH) public static void cleanup(net.minecraft.server.level.ServerLevel level){for(F f:List.copyOf(FIXTURES))f.close();}

    @GameTest(templateNamespace="dreamseekers_aaroncos_skills",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=480)
    public static void leftNaturalTwelveTickDamageAndFourHundredCooldown(GameTestHelper h) {
        F f=new F(h,"leftclock");var s=f.hand(true);var target=f.target(5,0);long[] start={0};
        h.startSequence().thenIdle(30).thenExecute(()->{
            var q=f.request(s,target);start[0]=h.getLevel().getGameTime();
            h.assertTrue(SkillService.request(f.peer.actor,q)==SkillResult.SUCCESS,"auth left paid cast");
            h.assertTrue(f.shadow()==480&&s.rts().skills().cooldownTicks(q.skill())==400&&AaroncosCombat.remainingTicks(s)==12,"exact shadow20/CD400/windup12");
            h.assertTrue(SkillService.request(f.peer.actor,q)==SkillResult.REPLAY&&f.shadow()==480,"same revision cannot spend twice");
        }).thenIdle(11).thenExecute(()->f.hp(target,1000,"no instant/11tick damage"))
          .thenIdle(1).thenExecute(()->{f.hp(target,954.5f,"exact actual45.5 HP loss at12");h.assertTrue(AaroncosShockSuppression.remainingTicks(target)==0,"left press never grants right suppression");h.assertTrue(!AaroncosCombat.active(s),"single release only");})
          .thenIdle(387).thenExecute(()->{h.assertTrue(h.getLevel().getGameTime()==start[0]+399&&s.rts().skills().cooldownTicks(UnitSkill.AARONCOS_LEFT_PRESS)==1,"natural last cooldowntick");h.assertTrue(f.cast(s,target)==SkillResult.COOLDOWN,"cannot recast one tickearly");})
          .thenIdle(1).thenExecute(()->{h.assertTrue(s.rts().skills().cooldownTicks(UnitSkill.AARONCOS_LEFT_PRESS)==0,"real400 deadline");h.assertTrue(f.cast(s,target)==SkillResult.SUCCESS&&f.shadow()==460,"second real paid cast after deadline");Unit.fullResetBehaviours(s);f.done();});
    }
    @GameTest(templateNamespace="dreamseekers_aaroncos_skills",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void rightFixedFourTargetsAndTwentyTickRelease(GameTestHelper h) {
        F f=new F(h,"rightcap");var s=f.hand(false);var targets=List.of(f.target(3,0),f.target(0,3),f.target(-3,0),f.target(0,-3),f.target(3,2));
        List<UUID> actual=new ArrayList<>();List<LivingEntity> expected=new ArrayList<>();
        f.guard(e->{if(AaroncosCombat.isStrike(e.getSource())&&e.getSource().getEntity()==s.mob())actual.add(e.getEntity().getUUID());});
        h.startSequence().thenIdle(30).thenExecute(()->{
            expected.addAll(targets.stream().sorted(Comparator.comparingDouble((GoldenFoxUnit t)->ShadowGolemStrike.distanceSquared(s.mob().position(),t.getBoundingBox())).thenComparing(t->t.getUUID().toString())).limit(4).toList());
            h.assertTrue(f.cast(s,s.mob())==SkillResult.SUCCESS&&f.shadow()==470,"self input casts ring for30shadow");
        }).thenIdle(19).thenExecute(()->{for(var t:targets)f.hp(t,1000,"no19tick damage");})
          .thenIdle(1).thenExecute(()->{for(var t:targets){f.hp(t,expected.contains(t)?976:1000,"fixedmax4 actual damage");h.assertTrue(AaroncosShockSuppression.remainingTicks(t)==(expected.contains(t)?40:0),"only exact four real surviving hit receipts obtain forty-tick suppression");}
            h.assertTrue(actual.equals(expected.stream().map(Entity::getUUID).toList()),"stable distance UUID hit ordering");h.assertTrue(s.rts().skills().cooldownTicks(UnitSkill.AARONCOS_RIGHT_SHOCK)==580,"actual600 cooldown consumed20");f.done();});
    }
    @GameTest(templateNamespace="dreamseekers_aaroncos_skills",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void insufficientInvalidFriendlyHiddenAndSolidLosNeverSpend(GameTestHelper h) {
        F f=new F(h,"invalid");var s=f.hand(true);var target=f.target(5,0);
        h.startSequence().thenIdle(30).thenExecute(()->{
            long revision=s.rts().skills().revision();f.account.extra.put(DreamPassiveEconomy.SHADOW,19);
            h.assertTrue(f.cast(s,target)==SkillResult.INSUFFICIENT_RESOURCES,"insufficientshadow rejected before reset");f.account.extra.put(DreamPassiveEconomy.SHADOW,500);
            target.setOwnerName(f.owner);h.assertTrue(f.cast(s,target)==SkillResult.INVALID_TARGET,"own target rejected");target.setOwnerName(f.enemy);
            target.setInvisible(true);h.assertTrue(f.cast(s,target)==SkillResult.INVALID_TARGET,"invisible target rejected");target.setInvisible(false);
            for(BlockPos p:BlockPos.betweenClosed(f.center.offset(3,0,-2),f.center.offset(3,5,2)))h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),2);
            h.assertTrue(f.cast(s,target)==SkillResult.INVALID_TARGET,"actual solid wall blocks lineofsight");
            h.assertTrue(f.shadow()==500&&s.rts().skills().revision()==revision&&!AaroncosCombat.active(s),"all invalidcases no payment/CD");f.done();
        });
    }
    @GameTest(templateNamespace="dreamseekers_aaroncos_skills",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=110)
    public static void nativeStopDuringWindupKeepsPaymentAndCooldown(GameTestHelper h) {
        F f=new F(h,"stop");var s=f.hand(true);var target=f.target(5,0);
        h.startSequence().thenIdle(30).thenExecute(()->h.assertTrue(f.cast(s,target)==SkillResult.SUCCESS,"castbeforeSTOP"))
          .thenIdle(5).thenExecute(()->new UnitActionItem(f.owner,UnitAction.STOP,-1,new int[]{s.mob().getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel()))
          .thenIdle(20).thenExecute(()->{f.hp(target,1000,"STOP canceledcharge no damage");h.assertTrue(!AaroncosCombat.active(s)&&f.shadow()==480&&s.rts().skills().cooldownTicks(UnitSkill.AARONCOS_LEFT_PRESS)==375,"cancellation no refund orcooldown reset");f.done();});
    }
    @GameTest(templateNamespace="dreamseekers_aaroncos_skills",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void hurtCallbackOwnerAbaRejectsOuterAndRemainingRingHits(GameTestHelper h) {
        F f=new F(h,"revoke");var s=f.hand(false);var a=f.target(3,0);var b=f.target(0,3);int[] callbacks={0};
        f.guard(e->{if(AaroncosCombat.isStrike(e.getSource())&&e.getSource().getEntity()==s.mob()){callbacks[0]++;s.setOwnerName(f.enemy);s.setOwnerName(f.owner);}});
        h.startSequence().thenIdle(30).thenExecute(()->h.assertTrue(f.cast(s,s.mob())==SkillResult.SUCCESS,"ringbeforeABA"))
          .thenIdle(25).thenExecute(()->{h.assertTrue(callbacks[0]==1,"onlyfirstcallback reached before lease revoked");f.hp(a,1000,"first nofinalHP afterABA");f.hp(b,1000,"remainingtarget nothit");h.assertTrue(AaroncosShockSuppression.remainingTicks(a)==0&&AaroncosShockSuppression.remainingTicks(b)==0,"revoked exact impact cannot install right suppression");h.assertTrue(f.shadow()==470,"interrupted paid ring not refunded");f.done();});
    }
    @GameTest(templateNamespace="dreamseekers_aaroncos_skills",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void identicalDamageSourceReentryCannotForgeAnExtraHit(GameTestHelper h) {
        F f=new F(h,"reentry");var s=f.hand(true);var a=f.target(5,0);var b=f.target(5,2);boolean[] nested={false};DamageSource[] seen={null};
        f.guard(e->{if(AaroncosCombat.isStrike(e.getSource())&&e.getSource().getEntity()==s.mob()&&seen[0]==null){seen[0]=e.getSource();nested[0]=b.hurt(e.getSource(),999);}});
        h.startSequence().thenIdle(30).thenExecute(()->h.assertTrue(f.cast(s,a)==SkillResult.SUCCESS,"left starts"))
          .thenIdle(15).thenExecute(()->{h.assertTrue(seen[0]!=null&&!nested[0],"same privateDS on unplannedtarget refused");f.hp(a,954.5f,"originaltarget keeps legitimatehit");f.hp(b,1000,"nestedtarget untouched");h.assertTrue(!a.hurt(seen[0],999),"retainedDS rejected outside exact invocation");f.done();});
    }
    @GameTest(templateNamespace="dreamseekers_aaroncos_skills",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=110)
    public static void ordinaryArmorAbsorptionAndGelApplyOnce(GameTestHelper h) {
        F f=new F(h,"defenses");var s=f.hand(true);var target=f.target(5,0);var gel=f.add(DreamseekersMod.PINK_SLIME_UNIT.get().create(h.getLevel()),f.enemy,5,3);
        target.getAttribute(Attributes.ARMOR).setBaseValue(5);target.setAbsorptionAmount(5);double[] expected={0};
        h.startSequence().thenIdle(30).thenExecute(()->{
            try(var other=new PassiveIncomePeer(h.getLevel(),f.enemy)) {
                h.assertTrue(SkillService.request(other.actor,new SkillRequest(UnitSkill.GEL_GUARD,gel.getId(),gel.getUUID(),gel.rts().skills().revision(),target.getId(),target.getUUID()))==SkillResult.SUCCESS,"realenemyGel applied");
            }
            expected[0]=45.5*(1-target.getUnitPhysicalArmorPercentage())*(1-target.getUnitResistPercentage())-5-12;
            h.assertTrue(expected[0]>0,"positiveactual physical remainder");h.assertTrue(f.cast(s,target)==SkillResult.SUCCESS,"castagainstrealdefenses");
        }).thenIdle(15).thenExecute(()->{f.hp(target,(float)(1000-expected[0]),"armor absorption Gel each once");h.assertTrue(target.getAbsorptionAmount()==0&&SkillEffects.shieldRemaining(target)==0,"shields consumed not bypassed");f.done();});
    }
    @GameTest(templateNamespace="dreamseekers_aaroncos_skills",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void rightDoesNotRetargetToNewArrivalDuringWindup(GameTestHelper h) {
        F f=new F(h,"fixed");var s=f.hand(false);var old=f.target(3,0);var outsider=f.target(9,0);
        h.startSequence().thenIdle(30).thenExecute(()->h.assertTrue(f.cast(s,s.mob())==SkillResult.SUCCESS,"rightstarted"))
          .thenIdle(10).thenExecute(()->{old.setPos(f.center.getX()+10.5,100,f.center.getZ()+.5);outsider.setPos(f.center.getX()+3.5,100,f.center.getZ()+.5);})
          .thenIdle(15).thenExecute(()->{f.hp(old,1000,"originaloutofrange nothit");f.hp(outsider,1000,"newarrival notaddedtocapturedlist");h.assertTrue(AaroncosShockSuppression.remainingTicks(old)==0&&AaroncosShockSuppression.remainingTicks(outsider)==0,"missed captured target and new arrival have no receipt-based suppression");h.assertTrue(f.shadow()==470,"canceled afterpayment notrefund");f.done();});
    }
    @GameTest(templateNamespace="dreamseekers_aaroncos_skills",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=110)
    public static void sourceAccountReplacementCancelsPaidWindup(GameTestHelper h) {
        F f=new F(h,"epoch");var s=f.hand(true);var target=f.target(5,0);
        h.startSequence().thenIdle(30).thenExecute(()->h.assertTrue(f.cast(s,target)==SkillResult.SUCCESS,"paidbeforeepochchange"))
          .thenIdle(5).thenExecute(()->{ResourcesServerEvents.resourcesList.remove(f.account);var next=new Resources(f.owner,1000,1000,1000);next.extra.put(DreamPassiveEconomy.SHADOW,500);ResourcesServerEvents.resourcesList.add(next);})
          .thenIdle(15).thenExecute(()->{f.hp(target,1000,"new same-name account cannot authorizeoldcharge");h.assertTrue(ExtendedResourcesSync.account(f.owner).extra.get(DreamPassiveEconomy.SHADOW)==500,"new account untouched");f.done();});
    }

    @GameTest(templateNamespace="dreamseekers_aaroncos_skills",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=110)
    public static void currentResearchAndWeaponAwareNormalBaselineMultipliesOnce(GameTestHelper h){
        F f=new F(h,"baseline");var s=f.hand(true);var ordinary=f.target(5,2);var skill=f.target(5,0);
        s.mob().getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(24);
        s.mob().setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
        double[] normal={0};h.startSequence().thenIdle(30).thenExecute(()->{
            h.assertTrue(s.mob().doHurtTarget(ordinary),"actual native ordinary attack succeeds");normal[0]=1000-ordinary.getHealth();
            h.assertTrue(normal[0]>24,"real equipped weapon counted by ordinary pipeline");
            h.assertTrue(f.cast(s,skill)==SkillResult.SUCCESS,"real paid skill after ordinarybaseline");
        }).thenIdle(15).thenExecute(()->{f.hp(skill,(float)(1000-normal[0]*1.75),"175 percent current ordinary baseline, not raw vanilla hurt amount");f.done();});
    }
    @GameTest(templateNamespace="dreamseekers_aaroncos_skills",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=110)
    public static void sameTargetRecursiveHurtRevokesEntireExactImpact(GameTestHelper h){
        F f=new F(h,"sameentry");var s=f.hand(true);var t=f.target(5,0);boolean[] called={false};
        f.guard(e->{if(AaroncosCombat.isStrike(e.getSource())&&e.getEntity()==t&&!called[0]){called[0]=true;h.assertTrue(!t.hurt(e.getSource(),999),"same target recursive hurt rejected");}});
        h.startSequence().thenIdle(30).thenExecute(()->h.assertTrue(f.cast(s,t)==SkillResult.SUCCESS,"castbefore recursivecallback"))
          .thenIdle(15).thenExecute(()->{h.assertTrue(called[0],"real final damage event reached");f.hp(t,1000,"revoked repeated exact invocation cannot finish outer HP write");f.done();});
    }
    @GameTest(templateNamespace="dreamseekers_aaroncos_skills",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=110)
    public static void actualFatalRingCreditsOnlyCommittedVictimOnce(GameTestHelper h){
        F f=new F(h,"kill");var s=f.hand(false);var a=f.target(3,0);var b=f.target(0,3);f.target(10,10);
        var probe=new ActiveSkillEconomicGameTests.ActiveSkillEconomicSubscriber(h);probe.watch(a,b);probe.cancel.add(b);f.listeners.add(probe);
        h.startSequence().thenIdle(30).thenExecute(()->{a.setHealth(1);b.setHealth(1);h.assertTrue(f.cast(s,s.mob())==SkillResult.SUCCESS,"real paid fatal ring");})
          .thenIdle(25).thenExecute(()->{probe.one(a,"AaroncosCombat");probe.zero(b);h.assertTrue(f.account.ore==1001,"one exact confirmed kill credit, canceled target none");h.assertTrue(f.shadow()==470,"skillcost not refunded or accidentally repaid by faction");ActiveSkillEconomicGameTests.PROBES.remove(probe);f.done();});
    }
    @GameTest(templateNamespace="dreamseekers_aaroncos_skills",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=110)
    public static void nonRtsPlayersOutOfRangeAndTerrainAreNeverValidTargets(GameTestHelper h){
        F f=new F(h,"bounds");var s=f.hand(true);var far=f.target(8,0);var near=f.target(5,0);
        h.startSequence().thenIdle(30).thenExecute(()->{
            h.assertTrue(f.cast(s,f.peer.actor)==SkillResult.INVALID_TARGET,"player cannot be attacked/grabbed");h.assertTrue(f.cast(s,far)==SkillResult.INVALID_TARGET,"outside6 AABB rejected");
            h.assertTrue(f.shadow()==500,"invalid starts cost zero");h.assertTrue(f.cast(s,near)==SkillResult.SUCCESS,"legal targetstarts");
        }).thenIdle(15).thenExecute(()->{h.assertTrue(h.getLevel().getBlockState(f.center.below()).is(Blocks.STONE),"ring/strike never mutates ground");f.hp(far,1000,"outside target unaffected");f.done();});
    }
}
