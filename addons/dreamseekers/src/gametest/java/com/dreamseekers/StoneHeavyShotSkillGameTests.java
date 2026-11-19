// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.*;
import com.dreamseekers.stone.HeavyStoneProjectile;
import com.dreamseekers.stone.StoneProjectile;
import com.dreamseekers.unit.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** First four vertical slices. Real authenticated casts, natural AI/entity ticks and physical impacts.
 * Observers only read actual Join/Impact/Damage events; they never arm a shot, grant a hit scope,
 * manually tick an attack goal, or register a delayed action from inside a callback.
 */
@GameTestHolder("dreamseekers_stone_heavy_shot")
@PrefixGameTestTemplate(false)
public final class StoneHeavyShotSkillGameTests {
    private static final String BATCH="dreamseekers_stone_heavy_shot", PREFIX="HeavyStone_";
    private static final List<Entity> ENTITIES=new ArrayList<>();
    private static final Set<String> OWNERS=new LinkedHashSet<>();
    private static final Map<String,FakePlayer> PLAYERS=new HashMap<>();
    private static final Set<ChunkPos> TICKETS=new LinkedHashSet<>();
    private static final Probe PROBE=new Probe();
    private static boolean listening;

    @AfterBatch(batch=BATCH)
    public static void cleanup(ServerLevel level) {
        for(var mutation:MUTATIONS)MinecraftForge.EVENT_BUS.unregister(mutation);MUTATIONS.clear();
        for(var boundary:BOUNDARIES)MinecraftForge.EVENT_BUS.unregister(boundary);BOUNDARIES.clear();
        if(listening){MinecraftForge.EVENT_BUS.unregister(PROBE);listening=false;}
        for(var trace:PROBE.traces.values())for(var shot:trace.shots)if(!shot.isRemoved())shot.discard();
        PROBE.traces.clear();
        for(String a:OWNERS)for(String b:OWNERS)if(!a.equals(b))AlliancesServerEvents.removeAlliance(a,b);
        PlayerServerEvents.rtsPlayers.removeIf(p->OWNERS.contains(p.name));
        ResourcesServerEvents.resourcesList.removeIf(r->OWNERS.contains(r.ownerName));
        PlayerServerEvents.players.removeIf(p->OWNERS.contains(p.getGameProfile().getName()));
        for(Entity entity:List.copyOf(ENTITIES))if(!entity.isRemoved()) {
            if(entity instanceof ServerPlayer player)player.serverLevel().removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);
            else entity.discard();
        }
        for(ChunkPos chunk:TICKETS)level.setChunkForced(chunk.x,chunk.z,false);
        TICKETS.clear();ENTITIES.clear();OWNERS.clear();PLAYERS.clear();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=520)
    public static void authenticatedTargetCodecCommitsOnePaidClockWithoutResourceOrSiegeEffects(GameTestHelper h) {
        room(h);
        var source=stone(h,"Authority",4.5,5.5);
        var target=jelly(h,"AuthorityEnemy",12.5,5.5);
        var friendly=jelly(h,"Authority",7.5,5.5);
        var wrong=DreamseekersMod.FIREFLY_WORKER.get().create(h.getLevel());owned(h,wrong,"Authority",4.5,15.5,true);
        var trace=watch(source,target,friendly);long[] at={0};float hp=target.getHealth();
        var seq=ready(h,source,target,friendly,wrong).thenExecute(()->{
            h.assertTrue(!source.isNoAi(),"authority positive source retains natural entity AI");
            h.assertTrue(UnitSkill.STONE_HEAVY_SHOT.range()==10&&UnitSkill.STONE_HEAVY_SHOT.durationTicks()==20
                    &&UnitSkill.STONE_HEAVY_SHOT.cooldownTicks()==360,"approved range ten / windup twenty / cooldown three hundred sixty");
            var packet=new SkillNetwork.Cast(request(source,target));var bytes=new FriendlyByteBuf(Unpooled.buffer());SkillNetwork.Cast decoded;
            try {packet.encode(bytes);decoded=SkillNetwork.Cast.decode(bytes);h.assertTrue(packet.equals(decoded)&&bytes.readableBytes()==0,"exact real Cast codec carries only skill/source/target identity and revision");}
            finally {bytes.release();}
            var sender=player(h,source.getOwnerName());long revision=source.rts().skills().revision();
            require(h,decoded.execute(null),SkillResult.INVALID_REQUEST,"missing authenticated sender");
            require(h,decoded.execute(player(h,"AuthorityForeign")),SkillResult.NOT_OWNER,"foreign sender cannot spend owner's skill");
            require(h,SkillService.request(sender,new SkillRequest(UnitSkill.STONE_HEAVY_SHOT,source.getId(),UUID.randomUUID(),revision,target.getId(),target.getUUID())),SkillResult.INVALID_SOURCE,"stale source UUID");
            require(h,SkillService.request(sender,new SkillRequest(UnitSkill.STONE_HEAVY_SHOT,source.getId(),source.getUUID(),revision,target.getId(),UUID.randomUUID())),SkillResult.INVALID_TARGET,"stale target UUID");
            require(h,SkillService.request(sender,new SkillRequest(UnitSkill.STONE_HEAVY_SHOT,wrong.getId(),wrong.getUUID(),wrong.rts().skills().revision(),target.getId(),target.getUUID())),SkillResult.INVALID_SOURCE,"old worker cannot borrow thrower's active");
            require(h,cast(h,source,source),SkillResult.INVALID_TARGET,"self is not an enemy target");
            require(h,cast(h,source,friendly),SkillResult.INVALID_TARGET,"own unit is not an enemy target");
            require(h,java.util.concurrent.CompletableFuture.supplyAsync(()->decoded.execute(sender)).join(),SkillResult.INVALID_REQUEST,"off-server-thread request is inert");
            h.assertTrue(cd(source)==0&&source.rts().skills().revision()==revision&&trace.shots.isEmpty(),"all rejected requests leave clock/revision/projectiles unchanged");
            h.assertTrue(source.getAbilities().get().equals(List.of(UnitSkillAbility.STONE_HEAVY_SHOT)),"exact one approved active; no siege/research button");
            var reserved=new ReservedSkillRequest(ReservedSkillRequest.Skill.STONE_SPIRIT_SIEGE_RESEARCH,source.getId(),source.getUUID(),new ReservedSkillRequest.Self());
            h.assertTrue(ReservedSkillService.request(sender,reserved)==ReservedSkillService.Result.DISABLED
                    &&ReservedSkillService.request(player(h,"AuthorityForeign"),reserved)==ReservedSkillService.Result.NOT_OWNER
                    &&source.getAttackBuildingGoal()==null&&!source.canAttackBuildings(),"reserved research remains disabled and building attacks remain unavailable");
            at[0]=h.getLevel().getGameTime();require(h,decoded.execute(sender),SkillResult.SUCCESS,"real owner reserves legal heavy shot");
            h.assertTrue(cd(source)==360&&StoneHeavyShot.active(source)&&StoneHeavyShot.remainingTicks(source)==20,"successful reservation starts exact paid clock and windup");
            require(h,decoded.execute(sender),SkillResult.REPLAY,"successful original token cannot replay");
            require(h,cast(h,source,target),SkillResult.COOLDOWN,"fresh revision cannot refresh reservation");
            h.assertTrue(target.getHealth()==hp&&friendly.getHealth()==friendly.getMaxHealth(),"reservation never performs immediate damage");bank(h);
        });
        for(int tick=1;tick<=19;tick++){final int elapsed=tick;seq.thenIdle(1).thenExecute(()->beforeRelease(h,trace,target,hp,at[0],elapsed));}
        seq.thenIdle(2).thenExecute(()->releasedAtTwenty(h,trace,at[0]))
            .thenIdle(24).thenExecute(()->{
                flight(h,trace,target);close(h,hp-hpAfterHeavy(h,trace,target),7.5,"one real unarmoured primary is 150% of released attack five");
                h.assertTrue(trace.damage.size()==1&&hpAfterHeavy(h,trace,friendly)==friendly.getMaxHealth(),"friendly body is never a primary or splash victim");bank(h);
            }).thenIdle(314).thenExecute(()->{
                h.assertTrue(h.getLevel().getGameTime()-at[0]==359&&cd(source)==1,"absolute cooldown has one tick remaining at elapsed 359");
                require(h,cast(h,source,target),SkillResult.COOLDOWN,"no early recast before original deadline");
            }).thenIdle(1).thenExecute(()->{
                h.assertTrue(h.getLevel().getGameTime()-at[0]==360&&cd(source)==0,"original deadline, not effect duration or target hit, ends cooldown");
                require(h,cast(h,source,target),SkillResult.SUCCESS,"same actual source may reserve again only at original deadline");
                bank(h);pass("AUTH_CODEC_CLOCK_NO_RESOURCE_OR_SIEGE");
            }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=260)
    public static void naturalLastOrdinaryWindupCannotLeakDuringTwentyTickHeavyReservation(GameTestHelper h) throws Exception {
        room(h);
        var source=stone(h,"Windup",4.5,8.5);var target=jelly(h,"WindupEnemy",12.5,8.5);
        var control=stone(h,"OrdinaryControl",4.5,22.5);var controlTarget=jelly(h,"OrdinaryEnemy",12.5,22.5);
        var trace=watch(source,target);var ordinary=watch(control,controlTarget);long[] at={0};Vec3[] start={null};float hp=target.getHealth(),controlHp=controlTarget.getHealth();
        var windup=UnitRangedAttackGoal.class.getDeclaredField("attackWindupTicksLeft");windup.setAccessible(true);
        var seq=ready(h,source,target,control,controlTarget).thenExecute(()->{
            h.assertTrue(!source.isNoAi()&&!control.isNoAi()&&source.getAttackGoal() instanceof UnitRangedAttackGoal,"both real ground sources use original natural ranged AI");
            order(h,source,UnitAction.ATTACK,target);order(h,control,UnitAction.ATTACK,controlTarget);
        }).thenWaitUntil(()->{
            try {h.assertTrue(!UnitServerEvents.hasPendingImmediateAction(source.getId())&&source.getTarget()==target
                    &&windup.getInt(source.getAttackGoal())==1,"observe actual last ordinary windup, never reset/tick a goal by hand");}
            catch(IllegalAccessException failure){throw new RuntimeException(failure);}
        }).thenExecute(()->{
            h.assertTrue(trace.shots.isEmpty()&&target.getHealth()==hp,"old ordinary attack has not released before genuine last-windup observation");
            start[0]=source.position();at[0]=h.getLevel().getGameTime();require(h,cast(h,source,target),SkillResult.SUCCESS,"real heavy reservation supersedes actual imminent ordinary attack");
            h.assertTrue(source.getTarget()==null&&source.getMoveGoal().getMoveTarget()==null&&source.getNavigation().isDone(),"reservation discards the current ordinary order/path instead of restoring it later");
        });
        for(int tick=1;tick<=19;tick++){final int elapsed=tick;seq.thenIdle(1).thenExecute(()->beforeRelease(h,trace,target,hp,at[0],elapsed));}
        seq.thenIdle(2).thenExecute(()->releasedAtTwenty(h,trace,at[0]));
        for(int tick=0;tick<30;tick++)seq.thenIdle(1).thenExecute(()->observe(trace));
        seq.thenExecute(()->{
            flight(h,trace,target);close(h,hp-hpAfterHeavy(h,trace,target),7.5,"actual HP sampled after heavy completion excludes later ordinary damage");
            h.assertTrue(heavyShots(trace).size()==1&&trace.shots.stream().filter(StoneProjectile.class::isInstance).allMatch(shot->trace.joinTimes.get(shot)>=at[0]+20),"exactly one heavy; later natural ordinary fire is allowed only after release, never during the twenty-tick windup");
            h.assertTrue(ordinary.shots.stream().anyMatch(StoneProjectile.class::isInstance)&&controlTarget.getHealth()<controlHp,"same original natural ranged AI really fires in unreserved control lane");
            h.assertTrue(source.position().distanceToSqr(start[0])<.08&&source.getMoveGoal().getMoveTarget()==null,"no hidden pursuit or old path restoration in heavy lane");
            bank(h);pass("NATURAL_LAST_WINDUP_TWENTY_TICKS_NO_ORDINARY_LEAK");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=180)
    public static void realImpactUsesReleaseAttackSnapshotAndOnlyNearestThreeSecondaryBodies(GameTestHelper h) {
        room(h);
        var source=stone(h,"Splash",4.5,12.5);var primary=jelly(h,"SplashEnemy",12.5,12.5);
        var a=jelly(h,"SplashEnemy",11.85,13.4);var b=jelly(h,"SplashEnemy",11.85,11.6);
        var c=jelly(h,"SplashEnemy",13.4,13.0);var d=jelly(h,"SplashEnemy",13.4,12.0);
        var friendly=jelly(h,"Splash",12.65,13.25);var outside=jelly(h,"SplashEnemy",11.85,14.4);
        var trace=watch(source,primary,a,b,c,d,friendly,outside);trace.candidates=List.of(a,b,c,d);trace.friendly=friendly;trace.outside=outside;
        long[] at={0};Map<UUID,Float> hp=new HashMap<>();for(var e:List.of(primary,a,b,c,d,friendly,outside))hp.put(e.getUUID(),e.getHealth());
        var seq=ready(h,source,primary,a,b,c,d,friendly,outside).thenExecute(()->{
            close(h,source.getUnitAttackDamage(),5,"cast-time fixture attack is five");
            at[0]=h.getLevel().getGameTime();require(h,cast(h,source,primary),SkillResult.SUCCESS,"actual enemy heavy cast starts unaltered windup");
        });
        for(int tick=1;tick<=19;tick++){final int elapsed=tick;seq.thenIdle(1).thenExecute(()->{
            beforeRelease(h,trace,primary,hp.get(primary.getUUID()),at[0],elapsed);
            if(elapsed==19){damage(source,10);close(h,source.getUnitAttackDamage(),10,"real attribute change before release makes released base ten");}
        });}
        seq.thenIdle(2).thenExecute(()->{
            releasedAtTwenty(h,trace,at[0]);h.assertTrue(primary.getHealth()==hp.get(primary.getUUID()),"distant physical projectile has not instant-hit during release");
            damage(source,20);close(h,source.getUnitAttackDamage(),20,"in-flight attribute change cannot replace released snapshot");
        }).thenIdle(24).thenExecute(()->{
            flight(h,trace,primary);h.assertTrue(trace.impacts.size()==1,"one natural physical collision, not a manufactured primary callback");
            var impact=trace.impacts.get(0);
            h.assertTrue(impact.distanceSq.size()==4&&impact.distanceSq.values().stream().allMatch(distance->distance<=2.25),"all four candidate bbox centres actually lie in impact-centred radius 1.5: "+impact.distanceSq);
            h.assertTrue(impact.friendlyDistanceSq<=2.25&&impact.outsideDistanceSq>2.25,"real inside-friendly and outside-enemy controls bracket actual collision-centred radius");
            var expected=new ArrayList<UUID>();expected.add(primary.getUUID());expected.addAll(impact.ordered.subList(0,3));
            h.assertTrue(trace.damage.stream().map(DamageRead::target).toList().equals(expected),"actual hit order is primary, then exactly nearest-three bbox centres / UUID tie-break: expected="+expected+" actual="+trace.damage);
            close(h,hp.get(primary.getUUID())-hpAfterHeavy(h,trace,primary),15,"primary is release-base ten times 1.5, not cast five or later twenty");
            for(var secondary:trace.candidates){boolean hit=expected.contains(secondary.getUUID());close(h,hp.get(secondary.getUUID())-hpAfterHeavy(h,trace,secondary),hit?3:0,"secondary is released base times .3, never primary fifteen times .3: "+secondary.getUUID());}
            h.assertTrue(hpAfterHeavy(h,trace,friendly)==hp.get(friendly.getUUID())&&hpAfterHeavy(h,trace,outside)==hp.get(outside.getUUID()),"friendly and out-of-radius bodies never become splash recipients");
            h.assertTrue(trace.damage.stream().allMatch(DamageRead::published),"same actual projectile remains published through primary and every secondary callback");
            h.assertTrue(heavyShots(trace).size()==1&&cd(source)==315,"splash creates no extra heavy projectile or cooldown refresh; ordinary AI remains enabled");
            bank(h);pass("RELEASE_SNAPSHOT_PHYSICAL_PRIMARY_NEAREST_THREE_SPLASH");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=180)
    public static void illegalTargetsAndPhysicalWallNeverTurnHeavyShotIntoTerrainOrUnownedExplosion(GameTestHelper h) {
        room(h);
        var source=stone(h,"Wall",4.5,12.5);var target=jelly(h,"WallEnemy",12.5,12.5);var nearby=jelly(h,"WallEnemy",13.4,12.5);
        var own=jelly(h,"Wall",6.5,6.5);var ally=jelly(h,"WallAlly",6.5,9.5);var far=jelly(h,"WallEnemy",25.5,12.5);
        AlliancesServerEvents.addAlliance(source.getOwnerName(),ally.getOwnerName());
        var wild=EntityType.COW.create(h.getLevel());wild.setNoAi(true);place(h,wild,8.5,18.5);
        var boat=new net.minecraft.world.entity.vehicle.Boat(h.getLevel(),0,0,0);place(h,boat,5.5,20.5);
        var trace=watch(source,target,nearby);long[] at={0};float hp=target.getHealth(),nearHp=nearby.getHealth();
        var seq=ready(h,source,target,nearby,own,ally,far,wild,boat).thenExecute(()->{
            long revision=source.rts().skills().revision();
            for(Entity illegal:List.of(source,own,ally,wild,boat))require(h,cast(h,source,illegal),SkillResult.INVALID_TARGET,"non-enemy/non-RTS/nonliving target denied: "+illegal.getType());
            require(h,cast(h,source,far),SkillResult.RANGE,"current server range above ten denied");
            target.setInvisible(true);require(h,cast(h,source,target),SkillResult.HIDDEN,"actually invisible target denied");target.setInvisible(false);
            wall(h,Blocks.STONE);h.assertTrue(!source.hasLineOfSight(target),"real solid wall blocks original entity LOS");
            h.assertTrue(cast(h,source,target)!=SkillResult.SUCCESS,"physical wall at cast cannot reserve a shot");
            h.assertTrue(cd(source)==0&&source.rts().skills().revision()==revision&&!StoneHeavyShot.active(source)&&trace.shots.isEmpty(),"all illegal inputs are clock/revision/projectile inert");
            wall(h,Blocks.AIR);h.assertTrue(source.hasLineOfSight(target),"same actual scene becomes visible when fixture wall is removed");
            at[0]=h.getLevel().getGameTime();require(h,cast(h,source,target),SkillResult.SUCCESS,"legal same-source cast after denials");
        });
        for(int tick=1;tick<=19;tick++){final int elapsed=tick;seq.thenIdle(1).thenExecute(()->beforeRelease(h,trace,target,hp,at[0],elapsed));}
        seq.thenIdle(2).thenExecute(()->{
            var shot=releasedAtTwenty(h,trace,at[0]);h.assertTrue(shot.getX()<h.absolutePos(new BlockPos(8,1,12)).getX(),"genuine projectile is still before wall plane, never teleported");
            wall(h,Blocks.STONE);
        }).thenIdle(30).thenExecute(()->{
            observe(trace);h.assertTrue(trace.shots.size()==1&&trace.shots.get(0).isRemoved()&&trace.impacts.size()==1
                    &&trace.impacts.get(0).type==HitResult.Type.BLOCK,"natural flight actually strikes a real solid block and terminates");
            h.assertTrue(trace.travel>1&&trace.damage.isEmpty()&&target.getHealth()==hp&&nearby.getHealth()==nearHp,"physical wall yields no primary, splash, or delayed unowned explosion");
            for(int z=10;z<=14;z++)for(int y=1;y<=5;y++)h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(8,y,z))).is(Blocks.STONE),"every real wall block survives heavy impact");
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class,area(h)).isEmpty(),"heavy shot creates no terrain drops or gifts");
            h.assertTrue(source.getAttackBuildingGoal()==null&&!source.canAttackBuildings(),"heavy shot still does not grant unresearched building attack");
            bank(h);pass("ILLEGAL_TARGETS_REAL_WALL_NO_SPLASH_NO_TERRAIN");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=180)
    public static void immediateCommandsCancelPaidWindupButShiftAndReleasedShotKeepTheirBoundaries(GameTestHelper h) {
        room(h);boundaryWalls(h,false);
        List<StoneSpiritThrowerUnit> sources=new ArrayList<>();List<JellyfishUnit> targets=new ArrayList<>();List<Trace> traces=new ArrayList<>();List<Entity> all=new ArrayList<>();
        for(int i=0;i<7;i++){double z=3.5+4*i;var source=stone(h,"Command"+i,4.5,z);var target=jelly(h,"CommandEnemy"+i,12.5,z);sources.add(source);targets.add(target);traces.add(watch(source,target));all.add(source);all.add(target);}
        var friend=jelly(h,"Command3",7.5,16.1);all.add(friend);
        UnitAction[] actions={UnitAction.STOP,UnitAction.MOVE,UnitAction.ATTACK,UnitAction.FOLLOW,UnitAction.HOLD};
        long[] at={0};long[] revision=new long[7];Vec3[] starts=new Vec3[7];
        BlockPos queuedDestination=h.absolutePos(new BlockPos(2,0,23));
        var seq=ready(h,all.toArray(Entity[]::new)).thenExecute(()->{
            at[0]=h.getLevel().getGameTime();
            for(int i=0;i<7;i++){var source=sources.get(i);h.assertTrue(!source.isNoAi(),"command lanes retain real natural AI");starts[i]=source.position();require(h,cast(h,source,targets.get(i)),SkillResult.SUCCESS,"real paid reservation before command boundary lane="+i);revision[i]=source.rts().commandRevision;}
            command(h,sources.get(5),UnitAction.MOVE,-1,queuedDestination,true);
            h.assertTrue(queuedMove(sources.get(5))&&StoneHeavyShot.active(sources.get(5)),"real Shift packet stores a future MOVE without replacing current heavy windup");
        });
        for(int tick=1;tick<=19;tick++){final int elapsed=tick;seq.thenIdle(1).thenExecute(()->{
            for(int i=0;i<7;i++)beforeRelease(h,traces.get(i),targets.get(i),1000,at[0],elapsed);
            var queued=sources.get(5);h.assertTrue(queuedMove(queued)&&queued.getMoveGoal().getMoveTarget()==null&&!queued.isIdle()
                    &&queued.rts().commandRevision==revision[5]&&horizontalSquared(queued.position(),starts[5])<.08,"Shift remains queued with no early movement/command revision through tick nineteen");
            if(elapsed==19)for(int i=0;i<5;i++){
                var source=sources.get(i);LivingEntity target=i==3?friend:targets.get(i);
                command(h,source,actions[i],target.getId(),h.absolutePos(new BlockPos(8,0,3+4*i)),false);
                commandBoundaryRaw(h,traces.get(i),revision[i],at[0],"t19_after_packet_"+actions[i]);
                h.assertTrue(!StoneHeavyShot.active(source)&&cd(source)==341,"actual newly accepted immediate command invalidates windup before dispatch, without refund: "+actions[i]);
            }
        });}
        seq.thenIdle(1).thenExecute(()->{
            for(int i=0;i<7;i++)commandBoundaryRaw(h,traces.get(i),revision[i],at[0],"t20_before_original_t21_assert_lane_"+i);
        }).thenIdle(1).thenExecute(()->{
            for(int i=0;i<7;i++)commandBoundaryRaw(h,traces.get(i),revision[i],at[0],"t21_before_original_assert_lane_"+i);
            for(int i=0;i<5;i++)h.assertTrue(!StoneHeavyShot.active(sources.get(i))&&heavyShots(traces.get(i)).isEmpty()
                    &&cd(sources.get(i))==339&&(actions[i]==UnitAction.ATTACK
                        ?sources.get(i).getTarget()==targets.get(i)&&sources.get(i).getTargetGoal().forced&&!UnitServerEvents.hasPendingImmediateAction(sources.get(i).getId())
                        :sources.get(i).rts().commandRevision>revision[i]),"natural dispatcher applies replacement and prevents heavy publication: "+actions[i]);
            releasedAtTwenty(h,traces.get(5),at[0]);var released=releasedAtTwenty(h,traces.get(6),at[0]);
            command(h,sources.get(6),UnitAction.STOP,-1,BlockPos.ZERO,false);
            h.assertTrue(!released.isRemoved()&&h.getLevel().getEntity(released.getUUID())==released&&cd(sources.get(6))==339,"STOP after real publication cannot recall the already released projectile or refund cooldown");
        }).thenIdle(24).thenExecute(()->{
            h.assertTrue(h.getLevel().getGameTime()-at[0]==45,"new command proof uses fixed release-plus-twenty-five observation, not a retry window");
            for(int i=0;i<5;i++){
                h.assertTrue(heavyShots(traces.get(i)).isEmpty()&&cd(sources.get(i))==315,"canceled windup never resumes or refunds, even if ordinary AI later fires: "+actions[i]);
                require(h,cast(h,sources.get(i),targets.get(i)),SkillResult.COOLDOWN,"new legal request cannot bypass original command-canceled clock");
            }
            for(int i=5;i<7;i++){flight(h,traces.get(i),targets.get(i));close(h,1000-hpAfterHeavy(h,traces.get(i),targets.get(i)),7.5,"true released Heavy still hits after queued/new command lane="+i);}
            Vec3 destination=Vec3.atBottomCenterOf(queuedDestination),moved=sources.get(5).position().subtract(starts[5]),toward=destination.subtract(starts[5]);
            h.assertTrue(!queuedMove(sources.get(5))&&sources.get(5).rts().commandRevision>revision[5]
                    &&moved.x*toward.x+moved.z*toward.z>1e-6
                    &&horizontalSquared(sources.get(5).position(),destination)<horizontalSquared(starts[5],destination),"only after release does genuine Shift MOVE dispatch and naturally advance toward its actual destination, without an arbitrary speed/distance quota");
            h.assertTrue(traces.get(5).shots.stream().filter(StoneProjectile.class::isInstance).allMatch(shot->traces.get(5).joinTimes.get(shot)>=at[0]+20)
                    &&traces.get(6).shots.stream().filter(StoneProjectile.class::isInstance).allMatch(shot->traces.get(6).joinTimes.get(shot)>=at[0]+20),"post-release legal ordinary projectiles remain a separate type, never evidence of heavy replay");
            bank(h);pass("IMMEDIATE_CANCEL_PAID_CLOCK_SHIFT_AND_POST_RELEASE_STOP");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=200)
    public static void actualConstructionJoinAndLifecycleChangesCannotRecycleHeavyAuthority(GameTestHelper h) {
        room(h);boundaryWalls(h,true);
        List<StoneSpiritThrowerUnit> sources=new ArrayList<>();List<JellyfishUnit> targets=new ArrayList<>(),nearby=new ArrayList<>();List<Trace> traces=new ArrayList<>();List<Entity> all=new ArrayList<>();
        for(int i=0;i<9;i++){double x=i<5?4.5:19.5,z=3.5+6*(i%5);var source=stone(h,"Life"+i,x,z);var target=jelly(h,"LifeEnemy"+i,x+8,z);var near=jelly(h,"LifeEnemy"+i,x+8.9,z+.5);sources.add(source);targets.add(target);nearby.add(near);traces.add(watch(source,target,near));all.add(source);all.add(target);all.add(near);}
        // Actual surviving same-owner bodies preserve the original ledgers through death/unload lanes.
        all.add(jelly(h,"Life6",17.5,10));all.add(jelly(h,"Life7",17.5,16));
        var boundary=new BoundaryProbe(h);BOUNDARIES.add(boundary);MinecraftForge.EVENT_BUS.register(boundary);
        long[] at=new long[9];boolean[] fired=new boolean[9];String temporary=account("LifeTemporary");
        float[] primaryBefore=new float[9],nearBefore=new float[9];
        var seq=controlledReady(h,"lifecycle",sources,targets,nearby,traces,primaryBefore,nearBefore,all).thenExecute(()->{
            boundary.constructingSource=sources.get(0);
            boundary.constructing=event->{
                fired[0]=true;boundary.constructed=event.getEntity();
                h.assertTrue(h.getLevel().getGameTime()==at[0]+20&&StoneHeavyShot.active(sources.get(0)),"actual constructor callback occurs in original source's natural release tick while reservation still exists");
                require(h,cast(h,sources.get(0),targets.get(0)),SkillResult.COOLDOWN,"real constructor reentry cannot buy a second reservation");
                command(h,sources.get(0),UnitAction.STOP,-1,BlockPos.ZERO,false);
            };
            for(int i=1;i<=4;i++){final int lane=i;boundary.joins.put(sources.get(i).getUUID(),event->{
                fired[lane]=true;var shot=(HeavyStoneProjectile)event.getEntity();boundary.joinedShots.put(lane,shot);
                h.assertTrue(h.getLevel().getGameTime()==at[lane]+20,"real Join callback retains exact twenty-tick release lane="+lane);
                require(h,cast(h,sources.get(lane),targets.get(lane)),SkillResult.COOLDOWN,"actual Join reentry cannot refresh the already paid reservation");
                switch(lane){
                    case 1->command(h,sources.get(lane),UnitAction.STOP,-1,BlockPos.ZERO,false);
                    case 2->event.setCanceled(true);
                    case 3->{String owner=sources.get(lane).getOwnerName();sources.get(lane).setOwnerName(temporary);sources.get(lane).setOwnerName(owner);}
                    case 4->{shot.setOwner(targets.get(lane));shot.setOwner(sources.get(lane));}
                    default->throw new IllegalStateException();
                }
            });}
            boundary.hurts.put(targets.get(8).getUUID(),event->{
                fired[8]=true;h.assertTrue(event.getSource().getEntity()==sources.get(8)&&event.getSource().getDirectEntity() instanceof HeavyStoneProjectile,"actual original heavy hurt is the victim-ABA callback, not a manufactured scope");
                String owner=targets.get(8).getOwnerName();targets.get(8).setOwnerName(temporary);targets.get(8).setOwnerName(owner);
            });
            at[0]=h.getLevel().getGameTime();require(h,cast(h,sources.get(0),targets.get(0)),SkillResult.SUCCESS,"real constructor-reentry source reserves first");
        });
        // Entire timeline is registered now. Other lanes reserve one tick later, never from a callback.
        for(int tick=1;tick<=22;tick++){final int elapsed=tick;seq.thenIdle(1).thenExecute(()->{
            h.assertTrue(h.getLevel().getGameTime()-at[0]==elapsed,"fixed lifecycle timeline elapsed="+elapsed);
            if(elapsed<=19)beforeRelease(h,traces.get(0),targets.get(0),primaryBefore[0],at[0],elapsed);
            if(elapsed==1)for(int i=1;i<9;i++){at[i]=h.getLevel().getGameTime();require(h,cast(h,sources.get(i),targets.get(i)),SkillResult.SUCCESS,"real natural-tick manual lifecycle reservation lane="+i);}
            if(elapsed>=2&&elapsed<=20)for(int i=1;i<9;i++)beforeRelease(h,traces.get(i),targets.get(i),primaryBefore[i],at[i],elapsed-1);
            if(elapsed==20){String owner=targets.get(5).getOwnerName();targets.get(5).setOwnerName(temporary);targets.get(5).setOwnerName(owner);fired[5]=true;
                h.assertTrue(!StoneHeavyShot.active(sources.get(5))&&cd(sources.get(5))==341,"real target owner A-to-B-to-A at tick nineteen revokes aim identity without refund");}
            if(elapsed==21){
                h.assertTrue(fired[0]&&boundary.constructed!=null&&boundary.constructed.isRemoved()
                        &&h.getLevel().getEntity(boundary.constructed.getUUID())==null&&heavyShots(traces.get(0)).isEmpty()&&cd(sources.get(0))==339,"constructor STOP keeps actual created object unpublished and cannot leave a delayed heavy behind");
            }
            if(elapsed==22){
                for(int i=1;i<=4;i++)h.assertTrue(fired[i]&&boundary.joinedShots.containsKey(i),"actual one-shot Join callback was exercised lane="+i);
                releasedAtTwenty(h,traces.get(1),at[1]);
                for(int i=2;i<=4;i++){var shot=boundary.joinedShots.get(i);h.assertTrue(shot.isRemoved()&&h.getLevel().getEntity(shot.getUUID())==null&&cd(sources.get(i))==339,"canceled Join or source/projectile owner ABA never retains published flight authority lane="+i);}
                h.assertTrue(heavyShots(traces.get(5)).isEmpty(),"target ownership restoration cannot resurrect canceled windup");
                for(int i=6;i<9;i++){releasedAtTwenty(h,traces.get(i),at[i]);h.assertTrue(targets.get(i).getHealth()==primaryBefore[i],"actual flight has not reached distant victim before lifecycle mutation lane="+i);}
                var dead=sources.get(6);dead.hurt(dead.damageSources().genericKill(),10000);fired[6]=true;h.assertTrue(!dead.isAlive()&&cd(dead)==339,"real source death revokes flight, not original paid deadline");
                sources.get(7).remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);fired[7]=true;h.assertTrue(sources.get(7).isRemoved()&&cd(sources.get(7))==339,"real source removal lifecycle revokes flight without manual NBT reconstruction");
            }
        });}
        seq.thenIdle(24).thenExecute(()->{
            flight(h,traces.get(1),targets.get(1));close(h,primaryBefore[1]-hpAfterHeavy(h,traces.get(1),targets.get(1)),7.5,"STOP inside actual Join cannot recall committed primary");
            close(h,nearBefore[1]-hpAfterHeavy(h,traces.get(1),nearby.get(1)),1.5,"same committed Join-STOP shot still owns its legitimate secondary");
            for(int i=0;i<9;i++){
                h.assertTrue(fired[i],"every bounded constructor/Join/lifecycle probe actually fired lane="+i);
                h.assertTrue(cd(sources.get(i))==360-(h.getLevel().getGameTime()-at[i]),"no callback/lifecycle path refunds or refreshes absolute paid cooldown lane="+i);
                h.assertTrue(heavyShots(traces.get(i)).size()<=1&&heavyShots(traces.get(i)).stream().allMatch(Entity::isRemoved),"at most original heavy object, always consumed/revoked lane="+i);
                if(i!=1){close(h,targets.get(i).getHealth(),primaryBefore[i],"invalid authority never writes primary HP lane="+i);close(h,nearby.get(i).getHealth(),nearBefore[i],"invalid authority never creates secondary HP loss lane="+i);h.assertTrue(traces.get(i).damage.stream().allMatch(read->read.amount==0),"invalid authority has no positive heavy damage event lane="+i);}
            }
            h.assertTrue(traces.get(8).impacts.size()==1&&targets.get(8).getUUID().equals(traces.get(8).impacts.get(0).victim),"actual physical primary collision precedes victim ABA, which cannot create a splash receipt");
            bank(h);pass("REAL_CONSTRUCTOR_JOIN_REENTRY_AND_LIFECYCLE_AUTHORIZATION");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=180)
    public static void onlyExactPrimaryHealthOrEnemyGelReceiptTriggersOneSplashAcrossCancelAndReentry(GameTestHelper h) {
        room(h);boundaryWalls(h,true);
        List<StoneSpiritThrowerUnit> sources=new ArrayList<>();List<JellyfishUnit> targets=new ArrayList<>(),nearby=new ArrayList<>();List<Trace> traces=new ArrayList<>();List<Entity> all=new ArrayList<>();
        for(int i=0;i<8;i++){double x=i<5?4.5:19.5,z=3.5+6*(i%5);var source=stone(h,"Receipt"+i,x,z);damage(source,i==0?5:10);var target=jelly(h,"ReceiptEnemy"+i,x+8,z);var near=jelly(h,"ReceiptEnemy"+i,x+8.9,z+.5);sources.add(source);targets.add(target);nearby.add(near);traces.add(watch(source,target,near));all.add(source);all.add(target);all.add(near);}
        var gel=support(h,DreamseekersMod.PINK_SLIME_UNIT.get().create(h.getLevel()),"ReceiptEnemy0",14.7,5);all.add(gel);
        var mutation=mutations();boolean[] fired=new boolean[8];net.minecraft.world.damagesource.DamageSource[] spentSource={null};long[] at={0};
        float[] primaryBefore=new float[8],nearBefore=new float[8];
        var seq=controlledReady(h,"receipt",sources,targets,nearby,traces,primaryBefore,nearBefore,all).thenExecute(()->{
            require(h,otherSkill(h,gel,targets.get(0),UnitSkill.GEL_GUARD),SkillResult.SUCCESS,"real same-owner Gel cast, never injected shield state");close(h,SkillEffects.shieldRemaining(targets.get(0)),12,"actual twelve-point enemy Gel shield exists before heavy cast");
            targets.get(3).setAbsorptionAmount(100); // Native absorption fixture, not an addon receipt or shield grant.
            mutation.hurts.put(targets.get(1).getUUID(),event->{fired[1]=true;event.setCanceled(true);});
            mutation.damages.put(targets.get(2).getUUID(),event->{fired[2]=true;event.setAmount(0);});
            mutation.damages.put(targets.get(4).getUUID(),event->{
                fired[4]=true;var victim=targets.get(4);float hp=victim.getHealth();victim.invulnerableTime=0;
                h.assertTrue(victim.hurt(victim.damageSources().generic(),2),"real nested non-Heavy generic hurt is independently accepted");close(h,hp-victim.getHealth(),2,"unrelated nested event actually writes two HP before outer finalization");event.setAmount(0);
            });
            mutation.hurts.put(targets.get(5).getUUID(),event->{fired[5]=true;h.assertTrue(!targets.get(5).hurt(event.getSource(),15),"same exact DamageSource recursive hurt is rejected, revoking the outer Heavy section");});
            mutation.hurts.put(targets.get(6).getUUID(),event->{
                fired[6]=true;spentSource[0]=event.getSource();var different=targets.get(6).damageSources().thrown(event.getSource().getDirectEntity(),sources.get(6));
                h.assertTrue(different!=event.getSource()&&!targets.get(6).hurt(different,15),"new DamageSource with same real projectile cannot borrow original section; original outer hit remains independent");
            });
            mutation.damages.put(targets.get(7).getUUID(),event->{fired[7]=true;event.setCanceled(true);});
            at[0]=h.getLevel().getGameTime();for(int i=0;i<8;i++)require(h,cast(h,sources.get(i),targets.get(i)),SkillResult.SUCCESS,"actual paid receipt-boundary cast lane="+i);
        });
        for(int tick=1;tick<=19;tick++){final int elapsed=tick;seq.thenIdle(1).thenExecute(()->{for(int i=0;i<8;i++)beforeRelease(h,traces.get(i),targets.get(i),primaryBefore[i],at[0],elapsed);});}
        seq.thenIdle(2).thenExecute(()->{for(var trace:traces)releasedAtTwenty(h,trace,at[0]);}).thenIdle(24).thenExecute(()->{
            for(int i=0;i<8;i++){
                var trace=traces.get(i);var source=sources.get(i);completedReceiptFlight(h,trace,targets.get(i));
                if(i!=4)h.assertTrue(trace.hpSnapshot.incoming.stream().allMatch(read->read.actor==source&&read.direct==heavyShots(trace).get(0)),"all snapshot incoming belongs to the exact Heavy in isolated receipt lane="+i);
                else h.assertTrue(trace.hpSnapshot.incoming.stream().filter(read->!(read.direct instanceof HeavyStoneProjectile)).count()==1
                        &&trace.hpSnapshot.incoming.stream().filter(read->!(read.direct instanceof HeavyStoneProjectile)).allMatch(read->read.target.equals(targets.get(4).getUUID())&&read.actor==null&&read.direct==null&&read.eventAmount==2),"the declared independently observed generic two-HP nested write is the only non-Heavy provenance; never count it as primary receipt");
                h.assertTrue(cd(sources.get(i))==315,"cancel/zero/native absorption/nested sections never refund or restart paid deadline lane="+i);
            }
            close(h,hpAfterHeavy(h,traces.get(0),targets.get(0)),primaryBefore[0],"actual primary HP stays at its measured baseline when real enemy Gel absorbs all seven-and-a-half");close(h,SkillEffects.shieldRemaining(targets.get(0)),4.5,"actual enemy Gel consumption, not HP/event amount, supplies the legitimate primary receipt");
            close(h,nearBefore[0]-hpAfterHeavy(h,traces.get(0),nearby.get(0)),1.5,"real shield-only primary triggers exactly one independent skill thirty-percent secondary");
            for(int i:new int[]{1,2,3,5,7}){close(h,hpAfterHeavy(h,traces.get(i),targets.get(i)),primaryBefore[i],"cancel/zero/native absorption/same-source recursion has zero actual primary HP loss lane="+i);close(h,hpAfterHeavy(h,traces.get(i),nearby.get(i)),nearBefore[i],"no exact positive primary receipt means no splash lane="+i);h.assertTrue(traces.get(i).damage.stream().noneMatch(read->read.target.equals(nearby.get(i).getUUID())),"no secondary damage callback from canceled/absorbed/reentrant primary lane="+i);}
            close(h,targets.get(3).getAbsorptionAmount(),85,"native absorption really consumed fifteen, but native absorption alone is not an enemy Gel or HP receipt");
            close(h,hpAfterHeavy(h,traces.get(4),targets.get(4)),primaryBefore[4]-2,"only declared nested generic two HP was actually written");close(h,hpAfterHeavy(h,traces.get(4),nearby.get(4)),nearBefore[4],"unrelated nested HP loss cannot impersonate outer-zero receipt or start splash");
            close(h,primaryBefore[6]-hpAfterHeavy(h,traces.get(6),targets.get(6)),15,"rejected different-source recursion does not revoke legitimate original outer primary");close(h,nearBefore[6]-hpAfterHeavy(h,traces.get(6),nearby.get(6)),3,"one original receipt still owns exactly its one legal secondary");
            for(int i:new int[]{1,2,4,5,6,7})h.assertTrue(fired[i],"actual Forge cancellation/reentry callback ran lane="+i);
            var victim=targets.get(6);float before=victim.getHealth();int events=traces.get(6).incoming.size();victim.invulnerableTime=0;
            h.assertTrue(spentSource[0]!=null&&!victim.hurt(spentSource[0],100)&&victim.getHealth()==before&&traces.get(6).incoming.size()==events,"reusing exact already-spent real hit source with fresh native i-frames cannot replay damage or splash");
            bank(h);pass("EXACT_HEALTH_OR_ENEMY_GEL_RECEIPT_CANCEL_ZERO_ABSORPTION_REENTRY");
        }).thenSucceed();
    }

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=180)
    public static void trueDefenseAndMarkCastsPreserveRangedArmorAndIndependentCurrentCriticalRolls(GameTestHelper h) {
        room(h);boundaryWalls(h,true);
        List<StoneSpiritThrowerUnit> sources=new ArrayList<>();List<LivingEntity> targets=new ArrayList<>(),nearby=new ArrayList<>();List<Trace> traces=new ArrayList<>();List<Entity> all=new ArrayList<>();
        for(int i=0;i<6;i++){double x=i<5?4.5:19.5,z=3.5+6*(i%5);var source=stone(h,"Defense"+i,x,z);damage(source,10);var target=jelly(h,"DefenseEnemy"+i,x+8,z);LivingEntity near;
            if(i==0){var beetle=support(h,DreamseekersMod.BEETLE.get().create(h.getLevel()),"DefenseEnemy0",x+8.9,z+.5);beetle.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);beetle.setHealth(1000);near=beetle;}else near=jelly(h,"DefenseEnemy"+i,x+8.9,z+.5);
            sources.add(source);targets.add(target);nearby.add(near);traces.add(watch(source,target,near));all.add(source);all.add(target);all.add(near);
        }
        var snail=support(h,DreamseekersMod.BASALT_SNAIL.get().create(h.getLevel()),"DefenseEnemy0",14.7,3.5);all.add(snail);
        var markPrimary=support(h,DreamseekersMod.BONE_WING_UNIT.get().create(h.getLevel()),"Defense4",7.5,28.7);var markSecondary=support(h,DreamseekersMod.BONE_WING_UNIT.get().create(h.getLevel()),"Defense4",7.5,26.3);var markControl=support(h,DreamseekersMod.BONE_WING_UNIT.get().create(h.getLevel()),"Defense5",22.5,4.7);all.addAll(List.of(markPrimary,markSecondary,markControl));
        var mutation=mutations();boolean[] critChanged={false};long[] at={0};double[] ordinaryTravel={0};
        float[] primaryBefore=new float[6],nearBefore=new float[6];
        var seq=controlledReady(h,"defense",sources,targets,nearby,traces,primaryBefore,nearBefore,all)
          .thenWaitUntil(()->h.assertTrue(targets.get(5).invulnerableTime==0,"ordinary control waits for its old native damage immunity to expire naturally before real buffs and new factory shot"))
          .thenExecute(()->{
            require(h,otherSkill(h,snail,snail,UnitSkill.SNAIL_SHELL_FORMATION),SkillResult.SUCCESS,"real nearby Snail formation, not a direct defensive buff injection");
            h.assertTrue(StoneHeavyShot.RANGE==10&&SnailShellFormation.active(snail)&&snail.distanceToSqr(targets.get(0))<=9&&snail.distanceToSqr(nearby.get(0))<=9,"actual active formation covers both primary and secondary bodies");close(h,BeetleGuard.reduction(nearby.get(0)),.1,"real same-owner nearby combat snail enables independent native Beetle guard ten");
            for(var target:List.of(targets.get(1),nearby.get(1)))target.getAttribute(AttributeRegistrar.RANGED_DAMAGE_RESIST.get()).setBaseValue(.5);
            sources.get(3).getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(1);
            mutation.hurts.put(targets.get(3).getUUID(),event->{critChanged[0]=true;close(h,event.getAmount(),45,"primary's real current 100-percent roll already applied original native three-times critical");sources.get(3).getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);});
            require(h,otherSkill(h,markPrimary,targets.get(4),UnitSkill.COORDINATED_MARK),SkillResult.SUCCESS,"real Bone cast marks heavy primary");require(h,otherSkill(h,markSecondary,nearby.get(4),UnitSkill.COORDINATED_MARK),SkillResult.SUCCESS,"separate real Bone cast marks heavy secondary");require(h,otherSkill(h,markControl,targets.get(5),UnitSkill.COORDINATED_MARK),SkillResult.SUCCESS,"same actual mark mechanism for original ordinary Stone control");
            for(int i:new int[]{4,5})h.assertTrue(CoordinatedMark.ticksRemaining(targets.get(i),sources.get(i).getOwnerName())==120,"genuine owner-scoped mark exists before physical shot lane="+i);
            h.assertTrue(CoordinatedMark.ticksRemaining(nearby.get(4),sources.get(4).getOwnerName())==120,"secondary has its own genuinely cast mark, not an unmarked negative");
            at[0]=h.getLevel().getGameTime();for(int i=0;i<5;i++)require(h,cast(h,sources.get(i),targets.get(i)),SkillResult.SUCCESS,"real authenticated defense/critical heavy cast lane="+i);
            sources.get(5).performUnitRangedAttack(targets.get(5),1);
            h.assertTrue(traces.get(5).shots.size()==1&&traces.get(5).shots.get(0) instanceof StoneProjectile&&cd(sources.get(5))==0,"control really uses unchanged ordinary Stone factory, not a Heavy or paid skill grant");
        });
        for(int tick=1;tick<=19;tick++){final int elapsed=tick;seq.thenIdle(1).thenExecute(()->{
            for(int i=0;i<5;i++)beforeRelease(h,traces.get(i),targets.get(i),primaryBefore[i],at[0],elapsed);
            for(var shot:traces.get(5).shots)ordinaryTravel[0]=Math.max(ordinaryTravel[0],shot.position().distanceTo(traces.get(5).starts.get(shot)));
        });}
        seq.thenIdle(2).thenExecute(()->{
            for(int i=0;i<5;i++)releasedAtTwenty(h,traces.get(i),at[0]);
            close(h,sources.get(2).getCriticalChance(),0,"released critical-control shot really launched at zero chance");sources.get(2).getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(1);
            close(h,sources.get(2).getCriticalChance(),1,"actual in-flight change makes current hit chance 100 percent; chance is not the release AD snapshot");
        }).thenIdle(24).thenExecute(()->{
            double[] primaryLoss={12,7.5,45,45,15},secondaryLoss={2.7,1.5,9,3,3};
            for(int i=0;i<5;i++){flight(h,traces.get(i),targets.get(i));close(h,primaryBefore[i]-hpAfterHeavy(h,traces.get(i),targets.get(i)),primaryLoss[i],"actual primary HP honors only its legitimate defenses/current native roll lane="+i);close(h,nearBefore[i]-hpAfterHeavy(h,traces.get(i),nearby.get(i)),secondaryLoss[i],"actual secondary retains thrown rangedArmor/Guard/current critical, without borrowing Primary bridge or Bone ordinary authorization lane="+i);h.assertTrue(cd(sources.get(i))==315,"defense/current chance/mark cannot rewrite paid Heavy deadline lane="+i);}
            h.assertTrue(SnailShellFormation.active(snail)&&critChanged[0]&&sources.get(3).getCriticalChance()==0,"real defense remains active, and actual primary callback changed secondary's current chance");
            var ordinary=traces.get(5);var shot=ordinary.shots.get(0);
            h.assertTrue(ordinary.shots.size()==1&&shot instanceof StoneProjectile&&shot.isRemoved()&&ordinaryTravel[0]>2&&ordinary.incoming.size()==1&&ordinary.incoming.get(0).actor==sources.get(5)&&ordinary.incoming.get(0).direct==shot,"unchanged ordinary control physically travelled and uniquely caused its real HP write, no other incoming contamination");
            close(h,primaryBefore[5]-targets.get(5).getHealth(),11.2,"real Bone mark positively enhances ordinary base ten by twelve percent");close(h,nearby.get(5).getHealth(),nearBefore[5],"ordinary control does not gain Heavy splash");
            h.assertTrue(CoordinatedMark.ticksRemaining(targets.get(4),sources.get(4).getOwnerName())>0&&CoordinatedMark.ticksRemaining(nearby.get(4),sources.get(4).getOwnerName())>0,"Heavy excluded live real marks rather than silently clearing them");
            bank(h);pass("REAL_SNAIL_PRIMARY_ONLY_GUARD_RANGED_ARMOR_CURRENT_CRIT_BONE_EXCLUSION");
        }).thenSucceed();
    }

    /** Prebuilt controlled-fixture admission for #6/#7/#8 only; the natural-AI first five are unchanged. */
    private static GameTestSequence controlledReady(GameTestHelper h,String suite,List<StoneSpiritThrowerUnit> sources,
            List<? extends LivingEntity> primary,List<? extends LivingEntity> nearby,List<Trace> traces,
            float[] primaryBefore,float[] nearBefore,List<Entity> all) {
        List<Trace> setupTraces=List.copyOf(traces); // Retain complete original objects/lists; never clear their shots or incoming damage.
        return ready(h,all.toArray(Entity[]::new)).thenExecute(()->{
            for(var source:sources)source.setNoAi(true); // Only after actual natural grounded readiness; Heavy remains enabled.
        }).thenWaitUntil(()->h.assertTrue(setupTraces.stream().flatMap(trace->trace.shots.stream()).allMatch(Entity::isRemoved),
                "previously observed setup ordinary projectiles must finish naturally; no discard, HP repair or extra timeout"))
          .thenExecute(()->{
            for(int i=0;i<sources.size();i++){
                primaryBefore[i]=primary.get(i).getHealth();nearBefore[i]=nearby.get(i).getHealth();
                h.assertTrue(primaryBefore[i]>0&&nearBefore[i]>0,"actual surviving bodies define the skill-only HP baseline after setup flight suite="+suite+" lane="+i);
                traces.set(i,beginControlledSkillObservation(h,setupTraces.get(i),suite,i,primaryBefore[i],nearBefore[i]));
            }
        });
    }

    /** Preserve complete setup evidence, then observe a new actual cast window without clearing any history. */
    private static Trace beginControlledSkillObservation(GameTestHelper h,Trace setup,String suite,int lane,float primaryHp,float nearbyHp) {
        h.assertTrue(PROBE.traces.get(setup.source.getUUID())==setup&&heavyShots(setup).isEmpty()
                &&setup.shots.stream().allMatch(Entity::isRemoved),"uncast setup observer contains no Heavy and every old ordinary actually terminated before skill-window admission lane="+lane);
        System.out.println("STONE_HEAVY_CONTROLLED_SETUP_HISTORY suite="+suite+" lane="+lane+" source="+setup.source.getUUID()
                +" gameTime="+h.getLevel().getGameTime()+" actualPrimaryHP="+primaryHp+" actualNearbyHP="+nearbyHp
                +" setupShots="+setup.shots.stream().map(shot->EntityType.getKey(shot.getType())+"@"+shot.getUUID()+" join="+setup.joinTimes.get(shot)+" removed="+shot.isRemoved()).toList()
                +" setupIncoming="+setup.incoming+" sourceRaw="+sourceShotRaw(setup.source));
        var window=new Trace(setup.source,setup.observed.values().toArray(LivingEntity[]::new));
        PROBE.traces.put(setup.source.getUUID(),window); // setupTraces still owns the original unmodified lists and records.
        return window;
    }

    private static void beforeRelease(GameTestHelper h,Trace trace,LivingEntity target,float hp,long at,int elapsed) {
        observe(trace);
        h.assertTrue(h.getLevel().getGameTime()-at==elapsed,"prebuilt observation uses exact authoritative gameTime offset "+elapsed);
        h.assertTrue(StoneHeavyShot.active(trace.source)&&StoneHeavyShot.remainingTicks(trace.source)==20-elapsed&&cd(trace.source)==360-elapsed,"twenty-tick reservation and original absolute clock advance together");
        h.assertTrue(trace.shots.isEmpty()&&target.getHealth()==hp,"neither ordinary nor heavy projectile/damage may leak before release, elapsed="+elapsed);
    }
    private static HeavyStoneProjectile releasedAtTwenty(GameTestHelper h,Trace trace,long at) {
        observe(trace);h.assertTrue(trace.shots.size()==1&&trace.shots.get(0) instanceof HeavyStoneProjectile,"exactly one genuine registered heavy factory projectile, never an ordinary substitute");
        var shot=(HeavyStoneProjectile)trace.shots.get(0);
        h.assertTrue(trace.joinTimes.get(shot)==at+20,"actual EntityJoin gameTime is precisely cast+20, independent of same-tick GameTest observation order: "+trace.joinTimes.get(shot)+" vs "+at);
        h.assertTrue(shot.getOwner()==trace.source&&h.getLevel().getEntity(shot.getId())==shot&&h.getLevel().getEntity(shot.getUUID())==shot&&!shot.noPhysics&&!shot.isOnFire(),"real heavy shot is published, owned, colliding and not native fire");
        h.assertTrue(!StoneHeavyShot.active(trace.source)&&cd(trace.source)==339,"release clears only short windup, never paid cooldown");return shot;
    }
    private static List<HeavyStoneProjectile> heavyShots(Trace trace) {return trace.shots.stream().filter(HeavyStoneProjectile.class::isInstance).map(HeavyStoneProjectile.class::cast).toList();}
    private static void observe(Trace trace) {for(var shot:heavyShots(trace))trace.travel=Math.max(trace.travel,shot.position().distanceTo(trace.starts.get(shot)));}
    private static void flight(GameTestHelper h,Trace trace,LivingEntity primary) {
        shotTimelineRaw(h,trace,primary); // observation before the unchanged first flight assertion
        observe(trace);var heavy=heavyShots(trace);h.assertTrue(heavy.size()==1,"one real heavy factory shot; later ordinary shots retain their own type");
        var shot=heavy.get(0);
        h.assertTrue(trace.impacts.size()==1&&trace.impacts.get(0).type==HitResult.Type.ENTITY&&primary.getUUID().equals(trace.impacts.get(0).victim),"actual first physical collision is the expected enemy body");
        h.assertTrue(shot.isRemoved()&&trace.travel>2&&!trace.damage.isEmpty(),"natural travel precedes real native damage and final projectile consumption");
        h.assertTrue(trace.damage.stream().allMatch(read->read.direct==shot&&read.published),"all real damage callbacks name this same still-published projectile, not a proxy or forged source");
        var snapshot=trace.hpSnapshot;
        h.assertTrue(snapshot!=null&&snapshot.projectile.equals(shot.getUUID())&&snapshot.gameTime==trace.impactAt
                &&snapshot.gameTime>=trace.joinTimes.get(shot)&&snapshot.gameTime<=h.getLevel().getGameTime(),"actual HP comes from the first same-world Level END after this Heavy completed, before the unchanged original assertion time");
        h.assertTrue(snapshot.incoming.size()==trace.damage.size()&&snapshot.incoming.stream().allMatch(read->read.actor==trace.source&&read.direct==shot),"every observed victim's incoming damage up to the HP snapshot must belong to this exact Heavy; any non-Heavy contamination fails attribution: "+snapshot.incoming);
        System.out.println("STONE_HEAVY_FLIGHT source="+trace.source.getUUID()+" projectile="+shot.getUUID()+" impact="+trace.impacts.get(0).point+" travel="+trace.travel+" hits="+trace.damage);
    }
    private static void shotTimelineRaw(GameTestHelper h,Trace trace,LivingEntity primary) {
        System.out.println("STONE_HEAVY_SHOTS_BEFORE_FLIGHT_RAW testTick="+h.getTick()+" gameTime="+h.getLevel().getGameTime()
                +" count="+trace.shots.size()+" sourceState="+sourceShotRaw(trace.source)
                +" primary="+primary.getUUID()+" primaryHP="+primary.getHealth()+" primaryPos="+primary.position()
                +" impacts="+trace.impacts+" heavyDamage="+trace.damage+" hpSnapshot="+trace.hpSnapshot+" allIncoming="+trace.incoming);
        for(var shot:trace.shots)System.out.println("STONE_HEAVY_SHOT_STATE_RAW type="+EntityType.getKey(shot.getType())
                +" uuid="+shot.getUUID()+" id="+shot.getId()+" joinGameTime="+trace.joinTimes.get(shot)
                +" owner="+(shot.getOwner()==null?"null":shot.getOwner().getUUID())+" pos="+shot.position()
                +" velocity="+shot.getDeltaMovement()+" entityTick="+shot.tickCount+" removed="+shot.isRemoved()
                +" publishedId="+(h.getLevel().getEntity(shot.getId())==shot)+" publishedUuid="+(h.getLevel().getEntity(shot.getUUID())==shot));
    }
    private static String sourceShotRaw(StoneSpiritThrowerUnit source) {
        return "uuid="+source.getUUID()+",entityTick="+source.tickCount+",pos="+source.position()+",noAI="+source.isNoAi()
                +",target="+rawShotField(source.getTargetGoal(),"target")+",forced="+source.getTargetGoal().forced
                +",rawAggro="+source.getAttributeValue(AttributeRegistrar.AGGRO_RANGE.get())
                +",actualAggro="+source.getAggroRange()+",attackRange="+source.getAttackRange()
                +",ordinaryCD="+((UnitRangedAttackGoal<?>)source.getAttackGoal()).getAttackCooldown()
                +",ordinaryWindup="+rawShotField(source.getAttackGoal(),"attackWindupTicksLeft")
                +",commandRevision="+source.rts().commandRevision+",rawHold="+source.rts().hold
                +",move="+source.getMoveGoal().getMoveTarget()+",attackMove="+source.rts().attackMove
                +",follow="+(source.rts().follow==null?"null":source.rts().follow.getUUID())
                +",pending="+UnitServerEvents.hasPendingImmediateAction(source.getId())
                +",rawLastDamage="+rawShotField(source,"lastDamageSource");
    }
    /** Raw field reads only. In particular do not call getLastDamageSource(), which may expire its cache. */
    private static String rawShotField(Object object,String name) {
        for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try {
            var field=type.getDeclaredField(name);field.setAccessible(true);Object value=field.get(object);
            return value instanceof Entity entity?EntityType.getKey(entity.getType())+"@"+entity.getUUID():String.valueOf(value);
        } catch(NoSuchFieldException absentHere) { }
        catch(ReflectiveOperationException|RuntimeException unavailable){return "unreadable:"+unavailable.getClass().getSimpleName();}
        return "missing";
    }
    private static float hpAfterHeavy(GameTestHelper h,Trace trace,LivingEntity target) {
        h.assertTrue(trace.hpSnapshot!=null&&trace.hpSnapshot.health.containsKey(target.getUUID()),"actual completed-Heavy HP snapshot includes the declared target: "+target.getUUID());
        return trace.hpSnapshot.health.get(target.getUUID()); // actual getHealth at END, never Forge event amount
    }
    private record IncomingRead(UUID target,long gameTime,Entity actor,Entity direct,float eventAmount) { }
    private record HpSnapshot(long gameTime,UUID projectile,Map<UUID,Float> health,List<IncomingRead> incoming) { }
    private record DamageRead(UUID target,float amount,Entity direct,boolean published) { }
    private record ImpactRead(HitResult.Type type,UUID victim,Vec3 point,List<UUID> ordered,Map<UUID,Double> distanceSq,double friendlyDistanceSq,double outsideDistanceSq) { }
    private static final class Trace {
        final StoneSpiritThrowerUnit source;final List<Projectile> shots=new ArrayList<>();
        final Map<Projectile,Long> joinTimes=new IdentityHashMap<>();final Map<Projectile,Vec3> starts=new IdentityHashMap<>();
        final List<ImpactRead> impacts=new ArrayList<>();final List<DamageRead> damage=new ArrayList<>();
        final Map<UUID,LivingEntity> observed=new LinkedHashMap<>();final List<IncomingRead> incoming=new ArrayList<>();
        List<? extends LivingEntity> candidates=List.of();LivingEntity friendly,outside;double travel;long impactAt=Long.MIN_VALUE;HpSnapshot hpSnapshot;
        Trace(StoneSpiritThrowerUnit source,LivingEntity...targets){this.source=source;for(var target:targets)observed.put(target.getUUID(),target);}
    }
    private static Trace watch(StoneSpiritThrowerUnit source,LivingEntity...targets) {
        if(!listening){MinecraftForge.EVENT_BUS.register(PROBE);listening=true;}
        var trace=new Trace(source,targets);if(PROBE.traces.put(source.getUUID(),trace)!=null)throw new IllegalStateException("duplicate source observer");return trace;
    }
    private static final class Probe {
        final Map<UUID,Trace> traces=new HashMap<>();
        @SubscribeEvent(priority=EventPriority.LOWEST) public void joined(EntityJoinLevelEvent event) {
            if(event.getLevel().isClientSide()||!(event.getEntity() instanceof Projectile shot)||shot.getOwner()==null)return;
            var trace=traces.get(shot.getOwner().getUUID());if(trace==null)return;
            trace.shots.add(shot);trace.joinTimes.put(shot,event.getLevel().getGameTime());trace.starts.put(shot,shot.position());
            System.out.println("STONE_HEAVY_SHOT_JOIN_RAW gameTime="+event.getLevel().getGameTime()
                    +" type="+EntityType.getKey(shot.getType())+" uuid="+shot.getUUID()+" id="+shot.getId()
                    +" owner="+shot.getOwner().getUUID()+" pos="+shot.position()+" removed="+shot.isRemoved()
                    +" count="+trace.shots.size()+" sourceState="+sourceShotRaw(trace.source));
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void impact(ProjectileImpactEvent event) {
            var shot=event.getProjectile();if(!(shot instanceof HeavyStoneProjectile)||shot.getOwner()==null)return;
            var trace=traces.get(shot.getOwner().getUUID());if(trace==null)return;var hit=event.getRayTraceResult();var point=hit.getLocation();
            trace.impactAt=shot.level().getGameTime();
            Map<UUID,Double> distances=new LinkedHashMap<>();for(var candidate:trace.candidates)distances.put(candidate.getUUID(),candidate.getBoundingBox().getCenter().distanceToSqr(point));
            var ordered=trace.candidates.stream().sorted(Comparator.comparingDouble((LivingEntity e)->e.getBoundingBox().getCenter().distanceToSqr(point)).thenComparing(e->e.getUUID().toString())).map(Entity::getUUID).toList();
            trace.impacts.add(new ImpactRead(hit.getType(),hit instanceof EntityHitResult entity?entity.getEntity().getUUID():null,point,ordered,distances,
                    trace.friendly==null?Double.NaN:trace.friendly.getBoundingBox().getCenter().distanceToSqr(point),trace.outside==null?Double.NaN:trace.outside.getBoundingBox().getCenter().distanceToSqr(point)));
            trace.travel=Math.max(trace.travel,point.distanceTo(trace.starts.get(shot)));
        }
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void damage(LivingDamageEvent event) {
            // Observe EVERY incoming source for declared victims, before filtering the Heavy proof.
            // This event is pre-health-write; it records provenance only, never substitutes eventAmount for HP.
            for(var observed:traces.values())if(observed.observed.get(event.getEntity().getUUID())==event.getEntity())
                observed.incoming.add(new IncomingRead(event.getEntity().getUUID(),event.getEntity().level().getGameTime(),event.getSource().getEntity(),event.getSource().getDirectEntity(),event.getAmount()));
            var source=event.getSource().getEntity();if(source==null||!(event.getSource().getDirectEntity() instanceof HeavyStoneProjectile shot))return;
            var trace=traces.get(source.getUUID());if(trace==null)return;
            var level=(ServerLevel)event.getEntity().level();
            trace.damage.add(new DamageRead(event.getEntity().getUUID(),event.getAmount(),shot,level.getEntity(shot.getId())==shot&&level.getEntity(shot.getUUID())==shot&&!shot.isRemoved()));
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void levelEnd(TickEvent.LevelTickEvent event) {
            if(event.phase!=TickEvent.Phase.END||event.level.isClientSide())return;
            for(var trace:traces.values()) {
                if(trace.source.level()!=event.level||trace.hpSnapshot!=null||trace.impacts.isEmpty())continue;
                var heavy=heavyShots(trace);if(heavy.size()!=1||!heavy.get(0).isRemoved())continue;
                Map<UUID,Float> health=new LinkedHashMap<>();
                for(var target:trace.observed.values())health.put(target.getUUID(),target.getHealth());
                trace.hpSnapshot=new HpSnapshot(event.level.getGameTime(),heavy.get(0).getUUID(),Map.copyOf(health),List.copyOf(trace.incoming));
                System.out.println("STONE_HEAVY_END_HP_SNAPSHOT source="+trace.source.getUUID()+" snapshot="+trace.hpSnapshot);
            }
        }
    }

    /** Pure command-boundary observation: no goal helpers, cache-expiring damage getters or path creation. */
    private static void commandBoundaryRaw(GameTestHelper h,Trace trace,long capturedRevision,long castAt,String point) {
        var source=trace.source;
        System.out.println("STONE_HEAVY_COMMAND_BOUNDARY_RAW point="+point+" testTick="+h.getTick()+" gameTime="+h.getLevel().getGameTime()
                +" elapsed="+(h.getLevel().getGameTime()-castAt)+" source="+source.getUUID()
                +" active="+StoneHeavyShot.active(source)+" cd="+cd(source)+" capturedRevision="+capturedRevision+" actualRevision="+source.rts().commandRevision
                +" heavy="+heavyShots(trace).stream().map(shot->shot.getUUID()+"@join="+trace.joinTimes.get(shot)+",removed="+shot.isRemoved()+",pos="+shot.position()).toList()
                +" queuedMove="+queuedMove(source)+" sourceRaw="+sourceShotRaw(source));
    }

    private static final List<BoundaryProbe> BOUNDARIES=new ArrayList<>();
    private static final class BoundaryProbe {
        final GameTestHelper h;LivingEntity lastTicking;long lastTickTime=Long.MIN_VALUE;
        StoneSpiritThrowerUnit constructingSource;Entity constructed;
        java.util.function.Consumer<net.minecraftforge.event.entity.EntityEvent.EntityConstructing> constructing;
        final Map<UUID,java.util.function.Consumer<EntityJoinLevelEvent>> joins=new HashMap<>();
        final Map<Integer,HeavyStoneProjectile> joinedShots=new HashMap<>();
        final Map<UUID,java.util.function.Consumer<net.minecraftforge.event.entity.living.LivingHurtEvent>> hurts=new HashMap<>();
        BoundaryProbe(GameTestHelper h){this.h=h;}
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void livingTick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
            if(event.getEntity().level()==h.getLevel()){lastTicking=event.getEntity();lastTickTime=h.getLevel().getGameTime();}
        }
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void constructing(net.minecraftforge.event.entity.EntityEvent.EntityConstructing event) {
            if(!(event.getEntity() instanceof HeavyStoneProjectile)||event.getEntity().level()!=h.getLevel()
                    ||constructing==null||lastTicking!=constructingSource||lastTickTime!=h.getLevel().getGameTime())return;
            var callback=constructing;constructing=null;callback.accept(event);
        }
        @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true) public void joined(EntityJoinLevelEvent event) {
            if(event.getLevel()!=h.getLevel()||!(event.getEntity() instanceof HeavyStoneProjectile shot)||shot.getOwner()==null)return;
            var callback=joins.remove(shot.getOwner().getUUID());if(callback!=null)callback.accept(event);
        }
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void hurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
            if(!(event.getSource().getDirectEntity() instanceof HeavyStoneProjectile))return;
            var callback=hurts.remove(event.getEntity().getUUID());if(callback!=null)callback.accept(event);
        }
    }
    private static boolean queuedMove(StoneSpiritThrowerUnit source){return UnitServerEvents.getUnitActionSlowQueue().stream().anyMatch(item->item.getAction()==UnitAction.MOVE&&Arrays.stream(item.getUnitIds()).anyMatch(id->id==source.getId()));}
    private static double horizontalSquared(Vec3 a,Vec3 b){return (a.x-b.x)*(a.x-b.x)+(a.z-b.z)*(a.z-b.z);}
    private static void command(GameTestHelper h,StoneSpiritThrowerUnit source,UnitAction action,int targetId,BlockPos position,boolean shift) {
        try {var sender=player(h,source.getOwnerName());var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);connection.setListener(sender.connection);var constructor=net.minecraftforge.network.NetworkEvent.Context.class.getDeclaredConstructor(net.minecraft.network.Connection.class,net.minecraftforge.network.NetworkDirection.class,int.class);constructor.setAccessible(true);var context=constructor.newInstance(connection,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER,-1);
            h.assertTrue(context.getSender()==sender,"actual Forge command boundary resolves authenticated owner");
            h.assertTrue(new com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket(source.getOwnerName(),action,targetId,new int[]{source.getId()},position,BlockPos.ZERO,shift).handle(()->context),"real authenticated boundary command accepted: "+action+" shift="+shift);
        }catch(ReflectiveOperationException failure){throw new RuntimeException("real existing Forge boundary command context",failure);}
    }
    private static void boundaryWalls(GameTestHelper h,boolean grid) {
        // Local fixture-only partitions prevent unrelated lane acquisition/splash; all writes fit the original 32x10x32 template.
        for(int z=grid?6:5;z<31;z+=grid?6:4)for(int x=1;x<31;x++)for(int y=1;y<9;y++)h.getLevel().setBlock(h.absolutePos(new BlockPos(x,y,z)),Blocks.STONE.defaultBlockState(),2);
        if(grid)for(int z=1;z<31;z++)for(int y=1;y<9;y++)h.getLevel().setBlock(h.absolutePos(new BlockPos(16,y,z)),Blocks.STONE.defaultBlockState(),2);
    }

    private static final List<MutationProbe> MUTATIONS=new ArrayList<>();
    private static final class MutationProbe {
        final Map<UUID,java.util.function.Consumer<net.minecraftforge.event.entity.living.LivingHurtEvent>> hurts=new HashMap<>();
        final Map<UUID,java.util.function.Consumer<LivingDamageEvent>> damages=new HashMap<>();
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void hurt(net.minecraftforge.event.entity.living.LivingHurtEvent event){if(!(event.getSource().getDirectEntity() instanceof HeavyStoneProjectile))return;var callback=hurts.remove(event.getEntity().getUUID());if(callback!=null)callback.accept(event);}
        @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=true) public void damage(LivingDamageEvent event){if(!(event.getSource().getDirectEntity() instanceof HeavyStoneProjectile))return;var callback=damages.remove(event.getEntity().getUUID());if(callback!=null)callback.accept(event);}
    }
    private static MutationProbe mutations(){var probe=new MutationProbe();MUTATIONS.add(probe);MinecraftForge.EVENT_BUS.register(probe);return probe;}
    private static <T extends Mob> T support(GameTestHelper h,T body,String owner,double x,double z){owned(h,body,owner,x,z,true);neutral(body);return body;}
    private static SkillResult otherSkill(GameTestHelper h,SkillUnit source,LivingEntity target,UnitSkill skill){var body=source.mob();return new SkillNetwork.Cast(new SkillRequest(skill,body.getId(),body.getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID())).execute(player(h,source.getOwnerName()));}
    private static void completedReceiptFlight(GameTestHelper h,Trace trace,LivingEntity primary){shotTimelineRaw(h,trace,primary);observe(trace);var heavy=heavyShots(trace);h.assertTrue(trace.shots.size()==1&&heavy.size()==1&&heavy.get(0).isRemoved()&&trace.travel>2&&trace.impacts.size()==1&&primary.getUUID().equals(trace.impacts.get(0).victim),"real unique physical Heavy completed against actual primary before receipt assertions");h.assertTrue(trace.hpSnapshot!=null&&trace.hpSnapshot.projectile.equals(heavy.get(0).getUUID())&&trace.hpSnapshot.gameTime==trace.impactAt&&trace.hpSnapshot.gameTime<=h.getLevel().getGameTime(),"receipt expectations use actual first completed-Heavy Level END HP, never event amount as health");h.assertTrue(trace.damage.stream().allMatch(read->read.direct==heavy.get(0)&&read.published),"even canceled final events identify same real still-published Heavy during callbacks");}

    private static StoneSpiritThrowerUnit stone(GameTestHelper h,String owner,double x,double z) {var e=DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get().create(h.getLevel());owned(h,e,owner,x,z,false);damage(e,5);neutral(e);return e;}
    private static JellyfishUnit jelly(GameTestHelper h,String owner,double x,double z) {var e=DreamseekersMod.JELLYFISH_UNIT.get().create(h.getLevel());owned(h,e,owner,x,z,true);neutral(e);e.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);e.setHealth(1000);return e;}
    private static void owned(GameTestHelper h,Mob e,String suffix,double x,double z,boolean noAi) {
        h.assertTrue(e instanceof Unit,"registered genuine RTS fixture");((Unit)e).setOwnerName(account(suffix));e.setNoAi(noAi);
        var aggro=e.getAttribute(AttributeRegistrar.AGGRO_RANGE.get());if(aggro!=null)aggro.setBaseValue(0);place(h,e,x,z);player(h,suffix);
    }
    private static void place(GameTestHelper h,Entity entity,double x,double z) {var origin=h.absolutePos(BlockPos.ZERO);entity.moveTo(origin.getX()+x,origin.getY()+1,origin.getZ()+z,0,0);entity.setDeltaMovement(Vec3.ZERO);ENTITIES.add(entity);h.assertTrue(h.getLevel().addFreshEntity(entity),"genuine entity added before natural ready wait");}
    private static GameTestSequence ready(GameTestHelper h,Entity...entities) {
        return h.startSequence().thenWaitUntil(()->{
            // Observation only: keep the original readiness predicate and every existing deadline.
            if(h.getTick()==4||h.getTick()==20||h.getTick()==80||h.getTick()==160||h.getTick()==250||h.getTick()==500)
                for(Entity e:entities)if(e instanceof StoneSpiritThrowerUnit source)groundReadyRaw(h,source);
            for(Entity e:entities)h.assertTrue(e.tickCount>3&&h.getLevel().getEntity(e.getId())==e&&h.getLevel().getEntity(e.getUUID())==e,"actual natural ticks and ID/UUID publication: "+e.getUUID());
            for(Entity e:entities)if(e instanceof StoneSpiritThrowerUnit)h.assertTrue(e.onGround()&&e.verticalCollision&&Math.abs(e.getBoundingBox().minY-(h.absolutePos(BlockPos.ZERO).getY()+1))<.001,"actual grounded source, never fake setOnGround");
        }).thenExecute(()->{h.assertTrue(!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isEnabled(),"first four use actual fog-off environment, not fabricated visibility");bank(h);});
    }
    private static void groundReadyRaw(GameTestHelper h,StoneSpiritThrowerUnit source) {
        var level=h.getLevel();var box=source.getBoundingBox();
        var floor=BlockPos.containing(source.getX(),box.minY-.01,source.getZ());
        var chunk=level.getChunkSource().getChunkNow(floor.getX()>>4,floor.getZ()>>4);
        System.out.println("STONE_HEAVY_GROUND_READY_RAW testTick="+h.getTick()+" gameTime="+level.getGameTime()
                +" source="+source.getUUID()+" id="+source.getId()+" entityTick="+source.tickCount
                +" publishedId="+(level.getEntity(source.getId())==source)+" publishedUuid="+(level.getEntity(source.getUUID())==source)
                +" pos="+source.position()+" bboxMinY="+box.minY+" velocity="+source.getDeltaMovement()
                +" onGround="+source.onGround()+" noAI="+source.isNoAi()+" noGravity="+source.isNoGravity()
                +" horizontalCollision="+source.horizontalCollision+" verticalCollision="+source.verticalCollision
                +" floorPos="+floor+" floor="+(chunk==null?"unloaded":chunk.getBlockState(floor).toString()));
    }
    private static String account(String suffix) {String name=suffix.startsWith(PREFIX)?suffix:PREFIX+suffix;if(OWNERS.add(name)){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,123,234,345,456));}return name;}
    private static FakePlayer player(GameTestHelper h,String suffix) {String name=account(suffix);return PLAYERS.computeIfAbsent(name,key->{var player=new FakePlayer(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),key));player.connection=new SilentHandler(player);var origin=h.absolutePos(BlockPos.ZERO);player.moveTo(origin.getX()+2.5,origin.getY()+1,origin.getZ()+29.5);ENTITIES.add(player);h.getLevel().addNewPlayer(player);PlayerServerEvents.players.add(player);return player;});}
    private static SkillRequest request(StoneSpiritThrowerUnit source,Entity target) {return new SkillRequest(UnitSkill.STONE_HEAVY_SHOT,source.getId(),source.getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID());}
    private static SkillResult cast(GameTestHelper h,StoneSpiritThrowerUnit source,Entity target) {return new SkillNetwork.Cast(request(source,target)).execute(player(h,source.getOwnerName()));}
    private static int cd(StoneSpiritThrowerUnit source) {return source.rts().skills().cooldownTicks(UnitSkill.STONE_HEAVY_SHOT);}
    private static void order(GameTestHelper h,StoneSpiritThrowerUnit source,UnitAction action,LivingEntity target) {
        try {var sender=player(h,source.getOwnerName());var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);connection.setListener(sender.connection);var constructor=net.minecraftforge.network.NetworkEvent.Context.class.getDeclaredConstructor(net.minecraft.network.Connection.class,net.minecraftforge.network.NetworkDirection.class,int.class);constructor.setAccessible(true);var context=constructor.newInstance(connection,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER,-1);h.assertTrue(context.getSender()==sender,"actual Forge command context resolves exact sender");h.assertTrue(new com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket(source.getOwnerName(),action,target.getId(),new int[]{source.getId()},target.blockPosition(),BlockPos.ZERO,false).handle(()->context),"real authenticated ordinary ATTACK accepted");}
        catch(ReflectiveOperationException failure){throw new RuntimeException("exact existing Forge network context",failure);}
    }
    private static void damage(LivingEntity source,double amount) {source.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(amount);if(source.getAttribute(Attributes.ATTACK_DAMAGE)!=null)source.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(amount);var crit=source.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get());if(crit!=null)crit.setBaseValue(0);}
    private static void neutral(LivingEntity target) {target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);target.setAbsorptionAmount(0);for(var attribute:List.of(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(),AttributeRegistrar.RANGED_DAMAGE_RESIST.get()))if(target.getAttribute(attribute)!=null)target.getAttribute(attribute).setBaseValue(0);}
    private static void bank(GameTestHelper h) {for(String owner:OWNERS){var r=ResourcesServerEvents.resourcesList.stream().filter(value->value.ownerName.equals(owner)).findFirst().orElse(null);h.assertTrue(r!=null&&r.food==123&&r.wood==234&&r.ore==345&&r.emerald==456&&r.foodToAdd==0&&r.woodToAdd==0&&r.oreToAdd==0&&r.emeraldToAdd==0,"no skill currency, pending-resource mutation or silently recreated ledger: "+owner);}for(Entity e:ENTITIES)if(e instanceof Unit unit)h.assertTrue(unit.getItems().isEmpty(),"heavy skill never manufactures cargo");}
    private static void require(GameTestHelper h,SkillResult actual,SkillResult expected,String why) {h.assertTrue(actual==expected,why+" expected="+expected+" actual="+actual);}
    private static void close(GameTestHelper h,double actual,double expected,String why) {h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<.001,why+" expected="+expected+" actual="+actual);}
    private static void pass(String name) {System.out.println("DREAMSEEKERS_STONE_HEAVY_SHOT_PASS "+name);}
    private static AABB area(GameTestHelper h) {var p=h.absolutePos(BlockPos.ZERO);return new AABB(p,p.offset(32,10,32));}
    private static void wall(GameTestHelper h,net.minecraft.world.level.block.Block block) {for(int z=10;z<=14;z++)for(int y=1;y<=5;y++)h.getLevel().setBlock(h.absolutePos(new BlockPos(8,y,z)),block.defaultBlockState(),2);}
    private static void room(GameTestHelper h) {
        var p=h.absolutePos(BlockPos.ZERO);var level=h.getLevel();
        for(int x=p.getX()>>4;x<=(p.getX()+31)>>4;x++)for(int z=p.getZ()>>4;z<=(p.getZ()+31)>>4;z++){var chunk=new ChunkPos(x,z);if(!level.getForcedChunks().contains(chunk.toLong())){level.setChunkForced(x,z,true);TICKETS.add(chunk);}}
        for(int x=0;x<32;x++)for(int z=0;z<32;z++)level.setBlock(p.offset(x,9,z),Blocks.STONE.defaultBlockState(),2);
        level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(p.getX(),p.getY()+1,p.getZ(),p.getX()+32,level.getMaxBuildHeight(),p.getZ()+32)).forEach(Entity::discard);
        for(int x=0;x<32;x++)for(int z=0;z<32;z++){level.setBlock(p.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<9;y++)level.setBlock(p.offset(x,y,z),(x==0||x==31||z==0||z==31?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}
        level.getEntitiesOfClass(ItemEntity.class,area(h)).forEach(Entity::discard);
    }
    private static final class SilentHandler extends ServerGamePacketListenerImpl {SilentHandler(ServerPlayer player){super(player.serverLevel().getServer(),new SilentConnection(),player);}@Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> packet){}@Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){}}
    private static final class SilentConnection extends net.minecraft.network.Connection {SilentConnection(){super(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);}@Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> packet){}@Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){}}
}
