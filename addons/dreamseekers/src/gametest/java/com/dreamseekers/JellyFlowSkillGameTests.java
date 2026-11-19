// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Actual server casts, automatic selection, ordinary healing and native navigation. No GUI/socket claims. */
@GameTestHolder("dreamseekers_jelly_flow")
@PrefixGameTestTemplate(false)
public final class JellyFlowSkillGameTests {
    private static final String BATCH="dreamseekers_jelly_flow", PREFIX="JellyFlow_";
    private static final List<Entity> FIXTURES=new ArrayList<>();
    private static final HealProbe PROBE=new HealProbe();
    private static boolean listening;

    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) {
        if(listening){MinecraftForge.EVENT_BUS.unregister(PROBE);listening=false;}
        PROBE.modes.clear();PROBE.once.clear();PROBE.events.clear();PROBE.each.clear();
        var names=PlayerServerEvents.rtsPlayers.stream().map(p->p.name).filter(n->n.startsWith(PREFIX)).toList();
        for(String a:names)for(String b:names)if(!a.equals(b))AlliancesServerEvents.removeAlliance(a,b);
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));
        ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));
        for(Entity e:FIXTURES)if(!e.isRemoved())e.discard();FIXTURES.clear();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=520)
    public static void manualFlowHasSixRealTwoPointPulsesThenFiniteTideBuff(GameTestHelper h) {
        floor(h);var caster=jelly(h,"Pulses",4,5);var target=slime(h,"Pulses",7,5);
        var sequence=ready(h,caster,target).thenExecute(()->{
            target.setHealth(20);require(h,cast(h,caster,target),SkillResult.SUCCESS,"manual flow cast");
            h.assertTrue(SkillEffects.healTicks(target)==120&&caster.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW)==240,"initial authoritative duration and cooldown");
        }).thenIdle(19).thenExecute(()->close(h,target.getHealth(),20,"no immediate healing before first pulse"))
          .thenIdle(2).thenExecute(()->close(h,target.getHealth(),22,"first real pulse"));
        for(int pulse=2;pulse<=6;pulse++){final int count=pulse;sequence.thenIdle(20).thenExecute(()->close(h,target.getHealth(),20+2*count,"real pulse "+count));}
        sequence.thenExecute(()->{
            h.assertTrue(SkillEffects.healTicks(target)==0,"flow completes after sixth pulse");close(h,SkillEffects.bonus(target,BuffKind.MOVEMENT_SPEED),.1,"completed flow awards tide");ledger(h,"Pulses");
        }).thenIdle(81).thenExecute(()->{
            close(h,SkillEffects.bonus(target,BuffKind.MOVEMENT_SPEED),0,"tide expires after four seconds");h.assertTrue(caster.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW)>0,"completion never refunds cooldown");pass("SIX_REAL_PULSES_TIDE_EXPIRY");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=260)
    public static void fullHealthInvalidOwnerIdentityAndTargetsNeverSpendCooldown(GameTestHelper h) {
        floor(h);var caster=jelly(h,"Authority",4,5);var target=slime(h,"Authority",7,5);var enemy=slime(h,"AuthorityEnemy",8,5);var far=slime(h,"Authority",20,5);
        var wild=EntityType.PIG.create(h.getLevel());place(h,wild,6,8);wild.setNoAi(true);
        ready(h,caster,target,enemy,far,wild).thenExecute(()->{
            require(h,cast(h,caster,target),SkillResult.FULL_HEALTH,"full-health friend rejected");
            h.assertTrue(caster.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW)==0,"full-health request spends no cooldown");
            target.setHealth(20);enemy.setHealth(20);far.setHealth(20);SkillRequest good=request(caster,target);
            require(h,SkillService.request(playerNamed(h,enemy.getOwnerName()),good),SkillResult.NOT_OWNER,"foreign authenticated sender");
            require(h,SkillService.request(playerNamed(h,caster.getOwnerName()),new SkillRequest(UnitSkill.JELLY_FLOW,caster.getId(),UUID.randomUUID(),good.revision(),target.getId(),target.getUUID())),SkillResult.INVALID_SOURCE,"wrong source UUID");
            denied(h,SkillService.request(playerNamed(h,caster.getOwnerName()),new SkillRequest(UnitSkill.JELLY_FLOW,caster.getId(),caster.getUUID(),good.revision(),target.getId(),UUID.randomUUID())),"wrong target UUID");
            denied(h,cast(h,caster,caster),"self target");denied(h,cast(h,caster,enemy),"enemy target");denied(h,cast(h,caster,far),"outside eight blocks");denied(h,cast(h,caster,wild),"non-unit target");
            target.setOwnerName("");denied(h,cast(h,caster,target),"unowned target");target.setOwnerName(caster.getOwnerName());
            h.assertTrue(caster.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW)==0&&SkillEffects.healTicks(target)==0,"all invalid requests leave cooldown/effect untouched");
            require(h,cast(h,caster,target),SkillResult.SUCCESS,"valid manual request after rejections");ledger(h,"Authority");pass("AUTHORITY_FULL_HEALTH_NO_DEBIT");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=330)
    public static void differentSendersCannotStackRefreshOrDuplicateTheSameFlow(GameTestHelper h) {
        floor(h);var a=jelly(h,"AtomicA",4,5);var b=jelly(h,"AtomicB",5,8);var target=slime(h,"AtomicA",7,5);AlliancesServerEvents.addAlliance(a.getOwnerName(),b.getOwnerName());
        SkillRequest[] first={null};long[] expiry={0};
        ready(h,a,b,target).thenExecute(()->{
            target.setHealth(20);first[0]=request(a,target);require(h,SkillService.request(playerNamed(h,a.getOwnerName()),first[0]),SkillResult.SUCCESS,"first sender wins");
            require(h,cast(h,b,target),SkillResult.ALREADY_ACTIVE,"second authenticated sender cannot duplicate flow");denied(h,SkillService.request(playerNamed(h,a.getOwnerName()),first[0]),"replayed successful request");
            expiry[0]=h.getLevel().getGameTime()+SkillEffects.healTicks(target);
        }).thenIdle(101).thenExecute(()->{
            close(h,target.getHealth(),30,"only five two-point pulses before final pulse");require(h,cast(h,b,target),SkillResult.ALREADY_ACTIVE,"nearly complete flow cannot refresh");
            h.assertTrue(h.getLevel().getGameTime()+SkillEffects.healTicks(target)==expiry[0]&&b.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW)==0,"expiry unchanged and loser never pays cooldown");
            ledger(h,"AtomicA");ledger(h,"AtomicB");pass("TWO_SENDERS_ATOMIC_HOT_NO_REFRESH");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=410)
    public static void lightWoundReachesFullWithoutOverhealButStillFinishesBeforeTide(GameTestHelper h) {
        floor(h);var caster=jelly(h,"Light",4,5);var target=slime(h,"Light",7,5);
        ready(h,caster,target).thenExecute(()->{target.setHealth(target.getMaxHealth()-2);require(h,cast(h,caster,target),SkillResult.SUCCESS,"light wound cast");})
          .thenIdle(21).thenExecute(()->{close(h,target.getHealth(),target.getMaxHealth(),"light wound capped at full HP");h.assertTrue(SkillEffects.healTicks(target)>0,"full health does not complete flow early");close(h,SkillEffects.bonus(target,BuffKind.MOVEMENT_SPEED),0,"no early tide reward");})
          .thenIdle(100).thenExecute(()->{close(h,target.getHealth(),target.getMaxHealth(),"later pulses never overheal");h.assertTrue(SkillEffects.healTicks(target)==0,"full duration completed");close(h,SkillEffects.bonus(target,BuffKind.MOVEMENT_SPEED),.1,"any positive actual healing qualifies on completion");pass("LIGHT_WOUND_NO_OVERHEAL_FULL_COMPLETION");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=380)
    public static void casterDeathAndUnloadInterruptWithoutCompletionReward(GameTestHelper h) {
        floor(h);var a=jelly(h,"Death",3,5);var t1=slime(h,"Death",6,5);var b=jelly(h,"Death",3,14);var t2=slime(h,"Death",6,14);
        ready(h,a,t1,b,t2).thenExecute(()->{t1.setHealth(20);t2.setHealth(20);require(h,cast(h,a,t1),SkillResult.SUCCESS,"first cast");require(h,cast(h,b,t2),SkillResult.SUCCESS,"second cast");})
          .thenIdle(21).thenExecute(()->{close(h,t1.getHealth(),22,"first pulse before source death");close(h,t2.getHealth(),22,"first pulse before source removal");a.hurt(a.damageSources().genericKill(),10000);b.discard();})
          .thenIdle(110).thenExecute(()->{for(var target:List.of(t1,t2)){close(h,target.getHealth(),22,"no pulses after source invalidation");h.assertTrue(SkillEffects.healTicks(target)==0,"flow removed");close(h,SkillEffects.bonus(target,BuffKind.MOVEMENT_SPEED),0,"canceled flow never completes");}pass("CASTER_DEATH_UNLOAD_CANCEL");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=390)
    public static void beyondTwelveBlocksCancelsInsteadOfChasingOrResumingOnReturn(GameTestHelper h) {
        floor(h);var caster=jelly(h,"Leash",3,5);var target=slime(h,"Leash",7,5);Vec3[] start={null};
        ready(h,caster,target).thenExecute(()->{start[0]=caster.position();target.setHealth(20);require(h,cast(h,caster,target),SkillResult.SUCCESS,"leash fixture cast");})
          .thenIdle(21).thenExecute(()->{close(h,target.getHealth(),22,"one pulse before leaving");moveFixture(h,target,20,5);})
          .thenIdle(2).thenExecute(()->{h.assertTrue(SkillEffects.healTicks(target)==0,"over-twelve-block target cancels");moveFixture(h,target,7,5);})
          .thenIdle(110).thenExecute(()->{close(h,target.getHealth(),22,"return does not resume canceled flow");close(h,SkillEffects.bonus(target,BuffKind.MOVEMENT_SPEED),0,"no canceled completion reward");h.assertTrue(caster.position().distanceToSqr(start[0])<.05&&caster.getMoveGoal().getMoveTarget()==null,"skill never chases target");h.assertTrue(caster.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW)>0,"interrupt does not refund cooldown");pass("DISTANCE_CANCEL_NO_CHASE_NO_RESUME");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=360)
    public static void ownerAbaOnCasterAddonTargetAndCoreTargetInvalidatesEveryPulse(GameTestHelper h) {
        floor(h);var a=jelly(h,"ABA",3,4);var t1=slime(h,"ABA",6,4);var b=jelly(h,"ABA",3,11);var t2=slime(h,"ABA",6,11);var c=jelly(h,"ABA",3,18);
        var core=EntityRegistrar.ZOMBIE_UNIT.get().create(h.getLevel());core.setOwnerName(account("ABA"));place(h,core,6,18);core.setNoAi(true);float[] coreHp={0};
        ready(h,a,t1,b,t2,c,core).thenExecute(()->{
            t1.setHealth(20);t2.setHealth(20);core.setHealth(core.getMaxHealth()-10);coreHp[0]=core.getHealth();require(h,cast(h,a,t1),SkillResult.SUCCESS,"source ABA cast");require(h,cast(h,b,t2),SkillResult.SUCCESS,"addon-target ABA cast");require(h,cast(h,c,core),SkillResult.SUCCESS,"core-target ABA cast");
            String old=a.getOwnerName();a.setOwnerName(account("ABAForeign"));a.setOwnerName(old);t2.setOwnerName(account("ABAForeign"));t2.setOwnerName(old);core.setOwnerName(account("ABAForeign"));core.setOwnerName(old);
        }).thenIdle(125).thenExecute(()->{
            close(h,t1.getHealth(),20,"source ABA blocks every pulse");close(h,t2.getHealth(),20,"addon target ABA blocks every pulse");close(h,core.getHealth(),coreHp[0],"core Unit target ABA blocks every pulse");
            for(LivingEntity target:List.of(t1,t2,core)){h.assertTrue(SkillEffects.healTicks(target)==0,"ABA removes flow");close(h,SkillEffects.bonus(target,BuffKind.MOVEMENT_SPEED),0,"ABA gives no tide");}pass("CASTER_ADDON_CORE_OWNER_ABA");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=360)
    public static void allianceAbaCancelsImmediatelyAndCannotReviveOldHealing(GameTestHelper h) {
        floor(h);var caster=jelly(h,"Alliance",4,5);var target=slime(h,"AllianceTarget",7,5);AlliancesServerEvents.addAlliance(caster.getOwnerName(),target.getOwnerName());
        ready(h,caster,target).thenExecute(()->{target.setHealth(20);require(h,cast(h,caster,target),SkillResult.SUCCESS,"allied flow");AlliancesServerEvents.removeAlliance(caster.getOwnerName(),target.getOwnerName());h.assertTrue(SkillEffects.healTicks(target)==0,"revocation cancels immediately");AlliancesServerEvents.addAlliance(caster.getOwnerName(),target.getOwnerName());})
          .thenIdle(125).thenExecute(()->{close(h,target.getHealth(),20,"readding alliance cannot revive old flow");close(h,SkillEffects.bonus(target,BuffKind.MOVEMENT_SPEED),0,"no revived completion");pass("ALLIANCE_ABA_NO_REPLAY");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=360)
    public static void deadAndRemovedTargetsCannotReceiveHealingOrCompletion(GameTestHelper h) {
        floor(h);var a=jelly(h,"TargetLife",3,5);var t1=slime(h,"TargetLife",6,5);var b=jelly(h,"TargetLife",3,14);var t2=slime(h,"TargetLife",6,14);
        ready(h,a,t1,b,t2).thenExecute(()->{t1.setHealth(20);t2.setHealth(20);require(h,cast(h,a,t1),SkillResult.SUCCESS,"death target cast");require(h,cast(h,b,t2),SkillResult.SUCCESS,"removed target cast");t1.hurt(t1.damageSources().genericKill(),10000);t2.discard();})
          .thenIdle(125).thenExecute(()->{h.assertTrue((!t1.isAlive()||t1.isRemoved())&&t2.isRemoved(),"targets not resurrected");for(var target:List.of(t1,t2)){h.assertTrue(SkillEffects.healTicks(target)==0,"invalid target flow cleared");close(h,SkillEffects.bonus(target,BuffKind.MOVEMENT_SPEED),0,"invalid target receives no tide");}h.assertTrue(a.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW)>0&&b.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW)>0,"invalid targets do not refund cooldowns");pass("TARGET_DEATH_REMOVE_NO_REVIVE");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=510)
    public static void authenticatedToggleCodecDisablesFutureAutoWithoutCancelingActiveFlow(GameTestHelper h) {
        floor(h);var caster=jelly(h,"Toggle",4,5);var target=slime(h,"Toggle",7,5);SkillNetwork.Toggle[] disable={null};
        ready(h,caster,target).thenExecute(()->{h.assertTrue(caster.rts().skills().autocastEnabled(UnitSkill.JELLY_FLOW),"automatic casting defaults enabled");target.setHealth(20);caster.setNoAi(false);})
          .thenIdle(3).thenExecute(()->{
            h.assertTrue(SkillEffects.healTicks(target)>0,"real server tick automatically casts");disable[0]=new SkillNetwork.Toggle(UnitSkill.JELLY_FLOW,caster.getId(),caster.getUUID(),caster.rts().skills().revision(),false);
            FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());try{disable[0].encode(buffer);var decoded=SkillNetwork.Toggle.decode(buffer);h.assertTrue(decoded.equals(disable[0])&&buffer.readableBytes()==0,"toggle codec exact roundtrip");}finally{buffer.release();}
            // FINAL22_SYNC_TRUE_BEGIN
            var sync=new SkillNetwork.Sync(UnitSkill.JELLY_FLOW,caster.getId(),caster.getUUID(),caster.rts().skills().revision(),caster.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW),true);
            FriendlyByteBuf syncBuffer=new FriendlyByteBuf(Unpooled.buffer());try{sync.encode(syncBuffer);var decoded=SkillNetwork.Sync.decode(syncBuffer);h.assertTrue(decoded.equals(sync)&&decoded.autocastEnabled()&&syncBuffer.readableBytes()==0,"v2 sync preserves true preference without trailing bytes");}finally{syncBuffer.release();}
            // FINAL22_SYNC_TRUE_END
            require(h,disable[0].execute(playerNamed(h,account("ToggleForeign"))),SkillResult.NOT_OWNER,"foreign toggle denied");require(h,disable[0].execute(playerNamed(h,caster.getOwnerName())),SkillResult.SUCCESS,"owner toggle accepted");denied(h,disable[0].execute(playerNamed(h,caster.getOwnerName())),"toggle replay rejected");
            h.assertTrue(!caster.rts().skills().autocastEnabled(UnitSkill.JELLY_FLOW)&&SkillEffects.healTicks(target)>0,"disabling future casts does not cancel existing flow");
        }).thenIdle(250).thenExecute(()->{close(h,target.getHealth(),32,"existing flow finishes but does not automatically recast");h.assertTrue(SkillEffects.healTicks(target)==0&&caster.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW)==0,"ready cooldown stays unused while auto disabled");ledger(h,"Toggle");pass("TOGGLE_CODEC_AUTH_NO_CANCEL_NO_FUTURE_AUTO");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=300)
    public static void noAiPreventsAutomaticCastButNeverBlocksAuthorizedManualCast(GameTestHelper h) {
        floor(h);var caster=jelly(h,"NoAI",4,5);var target=slime(h,"NoAI",7,5);
        ready(h,caster,target).thenExecute(()->target.setHealth(20)).thenIdle(35).thenExecute(()->{close(h,target.getHealth(),20,"NoAI fixture never autocasts");h.assertTrue(caster.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW)==0,"NoAI never spends cooldown");require(h,cast(h,caster,target),SkillResult.SUCCESS,"NoAI still accepts authenticated manual cast");})
          .thenIdle(21).thenExecute(()->{close(h,target.getHealth(),22,"manual flow genuinely heals under NoAI");pass("NOAI_AUTO_OFF_MANUAL_ON");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=310)
    public static void twoOfflineOwnerAutoCastersReserveOneTargetAndLoserCanChooseAnother(GameTestHelper h) {
        floor(h);var a=jelly(h,"AutoA",4,5);var b=jelly(h,"AutoB",5,8);var t1=slime(h,"AutoA",7,5);var t2=slime(h,"AutoA",8,8);AlliancesServerEvents.addAlliance(a.getOwnerName(),b.getOwnerName());
        ready(h,a,b,t1,t2).thenExecute(()->{h.assertTrue(h.getLevel().getServer().getPlayerList().getPlayerByName(a.getOwnerName())==null&&h.getLevel().getServer().getPlayerList().getPlayerByName(b.getOwnerName())==null,"automatic owners are not online players");t1.setHealth(20);a.setNoAi(false);b.setNoAi(false);})
          .thenIdle(3).thenExecute(()->{boolean aPaid=a.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW)>0,bPaid=b.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW)>0;h.assertTrue(aPaid!=bPaid&&SkillEffects.healTicks(t1)>0,"one automatic winner for one patient");t2.setHealth(20);})
          .thenIdle(3).thenExecute(()->{h.assertTrue(a.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW)>0&&b.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW)>0&&SkillEffects.healTicks(t1)>0&&SkillEffects.healTicks(t2)>0,"loser can reserve another wounded target without duplicate flow");})
          .thenIdle(17).thenExecute(()->{close(h,t1.getHealth(),22,"first patient receives one actual pulse");h.assertTrue(t2.getHealth()<=22,"second patient never receives duplicated pulse");ledger(h,"AutoA");ledger(h,"AutoB");pass("OFFLINE_AUTO_ATOMIC_DEDUP_OTHER_TARGET");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=260)
    public static void automaticSelectionPrefersLowerHealthRatioBeforeDistance(GameTestHelper h) {
        floor(h);var caster=jelly(h,"Priority",4,5);var near=slime(h,"Priority",6,5);var far=slime(h,"Priority",10,5);
        ready(h,caster,near,far).thenExecute(()->{near.setHealth(40);far.setHealth(15);caster.setNoAi(false);})
          .thenIdle(3).thenExecute(()->{h.assertTrue(SkillEffects.healTicks(far)>0&&SkillEffects.healTicks(near)==0,"more wounded ratio takes priority over nearer patient");pass("AUTO_HEALTH_RATIO_PRIORITY");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=360)
    public static void holdAutocastDoesNotChaseDistantPatientsOrChangeCommandRevision(GameTestHelper h) {
        floor(h);var caster=jelly(h,"Hold",4,5);var target=slime(h,"Hold",7,5);Vec3[] start={null};long[] command={0};
        ready(h,caster,target).thenExecute(()->{caster.setNoAi(false);new UnitActionItem(caster.getOwnerName(),UnitAction.HOLD,-1,new int[]{caster.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());start[0]=caster.position();command[0]=caster.rts().commandRevision;target.setHealth(20);})
          .thenIdle(25).thenExecute(()->{close(h,target.getHealth(),22,"automatic flow works under HOLD");h.assertTrue(caster.getHoldPosition()&&caster.rts().commandRevision==command[0]&&caster.getFollowTarget()==null&&caster.getMoveGoal().getMoveTarget()==null,"automatic support preserves HOLD and command revision");moveFixture(h,target,21,5);})
          .thenIdle(70).thenExecute(()->{h.assertTrue(caster.position().distanceToSqr(start[0])<.1&&caster.getNavigation().isDone()&&caster.getHoldPosition(),"HOLD never chases distant patient");close(h,target.getHealth(),22,"distant patient canceled");close(h,SkillEffects.bonus(target,BuffKind.MOVEMENT_SPEED),0,"distant interrupted flow receives no tide");pass("HOLD_REAL_AUTO_NO_CHASE_COMMAND_MUTATION");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=360)
    public static void automaticHealingPreservesRealFollowAndPendingShiftOrders(GameTestHelper h) {
        floor(h);var follower=jelly(h,"FollowOrder",4,5);var followTarget=slime(h,"FollowOrder",20,5);var patient1=slime(h,"FollowOrder",6,7);var mover=jelly(h,"QueueOrder",4,15);var patient2=slime(h,"QueueOrder",6,17);
        BlockPos first=h.absolutePos(new BlockPos(20,0,15)),second=h.absolutePos(new BlockPos(20,0,20));long[] command=new long[2];
        ready(h,follower,followTarget,patient1,mover,patient2).thenExecute(()->{
            follower.setNoAi(false);mover.setNoAi(false);new UnitActionItem(follower.getOwnerName(),UnitAction.FOLLOW,followTarget.getId(),new int[]{follower.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());command[0]=follower.rts().commandRevision;
            UnitServerEvents.addActionItem(mover.getOwnerName(),UnitAction.MOVE,-1,new int[]{mover.getId()},first,BlockPos.ZERO);
            UnitServerEvents.addActionItem(mover.getOwnerName(),UnitAction.MOVE,-1,new int[]{mover.getId()},second,BlockPos.ZERO,true);patient1.setHealth(20);patient2.setHealth(20);
        }).thenIdle(4).thenExecute(()->{
            h.assertTrue(SkillEffects.healTicks(patient1)>0&&SkillEffects.healTicks(patient2)>0,"automatic support while following and moving");h.assertTrue(follower.getFollowTarget()==followTarget&&follower.rts().commandRevision==command[0],"follow identity and generation unchanged");
            h.assertTrue(mover.getMoveGoal().getMoveTarget()!=null&&queued(mover,second),"real pending Shift destination survives automatic cast");command[1]=mover.rts().commandRevision;
        }).thenIdle(22).thenExecute(()->{
            h.assertTrue(follower.getFollowTarget()==followTarget&&follower.rts().commandRevision==command[0],"flow does not rewrite follow command");h.assertTrue(mover.rts().commandRevision==command[1]&&queued(mover,second),"flow does not rewrite pending Shift queue");h.assertTrue(patient1.getHealth()>=22&&patient2.getHealth()>=22,"both automatic flows produce actual healing");
            for(var source:List.of(follower,mover))new UnitActionItem(source.getOwnerName(),UnitAction.STOP,-1,new int[]{source.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());pass("AUTO_PRESERVES_FOLLOW_SHIFT_ORDERS");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=360)
    public static void realCompletedTideIncreasesGroundNavigationDisplacement(GameTestHelper h) {navigation(h,false);}

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=360)
    public static void realCompletedTideIncreasesFlyingNavigationDisplacement(GameTestHelper h) {navigation(h,true);}

    private static void navigation(GameTestHelper h,boolean flight) {
        floor(h);String suffix=flight?"FlySpeed":"GroundSpeed";var caster=jelly(h,suffix,4,12);Mob normal=flight?jelly(h,suffix,3,6):slime(h,suffix,3,6);Mob boosted=flight?jelly(h,suffix,3,15):slime(h,suffix,3,15);Vec3[] start=new Vec3[2];
        var sequence=ready(h,caster,normal,boosted).thenExecute(()->{boosted.setHealth(boosted.getMaxHealth()-2);require(h,cast(h,caster,boosted),SkillResult.SUCCESS,"real flow before navigation comparison");})
          .thenIdle(121).thenExecute(()->{close(h,SkillEffects.bonus(boosted,BuffKind.MOVEMENT_SPEED),.1,"real completed tide before move");normal.setNoAi(false);boosted.setNoAi(false);start[0]=normal.position();start[1]=boosted.position();new UnitActionItem(((Unit)normal).getOwnerName(),UnitAction.MOVE,-1,new int[]{normal.getId()},h.absolutePos(new BlockPos(21,0,6)),BlockPos.ZERO).action(h.getLevel());new UnitActionItem(((Unit)boosted).getOwnerName(),UnitAction.MOVE,-1,new int[]{boosted.getId()},h.absolutePos(new BlockPos(21,0,15)),BlockPos.ZERO).action(h.getLevel());});
        for(int i=0;i<35;i++)sequence.thenIdle(1).thenExecute(()->h.assertTrue(h.getLevel().noCollision(normal)&&h.getLevel().noCollision(boosted),"actual navigation preserves collision"));
        sequence.thenExecute(()->{
            double base=normal.getX()-start[0].x,fast=boosted.getX()-start[1].x;h.assertTrue(base>1&&fast>base*1.035,"actual "+(flight?"flying":"ground")+" displacement improves: normal="+base+" boosted="+fast+" normalPos="+normal.position()+" boostedPos="+boosted.position());if(flight)h.assertTrue(normal.isNoGravity()&&boosted.isNoGravity(),"native flying units remain gravity-free");
            for(Mob mob:List.of(normal,boosted))new UnitActionItem(((Unit)mob).getOwnerName(),UnitAction.STOP,-1,new int[]{mob.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());pass(flight?"REAL_TIDE_FLYING_NAVIGATION":"REAL_TIDE_GROUND_NAVIGATION");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=410)
    public static void movementStrongestCapAndCleanupPreserveBothPermanentNavigationAttributes(GameTestHelper h) {
        floor(h);var caster=jelly(h,"MoveCap",4,5);var target=jelly(h,"MoveCap",7,5);UUID permanent=UUID.fromString("dfbb2929-d82c-42f3-9ad2-6c9726442222");
        ready(h,caster,target).thenExecute(()->{for(var attr:List.of(Attributes.MOVEMENT_SPEED,Attributes.FLYING_SPEED)){target.getAttribute(attr).setBaseValue(.30);target.getAttribute(attr).addPermanentModifier(new AttributeModifier(permanent,"permanent movement research",.02,AttributeModifier.Operation.ADDITION));}target.setHealth(target.getMaxHealth()-2);require(h,cast(h,caster,target),SkillResult.SUCCESS,"real tide seed");})
          .thenIdle(121).thenExecute(()->{close(h,SkillEffects.bonus(target,BuffKind.MOVEMENT_SPEED),.1,"real completed tide");h.assertTrue(SkillEffects.addBuff(caster,target,"twenty",BuffKind.MOVEMENT_SPEED,.2,40)&&SkillEffects.addBuff(caster,target,"overcap",BuffKind.MOVEMENT_SPEED,.8,15),"test overlapping stronger providers");close(h,SkillEffects.bonus(target,BuffKind.MOVEMENT_SPEED),.25,"normal movement cap twenty-five percent");for(var attr:List.of(Attributes.MOVEMENT_SPEED,Attributes.FLYING_SPEED))close(h,target.getAttributeValue(attr),.32*1.25,"both navigation attributes apply one capped modifier");})
          .thenIdle(17).thenExecute(()->close(h,SkillEffects.bonus(target,BuffKind.MOVEMENT_SPEED),.2,"expired strongest falls back to surviving provider"))
          .thenIdle(25).thenExecute(()->close(h,SkillEffects.bonus(target,BuffKind.MOVEMENT_SPEED),.1,"expired twenty percent does not clear surviving tide"))
          .thenIdle(40).thenExecute(()->{for(var attr:List.of(Attributes.MOVEMENT_SPEED,Attributes.FLYING_SPEED)){close(h,target.getAttributeValue(attr),.32,"temporary movement leaves base and permanent research");h.assertTrue(target.getAttribute(attr).getModifier(permanent)!=null,"permanent research retained");}pass("MOVEMENT_CAP_BOTH_ATTRIBUTES_PERMANENT");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=380)
    public static void allCanceledOrZeroHealPulsesCannotAwardCompletionTide(GameTestHelper h) {
        floor(h);var a=jelly(h,"CancelHeal",3,5);var t1=slime(h,"CancelHeal",6,5);var b=jelly(h,"CancelHeal",3,14);var t2=slime(h,"CancelHeal",6,14);
        ready(h,a,t1,b,t2).thenExecute(()->{t1.setHealth(20);t2.setHealth(20);watch(t1);watch(t2);PROBE.modes.put(t1.getUUID(),1);PROBE.modes.put(t2.getUUID(),2);require(h,cast(h,a,t1),SkillResult.SUCCESS,"cancel event fixture cast");require(h,cast(h,b,t2),SkillResult.SUCCESS,"zero event fixture cast");})
          .thenIdle(125).thenExecute(()->{for(var target:List.of(t1,t2)){close(h,target.getHealth(),20,"canceled/zero event cannot write HP");h.assertTrue(SkillEffects.healTicks(target)==0&&PROBE.events.get(target.getUUID())==6,"six actual events and completed lifetime");close(h,SkillEffects.bonus(target,BuffKind.MOVEMENT_SPEED),0,"no actual healing means no completion tide");}pass("CANCEL_ZERO_PULSES_NO_TIDE");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=380)
    public static void healEventOwnershipAbaOrClearPreventsCurrentPulseAndCompletion(GameTestHelper h) {
        floor(h);var a=jelly(h,"HealEvent",3,5);var t1=slime(h,"HealEvent",6,5);var b=jelly(h,"HealEvent",3,14);var t2=slime(h,"HealEvent",6,14);
        ready(h,a,t1,b,t2).thenExecute(()->{t1.setHealth(20);t2.setHealth(20);watch(t1);watch(t2);PROBE.once.put(t1.getUUID(),()->{String old=t1.getOwnerName();t1.setOwnerName(account("HealEventForeign"));t1.setOwnerName(old);});PROBE.once.put(t2.getUUID(),()->SkillEffects.clear(t2));require(h,cast(h,a,t1),SkillResult.SUCCESS,"event ABA fixture cast");require(h,cast(h,b,t2),SkillResult.SUCCESS,"event clear fixture cast");})
          .thenIdle(125).thenExecute(()->{for(var target:List.of(t1,t2)){close(h,target.getHealth(),20,"event invalidation blocks current pulse HP write");h.assertTrue(SkillEffects.healTicks(target)==0&&PROBE.events.get(target.getUUID())==1,"only invalidated first event executes");close(h,SkillEffects.bonus(target,BuffKind.MOVEMENT_SPEED),0,"event invalidation cannot complete");}pass("HEAL_EVENT_ABA_CLEAR_POSTCHECK");}).thenSucceed();
    }

    // FINAL22_EXTRA_TESTS_BEGIN
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=390)
    public static void exactFinalPulseKeepsReservationAgainstAnotherAuthenticatedCaster(GameTestHelper h) {
        floor(h);var first=jelly(h,"FinalA",4,5);var rival=jelly(h,"FinalB",5,8);var target=slime(h,"FinalA",7,5);AlliancesServerEvents.addAlliance(first.getOwnerName(),rival.getOwnerName());
        long[] castTime={0},observed={-1};boolean[] reserved={false};int[] remaining={-1},rivalCooldown={-1};SkillResult[] rivalResult={null};
        ready(h,first,rival,target).thenExecute(()->{
            target.setHealth(20);watch(target);PROBE.each.put(target.getUUID(),()->{
                if(PROBE.events.get(target.getUUID())!=6)return;PROBE.each.remove(target.getUUID());observed[0]=h.getLevel().getGameTime();reserved[0]=SkillEffects.hasHeal(target);remaining[0]=SkillEffects.healTicks(target);rivalResult[0]=cast(h,rival,target);rivalCooldown[0]=rival.rts().skills().cooldownTicks(UnitSkill.JELLY_FLOW);
            });castTime[0]=h.getLevel().getGameTime();require(h,cast(h,first,target),SkillResult.SUCCESS,"first flow before exact final pulse competition");
        }).thenIdle(121).thenExecute(()->{
            h.assertTrue(observed[0]==castTime[0]+120,"competitor executes during actual sixth event at exact expiry tick, observed="+observed[0]+" cast="+castTime[0]);h.assertTrue(reserved[0]&&remaining[0]==0,"zero displayed ticks still reserves in-progress final pulse");require(h,rivalResult[0],SkillResult.ALREADY_ACTIVE,"same-name reservation persists through final callback");h.assertTrue(rivalCooldown[0]==0,"loser never spends cooldown during final pulse");close(h,target.getHealth(),32,"original six pulses only");h.assertTrue(SkillEffects.healTicks(target)==0,"original completed");close(h,SkillEffects.bonus(target,BuffKind.MOVEMENT_SPEED),.1,"one original completion tide");ledger(h,"FinalA");ledger(h,"FinalB");pass("EXACT_FINAL_PULSE_ATOMIC_RESERVATION");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=390)
    public static void recursiveHealTickAndAmplifiedEventCannotRepeatPulsesOrExceedTwoHp(GameTestHelper h) {
        floor(h);var caster=jelly(h,"HealRecursive",4,5);var target=slime(h,"HealRecursive",7,5);boolean[] entered={false},reentered={false};
        ready(h,caster,target).thenExecute(()->{
            target.setHealth(20);watch(target);PROBE.modes.put(target.getUUID(),3);PROBE.each.put(target.getUUID(),()->{if(entered[0]){reentered[0]=true;return;}entered[0]=true;try{SkillEffects.tick(h.getLevel());}finally{entered[0]=false;}});require(h,cast(h,caster,target),SkillResult.SUCCESS,"real flow with recursive/amplifying callback");
        }).thenIdle(21).thenExecute(()->{close(h,target.getHealth(),22,"amplified event remains capped at two HP");h.assertTrue(PROBE.events.get(target.getUUID())==1&&!reentered[0],"recursive tick cannot dispatch same pulse twice");})
          .thenIdle(100).thenExecute(()->{close(h,target.getHealth(),32,"six capped pulses exactly twelve HP");h.assertTrue(PROBE.events.get(target.getUUID())==6&&!reentered[0]&&SkillEffects.healTicks(target)==0,"six non-reentrant pulses then completion");close(h,SkillEffects.bonus(target,BuffKind.MOVEMENT_SPEED),.1,"one completion reward");close(h,target.getAttributeValue(Attributes.MOVEMENT_SPEED),target.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue()*1.1,"one actual navigation modifier");pass("HEAL_CALLBACK_RECURSION_POSITIVE_AMOUNT_CAP");}).thenSucceed();
    }
    // FINAL22_EXTRA_TESTS_END

    private static boolean queued(JellyfishUnit source,BlockPos target){return UnitServerEvents.getUnitActionSlowQueue().stream().anyMatch(a->Arrays.stream(a.getUnitIds()).anyMatch(id->id==source.getId())&&a.getPreselectedBlockPos().equals(target));}
    private static void watch(LivingEntity target){if(!listening){MinecraftForge.EVENT_BUS.register(PROBE);listening=true;}PROBE.events.put(target.getUUID(),0);}
    public static final class HealProbe {
        final Map<UUID,Integer> modes=new HashMap<>(),events=new HashMap<>();final Map<UUID,Runnable> once=new HashMap<>(),each=new HashMap<>();
        @SubscribeEvent(priority=EventPriority.LOWEST) public void heal(LivingHealEvent event){UUID id=event.getEntity().getUUID();if(!events.containsKey(id))return;events.merge(id,1,Integer::sum);int mode=modes.getOrDefault(id,0);if(mode==1)event.setCanceled(true);if(mode==2)event.setAmount(0);if(mode==3)event.setAmount(100);Runnable callback=once.remove(id);if(callback!=null)callback.run();Runnable repeated=each.get(id);if(repeated!=null)repeated.run();}
    }
    private static SkillRequest request(JellyfishUnit source,Entity target){return new SkillRequest(UnitSkill.JELLY_FLOW,source.getId(),source.getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID());}
    private static SkillResult cast(GameTestHelper h,JellyfishUnit source,Entity target){return SkillService.request(playerNamed(h,source.getOwnerName()),request(source,target));}
    private static GameTestSequence ready(GameTestHelper h,Entity...entities){return h.startSequence().thenWaitUntil(()->{for(Entity e:entities)h.assertTrue(e.tickCount>3&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e,"actual published entity ready: "+e.getUUID()+" ticks="+e.tickCount);});}
    private static void require(GameTestHelper h,SkillResult actual,SkillResult expected,String why){h.assertTrue(actual==expected,why+": expected "+expected+", got "+actual);}
    private static void denied(GameTestHelper h,SkillResult result,String why){h.assertTrue(result!=SkillResult.SUCCESS,why+" denied, got "+result);}
    private static void close(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Math.abs(actual-expected)<.0003,why+": expected "+expected+", got "+actual);}
    private static void pass(String name){System.out.println("DREAMSEEKERS_JELLY_FLOW_"+name+"_PASS");}
    private static String account(String suffix){String name=PREFIX+suffix;if(PlayerServerEvents.getRTSPlayer(name)==null){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,500,500,500,123));}return name;}
    private static void ledger(GameTestHelper h,String suffix){String name=PREFIX+suffix;var r=ResourcesServerEvents.resourcesList.stream().filter(e->e.ownerName.equals(name)).findFirst().orElseThrow();h.assertTrue(r.food==500&&r.wood==500&&r.ore==500&&r.emerald==123,"ordinary healing does not debit/grant global resources");}
    private static JellyfishUnit jelly(GameTestHelper h,String suffix,int x,int z){var e=DreamseekersMod.JELLYFISH_UNIT.get().create(h.getLevel());e.setOwnerName(account(suffix));place(h,e,x,z);e.setNoAi(true);return e;}
    private static PinkSlimeUnit slime(GameTestHelper h,String suffix,int x,int z){var e=DreamseekersMod.PINK_SLIME_UNIT.get().create(h.getLevel());e.setOwnerName(account(suffix));place(h,e,x,z);e.setNoAi(true);return e;}
    private static FakePlayer playerNamed(GameTestHelper h,String name){return FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name));}
    private static void moveFixture(GameTestHelper h,Entity e,int x,int z){BlockPos p=h.absolutePos(new BlockPos(x,1,z));e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);e.setDeltaMovement(Vec3.ZERO);}
    private static void place(GameTestHelper h,Entity e,int x,int z){h.assertTrue(e!=null,"registered fixture created");moveFixture(h,e,x,z);FIXTURES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"fixture enters real world");}
    private static void floor(GameTestHelper h){BlockPos p=h.absolutePos(BlockPos.ZERO);for(int x=0;x<24;x++)for(int z=0;z<24;z++)h.getLevel().setBlock(p.offset(x,7,z),Blocks.STONE.defaultBlockState(),2);h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+24,h.getLevel().getMaxBuildHeight(),p.getZ()+24)).forEach(Entity::discard);for(int x=0;x<24;x++)for(int z=0;z<24;z++){h.getLevel().setBlock(p.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<7;y++)h.getLevel().setBlock(p.offset(x,y,z),(x==0||x==23||z==0||z==23?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}}
}
