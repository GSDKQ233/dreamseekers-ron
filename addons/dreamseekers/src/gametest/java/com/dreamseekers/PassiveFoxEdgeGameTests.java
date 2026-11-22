// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.CoordinatedMark;
import com.dreamseekers.skill.SkillNetwork;
import com.dreamseekers.skill.SkillResult;
import com.dreamseekers.spore.JungleSporeCarrier;
import com.dreamseekers.spore.SporeEffects;
import com.dreamseekers.spore.SporeLease;
import com.dreamseekers.unit.BoneWingUnit;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.dreamseekers.DreamseekersMod.*;
import static com.dreamseekers.skill.UnitSkill.*;

@GameTestHolder("dreamseekers_passive_edges")
@PrefixGameTestTemplate(false)
public final class PassiveFoxEdgeGameTests {
    private static final String BATCH="passive_fox_edges", ARENA="unit_isolation_arena";
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) { AutocastGameTests.cleanup(level); }

    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=160)
    public static void stunnedPatientOutsideCastRadiusWithNoAttackFocusIsSavedFromLiveThreat(GameTestHelper h) {
        var f=new AutocastGameTests.F(h,"FoxEdgesA");
        var fox=f.spawn(GOLDEN_FOX_UNIT.get(),f.own,5,10);
        var patient=f.spawn(BONE_WING_UNIT.get(),f.ally,16,10);
        f.stationary(fox);f.stationary(patient);
        var stun=new MobEffectInstance(MobEffectRegistrar.STUN.get(),420);
        var state=fox.rts().skills();
        double castRadiusSq=(double)SporeLease.CAST_RANGE*SporeLease.CAST_RANGE;
        double auraRadiusSq=SporeLease.RADIUS*SporeLease.RADIUS;
        BoneWingUnit[] threat={null};
        long[] orderAt={-1}, revision={-1};
        float[] healthBefore={Float.NaN};
        record CleanseSample(long tick,boolean incomingFocus,boolean noAttackOrHit,
                             double casterToSporeSq,double sporeToPatientSq,double casterToPatientSq) {}
        List<CleanseSample> removals=new ArrayList<>();
        Consumer<MobEffectEvent.Remove> clock=e->{
            if(e.getEntity()!=patient||e.getEffect()!=stun.getEffect()||patient.getEffect(stun.getEffect())!=stun)return;
            var carrier=SporeLease.activeFor(fox);
            var enemy=threat[0];
            if(carrier==null)return;
            removals.add(new CleanseSample(f.level.getGameTime(),
                    enemy!=null&&enemy.isAlive()&&!enemy.isNoAi()&&enemy.getTarget()==patient
                            &&CoordinatedMark.visible(f.peer.actor,enemy)&&enemy.hasLineOfSight(patient),
                    patient.getTarget()==null&&patient.getLastHurtByMob()==null&&patient.getHealth()==healthBefore[0],
                    fox.distanceToSqr(carrier),carrier.distanceToSqr(patient),fox.distanceToSqr(patient)));
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL,clock);
        f.cleanups.add(()->MinecraftForge.EVENT_BUS.unregister(clock));
        f.ready().thenExecute(()->{
            h.assertTrue(patient.addEffect(stun)&&patient.getEffect(stun.getEffect())==stun,"finite native control installed");
            revision[0]=state.revision();
            fox.setNoAi(false);
        }).thenIdle(23).thenExecute(()->{
            h.assertTrue(patient.getTarget()==null&&patient.getLastHurtByMob()==null
                    &&patient.getEffect(stun.getEffect())==stun&&fox.distanceToSqr(patient)>castRadiusSq
                    &&SporeLease.activeFor(fox)==null&&state.cooldownTicks(FOX_DREAM_SEED)==0&&state.revision()==revision[0],
                    "idle original STUN alone does not spend; d2="+fox.distanceToSqr(patient)
                            +" stun="+patient.getEffect(stun.getEffect())+" target="+patient.getTarget()
                            +" attacker="+patient.getLastHurtByMob()+" cooldown="+state.cooldownTicks(FOX_DREAM_SEED));
            // No hostile body existed during the idle sample. Seven blocks leaves real windup/flight
            // time before the first bolt can land, while remaining inside native ranged reach.
            threat[0]=f.spawn(BONE_WING_UNIT.get(),f.foe,16,17);
            f.stationary(threat[0]);
        }).thenWaitUntil(()->{
            var enemy=threat[0];
            h.assertTrue(enemy.tickCount>3&&enemy.isAddedToWorld()&&f.level.getEntity(enemy.getUUID())==enemy
                    &&CoordinatedMark.visible(f.peer.actor,enemy)&&fox.hasLineOfSight(enemy)&&enemy.hasLineOfSight(patient)
                    &&enemy.distanceTo(patient)<enemy.getAttackRange(),"published hostile is visible and in real ranged reach");
        }).thenExecute(()->{
            h.assertTrue(patient.getEffect(stun.getEffect())==stun&&patient.getTarget()==null&&patient.getLastHurtByMob()==null
                    &&SporeLease.activeFor(fox)==null&&state.revision()==revision[0],"no trigger before the actual ATTACK order");
            healthBefore[0]=patient.getHealth();
            orderAt[0]=f.level.getGameTime();
            f.focus(threat[0],patient);
            threat[0].setNoAi(false);
        }).thenWaitUntil(()->h.assertTrue(SporeLease.activeFor(fox)!=null,"actual adversary targeting the incapacitated ally triggered a paid rescue"))
          .thenExecute(()->{
              h.assertTrue(removals.size()==1,"the live carrier attempted native removal of exactly the original STUN: "+removals);
              var sample=removals.get(0);
              long elapsed=f.level.getGameTime()-sample.tick();
              h.assertTrue(sample.tick()>=orderAt[0]&&sample.incomingFocus()&&sample.noAttackOrHit()
                      &&sample.casterToSporeSq()<=castRadiusSq&&sample.sporeToPatientSq()<=auraRadiusSq
                      &&sample.casterToPatientSq()>castRadiusSq&&!patient.hasEffect(stun.getEffect())
                      &&SporeEffects.cleanseProtectionTicks(patient)==SporeLease.CLEANSE_PROTECTION_TICKS-elapsed
                      &&state.cooldownTicks(FOX_DREAM_SEED)==FOX_DREAM_SEED.cooldownTicks()-elapsed&&state.revision()==revision[0]+1,
                      "incoming focus alone cleanses via reachable intermediate ground; sample="+sample+" elapsed="+elapsed
                              +" stun="+patient.hasEffect(stun.getEffect())+" protection="+SporeEffects.cleanseProtectionTicks(patient)
                              +" cooldown="+state.cooldownTicks(FOX_DREAM_SEED)+" revision="+state.revision());
              f.done();
          });
    }

    @GameTest(template=ARENA,batch=BATCH,timeoutTicks=180)
    public static void replacementOfSameDebuffDuringJoinIsNotTheSelectedNeedAndNextDecisionMayUseIt(GameTestHelper h) {
        var f=new AutocastGameTests.F(h,"FoxEdgesB");
        var fox=f.spawn(GOLDEN_FOX_UNIT.get(),f.own,6,10);
        var patient=f.spawn(BONE_WING_UNIT.get(),f.ally,10,10);
        var enemy=f.spawn(JELLYFISH_UNIT.get(),f.foe,13,10);
        f.stationary(fox);f.stationary(patient);f.stationary(enemy);
        var original=new MobEffectInstance(MobEffects.WEAKNESS,400);
        var replacement=new MobEffectInstance(MobEffects.WEAKNESS,400);
        List<JungleSporeCarrier> attempts=new ArrayList<>();
        List<Long> ticks=new ArrayList<>();
        long[] before={-1};
        Consumer<EntityJoinLevelEvent> probe=e->{
            if(e.getEntity() instanceof JungleSporeCarrier p&&p.level()==f.level&&p.distanceToSqr(patient)<=9) {
                ticks.add(f.level.getGameTime());attempts.add(p);
                if(attempts.size()==1) {
                    h.assertTrue(patient.getEffect(MobEffects.WEAKNESS)==original&&patient.removeEffect(MobEffects.WEAKNESS)
                            &&patient.addEffect(replacement)&&patient.getEffect(MobEffects.WEAKNESS)==replacement,
                            "Forge Join genuinely replaced (not refreshed) the observed debuff before birth commit");
                }
            }
        };
        // Observe the real first body's rollback immediately, independent of when the test
        // sequence next executes (the following legitimate natural decision need not wait for it).
        boolean[] rolledBack={false};
        Consumer<EntityLeaveLevelEvent> leave=e->{
            if(!attempts.isEmpty()&&e.getEntity()==attempts.get(0)&&e.getLevel()==f.level) {
                rolledBack[0]=f.level.getGameTime()==ticks.get(0)&&fox.rts().skills().cooldownTicks(FOX_DREAM_SEED)==0
                        &&fox.rts().skills().revision()==before[0]&&patient.getEffect(MobEffects.WEAKNESS)==replacement
                        &&SporeEffects.cleanseProtectionTicks(patient)==0&&!SporeLease.active(attempts.get(0));
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL,leave);
        f.cleanups.add(()->MinecraftForge.EVENT_BUS.unregister(leave));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL,probe);
        f.cleanups.add(()->MinecraftForge.EVENT_BUS.unregister(probe));
        f.ready().thenExecute(()->{
            h.assertTrue(new SkillNetwork.Toggle(FOX_BLESSING,fox.getId(),fox.getUUID(),fox.rts().skills().revision(),false)
                    .execute(f.peer.actor)==SkillResult.SUCCESS,"isolate independent blessing");
            h.assertTrue(patient.addEffect(original),"first removable negative installed");
            before[0]=fox.rts().skills().revision();
            f.focus(patient,enemy);fox.setNoAi(false);
        }).thenWaitUntil(()->h.assertTrue(rolledBack[0],
                "same-tick native Leave proves stale selection lost its carrier without spending, protection or cleansing the replacement"))
          .thenWaitUntil(()->h.assertTrue(SporeLease.activeFor(fox)!=null,"a later native decision may select the real replacement"))
          .thenExecute(()->{
              h.assertTrue(attempts.size()==2&&ticks.get(1)>ticks.get(0)&&SporeLease.activeFor(fox)==attempts.get(1)
                      &&attempts.get(0).isRemoved()&&!patient.hasEffect(MobEffects.WEAKNESS)
                      &&SporeEffects.cleanseProtectionTicks(patient)==SporeLease.CLEANSE_PROTECTION_TICKS-(f.level.getGameTime()-ticks.get(1))
                      &&fox.rts().skills().cooldownTicks(FOX_DREAM_SEED)>0&&fox.rts().skills().revision()==before[0]+1,
                      "only the fresh independent decision paid and applied; no same-tick retry or double spend");
              f.done();
          });
    }
}
