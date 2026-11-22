// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.*;
import com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.*;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.*;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.network.*;
import java.util.*;
import java.util.function.Consumer;

/** Real server requests, ordinary native navigation/hurt and prebuilt timelines. No GUI/socket claims. */
@GameTestHolder("dreamseekers_wind_dash")
@PrefixGameTestTemplate(false)
public final class WindDashSkillGameTests {
    private static final String BATCH="dreamseekers_wind_dash",PREFIX="WindSkill_";
    private static final List<Entity> FIXTURES=new ArrayList<>();
    private static final DamageProbe PROBE=new DamageProbe();
    private static boolean listening;
    private static final UUID PERMANENT=UUID.fromString("1d3aa511-61e0-4cde-81ad-a3c1dce61036");

    private static final List<WindFangHealthLedger> FANG_HEALTH_LEDGERS=new ArrayList<>();
    private static final List<WindFangPassiveDiagnostic> FANG_PASSIVE_DIAGNOSTICS=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) {
        for(var ledger:FANG_HEALTH_LEDGERS)MinecraftForge.EVENT_BUS.unregister(ledger);
        FANG_HEALTH_LEDGERS.clear();
        for(var diagnostic:FANG_PASSIVE_DIAGNOSTICS)MinecraftForge.EVENT_BUS.unregister(diagnostic);
        FANG_PASSIVE_DIAGNOSTICS.clear();
        if(listening){MinecraftForge.EVENT_BUS.unregister(PROBE);listening=false;}PROBE.plans.clear();PROBE.counts.clear();PROBE.ranges.clear();PROBE.fangBatches.clear();
        var names=PlayerServerEvents.rtsPlayers.stream().map(p->p.name).filter(n->n.startsWith(PREFIX)).toList();for(String a:names)for(String b:names)if(!a.equals(b))AlliancesServerEvents.removeAlliance(a,b);
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));for(Entity e:FIXTURES)if(!e.isRemoved())e.discard();FIXTURES.clear();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=900)
    public static void realSenderExactIdentityEnemyTokenRevisionAndServerThreadAreRequired(GameTestHelper h) {
        floor(h);var f=fixture(h,"Authority");var distant=enemy(h,"AuthorityEnemy",21,10);var pig=EntityType.PIG.create(h.getLevel());place(h,pig,6,7);pig.setNoAi(true);
        ready(h,f.source,f.target,f.friend,distant,pig).thenExecute(()->{
            var owner=playerNamed(h,f.source.getOwnerName());SkillRequest good=request(f.source,f.target);
            require(h,SkillService.request(null,good),SkillResult.INVALID_REQUEST,"real sender required");require(h,SkillService.request(player(h,"AuthorityForeign"),good),SkillResult.NOT_OWNER,"foreign same-faction sender cannot dash");
            require(h,SkillService.request(owner,new SkillRequest(UnitSkill.WIND_DASH,f.source.getId(),UUID.randomUUID(),good.revision(),f.target.getId(),f.target.getUUID())),SkillResult.INVALID_SOURCE,"wrong source UUID");
            denied(h,SkillService.request(owner,new SkillRequest(UnitSkill.WIND_DASH,f.source.getId(),f.source.getUUID(),good.revision(),f.target.getId(),UUID.randomUUID())),"wrong target UUID");
            denied(h,SkillService.request(owner,request(f.source,f.source)),"self is not enemy token");denied(h,SkillService.request(owner,request(f.source,f.friend)),"friendly target");denied(h,SkillService.request(owner,request(f.source,pig)),"non-unit target");denied(h,SkillService.request(owner,request(f.source,distant)),"beyond seven-block cast range");
            require(h,java.util.concurrent.CompletableFuture.supplyAsync(()->SkillService.request(owner,good)).join(),SkillResult.INVALID_REQUEST,"worker thread cannot mutate dash");
            f.source.addEffect(new MobEffectInstance(MobEffectRegistrar.STUN.get(),20));require(h,SkillService.request(owner,good),SkillResult.DISABLED,"stunned source");f.source.removeEffect(MobEffectRegistrar.STUN.get());
            var nether=h.getLevel().getServer().getLevel(Level.NETHER);h.assertTrue(nether!=null,"real second dimension available");denied(h,SkillService.request(FakePlayerFactory.get(nether,owner.getGameProfile()),good),"wrong dimension sender");
            f.target.setInvisible(true);
            require(h,SkillService.request(owner,good),SkillResult.HIDDEN,"physical LOS to invisible unit cannot start dash");
            f.target.setInvisible(false);
            h.assertTrue(cd(f.source)==0&&!WindDash.isActive(f.source),"all rejected requests leave cooldown and activity untouched");require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"valid authenticated dash");h.assertTrue(cd(f.source)==440&&WindDash.isActive(f.source),"real start atomically pays twenty-two-second cooldown");denied(h,SkillService.request(owner,good),"successful request replay");command(h,f.source,UnitAction.STOP,-1,BlockPos.ZERO,false);ledger(h,"Authority");pass("SENDER_UUID_ENEMY_THREAD_REPLAY");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=900)
    public static void packetCodecCarriesOnlyEnemyIdentityAndUsesSameAuthoritativeExecution(GameTestHelper h) {
        floor(h);var f=fixture(h,"Codec");ready(h,f.source,f.target,f.friend).thenExecute(()->{
            var original=new SkillNetwork.Cast(request(f.source,f.target));FriendlyByteBuf wire=new FriendlyByteBuf(Unpooled.buffer());SkillNetwork.Cast decoded;try{original.encode(wire);decoded=SkillNetwork.Cast.decode(wire);h.assertTrue(decoded.equals(original)&&wire.readableBytes()==0,"cast codec exact IDs/UUIDs/revision without amount or owner");}finally{wire.release();}
            require(h,decoded.execute(player(h,"CodecForeign")),SkillResult.NOT_OWNER,"decoded route authenticates actual sender");f.source.setNoAi(false);require(h,decoded.execute(playerNamed(h,f.source.getOwnerName())),SkillResult.SUCCESS,"decoded route begins real dash");denied(h,decoded.execute(playerNamed(h,f.source.getOwnerName())),"packet replay");
            var state=new SkillNetwork.Sync(UnitSkill.WIND_DASH,f.source.getId(),f.source.getUUID(),f.source.rts().skills().revision(),440,false);FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());try{state.encode(buffer);h.assertTrue(state.equals(SkillNetwork.Sync.decode(buffer))&&buffer.readableBytes()==0,"owner state roundtrip has no target-position or damage output");}finally{buffer.release();}
            require(h,SkillService.setAutocast(playerNamed(h,f.source.getOwnerName()),UnitSkill.WIND_DASH,f.source.getId(),f.source.getUUID(),f.source.rts().skills().revision(),true),SkillResult.DISABLED,"dash has no automatic mode");command(h,f.source,UnitAction.STOP,-1,BlockPos.ZERO,false);pass("CODEC_AUTHORITATIVE_ENEMY_INPUT_NO_AUTO");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1020)
    public static void reachableDashMovesThroughRealCollisionAndDealsExactlyFifteenOnce(GameTestHelper h) {
        floor(h);var f=fixture(h,"Real");Vec3[] previous={null},start={null};double[] maximumStep={0},travel={0};
        var sequence=ready(h,f.source,f.target,f.friend).thenExecute(()->{start[0]=f.source.position();previous[0]=start[0];require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"real reachable start");});
        for(int i=0;i<70;i++)sequence.thenIdle(1).thenExecute(()->{double step=f.source.position().distanceTo(previous[0]);maximumStep[0]=Math.max(maximumStep[0],step);travel[0]+=step;previous[0]=f.source.position();h.assertTrue(h.getLevel().noCollision(f.source),"real 1.6-wide/3.2-high body never clips");if(!WindDash.isActive(f.source))f.source.setNoAi(true);});
        sequence.thenExecute(()->{verifyCommandRange(f);h.assertTrue(!WindDash.isActive(f.source)&&travel[0]>1&&maximumStep[0]<1.25,"real navigation moves over multiple collision-safe ticks, not teleport: travel="+travel[0]+" maxStep="+maximumStep[0]);close(h,f.target.getHealth(),185,"one genuine ten-times-one-point-five ordinary hit");h.assertTrue(commanded(f.friend,BuffKind.MOVEMENT_SPEED)&&commanded(f.friend,BuffKind.ATTACK_DAMAGE),"successful actual hit awards nearby ally command");h.assertTrue(cd(f.source)>0&&!f.target.isCurrentlyGlowing(),"paid cooldown retained, no global glow");ledger(h,"Real");pass("REAL_NAVIGATION_SINGLE_FIFTEEN_HP");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=920)
    public static void fullHeightSealedWallIsNoPathWithoutCooldownOrReward(GameTestHelper h) {
        floor(h);wall(h,8);var f=fixture(h,"Wall");ready(h,f.source,f.target,f.friend).thenExecute(()->{require(h,cast(h,f.source,f.target),SkillResult.NO_PATH,"sealed real wall is not a legal dash path");f.source.setNoAi(true);h.assertTrue(cd(f.source)==0&&!WindDash.isActive(f.source)&&!commanded(f.friend,BuffKind.MOVEMENT_SPEED),"no path does not pay cooldown or award command");close(h,f.target.getHealth(),200,"wall target untouched");pass("SEALED_WALL_NO_PATH_NO_CD");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1040)
    public static void actualClosedDoorBlocksWideBodyUntilDoorIsReallyOpened(GameTestHelper h) {
        floor(h);wall(h,8);for(int z=9;z<=11;z++)for(int y=1;y<=4;y++)set(h,8,y,z,Blocks.AIR);door(h,false);var f=fixture(h,"Door");
        ready(h,f.source,f.target,f.friend).thenExecute(()->{require(h,cast(h,f.source,f.target),SkillResult.NO_PATH,"closed center door leaves insufficient wide-body gap");f.source.setNoAi(true);h.assertTrue(cd(f.source)==0,"closed door rejection is free");door(h,true);require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"same real opening becomes reachable after physical door opens");})
          .thenWaitUntil(()->h.assertTrue(!WindDash.isActive(f.source),"opened-door dash completes"))
          .thenExecute(()->{f.source.setNoAi(true);close(h,f.target.getHealth(),185,"opened path permits genuine hit");h.assertTrue(h.getLevel().noCollision(f.source),"wide body remains collision safe");pass("REAL_CLOSED_OPEN_DOOR");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1040)
    public static void ThreeBlockClearCeilingRejectsTallBodyUntilActualCeilingRemoved(GameTestHelper h) {
        floor(h);for(int x=0;x<24;x++)for(int y=1;y<=6;y++)for(int z:new int[]{8,12})set(h,x,y,z,Blocks.STONE);for(int x=8;x<=9;x++)for(int z=9;z<=11;z++)set(h,x,4,z,Blocks.STONE);var source=knight(h,"Ceiling",5,10);var target=enemy(h,"CeilingEnemy",11,10);
        ready(h,source,target).thenExecute(()->{require(h,cast(h,source,target),SkillResult.NO_PATH,"3.2-high body cannot fit three-block clear tunnel");source.setNoAi(true);h.assertTrue(cd(source)==0,"height rejection does not spend");for(int x=8;x<=9;x++)for(int z=9;z<=11;z++)set(h,x,4,z,Blocks.AIR);require(h,cast(h,source,target),SkillResult.SUCCESS,"actual higher tunnel allows ground dash");})
          .thenWaitUntil(()->h.assertTrue(!WindDash.isActive(source),"opened-ceiling dash finishes"))
          .thenExecute(()->{source.setNoAi(true);close(h,target.getHealth(),185,"high enough tunnel allows actual hit");h.assertTrue(h.getLevel().noCollision(source),"no vertical clipping");pass("TALL_BODY_REAL_CEILING");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1040)
    public static void newlyBuiltWallCancelsActivePathWithoutRefundOrPhasing(GameTestHelper h) {
        floor(h);var f=fixture(h,"MidWall");var sequence=ready(h,f.source,f.target,f.friend).thenExecute(()->{require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"open path accepted before new construction");wall(h,8);});
        for(int i=0;i<75;i++)sequence.thenIdle(1).thenExecute(()->{h.assertTrue(h.getLevel().noCollision(f.source)&&f.source.getX()<h.absolutePos(new BlockPos(8,0,0)).getX(),"new obstacle blocks collision path without phase");if(!WindDash.isActive(f.source))f.source.setNoAi(true);});
        sequence.thenExecute(()->{h.assertTrue(!WindDash.isActive(f.source)&&cd(f.source)>0,"failed in-progress dash stays paid");close(h,f.target.getHealth(),200,"no wall-through hit");h.assertTrue(!commanded(f.friend,BuffKind.MOVEMENT_SPEED),"failed path never grants command");pass("MID_DASH_NEW_WALL_CANCEL_NO_REFUND");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1010)
    public static void enemyEscapingBeyondTwelveBlocksCancelsAndCannotResumeOnReturn(GameTestHelper h) {
        floor(h);var f=fixture(h,"Leash");ready(h,f.source,f.target,f.friend).thenExecute(()->{require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"valid initial range");moveFixture(h,f.target,21,10);})
          .thenIdle(3).thenExecute(()->{h.assertTrue(!WindDash.isActive(f.source)&&cd(f.source)>0,"target beyond twelve cancels without refund");f.source.setNoAi(true);moveFixture(h,f.target,11,10);})
          .thenIdle(65).thenExecute(()->{close(h,f.target.getHealth(),200,"return cannot resurrect old dash");h.assertTrue(!commanded(f.friend,BuffKind.MOVEMENT_SPEED),"canceled dash never awards command");pass("LIVE_TWELVE_BLOCK_LEASH_NO_RESUME");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1010)
    public static void authenticatedStopImmediatelyCancelsAndNeverResumesChase(GameTestHelper h) {
        floor(h);var f=fixture(h,"Stop");Vec3[] stopped={null};ready(h,f.source,f.target,f.friend).thenExecute(()->{require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"active dash before STOP");h.assertTrue(command(h,f.source,UnitAction.STOP,-1,BlockPos.ZERO,false),"actual authenticated STOP accepted");h.assertTrue(!WindDash.isActive(f.source),"accepted STOP cancels before later dispatch can resume old dash");})
          .thenIdle(4).thenExecute(()->stopped[0]=f.source.position()).thenIdle(40).thenExecute(()->{h.assertTrue(f.source.position().distanceToSqr(stopped[0])<.10&&f.source.getMoveGoal().getMoveTarget()==null,"no stale chase after STOP");close(h,f.target.getHealth(),200,"STOP before contact causes no skill hit");h.assertTrue(cd(f.source)>0&&!commanded(f.friend,BuffKind.MOVEMENT_SPEED),"STOP retains spent cooldown and grants no command");pass("AUTHENTICATED_STOP_NO_STALE_DASH");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=920)
    public static void sameEnemyExplicitAttackIsNewOrderAndImmediatelyCancelsDash(GameTestHelper h) {
        floor(h);var f=fixture(h,"AttackOrder");ready(h,f.source,f.target,f.friend).thenExecute(()->{require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"active dash");h.assertTrue(command(h,f.source,UnitAction.ATTACK,f.target.getId(),BlockPos.ZERO,false),"same-enemy explicit ATTACK accepted");h.assertTrue(!WindDash.isActive(f.source)&&cd(f.source)>0,"same enemy identity does not suppress explicit-order cancellation");f.source.setNoAi(true);})
          .thenIdle(8).thenExecute(()->{close(h,f.target.getHealth(),200,"frozen new ordinary order cannot execute a delayed old skill hit");h.assertTrue(!commanded(f.friend,BuffKind.MOVEMENT_SPEED),"mere command does not award skill success");pass("SAME_TARGET_ATTACK_CANCELS_SKILL");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1080)
    public static void canceledDashCleanupCannotDeleteNewAuthenticatedMove(GameTestHelper h) {
        floor(h);var f=fixture(h,"Replace");BlockPos destination=h.absolutePos(new BlockPos(5,0,20));Vec3[] start={null};ready(h,f.source,f.target,f.friend).thenExecute(()->{start[0]=f.source.position();require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"dash before replacement");h.assertTrue(command(h,f.source,UnitAction.MOVE,-1,destination,false),"new authenticated MOVE accepted");h.assertTrue(!WindDash.isActive(f.source),"MOVE cancels dash immediately");})
          .thenIdle(4).thenExecute(()->h.assertTrue(destination.equals(f.source.getMoveGoal().getMoveTarget()),"replacement MOVE survives old dash cleanup"))
          .thenIdle(45).thenExecute(()->{h.assertTrue(f.source.getZ()>start[0].z+2&&h.getLevel().noCollision(f.source),"actual new navigation proceeds instead of deleted/stale dash path");close(h,f.target.getHealth(),200,"replacement does not carry a delayed skill hit");h.assertTrue(cd(f.source)>0&&!commanded(f.friend,BuffKind.MOVEMENT_SPEED),"replacement keeps cooldown without success reward");command(h,f.source,UnitAction.STOP,-1,BlockPos.ZERO,false);pass("NEW_MOVE_SURVIVES_OLD_DASH_CLEANUP");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1160)
    public static void authenticatedShiftQueueWaitsForActiveDashAndRunsAfterIt(GameTestHelper h) {
        floor(h);var f=fixture(h,"Shift");BlockPos destination=h.absolutePos(new BlockPos(18,0,18));boolean[] observedWaiting={false};
        var sequence=ready(h,f.source,f.target,f.friend).thenExecute(()->{h.assertTrue(command(h,f.source,UnitAction.MOVE,-1,destination,true),"genuine owner Shift input accepted");require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"skill begins without deleting queued Shift");h.assertTrue(queued(f.source,destination),"accepted Shift entry survives skill start");});
        for(int i=0;i<65;i++)sequence.thenIdle(1).thenExecute(()->{if(WindDash.isActive(f.source)){observedWaiting[0]=true;h.assertTrue(queued(f.source,destination)&&!f.source.isIdle(),"active dash is non-idle and prevents early Shift dequeue");}h.assertTrue(h.getLevel().noCollision(f.source),"dash/queued navigation remains collision-safe");});
        sequence.thenIdle(70).thenExecute(()->{h.assertTrue(observedWaiting[0]&&!WindDash.isActive(f.source),"actual active waiting window observed then completed");h.assertTrue(f.source.getZ()>h.absolutePos(new BlockPos(0,0,13)).getZ(),"retained shifted destination actually executes after dash");h.assertTrue(cd(f.source)>0,"Shift does not refund skill");command(h,f.source,UnitAction.STOP,-1,BlockPos.ZERO,false);pass("AUTHENTICATED_SHIFT_WAIT_THEN_REAL_MOVE");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1050)
    public static void fifteenPointStrikeIsAppliedBeforeActualPhysicalArmor(GameTestHelper h) {
        floor(h);var f=fixture(h,"Armor");var ordinary=knight(h,"ArmorNormal",5,18);var control=enemy(h,"ArmorEnemy",11,18);double[] reduced={0};
        var seq=ready(h,f.source,f.target,f.friend,ordinary,control).thenExecute(()->{close(h,control.getHealth(),200,"ordinary control was not attacked during real ground preparation");moveFixture(h,control,6,18);f.target.getAttribute(Attributes.ARMOR).setBaseValue(10);control.getAttribute(Attributes.ARMOR).setBaseValue(10);reduced[0]=1-((Unit)f.target).getUnitPhysicalArmorPercentage();h.assertTrue(reduced[0]>0&&reduced[0]<1,"real physical armor fixture");h.assertTrue(ordinary.doHurtTarget(control),"ordinary actual melee control");close(h,200-control.getHealth(),10*reduced[0],"ordinary ten damage goes through actual armor");require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"real armored dash");});
        finish(seq,f).thenExecute(()->{close(h,200-f.target.getHealth(),15*reduced[0],"one-point-five multiplier precedes the same real armor calculation");pass("PREARMOR_FIFTEEN_VS_TEN");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1100)
    public static void nominalKnockbackUsesFiveCollisionMovesAndHonorsWallAndResistance(GameTestHelper h) {
        floor(h);List<Fixture> fs=List.of(fixture(h,"KickOpen",3,4),fixture(h,"KickWall",3,11),fixture(h,"KickResist",3,18));Vec3[] before=new Vec3[3];
        // Backstop is behind the victim, not across the approach path.
        for(int z=9;z<=13;z++)for(int y=1;y<=4;y++)set(h,10,y,z,Blocks.STONE);fs.get(2).target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        var seq=ready(h,entities(fs)).thenExecute(()->{for(int i=0;i<3;i++){before[i]=fs.get(i).target.position();require(h,cast(h,fs.get(i).source,fs.get(i).target),SkillResult.SUCCESS,"real kick fixture "+i);}});
        for(int i=0;i<70;i++)seq.thenIdle(1).thenExecute(()->{for(var f:fs){if(!WindDash.isActive(f.source))f.source.setNoAi(true);h.assertTrue(h.getLevel().noCollision(f.target),"each discrete knockback step remains collision-safe");}});
        seq.thenExecute(()->{double open=fs.get(0).target.getX()-before[0].x,wall=fs.get(1).target.getX()-before[1].x,resist=fs.get(2).target.position().distanceTo(before[2]);h.assertTrue(open>1.15&&open<=1.65,"unblocked nominal 1.5-block knockback: "+open);h.assertTrue(wall>=0&&wall<open-.5,"wall truncates movement, never forced completion: "+wall);h.assertTrue(resist<.10,"full native resistance suppresses displacement: "+resist);for(var f:fs){close(h,f.target.getHealth(),185,"resistance/collision never changes actual skill damage");h.assertTrue(WindDash.knockbackTicks(f.target)==0,"short kick plan ends");}pass("KNOCKBACK_COLLISION_RESISTANCE_FIVE_TICKS");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1090)
    public static void skillMeleeConsumesOrdinaryCooldownInsteadOfGrantingAFreeSecondHit(GameTestHelper h) {
        floor(h);var f=fixture(h,"OrdinaryCd");long[] hitAt={0};
        var seq=ready(h,f.source,f.target,f.friend).thenExecute(()->{f.target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);watch(f.target,Stage.DAMAGE,hit->hitAt[0]=h.getLevel().getGameTime());require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"skill melee before ordinary cadence probe");});
        finish(seq,f).thenExecute(()->{close(h,f.target.getHealth(),185,"one skill hit initially");f.source.setUnitAttackTargetForced(f.target);f.source.setHoldPosition(true);f.source.setNoAi(false);})
          .thenIdle(12).thenExecute(()->close(h,f.target.getHealth(),185,"ordinary goal cannot immediately deliver a free follow-up after skill"))
          .thenWaitUntil(()->h.assertTrue(f.target.getHealth()<185,"ordinary attack eventually resumes after spent interval"))
          .thenExecute(()->{long elapsed=h.getLevel().getGameTime()-hitAt[0];h.assertTrue(elapsed>=25,"actual next ordinary hit is delayed by normal interval: "+elapsed);close(h,f.target.getHealth(),175,"resumed ordinary hit is ten, not a leaked dash multiplier");f.source.setNoAi(true);pass("SKILL_SPENDS_REAL_ORDINARY_ATTACK_COOLDOWN");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1040)
    public static void livingAttackSameSourceSameVictimReentryCannotStealHitTicket(GameTestHelper h){sameSourceReentry(h,Stage.ATTACK);}
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1040)
    public static void livingHurtSameSourceSameVictimReentryCannotDuplicateMultiplier(GameTestHelper h){sameSourceReentry(h,Stage.HURT);}
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1040)
    public static void livingDamageSameSourceSameVictimReentryCannotDuplicateReceipt(GameTestHelper h){sameSourceReentry(h,Stage.DAMAGE);}

    private static void sameSourceReentry(GameTestHelper h,Stage stage) {
        floor(h);var f=fixture(h,"Same"+stage);boolean[] nested={true};
        var seq=ready(h,f.source,f.target,f.friend).thenExecute(()->{watch(f.target,stage,hit->{f.target.invulnerableTime=0;nested[0]=f.target.hurt(hit.source,10);f.target.invulnerableTime=0;});require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"real dash with early/final same-DS callback");});
        finish(seq,f).thenExecute(()->{h.assertTrue(!nested[0],"same DamageSource object and exact victim nested entry is rejected before it can claim ticket");close(h,f.target.getHealth(),185,"exactly one fifteen-point hit survives "+stage+" reentry");h.assertTrue(Arrays.equals(PROBE.counts.get(f.target.getUUID()),new int[]{1,1,1}),"one actual Forge dispatch at every layer, not recursively duplicated: "+Arrays.toString(PROBE.counts.get(f.target.getUUID())));h.assertTrue(commanded(f.friend,BuffKind.MOVEMENT_SPEED),"legitimate outer hit still succeeds");pass("SAME_DS_REENTRY_"+stage);}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1040)
    public static void differentDamageSourceNestedHitIsOrdinaryTenAndOuterIsFifteen(GameTestHelper h) {
        floor(h);var f=fixture(h,"DifferentDS");boolean[] nested={false};
        var seq=ready(h,f.source,f.target,f.friend).thenExecute(()->{watch(f.target,Stage.ATTACK,hit->{f.target.invulnerableTime=0;DamageSource other=f.target.damageSources().mobAttack(f.source);nested[0]=f.target.hurt(other,10);f.target.invulnerableTime=0;});require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"real dash with distinct-DS nested ordinary damage");});
        finish(seq,f).thenExecute(()->{h.assertTrue(nested[0],"different DS ordinary damage remains legal");close(h,f.target.getHealth(),175,"nested ordinary ten plus exact outer fifteen, not thirty");h.assertTrue(commanded(f.friend,BuffKind.MOVEMENT_SPEED),"genuine outer fifteen alone qualifies");pass("DIFFERENT_DS_NORMAL_TEN_OUTER_FIFTEEN");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1040)
    public static void sameDamageSourceOtherVictimCannotReceiveOrStealDashMultiplier(GameTestHelper h) {
        floor(h);var f=fixture(h,"OtherVictim");var other=enemy(h,"OtherVictimEnemy",15,16);boolean[] nested={false};
        var seq=ready(h,f.source,f.target,f.friend,other).thenExecute(()->{watch(f.target,Stage.DAMAGE,hit->{other.invulnerableTime=0;nested[0]=other.hurt(hit.source,10);});require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"real dash with other-victim nested damage");});
        finish(seq,f).thenExecute(()->{h.assertTrue(nested[0],"other victim remains ordinary legal damage");close(h,other.getHealth(),190,"same DS on another victim is ordinary ten");close(h,f.target.getHealth(),185,"original exact victim retains one fifteen-point ticket");h.assertTrue(commanded(f.friend,BuffKind.MOVEMENT_SPEED),"only real outer damage produces its command");pass("OTHER_VICTIM_CANNOT_STEAL_MULTIPLIER");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1040)
    public static void canceledOuterCannotClaimNestedOrdinaryHealthDamageAsSuccess(GameTestHelper h) {
        floor(h);var f=fixture(h,"NestedCanceled");var seq=ready(h,f.source,f.target,f.friend).thenExecute(()->{watch(f.target,Stage.DAMAGE,hit->{f.target.invulnerableTime=0;f.target.hurt(f.target.damageSources().mobAttack(f.source),10);f.target.invulnerableTime=0;hit.cancel();});require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"real dash with final canceled outer plus inner ordinary hit");});
        finish(seq,f).thenExecute(()->{close(h,f.target.getHealth(),190,"only nested ordinary ten damages HP");h.assertTrue(!commanded(f.friend,BuffKind.MOVEMENT_SPEED)&&WindDash.knockbackTicks(f.target)==0,"outer before/after HP delta cannot impersonate an exact receipt");h.assertTrue(cd(f.source)>0,"unsuccessful started dash remains paid");pass("CANCELED_OUTER_CANNOT_CLAIM_INNER_HP");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1100)
    public static void canceledZeroAdjustedAndAbsorptionOnlyHitsUseActualReceipt(GameTestHelper h) {
        floor(h);List<Fixture> fs=new ArrayList<>();for(int i=0;i<6;i++)fs.add(fixture(h,"FinalAmount"+i,i%2==0?3:14,4+(i/2)*7));float[] expected={0,0,7,25,7,0};
        var seq=ready(h,entities(fs)).thenExecute(()->{for(int i=0;i<fs.size();i++){var f=fs.get(i);final int mode=i;if(mode<5)watch(f.target,mode==4?Stage.HURT:Stage.DAMAGE,hit->{if(mode==0)hit.cancel();else hit.amount(mode==1?0:mode==3?25:7);});else f.target.setAbsorptionAmount(100);require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"real amount/absorption fixture "+i);}});
        finishAll(seq,fs).thenExecute(()->{for(int i=0;i<fs.size();i++){var f=fs.get(i);close(h,200-f.target.getHealth(),expected[i],"actual final amount honored exactly, fixture="+i);h.assertTrue(commanded(f.friend,BuffKind.MOVEMENT_SPEED)==(expected[i]>0),"command requires genuine HP/Gel receipt, not hurt true or vanilla absorption: "+i);}close(h,fs.get(5).target.getAbsorptionAmount(),85,"vanilla absorption consumed but is not approved success currency");pass("CANCEL_ZERO_EVENT_AMOUNTS_VANILLA_ABSORPTION");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1080)
    public static void ownerAbaDuringFinalCallbackInvalidatesCasterAndVictimReceipts(GameTestHelper h) {
        floor(h);var a=fixture(h,"OwnerCaster",3,5);var b=fixture(h,"OwnerTarget",3,17);List<Fixture> fs=List.of(a,b);
        var seq=ready(h,entities(fs)).thenExecute(()->{watch(a.target,Stage.DAMAGE,hit->{String old=a.source.getOwnerName();a.source.setOwnerName(account("OwnerForeign"));a.source.setOwnerName(old);});watch(b.target,Stage.DAMAGE,hit->{Unit unit=(Unit)b.target;String old=unit.getOwnerName();unit.setOwnerName(account("OwnerForeign"));unit.setOwnerName(old);});for(var f:fs)require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"ABA callback fixture");});
        finishAll(seq,fs).thenExecute(()->{for(var f:fs){close(h,f.target.getHealth(),200,"same-tick ABA invalidates old hit before health write");h.assertTrue(!commanded(f.friend,BuffKind.MOVEMENT_SPEED)&&cd(f.source)>0,"ABA no reward/refund");}pass("CALLBACK_SOURCE_AND_TARGET_OWNER_ABA");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1040)
    public static void enemyBecomingAllyInsideFinalCallbackCannotBeDamagedOrRewardCommand(GameTestHelper h) {
        floor(h);var allied=fixture(h,"DiplomacyHit",3,5);var hidden=fixture(h,"HiddenHit",3,17);var fs=List.of(allied,hidden);
        var seq=ready(h,entities(fs)).thenExecute(()->{
            watch(allied.target,Stage.DAMAGE,hit->AlliancesServerEvents.addAlliance(allied.source.getOwnerName(),((Unit)allied.target).getOwnerName()));
            watch(hidden.target,Stage.DAMAGE,hit->hidden.target.setInvisible(true));
            for(var f:fs)require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"both enemies visible and legal at start");
        });
        finishAll(seq,fs).thenExecute(()->{
            for(var f:fs){
                close(h,f.target.getHealth(),200,"alliance or visibility loss blocks actual final HP write");
                h.assertTrue(!commanded(f.friend,BuffKind.MOVEMENT_SPEED)&&WindDash.knockbackTicks(f.target)==0&&cd(f.source)>0,
                        "no command/knockback/refund after final callback revocation");
            }
            AlliancesServerEvents.removeAlliance(allied.source.getOwnerName(),((Unit)allied.target).getOwnerName());
            hidden.target.setInvisible(false);
            h.assertTrue(!WindDash.isActive(allied.source)&&!WindDash.isActive(hidden.source),"revoked dash cannot resume");
            pass("CALLBACK_ALLIANCE_OR_HIDDEN_INVALIDATES_RECEIPT");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1080)
    public static void sourceOrVictimDyingInsideFinalCallbackCannotCreditOuterHit(GameTestHelper h) {
        floor(h);var a=fixture(h,"DeathCaster",3,5);var b=fixture(h,"DeathTarget",3,17);List<Fixture> fs=List.of(a,b);
        var seq=ready(h,entities(fs)).thenExecute(()->{watch(a.target,Stage.DAMAGE,hit->a.source.hurt(a.source.damageSources().genericKill(),10000));watch(b.target,Stage.DAMAGE,hit->b.target.hurt(b.target.damageSources().genericKill(),10000));for(var f:fs)require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"callback death fixture");});
        finishAll(seq,fs).thenExecute(()->{close(h,a.target.getHealth(),200,"dead caster cannot commit delayed outer damage");h.assertTrue(!a.source.isAlive()&&!b.target.isAlive(),"callback deaths are genuine");for(var f:fs)h.assertTrue(!commanded(f.friend,BuffKind.MOVEMENT_SPEED),"nested death is not outer skill success");pass("CALLBACK_ENDPOINT_DEATH_NOT_OUTER_SUCCESS");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1090)
    public static void lethalOuterHitStillRewardsOnlyItsClampedRealHealthLoss(GameTestHelper h) {
        floor(h);var f=fixture(h,"Lethal");var seq=ready(h,f.source,f.target,f.friend).thenExecute(()->{f.target.setHealth(5);require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"genuine lethal dash");});
        finish(seq,f).thenExecute(()->{h.assertTrue(!f.target.isAlive()&&f.target.getHealth()==0,"actual five-HP victim killed");h.assertTrue(commanded(f.friend,BuffKind.MOVEMENT_SPEED)&&commanded(f.friend,BuffKind.ATTACK_DAMAGE),"positive own clamped health loss counts even when target just died");pass("LETHAL_OWN_RECEIPT_SUCCEEDS");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1080)
    public static void gelShieldConsumptionAndHpOverflowCombineIntoOneSuccessfulHit(GameTestHelper h) {
        floor(h);var f=fixture(h,"GelCombined");var shield=slime(h,"GelCombinedEnemy",14,10);
        var seq=ready(h,f.source,f.target,f.friend,shield).thenExecute(()->{require(h,gel(h,shield,f.target),SkillResult.SUCCESS,"real enemy twelve-point Gel guard");require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"real fifteen-point dash against Gel");});
        finish(seq,f).thenExecute(()->{close(h,SkillEffects.shieldRemaining(f.target),0,"exact enemy Gel consumed");close(h,f.target.getHealth(),197,"only three-point overflow affects HP");close(h,SkillEffects.bonus(f.friend,BuffKind.MOVEMENT_SPEED),.2,"one command multiplier, not shield plus HP stacked rewards");close(h,SkillEffects.bonus(f.friend,BuffKind.ATTACK_DAMAGE),.1,"one command attack multiplier");close(h,SkillEffects.bonus(f.target,BuffKind.ATTACK_SPEED),.15,"real hostile Gel break still grants protectee rebound");})
          .thenIdle(101).thenExecute(()->{h.assertTrue(!commanded(f.friend,BuffKind.MOVEMENT_SPEED),"one finite completion lifetime, never replayed by delayed shield handling");pass("GEL_AND_HP_ONE_REAL_SUCCESS");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1080)
    public static void innerOrdinaryHitExhaustingGelCannotGiveCanceledOuterDashCredit(GameTestHelper h) {
        floor(h);var f=fixture(h,"InnerGel");var shield=slime(h,"InnerGelEnemy",14,10);var inner=slime(h,"InnerGel",6,6);
        var seq=ready(h,f.source,f.target,f.friend,shield,inner).thenExecute(()->{damage(inner,12);require(h,gel(h,shield,f.target),SkillResult.SUCCESS,"real enemy Gel before nested final event");watch(f.target,Stage.DAMAGE,hit->{f.target.invulnerableTime=0;f.target.hurt(f.target.damageSources().mobAttack(inner),12);f.target.invulnerableTime=0;hit.amount(0);});require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"outer captures shield before inner depletion");});
        finish(seq,f).thenExecute(()->{close(h,SkillEffects.shieldRemaining(f.target),0,"inner ordinary enemy hit actually exhausted shield");close(h,f.target.getHealth(),200,"inner fully shielded and outer final zero");close(h,SkillEffects.bonus(f.target,BuffKind.ATTACK_SPEED),.15,"legitimate inner Gel break retains rebound");h.assertTrue(!commanded(f.friend,BuffKind.MOVEMENT_SPEED)&&WindDash.knockbackTicks(f.target)==0,"outer cannot claim inner consumed shield as its own receipt");pass("INNER_GEL_DEPLETION_NOT_OUTER_CREDIT");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1080)
    public static void clearingAndReplacingGelDuringFinalCallbackDoesNotExposeReplacementToOldHit(GameTestHelper h) {
        floor(h);var f=fixture(h,"ReplaceGel");var old=slime(h,"ReplaceGelEnemy",14,8);var fresh=slime(h,"ReplaceGelEnemy",14,12);SkillResult[] replaced={null};
        var seq=ready(h,f.source,f.target,f.friend,old,fresh).thenExecute(()->{require(h,gel(h,old,f.target),SkillResult.SUCCESS,"original enemy shield");watch(f.target,Stage.DAMAGE,hit->{SkillEffects.clear(f.target);replaced[0]=gel(h,fresh,f.target);});require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"outer managed strike captures original shield");});
        finish(seq,f).thenExecute(()->{require(h,replaced[0],SkillResult.SUCCESS,"genuine new caster replaces explicitly cleared shield");close(h,SkillEffects.shieldRemaining(f.target),12,"old hit cannot consume replacement effect identity");close(h,f.target.getHealth(),200,"clear also cancels dependent active dash and old managed hit fails closed");h.assertTrue(!commanded(f.friend,BuffKind.MOVEMENT_SPEED),"cleared old hit cannot award success");pass("REPLACEMENT_GEL_IDENTITY_FAIL_CLOSED");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1000)
    public static void anExistingLegalMeleeStanceNeedsNoArtificialPathMovement(GameTestHelper h) {
        floor(h);var source=knight(h,"Stand",5,10);var target=enemy(h,"StandEnemy",11,10);Vec3[] before={null};
        ready(h,source,target).thenExecute(()->{position(target,source.position().add(1.8,0,0));before[0]=source.position();h.assertTrue(source.distanceTo(target)<=2,"real prepared close stance already inside exact strike range");require(h,cast(h,source,target),SkillResult.SUCCESS,"already in a legal visible melee stance may start");})
          .thenWaitUntil(()->h.assertTrue(!WindDash.isActive(source),"close stance completes"))
          .thenExecute(()->{source.setNoAi(true);close(h,target.getHealth(),185,"close stance still performs the genuine melee hit");h.assertTrue(source.position().distanceTo(before[0])<.5&&h.getLevel().noCollision(source),"does not invent a teleport or long path for existing stance");pass("EXISTING_MELEE_STANCE_REAL_HIT");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1050)
    public static void commandSelectsFourNearestNonworkersWithUuidTiesAndSupportMovementOnly(GameTestHelper h) {
        floor(h);String owner="Selection";var source=knight(h,owner,5,10);var target=enemy(h,owner+"Enemy",11,10);
        var fox=fox(h,owner,3,3);var jelly=jelly(h,owner,5,3);var chicken=chicken(h,owner,7,3);var a=slime(h,owner,9,3);var b=slime(h,owner,11,3);var farther=slime(h,owner,13,3);
        var worker=DreamseekersMod.FIREFLY_WORKER.get().create(h.getLevel());owned(h,worker,owner,15,3);var pig=EntityType.PIG.create(h.getLevel());place(h,pig,17,3);pig.setNoAi(true);
        var spore=DreamseekersMod.JUNGLE_SPORE_CARRIER.get().create(h.getLevel());spore.setNoAi(true);boolean[] sporePublished={false};
        List<Mob> supports=List.of(fox,jelly,chicken);List<PinkSlimeUnit> tied=java.util.stream.Stream.of(a,b).sorted(Comparator.comparing(e->e.getUUID().toString())).toList();
        ready(h,source,target,fox,jelly,chicken,a,b,farther,worker,pig).thenExecute(()->{
            watch(target,Stage.DAMAGE,hit->{Vec3 p=source.position();position(fox,p.add(0,0,-1.5));position(jelly,p.add(0,0,1.8));position(chicken,p.add(-2,0,0));position(a,p.add(0,0,-2.5));position(b,p.add(0,0,-2.5));position(farther,p.add(0,0,2.8));position(worker,p.add(-.5,0,0));position(pig,p.add(-.7,0,0));position(spore,p.add(-.9,0,0));FIXTURES.add(spore);sporePublished[0]=h.getLevel().addFreshEntity(spore)&&h.getLevel().getEntitiesOfClass(LivingEntity.class,source.getBoundingBox().inflate(3)).contains(spore);});
            require(h,cast(h,source,target),SkillResult.SUCCESS,"one real success chooses recipients at impact");
        }).thenWaitUntil(()->h.assertTrue(!WindDash.isActive(source),"selection impact completed"))
          .thenExecute(()->{source.setNoAi(true);close(h,target.getHealth(),185,"selection is backed by real damage");h.assertTrue(sporePublished[0],"disabled non-Unit spore existed in actual impact query before its normal first-tick termination");for(Mob e:supports){h.assertTrue(commanded(e,BuffKind.MOVEMENT_SPEED),"nonworker support receives movement: "+e.getType());h.assertTrue(!commanded(e,BuffKind.ATTACK_DAMAGE),"nonattacker support does not receive fake attack damage");}h.assertTrue(commanded(tied.get(0),BuffKind.MOVEMENT_SPEED)&&commanded(tied.get(0),BuffKind.ATTACK_DAMAGE),"nearest tied combat recipient wins stable UUID order");for(Mob e:List.of(tied.get(1),farther,worker,pig,spore,source))h.assertTrue(!commanded(e,BuffKind.MOVEMENT_SPEED)&&!commanded(e,BuffKind.ATTACK_DAMAGE),"maximum four and source/worker/non-Unit exclusions: "+e.getType());ledger(h,owner);pass("COMMAND_NEAREST_FOUR_UUID_SUPPORT_WORKER_FILTER");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1050)
    public static void commandUsesExactThreeBlockImpactRadiusAndDoesNotRequireStayingNearby(GameTestHelper h) {
        floor(h);var source=knight(h,"Radius",5,10);var target=enemy(h,"RadiusEnemy",11,10);var inside=slime(h,"Radius",3,3);var edge=slime(h,"Radius",5,3);var outside=slime(h,"Radius",7,3);
        ready(h,source,target,inside,edge,outside).thenExecute(()->{watch(target,Stage.DAMAGE,hit->{Vec3 p=source.position();position(inside,p.add(0,0,-2.99));position(edge,p.add(-3,0,0));position(outside,p.add(0,0,3.01));System.out.println("WIND_COMMAND_EXACT_RADIUS inside="+source.distanceTo(inside)+" edge="+source.distanceTo(edge)+" outside="+source.distanceTo(outside));});require(h,cast(h,source,target),SkillResult.SUCCESS,"real radius fixture strike");})
          .thenWaitUntil(()->h.assertTrue(!WindDash.isActive(source),"radius strike finishes"))
          .thenExecute(()->{source.setNoAi(true);h.assertTrue(commanded(inside,BuffKind.MOVEMENT_SPEED)&&commanded(edge,BuffKind.MOVEMENT_SPEED)&&!commanded(outside,BuffKind.MOVEMENT_SPEED),"impact radius includes <=3 and excludes 3.01, without changing product range");moveFixture(h,inside,3,20);moveFixture(h,edge,6,20);})
          .thenIdle(20).thenExecute(()->{h.assertTrue(commanded(inside,BuffKind.MOVEMENT_SPEED)&&commanded(edge,BuffKind.MOVEMENT_SPEED),"leaving impact radius does not tether a completed command buff");})
          .thenIdle(81).thenExecute(()->{h.assertTrue(!commanded(inside,BuffKind.MOVEMENT_SPEED)&&!commanded(edge,BuffKind.MOVEMENT_SPEED),"finite five-second command expires normally");pass("COMMAND_EXACT_RADIUS_AND_UNTETHERED_DURATION");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1100)
    public static void successfulCommandIncreasesRealGroundNavigationDisplacement(GameTestHelper h){commandNavigation(h,false);}
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1100)
    public static void successfulCommandIncreasesRealFlyingNavigationDisplacement(GameTestHelper h){commandNavigation(h,true);}
    private static void commandNavigation(GameTestHelper h,boolean flying){
        floor(h);String owner=flying?"CommandFlight":"CommandGround";var source=knight(h,owner,5,20);var target=enemy(h,owner+"Enemy",11,20);Mob normal=flying?jelly(h,owner,3,6):slime(h,owner,3,6);Mob boosted=flying?jelly(h,owner,3,15):slime(h,owner,3,15);Vec3[] start=new Vec3[2];
        var seq=ready(h,source,target,normal,boosted).thenExecute(()->{watch(target,Stage.DAMAGE,hit->position(boosted,source.position().add(0,0,-2)));require(h,cast(h,source,target),SkillResult.SUCCESS,"native navigation comparison uses genuine successful command");})
          .thenWaitUntil(()->h.assertTrue(!WindDash.isActive(source),"command strike completes"))
          .thenExecute(()->{source.setNoAi(true);close(h,target.getHealth(),185,"navigation buff backed by genuine hit");close(h,SkillEffects.bonus(boosted,BuffKind.MOVEMENT_SPEED),.2,"twenty-percent live command");close(h,SkillEffects.bonus(normal,BuffKind.MOVEMENT_SPEED),0,"normal control unbuffed");moveFixture(h,normal,3,6);moveFixture(h,boosted,3,15);normal.setNoAi(false);boosted.setNoAi(false);start[0]=normal.position();start[1]=boosted.position();moveUnit(h,normal,21,6);moveUnit(h,boosted,21,15);});
        for(int i=0;i<35;i++)seq.thenIdle(1).thenExecute(()->h.assertTrue(h.getLevel().noCollision(normal)&&h.getLevel().noCollision(boosted),"native boosted movement remains collision safe"));
        seq.thenExecute(()->{double base=normal.getX()-start[0].x,fast=boosted.getX()-start[1].x;System.out.println("WIND_COMMAND_NATIVE_NAVIGATION flying="+flying+" normal="+base+" boosted="+fast);h.assertTrue(base>1&&fast>base*1.07,"actual movement, not only displayed attribute, improves: normal="+base+" boosted="+fast);if(flying)h.assertTrue(normal.isNoGravity()&&boosted.isNoGravity(),"native flight controllers retained");stopUnit(h,normal);stopUnit(h,boosted);pass(flying?"COMMAND_REAL_FLIGHT_SPEED":"COMMAND_REAL_GROUND_SPEED");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1200)
    public static void commandDamageChangesRealOrdinaryHitsAndStrongestCapsPreservePermanentResearch(GameTestHelper h){
        floor(h);var f=fixture(h,"CommandDamage");var dummy=enemy(h,"CommandDamageEnemy",18,18);double[] baseMove={0};
        var seq=ready(h,f.source,f.target,f.friend,dummy).thenExecute(()->{damage(f.friend,10);f.friend.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).addPermanentModifier(new AttributeModifier(PERMANENT,"permanent attack research",2,AttributeModifier.Operation.ADDITION));baseMove[0]=f.friend.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();f.friend.getAttribute(Attributes.MOVEMENT_SPEED).addPermanentModifier(new AttributeModifier(PERMANENT,"permanent movement research",.02,AttributeModifier.Operation.ADDITION));require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"real command before damage comparison");});
        finish(seq,f).thenExecute(()->{ordinaryHit(h,f.friend,dummy,13.2,"real command multiplies base plus permanent damage exactly once");close(h,f.friend.getAttributeValue(Attributes.MOVEMENT_SPEED),(baseMove[0]+.02)*1.2,"command applies single temporary movement modifier");h.assertTrue(SkillEffects.addBuff(f.source,f.friend,"stronger_test",BuffKind.ATTACK_DAMAGE,.8,30)&&SkillEffects.addBuff(f.source,f.friend,"stronger_test",BuffKind.MOVEMENT_SPEED,.8,30),"independent stronger provider coexists");ordinaryHit(h,f.friend,dummy,14.4,"normal damage cap twenty percent, not additive or repeatedly multiplied");close(h,SkillEffects.bonus(f.friend,BuffKind.MOVEMENT_SPEED),.25,"normal movement cap twenty-five percent");net.minecraft.nbt.CompoundTag tag=new net.minecraft.nbt.CompoundTag();f.friend.saveWithoutId(tag);h.assertTrue(countSerializedModifier(tag,PERMANENT)==2,"only two genuine permanent research modifiers serialized, never short effects");})
          .thenIdle(31).thenExecute(()->{ordinaryHit(h,f.friend,dummy,13.2,"expiry of stronger provider restores still-live command");close(h,SkillEffects.bonus(f.friend,BuffKind.MOVEMENT_SPEED),.2,"weaker surviving command is not removed with expired provider");})
          .thenIdle(70).thenExecute(()->{ordinaryHit(h,f.friend,dummy,12,"expired command returns ordinary baseline plus permanent research");close(h,f.friend.getAttributeValue(Attributes.MOVEMENT_SPEED),baseMove[0]+.02,"movement base and permanent research remain");h.assertTrue(f.friend.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).getModifier(PERMANENT)!=null&&f.friend.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(PERMANENT)!=null,"neither permanent modifier was removed");pass("COMMAND_REAL_ATTACK_DAMAGE_CAP_RESEARCH_EXPIRY");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1120)
    public static void commandLifecycleClearsOwnerAbaDeathsUnloadAndAllianceAbaWithoutRefund(GameTestHelper h){
        floor(h);List<Fixture> fs=new ArrayList<>();for(int i=0;i<6;i++)fs.add(fixture(h,"Life"+i,i<3?3:14,4+(i%3)*7));String ally=account("Life4Ally");fs.get(4).friend.setOwnerName(ally);AlliancesServerEvents.addAlliance(fs.get(4).source.getOwnerName(),ally);
        var seq=ready(h,entities(fs)).thenExecute(()->{for(var f:fs)require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"real command lifecycle fixture");});
        finishAll(seq,fs).thenExecute(()->{for(var f:fs){close(h,f.target.getHealth(),185,"lifecycle starts with actual completed damage");h.assertTrue(commanded(f.friend,BuffKind.MOVEMENT_SPEED)&&commanded(f.friend,BuffKind.ATTACK_DAMAGE),"actual completed command exists before lifecycle mutation");}String a=fs.get(0).source.getOwnerName();fs.get(0).source.setOwnerName(account("LifeForeign"));fs.get(0).source.setOwnerName(a);String b=fs.get(1).friend.getOwnerName();fs.get(1).friend.setOwnerName(account("LifeForeign"));fs.get(1).friend.setOwnerName(b);fs.get(2).source.hurt(fs.get(2).source.damageSources().genericKill(),10000);fs.get(3).friend.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);AlliancesServerEvents.removeAlliance(fs.get(4).source.getOwnerName(),ally);AlliancesServerEvents.addAlliance(fs.get(4).source.getOwnerName(),ally);fs.get(5).friend.hurt(fs.get(5).friend.damageSources().genericKill(),10000);})
          .thenIdle(1).thenExecute(()->{for(var f:fs){h.assertTrue(!commanded(f.friend,BuffKind.MOVEMENT_SPEED)&&!commanded(f.friend,BuffKind.ATTACK_DAMAGE),"old command cannot survive or resurrect across endpoint/relationship lifecycle");close(h,SkillEffects.bonus(f.friend,BuffKind.MOVEMENT_SPEED),0,"no stale movement category");h.assertTrue(cd(f.source)>0,"completed command cleanup never refunds spent dash cooldown");}h.assertTrue(!fs.get(2).source.isAlive()&&fs.get(3).friend.isRemoved()&&!fs.get(5).friend.isAlive(),"real death and unload transitions happened");pass("COMMAND_OWNER_DEATH_UNLOAD_ALLIANCE_LIFECYCLE");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1100)
    public static void repeatedSameTickDiplomacyCannotCompressFiveKnockbackSegments(GameTestHelper h){
        floor(h);var f=fixture(h,"KickTick");String a=account("UnrelatedDiplomatA"),b=account("UnrelatedDiplomatB");long[] tick={0};int[] remaining={0};Vec3[] after={null};
        ready(h,f.source,f.target,f.friend).thenExecute(()->require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"real knockback start"))
          .thenWaitUntil(()->h.assertTrue(WindDash.knockbackTicks(f.target)>0,"observe unfinished real knockback"))
          .thenExecute(()->{f.source.setNoAi(true);close(h,f.target.getHealth(),185,"knockback follows real damage");SkillEffects.tick(h.getLevel());tick[0]=h.getLevel().getGameTime();remaining[0]=WindDash.knockbackTicks(f.target);after[0]=f.target.position();h.assertTrue(remaining[0]>0&&remaining[0]<5,"one genuine segment has occurred but more remain");for(int i=0;i<20;i++){AlliancesServerEvents.addAlliance(a,b);AlliancesServerEvents.removeAlliance(a,b);SkillEffects.relationshipsChanged();SkillEffects.tick(h.getLevel());}h.assertTrue(h.getLevel().getGameTime()==tick[0]&&WindDash.knockbackTicks(f.target)==remaining[0]&&f.target.position().equals(after[0]),"twenty same-time lifecycle/tick calls cannot consume or move extra segments");})
          .thenIdle(1).thenExecute(()->h.assertTrue(WindDash.knockbackTicks(f.target)==remaining[0]-1,"exactly one next-world-tick segment, not duplicated by diplomacy"))
          .thenIdle(6).thenExecute(()->{h.assertTrue(WindDash.knockbackTicks(f.target)==0&&h.getLevel().noCollision(f.target),"finite collision-safe knockback ends normally");pass("KNOCKBACK_ONE_SEGMENT_PER_REAL_WORLD_TICK");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1100)
    public static void sourceChangingRealDimensionInFinalEventCannotCommitOldWorldHit(GameTestHelper h){
        floor(h);var f=fixture(h,"DimensionHit");Entity[] moved={null};ServerLevel other=h.getLevel().getServer().getLevel(Level.NETHER);
        var seq=ready(h,f.source,f.target,f.friend).thenExecute(()->{h.assertTrue(other!=null,"actual second server dimension exists");watch(f.target,Stage.DAMAGE,hit->{moved[0]=f.source.changeDimension(other,new ITeleporter(){@Override public net.minecraft.world.level.portal.PortalInfo getPortalInfo(Entity e,ServerLevel destination,java.util.function.Function<ServerLevel,net.minecraft.world.level.portal.PortalInfo> fallback){return new net.minecraft.world.level.portal.PortalInfo(new Vec3(2000+f.source.getX(),200,2000+f.source.getZ()),Vec3.ZERO,0,0);}@Override public Entity placeEntity(Entity entity,ServerLevel current,ServerLevel destination,float yaw,java.util.function.Function<Boolean,Entity> reposition){return reposition.apply(false);}});if(moved[0] instanceof Mob mob){mob.setNoAi(true);mob.setNoGravity(true);FIXTURES.add(mob);}});require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"real in-world start before dimension callback");});
        finish(seq,f).thenExecute(()->{h.assertTrue(moved[0]!=null&&moved[0].level()==other&&moved[0].getUUID().equals(f.source.getUUID())&&f.source.isRemoved(),"actual Entity.changeDimension replacement, not fake owner/world marker");close(h,f.target.getHealth(),200,"old-world exact hit cannot write HP after transfer");h.assertTrue(!commanded(f.friend,BuffKind.MOVEMENT_SPEED),"dimension-revoked hit does not reward command");pass("ACTUAL_DIMENSION_TRANSFER_REVOKES_OLD_HIT");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1100)
    public static void chasingWithinTetherStillHonorsFiniteCumulativePathBudget(GameTestHelper h){
        floor(h);var f=fixture(h,"PathBudget",3,10);Vec3[] previous={null};double[] distance={0};
        var seq=ready(h,f.source,f.target,f.friend).thenExecute(()->{previous[0]=f.source.position();require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"initial six-block path is legal");});
        for(int i=0;i<75;i++)seq.thenIdle(1).thenExecute(()->{distance[0]+=f.source.position().distanceTo(previous[0]);previous[0]=f.source.position();h.assertTrue(h.getLevel().noCollision(f.source),"bounded chase stays collision safe");if(WindDash.isActive(f.source)){position(f.target,f.source.position().add(6,0,0));h.assertTrue(f.source.distanceTo(f.target)<12,"fixture always stays inside tether, so cancellation is not simple range exit");}else f.source.setNoAi(true);});
        seq.thenExecute(()->{h.assertTrue(!WindDash.isActive(f.source)&&distance[0]>1&&distance[0]<=WindDash.MAX_PATH_DISTANCE+.5,"real repeated replans cannot turn one dash into unlimited travel: "+distance[0]);close(h,f.target.getHealth(),200,"perpetually six-block-ahead enemy never receives a remote hit");h.assertTrue(cd(f.source)>0&&!commanded(f.friend,BuffKind.MOVEMENT_SPEED),"finite unsuccessful chase remains paid without reward");pass("CUMULATIVE_REPLANNED_PATH_BUDGET");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1080)
    public static void genuineEnemyGelConsumptionAloneIsSuccessWithoutAnyHealthLoss(GameTestHelper h){
        floor(h);var f=fixture(h,"GelOnly");var shield=slime(h,"GelOnlyEnemy",14,10);
        var seq=ready(h,f.source,f.target,f.friend,shield).thenExecute(()->{require(h,gel(h,shield,f.target),SkillResult.SUCCESS,"real hostile Gel guard");watch(f.target,Stage.DAMAGE,hit->hit.amount(8));require(h,cast(h,f.source,f.target),SkillResult.SUCCESS,"real managed dash whose final amount fits shield");});
        finish(seq,f).thenExecute(()->{close(h,f.target.getHealth(),200,"no HP loss whatsoever");close(h,SkillEffects.shieldRemaining(f.target),4,"exactly eight valid enemy shield points consumed");h.assertTrue(commanded(f.friend,BuffKind.MOVEMENT_SPEED)&&commanded(f.friend,BuffKind.ATTACK_DAMAGE),"own shield receipt alone qualifies once");close(h,SkillEffects.bonus(f.target,BuffKind.ATTACK_SPEED),0,"partial shield consumption does not falsely break shield");pass("PURE_GEL_CONSUMPTION_REAL_SUCCESS");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1150)
    public static void actualWindCommandEnhancesNativeLineAndCircleFangHealthDamage(GameTestHelper h){fangDamage(h,false);}
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1150)
    public static void nativeFangDamageRetainsRealZealEnchantmentAfterCommandAndExpiry(GameTestHelper h){fangDamage(h,true);}
    private static void fangDamage(GameTestHelper h,boolean zeal){
        floor(h);String owner=zeal?"FangZeal":"FangBase";var source=knight(h,owner,5,21);var dashTarget=enemy(h,owner+"Enemy",11,21);List<com.solegendary.reignofnether.unit.units.villagers.EvokerUnit> casters=new ArrayList<>();List<Mob> victims=new ArrayList<>();
        for(int i=0;i<4;i++){casters.add(evoker(h,owner,4,3+i*5));victims.add(enemy(h,owner+"Enemy",6,3+i*5));}List<Entity> all=new ArrayList<>();all.add(source);all.add(dashTarget);all.addAll(casters);all.addAll(victims);boolean[] boosted={false,true,false,true};
        var ledger=new WindFangHealthLedger(h,owner,casters,victims);FANG_HEALTH_LEDGERS.add(ledger);MinecraftForge.EVENT_BUS.register(ledger);
        var diagnostic=new WindFangPassiveDiagnostic(h,owner,casters,victims);FANG_PASSIVE_DIAGNOSTICS.add(diagnostic);MinecraftForge.EVENT_BUS.register(diagnostic);diagnostic.snapshot("setup");
        ready(h,all.toArray(Entity[]::new)).thenExecute(()->{for(var e:casters){h.assertTrue(e.getMainHandItem().getItem() instanceof net.minecraft.world.item.BowItem,"original legal BOW retained");close(h,e.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).getBaseValue(),0,"test does not fake positive attribute to bypass native fixed-fang qualification");if(zeal)e.getMainHandItem().enchant(EnchantmentRegistrar.ZEAL.get(),1);}watch(dashTarget,Stage.DAMAGE,hit->{position(casters.get(1),source.position().add(0,0,-2));position(casters.get(3),source.position().add(-2,0,0));});require(h,cast(h,source,dashTarget),SkillResult.SUCCESS,"genuine Wind hit supplies Evoker command, not trusted injection");})
          .thenWaitUntil(()->h.assertTrue(!WindDash.isActive(source),"real command precursor completes"))
          .thenExecute(()->{source.setNoAi(true);close(h,dashTarget.getHealth(),185,"real precursor HP receipt");for(int i=0;i<4;i++){var e=casters.get(i);h.assertTrue(commanded(e,BuffKind.ATTACK_DAMAGE)==boosted[i],"shared combat qualification admits actual native Evoker only where truly commanded");moveFixture(h,e,4,3+i*5);/* Circle +1.05 hits only the first inner fang, excluding native outer-ring AABB overlap. */position(victims.get(i),e.position().add(i<2?1.25:1.05,0,0));e.isUsingLineFangs=i<2;diagnostic.snapshotOne("first-launch-before",i);ledger.begin(i,1);e.performUnitRangedAttack(victims.get(i),1);diagnostic.snapshotOne("first-launch-after",i);}})
          .thenIdle(50).thenExecute(()->{diagnostic.snapshot("first-endpoint-before-original-assertions");for(int i=0;i<4;i++){double raw=i<2?6:12,expected=raw*(boosted[i]?1.1:1)+(zeal?1:0);close(h,ledger.finish(i),expected,"actual spawned "+(i<2?"line":"circle")+" fang HP loss, commanded="+boosted[i]+" zeal="+zeal);h.assertTrue(!commanded(casters.get(i),BuffKind.ATTACK_SPEED),"Wind AD adapter does not invent APS blessing");}var e=casters.get(1);var v=victims.get(1);v.invulnerableTime=0;float before=v.getHealth();v.hurt(v.damageSources().indirectMagic(e,e),6);close(h,before-v.getHealth(),6+(zeal?1:0),"same Evoker's unrelated magic is not multiplied merely because command exists");})
          .thenIdle(51).thenExecute(()->{for(int i=0;i<4;i++){var e=casters.get(i);h.assertTrue(!commanded(e,BuffKind.ATTACK_DAMAGE),"temporary command expired");if(zeal)h.assertTrue(e.getMainHandItem().getEnchantmentLevel(EnchantmentRegistrar.ZEAL.get())==1,"real permanent Zeal remains on original BOW");diagnostic.snapshotOne("second-original-reset-before",i);victims.get(i).setHealth(200);victims.get(i).invulnerableTime=0;diagnostic.snapshotOne("second-original-reset-after-launch-before",i);ledger.begin(i,2);e.performUnitRangedAttack(victims.get(i),1);diagnostic.snapshotOne("second-launch-after",i);}})
          .thenIdle(50).thenExecute(()->{diagnostic.snapshot("second-endpoint-before-original-assertions");for(int i=0;i<4;i++)close(h,ledger.finish(i),(i<2?6:12)+(zeal?1:0),"normal post-expiry fang path returns exactly to native damage plus retained research");pass(zeal?"NATIVE_FANG_ZEAL_COMMAND_AND_EXPIRY":"NATIVE_FANG_LINE_CIRCLE_REAL_HP");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1200)
    public static void enhancedFangEventRevocationAndOldOwnerAbaCannotBorrowNewCommand(GameTestHelper h){
        floor(h);String owner="FangRevoke";var source=knight(h,owner,5,21);var dashTarget=enemy(h,owner+"Enemy",11,21);List<com.solegendary.reignofnether.unit.units.villagers.EvokerUnit> es=new ArrayList<>();List<Mob> vs=new ArrayList<>();for(int i=0;i<3;i++){es.add(evoker(h,owner,4,4+i*6));vs.add(enemy(h,owner+"Enemy",6,4+i*6));}var newWind=knight(h,owner,17,21);var newTarget=enemy(h,owner+"Enemy",20,21);List<Entity> all=new ArrayList<>(List.of(source,dashTarget,newWind,newTarget));all.addAll(es);all.addAll(vs);boolean[] nested={true};
        ready(h,all.toArray(Entity[]::new)).thenExecute(()->{watch(dashTarget,Stage.DAMAGE,hit->{for(int i=0;i<3;i++)position(es.get(i),source.position().add(-1.5,0,(i-1)*.5));});require(h,cast(h,source,dashTarget),SkillResult.SUCCESS,"actual command precedes all native enhanced fangs");})
          .thenWaitUntil(()->h.assertTrue(!WindDash.isActive(source),"initial real command finishes"))
          .thenExecute(()->{source.setNoAi(true);for(int i=0;i<3;i++){h.assertTrue(commanded(es.get(i),BuffKind.ATTACK_DAMAGE),"native evoker genuinely commanded");moveFixture(h,es.get(i),4,4+i*6);position(vs.get(i),es.get(i).position().add(1.25,0,0));es.get(i).performUnitRangedAttack(vs.get(i),1);}watch(vs.get(0),Stage.DAMAGE,hit->{SkillEffects.clear(es.get(0));});watch(vs.get(1),Stage.ATTACK,hit->{vs.get(1).invulnerableTime=0;nested[0]=vs.get(1).hurt(hit.source,6);vs.get(1).invulnerableTime=0;});watchFangs(es.get(2));})
          .thenWaitUntil(()->h.assertTrue(!PROBE.fangBatches.get(es.get(2).getUUID()).isEmpty(),"actual old-owner fang entities have spawned before impact"))
          .thenExecute(()->{String prior=es.get(2).getOwnerName();es.get(2).setOwnerName(account("FangRevokeForeign"));es.get(2).setOwnerName(prior);h.assertTrue(!commanded(es.get(2),BuffKind.ATTACK_DAMAGE),"owner ABA actually revokes original command");position(newTarget,newWind.position().add(1.8,0,0));watch(newTarget,Stage.DAMAGE,hit->position(es.get(2),newWind.position().add(0,0,-2)));require(h,cast(h,newWind,newTarget),SkillResult.SUCCESS,"second real Wind hit, not injection, provides fresh-owner command");})
          .thenWaitUntil(()->h.assertTrue(!WindDash.isActive(newWind),"replacement legitimate command finishes"))
          .thenExecute(()->{newWind.setNoAi(true);h.assertTrue(commanded(es.get(2),BuffKind.ATTACK_DAMAGE),"fresh command genuinely restored after ABA");})
          .thenIdle(40).thenExecute(()->{close(h,vs.get(0).getHealth(),200,"clearing active enhancement during its final event fails the whole managed fang closed");h.assertTrue(!nested[0],"same-DS native enhanced fang recursive hurt rejected");close(h,200-vs.get(1).getHealth(),6.6,"legitimate fang still lands once without recursive amplification");close(h,vs.get(2).getHealth(),200,"already spawned old-epoch fang cannot borrow newly awarded command");pass("NATIVE_FANG_EXACT_FRAME_AND_OLD_EPOCH_REVOCATION");}).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1550)
    public static void genuineFoxBlessingAcceleratesNativeEvokerFangBatchesThenRestoresMatchedCadence(GameTestHelper h){
        floor(h);var fox=fox(h,"FangFoxBoost",3,16);var normal=evoker(h,"FangFoxNormal",5,5);var fast=evoker(h,"FangFoxBoost",5,15);var v1=enemy(h,"FangFoxEnemy",10,5);var v2=enemy(h,"FangFoxEnemy",10,15);long[] start={0};double[] permanent={0};
        var seq=ready(h,fox,normal,fast,v1,v2).thenExecute(()->{for(var e:List.of(normal,fast)){e.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).addPermanentModifier(new AttributeModifier(PERMANENT,"permanent APS research",.025,AttributeModifier.Operation.ADDITION));e.setHoldPosition(true);seedBow((com.solegendary.reignofnether.unit.goals.UnitBowAttackGoal<?>)e.getAttackGoal());watchFangs(e);}for(var v:List.of(v1,v2)){v.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);v.setHealth(2000);v.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);}permanent[0]=fast.getAttributeValue(AttributeRegistrar.ATTACKS_PER_SECOND.get());close(h,normal.getAttackCooldown(),fast.getAttackCooldown(),"same native cooldown before actual blessing");require(h,SkillService.request(playerNamed(h,fox.getOwnerName()),new SkillRequest(UnitSkill.FOX_BLESSING,fox.getId(),fox.getUUID(),fox.rts().skills().revision(),fox.getId(),fox.getUUID())),SkillResult.SUCCESS,"real self-token Fox cast admits native zero-attribute Evoker attacker");h.assertTrue(SkillEffects.hasNamedBuff(fast,SkillEffects.FOX_BLESSING_KEY,BuffKind.ATTACK_SPEED)&&!SkillEffects.hasNamedBuff(normal,SkillEffects.FOX_BLESSING_KEY,BuffKind.ATTACK_SPEED),"only actual eligible nearby Evoker blessed");for(var e:List.of(normal,fast))((com.solegendary.reignofnether.unit.goals.UnitBowAttackGoal<?>)e.getAttackGoal()).setToMaxAttackCooldown();start[0]=h.getLevel().getGameTime();normal.setUnitAttackTargetForced(v1);fast.setUnitAttackTargetForced(v2);normal.setNoAi(false);fast.setNoAi(false);});
        for(int i=0;i<650;i++)seq.thenIdle(1).thenExecute(()->h.assertTrue(h.getLevel().noCollision(normal)&&h.getLevel().noCollision(fast),"native fang casters remain real stationary collision-safe actors"));
        seq.thenExecute(()->{var a=PROBE.fangBatches.get(normal.getUUID());var b=PROBE.fangBatches.get(fast.getUUID());System.out.println("WIND_STAGE_REAL_FOX_EVOKER_FANG_BATCHES start="+start[0]+" normal="+a+" blessed="+b);h.assertTrue(a.size()>=4&&b.size()>=4,"multiple actual native fang batches spawned, not a cooldown getter");h.assertTrue(b.get(0)<a.get(0),"real initial native wave arrives earlier under actual Fox blessing");h.assertTrue(v1.getHealth()<2000&&v2.getHealth()<2000,"real spawned fangs also damage actual enemies");int compared=0;for(int ordinal=1;ordinal<Math.min(a.size(),b.size());ordinal++){if(a.get(ordinal-1)-start[0]<200||b.get(ordinal-1)-start[0]<200)continue;long da=a.get(ordinal)-a.get(ordinal-1),db=b.get(ordinal)-b.get(ordinal-1);h.assertTrue(da==db,"post-expiry same RNG/native wave ordinal returns identical cadence: ordinal="+ordinal+" normal="+da+" formerlyBlessed="+db);compared++;}h.assertTrue(compared>=2,"at least two whole matching ordinary post-expiry fang intervals");close(h,fast.getAttackCooldown(),normal.getAttackCooldown(),"native cooldown returns to equal baseline");close(h,fast.getAttributeValue(AttributeRegistrar.ATTACKS_PER_SECOND.get()),permanent[0],"permanent APS remains after blessing expiry");h.assertTrue(fast.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getModifier(PERMANENT)!=null,"permanent APS modifier not erased");normal.setNoAi(true);fast.setNoAi(true);pass("REAL_FOX_EVOKER_NATIVE_BATCH_CADENCE_RESTORED");}).thenSucceed();
    }

    private record Fixture(WindKnightUnit source,Mob target,PinkSlimeUnit friend) { }
    private static Fixture fixture(GameTestHelper h,String suffix){return fixture(h,suffix,5,10);}
    private static Fixture fixture(GameTestHelper h,String suffix,int x,int z){
        // The command radius is measured at the REAL strike position, not the initial cast position.
        // Former (x+2,z+2) was edge-sensitive; retain its inferred point only for diagnostics.
        var f=new Fixture(knight(h,suffix,x,z),enemy(h,suffix+"Enemy",x+6,z),slime(h,suffix,x+3,z+2));
        listen();PROBE.ranges.put(f.target.getUUID(),new CommandRange(h,f,f.friend.position().add(-1,0,0)));return f;
    }
    private static void verifyCommandRange(Fixture f){var range=PROBE.ranges.get(f.target.getUUID());if(range==null)throw new IllegalStateException("missing fixture range probe");range.helper.assertTrue(range.hitDistance!=null,"actual Wind hit observed before command-radius assertion");range.helper.assertTrue(range.hitDistance<=WindDash.COMMAND_RANGE,"fixture friend is genuinely within unchanged three-block command radius at actual hit: "+range.hitDistance+" caster="+range.hitSource+" friend="+range.hitFriend);}
    private static Entity[] entities(List<Fixture> fs){return fs.stream().flatMap(f->java.util.stream.Stream.of(f.source,f.target,f.friend)).toArray(Entity[]::new);}
    private static GameTestSequence finish(GameTestSequence seq,Fixture f){return seq.thenWaitUntil(()->{if(WindDash.isActive(f.source))throw new GameTestAssertException("wait for real dash outcome");}).thenExecute(()->{f.source.setNoAi(true);verifyCommandRange(f);});}
    private static GameTestSequence finishAll(GameTestSequence seq,List<Fixture> fs){return seq.thenWaitUntil(()->{for(var f:fs)if(WindDash.isActive(f.source))throw new GameTestAssertException("wait for real dash outcomes");}).thenExecute(()->fs.forEach(f->{f.source.setNoAi(true);verifyCommandRange(f);}));}
    private static SkillResult gel(GameTestHelper h,PinkSlimeUnit source,Entity target){return SkillService.request(playerNamed(h,source.getOwnerName()),new SkillRequest(UnitSkill.GEL_GUARD,source.getId(),source.getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID()));}
    private static SkillRequest request(WindKnightUnit source,Entity target){return new SkillRequest(UnitSkill.WIND_DASH,source.getId(),source.getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID());}
    private static SkillResult cast(GameTestHelper h,WindKnightUnit source,Entity target){source.setNoAi(false);System.out.println("WIND_SKILL_CAST_DIAGNOSTIC testTick="+h.getTick()+" source="+groundState(source)+" target="+target.position()+" rawExactTargetPath="+rawPath(source,target));return SkillService.request(playerNamed(h,source.getOwnerName()),request(source,target));}
    private static String groundState(WindKnightUnit source){return source.getId()+"{noAI="+source.isNoAi()+",ground="+source.onGround()+",pos="+source.position()+",delta="+source.getDeltaMovement()+",collisionFree="+source.level().noCollision(source)+"}";}
    private static String rawPath(WindKnightUnit source,Entity target){var planner=new net.minecraft.world.entity.ai.navigation.GroundPathNavigation(source,source.level());planner.setCanOpenDoors(false);planner.setCanPassDoors(true);planner.setCanFloat(true);var p=planner.createPath(target,0);return p==null?"null":"reach="+p.canReach()+",nodes="+p.getNodeCount()+",end="+p.getEndNode();}
    private static int cd(WindKnightUnit source){return source.rts().skills().cooldownTicks(UnitSkill.WIND_DASH);}
    private static boolean commanded(LivingEntity target,BuffKind kind){return SkillEffects.hasNamedBuff(target,WindDash.COMMAND_KEY,kind);}
    private static boolean command(GameTestHelper h,WindKnightUnit source,UnitAction action,int targetId,BlockPos pos,boolean shift){try{ServerPlayer sender=playerNamed(h,source.getOwnerName());Connection connection=new Connection(PacketFlow.SERVERBOUND);connection.setListener(sender.connection);var ctor=NetworkEvent.Context.class.getDeclaredConstructor(Connection.class,NetworkDirection.class,int.class);ctor.setAccessible(true);NetworkEvent.Context context=ctor.newInstance(connection,NetworkDirection.PLAY_TO_SERVER,-1);h.assertTrue(context.getSender()==sender,"real Forge network context carries exact sender");return new UnitActionServerboundPacket(source.getOwnerName(),action,targetId,new int[]{source.getId()},pos,BlockPos.ZERO,shift).handle(()->context);}catch(ReflectiveOperationException e){throw new RuntimeException(e);}}
    private static boolean queued(WindKnightUnit source,BlockPos destination){return UnitServerEvents.getUnitActionSlowQueue().stream().anyMatch(a->Arrays.stream(a.getUnitIds()).anyMatch(id->id==source.getId())&&a.getPreselectedBlockPos().equals(destination));}
    private static GameTestSequence ready(GameTestHelper h,Entity...entities){
        long[] groundedAt={Long.MIN_VALUE};
        return h.startSequence().thenWaitUntil(()->{for(Entity e:entities)h.assertTrue(e.tickCount>3&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e,"actual published fixture: "+e.getUUID()+" entityTick="+e.tickCount);})
          .thenExecute(()->{
            System.out.println("WIND_SKILL_PUBLISHED testTick="+h.getTick()+" fixtures="+Arrays.stream(entities).map(e->e instanceof WindKnightUnit w?groundState(w):e.getId()+":"+e.tickCount).toList());
            // NoAI prevents vanilla travel, so tickCount alone never proved ground/path readiness.
            // Only fixture preparation enables actual physics; no setOnGround or synthetic fall is used.
            // Ordinary close-range control targets start six blocks away and are moved in only AFTER ready.
            for(Entity e:entities)if(e instanceof WindKnightUnit w){w.getTargetGoal().setTarget(null);w.getNavigation().stop();w.setNoAi(false);}
          }).thenWaitUntil(()->{
            boolean stable=true;for(Entity e:entities)if(e instanceof WindKnightUnit w)stable&=w.onGround()&&h.getLevel().noCollision(w);
            if(!stable){groundedAt[0]=Long.MIN_VALUE;h.assertTrue(false,"wait for genuine ground collision of every Wind fixture");}
            long now=h.getLevel().getGameTime();if(groundedAt[0]==Long.MIN_VALUE)groundedAt[0]=now;
            h.assertTrue(now>groundedAt[0],"two distinct real world ticks stably grounded before authoritative input");
          }).thenExecute(()->{
            System.out.println("WIND_SKILL_READY testTick="+h.getTick()+" fixtures="+Arrays.stream(entities).map(e->e instanceof WindKnightUnit w?groundState(w):e.getId()+":"+e.tickCount).toList());
            for(Entity e:entities)if(e instanceof WindKnightUnit w)w.setNoAi(true);
          });
    }
    private static void require(GameTestHelper h,SkillResult actual,SkillResult expected,String why){h.assertTrue(actual==expected,why+": expected="+expected+" actual="+actual);}
    private static void denied(GameTestHelper h,SkillResult result,String why){h.assertTrue(result!=SkillResult.SUCCESS,why+": got="+result);}
    private static void close(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Math.abs(actual-expected)<.0004,why+": expected="+expected+" actual="+actual);}
    private static void pass(String name){System.out.println("DREAMSEEKERS_WIND_DASH_"+name+"_PASS");}
    private static String account(String suffix){String name=PREFIX+suffix;if(PlayerServerEvents.getRTSPlayer(name)==null){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,500,500,500,123));}return name;}
    private static void ledger(GameTestHelper h,String suffix){var r=ResourcesServerEvents.resourcesList.stream().filter(e->e.ownerName.equals(PREFIX+suffix)).findFirst().orElseThrow();h.assertTrue(r.food==500&&r.wood==500&&r.ore==500&&r.emerald==123,"ordinary dash/command neither debits nor mints global resources");}
    // AGGRO_RANGE=0 isolates skill cleanup from legitimate new idle acquisition; explicit ordinary ATTACK remains tested.
    private static com.solegendary.reignofnether.unit.units.villagers.EvokerUnit evoker(GameTestHelper h,String owner,int x,int z){var e=EntityRegistrar.EVOKER_UNIT.get().create(h.getLevel());owned(h,e,owner,x,z);e.setupEquipmentAndUpgradesServer();return e;}
    private static void seedBow(com.solegendary.reignofnether.unit.goals.UnitBowAttackGoal<?> goal){try{var random=goal.getClass().getDeclaredField("random");var windup=goal.getClass().getDeclaredField("attackWindupTime");random.setAccessible(true);windup.setAccessible(true);((Random)random.get(goal)).setSeed(739105L);windup.setInt(goal,0);}catch(ReflectiveOperationException e){throw new RuntimeException(e);}}
    private static void watchFangs(LivingEntity source){listen();PROBE.fangBatches.put(source.getUUID(),new ArrayList<>());}
    private static JellyfishUnit jelly(GameTestHelper h,String owner,int x,int z){var e=DreamseekersMod.JELLYFISH_UNIT.get().create(h.getLevel());owned(h,e,owner,x,z);return e;}
    private static PinkChickenScout chicken(GameTestHelper h,String owner,int x,int z){var e=DreamseekersMod.PINK_CHICKEN_SCOUT.get().create(h.getLevel());owned(h,e,owner,x,z);return e;}
    private static void position(Entity e,Vec3 p){e.moveTo(p.x,p.y,p.z);e.setDeltaMovement(Vec3.ZERO);}
    private static void moveUnit(GameTestHelper h,Mob e,int x,int z){new UnitActionItem(((Unit)e).getOwnerName(),UnitAction.MOVE,-1,new int[]{e.getId()},h.absolutePos(new BlockPos(x,0,z)),BlockPos.ZERO).action(h.getLevel());}
    private static void stopUnit(GameTestHelper h,Mob e){new UnitActionItem(((Unit)e).getOwnerName(),UnitAction.STOP,-1,new int[]{e.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());}
    private static void ordinaryHit(GameTestHelper h,Mob source,LivingEntity target,double expected,String message){target.invulnerableTime=0;float before=target.getHealth();h.assertTrue(source.doHurtTarget(target),"actual native ordinary melee accepted");close(h,before-target.getHealth(),expected,message);}
    private static int countSerializedModifier(net.minecraft.nbt.CompoundTag tag,UUID id){int count=0;var attrs=tag.getList("Attributes",10);for(int i=0;i<attrs.size();i++){var mods=attrs.getCompound(i).getList("Modifiers",10);for(int j=0;j<mods.size();j++){hardenPermanent(mods.getCompound(j),id);if(mods.getCompound(j).getUUID("UUID").equals(id))count++;}}return count;}
    private static void hardenPermanent(net.minecraft.nbt.CompoundTag modifier,UUID expected){if(!modifier.hasUUID("UUID")||!modifier.getUUID("UUID").equals(expected))throw new GameTestAssertException("unexpected serialized modifier: "+modifier);}
    private static WindKnightUnit knight(GameTestHelper h,String suffix,int x,int z){var e=DreamseekersMod.WIND_KNIGHT_UNIT.get().create(h.getLevel());owned(h,e,suffix,x,z);damage(e,10);e.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);return e;}
    private static PinkSlimeUnit slime(GameTestHelper h,String suffix,int x,int z){var e=DreamseekersMod.PINK_SLIME_UNIT.get().create(h.getLevel());owned(h,e,suffix,x,z);return e;}
    private static GoldenFoxUnit fox(GameTestHelper h,String suffix,int x,int z){var e=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());owned(h,e,suffix,x,z);return e;}
    private static Mob enemy(GameTestHelper h,String suffix,int x,int z){var e=EntityRegistrar.ZOMBIE_UNIT.get().create(h.getLevel());owned(h,e,suffix,x,z);durable(e);return e;}
    private static void owned(GameTestHelper h,Mob e,String suffix,int x,int z){((Unit)e).setOwnerName(account(suffix));place(h,e,x,z);e.setNoAi(true);}
    private static FakePlayer player(GameTestHelper h,String suffix){return playerNamed(h,account(suffix));}
    private static FakePlayer playerNamed(GameTestHelper h,String name){return FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name));}
    private static void place(GameTestHelper h,Entity e,int x,int z){h.assertTrue(e!=null,"registered fixture exists");moveFixture(h,e,x,z);FIXTURES.add(e);h.assertTrue(h.getLevel().addFreshEntity(e),"fixture enters ordinary level");}
    private static void moveFixture(GameTestHelper h,Entity e,int x,int z){BlockPos p=h.absolutePos(new BlockPos(x,1,z));e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);e.setDeltaMovement(Vec3.ZERO);}
    private static void damage(LivingEntity e,double amount){e.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(amount);if(e.getAttribute(Attributes.ATTACK_DAMAGE)!=null)e.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(amount);var crit=e.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get());if(crit!=null)crit.setBaseValue(0);}
    private static void neutral(LivingEntity e){e.getAttribute(Attributes.ARMOR).setBaseValue(0);e.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);e.setAbsorptionAmount(0);for(var a:List.of(AttributeRegistrar.RANGED_DAMAGE_RESIST.get(),AttributeRegistrar.MAGIC_DAMAGE_RESIST.get()))if(e.getAttribute(a)!=null)e.getAttribute(a).setBaseValue(0);}
    private static void durable(LivingEntity e){neutral(e);e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);e.setHealth(200);e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);}
    private static void set(GameTestHelper h,int x,int y,int z,Block b){h.getLevel().setBlock(h.absolutePos(new BlockPos(x,y,z)),b.defaultBlockState(),2);}
    private static void wall(GameTestHelper h,int x){for(int z=0;z<24;z++)for(int y=1;y<=6;y++)set(h,x,y,z,Blocks.STONE);}
    private static void door(GameTestHelper h,boolean open){var state=Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING,Direction.EAST).setValue(DoorBlock.OPEN,open);h.getLevel().setBlock(h.absolutePos(new BlockPos(8,1,10)),state.setValue(DoorBlock.HALF,DoubleBlockHalf.LOWER),2);h.getLevel().setBlock(h.absolutePos(new BlockPos(8,2,10)),state.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);}
    private static void floor(GameTestHelper h){BlockPos p=h.absolutePos(BlockPos.ZERO);for(int x=0;x<24;x++)for(int z=0;z<24;z++)h.getLevel().setBlock(p.offset(x,7,z),Blocks.STONE.defaultBlockState(),2);h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+24,h.getLevel().getMaxBuildHeight(),p.getZ()+24)).forEach(Entity::discard);for(int x=0;x<24;x++)for(int z=0;z<24;z++){h.getLevel().setBlock(p.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<7;y++)h.getLevel().setBlock(p.offset(x,y,z),(x==0||x==23||z==0||z==23?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}}
    private enum Stage { ATTACK,HURT,DAMAGE }
    private static final class Hit {
        final LivingEntity victim;final DamageSource source;final net.minecraftforge.eventbus.api.Event event;
        Hit(LivingEntity victim,DamageSource source,net.minecraftforge.eventbus.api.Event event){this.victim=victim;this.source=source;this.event=event;}
        void cancel(){event.setCanceled(true);}
        void amount(float value){if(event instanceof LivingHurtEvent e)e.setAmount(value);else if(event instanceof LivingDamageEvent e)e.setAmount(value);else throw new IllegalStateException("Attack event has no mutable amount");}
    }
    private record Plan(Stage stage,Consumer<Hit> once) { }
    private static void listen(){if(!listening){MinecraftForge.EVENT_BUS.register(PROBE);listening=true;}}
    private static void watch(LivingEntity target,Stage stage,Consumer<Hit> callback){listen();PROBE.counts.put(target.getUUID(),new int[3]);PROBE.plans.put(target.getUUID(),new Plan(stage,callback));}
    private static final class CommandRange {
        final GameTestHelper helper;final Fixture fixture;final Vec3 formerFriendPoint;
        Double hitDistance;Vec3 hitSource,hitFriend;
        CommandRange(GameTestHelper helper,Fixture fixture,Vec3 formerFriendPoint){this.helper=helper;this.fixture=fixture;this.formerFriendPoint=formerFriendPoint;}
        void observe(DamageSource damageSource){
            if(hitDistance!=null||damageSource.getEntity()!=fixture.source||!WindDash.isActive(fixture.source))return;
            hitSource=fixture.source.position();hitFriend=fixture.friend.position();hitDistance=hitSource.distanceTo(hitFriend);
            System.out.println("WIND_SKILL_HIT_RANGE testTick="+helper.getTick()+" source="+fixture.source.getId()+" caster="+hitSource+" friend="+hitFriend+" actualDistance="+hitDistance+" limit="+WindDash.COMMAND_RANGE+" formerFixturePoint="+formerFriendPoint+" inferredFormerDistance="+hitSource.distanceTo(formerFriendPoint));
        }
    }
    /** Test-only isolated-tick ledger: real subsequent HP reads, never a setter/commit receipt. */
    public static final class WindFangHealthLedger {
        private final GameTestHelper helper;
        private final String caseName;
        private final List<com.solegendary.reignofnether.unit.units.villagers.EvokerUnit> casters;
        private final List<Mob> victims;
        private final Window[] windows=new Window[4];
        private static final class Window {
            final int ordinal;final float initial;
            float sample,damageTotal,healTotal,damageAmount,healAmount;
            int attacks,hurts,damages,heals,damageTicks,healTicks;
            long eventTick=Long.MIN_VALUE;DamageSource source;String failure;
            Window(int ordinal,float hp){this.ordinal=ordinal;initial=hp;sample=hp;}
            void require(boolean ok,String why){if(!ok&&failure==null)failure=why;}
        }
        WindFangHealthLedger(GameTestHelper helper,String caseName,List<com.solegendary.reignofnether.unit.units.villagers.EvokerUnit> casters,List<Mob> victims){this.helper=helper;this.caseName=caseName;this.casters=List.copyOf(casters);this.victims=List.copyOf(victims);}
        private int index(LivingEntity body){for(int i=0;i<victims.size();i++)if(victims.get(i)==body)return i;return -1;}
        private static boolean closeEnough(float a,float b){return Float.isFinite(a)&&Float.isFinite(b)&&Math.abs(a-b)<.0004;}
        private static boolean directUnitTickHeal(){
            var frames=Thread.currentThread().getStackTrace();
            for(int i=0;i+1<frames.length;i++){
                var frame=frames[i];
                if(frame.getClassName().equals(LivingEntity.class.getName())&&frame.getMethodName().equals("heal")){
                    // The first (innermost) heal must be called directly by Unit.tick, not by
                    // an effect or a callback nested inside another genuine nocturnal heal.
                    var caller=frames[i+1];
                    return caller.getClassName().equals(Unit.class.getName())&&caller.getMethodName().equals("tick");
                }
            }
            return false;
        }
        private void identities(int i,Window w){
            var level=helper.getLevel();var v=victims.get(i);var c=casters.get(i);
            w.require(v.level()==level&&c.level()==level&&!v.isRemoved()&&!c.isRemoved()&&level.getEntity(v.getId())==v&&level.getEntity(v.getUUID())==v&&level.getEntity(c.getId())==c&&level.getEntity(c.getUUID())==c,"original caster/victim identity must stay published");
        }
        void begin(int i,int ordinal){
            helper.assertTrue(windows[i]==null,"previous fang health window closed");
            windows[i]=new Window(ordinal,victims.get(i).getHealth());identities(i,windows[i]);
            windows[i].require(closeEnough(windows[i].initial,200),"original fang observation starts at finite200HP");
            System.out.println("WIND_FANG_HEALTH_WINDOW_BEGIN case="+caseName+" index="+i+" ordinal="+ordinal+" gameTime="+helper.getLevel().getGameTime()+" actorTick="+victims.get(i).tickCount+" initialHP="+windows[i].initial);
        }
        private Window event(int i){
            if(i<0||windows[i]==null)return null;var w=windows[i];identities(i,w);
            long now=helper.getLevel().getGameTime();
            w.require(w.eventTick==Long.MIN_VALUE||w.eventTick==now,"event from unsampled previous tick");w.eventTick=now;
            w.require(closeEnough(victims.get(i).getHealth(),w.sample),"HP changed before event without an isolated observed tick");
            return w;
        }
        private void damage(int i,DamageSource source,float amount,boolean canceled,int stage){
            var w=event(i);if(w==null)return;
            w.require(!canceled&&Float.isFinite(amount)&&amount>0,"canceled/nonpositive/nonfinite damage event");
            w.require(source.getEntity()==casters.get(i)&&source.getDirectEntity()==casters.get(i)&&source.is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC),"unknown or unrelated damage source");
            w.require(Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(f->f.getClassName().equals("net.minecraft.world.entity.projectile.EvokerFangs")),"damage must originate in actual native fang call stack");
            w.require(w.source==null||w.source==source,"multiple DamageSource objects in one tick");w.source=source;
            if(stage==0)w.attacks++;else if(stage==1)w.hurts++;else{w.damages++;w.damageAmount=amount;}
            w.require(w.attacks<=1&&w.hurts<=1&&w.damages<=1&&w.heals==0,"extra damage event or mixed damage/heal tick");
            System.out.println("WIND_FANG_HEALTH_DAMAGE_OBS case="+caseName+" index="+i+" ordinal="+w.ordinal+" gameTime="+w.eventTick+" stage="+stage+" amount="+amount+" canceled="+canceled+" sourceUUID="+(source.getEntity()==null?"null":source.getEntity().getUUID())+" victimUUID="+victims.get(i).getUUID()+" exactSetterReceipt=false");
        }
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
        public void windFangLedgerOriginalAttackObservation(LivingAttackEvent event){damage(index(event.getEntity()),event.getSource(),event.getAmount(),event.isCanceled(),0);}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
        public void windFangLedgerOriginalHurtObservation(LivingHurtEvent event){damage(index(event.getEntity()),event.getSource(),event.getAmount(),event.isCanceled(),1);}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
        public void windFangLedgerOriginalDamageObservation(LivingDamageEvent event){damage(index(event.getEntity()),event.getSource(),event.getAmount(),event.isCanceled(),2);}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
        public void windFangLedgerNativeHealingObservation(LivingHealEvent event){
            int i=index(event.getEntity());var w=event(i);if(w==null)return;var body=victims.get(i);
            w.heals++;w.healAmount=event.getAmount();
            w.require(!event.isCanceled()&&closeEnough(w.healAmount,1)&&w.heals==1&&w.attacks==0&&w.hurts==0&&w.damages==0,"canceled/extra/nonunit native heal or mixed damage/heal tick");
            w.require(com.solegendary.reignofnether.faction.Factions.getFaction((Unit)body).equals(com.solegendary.reignofnether.faction.Factions.MONSTERS)&&body.tickCount%Unit.MONSTER_HEALING_TICKS==0&&!com.solegendary.reignofnether.time.TimeUtils.isDay(body.level()),"not original nocturnal monster regeneration condition");
            w.require(directUnitTickHeal(),"healing must be original Unit.tick nocturnal heal call, not another effect");
            System.out.println("WIND_FANG_HEALTH_HEAL_OBS case="+caseName+" index="+i+" ordinal="+w.ordinal+" gameTime="+w.eventTick+" actorTick="+body.tickCount+" amount="+event.getAmount()+" canceled="+event.isCanceled()+" victimUUID="+body.getUUID()+" exactSetterReceipt=false");
        }
        private void sample(int i,String stage){
            var w=windows[i];if(w==null)return;identities(i,w);float now=victims.get(i).getHealth(),delta=now-w.sample;
            boolean damage=w.attacks+w.hurts+w.damages>0,heal=w.heals>0;
            w.require(!(damage&&heal),"mixed damage/heal tick cannot be attributed");
            if(damage){
                w.require(w.attacks==1&&w.hurts==1&&w.damages==1&&delta<0&&closeEnough(-delta,w.damageAmount),"actual isolated HP loss must match one accepted three-stage fang event");
                if(delta<0){w.damageTotal-=delta;w.damageTicks++;}
            }else if(heal){
                w.require(w.heals==1&&delta>0&&closeEnough(delta,w.healAmount),"actual isolated HP gain must match native healing event");
                if(delta>0){w.healTotal+=delta;w.healTicks++;}
            }else w.require(closeEnough(delta,0),"unattributed HP write without damage/heal event");
            if(damage||heal||!closeEnough(delta,0))System.out.println("WIND_FANG_HEALTH_ACTUAL_READ case="+caseName+" index="+i+" ordinal="+w.ordinal+" stage="+stage+" gameTime="+helper.getLevel().getGameTime()+" actorTick="+victims.get(i).tickCount+" beforeHP="+w.sample+" afterHP="+now+" delta="+delta+" damageTotal="+w.damageTotal+" healTotal="+w.healTotal+" failure="+w.failure+" exactSetterReceipt=false");
            w.sample=now;w.attacks=w.hurts=w.damages=w.heals=0;w.source=null;w.eventTick=Long.MIN_VALUE;
        }
        @SubscribeEvent(priority=EventPriority.LOWEST)
        public void windFangLedgerNaturalEndHealthRead(net.minecraftforge.event.TickEvent.LevelTickEvent event){if(event.phase==net.minecraftforge.event.TickEvent.Phase.END&&event.level==helper.getLevel())for(int i=0;i<4;i++)sample(i,"natural-END");}
        float finish(int i){
            var w=windows[i];helper.assertTrue(w!=null,"original fang health window exists");sample(i,"original-endpoint");
            w.require(w.damageTicks==1,"one actual damaging fang tick per original lane");
            w.require(closeEnough(victims.get(i).getHealth(),w.initial-w.damageTotal+w.healTotal),"terminal health conservation with independently observed gains/losses");
            System.out.println("WIND_FANG_HEALTH_WINDOW_END case="+caseName+" index="+i+" ordinal="+w.ordinal+" initialHP="+w.initial+" finalHP="+victims.get(i).getHealth()+" observedDamage="+w.damageTotal+" observedHealing="+w.healTotal+" damageTicks="+w.damageTicks+" healTicks="+w.healTicks+" failure="+w.failure+" exactSetterReceipt=false");
            helper.assertTrue(w.failure==null,"strict isolated-tick fang health ledger: "+w.failure);windows[i]=null;return w.damageTotal;
        }
    }
    /** Passive original-Zombie observations. END health reads are not exact setHealth receipts. */
    public static final class WindFangPassiveDiagnostic {
        private final GameTestHelper helper;
        private final String caseName;
        private final List<com.solegendary.reignofnether.unit.units.villagers.EvokerUnit> casters;
        private final List<Mob> victims;
        private final float[] previousHealth=new float[4];
        private final int[] previousActorTick=new int[4];
        private final long[] previousGameTime=new long[4];
        private final boolean[] sampled=new boolean[4];
        WindFangPassiveDiagnostic(GameTestHelper helper,String caseName,List<com.solegendary.reignofnether.unit.units.villagers.EvokerUnit> casters,List<Mob> victims){this.helper=helper;this.caseName=caseName;this.casters=List.copyOf(casters);this.victims=List.copyOf(victims);}
        private int exactVictim(LivingEntity body){for(int i=0;i<victims.size();i++)if(victims.get(i)==body)return i;return -1;}
        private static String identity(Entity body){return body==null?"null":body.getType()+"/id="+body.getId()+"/uuid="+body.getUUID()+"/object="+System.identityHashCode(body)+"/owner="+(body instanceof Unit unit?unit.getOwnerName():"non-unit")+"/removed="+body.isRemoved();}
        void snapshot(String stage){for(int i=0;i<victims.size();i++)snapshotOne(stage,i);}
        void snapshotOne(String stage,int i){
            var victim=victims.get(i);var caster=casters.get(i);var level=helper.getLevel();
            System.out.println("WIND_FANG_PASSIVE_STATE case="+caseName+" stage="+stage+" index="+i+" testTick="+helper.getTick()+" gameTime="+level.getGameTime()+" dayTime="+level.getDayTime()+" victimTick="+victim.tickCount+" hp="+victim.getHealth()+" victim={"+identity(victim)+"} caster={"+identity(caster)+"} victimIdExact="+(level.getEntity(victim.getId())==victim)+" victimUuidExact="+(level.getEntity(victim.getUUID())==victim)+" casterIdExact="+(level.getEntity(caster.getId())==caster)+" casterUuidExact="+(level.getEntity(caster.getUUID())==caster)+" command="+commanded(caster,BuffKind.ATTACK_DAMAGE)+" zeal="+caster.getMainHandItem().getEnchantmentLevel(EnchantmentRegistrar.ZEAL.get())+" bow="+caster.getMainHandItem()+" circle="+caster.lastCastedCircleFangs+" faction="+com.solegendary.reignofnether.faction.Factions.getFaction((Unit)victim)+" nativeMonster="+com.solegendary.reignofnether.faction.Factions.getFaction((Unit)victim).equals(com.solegendary.reignofnether.faction.Factions.MONSTERS)+" nativeIsDay="+com.solegendary.reignofnether.time.TimeUtils.isDay(level)+" actorModulo160="+(victim.tickCount%Unit.MONSTER_HEALING_TICKS)+" noAI="+victim.isNoAi()+" absorption="+victim.getAbsorptionAmount());
        }
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
        public void windFangExactVictimPassiveHealDiagnostic(LivingHealEvent event){
            int i=exactVictim(event.getEntity());if(i<0)return;
            System.out.println("WIND_FANG_PASSIVE_HEAL_EVENT case="+caseName+" index="+i+" amount="+event.getAmount()+" canceled="+event.isCanceled()+" observerPriority=LOWEST notACommittedReceipt=true");snapshotOne("heal-event-before-native-write",i);
            new Exception("passive exact original Zombie LivingHealEvent call stack").printStackTrace(System.out);
        }
        private void damageEvent(String stage,LivingEntity victim,DamageSource source,float amount,boolean canceled){
            int i=exactVictim(victim);if(i<0)return;
            System.out.println("WIND_FANG_PASSIVE_DAMAGE_EVENT case="+caseName+" stage="+stage+" index="+i+" sourceType="+source.type()+" source={"+identity(source.getEntity())+"} direct={"+identity(source.getDirectEntity())+"} amount="+amount+" canceled="+canceled+" observerPriority=LOWEST notACommittedReceipt=true");snapshotOne(stage,i);
        }
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
        public void windFangExactVictimPassiveHurtDiagnostic(LivingHurtEvent event){damageEvent("living-hurt",event.getEntity(),event.getSource(),event.getAmount(),event.isCanceled());}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
        public void windFangExactVictimPassiveDamageDiagnostic(LivingDamageEvent event){damageEvent("living-damage",event.getEntity(),event.getSource(),event.getAmount(),event.isCanceled());}
        @SubscribeEvent(priority=EventPriority.LOWEST)
        public void windFangOriginalLevelEndHealthReadDiagnostic(net.minecraftforge.event.TickEvent.LevelTickEvent event){
            if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END||event.level!=helper.getLevel())return;
            for(int i=0;i<victims.size();i++){
                var victim=victims.get(i);float now=victim.getHealth();long gameTime=event.level.getGameTime();
                if(!sampled[i]||Float.compare(now,previousHealth[i])!=0){
                    System.out.println("WIND_FANG_PASSIVE_END_HP_READ case="+caseName+" index="+i+" previousSamplePresent="+sampled[i]+" previousGameTime="+previousGameTime[i]+" previousActorTick="+previousActorTick[i]+" previousHP="+previousHealth[i]+" currentGameTime="+gameTime+" currentActorTick="+victim.tickCount+" currentHP="+now+" delta="+(now-previousHealth[i])+" exactSetterReceipt=false");snapshotOne("level-END-subsequent-health-read",i);
                }
                sampled[i]=true;previousHealth[i]=now;previousActorTick[i]=victim.tickCount;previousGameTime[i]=gameTime;
            }
        }
    }
    public static final class DamageProbe {
        final Map<UUID,CommandRange> ranges=new HashMap<>();
        final Map<UUID,List<Long>> fangBatches=new HashMap<>();
        @SubscribeEvent public void fangs(net.minecraftforge.event.entity.EntityJoinLevelEvent event){if(event.getEntity() instanceof net.minecraft.world.entity.projectile.EvokerFangs fang&&fang.getOwner()!=null){var batches=fangBatches.get(fang.getOwner().getUUID());long now=event.getLevel().getGameTime();if(batches!=null&&(batches.isEmpty()||batches.get(batches.size()-1)!=now))batches.add(now);}}
        final Map<UUID,Plan> plans=new HashMap<>();final Map<UUID,int[]> counts=new HashMap<>();
        private void accept(Stage stage,LivingEntity victim,DamageSource source,net.minecraftforge.eventbus.api.Event event){if(stage==Stage.ATTACK){var range=ranges.get(victim.getUUID());if(range!=null)range.observe(source);}int[] count=counts.get(victim.getUUID());if(count==null)return;count[stage.ordinal()]++;Plan plan=plans.get(victim.getUUID());if(plan!=null&&plan.stage==stage){plans.remove(victim.getUUID());plan.once.accept(new Hit(victim,source,event));}}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void attack(LivingAttackEvent event){accept(Stage.ATTACK,event.getEntity(),event.getSource(),event);}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void hurt(LivingHurtEvent event){accept(Stage.HURT,event.getEntity(),event.getSource(),event);}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void damage(LivingDamageEvent event){accept(Stage.DAMAGE,event.getEntity(),event.getSource(),event);}
    }
}
