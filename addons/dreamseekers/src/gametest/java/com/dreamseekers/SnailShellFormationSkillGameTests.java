// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.bonewing.BoneWingBolt;
import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.*;
import com.solegendary.reignofnether.unit.units.monsters.SkeletonUnit;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

/** Initial vertical slices plus natural owner-sync regression. No hand-granted effects, scopes, projectiles or fabricated ground flags.
 * Factory attacks below are controlled real releases plus natural flight, not ordinary-AI cadence claims.
 */
@GameTestHolder("dreamseekers_snail_shell_formation")
@PrefixGameTestTemplate(false)
public final class SnailShellFormationSkillGameTests {
    private static final String BATCH="dreamseekers_snail_shell_formation", PREFIX="ShellSkill_";
    private static final List<Entity> ENTITIES=new ArrayList<>();
    private static final Set<String> ACCOUNTS=new LinkedHashSet<>();
    private static final Map<String,FakePlayer> PLAYERS=new HashMap<>();
    private static final Probe PROBE=new Probe();
    private static boolean listening;
    private static final Set<ServerPlayer> SYNC_MEMBERS=new LinkedHashSet<>();

    @AfterBatch(batch=BATCH)
    public static void cleanup(ServerLevel level) {
        if(listening){MinecraftForge.EVENT_BUS.unregister(PROBE);listening=false;}
        PROBE.damageOnce.clear();PROBE.hurtOnce.clear();
        for(var member:List.copyOf(SYNC_MEMBERS))syncMembership(member,false);
        for(Entity e:List.copyOf(ENTITIES))if(!e.isRemoved()) {
            if(e instanceof ServerPlayer p)p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);
            else e.discard();
        }
        for(String a:ACCOUNTS)for(String b:ACCOUNTS)if(!a.equals(b))AlliancesServerEvents.removeAlliance(a,b);
        PlayerServerEvents.players.removeIf(p->ACCOUNTS.contains(p.getGameProfile().getName()));
        PlayerServerEvents.rtsPlayers.removeIf(p->ACCOUNTS.contains(p.name));
        ResourcesServerEvents.resourcesList.removeIf(r->ACCOUNTS.contains(r.ownerName));
        ENTITIES.clear();ACCOUNTS.clear();PLAYERS.clear();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=850)
    public static void actualSenderSelfCodecReplayAndNoResourceChargeStayAuthoritative(GameTestHelper h) {
        floor(h);
        var source=snail(h,"Authority",4,1,5);
        var other=snail(h,"Other",12,1,5);
        var wrong=DreamseekersMod.FIREFLY_WORKER.get().create(h.getLevel());owned(h,wrong,"Authority",4,1,15);
        ready(h,source,other,wrong).thenExecute(()->{
            h.assertTrue(UnitSkill.SNAIL_SHELL_FORMATION.range()==3&&UnitSkill.SNAIL_SHELL_FORMATION.durationTicks()==100&&UnitSkill.SNAIL_SHELL_FORMATION.cooldownTicks()==480,"approved server constants are range three / duration one hundred / cooldown four hundred eighty");
            var request=request(source);var packet=new SkillNetwork.Cast(request);
            FriendlyByteBuf bytes=new FriendlyByteBuf(Unpooled.buffer());SkillNetwork.Cast decoded;
            try {packet.encode(bytes);decoded=SkillNetwork.Cast.decode(bytes);
                h.assertTrue(decoded.equals(packet)&&bytes.readableBytes()==0,"real Cast preserves exact self identity, skill and revision without surplus payload");
            } finally {bytes.release();}
            require(h,decoded.execute(null),SkillResult.INVALID_REQUEST,"missing authenticated sender");
            require(h,decoded.execute(player(h,other.getOwnerName())),SkillResult.NOT_OWNER,"foreign sender");
            require(h,SkillService.request(player(h,source.getOwnerName()),new SkillRequest(UnitSkill.SNAIL_SHELL_FORMATION,
                    source.getId(),UUID.randomUUID(),request.revision(),source.getId(),source.getUUID())),SkillResult.INVALID_SOURCE,"stale source UUID");
            require(h,SkillService.request(player(h,source.getOwnerName()),new SkillRequest(UnitSkill.SNAIL_SHELL_FORMATION,
                    source.getId(),source.getUUID(),request.revision(),other.getId(),other.getUUID())),SkillResult.INVALID_TARGET,"self token cannot name another body");
            require(h,SkillService.request(player(h,wrong.getOwnerName()),new SkillRequest(UnitSkill.SNAIL_SHELL_FORMATION,
                    wrong.getId(),wrong.getUUID(),wrong.rts().skills().revision(),wrong.getId(),wrong.getUUID())),SkillResult.INVALID_SOURCE,"worker cannot borrow snail button");
            var sender=player(h,source.getOwnerName());
            require(h,java.util.concurrent.CompletableFuture.supplyAsync(()->decoded.execute(sender)).join(),SkillResult.INVALID_REQUEST,"off-server-thread mutation denied");
            h.assertTrue(cd(source)==0&&!SnailShellFormation.active(source),"all denials leave cooldown and formation absent");
            h.assertTrue(source.getAbilities().get().equals(List.of(UnitSkillAbility.SNAIL_SHELL_FORMATION)),"one approved new active; old reactive shell is not an invented second button");
            require(h,decoded.execute(sender),SkillResult.SUCCESS,"actual owner self cast");
            require(h,decoded.execute(sender),SkillResult.REPLAY,"same successful token cannot replay");
            require(h,cast(h,source),SkillResult.COOLDOWN,"fresh revision cannot refresh active");
            h.assertTrue(cd(source)==480&&SnailShellFormation.remainingTicks(source)==100&&SnailShellFormation.active(source),"exact initial four-hundred-eighty CD and hundred-tick formation");
            h.assertTrue(source.getHoldPosition()&&!source.isIdle()&&!source.rts().hold,"formation is a temporary effective hold, not persistent user hold");
            h.assertTrue(source.getShellCooldown()==0&&!source.hasEffect(MobEffects.DAMAGE_RESISTANCE),"new active does not fabricate the old on-hit shell");
            ledger(h,"Authority");ledger(h,"Other");pass("AUTHORITY_SELF_CODEC_REPLAY_NO_RESOURCE");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1200)
    public static void actualGroundMoveStopsAtCastAndNewAuthenticatedMoveEndsOnlyFormation(GameTestHelper h) {
        floor(h);var source=snail(h,"Move",4,1,10);
        Vec3[] start={null},station={null};long[] castAt={0},revision={0};
        ready(h,source).thenExecute(()->source.setNoAi(false))
          .thenWaitUntil(()->h.assertTrue(source.onGround()&&h.getLevel().noCollision(source),"real ground navigation physically settled; no setOnGround fixture"))
          .thenExecute(()->{start[0]=source.position();order(h,source,UnitAction.MOVE,-1,h.absolutePos(new BlockPos(26,0,10)));})
          .thenWaitUntil(()->h.assertTrue(horizontal(source.position(),start[0])>.75&&!UnitServerEvents.hasPendingImmediateAction(source.getId()),"real authenticated old MOVE actually made progress before cast"))
          .thenExecute(()->{
              require(h,cast(h,source),SkillResult.SUCCESS,"real moving snail self-cast");
              station[0]=source.position();castAt[0]=h.getLevel().getGameTime();
              h.assertTrue(source.getHoldPosition()&&!source.isIdle()&&!source.rts().hold,"active position lock is derived, not persistent hold mutation");
          }).thenIdle(20).thenExecute(()->{
              h.assertTrue(SnailShellFormation.active(source)&&horizontal(source.position(),station[0])<.08,"twenty natural physics and unit ticks stay stationary despite prior long MOVE");
              h.assertTrue(source.getNavigation().isDone(),"old active navigation was stopped, not just visually hidden");
              revision[0]=source.rts().commandRevision;
              order(h,source,UnitAction.MOVE,-1,h.absolutePos(new BlockPos(26,0,10)));
          }).thenWaitUntil(()->h.assertTrue(!UnitServerEvents.hasPendingImmediateAction(source.getId())&&source.rts().commandRevision>revision[0]&&!SnailShellFormation.active(source),"genuine newer MOVE executed and revoked formation"))
          .thenWaitUntil(()->h.assertTrue(horizontal(source.position(),station[0])>.75,"new command resumes actual native path travel"))
          .thenExecute(()->{
              h.assertTrue(!source.getHoldPosition()&&!source.rts().hold,"new MOVE removes only temporary stance");
              h.assertTrue(cd(source)==Math.max(0,480-(h.getLevel().getGameTime()-castAt[0])),"new MOVE never refunds or restarts paid cooldown");
              require(h,cast(h,source),SkillResult.COOLDOWN,"movement cancellation does not enable free recast");
              ledger(h,"Move");pass("REAL_GROUND_STOP_NEW_MOVE_CD_CONTINUES");
          }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1000)
    public static void realArrowAndPaidBoltCoverWorkerSupportAndSelfButNotFriendlyOrNonProjectileDamage(GameTestHelper h) {
        floor(h);List<Mob> targets=new ArrayList<>(),shooters=new ArrayList<>();List<BasaltSnailUnit> casters=new ArrayList<>();
        List<Entity> all=new ArrayList<>();Shot[] shots=new Shot[4];Shot[] friendly={null};float[] beforeFriendly={0};
        for(int i=0;i<4;i++) {
            int z=5+7*i;Mob target;
            if(i<2)target=DreamseekersMod.FIREFLY_WORKER.get().create(h.getLevel());
            else if(i==2)target=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());
            else target=DreamseekersMod.BASALT_SNAIL.get().create(h.getLevel());
            owned(h,target,"Coverage"+i,10,1,z);neutral(target);durable(target);targets.add(target);all.add(target);
            Mob shooter=i==3?bone(h,"CoverageEnemy",4,2,z):skeleton(h,"CoverageEnemy",4,1,z);
            shooters.add(shooter);all.add(shooter);
            if(i>0){var caster=i==3?(BasaltSnailUnit)target:snail(h,"Coverage"+i,10,1,z+2);casters.add(caster);if(caster!=target)all.add(caster);}
        }
        var seq=ready(h,all.toArray(Entity[]::new)).thenExecute(()->{
            for(var caster:casters)require(h,cast(h,caster),SkillResult.SUCCESS,"actual self cast for finite coverage lane");
            h.assertTrue(!targets.get(3).hasEffect(MobEffects.DAMAGE_RESISTANCE),"self's first paid projectile enters before reactive shell exists");
            for(int i=0;i<4;i++)shots[i]=launch(h,shooters.get(i),targets.get(i),i==3);
        });
        observe(seq,shots,35);
        seq.thenExecute(()->{
            for(int i=0;i<4;i++){flight(h,shots[i],"coverage lane="+i);close(h,1000-targets.get(i).getHealth(),i==0?10:8,"real "+(i==3?"paid bolt self":i==2?"native Arrow support":"native Arrow worker")+" lane="+i);}
            h.assertTrue(shots[3].resistanceAtDamage==false&&targets.get(3).hasEffect(MobEffects.DAMAGE_RESISTANCE),"first-hit formation reduced actual primary before old shell is added only after hurt returns");
            var worker=targets.get(1);var enemy=shooters.get(1);
            close(h,rawDamage(worker,worker.damageSources().generic(),10),10,"source-free environment damage is not projectile protection");
            close(h,rawDamage(worker,worker.damageSources().mobAttack(enemy),10),10,"real enemy source does not turn ordinary melee into a projectile");
            // Explicit native friendly fire is legal only because this exact target is forced; no fake DamageSource proof.
            ((Unit)enemy).setOwnerName(((Unit)worker).getOwnerName());
            ((AttackerUnit)enemy).setUnitAttackTargetForced(worker);worker.invulnerableTime=0;beforeFriendly[0]=worker.getHealth();
            friendly[0]=launch(h,enemy,worker,false);
        });
        observe(seq,friendly,35);
        seq.thenExecute(()->{
            flight(h,friendly[0],"explicit same-owner friendly Arrow");
            h.assertTrue(SnailShellFormation.active(casters.get(0)),"friendly-negative hit occurred while real source formation was still live");
            close(h,beforeFriendly[0]-targets.get(1).getHealth(),10,"genuine primary friendly Arrow keeps legal base ten without enemy-only twenty percent");
            for(int i=0;i<4;i++)ledger(h,"Coverage"+i);ledger(h,"CoverageEnemy");
            pass("REAL_ARROW_PAID_BOLT_WORKER_SUPPORT_SELF_NONENEMY_NEGATIVE");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1000)
    public static void actualOldShellBaselineDoesNotReceiveExtraReductionAndBeetleTenTakesMaxWithTwenty(GameTestHelper h) {
        floor(h);var plain=snail(h,"OldPlain",10,1,5);var guarded=snail(h,"OldGuarded",10,1,12);
        var beetlePlain=beetle(h,"MaxPlain",10,1,19);var beetleGuarded=beetle(h,"MaxGuarded",10,1,26);
        var plainFriend=DreamseekersMod.PINK_SLIME_UNIT.get().create(h.getLevel());owned(h,plainFriend,"MaxPlain",10,1,21);
        var formation=snail(h,"MaxGuarded",10,1,28);
        List<Mob> targets=List.of(plain,guarded,beetlePlain,beetleGuarded);List<SkeletonUnit> shooters=new ArrayList<>();
        List<Entity> all=new ArrayList<>(List.of(plain,guarded,beetlePlain,beetleGuarded,plainFriend,formation));
        for(int i=0;i<4;i++){durable(targets.get(i));var shooter=skeleton(h,"ResistanceEnemy",4,1,5+7*i);shooters.add(shooter);all.add(shooter);}
        Shot[] shots=new Shot[4];float[] before=new float[4];long[] shellAt={0};
        var seq=ready(h,all.toArray(Entity[]::new)).thenExecute(()->{
            for(var snail:List.of(plain,guarded)) {
                h.assertTrue(!snail.hasEffect(MobEffects.DAMAGE_RESISTANCE)&&snail.getShellCooldown()==0,"old shell initially absent");
                close(h,rawDamage(snail,snail.damageSources().generic(),2),2,"first actual old-shell trigger cannot retroactively reduce its own source-free hit");
                oldShell(h,snail,80,200);
            }
            shellAt[0]=h.getLevel().getGameTime();
            require(h,cast(h,guarded),SkillResult.SUCCESS,"real formation with already-earned reactive shell");
            require(h,cast(h,formation),SkillResult.SUCCESS,"real nearby source for guarded beetle");
            close(h,BeetleGuard.reduction(beetlePlain),.1,"real same-owner combatant enables baseline beetle ten");
            close(h,BeetleGuard.reduction(beetleGuarded),.1,"actual nearby snail is eligible combat supporter for beetle ten");
            h.assertTrue(!beetlePlain.hasEffect(MobEffects.DAMAGE_RESISTANCE)&&!beetleGuarded.hasEffect(MobEffects.DAMAGE_RESISTANCE),"beetle max lanes have no native Resistance ambiguity");
            for(int i=0;i<4;i++){targets.get(i).invulnerableTime=0;before[i]=targets.get(i).getHealth();shots[i]=launch(h,shooters.get(i),targets.get(i),false);}
        });
        observe(seq,shots,35);
        seq.thenExecute(()->{
            for(int i=0;i<4;i++)flight(h,shots[i],"resistance/max lane="+i);
            double nativeLoss=before[0]-plain.getHealth(),guardedLoss=before[1]-guarded.getHealth();
            h.assertTrue(nativeLoss>0&&nativeLoss<10,"matched actual native Resistance baseline really reduced the ten-point Arrow");
            close(h,guardedLoss,nativeLoss,"formation adds no extra reduction after the actual native Resistance path; preserve legacy native math rather than assuming eight");
            h.assertTrue(shots[0].resistanceAtDamage&&shots[1].resistanceAtDamage,"both matched genuine arrows encountered the original live shell");
            for(var snail:List.of(plain,guarded)) {
                h.assertTrue(snail.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier()==0&&snail.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier()==2,"original shell remains I/III, no native IV leak");
                h.assertTrue(Math.abs(snail.getShellCooldown()-(200-(h.getLevel().getGameTime()-shellAt[0])))<=1,"second actual hit did not reset independent original shell cooldown");
            }
            close(h,before[2]-beetlePlain.getHealth(),9,"real unformed beetle baseline ten-percent defense");
            close(h,before[3]-beetleGuarded.getHealth(),8,"beetle ten and shell twenty take strongest twenty, never multiply to seven-point-two");
            System.out.println("SNAIL_SHELL_NATIVE_RESISTANCE_MATCH native="+nativeLoss+" withFormation="+guardedLoss);
            for(String suffix:List.of("OldPlain","OldGuarded","MaxPlain","MaxGuarded","ResistanceEnemy"))ledger(h,suffix);
            pass("OLD_SHELL_AFTER_HIT_NATIVE_BASELINE_EQUAL_BEETLE_MAX20");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1000)
    public static void naturalEntityTicksPublishInitialAndTenTickOwnerOnlySyncWithoutDuplicateStateTicks(GameTestHelper h) {
        floor(h);var owner=player(h,"SyncOwner");var stranger=player(h,"SyncStranger");
        owner.connection=new SyncRecordingHandler(owner);stranger.connection=new SyncRecordingHandler(stranger);
        syncMembership(owner,true);syncMembership(stranger,true);
        var snail=snail(h,"SyncOwner",5,1,5);var beetle=beetle(h,"SyncOwner",5,1,14);
        var worker=DreamseekersMod.FIREFLY_WORKER.get().create(h.getLevel());owned(h,worker,"SyncOwner",5,1,23);
        var reserve=EntityRegistrar.ZOMBIE_UNIT.get().create(h.getLevel());owned(h,reserve,"SyncStranger",24,1,14);
        List<SkillUnit> sources=List.of(snail,beetle,worker);long[] castAt={0},abaAt={0};long[] revisions=new long[3];
        ready(h,snail,beetle,worker,reserve,owner,stranger)
          .thenWaitUntil(()->{for(var source:sources)h.assertTrue(positiveSync(owner,source).size()>=1,"natural entity tick must publish initial owner sync for "+source.mob().getType());})
          .thenExecute(()->{
              h.assertTrue(h.getLevel().getServer().getPlayerList().getPlayerByName(owner.getGameProfile().getName())==owner&&h.getLevel().getServer().getPlayerList().getPlayer(owner.getUUID())==owner,"real PlayerList name and UUID lookup resolve actual owner");
              for(var source:sources) {
                  var packets=positiveSync(owner,source);h.assertTrue(packets.size()==1,"one initial encoded Sync before first heartbeat, source="+source.mob().getUUID()+" packets="+packets);
                  var observation=packets.get(0);var packet=observation.packet();
                  h.assertTrue(packet.skill()==SkillService.skillFor(source)&&packet.sourceId()==source.mob().getId()&&packet.sourceUuid().equals(source.mob().getUUID())&&packet.revision()==source.rts().skills().revision()&&packet.cooldownTicks()==0&&!packet.autocastEnabled(),"actual encoded initial payload has exact supported skill, original ID/UUID, revision and zero cooldown");
                  int count=syncWire(owner).syncs.size();long revision=source.rts().skills().revision();
                  source.rts().skills().tick();source.rts().skills().tick();
                  h.assertTrue(syncWire(owner).syncs.size()==count&&source.rts().skills().revision()==revision&&source.rts().skills().cooldownTicks(SkillService.skillFor(source))==0&&effectTicks(source)==0,"same-game-tick repeated state ticks cannot duplicate initial sync, mutate revision or create effect");
              }
              noForeignSync(h,stranger,sources,"initial ordinary entity tick");
          }).thenWaitUntil(()->{for(var source:sources)h.assertTrue(positiveSync(owner,source).size()>=2,"await genuine ten-tick heartbeat for "+source.mob().getUUID());})
          .thenExecute(()->{
              for(var source:sources) {
                  var packets=positiveSync(owner,source);h.assertTrue(packets.size()==2&&packets.get(1).gameTime()-packets.get(0).gameTime()==10,"natural initial/heartbeat exactly ten world ticks apart: "+packets);
                  h.assertTrue(packets.get(1).packet().cooldownTicks()==0,"unused heartbeat never invents cooldown");
              }
              noForeignSync(h,stranger,sources,"natural ten-tick heartbeat");
              castAt[0]=h.getLevel().getGameTime();
              for(var source:sources) {
                  UnitSkill skill=SkillService.skillFor(source);Mob body=source.mob();
                  require(h,new SkillNetwork.Cast(new SkillRequest(skill,body.getId(),body.getUUID(),source.rts().skills().revision(),body.getId(),body.getUUID())).execute(owner),SkillResult.SUCCESS,"actual authenticated cast after proven natural initial sync: "+skill);
                  var packets=positiveSync(owner,source);var last=packets.get(packets.size()-1);
                  h.assertTrue(last.gameTime()==castAt[0]&&last.packet().cooldownTicks()==skill.cooldownTicks()&&last.packet().revision()==source.rts().skills().revision(),"real cast emits exact paid cooldown through encoded owner channel");
                  assertRepeatedStateTicks(h,owner,source,skill.cooldownTicks(),skill.durationTicks());
              }
          }).thenIdle(5).thenExecute(()->{
              for(var source:sources){UnitSkill skill=SkillService.skillFor(source);int elapsed=(int)(h.getLevel().getGameTime()-castAt[0]);assertRepeatedStateTicks(h,owner,source,skill.cooldownTicks()-elapsed,skill.durationTicks()-elapsed);}
              noForeignSync(h,stranger,sources,"live effects and duplicate state ticks");
          }).thenWaitUntil(()->{
              h.assertTrue(h.getLevel().getGameTime()>=castAt[0]+10,"real post-cast heartbeat time reached");
              for(var source:sources)h.assertTrue(positiveSync(owner,source).stream().anyMatch(p->p.gameTime()==castAt[0]+10),"natural post-cast heartbeat emitted at exact tenth world tick");
          }).thenExecute(()->{
              for(var source:sources) {
                  UnitSkill skill=SkillService.skillFor(source);var atTen=positiveSync(owner,source).stream().filter(p->p.gameTime()==castAt[0]+10).toList();
                  h.assertTrue(atTen.size()==1&&atTen.get(0).packet().cooldownTicks()==skill.cooldownTicks()-10,"one exact heartbeat with wall-clock cooldown, never double decrement");
              }
              noForeignSync(h,stranger,sources,"post-cast heartbeat");
              abaAt[0]=h.getLevel().getGameTime();
              for(int i=0;i<sources.size();i++) {
                  var source=sources.get(i);int before=source.rts().skills().cooldownTicks(SkillService.skillFor(source));
                  source.setOwnerName(stranger.getGameProfile().getName());source.setOwnerName(owner.getGameProfile().getName());
                  revisions[i]=source.rts().skills().revision();
                  h.assertTrue(effectTicks(source)==0&&source.rts().skills().cooldownTicks(SkillService.skillFor(source))==before,"real owner ABA clears short effect without resetting paid cooldown");
                  long clears=syncWire(owner).syncs.stream().filter(p->p.gameTime()==abaAt[0]&&p.packet().sourceUuid().equals(source.mob().getUUID())&&p.packet().revision()<0).count();
                  h.assertTrue(clears==1,"actual prior owner receives exactly one encoded lifecycle detach clear");
              }
          }).thenWaitUntil(()->{
              for(int i=0;i<sources.size();i++){var source=sources.get(i);long revision=revisions[i];h.assertTrue(positiveSync(owner,source).stream().anyMatch(p->p.gameTime()>abaAt[0]&&p.packet().revision()==revision),"later natural entity tick resynchronizes true current owner after real lifecycle transition");}
          }).thenExecute(()->{
              for(var source:sources) {
                  UnitSkill skill=SkillService.skillFor(source);h.assertTrue(effectTicks(source)==0&&source.rts().skills().cooldownTicks(skill)==skill.cooldownTicks()-(h.getLevel().getGameTime()-castAt[0]),"normal post-ABA sync never revives old effect or refunds cooldown");
                  h.assertTrue(h.getLevel().getEntity(source.mob().getId())==source.mob()&&h.getLevel().getEntity(source.mob().getUUID())==source.mob(),"original exact body remains published through synchronization lifecycle");
              }
              noForeignSync(h,stranger,sources,"owner ABA with no intervening foreign ownership tick");
              ledger(h,"SyncOwner");ledger(h,"SyncStranger");
              System.out.println("SNAIL_SHELL_ACTUAL_SYNC owner="+syncWire(owner).syncs+" nonOwner="+syncWire(stranger).syncs);
              syncMembership(owner,false);syncMembership(stranger,false);
              pass("NATURAL_INITIAL_HEARTBEAT_OWNER_ONLY_SAME_TICK_IDEMPOTENT_LIFECYCLE");
          }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1200)
    public static void activeFormationSurvivesNaturalFiftyTickFarAnchorChecksThenExpiryRestoresOrdinaryRetreat(GameTestHelper h) {
        floor(h);var source=snail(h,"Anchor",4,1,4);var control=snail(h,"Anchor",4,1,8);
        BlockPos anchor=h.absolutePos(new BlockPos(28,0,28));Vec3[] station={null},controlStart={null};
        long[] castAt={0},command={0};int[] lastBoundary={0},boundaries={0};
        var seq=ready(h,source,control).thenExecute(()->{source.setNoAi(false);control.setNoAi(false);})
          .thenWaitUntil(()->h.assertTrue(source.onGround()&&control.onGround()&&h.getLevel().noCollision(source)&&h.getLevel().noCollision(control),"both real ground navigators have physically settled"))
          .thenWaitUntil(()->h.assertTrue(source.tickCount%50>=5&&source.tickCount%50<=10,"naturally reach a phase allowing two full fifty-tick checks inside hundred-tick stance; never assign tickCount"))
          .thenExecute(()->{
              source.setAnchor(anchor);control.setAnchor(anchor);
              h.assertTrue(!anchor.equals(BlockPos.ZERO)&&source.distanceToSqr(Vec3.atCenterOf(anchor))>Unit.ANCHOR_RETREAT_RANGE*Unit.ANCHOR_RETREAT_RANGE&&control.distanceToSqr(Vec3.atCenterOf(anchor))>Unit.ANCHOR_RETREAT_RANGE*Unit.ANCHOR_RETREAT_RANGE,"real pre-existing nonzero anchor lies beyond native thirty-block retreat threshold in both lanes");
              station[0]=source.position();controlStart[0]=control.position();
              require(h,cast(h,source),SkillResult.SUCCESS,"actual self formation while retaining existing far anchor");
              castAt[0]=h.getLevel().getGameTime();command[0]=source.rts().commandRevision;lastBoundary[0]=source.tickCount/50;
              h.assertTrue(source.getAnchor().equals(anchor)&&source.getMoveGoal().getMoveTarget()==null&&!source.isIdle(),"formation clears current navigation but keeps the actual anchor and effective nonidle stance");
              savedAnchor(h,source,anchor);
          });
        // These observations are all registered now, not from any test callback or private native helper.
        for(int i=0;i<99;i++)seq.thenIdle(1).thenExecute(()->{
            h.assertTrue(h.getLevel().getGameTime()<castAt[0]+100&&SnailShellFormation.active(source),"formation remains active throughout its original hundred-tick interval");
            h.assertTrue(source.getAnchor().equals(anchor)&&source.rts().commandRevision==command[0]&&source.getMoveGoal().getMoveTarget()==null,"natural auto-anchor checks cannot full-reset active formation or overwrite saved anchor");
            h.assertTrue(horizontal(source.position(),station[0])<.08&&!source.isIdle(),"natural unit and physics ticks do not pursue far anchor while active");
            int boundary=source.tickCount/50;
            if(boundary>lastBoundary[0]) {
                boundaries[0]+=boundary-lastBoundary[0];lastBoundary[0]=boundary;
                System.out.println("SNAIL_SHELL_NATURAL_ANCHOR_BOUNDARY gameTime="+h.getLevel().getGameTime()+" unitTick="+source.tickCount+" active="+SnailShellFormation.active(source)+" anchor="+source.getAnchor()+" command="+source.rts().commandRevision+" source="+source.position()+" control="+control.position()+" controlTarget="+control.getMoveGoal().getMoveTarget());
            }
        });
        seq.thenExecute(()->{
            h.assertTrue(boundaries[0]>=2,"actual entity ticks naturally crossed both fifty-tick native anchor decision boundaries");
            h.assertTrue(anchor.equals(control.getMoveGoal().getMoveTarget())&&horizontal(control.position(),controlStart[0])>.75,"unformed matched snail really ran ordinary automatic anchor retreat and physically travelled");
            savedAnchor(h,source,anchor);
        }).thenWaitUntil(()->h.assertTrue(h.getLevel().getGameTime()>=castAt[0]+100,"original hundred-tick deadline reached naturally"))
          .thenExecute(()->{
              h.assertTrue(!SnailShellFormation.active(source)&&SnailShellFormation.remainingTicks(source)==0&&!source.getHoldPosition()&&source.getAnchor().equals(anchor),"natural expiry removes only formation stance, retaining original anchor");
              h.assertTrue(source.getMoveGoal().getMoveTarget()==null,"expiry itself never restores an old navigation target; next ordinary native anchor check owns any return");
          }).thenWaitUntil(()->h.assertTrue(anchor.equals(source.getMoveGoal().getMoveTarget())&&source.rts().commandRevision>command[0],"later natural fifty-tick check resumes original native full-reset and anchor target after expiry"))
          .thenWaitUntil(()->h.assertTrue(horizontal(source.position(),station[0])>.75,"expired source physically follows ordinary automatic retreat rather than only storing a path"))
          .thenExecute(()->{
              h.assertTrue(!SnailShellFormation.active(source)&&source.getAnchor().equals(anchor)&&control.getAnchor().equals(anchor),"neither ordinary retreat nor expiry erases either original anchor");
              h.assertTrue(cd(source)==480-(h.getLevel().getGameTime()-castAt[0]),"automatic post-expiry retreat cannot refund or restart paid skill cooldown");
              savedAnchor(h,source,anchor);ledger(h,"Anchor");
              pass("NATURAL_FIFTY_TICK_FAR_ANCHOR_ACTIVE_GATE_EXACT_EXPIRY_NATIVE_RESUMES");
          }).thenSucceed();
    }
    private static void savedAnchor(GameTestHelper h,BasaltSnailUnit source,BlockPos anchor) {
        var saved=new net.minecraft.nbt.CompoundTag();source.addAdditionalSaveData(saved);
        h.assertTrue(saved.getInt("anchorPosX")==anchor.getX()&&saved.getInt("anchorPosY")==anchor.getY()&&saved.getInt("anchorPosZ")==anchor.getZ(),"ordinary entity save preserves the real original anchor, never a temporary zero override");
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1150)
    public static void actualPrimaryCallbacksRevalidateAllianceRangeOwnerEpochDeathAndNeverBorrowNewFormation(GameTestHelper h) {
        floor(h);List<Mob> targets=new ArrayList<>();List<SkeletonUnit> shooters=new ArrayList<>();List<BasaltSnailUnit> casters=new ArrayList<>();List<Entity> all=new ArrayList<>();
        Shot[] shots=new Shot[6],later=new Shot[2];BasaltSnailUnit[] replacement={null};boolean[] changed=new boolean[6];
        for(int i=0;i<6;i++) {
            int x=i<3?10:25,z=6+10*(i%3);var target=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());owned(h,target,"RevalidateTarget"+i,x,1,z);neutral(target);durable(target);targets.add(target);all.add(target);
            var shooter=skeleton(h,"RevalidateEnemy",x-6,1,z);shooters.add(shooter);all.add(shooter);
            var caster=snail(h,i==0?"RevalidateAlly":"RevalidateTarget"+i,x,1,z+2);casters.add(caster);all.add(caster);
            if(i==4){replacement[0]=snail(h,"RevalidateTarget4",x+2,1,z+2);all.add(replacement[0]);}
        }
        var seq=ready(h,all.toArray(Entity[]::new)).thenExecute(()->{
            AlliancesServerEvents.addAlliance(casters.get(0).getOwnerName(),((Unit)targets.get(0)).getOwnerName());
            for(int i=0;i<5;i++)require(h,cast(h,casters.get(i)),SkillResult.SUCCESS,"real captured formation before callback case="+i);
            h.assertTrue(!SnailShellFormation.active(casters.get(5))&&!SnailShellFormation.active(replacement[0]),"late formations genuinely do not exist at original primary entry");
            for(int i=0;i<6;i++) {
                final int lane=i;var caster=casters.get(i);var target=targets.get(i);
                shots[i]=launch(h,shooters.get(i),target,false);
                chainDamage(target,event->{
                    changed[lane]=true;
                    switch(lane) {
                        case 0->AlliancesServerEvents.removeAlliance(caster.getOwnerName(),((Unit)target).getOwnerName());
                        case 1->position(caster,target.position().add(0,0,4));
                        case 2->{String old=caster.getOwnerName();caster.setOwnerName(old+"Temporary");caster.setOwnerName(old);}
                        case 3->{var unit=(Unit)target;String old=unit.getOwnerName();unit.setOwnerName(old+"Temporary");unit.setOwnerName(old);}
                        case 4->{caster.invulnerableTime=0;h.assertTrue(caster.hurt(caster.damageSources().genericKill(),10000)&&!caster.isAlive(),"real captured caster died before target final write");require(h,cast(h,replacement[0]),SkillResult.SUCCESS,"different genuine caster activates during original final callback");}
                        case 5->require(h,cast(h,caster),SkillResult.SUCCESS,"first genuine formation activates only after unprotected primary entered");
                        default->throw new IllegalStateException("fixed revalidation lane");
                    }
                });
            }
        });
        observe(seq,shots,35);
        seq.thenExecute(()->{
            for(int i=0;i<6;i++){flight(h,shots[i],"callback revalidation="+i);h.assertTrue(changed[i],"actual final callback ran for exact lane="+i);close(h,1000-targets.get(i).getHealth(),10,"revocation or late grant retracts only twenty percent and preserves legal ten-point native base: lane="+i);}
            h.assertTrue(!SnailShellFormation.active(casters.get(2))&&cd(casters.get(2))>0,"source ABA cancels short formation without refund");
            h.assertTrue(SnailShellFormation.active(casters.get(3)),"target ABA invalidated this hit, not unrelated source formation lifetime");
            h.assertTrue(SnailShellFormation.active(replacement[0])&&SnailShellFormation.active(casters.get(5)),"new callback formations really exist after old hit but were not retroactive");
            for(int i=0;i<2;i++){var target=targets.get(i+4);target.invulnerableTime=0;later[i]=launch(h,shooters.get(i+4),target,false);}
        });
        observe(seq,later,35);
        seq.thenExecute(()->{
            for(int i=0;i<2;i++){flight(h,later[i],"later independent primary="+i);close(h,990-targets.get(i+4).getHealth(),8,"next independent physical primary may use newly legal real formation");}
            for(int i=0;i<6;i++)ledger(h,"RevalidateTarget"+i);ledger(h,"RevalidateAlly");ledger(h,"RevalidateEnemy");
            pass("REAL_PRIMARY_REVALIDATION_ALLIANCE_RANGE_ABA_DEATH_LATE_GRANTS");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1150)
    public static void diagnosticResistanceIsNotReceiptRecursiveNativeDoesNotPolluteOuterBeetleAndFinalDefensePrecedesGel(GameTestHelper h) {
        floor(h);List<Mob> targets=new ArrayList<>();List<BasaltSnailUnit> casters=new ArrayList<>();List<SkeletonUnit> shooters=new ArrayList<>();List<PinkSlimeUnit> gels=new ArrayList<>();List<Entity> all=new ArrayList<>();
        Shot[] shots=new Shot[5],second={null};double[] diagnostic={0},innerLoss={0};boolean[] innerAccepted={false};
        for(int i=0;i<5;i++) {
            int x=i<3?10:25,z=6+10*(i%3);Mob target=i<2?DreamseekersMod.BEETLE.get().create(h.getLevel()):DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());
            owned(h,target,"FinalTarget"+i,x,1,z);neutral(target);durable(target);targets.add(target);all.add(target);
            var caster=snail(h,"FinalTarget"+i,x,1,z+2);casters.add(caster);all.add(caster);
            var shooter=skeleton(h,"FinalEnemy",x-6,1,z);shooters.add(shooter);all.add(shooter);
            if(i>=2){var gel=DreamseekersMod.PINK_SLIME_UNIT.get().create(h.getLevel());owned(h,gel,"FinalTarget"+i,x+2,1,z+2);gels.add(gel);all.add(gel);}
        }
        var seq=ready(h,all.toArray(Entity[]::new)).thenExecute(()->{
            for(var caster:casters)require(h,cast(h,caster),SkillResult.SUCCESS,"actual formation for diagnostic/reentry/final order lane");
            for(int i=0;i<2;i++)close(h,BeetleGuard.reduction(targets.get(i)),.1,"real independent same-owner combat supporter enables beetle ten before callbacks");
            for(int i=2;i<5;i++)require(h,gel(h,gels.get(i-2),targets.get(i)),SkillResult.SUCCESS,"actual authenticated Gel twelve before physical impact");
            targets.get(4).setAbsorptionAmount(3);
            watchHurt(targets.get(1),event->{
                var victim=targets.get(1);h.assertTrue(!victim.hasEffect(MobEffects.DAMAGE_RESISTANCE),"outer hit actually had zero native Resistance");
                // Explicit native-potion boundary stimulus, not a fake formation or native receipt.
                h.assertTrue(victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,100,0)),"inner hit alone gets real temporary native Resistance I");
                float before=victim.getHealth();victim.invulnerableTime=0;innerAccepted[0]=victim.hurt(event.getSource(),10);innerLoss[0]=before-victim.getHealth();victim.removeEffect(MobEffects.DAMAGE_RESISTANCE);victim.invulnerableTime=0;
            });
            for(int i=0;i<5;i++)shots[i]=launch(h,shooters.get(i),targets.get(i),false);
            chainDamage(targets.get(0),event->{
                var victim=targets.get(0);h.assertTrue(!victim.hasEffect(MobEffects.DAMAGE_RESISTANCE),"real native calculation finished with no Resistance");
                h.assertTrue(victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,100,0)),"declared diagnostic-only native potion fixture");
                diagnostic[0]=victim.getDamageAfterMagicAbsorb(event.getSource(),10);victim.removeEffect(MobEffects.DAMAGE_RESISTANCE);
            });
            chainDamage(targets.get(2),event->event.setCanceled(true));
            chainDamage(targets.get(3),event->event.setAmount(0));
        });
        observe(seq,shots,35);
        seq.thenExecute(()->{
            for(int i=0;i<5;i++)flight(h,shots[i],"actual final order="+i);
            close(h,diagnostic[0],8,"pure public native diagnostic really evaluated Resistance I, not a fabricated zero result");
            close(h,1000-targets.get(0).getHealth(),8,"diagnostic getDamageAfterMagicAbsorb must not claim native receipt and suppress real formation twenty");
            h.assertTrue(innerAccepted[0]&&innerLoss[0]>0&&innerLoss[0]<10,"same exact DamageSource recursion actually traversed native Resistance only on inner hit");
            close(h,1000-targets.get(1).getHealth(),innerLoss[0]+9,"same-DS recursion revokes formation but inner Resistance cannot suppress outer independent beetle ten");
            for(int i=2;i<4;i++){close(h,1000-targets.get(i).getHealth(),0,"real final cancel/zero causes no HP loss: lane="+i);close(h,SkillEffects.shieldRemaining(targets.get(i)),12,"cancel/zero never spends actual Gel: lane="+i);}
            close(h,targets.get(4).getAbsorptionAmount(),0,"native absorption consumes own three before final defenses");
            close(h,1000-targets.get(4).getHealth(),0,"real remaining seven becomes five-point-six before Gel, fully shielded");
            close(h,SkillEffects.shieldRemaining(targets.get(4)),6.4,"twelve Gel minus (ten minus three native absorption) times point-eight leaves six-point-four");
            targets.get(4).invulnerableTime=0;second[0]=launch(h,shooters.get(4),targets.get(4),false);
        });
        observe(seq,second,35);
        seq.thenExecute(()->{
            flight(h,second[0],"later real Gel exhaustion");close(h,1000-targets.get(4).getHealth(),1.6,"later defended eight spends remaining six-point-four Gel then one-point-six actual HP");close(h,SkillEffects.shieldRemaining(targets.get(4)),0,"real captured Gel exhausted once");
            h.assertTrue(!targets.get(0).hasEffect(MobEffects.DAMAGE_RESISTANCE)&&!targets.get(1).hasEffect(MobEffects.DAMAGE_RESISTANCE),"native diagnostic/recursive fixtures were removed, not persisted as invented buffs");
            System.out.println("SNAIL_SHELL_REENTRANT_NATIVE inner="+innerLoss[0]+" outer="+(1000-targets.get(1).getHealth()-innerLoss[0]));
            for(int i=0;i<5;i++)ledger(h,"FinalTarget"+i);ledger(h,"FinalEnemy");
            pass("DIAGNOSTIC_NO_RECEIPT_REENTRY_OUTER_BEETLE_CANCEL_ZERO_ABSORPTION_FORMATION_GEL");
        }).thenSucceed();
    }
    private static SkillResult gel(GameTestHelper h,PinkSlimeUnit source,LivingEntity target){return SkillService.request(player(h,source.getOwnerName()),new SkillRequest(UnitSkill.GEL_GUARD,source.getId(),source.getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID()));}
    private static void chainDamage(LivingEntity target,Consumer<LivingDamageEvent> action){var first=PROBE.damageOnce.remove(target.getUUID());if(first==null)throw new IllegalStateException("missing genuine factory callback observer");watchDamage(target,event->{first.accept(event);action.accept(event);});}
    private static void watchHurt(LivingEntity target,Consumer<net.minecraftforge.event.entity.living.LivingHurtEvent> action){if(!listening){MinecraftForge.EVENT_BUS.register(PROBE);listening=true;}if(PROBE.hurtOnce.put(target.getUUID(),action)!=null)throw new IllegalStateException("duplicate one-shot native hurt listener");}

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1250)
    public static void naturalStationaryMeleeNeverPursuesAndRealStopFollowAttackOrQueuedMoveOwnFutureOrders(GameTestHelper h) {
        floor(h);var station=snail(h,"Commands",4,1,5);var stopped=snail(h,"Commands",4,1,14);var following=snail(h,"Commands",4,1,23);var attacking=snail(h,"Commands",18,1,5);var queued=snail(h,"Commands",18,1,14);
        var near=EntityRegistrar.ZOMBIE_UNIT.get().create(h.getLevel());owned(h,near,"CommandsEnemy",12,1,5);neutral(near);durable(near);
        var far=EntityRegistrar.ZOMBIE_UNIT.get().create(h.getLevel());owned(h,far,"CommandsEnemy",27,1,5);neutral(far);durable(far);
        var friend=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());owned(h,friend,"Commands",11,1,23);
        List<BasaltSnailUnit> sources=List.of(station,stopped,following,attacking,queued);Vec3[] starts=new Vec3[5];long[] castAt={0},hitAt={0};long[] revisions=new long[5];boolean[] hit={false};float[] farHealth={0};BlockPos queuedDestination=h.absolutePos(new BlockPos(27,0,14));
        var seq=ready(h,station,stopped,following,attacking,queued,near,far,friend)
          .thenExecute(()->{for(var source:sources)source.setNoAi(false);})
          .thenWaitUntil(()->{for(var source:sources)h.assertTrue(source.onGround()&&h.getLevel().noCollision(source),"each real command/combat ground navigator physically settled");})
          .thenExecute(()->{
              damage(station,10);station.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(8);
              for(int i=0;i<sources.size();i++){var source=sources.get(i);starts[i]=source.position();require(h,cast(h,source),SkillResult.SUCCESS,"genuine formation before native combat or new command lane="+i);revisions[i]=source.rts().commandRevision;}
              castAt[0]=h.getLevel().getGameTime();
              double gap=station.getBbWidth()/2+near.getBbWidth()/2+.1;position(near,station.position().add(gap,0,0));
              h.assertTrue(!station.getBoundingBox().intersects(near.getBoundingBox())&&station.hasLineOfSight(near)&&station.getTarget()==null&&near.getHealth()==1000,"legal nonoverlapping close enemy appears only after cast, before any natural target selection or melee receipt");
              order(h,stopped,UnitAction.STOP,-1,BlockPos.ZERO);order(h,following,UnitAction.FOLLOW,friend.getId(),friend.blockPosition());order(h,attacking,UnitAction.ATTACK,far.getId(),far.blockPosition());
              queuedOrder(h,queued,UnitAction.MOVE,queuedDestination);
              h.assertTrue(queuedMove(queued)&&SnailShellFormation.active(queued)&&queued.getMoveGoal().getMoveTarget()==null,"actual Shift packet retains future MOVE in server slow queue without issuing a new immediate command");
          });
        // Natural entity AI/physics only: no direct target setter, private targeting helper, hurt, or goal.tick.
        for(int i=0;i<99;i++)seq.thenIdle(1).thenExecute(()->{
            long elapsed=h.getLevel().getGameTime()-castAt[0];
            h.assertTrue(elapsed<100&&SnailShellFormation.active(station)&&SnailShellFormation.active(queued),"stationary-combat and Shift lanes preserve original hundred-tick live stance");
            h.assertTrue(horizontal(station.position(),starts[0])<.08&&horizontal(queued.position(),starts[4])<.08,"actual natural steering never pursues combat target or dequeues future MOVE during formation");
            h.assertTrue(queuedMove(queued)&&queued.getMoveGoal().getMoveTarget()==null&&!queued.isIdle(),"real future Shift MOVE remains queued through each active tick");
            if(!hit[0]&&near.getHealth()<1000) {
                close(h,1000-near.getHealth(),10,"original native melee goal lands one real ten-point hit after natural active-only target selection");
                h.assertTrue(station.getTarget()==near&&station.getHoldPosition(),"actual native goal held the real nearby target while effective stance stayed active");
                hit[0]=true;hitAt[0]=h.getLevel().getGameTime();farHealth[0]=near.getHealth();position(near,starts[0].add(8,0,0));
            }
            if(hit[0])close(h,near.getHealth(),farHealth[0],"moving actual former target out of melee reach prevents further hits; no teleport/ranged substitute");
            if(elapsed>=50)h.assertTrue(hit[0]&&hitAt[0]-castAt[0]<=50,"natural original melee cooldown/selection produced a real receipt within first half of skill window");
        });
        seq.thenExecute(()->{
            h.assertTrue(hit[0]&&h.getLevel().getGameTime()-hitAt[0]>=20,"observe genuine stationary melee followed by sustained real out-of-reach no-chase period");
            for(int i=1;i<=3;i++){var source=sources.get(i);h.assertTrue(!UnitServerEvents.hasPendingImmediateAction(source.getId())&&source.rts().commandRevision>revisions[i]&&!SnailShellFormation.active(source),"genuine newer STOP/FOLLOW/ATTACK executed and revoked only formation: lane="+i);}
            h.assertTrue(stopped.getTarget()==null&&stopped.getFollowTarget()==null&&stopped.getMoveGoal().getMoveTarget()==null&&horizontal(stopped.position(),starts[1])<.08,"real STOP leaves no former navigation or attack");
            h.assertTrue(following.getFollowTarget()==friend&&horizontal(following.position(),starts[2])>.75,"real FOLLOW exits formation and physically follows current friend");
            h.assertTrue(attacking.getTarget()==far&&attacking.getTargetGoal().forced&&horizontal(attacking.position(),starts[3])>.75,"real ATTACK exits formation and physically pursues exact newly ordered enemy");
            h.assertTrue(SnailShellFormation.remainingTicks(queued)==1&&queuedMove(queued),"Shift remains pending at exact ninety-nine ticks, not treated as an immediate order");
            for(var source:sources)h.assertTrue(cd(source)==480-(h.getLevel().getGameTime()-castAt[0]),"none of native melee or newer commands refunds/refreshes paid cooldown");
        }).thenWaitUntil(()->h.assertTrue(!SnailShellFormation.active(queued)&&!queuedMove(queued)&&queued.getMoveGoal().getMoveTarget()!=null,"only after natural expiry may ordinary idle queue dispatch the preserved real Shift MOVE"))
          .thenWaitUntil(()->h.assertTrue(horizontal(queued.position(),starts[4])>.75,"newly dispatched genuine Shift order causes actual native ground travel"))
          .thenExecute(()->{
              h.assertTrue(queued.rts().commandRevision>revisions[4]&&cd(queued)==480-(h.getLevel().getGameTime()-castAt[0]),"post-expiry queued command owns movement without rewriting original paid clock");
              h.assertTrue(!SnailShellFormation.active(stopped)&&stopped.getMoveGoal().getMoveTarget()==null,"old formation deadline cannot restore pre-STOP orders");
              ledger(h,"Commands");ledger(h,"CommandsEnemy");
              System.out.println("SNAIL_SHELL_NATURAL_MELEE firstHitElapsed="+(hitAt[0]-castAt[0])+" stationaryUntil99=true shiftTravel="+horizontal(queued.position(),starts[4]));
              pass("NATURAL_STATIONARY_MELEE_NO_PURSUIT_REAL_STOP_FOLLOW_ATTACK_SHIFT_EXPIRY");
          }).thenSucceed();
    }
    private static boolean queuedMove(BasaltSnailUnit source){return UnitServerEvents.getUnitActionSlowQueue().stream().anyMatch(item->item.getAction()==UnitAction.MOVE&&Arrays.stream(item.getUnitIds()).anyMatch(id->id==source.getId()));}
    private static void queuedOrder(GameTestHelper h,BasaltSnailUnit source,UnitAction action,BlockPos target) {
        try {
            var sender=player(h,source.getOwnerName());var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);connection.setListener(sender.connection);
            var ctor=net.minecraftforge.network.NetworkEvent.Context.class.getDeclaredConstructor(net.minecraft.network.Connection.class,net.minecraftforge.network.NetworkDirection.class,int.class);ctor.setAccessible(true);
            var context=ctor.newInstance(connection,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER,-1);h.assertTrue(context.getSender()==sender,"real Shift context authenticates exact owner");
            h.assertTrue(new com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket(source.getOwnerName(),action,-1,new int[]{source.getId()},target,BlockPos.ZERO,true).handle(()->context),"actual authenticated Shift packet accepted");
        } catch(ReflectiveOperationException failure){throw new RuntimeException("actual existing Shift network context",failure);}
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=1050)
    public static void deathUnloadAndOrdinaryNbtRoundtripClearOnlyFormationKeepPaidClockAndPreserveOldNativeShell(GameTestHelper h) {
        floor(h);var dead=snail(h,"Lifecycle",4,1,5);var unloaded=snail(h,"Lifecycle",4,1,12);var saved=snail(h,"Lifecycle",4,1,19);var unused=snail(h,"Lifecycle",4,1,26);
        BasaltSnailUnit[] loaded={null};long[] castAt={0},freshAt={0};UUID original=saved.getUUID();BlockPos anchor=h.absolutePos(new BlockPos(4,0,19));
        ready(h,dead,unloaded,saved,unused).thenExecute(()->{
            saved.setAnchor(anchor);close(h,rawDamage(saved,saved.damageSources().generic(),2),2,"real old reactive shell is earned before local entity-NBT boundary");oldShell(h,saved,80,200);
            for(var source:List.of(dead,unloaded,saved))require(h,cast(h,source),SkillResult.SUCCESS,"real authenticated cast before distinct lifecycle boundary");
            castAt[0]=h.getLevel().getGameTime();
            dead.invulnerableTime=0;h.assertTrue(dead.hurt(dead.damageSources().genericKill(),10000)&&!dead.isAlive(),"actual lethal damage, not a synthetic lifecycle helper");
            unloaded.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            h.assertTrue(unloaded.isRemoved()&&unloaded.getRemovalReason()==Entity.RemovalReason.UNLOADED_TO_CHUNK,"real removal event carries native unload reason");
            var snapshot=new net.minecraft.nbt.CompoundTag();h.assertTrue(saved.save(snapshot),"ordinary entity save emits complete registered NBT before removal");saved.discard();
            Entity restored=EntityType.loadEntityRecursive(snapshot,h.getLevel(),entity->entity);
            h.assertTrue(restored instanceof BasaltSnailUnit&&restored.getUUID().equals(original),"local entity-NBT roundtrip keeps exact registered type and UUID; this is not a claimed JVM restart");
            loaded[0]=(BasaltSnailUnit)restored;ENTITIES.add(restored);h.assertTrue(h.getLevel().addFreshEntity(restored),"actual restored entity enters level before natural publication wait");
            h.assertTrue(!SnailShellFormation.active(dead)&&!SnailShellFormation.active(unloaded)&&!SnailShellFormation.active(loaded[0]),"death, actual unload and local NBT each remove transient formation");
            h.assertTrue(cd(dead)==480&&cd(unloaded)==480&&cd(loaded[0])==480,"same-tick lifecycle boundaries never refund or restart original paid cooldown");
            oldShell(h,loaded[0],80,200);h.assertTrue(loaded[0].getAnchor().equals(anchor),"ordinary NBT preserves original nonzero anchor and native old shell rather than applying formation cleanup to them");
            require(h,cast(h,dead),SkillResult.INVALID_SOURCE,"dead original body cannot recast");require(h,cast(h,unloaded),SkillResult.INVALID_SOURCE,"unloaded original body cannot recast");
        }).thenWaitUntil(()->h.assertTrue(loaded[0].tickCount>3&&h.getLevel().getEntity(original)==loaded[0]&&h.getLevel().getEntity(loaded[0].getId())==loaded[0],"local loaded object truly published by both identities and naturally ticked"))
          .thenExecute(()->{
              int elapsed=(int)(h.getLevel().getGameTime()-castAt[0]);
              h.assertTrue(cd(loaded[0])==480-elapsed&&!SnailShellFormation.active(loaded[0])&&loaded[0].getAnchor().equals(anchor),"normal loaded state retains absolute original paid clock without short-effect replay");
              h.assertTrue(loaded[0].hasEffect(MobEffects.DAMAGE_RESISTANCE)&&loaded[0].getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier()==0&&loaded[0].hasEffect(MobEffects.MOVEMENT_SLOWDOWN)&&loaded[0].getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier()==2&&loaded[0].getShellCooldown()>0,"native old shell continues its original saved mechanism, not silently erased by new skill load cleanup");
              require(h,cast(h,loaded[0]),SkillResult.COOLDOWN,"ordinary loaded paid source still rejects free activation");
              require(h,cast(h,unused),SkillResult.SUCCESS,"genuinely unused live source can activate with a new real request");freshAt[0]=h.getLevel().getGameTime();
          }).thenWaitUntil(()->h.assertTrue(h.getLevel().getGameTime()>=castAt[0]+100,"cross original pre-removal formation deadline naturally"))
          .thenExecute(()->{
              h.assertTrue(!SnailShellFormation.active(dead)&&!SnailShellFormation.active(unloaded)&&!SnailShellFormation.active(loaded[0]),"old deadline never revives removed or loaded short effect");
              h.assertTrue(cd(loaded[0])==480-(h.getLevel().getGameTime()-castAt[0]),"old deadline neither refunds nor rewrites loaded cooldown");
              h.assertTrue(SnailShellFormation.remainingTicks(unused)==Math.max(0,100-(h.getLevel().getGameTime()-freshAt[0]))&&cd(unused)==480-(h.getLevel().getGameTime()-freshAt[0]),"fresh independent cast keeps its own original clocks, unaffected by former source expiry");
              h.assertTrue(!loaded[0].hasEffect(MobEffects.DAMAGE_RESISTANCE)&&!loaded[0].hasEffect(MobEffects.MOVEMENT_SLOWDOWN)&&loaded[0].getShellCooldown()>0&&loaded[0].getShellCooldown()<200,"old native short effects expire naturally while their independent cooldown continues");
              savedAnchor(h,loaded[0],anchor);ledger(h,"Lifecycle");
              pass("DEATH_UNLOAD_LOCAL_NBT_NO_FORMATION_REPLAY_PAID_CLOCK_OLD_SHELL_PRESERVED");
          }).thenSucceed();
    }

    private static void assertRepeatedStateTicks(GameTestHelper h,ServerPlayer owner,SkillUnit source,int cooldown,int remaining) {
        int packets=syncWire(owner).syncs.size();long revision=source.rts().skills().revision();UnitSkill skill=SkillService.skillFor(source);
        h.assertTrue(source.rts().skills().cooldownTicks(skill)==cooldown&&effectTicks(source)==remaining,"actual world-time cooldown/effect before duplicate state tick: "+skill);
        source.rts().skills().tick();source.rts().skills().tick();
        h.assertTrue(syncWire(owner).syncs.size()==packets&&source.rts().skills().revision()==revision&&source.rts().skills().cooldownTicks(skill)==cooldown&&effectTicks(source)==remaining,"same world tick duplicate calls do not double-send, double-decrement, refresh or consume live effect: "+skill);
    }
    private static int effectTicks(SkillUnit source) {
        if(source instanceof BasaltSnailUnit snail)return SnailShellFormation.remainingTicks(snail);
        if(source instanceof BeetleUnit beetle)return BeetleAdvance.remainingTicks(beetle);
        if(source instanceof FireflyWorker worker)return FireflyRush.remainingTicks(worker);
        throw new IllegalArgumentException("unsupported initial-sync fixture");
    }
    private static List<SyncObservation> positiveSync(ServerPlayer owner,SkillUnit source){return syncWire(owner).syncs.stream().filter(p->p.packet().sourceUuid().equals(source.mob().getUUID())&&p.packet().revision()>=0).toList();}
    private static void noForeignSync(GameTestHelper h,ServerPlayer stranger,List<SkillUnit> sources,String phase){h.assertTrue(syncWire(stranger).syncs.stream().noneMatch(p->sources.stream().anyMatch(source->p.packet().sourceUuid().equals(source.mob().getUUID()))),"nonowner gets neither cooldown nor target identity payload: "+phase);}
    private static SyncRecordingConnection syncWire(ServerPlayer player){return ((SyncRecordingHandler)player.connection).wire;}
    /** Actual PlayerList membership only; no direct SkillState.sync call or fake packet delivery. */
    @SuppressWarnings("unchecked") private static void syncMembership(ServerPlayer player,boolean present) {
        try {
            var list=player.serverLevel().getServer().getPlayerList();var players=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");var byId=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");players.setAccessible(true);byId.setAccessible(true);
            List<ServerPlayer> actual=(List<ServerPlayer>)players.get(list);Map<UUID,ServerPlayer> ids=(Map<UUID,ServerPlayer>)byId.get(list);
            if(present){if(!actual.contains(player))actual.add(player);ids.put(player.getUUID(),player);SYNC_MEMBERS.add(player);}
            else {actual.remove(player);ids.remove(player.getUUID(),player);SYNC_MEMBERS.remove(player);}
        } catch(ReflectiveOperationException error){throw new RuntimeException("actual local PlayerList membership",error);}
    }
    private record SyncObservation(long gameTime,SkillNetwork.Sync packet) { }
    private static final class SyncRecordingHandler extends ServerGamePacketListenerImpl {
        final SyncRecordingConnection wire;
        SyncRecordingHandler(ServerPlayer player){this(player,new SyncRecordingConnection(player.serverLevel()));}
        private SyncRecordingHandler(ServerPlayer player,SyncRecordingConnection wire){super(player.serverLevel().getServer(),wire,player);this.wire=wire;}
        @Override public void tick(){}
        @Override public void send(net.minecraft.network.protocol.Packet<?> packet){wire.send(packet);}
        @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){wire.send(packet,listener);}
    }
    private static final class SyncRecordingConnection extends net.minecraft.network.Connection {
        final ServerLevel level;final List<SyncObservation> syncs=new ArrayList<>();
        SyncRecordingConnection(ServerLevel level){super(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);this.level=level;}
        @Override public void tick(){}
        @Override public void send(net.minecraft.network.protocol.Packet<?> packet){record(packet);}
        @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){record(packet);}
        private void record(net.minecraft.network.protocol.Packet<?> packet) {
            if(packet instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket payload&&payload.getIdentifier().equals(DreamseekersMod.id("unit_skills"))) {
                FriendlyByteBuf bytes=new FriendlyByteBuf(payload.getData().copy());
                try {if(bytes.readVarInt()==1){var sync=SkillNetwork.Sync.decode(bytes);if(bytes.readableBytes()!=0)throw new IllegalStateException("trailing encoded Sync payload");syncs.add(new SyncObservation(level.getGameTime(),sync));}}
                finally {bytes.release();}
            }
        }
    }

    private static final class Shot {
        final Mob shooter,target;final Vec3 start;final boolean paid;AbstractArrow projectile;double travel;boolean callback,resistanceAtDamage;
        Shot(Mob shooter,Mob target,boolean paid){this.shooter=shooter;this.target=target;this.paid=paid;start=shooter.position();}
    }
    private static Shot launch(GameTestHelper h,Mob shooter,Mob target,boolean paid) {
        var shot=new Shot(shooter,target,paid);float health=target.getHealth();
        watchDamage(target,event->{
            shot.callback=true;shot.resistanceAtDamage=target.hasEffect(MobEffects.DAMAGE_RESISTANCE);
            h.assertTrue(event.getSource().getEntity()==shooter,"actual primary belongs to exact real RTS firing source");
            h.assertTrue(shot.projectile!=null,"factory publication preceded natural collision");
            if(paid)h.assertTrue(shot.projectile instanceof BoneWingBolt&&event.getSource().getDirectEntity()==shot.projectile&&shot.projectile.isRemoved(),"actual paid bolt consumes before native damage callbacks");
            else {
                Entity proxy=event.getSource().getDirectEntity();
                h.assertTrue(proxy instanceof Arrow&&proxy!=shot.projectile&&!proxy.isAddedToWorld()&&h.getLevel().getEntity(proxy.getUUID())==null,"actual native Arrow DamageSource has separate unpublished proxy, never fabricated proof");
                h.assertTrue(h.getLevel().getEntity(shot.projectile.getId())==shot.projectile&&h.getLevel().getEntity(shot.projectile.getUUID())==shot.projectile,"original flying Arrow remains genuinely published at native callback");
            }
        });
        ((RangedAttackerUnit)shooter).performUnitRangedAttack(target,1);
        var found=h.getLevel().getEntitiesOfClass(AbstractArrow.class,shooter.getBoundingBox().inflate(2),a->a.getOwner()==shooter);
        h.assertTrue(found.size()==1,"exact one genuine normal factory projectile, shooter="+shooter.getUUID()+" target="+target.getUUID()+" paid="+paid+" count="+found.size());
        shot.projectile=found.get(0);ENTITIES.add(shot.projectile);
        h.assertTrue(target.getHealth()==health,"factory did not instantly hurt distant victim");
        return shot;
    }
    /** Entire timeline is constructed during test setup, never nested registration from callbacks. */
    private static void observe(GameTestSequence seq,Shot[] shots,int ticks) {
        for(int i=0;i<ticks;i++)seq.thenIdle(1).thenExecute(()->{for(var shot:shots)shot.travel=Math.max(shot.travel,shot.projectile.position().distanceTo(shot.start));});
    }
    private static void flight(GameTestHelper h,Shot shot,String lane) {
        System.out.println("SNAIL_SHELL_PRIMARY "+lane+" source="+shot.shooter.getUUID()+" sourceType="+EntityType.getKey(shot.shooter.getType())+" target="+shot.target.getUUID()+" targetType="+EntityType.getKey(shot.target.getType())+" projectile="+shot.projectile.getUUID()+" paid="+shot.paid+" travel="+shot.travel+" callback="+shot.callback+" removed="+shot.projectile.isRemoved()+" hp="+shot.target.getHealth());
        h.assertTrue(shot.callback&&shot.travel>2&&shot.projectile.isRemoved(),"real factory release, natural flight and exact primary receipt: "+lane);
    }
    private static BasaltSnailUnit snail(GameTestHelper h,String owner,int x,int y,int z){var e=DreamseekersMod.BASALT_SNAIL.get().create(h.getLevel());owned(h,e,owner,x,y,z);neutral(e);return e;}
    private static BeetleUnit beetle(GameTestHelper h,String owner,int x,int y,int z){var e=DreamseekersMod.BEETLE.get().create(h.getLevel());owned(h,e,owner,x,y,z);neutral(e);return e;}
    private static SkeletonUnit skeleton(GameTestHelper h,String owner,int x,int y,int z){var e=EntityRegistrar.SKELETON_UNIT.get().create(h.getLevel());owned(h,e,owner,x,y,z);e.setupEquipmentAndUpgradesServer();damage(e,10);return e;}
    private static BoneWingUnit bone(GameTestHelper h,String owner,int x,int y,int z){var e=DreamseekersMod.BONE_WING_UNIT.get().create(h.getLevel());owned(h,e,owner,x,y,z);damage(e,10);return e;}
    private static void owned(GameTestHelper h,Mob e,String suffix,int x,int y,int z) {
        h.assertTrue(e!=null&&e instanceof Unit,"real registered RTS fixture exists");((Unit)e).setOwnerName(account(suffix));e.setNoAi(true);
        var aggro=e.getAttribute(AttributeRegistrar.AGGRO_RANGE.get());if(aggro!=null)aggro.setBaseValue(0);
        position(e,Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(x,y,z))));ENTITIES.add(e);
        h.assertTrue(h.getLevel().addFreshEntity(e),"real body added before publication/natural tick wait");player(h,suffix);
    }
    private static String account(String suffix) {
        String name=suffix.startsWith(PREFIX)?suffix:PREFIX+suffix;
        if(ACCOUNTS.add(name)){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,123,234,345,456));}
        return name;
    }
    private static FakePlayer player(GameTestHelper h,String suffix) {
        String name=account(suffix);return PLAYERS.computeIfAbsent(name,key->{
            var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),key));
            player.connection=new SilentHandler(player);position(player,Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(2,1,29))));
            ENTITIES.add(player);h.getLevel().addNewPlayer(player);PlayerServerEvents.players.add(player);return player;
        });
    }
    private static SkillRequest request(BasaltSnailUnit e){return new SkillRequest(UnitSkill.SNAIL_SHELL_FORMATION,e.getId(),e.getUUID(),e.rts().skills().revision(),e.getId(),e.getUUID());}
    private static SkillResult cast(GameTestHelper h,BasaltSnailUnit e){return SkillService.request(player(h,e.getOwnerName()),request(e));}
    private static int cd(BasaltSnailUnit e){return e.rts().skills().cooldownTicks(UnitSkill.SNAIL_SHELL_FORMATION);}
    private static void order(GameTestHelper h,BasaltSnailUnit e,UnitAction action,int targetId,BlockPos position) {
        try {
            ServerPlayer sender=player(h,e.getOwnerName());var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);connection.setListener(sender.connection);
            var ctor=net.minecraftforge.network.NetworkEvent.Context.class.getDeclaredConstructor(net.minecraft.network.Connection.class,net.minecraftforge.network.NetworkDirection.class,int.class);ctor.setAccessible(true);
            var context=ctor.newInstance(connection,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER,-1);
            h.assertTrue(context.getSender()==sender,"actual Forge command context resolves exact authenticated owner");
            h.assertTrue(new com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket(e.getOwnerName(),action,targetId,new int[]{e.getId()},position,BlockPos.ZERO,false).handle(()->context),"real authenticated command accepted: "+action);
        } catch(ReflectiveOperationException error){throw new RuntimeException("exact existing Forge command context",error);}
    }
    private static GameTestSequence ready(GameTestHelper h,Entity...es) {
        return h.startSequence().thenWaitUntil(()->{for(Entity e:es)h.assertTrue(e.tickCount>3&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e,"natural ticks and real ID/UUID publication: "+e.getUUID()+" ticks="+e.tickCount);})
          .thenExecute(()->{h.assertTrue(!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isEnabled(),"focused core uses actual fog-off environment");for(Entity e:es)if(e instanceof Unit u){h.assertTrue(u.getItems().isEmpty(),"construction drops did not contaminate cargo");ledger(h,u.getOwnerName());}});
    }
    private static void damage(LivingEntity e,double amount){e.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(amount);if(e.getAttribute(Attributes.ATTACK_DAMAGE)!=null)e.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(amount);var crit=e.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get());if(crit!=null)crit.setBaseValue(0);}
    private static void neutral(LivingEntity e){e.getAttribute(Attributes.ARMOR).setBaseValue(0);e.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);e.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);e.setAbsorptionAmount(0);for(var attr:List.of(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(),AttributeRegistrar.RANGED_DAMAGE_RESIST.get()))if(e.getAttribute(attr)!=null)e.getAttribute(attr).setBaseValue(0);}
    private static void durable(LivingEntity e){e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);e.setHealth(1000);}
    private static void position(Entity e,Vec3 p){e.moveTo(p.x,p.y,p.z);e.setDeltaMovement(Vec3.ZERO);}
    private static double horizontal(Vec3 a,Vec3 b){return Math.sqrt((a.x-b.x)*(a.x-b.x)+(a.z-b.z)*(a.z-b.z));}
    private static double rawDamage(LivingEntity e,DamageSource source,float amount){float health=e.getHealth();e.invulnerableTime=0;e.hurt(source,amount);return health-e.getHealth();}
    private static void oldShell(GameTestHelper h,BasaltSnailUnit e,int duration,int cooldown){var resistance=e.getEffect(MobEffects.DAMAGE_RESISTANCE);var slow=e.getEffect(MobEffects.MOVEMENT_SLOWDOWN);h.assertTrue(resistance!=null&&resistance.getAmplifier()==0&&resistance.getDuration()==duration&&slow!=null&&slow.getAmplifier()==2&&slow.getDuration()==duration&&e.getShellCooldown()==cooldown,"old actual on-hit shell remains Resistance I80 / Slow III80 / independent CD200");}
    private static void ledger(GameTestHelper h,String suffix){String owner=suffix.startsWith(PREFIX)?suffix:PREFIX+suffix;var r=ResourcesServerEvents.resourcesList.stream().filter(v->v.ownerName.equals(owner)).findFirst().orElse(null);h.assertTrue(r!=null,"original bank exists; never recreate after defeat");h.assertTrue(r.food==123&&r.wood==234&&r.ore==345&&r.emerald==456&&r.foodToAdd==0&&r.woodToAdd==0&&r.oreToAdd==0&&r.emeraldToAdd==0,"skill never changes bank or pending balances: "+owner);}
    private static void require(GameTestHelper h,SkillResult actual,SkillResult expected,String why){h.assertTrue(actual==expected,why+" expected="+expected+" actual="+actual);}
    private static void close(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<.001,why+" expected="+expected+" actual="+actual);}
    private static void pass(String name){System.out.println("DREAMSEEKERS_SNAIL_SHELL_FORMATION_PASS "+name);}
    private static void watchDamage(LivingEntity e,Consumer<LivingDamageEvent> action){if(!listening){MinecraftForge.EVENT_BUS.register(PROBE);listening=true;}if(PROBE.damageOnce.put(e.getUUID(),action)!=null)throw new IllegalStateException("duplicate one-shot native damage listener");}
    private static final class Probe {final Map<UUID,Consumer<net.minecraftforge.event.entity.living.LivingHurtEvent>> hurtOnce=new HashMap<>();@SubscribeEvent public void hurt(net.minecraftforge.event.entity.living.LivingHurtEvent e){var once=hurtOnce.remove(e.getEntity().getUUID());if(once!=null)once.accept(e);}final Map<UUID,Consumer<LivingDamageEvent>> damageOnce=new HashMap<>();@SubscribeEvent(priority=EventPriority.LOWEST) public void damage(LivingDamageEvent e){var once=damageOnce.remove(e.getEntity().getUUID());if(once!=null)once.accept(e);}}
    private static void floor(GameTestHelper h){var p=h.absolutePos(BlockPos.ZERO);var level=h.getLevel();for(int x=0;x<32;x++)for(int z=0;z<32;z++)level.setBlock(p.offset(x,9,z),Blocks.STONE.defaultBlockState(),2);level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+32,level.getMaxBuildHeight(),p.getZ()+32)).forEach(Entity::discard);for(int x=0;x<32;x++)for(int z=0;z<32;z++){level.setBlock(p.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<9;y++)level.setBlock(p.offset(x,y,z),(x==0||x==31||z==0||z==31?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}level.getEntitiesOfClass(ItemEntity.class,new AABB(p,p.offset(32,10,32))).forEach(Entity::discard);}
    private static final class SilentHandler extends ServerGamePacketListenerImpl {SilentHandler(ServerPlayer p){super(p.serverLevel().getServer(),new SilentConnection(),p);}@Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> p){}@Override public void send(net.minecraft.network.protocol.Packet<?> p,net.minecraft.network.PacketSendListener listener){}}
    private static final class SilentConnection extends net.minecraft.network.Connection {SilentConnection(){super(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);}@Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> p){}@Override public void send(net.minecraft.network.protocol.Packet<?> p,net.minecraft.network.PacketSendListener listener){}}
}
