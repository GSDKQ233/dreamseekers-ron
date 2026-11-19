// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.*;
import com.dreamseekers.unitprogression.AdvancedUnitResearch;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.AbstractMeleeAttackUnitGoal;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.*;
import java.lang.reflect.Field;
import java.util.*;

/** Actual installed ordinary left-hand melee, not direct doHurtTarget or manual goal ticks.
 * Reuses API52's registered-body/peer fixture and natural damage->Level END receipt technique.
 * The protected remaining timer is observed read-only; no attack timer, iframe or effect is set.
 * This is a one-actor natural-cadence test; both hand APS getters are covered separately. */
@GameTestHolder("dreamseekers_advanced_harmony_cadence") @PrefixGameTestTemplate(false)
public final class AdvancedHandHarmonyCadenceGameTests {
    private static final String BATCH="advanced_harmony_cadence";
    private static final List<AaroncosSkillCombatGameTests.F> FIXTURES=new ArrayList<>();
    private static final List<HarmonyProbe> PROBES=new ArrayList<>();
    private static final Field REMAINING=remainingField();
    private static Field remainingField(){try{Field f=AbstractMeleeAttackUnitGoal.class.getDeclaredField("ticksUntilNextAttack");f.setAccessible(true);return f;}catch(ReflectiveOperationException ex){throw new ExceptionInInitializerError(ex);}}
    private static int remaining(Mob body){try{return REMAINING.getInt(((AttackerUnit)body).getAttackGoal());}catch(IllegalAccessException ex){throw new IllegalStateException(ex);}}
    private static void near(GameTestHelper h,double actual,double expected,String message){h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<.0002,message+" actual="+actual+" expected="+expected);}
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level){
        for(var probe:PROBES)MinecraftForge.EVENT_BUS.unregister(probe);PROBES.clear();
        for(var f:List.copyOf(FIXTURES)){ResearchServerEvents.removeAllResearchFor(f.owner);f.close();}FIXTURES.clear();
    }
    @GameTest(templateNamespace="dreamseekers_advanced_harmony_cadence",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=800)
    public static void leftNaturalHpHitTimesFollowHarmonyEntryAndDepartureWithoutTimerWrites(GameTestHelper h){
        var f=new AaroncosSkillCombatGameTests.F(h,"API54HarmonyCadence");FIXTURES.add(f);
        var controlled=f.hand(true);Mob actor=controlled.mob();
        var partner=f.add(DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get().create(h.getLevel()),f.owner,-12,0);
        var target=f.target(5,0);var probe=new HarmonyProbe(h,actor,target);PROBES.add(probe);MinecraftForge.EVENT_BUS.register(probe);
        ResearchServerEvents.addResearch(f.owner,AdvancedUnitResearch.HAND_HARMONY_ID);
        h.startSequence().thenIdle(30).thenExecute(()->{
            h.assertTrue(actor.isAddedToWorld()&&h.getLevel().getEntity(actor.getUUID())==actor&&UnitServerEvents.getAllUnits().contains(actor),"real original published hand");
            near(h,actor.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getBaseValue(),.5,"original registered hand APS");
            near(h,SkillEffects.bonus(actor,BuffKind.ATTACK_SPEED),0,"researched but partner outside8 gives no bonus");
            new UnitActionItem(f.owner,UnitAction.ATTACK,target.getId(),new int[]{actor.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());
        }).thenWaitUntil(()->h.assertTrue(controlled.getTargetGoal().getTarget()==target&&!UnitServerEvents.hasPendingImmediateAction(actor.getId()),"real ordinary attack order selected target"))
        .thenExecute(()->{controlled.setHoldPosition(true);probe.enabled=true;actor.setNoAi(false);})
        .thenWaitUntil(()->probe.requireHits("baseline",4))
        .thenExecute(()->{
            probe.assertIntervals("baseline",40,4);probe.phase="transition";
            partner.moveTo(f.center.getX()-6.5,100,f.center.getZ()+.5); // 7 center-to-center, no overlap
        }).thenWaitUntil(()->{probe.healthy();near(h,SkillEffects.bonus(actor,BuffKind.ATTACK_SPEED),.10,"natural next10tick discovery installs actual harmony");})
        .thenExecute(()->{near(h,controlled.getAttacksPerSecond(),.55,"public APS consumer positive");probe.phase="harmony";})
        .thenWaitUntil(()->probe.requireHits("harmony",4))
        .thenExecute(()->{
            probe.assertIntervals("harmony",36,4);probe.phase="transition";
            partner.moveTo(f.center.getX()-11.5,100,f.center.getZ()+.5);
            near(h,SkillEffects.bonus(actor,BuffKind.ATTACK_SPEED),0,"same-tick leave retires harmony before native next read");
            near(h,controlled.getAttacksPerSecond(),.5,"actual public APS immediately restores baseline");probe.phase="departed";
        }).thenWaitUntil(()->probe.requireHits("departed",4))
        .thenExecute(()->{
            probe.assertIntervals("departed",40,4);
            near(h,actor.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getBaseValue(),.5,"research never rewrites registered base");
            h.assertTrue(f.shadow()==500&&f.account.food==1000&&f.account.wood==1000&&f.account.ore==1000,"passive consumes/grants no resources");
            h.assertTrue(controlled.rts().skills().cooldownTicks(UnitSkill.AARONCOS_LEFT_PRESS)==0&&partner.rts().skills().cooldownTicks(UnitSkill.AARONCOS_RIGHT_SHOCK)==0,"ordinary aura never starts either independent skill cooldown");
            System.out.println("API54_HARMONY_NATURAL_LEFT_MELEE_HP_TIMES "+probe.hits);
            probe.enabled=false;MinecraftForge.EVENT_BUS.unregister(probe);PROBES.remove(probe);
            ResearchServerEvents.removeAllResearchFor(f.owner);FIXTURES.remove(f);f.done();
        });
    }
    private record Pending(long at,String phase,int reset,float interval,float before){}
    private record Hit(long at,String phase,int reset,int remaining,float interval,float before,float after){}
    private static final class HarmonyProbe {
        final GameTestHelper h;final Mob actor;final LivingEntity victim;final List<Hit> hits=new ArrayList<>();final List<String> failures=new ArrayList<>();
        boolean enabled;String phase="baseline";Pending pending;
        HarmonyProbe(GameTestHelper h,Mob actor,LivingEntity victim){this.h=h;this.actor=actor;this.victim=victim;}
        void healthy(){h.assertTrue(failures.isEmpty(),"no discarded callback errors: "+failures);}
        void requireHits(String phase,int count){healthy();h.assertTrue(hits.stream().filter(hit->hit.phase().equals(phase)).count()>=count,"waiting for "+count+" actual HP-loss receipts in "+phase+": "+hits);}
        void assertIntervals(String phase,int expected,int minimum){
            healthy();var selected=hits.stream().filter(hit->hit.phase().equals(phase)).toList();h.assertTrue(selected.size()>=minimum,"enough real hits");
            for(int i=0;i<selected.size();i++){
                var hit=selected.get(i);h.assertTrue(hit.reset()==expected&&hit.remaining()==expected-1&&hit.after()<hit.before(),"native reset, post-return HP write and native decrement: "+hit);
                near(h,hit.before()-hit.after(),26,"ordinary base damage remains26, not a skill/potency multiplier");
                // The interval crossing the phase boundary is intentionally excluded.
                // Four within-phase HP writes provide three fully stable natural intervals.
                if(i>0)h.assertTrue(hit.at()-selected.get(i-1).at()==expected,"natural actual hit interval "+expected+": "+selected);
            }
        }
        /** Never throw through the Forge event bus: retain failures for the GameTest sequence. */
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
        public void observeHarmonyNaturalDamage(LivingDamageEvent event){
            if(!enabled||event.getEntity()!=victim)return;
            try {
                if(event.isCanceled()||event.getAmount()<=0||event.getSource().getEntity()!=actor||event.getSource().getDirectEntity()!=actor||AaroncosCombat.isStrike(event.getSource()))throw new AssertionError("not one positive original ordinary melee hit");
                if(actor.isNoAi()||actor.getTarget()!=victim||pending!=null)throw new AssertionError("not original naturally scheduled single hit");
                double bonus=SkillEffects.bonus(actor,BuffKind.ATTACK_SPEED);
                if(Math.abs(bonus)>.0001&&Math.abs(bonus-.10)>.0001)throw new AssertionError("unexpected APS contributor "+bonus);
                float interval=((AttackerUnit)actor).getAttackCooldown();double expected=20/(.5*(1+bonus));
                if(Math.abs(interval-expected)>.0002)throw new AssertionError("wrong real cooldown getter "+interval+" expected "+expected);
                int reset=remaining(actor);if(reset!=(int)expected)throw new AssertionError("wrong natural reset "+reset+" expected "+(int)expected);
                pending=new Pending(h.getLevel().getGameTime(),phase,reset,interval,victim.getHealth());
            }catch(RuntimeException|AssertionError failure){failures.add("damage@"+h.getLevel().getGameTime()+":"+failure);}
        }
        @SubscribeEvent(priority=EventPriority.LOWEST)
        public void observeHarmonyNaturalHpAfterReturn(TickEvent.LevelTickEvent event){
            if(!enabled||event.level!=h.getLevel()||event.phase!=TickEvent.Phase.END||pending==null)return;
            Pending hit=pending;pending=null;
            try {
                int nowRemaining=remaining(actor);float hp=victim.getHealth();
                if(hit.at()!=event.level.getGameTime()||hp>=hit.before()||nowRemaining!=Math.max(0,hit.reset()-1))throw new AssertionError("missing actual HP write or altered native countdown");
                hits.add(new Hit(hit.at(),hit.phase(),hit.reset(),nowRemaining,hit.interval(),hit.before(),hp));
            }catch(RuntimeException|AssertionError failure){failures.add("end@"+h.getLevel().getGameTime()+":"+failure);}
        }
    }
}
