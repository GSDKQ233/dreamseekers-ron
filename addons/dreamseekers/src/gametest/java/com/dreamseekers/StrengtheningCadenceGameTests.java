// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.AaroncosCombat;
import com.dreamseekers.skill.AaroncosShockSuppression;
import com.dreamseekers.skill.BuffKind;
import com.dreamseekers.skill.SkillEffects;
import com.dreamseekers.skill.SkillResult;
import com.dreamseekers.skill.UnitSkill;
import com.dreamseekers.unit.BeetleMotherUnit;
import com.dreamseekers.unit.BeetleUnit;
import com.dreamseekers.unit.GoldenFoxUnit;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.AbstractMeleeAttackUnitGoal;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/** Real installed melee goals and post-return HP writes, not direct doHurtTarget or goal.tick calls.
 * NoAI only contains fixture setup; each measured actor uses normal server AI throughout sampling.
 * The one reflected field is READ ONLY. Neither cooldown, iframe nor product effect state is set.
 * Core's existing dynamic cap is retained and explicitly observed at shock expiry.
 */
@GameTestHolder("dreamseekers_strengthening_cadence")
@PrefixGameTestTemplate(false)
public final class StrengtheningCadenceGameTests {
    private static final String BATCH="strengthening_cadence";
    private static final List<AaroncosSkillCombatGameTests.F> FIXTURES=new ArrayList<>();
    private static final List<Object> PROBES=new ArrayList<>();
    private static final Field REMAINING=remainingField();
    private static Field remainingField() {
        try { Field field=AbstractMeleeAttackUnitGoal.class.getDeclaredField("ticksUntilNextAttack");field.setAccessible(true);return field; }
        catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}
    }
    private static int remaining(Mob body) {
        Object goal=((AttackerUnit)body).getAttackGoal();
        if(!(goal instanceof AbstractMeleeAttackUnitGoal))throw new AssertionError("Expected installed original melee goal");
        try{return REMAINING.getInt(goal);}catch(IllegalAccessException failure){throw new RuntimeException(failure);}
    }
    private static void close(GameTestHelper h,double actual,double expected,String why) {
        h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<.0002,why+" actual="+actual+" expected="+expected);
    }
    private static AaroncosSkillCombatGameTests.F fixture(GameTestHelper h,String name) {
        var f=new AaroncosSkillCombatGameTests.F(h,"API52Cadence_"+name);FIXTURES.add(f);return f;
    }
    private static MeleeProbe watch(AaroncosSkillCombatGameTests.F f,Mob actor,LivingEntity victim,Mob right) {
        var probe=new MeleeProbe(f.h,actor,victim,right);PROBES.add(probe);MinecraftForge.EVENT_BUS.register(probe);return probe;
    }
    private static void finish(AaroncosSkillCombatGameTests.F f,MeleeProbe probe) {
        MinecraftForge.EVENT_BUS.unregister(probe);PROBES.remove(probe);FIXTURES.remove(f);f.done();
    }
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) {
        for(var probe:List.copyOf(PROBES))MinecraftForge.EVENT_BUS.unregister(probe);PROBES.clear();
        for(var f:List.copyOf(FIXTURES))f.close();FIXTURES.clear();
    }
    private static void order(AaroncosSkillCombatGameTests.F f,Mob actor,LivingEntity victim) {
        new UnitActionItem(((Unit)actor).getOwnerName(),UnitAction.ATTACK,victim.getId(),new int[]{actor.getId()},BlockPos.ZERO,BlockPos.ZERO).action(f.h.getLevel());
    }
    private static void selected(GameTestHelper h,Mob actor,LivingEntity victim) {
        h.assertTrue(actor.tickCount>3&&actor.isAddedToWorld()&&h.getLevel().getEntity(actor.getUUID())==actor
                &&h.getLevel().getEntity(actor.getId())==actor&&!UnitServerEvents.hasPendingImmediateAction(actor.getId())
                &&((Unit)actor).getTargetGoal().getTarget()==victim,"real command and loaded original melee target");
    }
    private static void start(MeleeProbe probe) {
        ((Unit)probe.actor).setHoldPosition(true);probe.enabled=true;probe.actor.setNoAi(false);
    }
    private static void body(Mob mob) {
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);mob.setHealth(1000);
        mob.getAttribute(Attributes.ARMOR).setBaseValue(0);mob.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
        mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);mob.setAbsorptionAmount(0);
        for(var a:List.of(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(),AttributeRegistrar.RANGED_DAMAGE_RESIST.get()))
            if(mob.getAttribute(a)!=null)mob.getAttribute(a).setBaseValue(0);
    }

    @GameTest(templateNamespace="dreamseekers_strengthening_cadence",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=500)
    public static void naturalBeetleMeleeHpTimesFollowMotherEntryAndWithdrawalWithoutTimerWrites(GameTestHelper h) {
        var f=fixture(h,"mother");
        BeetleMotherUnit mother=f.add(DreamseekersMod.BEETLE_MOTHER_UNIT.get().create(h.getLevel()),f.owner,-12,0);
        BeetleUnit actor=f.add(DreamseekersMod.BEETLE.get().create(h.getLevel()),f.owner,0,0);
        GoldenFoxUnit victim=f.target(1.1,0);var probe=watch(f,actor,victim,null);
        h.startSequence().thenIdle(30).thenExecute(()->{
            close(h,actor.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getBaseValue(),.8,"unmodified registered APS");
            close(h,SkillEffects.bonus(actor,BuffKind.ATTACK_SPEED),0,"mother genuinely outside six-block range");order(f,actor,victim);
        }).thenWaitUntil(()->selected(h,actor,victim)).thenExecute(()->start(probe))
          .thenWaitUntil(()->h.assertTrue(probe.count("base")>=3,"three actual baseline melee HP losses"))
          .thenExecute(()->{
              probe.intervals("base",25,3);probe.phase="transition";
              mother.moveTo(f.center.getX()-2.5,100,f.center.getZ()+.5);
          }).thenWaitUntil(()->close(h,SkillEffects.bonus(actor,BuffKind.ATTACK_SPEED),.15,"natural discovery of legitimate mother aura"))
          .thenExecute(()->probe.phase="aura")
          .thenWaitUntil(()->h.assertTrue(probe.count("aura")>=3,"three actual boosted melee HP losses"))
          .thenExecute(()->{
              probe.intervals("aura",21,3);probe.phase="transition";
              mother.moveTo(f.center.getX()-11.5,100,f.center.getZ()+.5);
              close(h,SkillEffects.bonus(actor,BuffKind.ATTACK_SPEED),0,"live range withdrawal before another APS read");probe.phase="withdrawn";
          }).thenWaitUntil(()->h.assertTrue(probe.count("withdrawn")>=3,"three actual withdrawn-aura melee HP losses"))
          .thenExecute(()->{
              probe.intervals("withdrawn",25,3);close(h,actor.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getBaseValue(),.8,"base never rewritten");
              h.assertTrue(f.shadow()==500&&f.account.food==1000&&f.account.wood==1000&&f.account.ore==1000,"passive never spends or grants resources");
              System.out.println("STRENGTHENING_NATURAL_MOTHER_MELEE_HP_TIMES "+probe.hits);finish(f,probe);
          });
    }

    @GameTest(templateNamespace="dreamseekers_strengthening_cadence",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=600)
    public static void paidRightShockChangesNaturalMilitiaMeleeResetOnceAndNativeExpiryCapRemains(GameTestHelper h) {
        var f=fixture(h,"right");var right=f.hand(false);
        var actor=f.add(EntityRegistrar.MILITIA_UNIT.get().create(h.getLevel()),f.enemy,3,0);actor.swapWeapons(false);body(actor);
        var victim=f.add(DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel()),f.owner,4.1,0);body(victim);
        var probe=watch(f,actor,victim,right.mob());
        h.startSequence().thenIdle(30).thenExecute(()->{
            h.assertTrue(!actor.isUsingBow(),"original sword melee mode");close(h,actor.getAttackCooldown(),40,"original constant-based Militia float baseline");order(f,actor,victim);
        }).thenWaitUntil(()->selected(h,actor,victim)).thenExecute(()->start(probe))
          .thenWaitUntil(()->h.assertTrue(probe.count("before")>=3,"three actual unsuppressed Militia hits"))
          .thenExecute(()->{
              probe.intervals("before",40,3);probe.castAt=h.getLevel().getGameTime();
              h.assertTrue(f.cast(right,right.mob())==SkillResult.SUCCESS&&f.shadow()==470,"genuine authenticated right ring spends thirty shadow once");
          }).thenWaitUntil(()->h.assertTrue(probe.impactAt>=0&&probe.count("suppressed")>=2,"two naturally scheduled melee hits inside the 80-tick suppression"))
          .thenExecute(()->{
              close(h,probe.impactAfter,probe.impactBefore-54,"post-return actual right HP loss, not event amount");
              h.assertTrue(probe.impactAt==probe.castAt+20,"natural original twenty-tick windup");
              probe.intervals("suppressed",56,2); // Actual attack times reflect 40 x 1.40 once, not twice.
          }).thenWaitUntil(()->h.assertTrue(probe.expiryAt>=0&&probe.count("after")>=3,"original deadline and three later unsuppressed natural hits"))
          .thenExecute(()->{
              probe.intervals("after",40,3);
              h.assertTrue(probe.expiryAt==probe.impactAt+80,"eighty-tick absolute effect deadline");
              Hit firstAfter=probe.select("after").get(0);
              h.assertTrue(firstAfter.at()==probe.expiryAt+probe.expiryRemaining+1,"first post-expiry hit follows observed native remaining countdown, no invented immutable-CD contract");
              h.assertTrue(AaroncosShockSuppression.remainingTicks(actor)==0&&f.shadow()==470
                      &&right.rts().skills().cooldownTicks(UnitSkill.AARONCOS_RIGHT_SHOCK)==600-(h.getLevel().getGameTime()-probe.castAt),"no effect replay, cost refund or original paid cooldown reset");
              System.out.println("STRENGTHENING_NATURAL_RIGHT_MILITIA_HP_TIMES "+probe.hits+" impact="+probe.impactAt+" expiry="+probe.expiryAt+" expiryRemaining="+probe.expiryRemaining);finish(f,probe);
          });
    }

    @GameTest(templateNamespace="dreamseekers_strengthening_cadence",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=220)
    public static void continuousSameMotherSurvivesOldLeaseBoundariesWithoutAnotherMotherStealing(GameTestHelper h) {
        var f=fixture(h,"leaseboundary");
        var first=f.add(DreamseekersMod.BEETLE_MOTHER_UNIT.get().create(h.getLevel()),f.owner,-12,0);
        var target=f.add(DreamseekersMod.BEETLE.get().create(h.getLevel()),f.owner,0,0);
        var probe=new AuraBoundaryProbe(h,target);PROBES.add(probe);MinecraftForge.EVENT_BUS.register(probe);
        h.startSequence().thenIdle(30).thenExecute(()->{
            close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),0,"first source actually outside range");
            probe.enabled=true;first.moveTo(f.center.getX()-2.5,100,f.center.getZ()+.5);
        }).thenWaitUntil(()->h.assertTrue(probe.firstAt>=0,"first legitimate END discovery observed before creating competitor"))
          .thenExecute(()->f.add(DreamseekersMod.BEETLE_MOTHER_UNIT.get().create(h.getLevel()),f.owner,3,0))
          .thenWaitUntil(()->h.assertTrue(h.getLevel().getGameTime()>probe.firstAt+60,"complete original sixty-tick pre-END observation window"))
          .thenExecute(()->{
              h.assertTrue(probe.boundaries.equals(List.of(probe.firstAt+20,probe.firstAt+40,probe.firstAt+60)),"exact old deadline windows, never wait until END repairs a gap");
              h.assertTrue(probe.samples.size()==60,"every intervening entity tick must be observed, not just successful boundary samples: "+probe.samples);
              for(int i=0;i<probe.samples.size();i++) {
                  AuraSample sample=probe.samples.get(i);
                  h.assertTrue(sample.at()==probe.firstAt+i+1&&sample.failure()==null,"exact uninterrupted sample timeline without a swallowed callback error: "+probe.samples);
                  close(h,sample.bonus(),.15,"recorded legitimate mother bonus before END at "+sample.at()+" all="+probe.samples);
                  close(h,sample.aps(),.92,"recorded real public APS before END cannot temporarily fall to baseline at "+sample.at());
              }
              probe.enabled=false;first.discard();
              close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),0,"other legal mother could neither stack nor steal the still-valid original source");
          }).thenWaitUntil(()->close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),.15,"next legal discovery may replace a dead source with the surviving mother"))
          .thenExecute(()->{
              close(h,target.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getBaseValue(),.8,"renewal never rewrites registered base");
              h.assertTrue(f.shadow()==500,"passive rediscovery does not spend or refund any summon payment");
              System.out.println("STRENGTHENING_MOTHER_PRE_END_LEASE_BOUNDARIES first="+probe.firstAt+" boundaries="+probe.boundaries+" allSamples="+probe.samples);
              MinecraftForge.EVENT_BUS.unregister(probe);PROBES.remove(probe);FIXTURES.remove(f);f.done();
          });
    }
    private record AuraSample(long at,double bonus,double aps,String failure) { }
    private static final class AuraBoundaryProbe {
        final GameTestHelper h;final BeetleUnit target;final List<Long> boundaries=new ArrayList<>();
        final List<AuraSample> samples=new ArrayList<>();
        boolean enabled;long firstAt=-1;
        AuraBoundaryProbe(GameTestHelper h,BeetleUnit target){this.h=h;this.target=target;}
        @SubscribeEvent(priority=EventPriority.LOWEST)
        public void discovered(TickEvent.LevelTickEvent event) {
            if(!enabled||firstAt>=0||event.phase!=TickEvent.Phase.END||event.level!=h.getLevel())return;
            if(Math.abs(SkillEffects.bonus(target,BuffKind.ATTACK_SPEED)-.15)<.0001)firstAt=event.level.getGameTime();
        }
        @SubscribeEvent(priority=EventPriority.HIGHEST)
        public void beforeOrdinaryTick(LivingEvent.LivingTickEvent event) {
            if(!enabled||firstAt<0||event.getEntity()!=target)return;
            long now=h.getLevel().getGameTime();if(now<=firstAt||now>firstAt+60)return;
            // Observe at exactly the original pre-END time, but let the GameTest sequence own
            // assertions. Throwing a test assertion out of a Forge entity callback can crash the
            // whole server instead of recording one failed test. Failed samples are NEVER dropped.
            try { samples.add(new AuraSample(now,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),target.getAttacksPerSecond(),null)); }
            catch(RuntimeException|AssertionError failure) { samples.add(new AuraSample(now,Double.NaN,Double.NaN,failure.getClass().getName()+": "+failure.getMessage())); }
            if(now==firstAt+20||now==firstAt+40||now==firstAt+60)boundaries.add(now);
        }
    }

    private record Hit(long at,String phase,int reset,int endRemaining,float interval,float before,float after) { }
    private record Pending(long at,String phase,int reset,float interval,float before) { }
    private static final class MeleeProbe {
        final GameTestHelper h;final Mob actor;final LivingEntity victim;final Mob right;final List<Hit> hits=new ArrayList<>();
        boolean enabled;String phase="base";Pending pending;
        long castAt=-1,impactAt=-1,impactPendingAt=-1,expiryAt=-1,previousAt=-1;
        float impactBefore,impactAfter;int expiryRemaining,previousRemaining;boolean previousShock;
        MeleeProbe(GameTestHelper h,Mob actor,LivingEntity victim,Mob right){this.h=h;this.actor=actor;this.victim=victim;this.right=right;}
        List<Hit> select(String phase){return hits.stream().filter(hit->hit.phase().equals(phase)).toList();}
        int count(String phase){return select(phase).size();}
        void intervals(String phase,int expected,int minimum) {
            List<Hit> selected=select(phase);h.assertTrue(selected.size()>=minimum,"enough actual HP writes for "+phase);
            for(int i=0;i<selected.size();i++) {
                Hit hit=selected.get(i);h.assertTrue(hit.reset()==expected&&hit.after()<hit.before(),"real reset and HP loss for "+phase+": "+hit+" all="+hits);
                if(i>0)h.assertTrue(hit.at()-selected.get(i-1).at()==expected,"natural actual HP timestamps for "+phase+": "+selected);
            }
        }
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
        public void damage(LivingDamageEvent event) {
            if(right!=null&&event.getEntity()==actor&&event.getSource().getEntity()==right&&AaroncosCombat.isStrike(event.getSource())) {
                h.assertTrue(impactAt<0&&impactPendingAt<0,"one actual skill impact only");impactBefore=actor.getHealth();impactPendingAt=h.getLevel().getGameTime();
            }
            if(!enabled||event.getEntity()!=victim)return;
            h.assertTrue(event.getSource().getEntity()==actor&&event.getSource().getDirectEntity()==actor&&!AaroncosCombat.isStrike(event.getSource()),"all measured victim damage comes from original ordinary melee actor");
            h.assertTrue(!actor.isNoAi()&&actor.getTarget()==victim&&pending==null,"one naturally AI-scheduled hit per original actor per tick");
            boolean shock=right!=null&&AaroncosShockSuppression.remainingTicks(actor)>0;
            double aura=right==null?SkillEffects.bonus(actor,BuffKind.ATTACK_SPEED):0;
            h.assertTrue(right!=null||aura==0||Math.abs(aura-.15)<.0001,"only real mother fifteen-percent APS participates");
            float expected=right==null?(float)(20/(.8*(1+aura))):40*(shock?1.4f:1);
            float interval=((AttackerUnit)actor).getAttackCooldown();close(h,interval,expected,"actual cadence getter matches independent baseline/category calculation");
            int reset=remaining(actor);h.assertTrue(reset==(int)expected,"real installed melee goal resets exactly once before hurt: expected="+(int)expected+" actual="+reset);
            String current=right==null?phase:shock?"suppressed":impactAt<0?"before":"after";
            pending=new Pending(h.getLevel().getGameTime(),current,reset,interval,victim.getHealth());
        }
        @SubscribeEvent(priority=EventPriority.LOWEST)
        public void end(TickEvent.LevelTickEvent event) {
            if(event.phase!=TickEvent.Phase.END||event.level!=h.getLevel()||!enabled)return;
            long now=event.level.getGameTime();int actualRemaining=remaining(actor);
            if(impactPendingAt>=0) {
                h.assertTrue(now==impactPendingAt,"first same-impact Level END captures completed HP write");impactAt=impactPendingAt;impactPendingAt=-1;impactAfter=actor.getHealth();
                close(h,impactAfter,impactBefore-54,"genuine 54-point loss installs right suppression");
                h.assertTrue(AaroncosShockSuppression.remainingTicks(actor)==80,"fresh paid HP receipt has eighty ticks");
            }
            boolean shock=right!=null&&AaroncosShockSuppression.remainingTicks(actor)>0;
            if(pending!=null) {
                h.assertTrue(pending.at()==now&&victim.getHealth()<pending.before(),"first same-hit Level END sees genuine positive HP loss");
                h.assertTrue(actualRemaining==Math.max(0,pending.reset()-1),"normal AttackerUnit.tick consumes one real cooldown tick after the natural hit");
                hits.add(new Hit(now,pending.phase(),pending.reset(),actualRemaining,pending.interval(),pending.before(),victim.getHealth()));pending=null;
            } else if(right!=null&&previousShock&&!shock&&previousAt==now-1) {
                h.assertTrue(expiryAt<0&&impactAt>=0&&now==impactAt+80,"only natural eighty-tick expiry");
                int nativeCap=40;
                int expected=Math.max(0,(previousRemaining>nativeCap?nativeCap:previousRemaining)-1);
                h.assertTrue(actualRemaining==expected,"unmodified native dynamic cap/decrement at expiry: before="+previousRemaining+" expected="+expected+" actual="+actualRemaining);
                expiryAt=now;expiryRemaining=actualRemaining;
            }
            previousAt=now;previousRemaining=actualRemaining;previousShock=shock;
        }
    }
}
