// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.SkillNetwork;
import com.dreamseekers.skill.SkillResult;
import com.dreamseekers.spore.JungleSporeCarrier;
import com.dreamseekers.spore.SporeEffects;
import com.dreamseekers.spore.SporeLease;
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
        var threat=f.spawn(BONE_WING_UNIT.get(),f.foe,19,10);
        f.stationary(fox);f.stationary(patient);f.stationary(threat);
        long[] cleanseAt={-1};
        boolean[] targetlessAtRemoval={false};
        double[] rangesAtRemoval={Double.NaN,Double.NaN,Double.NaN};
        Consumer<MobEffectEvent.Remove> clock=e->{
            if(e.getEntity()==patient&&e.getEffect()==MobEffectRegistrar.STUN.get()) {
                cleanseAt[0]=f.level.getGameTime();
                var live=SporeLease.activeFor(fox);
                if(live!=null) {
                    rangesAtRemoval[0]=fox.distanceToSqr(live);
                    rangesAtRemoval[1]=live.distanceToSqr(patient);
                    rangesAtRemoval[2]=fox.distanceToSqr(patient);
                    targetlessAtRemoval[0]=patient.getTargetGoal().getTarget()==null&&patient.getLastHurtByMob()==null;
                }
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL,clock);
        f.cleanups.add(()->MinecraftForge.EVENT_BUS.unregister(clock));
        f.ready().thenExecute(()->{
            h.assertTrue(patient.addEffect(new MobEffectInstance(MobEffectRegistrar.STUN.get(),420)),"finite native control installed");
            fox.setNoAi(false);
        }).thenIdle(23).thenExecute(()->{
            h.assertTrue(patient.getTargetGoal().getTarget()==null&&patient.getLastHurtByMob()==null
                    &&patient.hasEffect(MobEffectRegistrar.STUN.get())&&fox.distanceToSqr(patient)>100
                    &&fox.rts().skills().cooldownTicks(FOX_DREAM_SEED)==0,
                    "debuff by itself is not an engagement; the immobilized patient's feet are outside the ten-block cast");
            f.focus(threat,patient);
            h.assertTrue(patient.getTargetGoal().getTarget()==null,"patient did not receive an attack order");
        }).thenWaitUntil(()->h.assertTrue(SporeLease.activeFor(fox)!=null,"actual adversary targeting the incapacitated ally triggered a paid rescue"))
          .thenExecute(()->{
              var spore=SporeLease.activeFor(fox);
              h.assertTrue(!patient.hasEffect(MobEffectRegistrar.STUN.get())&&cleanseAt[0]>=0
                      &&SporeEffects.cleanseProtectionTicks(patient)==SporeLease.CLEANSE_PROTECTION_TICKS-(f.level.getGameTime()-cleanseAt[0])
                      &&rangesAtRemoval[0]<=100&&rangesAtRemoval[1]<=9&&rangesAtRemoval[2]>100&&targetlessAtRemoval[0],
                      "no victim attack/receipt required; lawful intermediate ground, real cleanse: stun="+patient.hasEffect(MobEffectRegistrar.STUN.get())
                      +" protection="+SporeEffects.cleanseProtectionTicks(patient)+" now="+f.level.getGameTime()+" remove="+cleanseAt[0]
                      +" d2AtRemove="+java.util.Arrays.toString(rangesAtRemoval)+" targetlessAtRemove="+targetlessAtRemoval[0]
                      +" postD2="+fox.distanceToSqr(spore)+"/"+spore.distanceToSqr(patient)+"/"+fox.distanceToSqr(patient)
                      +" postTarget="+patient.getTargetGoal().getTarget()+" postAttacker="+patient.getLastHurtByMob());
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
