// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.dreamseekers.bonewing.BoneWingBolt;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Real server requests, real final hurt paths and preconstructed timelines. No GUI/socket claims. */
@GameTestHolder("dreamseekers_gel_skill")
@PrefixGameTestTemplate(false)
public final class GelGuardSkillGameTests {
    private static final String BATCH="dreamseekers_gel_skill", PREFIX="GelSkill_";
    private static final List<Entity> FIXTURES=new ArrayList<>();
    private static final DamageProbe PROBE=new DamageProbe();
    private static boolean listening;
    private static final double EPS=.0002;

    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) {
        if(listening){MinecraftForge.EVENT_BUS.unregister(PROBE);listening=false;}
        PROBE.modes.clear();PROBE.events.clear();PROBE.amounts.clear();PROBE.once.clear();
        var names=PlayerServerEvents.rtsPlayers.stream().map(p->p.name).filter(n->n.startsWith(PREFIX)).toList();
        for(String a:names)for(String b:names)if(!a.equals(b))AlliancesServerEvents.removeAlliance(a,b);
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));
        ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));
        for(Entity e:FIXTURES)if(!e.isRemoved())e.discard();FIXTURES.clear();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=240)
    public static void authoritativeSenderExactIdentityAndInvalidTargetsNeverSpend(GameTestHelper h) {
        floor(h);var source=slime(h,"Authority",4,4);var own=slime(h,"Authority",7,4);
        var enemy=slime(h,"AuthorityEnemy",9,4);var distant=slime(h,"Authority",20,4);
        var wild=EntityType.PIG.create(h.getLevel());place(h,wild,6,7);wild.setNoAi(true);
        var owner=player(h,"Authority");var foreign=player(h,"AuthorityEnemy");
        ready(h,source,own,enemy,distant,wild).thenExecute(()->{
            var good=request(source,own);
            require(h,SkillService.request(null,good),SkillResult.INVALID_REQUEST,"null sender");
            require(h,SkillService.request(foreign,good),SkillResult.NOT_OWNER,"forged same-faction sender");
            require(h,SkillService.request(owner,new SkillRequest(UnitSkill.GEL_GUARD,source.getId(),UUID.randomUUID(),good.revision(),own.getId(),own.getUUID())),SkillResult.INVALID_SOURCE,"ID with wrong source UUID");
            denied(h,SkillService.request(owner,new SkillRequest(UnitSkill.GEL_GUARD,source.getId(),source.getUUID(),good.revision(),own.getId(),UUID.randomUUID())),"wrong target UUID");
            denied(h,SkillService.request(owner,request(source,source)),"self is not the requested single ally");
            denied(h,SkillService.request(owner,request(source,enemy)),"enemy target");
            denied(h,SkillService.request(owner,request(source,wild)),"wild non-unit target");
            denied(h,SkillService.request(owner,request(source,distant)),"outside six-block cast range");
            own.setOwnerName("");denied(h,SkillService.request(owner,request(source,own)),"unowned target");own.setOwnerName(source.getOwnerName());
            source.addEffect(new MobEffectInstance(MobEffectRegistrar.STUN.get(),20));
            denied(h,SkillService.request(owner,request(source,own)),"stunned source");source.removeEffect(MobEffectRegistrar.STUN.get());
            var other=h.getLevel().getServer().getLevel(Level.NETHER);
            if(other!=null){var wrongLevel=FakePlayerFactory.get(other,owner.getGameProfile());denied(h,SkillService.request(wrongLevel,request(source,own)),"cross-dimension authenticated sender");}
            h.assertTrue(source.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)==0&&SkillEffects.shieldRemaining(own)==0,"all denied requests leave cooldown and shield unchanged");
            require(h,SkillService.request(owner,request(source,own)),SkillResult.SUCCESS,"actual valid owned request");
            close(h,SkillEffects.shieldRemaining(own),12,"authoritative twelve-point shield");
            h.assertTrue(source.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)==360&&SkillEffects.shieldTicks(own)==160,"exact initial server cooldown and effect duration");
            ledger(h,"Authority");pass("AUTHORITY_IDENTITIES_TARGETS");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=240)
    public static void twoCastersSameTickOnlyOneCommitsAndReplayCannotRefresh(GameTestHelper h) {
        floor(h);var a=slime(h,"Atomic",4,5);var b=slime(h,"AtomicPeer",5,7);var target=slime(h,"Atomic",7,5);var owner=player(h,"Atomic");var peer=player(h,"AtomicPeer");
        AlliancesServerEvents.addAlliance(a.getOwnerName(),b.getOwnerName());
        SkillRequest[] first={null};long[] expires={0};
        ready(h,a,b,target).thenExecute(()->{
            first[0]=request(a,target);require(h,SkillService.request(owner,first[0]),SkillResult.SUCCESS,"first same-tick winner");
            require(h,SkillService.request(peer,request(b,target)),SkillResult.ALREADY_ACTIVE,"second same-tick caster cannot stack or spend");
            denied(h,SkillService.request(owner,first[0]),"successful request replay");
            h.assertTrue(b.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)==0,"loser keeps its whole cooldown");
            expires[0]=h.getLevel().getGameTime()+SkillEffects.shieldTicks(target);
            neutralDamage(target);hurt(target,target.damageSources().generic(),11);close(h,SkillEffects.shieldRemaining(target),1,"partially spent shield before repeated attempts");
        }).thenIdle(140).thenExecute(()->{
            require(h,SkillService.request(peer,request(b,target)),SkillResult.ALREADY_ACTIVE,"one-point nearly expired shield cannot be refreshed");
            close(h,SkillEffects.shieldRemaining(target),1,"no refill from repeated casting");
            h.assertTrue(h.getLevel().getGameTime()+SkillEffects.shieldTicks(target)==expires[0]&&b.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)==0,"attempts do not extend lifetime or debit loser");
            ledger(h,"Atomic");ledger(h,"AtomicPeer");pass("ATOMIC_DIFFERENT_SENDERS_DUPLICATE_REPLAY_NO_REFRESH");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=260)
    public static void actualRonMeleeConsumesExactlyOnceThenProtecteeGetsCounter(GameTestHelper h) {
        floor(h);var caster=slime(h,"Melee",4,5);var target=slime(h,"Melee",7,5);var enemy=slime(h,"MeleeEnemy",8,5);var owner=player(h,"Melee");
        ready(h,caster,target,enemy).thenExecute(()->{
            neutralDamage(target);damage(enemy,6);watch(target);
            require(h,SkillService.request(owner,request(caster,target)),SkillResult.SUCCESS,"real shield cast");
            float hp=target.getHealth();h.assertTrue(enemy.doHurtTarget(target),"actual RoN doHurtTarget first hit accepted");
            close(h,target.getHealth(),hp,"first actual attack absorbed");close(h,SkillEffects.shieldRemaining(target),6,"six final points consumed");
            close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),0,"no counter before depletion");
            target.invulnerableTime=0;h.assertTrue(enemy.doHurtTarget(target),"actual RoN second melee accepted");
            close(h,target.getHealth(),hp,"exact depletion still prevents HP loss");close(h,SkillEffects.shieldRemaining(target),0,"shield removed exactly at exhaustion");
            close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),.15,"protected unit receives counter");
            close(h,SkillEffects.bonus(caster,BuffKind.ATTACK_SPEED),0,"caster does not steal counter");
            h.assertTrue(PROBE.events.get(target.getUUID())==2,"one Forge final-damage event per RoN hit, not double bridge processing");
            target.invulnerableTime=0;enemy.doHurtTarget(target);close(h,target.getHealth(),hp-6,"third attack not absorbed by exhausted shield");
        }).thenIdle(61).thenExecute(()->{
            close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),0,"counter expires after three seconds without retrigger");pass("RON_MELEE_EXACT_BREAK_SINGLE_COUNTER");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=220)
    public static void vanillaDamageOverflowAndEnvironmentDepletionNeverCounter(GameTestHelper h) {
        floor(h);var caster=slime(h,"Vanilla",4,5);var target=slime(h,"Vanilla",7,5);var owner=player(h,"Vanilla");
        ready(h,caster,target).thenExecute(()->{
            neutralDamage(target);watch(target);require(h,SkillService.request(owner,request(caster,target)),SkillResult.SUCCESS,"shield cast");
            float hp=target.getHealth();hurt(target,target.damageSources().generic(),17);
            close(h,target.getHealth(),hp-5,"vanilla actuallyHurt subtracts only overflow");
            close(h,SkillEffects.shieldRemaining(target),0,"vanilla path consumes twelve points once");
            close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),0,"environment cannot farm elastic counter");
            h.assertTrue(PROBE.events.get(target.getUUID())==1,"one Forge event on vanilla path");pass("VANILLA_OVERFLOW_NO_ENV_COUNTER");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=260)
    public static void actualPhysicalBoltConsumesShieldWithoutExtraDamageOrVision(GameTestHelper h) {
        floor(h);var caster=slime(h,"Bolt",5,7);var target=slime(h,"Bolt",8,7);
        var enemy=DreamseekersMod.BONE_WING_UNIT.get().create(h.getLevel());enemy.setOwnerName(account("BoltEnemy"));place(h,enemy,14,7);enemy.setNoAi(true);
        double[] travel={0};float[] hp={0};
        var sequence=ready(h,caster,target,enemy).thenExecute(()->{
            neutralDamage(target);damage(enemy,18);watch(target);hp[0]=target.getHealth();
            require(h,SkillService.request(player(h,"Bolt"),request(caster,target)),SkillResult.SUCCESS,"shield before real projectile");
            enemy.performUnitRangedAttack(target,1);
            h.assertTrue(h.getLevel().getEntitiesOfClass(BoneWingBolt.class,enemy.getBoundingBox().inflate(2)).stream().anyMatch(b->b.getOwner()==enemy),"actual ranged attack creates a physical bolt");
        });
        for(int i=0;i<35;i++)sequence.thenIdle(1).thenExecute(()->{
            for(var bolt:h.getLevel().getEntitiesOfClass(BoneWingBolt.class,enemy.getBoundingBox().inflate(20)))if(bolt.getOwner()==enemy)travel[0]=Math.max(travel[0],bolt.distanceTo(enemy));
        });
        sequence.thenExecute(()->{
            h.assertTrue(travel[0]>1,"projectile traversed real world, not manually invoked hit callback");
            close(h,target.getHealth(),hp[0]-6,"one physical projectile spills only six HP through shield");
            close(h,SkillEffects.shieldRemaining(target),0,"real projectile exhausts shield");
            close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),.15,"enemy projectile genuinely triggers elastic counter");
            h.assertTrue(PROBE.events.get(target.getUUID())==1&&!target.isCurrentlyGlowing(),"one actual impact, no global glow/vision added");pass("REAL_PHYSICAL_PROJECTILE_SHIELD");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=240)
    public static void canceledAndZeroFinalDamageDoNotSpendShieldOrTriggerCounter(GameTestHelper h) {
        floor(h);var caster=slime(h,"Cancel",4,5);var target=slime(h,"Cancel",7,5);var enemy=slime(h,"CancelEnemy",8,5);
        ready(h,caster,target,enemy).thenExecute(()->{
            neutralDamage(target);damage(enemy,30);watch(target);require(h,SkillService.request(player(h,"Cancel"),request(caster,target)),SkillResult.SUCCESS,"shield cast");
            float hp=target.getHealth();PROBE.modes.put(target.getUUID(),1);enemy.doHurtTarget(target);
            close(h,target.getHealth(),hp,"RoN final-event cancellation prevents protected HP damage");close(h,SkillEffects.shieldRemaining(target),12,"canceled RoN hit never touches shield");
            PROBE.modes.put(target.getUUID(),2);target.invulnerableTime=0;enemy.doHurtTarget(target);
            close(h,target.getHealth(),hp,"zero final amount prevents protected HP damage");close(h,SkillEffects.shieldRemaining(target),12,"zero RoN hit never touches shield");
            PROBE.modes.put(target.getUUID(),1);hurt(target,target.damageSources().generic(),30);
            PROBE.modes.put(target.getUUID(),2);hurt(target,target.damageSources().generic(),30);
            close(h,target.getHealth(),hp,"vanilla cancel and zero also leave HP intact");close(h,SkillEffects.shieldRemaining(target),12,"vanilla cancel/zero preserve all shield");
            close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),0,"no actual damage means no counter");
            PROBE.modes.remove(target.getUUID());pass("CANCEL_ZERO_BOTH_FINAL_PATHS");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=240)
    public static void physicalArmorResistanceAndVanillaAbsorptionResolveBeforeShield(GameTestHelper h) {
        floor(h);var caster=slime(h,"Armor",4,5);var target=slime(h,"Armor",7,5);var enemy=slime(h,"ArmorEnemy",8,5);
        ready(h,caster,target,enemy).thenExecute(()->{
            neutralDamage(target);target.getAttribute(Attributes.ARMOR).setBaseValue(10);target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,100,0));damage(enemy,10);
            watch(target);require(h,SkillService.request(player(h,"Armor"),request(caster,target)),SkillResult.SUCCESS,"shield cast before layered defenses");
            target.setAbsorptionAmount(100);float hp=target.getHealth();enemy.doHurtTarget(target);
            close(h,SkillEffects.shieldRemaining(target),12,"existing vanilla absorption handles fully absorbed damage first");
            h.assertTrue(target.getAbsorptionAmount()<100,"real native absorption is spent, not fictitious shield UI");
            target.setAbsorptionAmount(1);target.invulnerableTime=0;enemy.doHurtTarget(target);
            float actual=PROBE.amounts.get(target.getUUID());h.assertTrue(actual>0&&actual<9,"armor/resistance/one absorption genuinely reduce final incoming amount: "+actual);
            close(h,SkillEffects.shieldRemaining(target),12-actual,"only final post-defense amount consumes shield");close(h,target.getHealth(),hp,"layered defenses and shield prevent remaining HP damage");
            close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),0,"partial shield cannot counter");pass("ARMOR_MAGIC_ABSORPTION_THEN_SHIELD");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=260)
    public static void friendlySelfAndEnvironmentExhaustionCannotFarmCounter(GameTestHelper h) {
        floor(h);var c1=slime(h,"NoFarm",3,4);var t1=slime(h,"NoFarm",6,4);var c2=slime(h,"NoFarm",3,10);var t2=slime(h,"NoFarm",6,10);var c3=slime(h,"NoFarm",3,16);var t3=slime(h,"NoFarm",6,16);
        ready(h,c1,t1,c2,t2,c3,t3).thenExecute(()->{
            for(var t:List.of(t1,t2,t3))neutralDamage(t);damage(c1,12);damage(t2,12);
            var owner=player(h,"NoFarm");require(h,SkillService.request(owner,request(c1,t1)),SkillResult.SUCCESS,"friendly damage fixture shield");require(h,SkillService.request(owner,request(c2,t2)),SkillResult.SUCCESS,"self damage fixture shield");require(h,SkillService.request(owner,request(c3,t3)),SkillResult.SUCCESS,"environment fixture shield");
            // Deliberately exercise DamageSource guards; the ordinary TeamSafe attack API itself rejects allies.
            hurt(t1,t1.damageSources().mobAttack(c1),12);hurt(t2,t2.damageSources().mobAttack(t2),12);hurt(t3,t3.damageSources().generic(),12);
            for(var t:List.of(t1,t2,t3)){close(h,SkillEffects.shieldRemaining(t),0,"nonhostile actual damage may consume shield");close(h,SkillEffects.bonus(t,BuffKind.ATTACK_SPEED),0,"nonenemy exhaustion never counters");}
            pass("FRIEND_SELF_ENV_NO_COUNTER_FARM");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=420)
    public static void expiryCleanupAndSourceDeathNeverPretendEnemyBreak(GameTestHelper h) {
        floor(h);var c1=slime(h,"Expire",3,4);var t1=slime(h,"Expire",6,4);var c2=slime(h,"Expire",3,10);var t2=slime(h,"Expire",6,10);var c3=slime(h,"Expire",3,16);var t3=slime(h,"Expire",6,16);
        ready(h,c1,t1,c2,t2,c3,t3).thenExecute(()->{
            var owner=player(h,"Expire");for(var pair:List.of(new PinkSlimeUnit[]{c1,t1},new PinkSlimeUnit[]{c2,t2},new PinkSlimeUnit[]{c3,t3}))require(h,SkillService.request(owner,request(pair[0],pair[1])),SkillResult.SUCCESS,"lifecycle shield");
            SkillEffects.clear(t2);c3.hurt(c3.damageSources().genericKill(),10000);
        }).thenIdle(2).thenExecute(()->{
            close(h,SkillEffects.shieldRemaining(t2),0,"explicit cleanup removes shield");close(h,SkillEffects.shieldRemaining(t3),0,"dead source invalidates effect");
            close(h,SkillEffects.bonus(t2,BuffKind.ATTACK_SPEED),0,"cleanup has no break reward");close(h,SkillEffects.bonus(t3,BuffKind.ATTACK_SPEED),0,"source death has no break reward");
        }).thenIdle(160).thenExecute(()->{
            close(h,SkillEffects.shieldRemaining(t1),0,"shield naturally expires");close(h,SkillEffects.bonus(t1,BuffKind.ATTACK_SPEED),0,"expiry cannot synthesize enemy damage");
            h.assertTrue(c1.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)>0,"expiration does not refund cooldown");pass("EXPIRY_CLEAR_DEATH_NO_TRIGGER");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=260)
    public static void casterAndTargetSameTickOwnershipAbaInvalidateEffectsAndRequests(GameTestHelper h) {
        floor(h);var c1=slime(h,"ABA",3,4);var t1=slime(h,"ABA",6,4);var c2=slime(h,"ABA",3,10);var t2=slime(h,"ABA",6,10);var c3=slime(h,"ABA",3,16);var t3=slime(h,"ABA",6,16);
        ready(h,c1,t1,c2,t2,c3,t3).thenExecute(()->{
            String old=c1.getOwnerName(),other=account("ABAForeign");var owner=player(h,"ABA");
            require(h,SkillService.request(owner,request(c1,t1)),SkillResult.SUCCESS,"source ABA shield");
            require(h,SkillService.request(owner,request(c2,t2)),SkillResult.SUCCESS,"target ABA shield");
            var notYetCast=request(c3,t3);c3.setOwnerName(other);c3.setOwnerName(old);
            denied(h,SkillService.request(owner,notYetCast),"A-to-B-to-A cannot revive pre-transfer request");
            c1.setOwnerName(other);c1.setOwnerName(old);t2.setOwnerName(other);t2.setOwnerName(old);
            close(h,SkillEffects.shieldRemaining(t1),0,"same-tick caster ABA invalidates old effect");close(h,SkillEffects.shieldRemaining(t2),0,"same-tick target ABA invalidates old effect");
            close(h,SkillEffects.bonus(t1,BuffKind.ATTACK_SPEED),0,"source ABA no break reward");close(h,SkillEffects.bonus(t2,BuffKind.ATTACK_SPEED),0,"target ABA no break reward");
            h.assertTrue(c1.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)>0&&c3.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)==0,"ownership transfer cannot reset spent cooldown or spend refused request");pass("SOURCE_TARGET_ABA_EPOCH");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=260)
    public static void alliedOtherRosterUnitAndCoreOwnershipAbaAreHandled(GameTestHelper h) {
        floor(h);var caster=slime(h,"CoreAlly",4,5);var target=EntityRegistrar.ZOMBIE_UNIT.get().create(h.getLevel());target.setOwnerName(account("CoreTarget"));place(h,target,7,5);target.setNoAi(true);
        AlliancesServerEvents.addAlliance(caster.getOwnerName(),target.getOwnerName());
        ready(h,caster,target).thenExecute(()->{
            var owner=player(h,"CoreAlly");require(h,SkillService.request(owner,request(caster,target)),SkillResult.SUCCESS,"actual allied non-addon RoN Unit is eligible");
            close(h,SkillEffects.shieldRemaining(target),12,"real core-roster unit protected");String old=target.getOwnerName();target.setOwnerName(account("CoreTargetForeign"));target.setOwnerName(old);
            close(h,SkillEffects.shieldRemaining(target),0,"core Unit owner-data ABA observed, not just addon rts epoch");
            close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),0,"core transfer does not trigger break");pass("CROSS_ROSTER_ALLY_CORE_ABA");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=260)
    public static void allianceRevocationAndDiscardClearWithoutCooldownRefund(GameTestHelper h) {
        floor(h);var c1=slime(h,"Revoke",3,5);var t1=slime(h,"RevokeAlly",6,5);var c2=slime(h,"Revoke",3,12);var t2=slime(h,"Revoke",6,12);
        AlliancesServerEvents.addAlliance(c1.getOwnerName(),t1.getOwnerName());
        ready(h,c1,t1,c2,t2).thenExecute(()->{
            var owner=player(h,"Revoke");require(h,SkillService.request(owner,request(c1,t1)),SkillResult.SUCCESS,"allied shield");require(h,SkillService.request(owner,request(c2,t2)),SkillResult.SUCCESS,"unload fixture shield");
            AlliancesServerEvents.removeAlliance(c1.getOwnerName(),t1.getOwnerName());t2.discard();
        }).thenIdle(2).thenExecute(()->{
            close(h,SkillEffects.shieldRemaining(t1),0,"revoked alliance invalidates effect");close(h,SkillEffects.shieldRemaining(t2),0,"EntityLeaveLevel discard clears ephemeral shield");
            close(h,SkillEffects.bonus(t1,BuffKind.ATTACK_SPEED),0,"revoke has no reward");close(h,SkillEffects.bonus(t2,BuffKind.ATTACK_SPEED),0,"unload has no reward");
            h.assertTrue(c1.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)>0&&c2.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)>0,"teardown never refunds cooldown");pass("ALLIANCE_LEAVE_NO_REFUND");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=380)
    public static void strongestBuffCapExpiryAndPermanentAttributesAreNotCorrupted(GameTestHelper h) {
        floor(h);var a=slime(h,"Buff",4,4);var b=slime(h,"Buff",4,7);var target=slime(h,"Buff",7,5);
        UUID permanent=UUID.fromString("ea365a22-1c41-43bb-b59e-cc34637fc786");
        ready(h,a,b,target).thenExecute(()->{
            var attr=target.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get());attr.setBaseValue(1.3);attr.addPermanentModifier(new AttributeModifier(permanent,"fixture permanent research",.2,AttributeModifier.Operation.ADDITION));
            SkillEffects.addBuff(a,target,"weak",BuffKind.ATTACK_SPEED,.15,100);SkillEffects.addBuff(b,target,"strong",BuffKind.ATTACK_SPEED,.20,60);
            close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),.20,"same category takes strongest, never multiplicative");
            for(int i=0;i<10;i++)SkillEffects.addBuff(b,target,"cap",BuffKind.ATTACK_SPEED,.75,20);
            close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),.25,"ordinary attack speed cap is twenty-five percent");
            h.assertTrue(attr.getValue()>1.5&&attr.getBaseValue()==1.3&&attr.getModifier(permanent)!=null,"real attack attribute boosted without changing permanent research/base");
            CompoundTag saved=new CompoundTag();target.saveWithoutId(saved);
            var attributes=saved.getList("Attributes",10);int permanentCount=0;
            for(int i=0;i<attributes.size();i++){var entry=attributes.getCompound(i);if(entry.getString("Name").equals("reignofnether:attacks_per_second")){var modifiers=entry.getList("Modifiers",10);permanentCount=modifiers.size();h.assertTrue(modifiers.size()==1&&modifiers.getCompound(0).hasUUID("UUID")&&modifiers.getCompound(0).getUUID("UUID").equals(permanent),"transient skill modifier not serialized beside permanent upgrade");}}
            h.assertTrue(permanentCount==1,"saved real APS attribute found");
        }).thenIdle(22).thenExecute(()->close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),.20,"expired strongest cap falls back to still-active twenty percent"))
          .thenIdle(40).thenExecute(()->close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),.15,"one source expiry must not erase another live source"))
          .thenIdle(40).thenExecute(()->{
              var attr=target.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get());close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),0,"all temporary buffs expire");close(h,attr.getValue(),1.5,"permanent research and edited base survive exact cleanup");h.assertTrue(attr.getModifier(permanent)!=null,"permanent UUID untouched");pass("STRONGEST_CAP_FALLBACK_TRANSIENT_PERMANENT");
          }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=450)
    public static void attackSpeedBonusChangesRealRepeatedMeleeIntervals(GameTestHelper h) {
        floor(h);var caster=slime(h,"APS",3,13);var normal=slime(h,"APS",6,6);var boosted=slime(h,"APS",6,16);var v1=fox(h,"APSEnemy",7,6);var v2=fox(h,"APSEnemy",7,16);
        List<Long> normalHits=new ArrayList<>(),boostHits=new ArrayList<>();float[] previous={0,0};
        var sequence=ready(h,caster,normal,boosted,v1,v2).thenExecute(()->{
            for(var v:List.of(v1,v2)){neutralDamage(v);v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);v.setHealth(500);v.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);}
            damage(normal,1);damage(boosted,1);SkillEffects.addBuff(caster,boosted,"interval_probe",BuffKind.ATTACK_SPEED,.15,240);
            normal.setNoAi(false);boosted.setNoAi(false);normal.setUnitAttackTargetForced(v1);boosted.setUnitAttackTargetForced(v2);normal.setHoldPosition(true);boosted.setHoldPosition(true);
            previous[0]=v1.getHealth();previous[1]=v2.getHealth();
        });
        for(int i=0;i<160;i++)sequence.thenIdle(1).thenExecute(()->{
            if(v1.getHealth()<previous[0]){normalHits.add(h.getLevel().getGameTime());previous[0]=v1.getHealth();}
            if(v2.getHealth()<previous[1]){boostHits.add(h.getLevel().getGameTime());previous[1]=v2.getHealth();}
        });
        sequence.thenExecute(()->{
            h.assertTrue(normalHits.size()>=4&&boostHits.size()>=5,"both real melee goals repeatedly hit: normal="+normalHits+" boosted="+boostHits);
            double normalMean=meanInterval(normalHits),boostedMean=meanInterval(boostHits);
            h.assertTrue(boostedMean<normalMean&&boostedMean>=normalMean/1.25-1,"actual attack intervals shorten within cap: normal="+normalMean+" boosted="+boostedMean);
            close(h,boosted.getAttributeValue(AttributeRegistrar.ATTACKS_PER_SECOND.get()),normal.getAttributeValue(AttributeRegistrar.ATTACKS_PER_SECOND.get())*1.15,"RoN APS attribute, not unused vanilla attack speed");
            normal.setNoAi(true);boosted.setNoAi(true);pass("REAL_REPEATED_MELEE_INTERVALS");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=260)
    public static void packetCodecsPreserveOnlyInputsAndSharedExecutionAuthenticates(GameTestHelper h) {
        floor(h);var source=slime(h,"Codec",4,5);var target=slime(h,"Codec",7,5);var owner=player(h,"Codec");var foreign=player(h,"CodecForeign");
        ready(h,source,target).thenExecute(()->{
            var original=new SkillNetwork.Cast(request(source,target));
            FriendlyByteBuf wire=new FriendlyByteBuf(Unpooled.buffer());
            SkillNetwork.Cast decoded;
            try{original.encode(wire);decoded=SkillNetwork.Cast.decode(wire);h.assertTrue(wire.readableBytes()==0&&decoded.equals(original),"Cast ID/UUID/revision/skill fields roundtrip exactly without extra owner/cooldown data");}finally{wire.release();}
            require(h,decoded.execute(foreign),SkillResult.NOT_OWNER,"same handler service rejects spoofed sender");
            var stale=new SkillNetwork.Cast(UnitSkill.GEL_GUARD,source.getId(),UUID.randomUUID(),source.rts().skills().revision(),target.getId(),target.getUUID());
            require(h,stale.execute(owner),SkillResult.INVALID_SOURCE,"network route checks source UUID");
            SkillResult worker=java.util.concurrent.CompletableFuture.supplyAsync(()->decoded.execute(owner)).join();
            require(h,worker,SkillResult.INVALID_REQUEST,"network-equivalent request cannot mutate from worker thread");
            h.assertTrue(source.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD)==0,"all forged/off-thread packets remain side-effect free");
            require(h,decoded.execute(owner),SkillResult.SUCCESS,"decoded request uses real authoritative execution path");
            denied(h,decoded.execute(owner),"decoded packet replay is not a second cast");
            close(h,SkillEffects.shieldRemaining(target),12,"one packet success applies exactly one shield");
            var sync=new SkillNetwork.Sync(UnitSkill.GEL_GUARD,source.getId(),source.getUUID(),source.rts().skills().revision(),360);
            FriendlyByteBuf state=new FriendlyByteBuf(Unpooled.buffer());
            try{sync.encode(state);h.assertTrue(SkillNetwork.Sync.decode(state).equals(sync)&&state.readableBytes()==0,"owner-state codec preserves revision and cooldown only");}finally{state.release();}
            var clear=SkillNetwork.Sync.clear(source);h.assertTrue(clear.revision()<0&&clear.cooldownTicks()==0,"owner-cache clear carries no target/effect reward");
            ledger(h,"Codec");pass("PACKET_CODEC_EXECUTE_SENDER_REPLAY_THREAD");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=360)
    public static void nestedRealDamageCallbacksCannotDuplicateShieldOrCounterLifetime(GameTestHelper h) {
        floor(h);var caster=slime(h,"Reentrant",4,5);var target=slime(h,"Reentrant",7,5);var enemy=slime(h,"ReentrantEnemy",8,5);float[] before={0};
        ready(h,caster,target,enemy).thenExecute(()->{
            neutralDamage(target);damage(enemy,8);watch(target);
            require(h,SkillService.request(player(h,"Reentrant"),request(caster,target)),SkillResult.SUCCESS,"shield before actual nested hurt");before[0]=target.getHealth();
            // The event probe consumes its once-only closure before invoking a second genuine hurt.
            PROBE.once.put(target.getUUID(),()->{target.invulnerableTime=0;h.assertTrue(enemy.doHurtTarget(target),"nested real ordinary melee executes from final damage dispatch");});
            h.assertTrue(enemy.doHurtTarget(target),"outer actual ordinary melee executes");
            h.assertTrue(PROBE.events.get(target.getUUID())==2&&!PROBE.once.containsKey(target.getUUID()),"exactly two real final-damage dispatches, not recursive probe reuse");
            close(h,target.getHealth(),before[0]-4,"two eight-point hits minus one twelve-point shield spill exactly four HP");
            close(h,SkillEffects.shieldRemaining(target),0,"shared shield consumed once across nested damage");
            close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),.15,"one effective elastic counter, not multiplied reentry rewards");
            close(h,target.getAttributeValue(AttributeRegistrar.ATTACKS_PER_SECOND.get()),target.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getBaseValue()*1.15,"real APS modifier is applied only once");
        }).thenIdle(30).thenExecute(()->{
            target.invulnerableTime=0;enemy.doHurtTarget(target);close(h,target.getHealth(),before[0]-12,"later unshielded hit cannot recreate spent shield");
        }).thenIdle(31).thenExecute(()->{
            close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),0,"post-break hit cannot refresh the original sixty-tick counter");pass("NESTED_REAL_DAMAGE_ATOMIC_BREAK");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=280)
    public static void innerBreakCannotMakeOuterManagedCancellationZeroOrReductionRevertToLegacy(GameTestHelper h) {
        floor(h);List<PinkSlimeUnit> casters=new ArrayList<>(),targets=new ArrayList<>(),enemies=new ArrayList<>();
        for(int i=0;i<3;i++){int z=4+i*7;casters.add(slime(h,"OuterTicket"+i,3,z));targets.add(slime(h,"OuterTicket"+i,6,z));enemies.add(slime(h,"OuterTicketEnemy"+i,8,z));}
        List<Entity> all=new ArrayList<>();all.addAll(casters);all.addAll(targets);all.addAll(enemies);
        ready(h,all.toArray(Entity[]::new)).thenExecute(()->{
            for(int i=0;i<3;i++){
                PinkSlimeUnit caster=casters.get(i),target=targets.get(i),enemy=enemies.get(i);neutralDamage(target);damage(enemy,8);watch(target);
                require(h,SkillService.request(player(h,"OuterTicket"+i),request(caster,target)),SkillResult.SUCCESS,"managed shield exists when outer dispatch enters");
                float hp=target.getHealth();int mode=i+1;PROBE.modes.put(target.getUUID(),mode);
                PROBE.once.put(target.getUUID(),()->{
                    // Only the outer event is canceled/zero/reduced. Inner is a genuine uncanceled twelve-point hit.
                    PROBE.modes.remove(target.getUUID());damage(enemy,12);target.invulnerableTime=0;
                    h.assertTrue(enemy.doHurtTarget(target),"inner actual hit exhausts shield before outer Forge dispatch returns");
                    close(h,SkillEffects.shieldRemaining(target),0,"inner callback has already committed complete shield removal");damage(enemy,8);
                });
                h.assertTrue(enemy.doHurtTarget(target),"outer real hurt executes around nested break");
                close(h,target.getHealth(),hp-(mode==3?3:0),"already-managed outer must honor final event result even after inner removed shield, mode="+mode);
                h.assertTrue(PROBE.events.get(target.getUUID())==2,"one outer and one inner event, mode="+mode);
                close(h,SkillEffects.bonus(target,BuffKind.ATTACK_SPEED),.15,"only genuine inner enemy break awards one counter, mode="+mode);
            }
            pass("MANAGED_OUTER_TICKET_INNER_BREAK_CANCEL_ZERO_REDUCE");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=260)
    public static void allianceRevocationRemovesActualBuffImmediatelyAndReaddCannotResurrect(GameTestHelper h) {
        floor(h);var caster=slime(h,"Immediate",4,5);var ally=slime(h,"ImmediateAlly",7,5);var owned=slime(h,"Immediate",7,11);
        AlliancesServerEvents.addAlliance(caster.getOwnerName(),ally.getOwnerName());
        ready(h,caster,ally,owned).thenExecute(()->{
            double allyBase=ally.getAttributeValue(AttributeRegistrar.ATTACKS_PER_SECOND.get()),ownedBase=owned.getAttributeValue(AttributeRegistrar.ATTACKS_PER_SECOND.get());
            require(h,SkillService.request(player(h,"Immediate"),request(caster,ally)),SkillResult.SUCCESS,"active shield before same-tick diplomacy change");
            h.assertTrue(SkillEffects.addBuff(caster,ally,"allied_probe",BuffKind.ATTACK_SPEED,.20,120)&&SkillEffects.addBuff(caster,owned,"owned_probe",BuffKind.ATTACK_SPEED,.15,120),"actual allied and owned transient modifiers installed");
            close(h,ally.getAttributeValue(AttributeRegistrar.ATTACKS_PER_SECOND.get()),allyBase*1.20,"real allied APS modifier present before revoke");
            AlliancesServerEvents.removeAlliance(caster.getOwnerName(),ally.getOwnerName());
            close(h,ally.getAttributeValue(AttributeRegistrar.ATTACKS_PER_SECOND.get()),allyBase,"revocation removes actual attribute in same callback, not only a query or next tick");
            close(h,SkillEffects.shieldRemaining(ally),0,"same-tick revoked shield disappears without break callback");
            close(h,owned.getAttributeValue(AttributeRegistrar.ATTACKS_PER_SECOND.get()),ownedBase*1.15,"unrelated owned buff survives bounded diplomacy cleanup");
            AlliancesServerEvents.addAlliance(caster.getOwnerName(),ally.getOwnerName());
            close(h,ally.getAttributeValue(AttributeRegistrar.ATTACKS_PER_SECOND.get()),allyBase,"re-adding alliance in same tick cannot resurrect removed modifier");
            close(h,SkillEffects.shieldRemaining(ally),0,"re-adding alliance cannot recreate prior shield");
            close(h,SkillEffects.bonus(ally,BuffKind.ATTACK_SPEED),0,"no counter or resurrected effect after diplomacy ABA");pass("SAME_TICK_DIPLOMACY_ATTRIBUTE_CLEANUP");
        }).thenSucceed();
    }

    private static double meanInterval(List<Long> ticks){return (double)(ticks.get(ticks.size()-1)-ticks.get(0))/(ticks.size()-1);}
    private static void watch(LivingEntity target){if(!listening){MinecraftForge.EVENT_BUS.register(PROBE);listening=true;}PROBE.events.put(target.getUUID(),0);}
    public static final class DamageProbe {
        final Map<UUID,Integer> modes=new HashMap<>(),events=new HashMap<>();final Map<UUID,Float> amounts=new HashMap<>();final Map<UUID,Runnable> once=new HashMap<>();
        @SubscribeEvent(priority=EventPriority.LOWEST) public void damage(LivingDamageEvent event){UUID id=event.getEntity().getUUID();if(!events.containsKey(id))return;events.merge(id,1,Integer::sum);amounts.put(id,event.getAmount());int mode=modes.getOrDefault(id,0);if(mode==1)event.setCanceled(true);if(mode==2)event.setAmount(0);if(mode==3)event.setAmount(3);Runnable callback=once.remove(id);if(callback!=null)callback.run();}
    }
    private static void neutralDamage(LivingEntity target){target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);target.removeAllEffects();target.setAbsorptionAmount(0);var range=target.getAttribute(AttributeRegistrar.RANGED_DAMAGE_RESIST.get());if(range!=null)range.setBaseValue(0);}
    private static void damage(LivingEntity source,double amount){source.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(amount);source.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(amount);var crit=source.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get());if(crit!=null)crit.setBaseValue(0);}
    private static void hurt(LivingEntity target,DamageSource source,float amount){target.invulnerableTime=0;target.hurt(source,amount);}
    private static SkillRequest request(PinkSlimeUnit source,Entity target){return new SkillRequest(UnitSkill.GEL_GUARD,source.getId(),source.getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID());}
    private static GameTestSequence ready(GameTestHelper h,Entity...entities){return h.startSequence().thenWaitUntil(()->{for(Entity e:entities)h.assertTrue(e.tickCount>3&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e,"actual published entity ready: "+e.getUUID()+" ticks="+e.tickCount);});}
    private static void require(GameTestHelper h,SkillResult actual,SkillResult expected,String why){h.assertTrue(actual==expected,why+": expected "+expected+", got "+actual);}
    private static void denied(GameTestHelper h,SkillResult result,String why){h.assertTrue(result!=SkillResult.SUCCESS,why+" denied, got "+result);}
    private static void close(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Math.abs(actual-expected)<EPS,why+": expected "+expected+", got "+actual);}
    private static void pass(String name){System.out.println("DREAMSEEKERS_GEL_SKILL_"+name+"_PASS");}
    private static String account(String suffix){String name=PREFIX+suffix;if(PlayerServerEvents.getRTSPlayer(name)==null){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,500,500,500,123));}return name;}
    private static void ledger(GameTestHelper h,String suffix){String name=PREFIX+suffix;var r=ResourcesServerEvents.resourcesList.stream().filter(e->e.ownerName.equals(name)).findFirst().orElseThrow();h.assertTrue(r.food==500&&r.wood==500&&r.ore==500&&r.emerald==123,"ordinary free support does not debit/grant global resources");}
    private static PinkSlimeUnit slime(GameTestHelper h,String suffix,int x,int z){var e=DreamseekersMod.PINK_SLIME_UNIT.get().create(h.getLevel());e.setOwnerName(account(suffix));place(h,e,x,z);e.setNoAi(true);return e;}
    private static GoldenFoxUnit fox(GameTestHelper h,String suffix,int x,int z){var e=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());e.setOwnerName(account(suffix));place(h,e,x,z);e.setNoAi(true);return e;}
    private static FakePlayer player(GameTestHelper h,String suffix){String name=PREFIX+suffix;return FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name));}
    private static void place(GameTestHelper h,Entity e,int x,int z){h.assertTrue(e!=null,"registered fixture created");BlockPos p=h.absolutePos(new BlockPos(x,1,z));e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);FIXTURES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"fixture enters real world");}
    private static void floor(GameTestHelper h){BlockPos p=h.absolutePos(BlockPos.ZERO);for(int x=0;x<24;x++)for(int z=0;z<24;z++)h.getLevel().setBlock(p.offset(x,7,z),Blocks.STONE.defaultBlockState(),2);h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+24,h.getLevel().getMaxBuildHeight(),p.getZ()+24)).forEach(Entity::discard);for(int x=0;x<24;x++)for(int z=0;z<24;z++){h.getLevel().setBlock(p.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<7;y++)h.getLevel().setBlock(p.offset(x,y,z),(x==0||x==23||z==0||z==23?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}}
}
