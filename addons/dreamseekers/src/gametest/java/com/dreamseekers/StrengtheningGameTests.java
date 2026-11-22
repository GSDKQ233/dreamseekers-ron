// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.aaroncos.ControlledAaroncosHand;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.resources.kill.RtsKillValueClaims;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Candidate API52 integration tests. Authenticated casts, real hurt and natural server deadlines.
 * NoAI deliberately isolates skill/aura arithmetic; it is NOT evidence of ordinary attack cadence.
 * The existing full Squeal cadence suite remains mandatory for the shared reset-time adapter.
 */
@GameTestHolder("dreamseekers_strengthening")
@PrefixGameTestTemplate(false)
public final class StrengtheningGameTests {
    private static final String BATCH="dreamseekers_strengthening";
    private static final List<AaroncosSkillCombatGameTests.F> FIXTURES=new ArrayList<>();
    private static final List<NeutralContractGameTests.F> CONTRACT_FIXTURES=new ArrayList<>();
    private static AaroncosSkillCombatGameTests.F fixture(GameTestHelper h,String name) {
        var f=new AaroncosSkillCombatGameTests.F(h,"API52_"+name);FIXTURES.add(f);return f;
    }
    private static void done(AaroncosSkillCombatGameTests.F f) {
        com.solegendary.reignofnether.research.ResearchServerEvents.removeCheat(f.owner,"foodforthought");
        FIXTURES.remove(f);f.done();
    }
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) {
        for(var f:List.copyOf(FIXTURES)){com.solegendary.reignofnether.research.ResearchServerEvents.removeCheat(f.owner,"foodforthought");f.close();}FIXTURES.clear();
        for(var f:List.copyOf(CONTRACT_FIXTURES))f.close();CONTRACT_FIXTURES.clear();
    }
    private static void close(GameTestHelper h,double actual,double expected,String reason) {
        h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<.0001,reason+" actual="+actual+" expected="+expected);
    }
    private static void suppression(GameTestHelper h,LivingEntity target,float factor,int remaining,String why) {
        close(h,AaroncosShockSuppression.factor(target),factor,why);
        h.assertTrue(AaroncosShockSuppression.remainingTicks(target)==remaining,why+" remaining="+AaroncosShockSuppression.remainingTicks(target));
    }
    private static BeetleMotherUnit mother(AaroncosSkillCombatGameTests.F f,double x,double z) {
        return f.add(DreamseekersMod.BEETLE_MOTHER_UNIT.get().create(f.h.getLevel()),f.owner,x,z);
    }
    private static BeetleUnit beetle(AaroncosSkillCombatGameTests.F f,String owner,double x,double z) {
        return f.add(DreamseekersMod.BEETLE.get().create(f.h.getLevel()),owner,x,z);
    }
    private static SkillResult seedProbe(GameTestHelper h,net.minecraft.server.level.ServerPlayer peer,GoldenFoxUnit source,BlockPos pos) {
        var level=h.getLevel();var box=DreamseekersMod.JUNGLE_SPORE_CARRIER.get().getDimensions().makeBoundingBox(net.minecraft.world.phys.Vec3.atBottomCenterOf(pos));
        String before="tick="+level.getGameTime()+" sourceTicks="+source.tickCount+" pos="+source.position()+" ground="+pos
                +" peerAlive="+peer.isAlive()+" peerRemoved="+peer.isRemoved()+" sourceAdded="+source.isAddedToWorld()
                +" idSame="+(level.getEntity(source.getId())==source)+" uuidSame="+(level.getEntity(source.getUUID())==source)
                +" chunks="+level.hasChunkAt(pos)+"/"+level.hasChunkAt(pos.below())
                +" sturdy="+level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),net.minecraft.core.Direction.UP)
                +" emptyGround="+level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()+" noCollision="+level.noCollision(null,box)
                +" fog="+com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isFogActiveFor(peer)
                +" visible="+com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isBlockVisibleFor(peer,pos.getX(),pos.getZ());
        SkillResult result=SkillService.requestGround(peer,new SkillGroundRequest(UnitSkill.FOX_DREAM_SEED,source.getId(),source.getUUID(),source.rts().skills().revision(),pos));
        System.out.println("STRENGTHENING_REAL_SEED_DIAGNOSTIC result="+result+" "+before);return result;
    }
    private static void aps(GameTestHelper h,BeetleUnit target,double bonus,String why) {
        close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),bonus,why+" contribution");
        // This is the real public APS getter; it invokes the live guarded-modifier refresh bridge.
        close(h,target.getAttacksPerSecond(),.8*(1+bonus),why+" actual APS getter");
    }

    @GameTest(templateNamespace="dreamseekers_strengthening",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=165)
    public static void rightRealHpReceiptHasExactEightyTickDeadlineAndSurvivesNormalStop(GameTestHelper h) {
        var f=fixture(h,"shockclock");var source=f.hand(false);var target=f.target(3,0);long[] castAt={0};
        h.startSequence().thenIdle(30).thenExecute(()->{
            castAt[0]=h.getLevel().getGameTime();h.assertTrue(f.cast(source,source.mob())==SkillResult.SUCCESS,"real paid right cast");
        }).thenIdle(19).thenExecute(()->{f.hp(target,1000,"nineteen ticks never damage");suppression(h,target,1,0,"no pre-release state");})
          .thenIdle(1).thenExecute(()->{
              f.hp(target,946,"one genuine 54 HP loss");suppression(h,target,1.4f,80,"exact receipt installation");
              h.assertTrue(f.shadow()==470&&!AaroncosCombat.active(source),"cost is spent and charge has already retired");
              new UnitActionItem(f.owner,UnitAction.STOP,-1,new int[]{source.mob().getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());
              target.setPos(f.center.getX()+12.5,100,f.center.getZ()+.5);
              suppression(h,target,1.4f,80,"finished hit is not tied to later STOP or six-block cast range");
          }).thenIdle(79).thenExecute(()->suppression(h,target,1.4f,1,"one final tick"))
          .thenIdle(1).thenExecute(()->{
              h.assertTrue(h.getLevel().getGameTime()==castAt[0]+100,"20 windup ticks plus 80 suppression ticks");
              suppression(h,target,1,0,"natural expiry");h.assertTrue(f.shadow()==470&&source.rts().skills().cooldownTicks(UnitSkill.AARONCOS_RIGHT_SHOCK)==500,"expiry never refunds thirty shadow or six-hundred cooldown");done(f);
          });
    }

    @GameTest(templateNamespace="dreamseekers_strengthening",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void realHpAndGelReceiptsButNeverAbsorptionOnlyOrCanceledHit(GameTestHelper h) {
        var f=fixture(h,"receipts");var source=f.hand(false);var hp=f.target(3,0);var gel=f.target(0,3);var absorb=f.target(-3,0);var cancel=f.target(0,-3);
        var slime=f.add(DreamseekersMod.PINK_SLIME_UNIT.get().create(h.getLevel()),f.enemy,0,6);
        f.guard(e->{if(AaroncosCombat.isStrike(e.getSource())){if(e.getEntity()==gel)e.setAmount(8);if(e.getEntity()==cancel)e.setCanceled(true);}});
        h.startSequence().thenIdle(30).thenExecute(()->{
            try(var peer=new PassiveIncomePeer(h.getLevel(),f.enemy)) {
                h.assertTrue(SkillService.request(peer.actor,new SkillRequest(UnitSkill.GEL_GUARD,slime.getId(),slime.getUUID(),slime.rts().skills().revision(),gel.getId(),gel.getUUID()))==SkillResult.SUCCESS,"real enemy Gel cast before right impact");
            }
            close(h,SkillEffects.shieldRemaining(gel),12,"real twelve-point shield");absorb.setAbsorptionAmount(80);
            h.assertTrue(f.cast(source,source.mob())==SkillResult.SUCCESS,"real four-target ring");
        }).thenIdle(20).thenExecute(()->{
            f.hp(hp,946,"normal realHP positive");suppression(h,hp,1.4f,80,"HP receipt positive");
            f.hp(gel,1000,"Gel-only consumes noHP");close(h,SkillEffects.shieldRemaining(gel),4,"exact final-event eight-point Gel consumption");suppression(h,gel,1.4f,80,"Gel receipt positive");
            f.hp(absorb,1000,"absorption-only noHP");close(h,absorb.getAbsorptionAmount(),26,"true absorption consumed");suppression(h,absorb,1,0,"absorption is not a receipt");
            f.hp(cancel,1000,"Forge canceled noHP");suppression(h,cancel,1,0,"Forge canceled no state");done(f);
        });
    }

    @GameTest(templateNamespace="dreamseekers_strengthening",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void zeroNanLethalAndInvulnerableHitsNeverLeaveSuppression(GameTestHelper h) {
        var f=fixture(h,"badreceipts");var source=f.hand(false);var zero=f.target(3,0);var nan=f.target(0,3);var lethal=f.target(-3,0);var invulnerable=f.target(0,-3);
        f.guard(e->{if(AaroncosCombat.isStrike(e.getSource())){if(e.getEntity()==zero)e.setAmount(0);if(e.getEntity()==nan)e.setAmount(Float.NaN);}});
        h.startSequence().thenIdle(30).thenExecute(()->{lethal.setHealth(1);invulnerable.setInvulnerable(true);h.assertTrue(f.cast(source,source.mob())==SkillResult.SUCCESS,"real ring before receipt denial cases");})
          .thenIdle(20).thenExecute(()->{
              f.hp(zero,1000,"zero final damage");f.hp(nan,1000,"nonfinite final damage");f.hp(invulnerable,1000,"real invulnerability");h.assertTrue(!lethal.isAlive(),"real lethal write positive control");
              for(var target:List.of(zero,nan,lethal,invulnerable))suppression(h,target,1,0,"no positive surviving receipt");done(f);
          });
    }

    @GameTest(templateNamespace="dreamseekers_strengthening",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=130)
    public static void secondPaidRightHitCannotRefreshOrStealFirstLease(GameTestHelper h) {
        var f=fixture(h,"nosteal");var first=f.hand(false);var second=f.hand(false);var target=f.target(3,0);
        h.startSequence().thenIdle(30).thenExecute(()->h.assertTrue(f.cast(first,first.mob())==SkillResult.SUCCESS,"first paid source"))
          .thenIdle(20).thenExecute(()->{suppression(h,target,1.4f,80,"first lease");h.assertTrue(f.cast(second,second.mob())==SkillResult.SUCCESS,"second independent paid source");})
          .thenIdle(20).thenExecute(()->{f.hp(target,892,"two genuine 54 HP hits");suppression(h,target,1.4f,60,"second hit did not extend existing deadline");h.assertTrue(f.shadow()==440,"both real costs paid");first.mob().discard();suppression(h,target,1,0,"first exact source owns lease; second hit cannot steal it");done(f);});
    }

    @GameTest(templateNamespace="dreamseekers_strengthening",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=110)
    public static void sourceAccountReplacementAndTargetOwnerAbaInvalidateBeforeNextRead(GameTestHelper h) {
        var f=fixture(h,"shockauthority");var source=f.hand(false);var a=f.target(3,0);var b=f.target(0,3);
        h.startSequence().thenIdle(30).thenExecute(()->h.assertTrue(f.cast(source,source.mob())==SkillResult.SUCCESS,"paid legal source"))
          .thenIdle(20).thenExecute(()->{
              suppression(h,a,1.4f,80,"first exact target");suppression(h,b,1.4f,80,"second exact target");
              a.setOwnerName(f.owner);a.setOwnerName(f.enemy);suppression(h,a,1,0,"target ownership ABA cannot resurrect receipt");
              ResourcesServerEvents.resourcesList.remove(f.account);var next=new Resources(f.owner,1000,1000,1000);next.extra.put(DreamPassiveEconomy.SHADOW,500);ResourcesServerEvents.resourcesList.add(next);
              suppression(h,b,1,0,"same-name new account cannot authorize old paid source");close(h,next.extra.get(DreamPassiveEconomy.SHADOW),500,"replacement account untouched");done(f);
          });
    }

    @GameTest(templateNamespace="dreamseekers_strengthening",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=130)
    public static void realWaveAndShockUseMaxNativeFactorAndFallBackWithoutMultiplication(GameTestHelper h) {
        var f=fixture(h,"maxslow");var right=f.hand(false);var target=f.target(3,0);var wave=f.add(DreamseekersMod.SHADOW_SQUEAL_GHOST_UNIT.get().create(h.getLevel()),f.owner,7,0);
        h.startSequence().thenIdle(30).thenExecute(()->{
            h.assertTrue(f.cast(right,right.mob())==SkillResult.SUCCESS,"right cast");
            h.assertTrue(SkillService.request(f.peer.actor,new SkillRequest(UnitSkill.SQUEAL_SUPPRESSION_WAVE,wave.getId(),wave.getUUID(),wave.rts().skills().revision(),target.getId(),target.getUUID()))==SkillResult.SUCCESS,"true physical wave cast");
        }).thenIdle(20).thenExecute(()->{
            h.assertTrue(SquealSuppressionWave.hasDebuff(target)&&AaroncosShockSuppression.remainingTicks(target)==80,"both real receipt-based leases active");
            close(h,ManagedAttackSlowdown.factor(target,1.1f),1.4,"shock wins over wave without stacking");
            target.addEffect(new MobEffectInstance(MobEffectRegistrar.ATTACK_SLOWDOWN.get(),100,9));
            close(h,ManagedAttackSlowdown.cachedFactor(target),1.5,"native stronger factor wins");
            close(h,SkillEffects.fixedAttackCooldown(target,100),150,"int reset applies one strongest factor");
            close(h,SkillEffects.fixedAttackCooldown(target,100f),150,"float reset applies one strongest factor");
            target.removeEffect(MobEffectRegistrar.ATTACK_SLOWDOWN.get());right.mob().discard();
            close(h,ManagedAttackSlowdown.factor(target,1),1.25,"after shock ends the smaller live wave penalty remains");
            wave.discard();close(h,ManagedAttackSlowdown.cachedFactor(target),1,"last source removal is cached no-op");
            h.assertTrue(Float.isNaN(ManagedAttackSlowdown.factor(target,Float.NaN)),"unmanaged native NaN passes through unchanged");done(f);
        });
    }

    @GameTest(templateNamespace="dreamseekers_strengthening",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void actualMilitiaFloatGetterAppliesRightSuppressionExactlyOnce(GameTestHelper h) {
        var f=fixture(h,"militiafloat");var right=f.hand(false);
        var militia=f.add(com.solegendary.reignofnether.registrars.EntityRegistrar.MILITIA_UNIT.get().create(h.getLevel()),f.enemy,3,0);
        militia.swapWeapons(true);militia.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);militia.setHealth(1000);
        float[] baseline={0};h.startSequence().thenIdle(30).thenExecute(()->{
            baseline[0]=militia.getAttackCooldown();h.assertTrue(baseline[0]>0,"real current ranged-mode Militia float cooldown baseline");
            h.assertTrue(f.cast(right,right.mob())==SkillResult.SUCCESS,"real paid right hit against actual Militia");
        }).thenIdle(20).thenExecute(()->{
            suppression(h,militia,1.4f,80,"actual Militia took surviving right receipt");
            close(h,militia.getAttackCooldown(),baseline[0]*1.4f,"core multiplier already includes managed slow; RETURN adapter must not multiply it twice");
            militia.addEffect(new MobEffectInstance(MobEffectRegistrar.ATTACK_SLOWDOWN.get(),100,9));
            close(h,militia.getAttackCooldown(),baseline[0]*1.5f,"stronger native maximum still exactly once on float consumer");
            right.mob().discard();close(h,militia.getAttackCooldown(),baseline[0]*1.5f,"without managed effect original native float behavior unchanged");
            militia.removeEffect(MobEffectRegistrar.ATTACK_SLOWDOWN.get());close(h,militia.getAttackCooldown(),baseline[0],"all effects removed returns exact original baseline");done(f);
        });
    }

    @GameTest(templateNamespace="dreamseekers_strengthening",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void motherSelectsOnlySixNearestExactOwnedBeetlesAndRecomputesMembership(GameTestHelper h) {
        var f=fixture(h,"mothercap");var mother=mother(f,0,0);
        List<BeetleUnit> recipients=List.of(beetle(f,f.owner,2,0),beetle(f,f.owner,0,2),beetle(f,f.owner,-2,0),beetle(f,f.owner,0,-2),beetle(f,f.owner,3,3),beetle(f,f.owner,-3,3),beetle(f,f.owner,0,5.7));
        var foreign=beetle(f,f.enemy,1,1);var otherType=f.add(DreamseekersMod.SHADOW_HAND_UNIT.get().create(h.getLevel()),f.owner,-1,1);BeetleUnit[] seventh={null};
        h.startSequence().thenIdle(30).thenExecute(()->{
            var sorted=recipients.stream().sorted(Comparator.comparingDouble((BeetleUnit b)->mother.distanceToSqr(b)).thenComparing(b->b.getUUID().toString())).toList();
            for(int i=0;i<sorted.size();i++)aps(h,sorted.get(i),i<6?.15:0,"distance/UUID member "+i);
            aps(h,foreign,0,"same-faction enemy owner rejected");close(h,SkillEffects.bonus(otherType,BuffKind.ATTACK_SPEED),0,"ordinary shadow hand not a beetle");
            seventh[0]=sorted.get(6);seventh[0].setPos(mother.getX()+.5,mother.getY(),mother.getZ()+.5);
        }).thenIdle(10).thenExecute(()->{
            var sorted=recipients.stream().sorted(Comparator.comparingDouble((BeetleUnit b)->mother.distanceToSqr(b)).thenComparing(b->b.getUUID().toString())).toList();
            h.assertTrue(sorted.subList(0,6).contains(seventh[0]),"former seventh really moved into nearest six");for(int i=0;i<sorted.size();i++)aps(h,sorted.get(i),i<6?.15:0,"next discovery recomputes bounded membership "+i);done(f);
        });
    }

    @GameTest(templateNamespace="dreamseekers_strengthening",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void motherLiveRangeLosAndDisabledSourceWithdrawBeforeApsRead(GameTestHelper h) {
        var f=fixture(h,"motherguard");var mother=mother(f,0,0);var target=beetle(f,f.owner,4,0);
        h.startSequence().thenIdle(30).thenExecute(()->{
            aps(h,target,.15,"in-range positive");target.setPos(mother.getX()+6.01,mother.getY(),mother.getZ());aps(h,target,0,"same-stack beyond actual 3D six range");
            target.setPos(mother.getX()+4,mother.getY(),mother.getZ());
        }).thenIdle(10).thenExecute(()->{
            aps(h,target,.15,"rediscovered inside range");for(BlockPos at:BlockPos.betweenClosed(f.center.offset(2,0,-2),f.center.offset(2,5,2)))h.getLevel().setBlock(at,Blocks.STONE.defaultBlockState(),2);
            h.assertTrue(!mother.hasLineOfSight(target),"actual solid wall blocks sight");aps(h,target,0,"same-stack wall withdraws installed transient modifier");
            for(BlockPos at:BlockPos.betweenClosed(f.center.offset(2,0,-2),f.center.offset(2,5,2)))h.getLevel().setBlock(at,Blocks.AIR.defaultBlockState(),2);
        }).thenIdle(10).thenExecute(()->{
            aps(h,target,.15,"natural discovery after wall removed");mother.addEffect(new MobEffectInstance(MobEffectRegistrar.STUN.get(),40));aps(h,target,0,"stunned source immediately stops passive");mother.removeEffect(MobEffectRegistrar.STUN.get());
        }).thenIdle(10).thenExecute(()->{
            aps(h,target,.15,"natural discovery after stun removed");mother.setOwnerName(f.enemy);mother.setOwnerName(f.owner);aps(h,target,0,"source ownership ABA cannot restore prior lease");done(f);
        });
    }

    @GameTest(templateNamespace="dreamseekers_strengthening",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void twoMothersNeverStackAndAccountReplacementRevokesExactLease(GameTestHelper h) {
        var f=fixture(h,"motheraccount");var a=mother(f,0,0);var b=mother(f,-2,0);var target=beetle(f,f.owner,2,0);
        h.startSequence().thenIdle(30).thenExecute(()->{
            aps(h,target,.15,"two real mothers still one fifteen-percent category");
            close(h,target.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getBaseValue(),.8,"base untouched");
            ResourcesServerEvents.resourcesList.remove(f.account);ResourcesServerEvents.resourcesList.add(new Resources(f.owner,1000,1000,1000));
            aps(h,target,0,"new same-name account cannot revive original runtime lease");
        }).thenIdle(10).thenExecute(()->{aps(h,target,.15,"fresh discovery captures new supported account");a.discard();b.discard();aps(h,target,0,"both source objects removed no tail bonus");done(f);});
    }

    @GameTest(templateNamespace="dreamseekers_strengthening",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void summonedBeetlesBenefitWithoutChangingPopulationFreeOriginOrLease(GameTestHelper h) {
        var f=fixture(h,"mothersummon");var mother=mother(f,0,0);
        com.solegendary.reignofnether.research.ResearchServerEvents.addCheat(f.owner,"foodforthought");int[] population={0};List<Mob> children=new ArrayList<>();
        h.startSequence().thenIdle(30).thenExecute(()->{
            population[0]=UnitServerEvents.getCurrentPopulation(f.owner);
            h.assertTrue(SkillService.request(f.peer.actor,new SkillRequest(UnitSkill.MOTHER_BROOD,mother.getId(),mother.getUUID(),mother.rts().skills().revision(),mother.getId(),mother.getUUID()))==SkillResult.SUCCESS,"actual authenticated brood cast");
            UnitServerEvents.getAllUnits().stream().filter(e->e instanceof Mob&&e instanceof Unit u&&u.getOwnerName().equals(f.owner)&&com.dreamseekers.summon.BoundedSummons.isTemporary(e)).map(e->(Mob)e).forEach(children::add);
            children.forEach(b->{b.setNoAi(true);f.bodies.add(b);});h.assertTrue(children.size()==2&&f.shadow()==500&&UnitServerEvents.getCurrentPopulation(f.owner)==population[0]+4,"exactly two children; population charged, shadow unchanged");
        }).thenIdle(10).thenExecute(()->{
            for(var body:children){aps(h,(BeetleUnit)body,.15,"real leased child allowed");h.assertTrue(RtsKillValueClaims.isFree(body)&&com.dreamseekers.summon.BoundedSummons.canAct(body),"free kill-value provenance and original active lease preserved");}
            h.assertTrue(mother.rts().skills().cooldownTicks(UnitSkill.MOTHER_BROOD)==890&&f.shadow()==500,"passive never changes the summon cooldown or shadow balance");
            mother.discard();for(var body:children)aps(h,(BeetleUnit)body,0,"expired parent lease not rescued by aura");
            com.solegendary.reignofnether.research.ResearchServerEvents.removeCheat(f.owner,"foodforthought");done(f);
        });
    }

    // Overall budget includes natural world publication and the shortened suppression interval.
    @GameTest(templateNamespace="dreamseekers_strengthening",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=180)
    public static void realSporeShortensNewRightLeaseOnlyOnceToSixtyFourTicks(GameTestHelper h) {
        var f=fixture(h,"shocksporeduration");var right=f.hand(false);var target=f.target(3,0);
        h.startSequence().thenIdle(30).thenWaitUntil(()->{
            // A prior runtime observed added=true but tickCount=0 and absent UUID/ID indexes.
            // Wait for the original bodies to publish naturally; never manually re-add or weaken source authorization.
            for(Mob body:List.of(right.mob(),target))h.assertTrue(body.tickCount>3&&body.isAddedToWorld()
                    &&h.getLevel().getEntity(body.getId())==body&&h.getLevel().getEntity(body.getUUID())==body,
                    "original skill source and target naturally ticking/indexed before exact post-cast clock");
        }).thenExecute(()->{
            try(var peer=new PassiveIncomePeer(h.getLevel(),f.enemy)) {
                h.assertTrue(seedProbe(h,peer.actor,target,f.center.offset(3,0,2))==SkillResult.SUCCESS,"actual enemy ground seed creates a protective spore lease");
            }
            h.assertTrue(com.dreamseekers.spore.SporeLease.activeFor(target)!=null,"real published active spore");
            h.assertTrue(f.cast(right,right.mob())==SkillResult.SUCCESS,"real paid ring after active protection");
        }).thenIdle(20).thenExecute(()->suppression(h,target,1.4f,64,"incoming80 shortened by .8 once"))
          .thenIdle(10).thenExecute(()->suppression(h,target,1.4f,54,"ten actual ticks subtract ten, no repeated .8"))
          .thenIdle(54).thenExecute(()->{suppression(h,target,1,0,"exact shortened natural expiry");h.assertTrue(f.shadow()==470,"duration transform never refunds paid skill");done(f);});
    }

    @GameTest(templateNamespace="dreamseekers_strengthening",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=120)
    public static void realSporeCleansesOneDeterministicCandidateAndProtectionIsNotDebuffImmunity(GameTestHelper h) {
        var f=fixture(h,"shocksporecleanse");var first=f.hand(false);var second=f.hand(false);var target=f.target(3,0);
        h.startSequence().thenIdle(30).thenExecute(()->h.assertTrue(f.cast(first,first.mob())==SkillResult.SUCCESS,"first real right cast"))
          .thenIdle(20).thenExecute(()->{
              suppression(h,target,1.4f,80,"live managed shock before cleanse");target.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS,100));
              try(var peer=new PassiveIncomePeer(h.getLevel(),f.enemy)) {
                  h.assertTrue(seedProbe(h,peer.actor,target,f.center.offset(3,0,2))==SkillResult.SUCCESS,"real spore activation chooses original deterministic candidate pool");
              }
              suppression(h,target,1,0,"dreamseekers:aaroncos sorts before minecraft:weakness and is removed");
              h.assertTrue(target.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS)&&com.dreamseekers.spore.SporeEffects.cleanseProtectionTicks(target)==200,"only one candidate removed; original vanilla negative remains and deletion throttle starts");
              h.assertTrue(f.cast(second,second.mob())==SkillResult.SUCCESS,"second independent paid right source");
          }).thenIdle(20).thenExecute(()->{
              suppression(h,target,1.4f,64,"cleanse protection is not new-debuff immunity; active spore shortens once");
              h.assertTrue(target.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS)&&com.dreamseekers.spore.SporeEffects.cleanseProtectionTicks(target)==180,"no extra automatic cleanse rounds");done(f);
          });
    }

    @GameTest(templateNamespace="dreamseekers_strengthening",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=700)
    public static void realCompletedContractBeetleBenefitsButNativeAndPendingBodyNeverDo(GameTestHelper h) {
        var f=new NeutralContractGameTests.F(h,"API52Aura");CONTRACT_FIXTURES.add(f);
        var worker=f.worker(3,-10,false);var wild=f.nativeBody(false,5,-10);var source=DreamseekersMod.BEETLE_MOTHER_UNIT.get().create(h.getLevel());source.setOwnerName(f.owner);source.setNoAi(true);f.place(source,3,-14);int[] pending={0};
        f.guard(e->{pending[0]++;h.assertTrue(e.getEntity() instanceof BeetleUnit,"real conversion publishes exact RTS beetle");close(h,SkillEffects.bonus((LivingEntity)e.getEntity(),BuffKind.ATTACK_SPEED),0,"uncommitted pending Join cannot borrow mother bonus");});
        h.startSequence().thenWaitUntil(()->f.ready(worker,wild,source)).thenExecute(()->{
            close(h,SkillEffects.bonus(wild,BuffKind.ATTACK_SPEED),0,"native wild beetle near mother is not RTS target");
            h.assertTrue(f.start(worker,wild).equals("approaching"),"real neutral contract order starts full native channel");
        }).thenWaitUntil(()->h.assertTrue(f.converted().size()==1,"actual paid conversion completes"))
          .thenIdle(10).thenExecute(()->{
              var converted=(BeetleUnit)f.converted().get(0);h.assertTrue(wild.isRemoved()&&pending[0]==1,"one exact native-to-RTS committed conversion");aps(h,converted,.15,"fully committed same-owner contract beneficiary");
              h.assertTrue(RtsKillValueClaims.isFree(converted)&&f.account.food==950&&f.account.extra.get(DreamPassiveEconomy.SHADOW)==485,"aura never erases free origin or changes original fifty-food fifteen-shadow fee");
              h.assertTrue(com.dreamseekers.neutralcontract.ContractLedger.get(f.level.getServer()).count(f.owner,f.account.accountEpoch)==1,"original persistent contract slot remains one");
              CONTRACT_FIXTURES.remove(f);f.close();h.succeed();
          });
    }
}
