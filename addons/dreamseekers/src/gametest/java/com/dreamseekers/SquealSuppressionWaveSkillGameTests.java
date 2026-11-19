// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.*;
import com.dreamseekers.shadow.SquealWaveBolt;
import com.dreamseekers.shadow.SuppressionWaveProjectile;
import com.dreamseekers.unit.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.goals.UnitRangedAttackGoal;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

/** Real authenticated casts and real projectile collisions. No hand-created projectile/skill scope.
 * Sources prepare under explicit NoAI to isolate skill quantification; the ordinary Bone control
 * restores natural AI and uses its installed ranged goal. HP/Gel are read at real Level END, never
 * inferred from Forge event amounts. Every sequence is completely registered before ticking.
 */
@GameTestHolder("dreamseekers_squeal_suppression_wave")
@PrefixGameTestTemplate(false)
public final class SquealSuppressionWaveSkillGameTests {
    private static final String BATCH="dreamseekers_squeal_suppression_wave",PREFIX="Suppression_";
    private static final List<Entity> ENTITIES=new ArrayList<>();
    private static final Set<String> OWNERS=new LinkedHashSet<>();
    private static final Map<String,FakePlayer> PLAYERS=new HashMap<>();
    private static final Set<ServerPlayer> SYNC_MEMBERS=new LinkedHashSet<>();
    private static final Set<ChunkPos> TICKETS=new LinkedHashSet<>();
    private static final Probe PROBE=new Probe();
    private static boolean listening;

    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) {
        for(Object listener:EXTRA_LISTENERS)MinecraftForge.EVENT_BUS.unregister(listener);EXTRA_LISTENERS.clear();
        if(listening){MinecraftForge.EVENT_BUS.unregister(PROBE);listening=false;}
        for(var trace:PROBE.traces.values())for(var shot:trace.shots)if(!shot.isRemoved())shot.discard();
        PROBE.traces.clear();PROBE.hurtOnce.clear();PROBE.damageOnce.clear();
        for(var member:List.copyOf(SYNC_MEMBERS))syncMembership(member,false);
        for(String a:OWNERS)for(String b:OWNERS)if(!a.equals(b))AlliancesServerEvents.removeAlliance(a,b);
        PlayerServerEvents.players.removeIf(p->OWNERS.contains(p.getGameProfile().getName()));
        PlayerServerEvents.rtsPlayers.removeIf(p->OWNERS.contains(p.name));
        ResourcesServerEvents.resourcesList.removeIf(r->OWNERS.contains(r.ownerName));
        for(Entity e:List.copyOf(ENTITIES))if(!e.isRemoved()) {
            if(e instanceof ServerPlayer p)p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);
            else e.discard();
        }
        for(var chunk:TICKETS)level.setChunkForced(chunk.x,chunk.z,false);
        TICKETS.clear();ENTITIES.clear();OWNERS.clear();PLAYERS.clear();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=520)
    public static void authenticatedTargetCodecAndNaturalOwnerSyncCommitOnlyOnePaidImmediateProjectile(GameTestHelper h) {
        room(h);var owner=player(h,"Authority");var stranger=player(h,"AuthorityOther");
        owner.connection=new RecordingHandler(owner);stranger.connection=new RecordingHandler(stranger);
        syncMembership(owner,true);syncMembership(stranger,true);
        var source=squeal(h,"Authority",4.5,5.5);var enemy=jelly(h,"AuthorityEnemy",12.5,5.5);
        var own=jelly(h,"Authority",7.5,9.5);var ally=jelly(h,"AuthorityAlly",7.5,12.5);var far=jelly(h,"AuthorityEnemy",25.5,5.5);
        var wrong=DreamseekersMod.FIREFLY_WORKER.get().create(h.getLevel());owned(h,wrong,"Authority",5.5,20.5);
        var wild=EntityType.COW.create(h.getLevel());wild.setNoAi(true);place(h,wild,8.5,20.5);
        AlliancesServerEvents.addAlliance(source.getOwnerName(),ally.getOwnerName());
        var trace=watch(source,enemy);long[] at={-1};
        ready(h,source,enemy,own,ally,far,wrong,wild)
          .thenWaitUntil(()->h.assertTrue(syncs(owner,source).size()>=2,"natural source ticks publish initial and ten-tick actual encoded owner Sync"))
          .thenExecute(()->{
              var sync=syncs(owner,source);h.assertTrue(sync.get(0).packet().cooldownTicks()==0&&sync.get(1).gameTime()-sync.get(0).gameTime()==10,"initial/heartbeat real clock, never direct state.sync");
              h.assertTrue(h.getLevel().getServer().getPlayerList().getPlayerByName(source.getOwnerName())==owner&&syncs(stranger,source).isEmpty(),"actual owner PlayerList index receives Sync; foreign member does not");
              h.assertTrue(source.getAbilities().get().equals(List.of(UnitSkillAbility.SQUEAL_SUPPRESSION_WAVE))&&UnitSkill.SQUEAL_SUPPRESSION_WAVE.range()==10&&UnitSkill.SQUEAL_SUPPRESSION_WAVE.cooldownTicks()==360&&UnitSkill.SQUEAL_SUPPRESSION_WAVE.durationTicks()==80,"exact singleton and approved ten/360/80 contract");
              var packet=new SkillNetwork.Cast(request(source,enemy));var bytes=new FriendlyByteBuf(Unpooled.buffer());SkillNetwork.Cast decoded;
              try{packet.encode(bytes);decoded=SkillNetwork.Cast.decode(bytes);h.assertTrue(packet.equals(decoded)&&bytes.readableBytes()==0,"real Cast codec preserves exact source/target identity and revision");}finally{bytes.release();}
              long revision=source.rts().skills().revision();
              require(h,decoded.execute(null),SkillResult.INVALID_REQUEST,"no authenticated sender");
              require(h,decoded.execute(stranger),SkillResult.NOT_OWNER,"foreign sender");
              require(h,SkillService.request(owner,new SkillRequest(UnitSkill.SQUEAL_SUPPRESSION_WAVE,source.getId(),UUID.randomUUID(),revision,enemy.getId(),enemy.getUUID())),SkillResult.INVALID_SOURCE,"stale source UUID");
              require(h,SkillService.request(owner,new SkillRequest(UnitSkill.SQUEAL_SUPPRESSION_WAVE,source.getId(),source.getUUID(),revision,enemy.getId(),UUID.randomUUID())),SkillResult.INVALID_TARGET,"stale target UUID");
              require(h,SkillService.request(owner,new SkillRequest(UnitSkill.SQUEAL_SUPPRESSION_WAVE,wrong.getId(),wrong.getUUID(),wrong.rts().skills().revision(),enemy.getId(),enemy.getUUID())),SkillResult.INVALID_SOURCE,"worker cannot borrow this active");
              for(Entity target:List.of(source,own,ally,wild))require(h,cast(h,source,target),SkillResult.INVALID_TARGET,"not a hostile paid target: "+target.getUUID());
              require(h,cast(h,source,far),SkillResult.RANGE,"out of ten-block range");
              enemy.setInvisible(true);require(h,cast(h,source,enemy),SkillResult.HIDDEN,"actual invisible target");enemy.setInvisible(false);
              require(h,java.util.concurrent.CompletableFuture.supplyAsync(()->decoded.execute(owner)).join(),SkillResult.INVALID_REQUEST,"off-server-thread request");
              h.assertTrue(cd(source)==0&&source.rts().skills().revision()==revision&&trace.shots.isEmpty()&&enemy.getHealth()==1000,"every denial leaves CD/revision/HP/projectiles unchanged");
              at[0]=h.getLevel().getGameTime();require(h,decoded.execute(owner),SkillResult.SUCCESS,"real owner launches legal independent skill projectile");
              immediate(h,trace,at[0]);h.assertTrue(cd(source)==360&&enemy.getHealth()==1000,"successful spawn commits exact CD, not instantaneous hurt");
              require(h,decoded.execute(owner),SkillResult.REPLAY,"old request cannot repeat");require(h,cast(h,source,enemy),SkillResult.COOLDOWN,"fresh revision cannot refresh paid clock");
              var last=syncs(owner,source).get(syncs(owner,source).size()-1).packet();h.assertTrue(last.cooldownTicks()==360&&last.revision()==source.rts().skills().revision(),"actual cast encodes owner revision/CD");bank(h);
          }).thenIdle(11).thenExecute(()->{
              h.assertTrue(syncs(owner,source).stream().anyMatch(s->s.gameTime()==at[0]+10&&s.packet().cooldownTicks()==350)&&syncs(stranger,source).isEmpty(),"natural tenth-tick paid heartbeat remains owner-only");
          }).thenIdle(348).thenExecute(()->{
              h.assertTrue(h.getLevel().getGameTime()==at[0]+359&&cd(source)==1,"absolute CD last tick independent of projectile/debuff expiry");
              require(h,cast(h,source,enemy),SkillResult.COOLDOWN,"no early recast");bank(h);
          }).thenIdle(1).thenExecute(()->{
              h.assertTrue(cd(source)==0,"exact original deadline360");require(h,cast(h,source,enemy),SkillResult.SUCCESS,"fresh real cast at original deadline");
              h.assertTrue(skillShots(trace).size()==2&&cd(source)==360,"second legal cast adds exactly one distinct skill projectile");bank(h);pass("AUTH_CODEC_NATURAL_SYNC_IMMEDIATE_CD360");
          }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=180)
    public static void physicalSnapshotUsesNearestThreeImpactCentresAndARealWallStopsTheIndependentShot(GameTestHelper h) {
        room(h);partition(h,20);
        var source=squeal(h,"Snapshot",4.5,10.5);var primary=jelly(h,"SnapshotEnemy",12.5,10.5);
        var a=jelly(h,"SnapshotEnemy",11.85,11.4);var b=jelly(h,"SnapshotEnemy",11.85,9.6);
        var c=jelly(h,"SnapshotEnemy",13.4,11);var d=jelly(h,"SnapshotEnemy",13.4,10);
        var friendly=jelly(h,"Snapshot",12.65,12);var outside=jelly(h,"SnapshotEnemy",12.5,13.5);
        var wallSource=squeal(h,"Wall",4.5,25.5);var wallTarget=jelly(h,"WallEnemy",12.5,25.5);var wallNear=jelly(h,"WallEnemy",13.4,25.5);
        var trace=watch(source,primary,a,b,c,d,friendly,outside);trace.candidates=List.of(a,b,c,d);trace.friendly=friendly;trace.outside=outside;
        var blocked=watch(wallSource,wallTarget,wallNear);long[] at={-1};
        var seq=ready(h,source,primary,a,b,c,d,friendly,outside,wallSource,wallTarget,wallNear).thenExecute(()->{
            wall(h,Blocks.STONE);h.assertTrue(!wallSource.hasLineOfSight(wallTarget),"actual full-height fixture wall blocks LOS");
            long revision=wallSource.rts().skills().revision();h.assertTrue(cast(h,wallSource,wallTarget)!=SkillResult.SUCCESS&&cd(wallSource)==0&&wallSource.rts().skills().revision()==revision&&blocked.shots.isEmpty(),"wall at request rejects without paying/spawning");
            wall(h,Blocks.AIR);h.assertTrue(wallSource.hasLineOfSight(wallTarget),"same actual lane clear before legitimate cast");
            at[0]=h.getLevel().getGameTime();require(h,cast(h,source,primary),SkillResult.SUCCESS,"release base ten");require(h,cast(h,wallSource,wallTarget),SkillResult.SUCCESS,"legal independent wall-lane shot");
            immediate(h,trace,at[0]);immediate(h,blocked,at[0]);damage(source,20);close(h,source.getUnitAttackDamage(),20,"real in-flight attribute change, not snapshot injection");
            wall(h,Blocks.STONE);h.assertTrue(primary.getHealth()==1000&&wallTarget.getHealth()==1000,"distant real shots have not instant-hit");
        });
        for(int tick=1;tick<=30;tick++)seq.thenIdle(1).thenExecute(()->{observe(trace);observe(blocked);});
        seq.thenExecute(()->{
            completed(h,trace,primary,false);completed(h,blocked,null,false);
            var impact=trace.impacts.get(0);h.assertTrue(impact.distances.size()==4&&impact.distances.values().stream().allMatch(v->v<=4),"all four genuine candidate bbox centres inside collision-centred radius two: "+impact.distances);
            h.assertTrue(impact.friendlyDistance<=4&&impact.outsideDistance>4,"friendly inside and hostile outside actual radius bracket selection");
            var expected=new ArrayList<UUID>();expected.add(primary.getUUID());expected.addAll(impact.ordered.subList(0,3));
            h.assertTrue(trace.damage.stream().map(DamageRead::target).toList().equals(expected),"actual primary then distance/UUID nearest three, total four: expected="+expected+" actual="+trace.damage);
            for(var target:List.of(primary,a,b,c,d)){boolean hit=expected.contains(target.getUUID());close(h,1000-after(h,trace,target).health,hit?8:0,"each actual hit is snapshot ten times .8, never later twenty or compounded splash");h.assertTrue(after(h,trace,target).suppressed==hit,"only actually hit recipients obtain managed slowdown");if(hit)h.assertTrue(after(h,trace,target).remaining==80,"fresh successful hit grants exact eighty ticks");}
            close(h,after(h,trace,friendly).health,1000,"own unit excluded");close(h,after(h,trace,outside).health,1000,"out-of-radius unit excluded");
            h.assertTrue(!after(h,trace,friendly).suppressed&&!after(h,trace,outside).suppressed,"negative recipients gain no hidden attack slowdown");
            h.assertTrue(blocked.impacts.get(0).kind==HitResult.Type.BLOCK&&blocked.damage.isEmpty(),"real physical wall terminates projectile without primary or secondary hurt");
            for(var target:List.of(wallTarget,wallNear)){close(h,after(h,blocked,target).health,1000,"wall prevents HP");h.assertTrue(!after(h,blocked,target).suppressed,"wall prevents suppression");}
            for(int z=23;z<=28;z++)for(int y=1;y<=8;y++)h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(8,y,z))).is(Blocks.STONE),"skill never destroys wall terrain");
            h.assertTrue(cd(source)==330&&cd(wallSource)==330&&source.getAttackBuildingGoal()==null&&!source.canAttackBuildings(),"one paid clock per projectile, no building route");
            bank(h);pass("PHYSICAL_SNAPSHOT80_R2_NEAREST3_WALL_NO_TERRAIN");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=180)
    public static void exactHealthAndEnemyGelReceiptsGateBothSplashAndEachRecipientsOwnSlowdown(GameTestHelper h) {
        room(h);for(int z:List.of(7,13,19,25))partition(h,z);
        List<ShadowSquealGhostUnit> sources=new ArrayList<>();List<JellyfishUnit> targets=new ArrayList<>(),near=new ArrayList<>();List<Trace> traces=new ArrayList<>();List<Entity> bodies=new ArrayList<>();
        for(int lane=0;lane<5;lane++){int z=4+lane*6;var source=squeal(h,"Receipt"+lane,4.5,z);var target=jelly(h,"ReceiptEnemy"+lane,12.5,z);var secondary=jelly(h,"ReceiptEnemy"+lane,13.4,z+.5);sources.add(source);targets.add(target);near.add(secondary);bodies.addAll(List.of(source,target,secondary));traces.add(watch(source,target,secondary));}
        var gelSlime=DreamseekersMod.PINK_SLIME_UNIT.get().create(h.getLevel());owned(h,gelSlime,"ReceiptEnemy1",17.5,10);bodies.add(gelSlime);
        var absorbSecondary=jelly(h,"ReceiptEnemy1",13.4,9.5);bodies.add(absorbSecondary);traces.get(1).observed.put(absorbSecondary.getUUID(),absorbSecondary);
        var seq=ready(h,bodies.toArray(Entity[]::new)).thenExecute(()->{
            require(h,otherSkill(h,gelSlime,targets.get(1),UnitSkill.GEL_GUARD),SkillResult.SUCCESS,"actual enemy Gel supplies twelve before real incoming wave");
            close(h,SkillEffects.shieldRemaining(targets.get(1)),12,"real shield positive control");
            absorbSecondary.setAbsorptionAmount(50);targets.get(4).setAbsorptionAmount(50);
            PROBE.hurtOnce.put(targets.get(2).getUUID(),e->e.setCanceled(true));
            PROBE.damageOnce.put(targets.get(3).getUUID(),e->e.setAmount(0));
            for(int lane=0;lane<5;lane++){require(h,cast(h,sources.get(lane),targets.get(lane)),SkillResult.SUCCESS,"actual independent receipt lane "+lane);immediate(h,traces.get(lane),h.getLevel().getGameTime());}
        });
        for(int tick=1;tick<=30;tick++)seq.thenIdle(1).thenExecute(()->traces.forEach(SquealSuppressionWaveSkillGameTests::observe));
        seq.thenExecute(()->{
            for(int lane=0;lane<5;lane++){
                var trace=traces.get(lane);completed(h,trace,targets.get(lane),false);var first=after(h,trace,targets.get(lane));var second=after(h,trace,near.get(lane));boolean receipt=lane<2;
                close(h,1000-first.health,lane==0?8:0,"actual primary HP rather than event amount in lane "+lane);
                close(h,1000-second.health,receipt?8:0,"only real primary HP/Gel permits secondary traversal lane "+lane);
                h.assertTrue(first.suppressed==receipt&&second.suppressed==receipt,"each actual HP/Gel recipient only receives own managed negative lane "+lane);
                if(receipt)h.assertTrue(first.remaining==80&&second.remaining==80,"actual newly installed effects each eighty ticks");
                h.assertTrue(cd(sources.get(lane))==330,"zero receipt never refunds paid projectile clock");
            }
            close(h,after(h,traces.get(1),targets.get(1)).gel,4,"Gel-only primary actual twelve-to-four debit proves splash eligibility despite HP zero");
            var absorbed=after(h,traces.get(1),absorbSecondary);close(h,absorbed.health,1000,"secondary native absorption-only has zero actual HP");close(h,absorbed.absorption,42,"real secondary native absorption debit");h.assertTrue(!absorbed.suppressed,"another successful primary cannot lend its receipt to absorbed secondary");
            close(h,after(h,traces.get(4),targets.get(4)).absorption,42,"native absorption-only primary really intercepted eight but cannot trigger splash/slow");
            h.assertTrue(PROBE.hurtOnce.isEmpty()&&PROBE.damageOnce.isEmpty(),"real target callbacks fired, never synthetic receipt state");bank(h);pass("EXACT_HP_GEL_CANCEL_ZERO_NATIVE_ABSORPTION_PER_RECIPIENT");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=220)
    public static void naturalOrdinarySquealStillUsesBoneButTheIndependentSkillNeverDoes(GameTestHelper h) {
        room(h);partition(h,10);partition(h,20);
        var plain=squeal(h,"Bone",4.5,5);var marked=squeal(h,"Bone",4.5,15);var skill=squeal(h,"Bone",4.5,25);
        var a=jelly(h,"BoneEnemy",12.5,5);var b=jelly(h,"BoneEnemy",12.5,15);var c=jelly(h,"BoneEnemy",12.5,25);var secondary=jelly(h,"BoneEnemy",13.4,25.5);
        var markOrdinary=DreamseekersMod.BONE_WING_UNIT.get().create(h.getLevel());owned(h,markOrdinary,"Bone",17.5,15);
        var markPrimary=DreamseekersMod.BONE_WING_UNIT.get().create(h.getLevel());owned(h,markPrimary,"Bone",17.5,24);
        var markSecondary=DreamseekersMod.BONE_WING_UNIT.get().create(h.getLevel());owned(h,markSecondary,"Bone",17.5,27);
        var first=watch(plain,a);first.ordinary=true;var second=watch(marked,b);second.ordinary=true;var third=watch(skill,c,secondary);
        long[] at={-1};
        var seq=ready(h,plain,marked,skill,a,b,c,secondary,markOrdinary,markPrimary,markSecondary).thenExecute(()->{
            h.assertTrue(plain.getAttackGoal() instanceof UnitRangedAttackGoal&&marked.getAttackGoal() instanceof UnitRangedAttackGoal,"unchanged genuine installed ranged goals");
            order(h,plain,a);order(h,marked,b);
        }).thenWaitUntil(()->h.assertTrue(!UnitServerEvents.hasPendingImmediateAction(plain.getId())&&!UnitServerEvents.hasPendingImmediateAction(marked.getId())&&plain.getTarget()==a&&marked.getTarget()==b,"real authenticated ordinary ATTACK dispatch selects precise victims"))
          .thenExecute(()->{
              h.assertTrue(first.shots.isEmpty()&&second.shots.isEmpty()&&third.shots.isEmpty()&&a.getHealth()==1000&&b.getHealth()==1000&&c.getHealth()==1000,"NoAI preparation has no prior ordinary projectile/HP contamination");
              require(h,otherSkill(h,markOrdinary,b,UnitSkill.COORDINATED_MARK),SkillResult.SUCCESS,"real Bone mark on ordinary control");
              require(h,otherSkill(h,markPrimary,c,UnitSkill.COORDINATED_MARK),SkillResult.SUCCESS,"real Bone mark on skill primary");
              require(h,otherSkill(h,markSecondary,secondary,UnitSkill.COORDINATED_MARK),SkillResult.SUCCESS,"real Bone mark on skill secondary");
              at[0]=h.getLevel().getGameTime();require(h,cast(h,skill,c),SkillResult.SUCCESS,"separate skill uses real owner service");immediate(h,third,at[0]);
              plain.setHoldPosition(true);marked.setHoldPosition(true);plain.setNoAi(false);marked.setNoAi(false);
          });
        for(int tick=1;tick<=65;tick++)seq.thenIdle(1).thenExecute(()->{
            observe(first);observe(second);observe(third);
            // Stop only subsequent ordinary shots after the observed first real physical hit.
            if(first.snapshot!=null)plain.setNoAi(true);if(second.snapshot!=null)marked.setNoAi(true);
        });
        seq.thenExecute(()->{
            completed(h,first,a,true);completed(h,second,b,true);completed(h,third,c,false);
            h.assertTrue(first.shots.size()==1&&second.shots.size()==1&&first.joinTimes.get(first.shots.get(0))>at[0]&&second.joinTimes.get(second.shots.get(0))>at[0],"ordinary projectiles came from later natural installed goal ticks, not a direct factory call");
            close(h,1000-after(h,first,a).health,10,"unmarked original ordinary base ten");close(h,1000-after(h,second,b).health,11.2,"original ordinary Squeal bolt retains true Bone one-point-twelve");
            close(h,1000-after(h,third,c).health,8,"independent skill primary never borrows ordinary Bone multiplier");close(h,1000-after(h,third,secondary).health,8,"independent skill secondary also never borrows Bone");
            h.assertTrue(!after(h,first,a).suppressed&&!after(h,second,b).suppressed&&after(h,third,c).suppressed&&after(h,third,secondary).suppressed,"ordinary baseline never acquires new skill suppression");
            h.assertTrue(CoordinatedMark.ticksRemaining(b,marked.getOwnerName())>0&&CoordinatedMark.ticksRemaining(c,skill.getOwnerName())>0&&CoordinatedMark.ticksRemaining(secondary,skill.getOwnerName())>0,"all real Bone marks still live, absence of amplification is not expired-mark artifact");
            h.assertTrue(cd(plain)==0&&cd(marked)==0&&cd(skill)==295,"ordinary goal never silently spends active CD");bank(h);pass("NATURAL_ORDINARY_BONE112_SKILL_PRIMARY_SECONDARY80_NOT_BONE");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=200)
    public static void actualConstructionJoinEpochsAndRecursiveReceiptsCannotMintOrRecycleSkillAuthority(GameTestHelper h) {
        room(h);for(int z:List.of(8,16,24))partition(h,z);for(int z=1;z<31;z++)for(int y=1;y<9;y++)h.getLevel().setBlock(h.absolutePos(new BlockPos(16,y,z)),Blocks.STONE.defaultBlockState(),2);
        List<ShadowSquealGhostUnit> sources=new ArrayList<>();List<JellyfishUnit> primary=new ArrayList<>(),near=new ArrayList<>();List<Trace> traces=new ArrayList<>();List<Entity> bodies=new ArrayList<>();
        for(int lane=0;lane<8;lane++){double x=lane<4?3.5:18.5,z=4+(lane%4)*8;var s=squeal(h,"Boundary"+lane,x,z);var p=jelly(h,"BoundaryEnemy"+lane,x+8,z);var n=jelly(h,"BoundaryEnemy"+lane,x+9,z+.5);sources.add(s);primary.add(p);near.add(n);traces.add(watch(s,p,n));bodies.addAll(List.of(s,p,n));}
        var boundary=new WaveBoundaryProbe();EXTRA_LISTENERS.add(boundary);MinecraftForge.EVENT_BUS.register(boundary);
        SkillResult[] nested={null};boolean[] sameAccepted={true},differentAccepted={false};float[] differentLoss={0};long[] at={-1};
        var seq=ready(h,bodies.toArray(Entity[]::new)).thenExecute(()->{
            at[0]=h.getLevel().getGameTime();
            boundary.constructing=event->{var s=sources.get(0);String owner=s.getOwnerName();s.setOwnerName(owner+"Temporary");s.setOwnerName(owner);};
            h.assertTrue(cast(h,sources.get(0),primary.get(0))!=SkillResult.SUCCESS,"real constructor owner ABA cannot mint a replacement authorization");
            h.assertTrue(boundary.constructed.size()==1&&boundary.constructed.get(0).isRemoved()&&traces.get(0).shots.isEmpty()&&cd(sources.get(0))==0,"actual constructor-created rejected body is gone and costs no cooldown");
            boundary.joins.put(sources.get(1).getUUID(),e->e.setCanceled(true));
            h.assertTrue(cast(h,sources.get(1),primary.get(1))!=SkillResult.SUCCESS,"actual Forge Join cancellation rejects publication");
            h.assertTrue(boundary.canceled!=null&&boundary.canceled.isRemoved()&&h.getLevel().getEntity(boundary.canceled.getUUID())==null&&cd(sources.get(1))==0,"canceled actual Join body never becomes a free published shot or paid CD");
            boundary.joins.put(sources.get(2).getUUID(),e->nested[0]=cast(h,sources.get(2),primary.get(2)));
            require(h,cast(h,sources.get(2),primary.get(2)),SkillResult.SUCCESS,"one outer real Join transaction succeeds");
            require(h,nested[0],SkillResult.ALREADY_ACTIVE,"real Join reentry cannot release second projectile before outer commit");
            replacement(h,sources.get(2),UnitAction.STOP,-1,BlockPos.ZERO);
            require(h,cast(h,sources.get(3),primary.get(3)),SkillResult.SUCCESS,"real published shot before projectile owner ABA");
            var changed=skillShots(traces.get(3)).get(0);changed.setOwner(sources.get(4));changed.setOwner(sources.get(3));
            require(h,cast(h,sources.get(4),primary.get(4)),SkillResult.SUCCESS,"real published shot before source owner ABA");String owner=sources.get(4).getOwnerName();sources.get(4).setOwnerName(owner+"Temporary");sources.get(4).setOwnerName(owner);
            PROBE.damageOnce.put(primary.get(5).getUUID(),e->{String name=primary.get(5).getOwnerName();primary.get(5).setOwnerName(name+"Temporary");primary.get(5).setOwnerName(name);});
            PROBE.hurtOnce.put(primary.get(6).getUUID(),e->{receiptBoundaryRaw(h,"same-before",primary.get(6),e.getSource(),null);sameAccepted[0]=primary.get(6).hurt(e.getSource(),3);receiptBoundaryRaw(h,"same-after",primary.get(6),e.getSource(),sameAccepted[0]);});
            PROBE.hurtOnce.put(primary.get(7).getUUID(),e->{float hp=primary.get(7).getHealth();receiptBoundaryRaw(h,"different-before",primary.get(7),e.getSource(),null);h.assertTrue(primary.get(7).invulnerableTime>10&&nativeLastHurt(primary.get(7))==8,"actual native iframe remembers outer eight before controlled distinct input ten");differentAccepted[0]=primary.get(7).hurt(primary.get(7).damageSources().generic(),10);differentLoss[0]=hp-primary.get(7).getHealth();receiptBoundaryRaw(h,"different-after",primary.get(7),e.getSource(),differentAccepted[0]);e.setAmount(0);});
            for(int lane=5;lane<8;lane++)require(h,cast(h,sources.get(lane),primary.get(lane)),SkillResult.SUCCESS,"actual paid callback receipt lane "+lane);
            for(int lane=2;lane<8;lane++)h.assertTrue(skillShots(traces.get(lane)).size()==1&&cd(sources.get(lane))==360,"one successful publication pays once even if later authority is revoked");
        });
        for(int tick=1;tick<=30;tick++)seq.thenIdle(1).thenExecute(()->traces.forEach(SquealSuppressionWaveSkillGameTests::observe));
        seq.thenExecute(()->{
            completed(h,traces.get(2),primary.get(2),false);close(h,1000-after(h,traces.get(2),primary.get(2)).health,8,"post-launch ordinary STOP never retracts committed nonhoming shot");close(h,1000-after(h,traces.get(2),near.get(2)).health,8,"same legitimate receipt still traverses one secondary after STOP");
            h.assertTrue(!UnitServerEvents.hasPendingImmediateAction(sources.get(2).getId()),"real STOP passed the normal dispatcher");
            for(int lane:List.of(0,1,3,4)){close(h,primary.get(lane).getHealth(),1000,"constructor/publication/epoch revoked lane stays HP inert: "+lane);close(h,near.get(lane).getHealth(),1000,"revoked shot never splashes");h.assertTrue(!SquealSuppressionWave.hasDebuff(primary.get(lane))&&!SquealSuppressionWave.hasDebuff(near.get(lane)),"revoked lane installs no effect");if(lane>=3)h.assertTrue(skillShots(traces.get(lane)).get(0).isRemoved()&&traces.get(lane).impacts.isEmpty(),"natural flight invalidation consumes old owner authority before collision");}
            for(int lane:List.of(5,6)){completed(h,traces.get(lane),primary.get(lane),false);close(h,after(h,traces.get(lane),primary.get(lane)).health,1000,"actual callback target epoch/same-DS revocation fails closed, unlike ordinary base damage");close(h,after(h,traces.get(lane),near.get(lane)).health,1000,"invalid primary receipt cannot lend AOE");h.assertTrue(!after(h,traces.get(lane),primary.get(lane)).suppressed&&!after(h,traces.get(lane),near.get(lane)).suppressed,"callback-invalid effect has no lease");}
            System.out.println("SQUEAL_SUPPRESSION_RECEIPT_RESULT_RAW sameAccepted="+sameAccepted[0]+" differentAccepted="+differentAccepted[0]+" differentLoss="+differentLoss[0]+" sameHp="+primary.get(6).getHealth()+" differentHp="+primary.get(7).getHealth());
            h.assertTrue(!sameAccepted[0]&&differentAccepted[0],"same-DS recursion rejected while independent real generic hit remains legal");close(h,differentLoss[0],2,"separate callback damage genuinely wrote two HP");
            var separate=traces.get(7);var shot=skillShots(separate).get(0);h.assertTrue(shot.isRemoved()&&separate.travel>2&&separate.impacts.size()==1&&separate.snapshot!=null&&separate.snapshot.time==separate.impactAt,"independent-damage control still completed one genuine physical wave");
            close(h,after(h,separate,primary.get(7)).health,998,"outer zero cannot borrow independent callback HP loss");close(h,after(h,separate,near.get(7)).health,1000,"independent callback loss never authorizes splash");
            h.assertTrue(!after(h,separate,primary.get(7)).suppressed&&!after(h,separate,near.get(7)).suppressed&&separate.snapshot.incoming.stream().allMatch(read->read.actor==separate.source&&read.direct==shot||read.actor==null&&read.direct==null),"only the declared true generic nested damage exists, not optional suppression receipt");
            for(int lane=0;lane<8;lane++)h.assertTrue(cd(sources.get(lane))==(lane<2?0:330),"failure-before-publication vs paid-after-publication exact clock lane "+lane);
            h.assertTrue(PROBE.hurtOnce.isEmpty()&&PROBE.damageOnce.isEmpty()&&boundary.joins.isEmpty()&&boundary.constructing==null,"all planned real engine callbacks actually executed");bank(h);pass("ACTUAL_CONSTRUCTOR_JOIN_OWNER_EPOCH_REENTRY_EXACT_RECEIPTS");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=500)
    public static void realNormalCrossbowSlimeAndMilitiaConsumeOneSlowFactorWithoutExtendingCurrentWork(GameTestHelper h) {
        room(h);for(int z:List.of(8,16,24))partition(h,z);
        var normal=squeal(h,"CadenceActor0",10,4);normal.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).setBaseValue(1);
        var crossbow=com.solegendary.reignofnether.registrars.EntityRegistrar.PILLAGER_UNIT.get().create(h.getLevel());owned(h,crossbow,"CadenceActor1",10,12);crossbow.setItemSlot(EquipmentSlot.MAINHAND,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CROSSBOW));
        var slime=com.solegendary.reignofnether.registrars.EntityRegistrar.SLIME_UNIT.get().create(h.getLevel());slime.setSize(2,true);owned(h,slime,"CadenceActor2",10,20);slime.setNoAi(false);slime.setHoldPosition(true); // Core NoAI skips travel; real ground/jump phases need AI; the command-setup gap is isolated below.
        var militia=com.solegendary.reignofnether.registrars.EntityRegistrar.MILITIA_UNIT.get().create(h.getLevel());militia.swapWeapons(true);owned(h,militia,"CadenceActor3",10,28);
        List<Mob> actors=List.of(normal,crossbow,slime,militia);List<ShadowSquealGhostUnit> waves=new ArrayList<>();List<JellyfishUnit> victims=new ArrayList<>();List<GoldenFoxUnit> foxes=new ArrayList<>();List<Entity> bodies=new ArrayList<>(actors);
        UUID permanent=UUID.fromString("0c81eec5-d4e9-41f0-b5fd-1d6463db373a");
        for(int lane=0;lane<4;lane++){int z=4+lane*8;var wave=squeal(h,"CadenceWave"+lane,3.5,z);var victim=jelly(h,"CadenceWave"+lane,lane==2?25:17,z); /* Slime victim begins beyond native aggro until true grounding and incoming wave complete. */var fox=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());owned(h,fox,"CadenceActor"+lane,13,z+2);waves.add(wave);victims.add(victim);foxes.add(fox);bodies.addAll(List.of(wave,victim,fox));neutral(actors.get(lane));damage(actors.get(lane),1);actors.get(lane).getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(permanent,"declared permanent APS research",.1,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));}
        var cadence=new CadenceProbe(h);EXTRA_LISTENERS.add(cadence);MinecraftForge.EVENT_BUS.register(cadence);for(int lane=0;lane<4;lane++)cadence.actors.put(actors.get(lane).getUUID(),new CadenceActor(actors.get(lane),victims.get(lane),lane));
        long[] start={-1};boolean[] firstJump={false};
        var seq=ready(h,bodies.toArray(Entity[]::new)).thenWaitUntil(()->{if(h.getTick()<=10||h.getTick()%40==0||h.getTick()==499)slimeGroundRaw(h,slime);h.assertTrue(slime.onGround()&&slime.verticalCollision&&Math.abs(slime.getBoundingBox().minY-(h.absolutePos(BlockPos.ZERO).getY()+1))<.001,"actual slime grounded on fixture floor, never fake onGround");})
          .thenExecute(()->{
              for(int lane=0;lane<4;lane++){var actor=cadence.actors.get(actors.get(lane).getUUID());actor.base=attacker(actor.body).getAttackCooldown();actor.aps=actor.body.getAttributeValue(AttributeRegistrar.ATTACKS_PER_SECOND.get());actor.cached=actor.kind==1?intField(attacker(actor.body).getAttackGoal(),"attackCooldownMax"):actor.kind==2?slime.PUSH_ATTACK_CD_MAX:0;
                  actor.nativeWeak=new net.minecraft.world.effect.MobEffectInstance(MobEffectRegistrar.ATTACK_SLOWDOWN.get(),300,0);actor.body.addEffect(actor.nativeWeak);actor.nativeWeak=actor.body.getEffect(MobEffectRegistrar.ATTACK_SLOWDOWN.get());
                  // Declared controlled original-goal reset seeds running work; actual post-hit consumption below is proved by natural launches/charge or physical landing.
                  if(actor.kind==0)((UnitRangedAttackGoal<?>)attacker(actor.body).getAttackGoal()).setToMaxAttackCooldown();else if(actor.kind==3)((com.solegendary.reignofnether.unit.goals.UnitBowAttackGoal<?>)attacker(actor.body).getAttackGoal()).setToMaxAttackCooldown();
                  require(h,cast(h,waves.get(lane),actor.body),SkillResult.SUCCESS,"real physical suppression on actual cadence consumer "+lane);
              }
          }).thenWaitUntil(()->h.assertTrue(actors.stream().allMatch(SquealSuppressionWave::hasDebuff)&&cadence.actors.values().stream().allMatch(a->a.applicationChecked),"every actual wave hit completed and same-tick current-work boundary observed"))
          .thenExecute(()->{
              h.assertTrue(slime.onGround()&&slime.verticalCollision,"actual grounded physics completed before isolated command setup");slime.setNoAi(true); // Only this command-setup gap is isolated; start below restores real AI before native jump.
              var near=h.absolutePos(new BlockPos(11,1,20));victims.get(2).moveTo(near.getX()+.2,near.getY(),near.getZ()); // Original legal close target, only after real grounding/skill impact; no physics flag or velocity setter.
              for(int lane=0;lane<4;lane++){var actor=cadence.actors.get(actors.get(lane).getUUID());h.assertTrue(actor.body.getEffect(MobEffectRegistrar.ATTACK_SLOWDOWN.get())==actor.nativeWeak&&actor.nativeWeak.getAmplifier()==0,"skill never replaces/removes original native weak effect instance");
                  close(h,attacker(actor.body).getAttackCooldown(),actor.base*1.25,"weak native1.05 and managed1.25 take max once before positive buff");
                  require(h,otherSkill(h,foxes.get(lane),foxes.get(lane),UnitSkill.FOX_BLESSING),SkillResult.SUCCESS,"actual same-owner Fox cast, never injected APS provider");
                  close(h,SkillEffects.bonus(actor.body,BuffKind.ATTACK_SPEED),.2,"real positive APS contribution");close(h,attacker(actor.body).getAttackCooldown(),actor.base*1.25/1.2,"positive division and same-class slow maximum combine once, including Militia float return");
                  actorCommand(h,actor.body,UnitAction.ATTACK,actor.victim.getId(),BlockPos.ZERO);
              }
          }).thenWaitUntil(()->{for(var actor:cadence.actors.values())h.assertTrue(!UnitServerEvents.hasPendingImmediateAction(actor.body.getId())&&((Unit)actor.body).getTargetGoal().getTarget()==actor.victim,"actual cadence ATTACK dispatch selected exact original victim");})
          .thenExecute(()->{
              start[0]=h.getLevel().getGameTime();cadence.started=true;
              for(var actor:cadence.actors.values()){((Unit)actor.body).setHoldPosition(true);actor.body.setNoAi(false);}
              h.assertTrue(slime.onGround()&&slime.pushAttackCd==0,"first controlled real jump starts from actual floor with original ready cooldown");slime.jumpFromGround();firstJump[0]=true;
          });
        for(int tick=1;tick<=190;tick++){final int elapsed=tick;seq.thenIdle(1).thenExecute(()->{
            if(elapsed==20){for(var actor:cadence.actors.values()){h.assertTrue(SquealSuppressionWave.hasDebuff(actor.body),"stronger native comparison happens while exact managed lease is live");actor.body.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffectRegistrar.ATTACK_SLOWDOWN.get(),55,9));close(h,attacker(actor.body).getAttackCooldown(),actor.base*1.5/1.2,"actual native stronger1.5 wins, never1.5 times1.25");}}
            var record=cadence.actors.get(slime.getUUID());
            // Explicit controlled native collision path, NOT autonomous slime navigation APS.
            // Native jump supplies real velocity; world physics supplies actual airborne/landing state.
            if(firstJump[0]&&record.resets.stream().anyMatch(r->r.path.equals("landing"))&&slime.pushAttackCd<=0){
                if(slime.onGround())slime.jumpFromGround();
                else {h.assertTrue(slime.getBoundingBox().minY>h.absolutePos(BlockPos.ZERO).getY()+1,"true airborne native body before controlled push callback");slime.push(victims.get(2));}
            }
        });}
        seq.thenExecute(()->{
            for(var actor:cadence.actors.values()){
                h.assertTrue(actor.applicationChecked&&actor.appBefore.equals(actor.appAfter),"actual installation preserves current remaining/windup/crossbow charge: "+actor.kind+" "+actor.appBefore+" -> "+actor.appAfter);
                h.assertTrue(actor.resets.stream().anyMatch(r->r.slow)&&actor.resets.stream().anyMatch(r->!r.slow&&!r.positive),"actual native consumer resets both during managed slow and after all addon effects expire: "+actor.kind+" "+actor.resets);
                h.assertTrue(actor.resets.stream().anyMatch(r->r.slow&&Math.abs(r.nativeFactor-1.5f)<.0001f),"each real normal/cached consumer resets while stronger native1.5 and managed1.25 coexist, taking only max: "+actor.kind+" "+actor.resets);
                h.assertTrue(actor.hits>0&&actor.victim.getHealth()<1000,"actual emitted projectile/landing/push also damages original enemy, not merely a cooldown getter");
                if(actor.kind!=2)h.assertTrue(actor.shots>=2,"native natural goal emits multiple physical projectiles: "+actor.kind);
                h.assertTrue(!SquealSuppressionWave.hasDebuff(actor.body)&&!SkillEffects.hasNamedBuff(actor.body,SkillEffects.FOX_BLESSING_KEY,BuffKind.ATTACK_SPEED),"both real short skill lifetimes ended naturally");
                h.assertTrue(actor.body.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getModifier(permanent)!=null,"original permanent APS UUID remains installed");close(h,actor.body.getAttributeValue(AttributeRegistrar.ATTACKS_PER_SECOND.get()),actor.aps,"positive modifier expires without rewriting permanent APS");
                close(h,attacker(actor.body).getAttackCooldown(),actor.base*1.05,"normal getter restores existing still-live native weak slow path, not invented flat reset");
                if(actor.kind==1)h.assertTrue(intField(attacker(actor.body).getAttackGoal(),"attackCooldownMax")==actor.cached,"crossbow constructor cache never mutated");
                if(actor.kind==2)h.assertTrue(slime.PUSH_ATTACK_CD_MAX==actor.cached&&actor.resets.stream().anyMatch(r->r.path.equals("landing")&&r.slow&&Math.abs(r.nativeFactor-1.05f)<.0001f)&&actor.resets.stream().anyMatch(r->r.path.equals("push")&&r.slow&&Math.abs(r.nativeFactor-1.5f)<.0001f),"real weak-native landing consumes managed1.25, then actual-airborne controlled push consumes stronger-native1.5; original cached max unchanged");
                actor.body.setNoAi(true);System.out.println("SQUEAL_SUPPRESSION_REAL_CADENCE kind="+actor.kind+" source="+actor.body.getUUID()+" resets="+actor.resets);
            }
            h.assertTrue(cadence.actors.get(normal.getUUID()).appBefore.remaining>0,"normal hit installed during a genuinely running old countdown, not a zero-only negative");
            h.assertTrue(cadence.cachedExpiryPreserved>0,"observed real running cached cycle crosses managed expiry without forced reset/clamp");
            bank(h);pass("REAL_FOUR_CONSUMERS_MAX_POSITIVE_CURRENT_WORK_NATIVE_EXPIRY");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=400)
    public static void actualSporeBirthAndOverlappingAurasCleanOneManagedIdAndShortenOnlyNewSuppression(GameTestHelper h) {
        room(h);
        var first=squeal(h,"SporeEnemy",4.5,8.5);var incoming=squeal(h,"SporeEnemy",4.5,12.5);var duplicate=squeal(h,"SporeEnemy",4.5,10.5);var late=squeal(h,"SporeEnemy",4.5,6.5);
        var oldSource=squeal(h,"SporeEnemy",20.5,20.5);var victim=jelly(h,"SporeFriend",12.5,8.5);var old=jelly(h,"SporeFriend",27,20.5);
        List<GoldenFoxUnit> foxes=new ArrayList<>();for(var p:List.of(new Vec3(8,0,5),new Vec3(8,0,12),new Vec3(8,0,16),new Vec3(8,0,3))){var fox=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());owned(h,fox,"SporeFriend",p.x,p.z);foxes.add(fox);}
        var initial=watch(first,victim);var oldTrace=watch(oldSource,old);Trace[] fresh={null},repeated={null},last={null};
        var removal=new NativeRemovalProbe();EXTRA_LISTENERS.add(removal);MinecraftForge.EVENT_BUS.register(removal);
        var expiry=new SlowExpiryProbe(h);EXTRA_LISTENERS.add(expiry);MinecraftForge.EVENT_BUS.register(expiry);
        long[] seedAt={-1},freshDeadline={-1},oldDeadline={-1};float[] duplicateBefore={0};net.minecraft.world.effect.MobEffectInstance[] vanilla={null};
        var bodies=new ArrayList<Entity>(List.of(first,incoming,duplicate,late,oldSource,victim,old));bodies.addAll(foxes);
        ready(h,bodies.toArray(Entity[]::new)).thenExecute(()->{require(h,cast(h,first,victim),SkillResult.SUCCESS,"real initial managed suppression before any aura");require(h,cast(h,oldSource,old),SkillResult.SUCCESS,"independent old managed instance outside future aura");})
          .thenWaitUntil(()->h.assertTrue(initial.snapshot!=null&&oldTrace.snapshot!=null&&SquealSuppressionWave.hasDebuff(victim)&&SquealSuppressionWave.hasDebuff(old),"both real physical waves actually completed"))
          .thenExecute(()->{
              completed(h,initial,victim,false);completed(h,oldTrace,old,false);h.assertTrue(after(h,initial,victim).remaining==80&&after(h,oldTrace,old).remaining==80,"both first instances genuinely started at eighty");
              oldDeadline[0]=h.getLevel().getGameTime()+SquealSuppressionWave.remainingDebuffTicks(old);expiry.ends.add(new SlowEnd(old,oldDeadline[0]));
              h.assertTrue(victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,600)),"real vanilla negative coexists before activation");vanilla[0]=victim.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
              seedAt[0]=h.getLevel().getGameTime();require(h,seed(h,foxes.get(0),12,10),SkillResult.SUCCESS,"actual registered first seed birth");
              h.assertTrue(!SquealSuppressionWave.hasDebuff(victim)&&victim.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)==vanilla[0]&&removal.count(victim)==0&&com.dreamseekers.spore.SporeEffects.cleanseProtectionTicks(victim)==200,"full dreamseekers ID before minecraft removes exactly one managed effect; no fake native Remove, shared200 starts");
              require(h,seed(h,foxes.get(1),10,8),SkillResult.SUCCESS,"second genuine overlapping aura cannot cleanse protected vanilla");
              h.assertTrue(victim.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)==vanilla[0]&&com.dreamseekers.spore.SporeEffects.cleanseProtectionTicks(victim)==200,"cross-source protection blocks removal without refresh");
              var p=h.absolutePos(new BlockPos(12,1,11));old.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);old.setDeltaMovement(Vec3.ZERO);
              h.assertTrue(h.getLevel().getGameTime()+SquealSuppressionWave.remainingDebuffTicks(old)==oldDeadline[0]&&com.dreamseekers.spore.SporeEffects.cleanseProtectionTicks(old)==0,"already-live later entrant is neither retroactively shortened nor periodically cleansed");
          }).thenIdle(20).thenExecute(()->{
              h.assertTrue(victim.invulnerableTime==0,"actual initial-hit iframe naturally expired before new protected incoming wave");
              fresh[0]=watch(incoming,victim);require(h,cast(h,incoming,victim),SkillResult.SUCCESS,"new actual enemy wave while target still protected and inside two real auras");
          }).thenWaitUntil(()->h.assertTrue(fresh[0]!=null&&fresh[0].snapshot!=null,"new physical hit completed under actual aura"))
          .thenExecute(()->{
              completed(h,fresh[0],victim,false);h.assertTrue(after(h,fresh[0],victim).remaining==64&&com.dreamseekers.spore.SporeEffects.cleanseProtectionTicks(victim)>0,"new80 becomes64 exactly once across overlapping auras; cleanse protection is not negative immunity");
              freshDeadline[0]=fresh[0].snapshot.time+64;expiry.ends.add(new SlowEnd(victim,freshDeadline[0]));
          }).thenIdle(20).thenExecute(()->{
              h.assertTrue(victim.invulnerableTime==0&&SquealSuppressionWave.hasDebuff(victim),"real ordinary iframe expired while same named managed effect still valid");duplicateBefore[0]=victim.getHealth();repeated[0]=watch(duplicate,victim);require(h,cast(h,duplicate,victim),SkillResult.SUCCESS,"independent source spends own real wave against occupied same-name effect");
          }).thenWaitUntil(()->h.assertTrue(repeated[0]!=null&&repeated[0].snapshot!=null,"duplicate legitimate wave actually hit"))
          .thenExecute(()->{
              completed(h,repeated[0],victim,false);close(h,duplicateBefore[0]-after(h,repeated[0],victim).health,8,"same-name occupancy does not suppress legitimate new base hit");
              h.assertTrue(repeated[0].snapshot.time+after(h,repeated[0],victim).remaining==freshDeadline[0],"valid global same key never refreshes duration or reapplies80-percent duration factor");
          }).thenWaitUntil(()->h.assertTrue(h.getLevel().getGameTime()>=seedAt[0]+180,"natural absolute shared protection clock reaches late recast phase"))
          .thenExecute(()->{
              h.assertTrue(expiry.ends.stream().allMatch(e->e.sawLast&&e.sawDeadline&&e.lastPresent&&!e.deadlinePresent),"real Level END proves distinct original80 and one-time64 expiries");
              h.assertTrue(!SquealSuppressionWave.hasDebuff(victim)&&!SquealSuppressionWave.hasDebuff(old)&&com.dreamseekers.spore.SporeEffects.cleanseProtectionTicks(victim)==20,"both short instances expired but original protection has twenty ticks left");
              last[0]=watch(late,victim);require(h,cast(h,late,victim),SkillResult.SUCCESS,"another original unused real source launches while protected");
          }).thenWaitUntil(()->h.assertTrue(last[0]!=null&&last[0].snapshot!=null,"late actual wave completes before original protection boundary"))
          .thenExecute(()->{completed(h,last[0],victim,false);h.assertTrue(after(h,last[0],victim).remaining==64,"still-live old aura shortens newly installed late effect once");})
          .thenWaitUntil(()->h.assertTrue(h.getLevel().getGameTime()>=seedAt[0]+199,"natural protection last tick"))
          .thenExecute(()->{
              h.assertTrue(h.getLevel().getGameTime()==seedAt[0]+199&&com.dreamseekers.spore.SporeEffects.cleanseProtectionTicks(victim)==1&&SquealSuppressionWave.hasDebuff(victim),"exact199 boundary has live new negative and one protection tick");
              require(h,seed(h,foxes.get(2),14,10),SkillResult.SUCCESS,"third real seed at199");h.assertTrue(SquealSuppressionWave.hasDebuff(victim)&&victim.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)==vanilla[0],"protected activation removes neither managed nor vanilla");
          }).thenIdle(1).thenExecute(()->{
              h.assertTrue(com.dreamseekers.spore.SporeEffects.cleanseProtectionTicks(victim)==0,"original protection expires exactly200");require(h,seed(h,foxes.get(3),12,6),SkillResult.SUCCESS,"fourth true seed after exact protection deadline");
              h.assertTrue(!SquealSuppressionWave.hasDebuff(victim)&&victim.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN)==vanilla[0]&&removal.count(victim)==0&&com.dreamseekers.spore.SporeEffects.cleanseProtectionTicks(victim)==200,"fresh unified choice again clears only new managed wave, leaves same native instance and starts200");
              bank(h);pass("REAL_SPORE_ONE_ID_200_OVERLAP_NEW64_OLD80_SAMEKEY");
          }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=240)
    public static void actualDeathOwnerEpochAndLocalNativeReloadDropOnlyShortSuppressionWhileKeepingPaidClockAndPermanentStats(GameTestHelper h) {
        room(h);var dead=squeal(h,"Life",4.5,6);var changed=squeal(h,"Life",4.5,12);var old=squeal(h,"Life",4.5,20);var saved=squeal(h,"Life",6.5,23.5);
        var a=jelly(h,"LifeEnemy",12.5,6);var b=jelly(h,"LifeEnemy",12.5,12);var victim=jelly(h,"LifeEnemy",12.5,20);var reserve=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(h.getLevel());owned(h,reserve,"LifeEnemy",25.5,5.5);
        UUID permanent=UUID.fromString("8eeff480-83d0-42f8-b5f5-2d49e5d65670");victim.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(permanent,"independent permanent APS research",.1,net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADDITION));double aps=victim.getAttributeValue(AttributeRegistrar.ATTACKS_PER_SECOND.get());
        victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffectRegistrar.ATTACK_SLOWDOWN.get(),300,1));
        var traces=List.of(watch(dead,a),watch(changed,b),watch(old,victim));Trace[] replacement={null};long[] at={-1},saveCast={-1};UUID[] ids=new UUID[2];int[] savedNative={0};float[] savedHp={0};
        ShadowSquealGhostUnit[] loadedSource={null};JellyfishUnit[] loadedTarget={null};
        ready(h,dead,changed,old,saved,a,b,victim,reserve).thenExecute(()->{at[0]=h.getLevel().getGameTime();require(h,cast(h,dead,a),SkillResult.SUCCESS,"actual wave before real source death");require(h,cast(h,changed,b),SkillResult.SUCCESS,"actual wave before source epoch change");require(h,cast(h,old,victim),SkillResult.SUCCESS,"actual wave before target epoch change");})
          .thenWaitUntil(()->h.assertTrue(traces.stream().allMatch(t->t.snapshot!=null)&&SquealSuppressionWave.hasDebuff(a)&&SquealSuppressionWave.hasDebuff(b)&&SquealSuppressionWave.hasDebuff(victim),"all actual physical hits installed live short state"))
          .thenExecute(()->{
              for(int i=0;i<3;i++)completed(h,traces.get(i),List.of(a,b,victim).get(i),false);
              h.assertTrue(dead.hurt(dead.damageSources().genericKill(),10000)&&!dead.isAlive(),"actual fatal health path, not synthetic removal flag");h.assertTrue(!SquealSuppressionWave.hasDebuff(a)&&cd(dead)==360-(h.getLevel().getGameTime()-at[0]),"source death clears only short effect, never pays cooldown back");
              String owner=changed.getOwnerName();changed.setOwnerName(owner+"Temporary");changed.setOwnerName(owner);h.assertTrue(!SquealSuppressionWave.hasDebuff(b)&&cd(changed)==360-(h.getLevel().getGameTime()-at[0]),"source owner ABA revokes installed lease without refund");
              String enemy=victim.getOwnerName();victim.setOwnerName(enemy+"Temporary");victim.setOwnerName(enemy);h.assertTrue(!SquealSuppressionWave.hasDebuff(victim)&&cd(old)==360-(h.getLevel().getGameTime()-at[0]),"target ABA clears old source lease without changing source paid clock");
              close(h,victim.getAttributeValue(AttributeRegistrar.ATTACKS_PER_SECOND.get()),aps,"managed suppression never writes original/permanent APS attribute");
          }).thenIdle(20).thenExecute(()->{
              h.assertTrue(victim.invulnerableTime==0,"natural prior-hit iframe expired before replacement positive");saveCast[0]=h.getLevel().getGameTime();replacement[0]=watch(saved,victim);require(h,cast(h,saved,victim),SkillResult.SUCCESS,"fresh original source captures actual current target epoch");
          }).thenWaitUntil(()->h.assertTrue(replacement[0]!=null&&replacement[0].snapshot!=null&&SquealSuppressionWave.hasDebuff(victim),"genuine new hit/short effect exists at local native save boundary"))
          .thenExecute(()->{
              completed(h,replacement[0],victim,false);var sourceNbt=saved.saveWithoutId(new net.minecraft.nbt.CompoundTag());var targetNbt=victim.saveWithoutId(new net.minecraft.nbt.CompoundTag());ids[0]=saved.getUUID();ids[1]=victim.getUUID();savedNative[0]=victim.getEffect(MobEffectRegistrar.ATTACK_SLOWDOWN.get()).getDuration();savedHp[0]=victim.getHealth();
              h.assertTrue(sourceNbt.getCompound("DreamseekersUnitSkills").getLong("SQUEAL_SUPPRESSION_WAVE")==saveCast[0]+360&&serializedModifiers(targetNbt,permanent)==1,"real native NBT saves original absolute paid deadline and one untouched permanent UUID");
              saved.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);h.assertTrue(!SquealSuppressionWave.hasDebuff(victim),"real source unload removes current dependent lease immediately");victim.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
              loadedSource[0]=DreamseekersMod.SHADOW_SQUEAL_GHOST_UNIT.get().create(h.getLevel());loadedSource[0].load(sourceNbt);loadedTarget[0]=DreamseekersMod.JELLYFISH_UNIT.get().create(h.getLevel());loadedTarget[0].load(targetNbt);
              h.assertTrue(!loadedSource[0].isNoAi()&&!loadedTarget[0].isNoAi()&&loadedSource[0].isNoGravity()&&loadedTarget[0].isNoGravity(),"observe actual native paid load policy before fixture AI isolation, never expect saved NoAI true");
              h.assertTrue(!SquealSuppressionWave.hasDebuff(loadedTarget[0])&&loadedTarget[0].getHealth()==savedHp[0]&&loadedTarget[0].getEffect(MobEffectRegistrar.ATTACK_SLOWDOWN.get()).getDuration()==savedNative[0],"native load drops managed short lease but retains real HP and existing vanilla negative, no refresh");
              // Explicit same-process NBT coverage; separate two-JVM tests own real disk/restart claims.
              for(Mob body:List.of(loadedSource[0],loadedTarget[0])){body.setNoAi(true);ENTITIES.add(body);h.assertTrue(h.getLevel().addFreshEntity(body),"real loaded original-UUID object republishes through ordinary server lifecycle");}
          }).thenWaitUntil(()->{for(Entity body:List.of(loadedSource[0],loadedTarget[0]))h.assertTrue(body.tickCount>3&&h.getLevel().getEntity(body.getId())==body&&h.getLevel().getEntity(body.getUUID())==body,"naturally ticked, actually ID/UUID-published local loaded originals");})
          .thenExecute(()->{
              h.assertTrue(loadedSource[0].getUUID().equals(ids[0])&&loadedTarget[0].getUUID().equals(ids[1])&&cd(loadedSource[0])==360-(h.getLevel().getGameTime()-saveCast[0]),"native identity and absolute original paid clock preserved");require(h,cast(h,loadedSource[0],loadedTarget[0]),SkillResult.COOLDOWN,"native load cannot mint fresh unearned wave");
              h.assertTrue(!SquealSuppressionWave.hasDebuff(loadedTarget[0])&&loadedTarget[0].getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getModifier(permanent)!=null,"no short replay, no deletion of unrelated permanent stat");close(h,loadedTarget[0].getAttributeValue(AttributeRegistrar.ATTACKS_PER_SECOND.get()),aps,"same permanent/base value after actual native load");
              var nativeEffect=loadedTarget[0].getEffect(MobEffectRegistrar.ATTACK_SLOWDOWN.get());h.assertTrue(nativeEffect!=null&&nativeEffect.getAmplifier()==1&&nativeEffect.getDuration()>0&&nativeEffect.getDuration()<=savedNative[0],"unrelated original native effect ages naturally, neither erased nor refreshed");
              h.assertTrue(reserve.isAlive()&&!reserve.isRemoved()&&h.getLevel().getEntity(reserve.getUUID())==reserve&&reserve.isNoAi()&&!SquealSuppressionWave.hasDebuff(reserve)&&reserve.rts().skills().cooldownTicks(UnitSkill.FOX_BLESSING)==0&&reserve.rts().skills().cooldownTicks(UnitSkill.FOX_DREAM_SEED)==0,"real same-owner reserve survives unload gap, stays unused, prevents synthetic defeat rather than recreating bank");
              bank(h);pass("REAL_DEATH_SOURCE_TARGET_EPOCH_LOCAL_NBT_PAID_CD_PERMANENT");
          }).thenSucceed();
    }

    private static SkillResult seed(GameTestHelper h,GoldenFoxUnit source,int x,int z){return new SkillNetwork.GroundCast(new SkillGroundRequest(UnitSkill.FOX_DREAM_SEED,source.getId(),source.getUUID(),source.rts().skills().revision(),h.absolutePos(new BlockPos(x,1,z)))).execute(player(h,source.getOwnerName()));}
    private static final class NativeRemovalProbe {
        final Map<UUID,Integer> counts=new HashMap<>();int count(LivingEntity body){return counts.getOrDefault(body.getUUID(),0);}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void removed(MobEffectEvent.Remove event){counts.merge(event.getEntity().getUUID(),1,Integer::sum);}
    }
    private static final class SlowEnd {
        final LivingEntity body;final long deadline;boolean sawLast,sawDeadline,lastPresent,deadlinePresent;
        SlowEnd(LivingEntity body,long deadline){this.body=body;this.deadline=deadline;}
    }
    private static final class SlowExpiryProbe {
        final GameTestHelper h;final List<SlowEnd> ends=new ArrayList<>();SlowExpiryProbe(GameTestHelper h){this.h=h;}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void end(TickEvent.LevelTickEvent event){if(event.level!=h.getLevel()||event.phase!=TickEvent.Phase.END)return;long now=event.level.getGameTime();for(var end:ends){if(now==end.deadline-1){end.sawLast=true;end.lastPresent=SquealSuppressionWave.hasDebuff(end.body);}if(now==end.deadline){end.sawDeadline=true;end.deadlinePresent=SquealSuppressionWave.hasDebuff(end.body);}}}
    }
    private static int serializedModifiers(net.minecraft.nbt.CompoundTag nbt,UUID uuid){int count=0;var attributes=nbt.getList("Attributes",10);for(int i=0;i<attributes.size();i++){var modifiers=attributes.getCompound(i).getList("Modifiers",10);for(int j=0;j<modifiers.size();j++){var modifier=modifiers.getCompound(j);if(modifier.hasUUID("UUID")&&modifier.getUUID("UUID").equals(uuid))count++;}}return count;}

    private static float nativeLastHurt(LivingEntity target){try{var field=LivingEntity.class.getDeclaredField("lastHurt");field.setAccessible(true);return field.getFloat(target);}catch(ReflectiveOperationException failure){throw new RuntimeException("raw native lastHurt",failure);}}
    /** Read-only failure observation, no timer/health/physics write and no extra hurt. */
    private static void receiptBoundaryRaw(GameTestHelper h,String phase,LivingEntity target,net.minecraft.world.damagesource.DamageSource outer,Boolean accepted){
        Object last;try{var field=LivingEntity.class.getDeclaredField("lastHurt");field.setAccessible(true);last=field.get(target);}catch(ReflectiveOperationException failure){throw new RuntimeException("raw native lastHurt",failure);}
        System.out.println("SQUEAL_SUPPRESSION_RECEIPT_BOUNDARY_RAW phase="+phase+" tick="+h.getTick()+" gameTime="+h.getLevel().getGameTime()+" target="+target.getUUID()+" hp="+target.getHealth()+" invulnerableTime="+target.invulnerableTime+" lastHurt="+last+" accepted="+accepted+" outerType="+outer.getMsgId()+" outerIdentity="+System.identityHashCode(outer));
    }
    /** Only an already-loaded chunk is read; diagnostic cannot load/create terrain or run pathfinding. */
    private static void slimeGroundRaw(GameTestHelper h,com.solegendary.reignofnether.unit.units.monsters.SlimeUnit slime){
        var level=h.getLevel();var box=slime.getBoundingBox();var floor=BlockPos.containing(slime.getX(),box.minY-.01,slime.getZ());var chunk=level.getChunkSource().getChunkNow(floor.getX()>>4,floor.getZ()>>4);
        System.out.println("SQUEAL_SUPPRESSION_SLIME_GROUND_RAW tick="+h.getTick()+" gameTime="+level.getGameTime()+" uuid="+slime.getUUID()+" entityTick="+slime.tickCount+" publishedId="+(level.getEntity(slime.getId())==slime)+" publishedUuid="+(level.getEntity(slime.getUUID())==slime)+" alive="+slime.isAlive()+" removed="+slime.isRemoved()+" onGround="+slime.onGround()+" verticalCollision="+slime.verticalCollision+" pos="+slime.position()+" bboxMinY="+box.minY+" floorExpected="+(h.absolutePos(BlockPos.ZERO).getY()+1)+" velocity="+slime.getDeltaMovement()+" noAI="+slime.isNoAi()+" noGravity="+slime.isNoGravity()+" floorPos="+floor+" floor="+(chunk==null?"unloaded":chunk.getBlockState(floor).toString()));
    }

    private static final List<Object> EXTRA_LISTENERS=new ArrayList<>();
    private static final class WaveBoundaryProbe {
        Consumer<net.minecraftforge.event.entity.EntityEvent.EntityConstructing> constructing;
        final List<Entity> constructed=new ArrayList<>();final Map<UUID,Consumer<EntityJoinLevelEvent>> joins=new HashMap<>();Entity canceled;
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void construct(net.minecraftforge.event.entity.EntityEvent.EntityConstructing event){if(!(event.getEntity() instanceof SuppressionWaveProjectile)||constructing==null)return;constructed.add(event.getEntity());var callback=constructing;constructing=null;callback.accept(event);}
        @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true) public void join(EntityJoinLevelEvent event){if(!(event.getEntity() instanceof SuppressionWaveProjectile shot)||shot.getOwner()==null)return;var callback=joins.remove(shot.getOwner().getUUID());if(callback!=null){callback.accept(event);if(event.isCanceled())canceled=shot;}}
    }
    private static void replacement(GameTestHelper h,ShadowSquealGhostUnit source,UnitAction action,int target,BlockPos position){actorCommand(h,source,action,target,position);}
    private static void actorCommand(GameTestHelper h,Mob source,UnitAction action,int target,BlockPos position){
        try{var sender=player(h,((Unit)source).getOwnerName());var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);connection.setListener(sender.connection);var ctor=net.minecraftforge.network.NetworkEvent.Context.class.getDeclaredConstructor(net.minecraft.network.Connection.class,net.minecraftforge.network.NetworkDirection.class,int.class);ctor.setAccessible(true);var context=ctor.newInstance(connection,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER,-1);
            h.assertTrue(context.getSender()==sender,"genuine owner command context");h.assertTrue(new com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket(((Unit)source).getOwnerName(),action,target,new int[]{source.getId()},position,BlockPos.ZERO,false).handle(()->context),"real authenticated native command accepted");
        }catch(ReflectiveOperationException failure){throw new RuntimeException(failure);}
    }
    private static com.solegendary.reignofnether.unit.interfaces.AttackerUnit attacker(Mob body){return (com.solegendary.reignofnether.unit.interfaces.AttackerUnit)body;}
    private static Object rawField(Object object,String name){try{var field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}catch(ReflectiveOperationException failure){throw new RuntimeException("raw observation "+name,failure);}}
    private static int intField(Object object,String name){return ((Number)rawField(object,name)).intValue();}
    private record WorkRead(float remaining,int windup,int useTicks,String state){}
    private record ResetRead(long time,String path,float remaining,int expected,boolean slow,boolean positive,float nativeFactor){}
    private static final class CadenceActor {
        final Mob body;final LivingEntity victim;final int kind;final List<ResetRead> resets=new ArrayList<>();
        float base;double aps;int cached,shots,hits,pendingShots;String pendingSlime,lastCrossbow="";float previousRemaining;boolean previousSlow;long expiresAt;
        net.minecraft.world.effect.MobEffectInstance nativeWeak;WorkRead appBefore,appAfter;boolean applicationChecked;
        CadenceActor(Mob body,LivingEntity victim,int kind){this.body=body;this.victim=victim;this.kind=kind;}
    }
    private static float remaining(CadenceActor actor){var goal=attacker(actor.body).getAttackGoal();return switch(actor.kind){case 0->((UnitRangedAttackGoal<?>)goal).getAttackCooldown();case 1->((com.solegendary.reignofnether.unit.goals.UnitCrossbowAttackGoal<?>)goal).getAttackCooldown();case 2->((com.solegendary.reignofnether.unit.units.monsters.SlimeUnit)actor.body).pushAttackCd;case 3->((com.solegendary.reignofnether.unit.goals.UnitBowAttackGoal<?>)goal).getAttackCooldown();default->throw new AssertionError();};}
    private static WorkRead work(CadenceActor actor){var goal=attacker(actor.body).getAttackGoal();int windup=actor.kind==0?intField(goal,"attackWindupTicksLeft"):actor.kind==1?intField(goal,"windupTime"):actor.kind==3?intField(goal,"attackWindupTime"):0;String state=actor.kind==1?rawField(goal,"crossbowState").toString():"none";return new WorkRead(remaining(actor),windup,actor.body.getTicksUsingItem(),state);}
    private static float nativeSlow(Mob actor){var effect=actor.getEffect(MobEffectRegistrar.ATTACK_SLOWDOWN.get());return effect==null?1:1+.05f*(effect.getAmplifier()+1);}
    private static int expectedReset(CadenceActor actor){boolean slow=SquealSuppressionWave.hasDebuff(actor.body);double bonus=SkillEffects.bonus(actor.body,BuffKind.ATTACK_SPEED);double factor=slow?Math.max(1.25,nativeSlow(actor.body)):(actor.kind==1||actor.kind==2?1:nativeSlow(actor.body));return Math.max(1,(int)Math.floor((actor.kind==1||actor.kind==2?actor.cached:actor.base)*factor/(1+bonus)));}
    private static final class CadenceProbe {
        final GameTestHelper h;final Map<UUID,CadenceActor> actors=new LinkedHashMap<>();boolean started;int cachedExpiryPreserved;
        CadenceProbe(GameTestHelper h){this.h=h;}
        @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true) public void damage(LivingDamageEvent event){
            var victim=actors.get(event.getEntity().getUUID());if(victim!=null&&event.getSource().getDirectEntity() instanceof SuppressionWaveProjectile&&victim.appBefore==null)victim.appBefore=work(victim);
            var source=event.getSource().getEntity();if(!started||source==null)return;var actor=actors.get(source.getUUID());if(actor==null||actor.victim!=event.getEntity())return;actor.hits++;
            if(actor.kind==2){var goal=(com.solegendary.reignofnether.unit.goals.MeleeAttackSlimeUnitGoal)attacker(actor.body).getAttackGoal();h.assertTrue(actor.pendingSlime==null,"one actual slime attack per Level END snapshot");actor.pendingSlime=goal.landedJump?"landing":"push";}
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void join(EntityJoinLevelEvent event){if(!started||!(event.getEntity() instanceof Projectile shot)||shot.getOwner()==null)return;var actor=actors.get(shot.getOwner().getUUID());if(actor==null)return;actor.shots++;if(actor.kind==0||actor.kind==3)actor.pendingShots++;}
        private void reset(CadenceActor actor,String path,int afterTick){int expected=expectedReset(actor);float actual=remaining(actor);h.assertTrue(actual==Math.max(0,expected-afterTick),"real "+path+" reset consumes baseline/positive/max once: kind="+actor.kind+" expected="+expected+" afterTick="+afterTick+" actual="+actual);actor.resets.add(new ResetRead(h.getLevel().getGameTime(),path,actual,expected,SquealSuppressionWave.hasDebuff(actor.body),SkillEffects.bonus(actor.body,BuffKind.ATTACK_SPEED)>0,nativeSlow(actor.body)));}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void end(TickEvent.LevelTickEvent event){if(event.phase!=TickEvent.Phase.END||event.level!=h.getLevel())return;
            for(var actor:actors.values()){
                if(actor.appBefore!=null&&!actor.applicationChecked&&SquealSuppressionWave.hasDebuff(actor.body)){actor.appAfter=work(actor);h.assertTrue(actor.appBefore.equals(actor.appAfter),"actual damage return installs suppression without changing old remaining/windup/use state: kind="+actor.kind+" before="+actor.appBefore+" after="+actor.appAfter);actor.applicationChecked=true;actor.expiresAt=event.level.getGameTime()+SquealSuppressionWave.remainingDebuffTicks(actor.body);}
                String state=actor.kind==1?rawField(attacker(actor.body).getAttackGoal(),"crossbowState").toString():"none";
                boolean reset=false;
                if(started){
                    if(actor.pendingShots>0){h.assertTrue(actor.pendingShots==1,"one native launch callback per actor per tick");reset(actor,actor.kind==0?"normal-projectile":"militia-bow-projectile",1);actor.pendingShots=0;reset=true;}
                    if(actor.kind==1&&state.equals("CHARGED")&&!actor.lastCrossbow.equals("CHARGED")){reset(actor,"actual-charge-completion",0);reset=true;}
                    if(actor.pendingSlime!=null){reset(actor,actor.pendingSlime,1);actor.pendingSlime=null;reset=true;}
                    boolean slow=SquealSuppressionWave.hasDebuff(actor.body);
                    if(actor.previousSlow&&!slow&&!reset&&actor.previousRemaining>0&&(actor.kind==2||actor.kind==1&&state.equals("CHARGED")&&actor.lastCrossbow.equals("CHARGED"))){h.assertTrue(remaining(actor)==Math.max(0,actor.previousRemaining-1),"cached in-progress interval naturally decrements across expiry, never rewritten by new skill");cachedExpiryPreserved++;}
                    if(actor.kind==0||actor.kind==3)h.assertTrue(remaining(actor)<=expectedReset(actor),"normal original tick clamp may shorten to current native interval, never extended on expiry");
                    actor.previousSlow=slow;actor.previousRemaining=remaining(actor);
                }
                actor.lastCrossbow=state;
            }
        }
    }

    private record Incoming(String phase,UUID victim,Entity actor,Entity direct,float eventAmount){}
    private record DamageRead(UUID target,float eventAmount,Projectile projectile,boolean published){}
    private record BodyRead(float health,float gel,float absorption,boolean suppressed,int remaining){}
    private record Snapshot(long time,UUID projectile,Map<UUID,BodyRead> bodies,List<Incoming> incoming){}
    private record ImpactRead(HitResult.Type kind,UUID victim,Vec3 point,List<UUID> ordered,Map<UUID,Double> distances,double friendlyDistance,double outsideDistance){}
    private static final class Trace {
        final ShadowSquealGhostUnit source;final Map<UUID,LivingEntity> observed=new LinkedHashMap<>();
        final List<Projectile> shots=new ArrayList<>();final Map<Projectile,Long> joinTimes=new IdentityHashMap<>();final Map<Projectile,Vec3> starts=new IdentityHashMap<>();
        final List<Incoming> incoming=new ArrayList<>();final List<DamageRead> damage=new ArrayList<>();final List<ImpactRead> impacts=new ArrayList<>();
        List<? extends LivingEntity> candidates=List.of();LivingEntity friendly,outside;boolean ordinary;double travel;long impactAt=Long.MIN_VALUE;Snapshot snapshot;
        Trace(ShadowSquealGhostUnit source,LivingEntity...targets){this.source=source;for(var target:targets)observed.put(target.getUUID(),target);}
    }
    private static Trace watch(ShadowSquealGhostUnit source,LivingEntity...targets){if(!listening){MinecraftForge.EVENT_BUS.register(PROBE);listening=true;}var trace=new Trace(source,targets);if(PROBE.traces.put(source.getUUID(),trace)!=null)throw new IllegalStateException("duplicate source observer");return trace;}
    private static List<SuppressionWaveProjectile> skillShots(Trace trace){return trace.shots.stream().filter(s->s.getClass()==SuppressionWaveProjectile.class).map(SuppressionWaveProjectile.class::cast).toList();}
    private static List<Projectile> measuredShots(Trace trace){return trace.shots.stream().filter(s->s.getClass()==(trace.ordinary?SquealWaveBolt.class:SuppressionWaveProjectile.class)).toList();}
    private static void observe(Trace trace){for(var shot:measuredShots(trace))trace.travel=Math.max(trace.travel,shot.position().distanceTo(trace.starts.get(shot)));}
    private static void immediate(GameTestHelper h,Trace trace,long time){var shots=skillShots(trace);h.assertTrue(shots.size()==1&&trace.shots.size()==1,"one actual independent skill projectile, no ordinary factory substitute");var shot=shots.get(0);h.assertTrue(trace.joinTimes.get(shot)==time&&h.getLevel().getEntity(shot.getId())==shot&&h.getLevel().getEntity(shot.getUUID())==shot&&shot.getOwner()==trace.source&&!shot.isRemoved()&&!shot.noPhysics,"real same-gameTime publication and owner, physical collision enabled");}
    private static BodyRead after(GameTestHelper h,Trace trace,LivingEntity target){h.assertTrue(trace.snapshot!=null&&trace.snapshot.bodies.containsKey(target.getUUID()),"first completed-shot Level END captures actual victim state");return trace.snapshot.bodies.get(target.getUUID());}
    private static void completed(GameTestHelper h,Trace trace,LivingEntity primary,boolean ordinary){
        observe(trace);var shots=measuredShots(trace);h.assertTrue(trace.ordinary==ordinary&&shots.size()==1&&trace.shots.size()==1,"one correctly typed physical factory shot, no hidden ordinary/skill contamination: "+trace.shots.stream().map(s->s.getClass().getSimpleName()+":"+s.getUUID()).toList());
        var shot=shots.get(0);h.assertTrue(shot.isRemoved()&&trace.travel>2&&trace.impacts.size()==1,"real multi-tick travel and one physical impact consume projectile");
        var impact=trace.impacts.get(0);h.assertTrue(primary==null?impact.kind==HitResult.Type.BLOCK:impact.kind==HitResult.Type.ENTITY&&primary.getUUID().equals(impact.victim),"actual collision identity/type, not manufactured hurt");
        var snapshot=trace.snapshot;h.assertTrue(snapshot!=null&&snapshot.projectile.equals(shot.getUUID())&&snapshot.time==trace.impactAt&&snapshot.time>=trace.joinTimes.get(shot)&&snapshot.time<=h.getLevel().getGameTime(),"actual getHealth/real Gel sampled first same-impact Level END, not Forge event amount");
        h.assertTrue(snapshot.incoming.stream().allMatch(read->read.actor==trace.source&&read.direct==shot),"ALL incoming Attack and Damage provenance up to snapshot belongs to exact shot; otherwise HP attribution fails: "+snapshot.incoming);
        if(!ordinary)h.assertTrue(trace.damage.stream().allMatch(read->read.projectile==shot&&read.published),"same real skill projectile remains published across all actual final callbacks");
        for(var target:trace.observed.values())h.assertTrue(!target.hasEffect(MobEffectRegistrar.STUN.get()),"suppression never grants native stun to any observed body");
        System.out.println("SQUEAL_SUPPRESSION_COMPLETED source="+trace.source.getUUID()+" shot="+shot.getUUID()+" impact="+impact+" snapshot="+snapshot);
    }
    private static final class Probe {
        final Map<UUID,Trace> traces=new HashMap<>();final Map<UUID,Consumer<LivingHurtEvent>> hurtOnce=new HashMap<>();final Map<UUID,Consumer<LivingDamageEvent>> damageOnce=new HashMap<>();
        @SubscribeEvent(priority=EventPriority.LOWEST) public void join(EntityJoinLevelEvent event){
            if(event.getLevel().isClientSide()||!(event.getEntity() instanceof Projectile shot)||shot.getOwner()==null)return;var trace=traces.get(shot.getOwner().getUUID());if(trace==null)return;
            trace.shots.add(shot);trace.joinTimes.put(shot,event.getLevel().getGameTime());trace.starts.put(shot,shot.position());
            System.out.println("SQUEAL_SUPPRESSION_REAL_JOIN time="+event.getLevel().getGameTime()+" source="+trace.source.getUUID()+" projectile="+shot.getUUID()+" type="+EntityType.getKey(shot.getType())+" pos="+shot.position());
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void impact(ProjectileImpactEvent event){
            var shot=event.getProjectile();if(shot.getOwner()==null)return;var trace=traces.get(shot.getOwner().getUUID());if(trace==null||!measuredShots(trace).contains(shot))return;
            var hit=event.getRayTraceResult();var point=hit.getLocation();Map<UUID,Double> distances=new LinkedHashMap<>();for(var target:trace.candidates)distances.put(target.getUUID(),target.getBoundingBox().getCenter().distanceToSqr(point));
            var sorted=trace.candidates.stream().sorted(Comparator.comparingDouble((LivingEntity e)->e.getBoundingBox().getCenter().distanceToSqr(point)).thenComparing(e->e.getUUID().toString())).map(Entity::getUUID).toList();
            trace.impacts.add(new ImpactRead(hit.getType(),hit instanceof EntityHitResult e?e.getEntity().getUUID():null,point,sorted,distances,trace.friendly==null?Double.NaN:trace.friendly.getBoundingBox().getCenter().distanceToSqr(point),trace.outside==null?Double.NaN:trace.outside.getBoundingBox().getCenter().distanceToSqr(point)));
            trace.impactAt=shot.level().getGameTime();trace.travel=Math.max(trace.travel,point.distanceTo(trace.starts.get(shot)));
        }
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void attack(LivingAttackEvent event){for(var trace:traces.values())if(trace.observed.get(event.getEntity().getUUID())==event.getEntity())trace.incoming.add(new Incoming("Attack",event.getEntity().getUUID(),event.getSource().getEntity(),event.getSource().getDirectEntity(),event.getAmount()));}
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void hurt(LivingHurtEvent event){if(event.getSource().getDirectEntity() instanceof SuppressionWaveProjectile){var callback=hurtOnce.remove(event.getEntity().getUUID());if(callback!=null)callback.accept(event);}}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void damage(LivingDamageEvent event){
            if(event.getSource().getDirectEntity() instanceof SuppressionWaveProjectile){var callback=damageOnce.remove(event.getEntity().getUUID());if(callback!=null)callback.accept(event);}
            // Pre-health-write event amounts are provenance diagnostics only, never the HP expectation.
            for(var trace:traces.values())if(trace.observed.get(event.getEntity().getUUID())==event.getEntity())trace.incoming.add(new Incoming("Damage",event.getEntity().getUUID(),event.getSource().getEntity(),event.getSource().getDirectEntity(),event.getAmount()));
            if(!(event.getSource().getDirectEntity() instanceof Projectile shot)||event.getSource().getEntity()==null)return;var trace=traces.get(event.getSource().getEntity().getUUID());if(trace==null||!measuredShots(trace).contains(shot))return;
            var level=(ServerLevel)event.getEntity().level();trace.damage.add(new DamageRead(event.getEntity().getUUID(),event.getAmount(),shot,!shot.isRemoved()&&level.getEntity(shot.getId())==shot&&level.getEntity(shot.getUUID())==shot));
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void end(TickEvent.LevelTickEvent event){
            if(event.phase!=TickEvent.Phase.END||event.level.isClientSide())return;
            for(var trace:traces.values()){
                if(trace.source.level()!=event.level||trace.snapshot!=null||trace.impacts.isEmpty())continue;var shots=measuredShots(trace);if(shots.size()!=1||!shots.get(0).isRemoved())continue;
                Map<UUID,BodyRead> states=new LinkedHashMap<>();for(var target:trace.observed.values())states.put(target.getUUID(),new BodyRead(target.getHealth(),SkillEffects.shieldRemaining(target),target.getAbsorptionAmount(),SquealSuppressionWave.hasDebuff(target),SquealSuppressionWave.remainingDebuffTicks(target)));
                trace.snapshot=new Snapshot(event.level.getGameTime(),shots.get(0).getUUID(),Map.copyOf(states),List.copyOf(trace.incoming));
                System.out.println("SQUEAL_SUPPRESSION_END_ACTUAL_STATE source="+trace.source.getUUID()+" snapshot="+trace.snapshot);
            }
        }
    }

    private static ShadowSquealGhostUnit squeal(GameTestHelper h,String owner,double x,double z) {var e=DreamseekersMod.SHADOW_SQUEAL_GHOST_UNIT.get().create(h.getLevel());owned(h,e,owner,x,z);damage(e,10);neutral(e);return e;}
    private static JellyfishUnit jelly(GameTestHelper h,String owner,double x,double z) {var e=DreamseekersMod.JELLYFISH_UNIT.get().create(h.getLevel());owned(h,e,owner,x,z);neutral(e);e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);e.setHealth(1000);return e;}
    private static void owned(GameTestHelper h,Mob e,String suffix,double x,double z) {
        h.assertTrue(e instanceof Unit,"registered genuine RTS fixture");((Unit)e).setOwnerName(account(suffix));e.setNoAi(true);e.setPersistenceRequired();
        var aggro=e.getAttribute(AttributeRegistrar.AGGRO_RANGE.get());if(aggro!=null)aggro.setBaseValue(0);place(h,e,x,z);player(h,suffix);
    }
    private static void place(GameTestHelper h,Entity entity,double x,double z) {var origin=h.absolutePos(BlockPos.ZERO);entity.moveTo(origin.getX()+x,origin.getY()+1,origin.getZ()+z,0,0);entity.setDeltaMovement(Vec3.ZERO);ENTITIES.add(entity);h.assertTrue(h.getLevel().addFreshEntity(entity),"genuine entity added before natural ready wait");}
    private static GameTestSequence ready(GameTestHelper h,Entity...entities){return h.startSequence().thenWaitUntil(()->{for(Entity e:entities)h.assertTrue(e.tickCount>3&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e,"real ID/UUID publication and natural entity ticks: "+e.getUUID()+" ticks="+e.tickCount);}).thenExecute(()->{h.assertTrue(!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isEnabled(),"focused core uses actual fog-off environment, no fabricated visibility");for(Entity e:entities)if(e instanceof Unit u)h.assertTrue(u.getItems().isEmpty(),"cargo not contaminated by fixture construction");bank(h);});}
    private static String account(String suffix) {String name=suffix.startsWith(PREFIX)?suffix:PREFIX+suffix;if(OWNERS.add(name)){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,123,234,345,456));}return name;}
    private static FakePlayer player(GameTestHelper h,String suffix) {String name=account(suffix);return PLAYERS.computeIfAbsent(name,key->{var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),key));player.connection=new SilentHandler(player);var origin=h.absolutePos(BlockPos.ZERO);player.moveTo(origin.getX()+2.5,origin.getY()+1,origin.getZ()+29.5);ENTITIES.add(player);h.getLevel().addNewPlayer(player);PlayerServerEvents.players.add(player);return player;});}
    private static SkillRequest request(ShadowSquealGhostUnit source,Entity target) {return new SkillRequest(UnitSkill.SQUEAL_SUPPRESSION_WAVE,source.getId(),source.getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID());}
    private static SkillResult cast(GameTestHelper h,ShadowSquealGhostUnit source,Entity target) {return new SkillNetwork.Cast(request(source,target)).execute(player(h,source.getOwnerName()));}
    private static int cd(ShadowSquealGhostUnit source) {return source.rts().skills().cooldownTicks(UnitSkill.SQUEAL_SUPPRESSION_WAVE);}
    private static void order(GameTestHelper h,ShadowSquealGhostUnit source,LivingEntity target) {
        try {var sender=player(h,source.getOwnerName());var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);connection.setListener(sender.connection);var constructor=net.minecraftforge.network.NetworkEvent.Context.class.getDeclaredConstructor(net.minecraft.network.Connection.class,net.minecraftforge.network.NetworkDirection.class,int.class);constructor.setAccessible(true);var context=constructor.newInstance(connection,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER,-1);h.assertTrue(context.getSender()==sender,"actual Forge command context resolves exact sender");h.assertTrue(new com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket(source.getOwnerName(),UnitAction.ATTACK,target.getId(),new int[]{source.getId()},target.blockPosition(),BlockPos.ZERO,false).handle(()->context),"real authenticated ordinary ATTACK accepted");}
        catch(ReflectiveOperationException failure){throw new RuntimeException("exact existing Forge network context",failure);}
    }
    private static void damage(LivingEntity source,double amount) {source.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(amount);if(source.getAttribute(Attributes.ATTACK_DAMAGE)!=null)source.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(amount);var crit=source.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get());if(crit!=null)crit.setBaseValue(0);}
    private static void neutral(LivingEntity target) {target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);target.setAbsorptionAmount(0);for(var attribute:List.of(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(),AttributeRegistrar.RANGED_DAMAGE_RESIST.get()))if(target.getAttribute(attribute)!=null)target.getAttribute(attribute).setBaseValue(0);}
    private static void bank(GameTestHelper h) {for(String owner:OWNERS){var r=ResourcesServerEvents.resourcesList.stream().filter(value->value.ownerName.equals(owner)).findFirst().orElse(null);h.assertTrue(r!=null&&r.food==123&&r.wood==234&&r.ore==345&&r.emerald==456&&r.foodToAdd==0&&r.woodToAdd==0&&r.oreToAdd==0&&r.emeraldToAdd==0,"no skill currency, pending-resource mutation or silently recreated ledger: "+owner);}for(Entity e:ENTITIES)if(e instanceof Unit unit)h.assertTrue(unit.getItems().isEmpty(),"suppression never manufactures cargo");}
    private static void require(GameTestHelper h,SkillResult actual,SkillResult expected,String why) {h.assertTrue(actual==expected,why+" expected="+expected+" actual="+actual);}
    private static void close(GameTestHelper h,double actual,double expected,String why) {h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<.001,why+" expected="+expected+" actual="+actual);}
    private static void pass(String name) {System.out.println("DREAMSEEKERS_SQUEAL_SUPPRESSION_WAVE_PASS "+name);}
    private static AABB area(GameTestHelper h) {var p=h.absolutePos(BlockPos.ZERO);return new AABB(p,p.offset(32,10,32));}
    private static void wall(GameTestHelper h,net.minecraft.world.level.block.Block block) {for(int z=23;z<=28;z++)for(int y=1;y<=8;y++)h.getLevel().setBlock(h.absolutePos(new BlockPos(8,y,z)),block.defaultBlockState(),2);}
    private static void room(GameTestHelper h) {
        var p=h.absolutePos(BlockPos.ZERO);var level=h.getLevel();
        for(int x=p.getX()>>4;x<=(p.getX()+31)>>4;x++)for(int z=p.getZ()>>4;z<=(p.getZ()+31)>>4;z++){var chunk=new ChunkPos(x,z);if(!level.getForcedChunks().contains(chunk.toLong())){level.setChunkForced(x,z,true);TICKETS.add(chunk);}}
        for(int x=0;x<32;x++)for(int z=0;z<32;z++)level.setBlock(p.offset(x,9,z),Blocks.STONE.defaultBlockState(),2);
        level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+32,level.getMaxBuildHeight(),p.getZ()+32)).forEach(Entity::discard);
        for(int x=0;x<32;x++)for(int z=0;z<32;z++){level.setBlock(p.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<9;y++)level.setBlock(p.offset(x,y,z),(x==0||x==31||z==0||z==31?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}
        level.getEntitiesOfClass(ItemEntity.class,area(h)).forEach(Entity::discard);
    }
    private static void partition(GameTestHelper h,int z){for(int x=1;x<31;x++)for(int y=1;y<9;y++)h.getLevel().setBlock(h.absolutePos(new BlockPos(x,y,z)),Blocks.STONE.defaultBlockState(),2);}
    private static SkillResult otherSkill(GameTestHelper h,SkillUnit source,LivingEntity target,UnitSkill skill){var body=source.mob();return new SkillNetwork.Cast(new SkillRequest(skill,body.getId(),body.getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID())).execute(player(h,source.getOwnerName()));}
    private record SyncObservation(long gameTime,SkillNetwork.Sync packet){}
    private static List<SyncObservation> syncs(ServerPlayer player,ShadowSquealGhostUnit source){return ((RecordingHandler)player.connection).wire.syncs.stream().filter(p->p.packet().sourceUuid().equals(source.getUUID())&&p.packet().skill()==UnitSkill.SQUEAL_SUPPRESSION_WAVE&&p.packet().revision()>=0).toList();}
    /** Local real PlayerList membership, not GUI/socket login simulation or direct state.sync. */
    @SuppressWarnings("unchecked") private static void syncMembership(ServerPlayer player,boolean present){try{var list=player.serverLevel().getServer().getPlayerList();var players=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");var byId=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");players.setAccessible(true);byId.setAccessible(true);List<ServerPlayer> actual=(List<ServerPlayer>)players.get(list);Map<UUID,ServerPlayer> ids=(Map<UUID,ServerPlayer>)byId.get(list);if(present){if(!actual.contains(player))actual.add(player);ids.put(player.getUUID(),player);SYNC_MEMBERS.add(player);}else{actual.remove(player);ids.remove(player.getUUID(),player);SYNC_MEMBERS.remove(player);}}catch(ReflectiveOperationException error){throw new RuntimeException("real local PlayerList membership",error);}}
    private static final class RecordingHandler extends ServerGamePacketListenerImpl {
        final RecordingConnection wire;RecordingHandler(ServerPlayer p){this(p,new RecordingConnection(p.serverLevel()));}private RecordingHandler(ServerPlayer p,RecordingConnection wire){super(p.serverLevel().getServer(),wire,p);this.wire=wire;}
        @Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> p){wire.send(p);}@Override public void send(net.minecraft.network.protocol.Packet<?> p,net.minecraft.network.PacketSendListener listener){wire.send(p,listener);}
    }
    private static final class RecordingConnection extends net.minecraft.network.Connection {
        final ServerLevel level;final List<SyncObservation> syncs=new ArrayList<>();RecordingConnection(ServerLevel level){super(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);this.level=level;}
        @Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> p){record(p);}@Override public void send(net.minecraft.network.protocol.Packet<?> p,net.minecraft.network.PacketSendListener listener){record(p);}
        private void record(net.minecraft.network.protocol.Packet<?> packet){if(packet instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket payload&&payload.getIdentifier().equals(DreamseekersMod.id("unit_skills"))){var bytes=new FriendlyByteBuf(payload.getData().copy());try{if(bytes.readVarInt()==1){var sync=SkillNetwork.Sync.decode(bytes);if(bytes.readableBytes()!=0)throw new IllegalStateException("trailing real Sync payload");syncs.add(new SyncObservation(level.getGameTime(),sync));}}finally{bytes.release();}}}
    }
    private static final class SilentHandler extends ServerGamePacketListenerImpl {SilentHandler(ServerPlayer p){super(p.serverLevel().getServer(),new SilentConnection(),p);}@Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> p){}@Override public void send(net.minecraft.network.protocol.Packet<?> p,net.minecraft.network.PacketSendListener listener){}}
    private static final class SilentConnection extends net.minecraft.network.Connection {SilentConnection(){super(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);}@Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> p){}@Override public void send(net.minecraft.network.protocol.Packet<?> p,net.minecraft.network.PacketSendListener listener){}}
}
