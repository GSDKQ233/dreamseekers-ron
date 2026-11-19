// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.*;
import com.dreamseekers.shadow.EmberLandingProjectile;
import com.dreamseekers.unit.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;

/** True normal-save/two-JVM boundary. Six original bodies, one actual already-landed transient
 * zone and another genuinely moving, normally saved skill projectile. Neither carrier nor zone
 * is rebuilt from NBT by this fixture. Loaded native NoAI=false is observed before containment.
 * Declared ordinary range2/aggro0 isolates native startup attacks; skill range8 is unchanged.
 * No world/load hook, manual collision, pulse tick, hurt, iframe/CD rewrite or slowed projectile.
 */
@GameTestHolder("dreamseekers_ash_ember_landing_skill_restart")
@PrefixGameTestTemplate(false)
public final class AshEmberLandingSkillRestartGameTests {
    private static final String OWNER="EmberRestart_Owner",ENEMY="EmberRestart_Enemy";
    private static final BlockPos ORIGIN=new BlockPos(106496,90,106496),GROUND=ORIGIN.offset(24,1,10);
    private static final File MANIFEST=new File("ash-ember-landing-restart-expectations.nbt");
    private static final UUID MS_TECH=UUID.fromString("7a183715-d531-4986-b519-0a675633074e");
    private static final List<String> KEYS=List.of("zoneCaster","flightCaster","unusedCaster","oldTarget","ownerReserve","enemyReserve");
    private static final Map<String,FakePlayer> PLAYERS=new HashMap<>();

    @GameTest(template="unit_isolation_arena",batch="dreamseekers_ash_ember_landing_skill_restart",timeoutTicks=1450)
    public static void originalBodiesPaidClocksAndMovingProjectileSaveButFlightZoneNeverReplayAndFreshFivePulsesWork(GameTestHelper h) throws Exception {
        switch(System.getProperty("dreamseekers.ashEmberLanding.test.phase","")) {
            case "prepare" -> prepare(h);
            case "verify" -> verify(h);
            default -> throw new IllegalArgumentException("Unknown ash-ember-landing restart phase");
        }
    }

    private static void prepare(GameTestHelper h) {
        var level=h.getLevel();
        h.assertTrue(!MANIFEST.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null&&PlayerServerEvents.getRTSPlayer(ENEMY)==null,"fresh isolated normal-save fixture, never reuse consumed verify state");
        var expected=new CompoundTag();expected.putInt("format",1);
        expected.putLong("prepareJvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        List<Long> forced=new ArrayList<>();
        for(int x=ORIGIN.getX()>>4;x<=(ORIGIN.getX()+31)>>4;x++)for(int z=ORIGIN.getZ()>>4;z<=(ORIGIN.getZ()+31)>>4;z++) {
            long key=ChunkPos.asLong(x,z);h.assertTrue(!level.getForcedChunks().contains(key),"only new fixture chunk tickets");
            level.setChunkForced(x,z,true);forced.add(key);
        }
        expected.putLongArray("forcedChunks",forced);arena(level);
        for(String name:List.of(OWNER,ENEMY)) {
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));
            ResourcesServerEvents.resourcesList.add(new Resources(name,123,234,345,456));player(level,name);
        }
        Map<String,Mob> bodies=new LinkedHashMap<>();
        double[][] homes={{17.5,10.5},{24.5,2.5},{18.5,13.5}};
        for(int i=0;i<3;i++) {
            var body=DreamseekersMod.ASH_BONE_WING_UNIT.get().create(level);
            owned(h,body,OWNER,homes[i][0],homes[i][1],9101+i);bodies.put(KEYS.get(i),body);
        }
        var target=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(level);
        owned(h,target,ENEMY,25.5,10.5,9104);durable(target);bodies.put("oldTarget",target);
        for(int i=0;i<2;i++) {
            var reserve=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(level);
            owned(h,reserve,i==0?OWNER:ENEMY,i==0?4.5:24.5,i==0?5.5:25.5,9105+i);
            bodies.put(i==0?"ownerReserve":"enemyReserve",reserve);
        }
        for(var entry:bodies.entrySet())expected.putUUID(entry.getKey(),entry.getValue().getUUID());
        var zoneCaster=(AshBoneWingUnit)bodies.get("zoneCaster");var flightCaster=(AshBoneWingUnit)bodies.get("flightCaster");
        var unused=(AshBoneWingUnit)bodies.get("unusedCaster");EmberLandingProjectile[] first={null},moving={null};
        h.startSequence().thenWaitUntil(()-> {
            for(var body:bodies.values())published(h,body);for(var p:PLAYERS.values())published(h,p);
        }).thenExecute(()-> {
            h.assertTrue(!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isEnabled(),"actual isolated world uses fog off, not fabricated visibility");
            h.assertTrue(AshEmberLanding.zones(level).isEmpty(),"no previous transient zones in isolated world");ledger(h);
            expected.putLong("zoneCastGameTime",level.getGameTime());
            h.assertTrue(cast(level,zoneCaster)==SkillResult.SUCCESS&&cd(zoneCaster)==440&&cd(flightCaster)==0&&cd(unused)==0,"actual ground cast publishes a real new skill projectile and only then pays440");
            first[0]=onlyFlight(h,zoneCaster);expected.putUUID("oldZoneId",first[0].getUUID());
            h.assertTrue(AshEmberLanding.zones(level).isEmpty(),"publication is not an immediate synthetic impact or area");
            close(h,target.getHealth(),1000,"no publication direct hit");
        }).thenWaitUntil(()->h.assertTrue(zone(level,expected.getUUID("oldZoneId"))!=null,"first actual nonhoming carrier must naturally collide and form its fixed zone"))
          .thenExecute(()-> {
            var old=zone(level,expected.getUUID("oldZoneId"));
            h.assertTrue(first[0].isRemoved()&&AshEmberLanding.activeFlightCount(zoneCaster)==0&&AshEmberLanding.zones(level).size()==1,"real first collision consumes only that original carrier");
            zoneGeometry(h,old,zoneCaster,target);h.assertTrue(old.pulses()==0&&level.getGameTime()<old.landedAt()+20,"actual original zone is live before its first20 pulse");
            expected.putLong("oldZoneLandedAt",old.landedAt());expected.putLong("oldZoneExpiresAt",old.expiresAt());putVec(expected,"oldZoneCenter",old.center());
            close(h,target.getHealth(),1000,"actual landing has no direct damage");expected.putLong("flightCastGameTime",level.getGameTime());
            h.assertTrue(cast(level,flightCaster)==SkillResult.SUCCESS&&cd(flightCaster)==440&&cd(unused)==0,"second actual source publishes independently; original unused source stays unused");
            moving[0]=onlyFlight(h,flightCaster);expected.putUUID("movingProjectile",moving[0].getUUID());
            expected.putUUID("movingProjectileOwner",flightCaster.getUUID());putVec(expected,"movingSpawnPos",moving[0].position());putVec(expected,"movingLaunchMotion",moving[0].getDeltaMovement());
        }).thenWaitUntil(()-> {
            var shot=moving[0];h.assertTrue(shot.tickCount>0&&shot.position().distanceToSqr(vec(expected,"movingSpawnPos"))>.01,"second genuine projectile must naturally MOVE before snapshot, never a stationary constructed carrier");
        }).thenExecute(()-> {
            long now=level.getGameTime();var shot=moving[0];var old=zone(level,expected.getUUID("oldZoneId"));
            h.assertTrue(old!=null&&old.pulses()==0&&now<old.landedAt()+20&&old.expiresAt()==old.landedAt()+100,"original zone remains live with its unextended100 clock and no pulse yet");
            h.assertTrue(!shot.isRemoved()&&shot.isAddedToWorld()&&level.getEntity(shot.getUUID())==shot&&level.getEntity(shot.getId())==shot&&shot.getOwner()==flightCaster&&AshEmberLanding.flightValid(shot)&&AshEmberLanding.activeFlightCount(flightCaster)==1&&shot.getDeltaMovement().lengthSqr()>0,"actual committed original owner-bound skill projectile still in genuine moving flight immediately before normal stop");
            close(h,target.getHealth(),1000,"no pulse/direct or unrelated native damage before save");
            expected.putLong("snapshotGameTime",now);expected.putInt("snapshotZoneRemaining",(int)(old.expiresAt()-now));expected.putInt("snapshotZonePulses",old.pulses());
            expected.putInt("movingSnapshotTickCount",shot.tickCount);putVec(expected,"movingSnapshotPos",shot.position());putVec(expected,"movingSnapshotMotion",shot.getDeltaMovement());
            expected.putUUID("permanentMovementUuid",MS_TECH);expected.putUUID("transientMovementUuid",BuffKind.MOVEMENT_SPEED.modifierId);expected.putLong("groundCell",GROUND.asLong());
            for(var entry:bodies.entrySet()) {
                var body=entry.getValue();var unit=(Unit)body;var state=new CompoundTag();
                state.putString("type",EntityType.getKey(body.getType()).toString());state.putString("class",body.getClass().getName());
                state.putString("owner",unit.getOwnerName());state.putInt("scenario",unit.getScenarioRoleIndex());state.putFloat("health",body.getHealth());
                state.putBoolean("noGravity",body.isNoGravity());state.putLong("anchor",unit.getAnchor().asLong());
                state.putDouble("movementBase",body.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue());state.putDouble("maxHealthBase",body.getAttribute(Attributes.MAX_HEALTH).getBaseValue());
                state.putDouble("ordinaryRangeBase",body.getAttribute(AttributeRegistrar.ATTACK_RANGE.get()).getBaseValue());
                h.assertTrue(body.isNoAi()&&body.getActiveEffects().isEmpty()&&body.getTarget()==null&&!unit.getAnchor().equals(BlockPos.ZERO),"declared prepare AI containment, no active attack/potion substitute and nonzero original home: "+entry.getKey());
                permanent(h,body,state);
                if(body instanceof AshBoneWingUnit source) {
                    state.putInt("cooldown",cd(source));state.putLong("revision",source.rts().skills().revision());
                    h.assertTrue(source.distanceTo(target)>source.getAggroRange()&&source.position().distanceTo(Vec3.atBottomCenterOf(GROUND))<=8,"declared ordinary range2/aggro0 contains startup AI without changing skill8");
                    long deadline=entry.getKey().equals("unusedCaster")?0:expected.getLong(entry.getKey().equals("zoneCaster")?"zoneCastGameTime":"flightCastGameTime")+440;
                    h.assertTrue(cd(source)==Math.max(0,deadline-now),"exact paid-on-publication440 deadline versus untouched original unused0");
                }
                expected.put(entry.getKey()+"State",state);
            }
            ledger(h);try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception failure){throw new RuntimeException(failure);}
            System.out.println("DREAMSEEKERS_ASH_EMBER_RESTART_PREPARED time="+now+" zoneId="+old.id()+" zoneLandedAt="+old.landedAt()+" zoneExpiresAt="+old.expiresAt()+" zoneRemaining="+(old.expiresAt()-now)+" movingUuid="+shot.getUUID()+" movingOwner="+shot.getOwner().getUUID()+" movingTickCount="+shot.tickCount+" movingMotion="+shot.getDeltaMovement()+" originalUUIDs="+KEYS.stream().map(key->key+":"+bodies.get(key).getUUID()).toList());
            // No save call, hold/slow/rewind projectile, transient clear, post-cast reposition or discard.
            // The pure external gate must find this actual moving carrier in normal Anvil, then
            // the runner must preserve an independent byte-identical prepare copy BEFORE verify.
        }).thenSucceed();
    }

    private static void verify(GameTestHelper h) throws Exception {
        var level=h.getLevel();h.assertTrue(MANIFEST.isFile(),"original prepare manifest exists");var expected=NbtIo.readCompressed(MANIFEST);
        h.assertTrue(expected.getInt("format")==1&&expected.getLong("prepareJvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"different genuine JVM; original combat/reserve bodies are never reconstructed");
        h.assertTrue(expected.getLong("oldZoneExpiresAt")==expected.getLong("oldZoneLandedAt")+100&&expected.getInt("snapshotZoneRemaining")>0&&expected.getInt("snapshotZoneRemaining")<=100&&expected.getLong("snapshotGameTime")+expected.getInt("snapshotZoneRemaining")==expected.getLong("oldZoneExpiresAt"),"actual old landed100/snapshot remaining reconcile without larger startup window");
        h.assertTrue(expected.getInt("snapshotZonePulses")==0&&expected.getInt("movingSnapshotTickCount")>0&&expected.getUUID("movingProjectileOwner").equals(expected.getUUID("flightCaster"))&&expected.getLong("groundCell")==GROUND.asLong(),"original manifest describes actual live zone and distinct naturally moving owner-bound projectile");
        for(long key:expected.getLongArray("forcedChunks")) {
            h.assertTrue(level.getForcedChunks().contains(key),"original forced chunks survive normal save");var c=new ChunkPos(key);level.getChunk(c.x,c.z);
        }
        for(String name:List.of(OWNER,ENEMY))player(level,name);
        Map<String,Mob> bodies=new LinkedHashMap<>();long[] elapsedAtLoad={-1},freshCastAt={-1},freshLandedAt={-1};
        UUID[] freshId={null};Vec3[] freshCenter={null};EmberLandingProjectile[] freshShot={null};
        var seq=h.startSequence().thenWaitUntil(()-> {
            for(String key:KEYS){var body=level.getEntity(expected.getUUID(key));h.assertTrue(body instanceof Mob,"ordinary real loader returns exact original UUID: "+key);published(h,body);}
            for(var p:PLAYERS.values())published(h,p);
        }).thenExecute(()-> {
            long now=level.getGameTime(),elapsed=now-expected.getLong("snapshotGameTime");elapsedAtLoad[0]=elapsed;
            h.assertTrue(elapsed>=0&&now<expected.getLong("oldZoneExpiresAt"),"actual first loaded observation is BEFORE original zone100 expiry; startup never receives an extended window");
            h.assertTrue(!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isEnabled(),"same actual fog-off visibility profile");
            for(String key:KEYS) {
                var body=(Mob)level.getEntity(expected.getUUID(key));bodies.put(key,body);var unit=(Unit)body;var state=expected.getCompound(key+"State");
                h.assertTrue(!body.isNoAi()&&body.isNoGravity()==state.getBoolean("noGravity")&&body.getClass().getName().equals(state.getString("class"))&&EntityType.getKey(body.getType()).toString().equals(state.getString("type")),"observe original native loaded NoAI=false and exact type/class/gravity BEFORE any fixture isolation: "+key);
                h.assertTrue(unit.getOwnerName().equals(state.getString("owner"))&&unit.getScenarioRoleIndex()==state.getInt("scenario")&&unit.getAnchor().equals(BlockPos.of(state.getLong("anchor"))),"original owner/role/home retained without snapshot restoration: "+key);
                h.assertTrue(UnitServerEvents.getAllUnits().stream().filter(e->e.getUUID().equals(body.getUUID())).count()==1,"one actual registered original roster body: "+key);
                close(h,body.getHealth(),state.getFloat("health"),"original HP unchanged by replay/autonomous load attacks: "+key);
                close(h,body.getAttribute(Attributes.MAX_HEALTH).getBaseValue(),state.getDouble("maxHealthBase"),"original max-health base retained: "+key);
                close(h,body.getAttribute(AttributeRegistrar.ATTACK_RANGE.get()).getBaseValue(),state.getDouble("ordinaryRangeBase"),"declared ordinary range2 remains persisted before containment: "+key);
                close(h,body.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).getBaseValue(),0,"declared ordinary aggro0 remains persisted: "+key);
                h.assertTrue(body.getActiveEffects().isEmpty(),"no native effect substitute replay: "+key);permanent(h,body,state);
                if(body instanceof AshBoneWingUnit source)h.assertTrue(AshEmberLanding.activeFlightCount(source)==0&&cd(source)==Math.max(0,state.getInt("cooldown")-elapsed)&&source.rts().skills().revision()>=state.getLong("revision"),"original paid absolute/unused clocks retained; no authorized flight restored: "+key);
            }
            noOldReplay(h,expected);h.assertTrue(AshEmberLanding.zones(level).isEmpty(),"no transient zone restored before original100 deadline");ledger(h);
            System.out.println("ASH_EMBER_RESTART_LOADED_BOUNDARY elapsed="+elapsed+" observedAt="+now+" oldZoneExpiresAt="+expected.getLong("oldZoneExpiresAt")+" observedBeforeOldZoneDeadline=true loadedNativeNoAI=false oldFlightClaim=saved_original_carrier_absent_no_replay_not_a_captured_join_instant");
            // ONLY after every original identity/native-AI/HP/owner/clock/permanent observation.
            for(var body:bodies.values())body.setNoAi(true);
            h.assertTrue(cast(level,(AshBoneWingUnit)bodies.get("zoneCaster"))==SkillResult.COOLDOWN&&cast(level,(AshBoneWingUnit)bodies.get("flightCaster"))==SkillResult.COOLDOWN,"both original paid sources reject a free reload rearm");
        }).thenWaitUntil(()-> {
            noOldReplay(h,expected);for(String key:KEYS)close(h,bodies.get(key).getHealth(),expected.getCompound(key+"State").getFloat("health"),"old flight/zone have no deferred HP replay: "+key);
            h.assertTrue(level.getGameTime()>expected.getLong("oldZoneExpiresAt"),"naturally cross original zone100 without recreating its pulses");
        }).thenExecute(()-> {
            noOldReplay(h,expected);ledger(h);var unused=(AshBoneWingUnit)bodies.get("unusedCaster");freshCastAt[0]=level.getGameTime();
            h.assertTrue(cd(unused)==0&&cast(level,unused)==SkillResult.SUCCESS&&cd(unused)==440,"same original UNUSED source now performs actual fresh ground cast and pays once");
            freshShot[0]=onlyFlight(h,unused);freshId[0]=freshShot[0].getUUID();
            h.assertTrue(!freshId[0].equals(expected.getUUID("movingProjectile"))&&!freshId[0].equals(expected.getUUID("oldZoneId"))&&AshEmberLanding.zones(level).isEmpty(),"fresh genuine carrier has a new UUID, not a restored old projectile/zone");
            close(h,bodies.get("oldTarget").getHealth(),1000,"fresh publication also has no direct damage");
        }).thenWaitUntil(()->h.assertTrue(zone(level,freshId[0])!=null,"fresh actual nonhoming flight must naturally collide; no direct internal landing call"))
          .thenExecute(()-> {
            var fresh=zone(level,freshId[0]);freshLandedAt[0]=fresh.landedAt();freshCenter[0]=fresh.center();
            zoneGeometry(h,fresh,(AshBoneWingUnit)bodies.get("unusedCaster"),bodies.get("oldTarget"));
            h.assertTrue(freshShot[0].isRemoved()&&AshEmberLanding.activeFlightCount((AshBoneWingUnit)bodies.get("unusedCaster"))==0&&fresh.pulses()==0&&fresh.landedAt()>freshCastAt[0]&&level.getGameTime()<fresh.landedAt()+20,"fresh actual collision consumes carrier, begins its own100, and does not pulse early");
            close(h,bodies.get("oldTarget").getHealth(),1000,"fresh landing itself is damage-free");
        });
        for(int n=1;n<=5;n++) {
            final int pulse=n;
            seq.thenWaitUntil(()->h.assertTrue(level.getGameTime()>=freshLandedAt[0]+20L*pulse-1,"wait naturally to immediately before next scheduled pulse"))
               .thenExecute(()-> {
                   h.assertTrue(level.getGameTime()==freshLandedAt[0]+20L*pulse-1,"pre-pulse observation at exact original relative time, no skipped stage");
                   close(h,bodies.get("oldTarget").getHealth(),1000-2*(pulse-1),"no early fresh pulse or unrelated ordinary damage");
                   var fresh=zone(level,freshId[0]);h.assertTrue(fresh!=null&&fresh.pulses()==pulse-1&&fresh.center().equals(freshCenter[0])&&fresh.expiresAt()==freshLandedAt[0]+100,"same fixed zone center/identity and unextended five-pulse schedule");
               }).thenWaitUntil(()->h.assertTrue(level.getGameTime()>=freshLandedAt[0]+20L*pulse+1,"observe after natural level-END scheduled pulse, never manually tick damage"))
               .thenExecute(()-> {
                   h.assertTrue(level.getGameTime()==freshLandedAt[0]+20L*pulse+1,"one natural tick after exact pulse schedule");
                   close(h,bodies.get("oldTarget").getHealth(),1000-2*pulse,"actual fresh pulse HP receipt uses neutral target and native skill magic pipeline");
                   var fresh=zone(level,freshId[0]);
                   h.assertTrue(pulse==5?fresh==null:fresh!=null&&fresh.pulses()==pulse&&fresh.center().equals(freshCenter[0]),"fifth100 pulse precedes zone removal; prior pulses retain same actual zone");
                   System.out.println("ASH_EMBER_RESTART_FRESH_PULSE index="+pulse+" scheduledAt="+(freshLandedAt[0]+20L*pulse)+" observedAt="+level.getGameTime()+" hp="+bodies.get("oldTarget").getHealth()+" originalTarget="+bodies.get("oldTarget").getUUID());
               });
        }
        seq.thenExecute(()-> {
            noOldReplay(h,expected);h.assertTrue(AshEmberLanding.zones(level).isEmpty(),"no old or completed fresh zone remains");
            for(String key:KEYS) {
                var body=bodies.get(key);var state=expected.getCompound(key+"State");
                close(h,body.getHealth(),key.equals("oldTarget")?990:state.getFloat("health"),"only five declared fresh pulse receipts changed original HP: "+key);permanent(h,body,state);
                if(body instanceof AshBoneWingUnit source) {
                    long paidAt=key.equals("unusedCaster")?freshCastAt[0]:expected.getLong(key.equals("zoneCaster")?"zoneCastGameTime":"flightCastGameTime");
                    h.assertTrue(AshEmberLanding.activeFlightCount(source)==0&&cd(source)==Math.max(0,paidAt+440-level.getGameTime()),"all three exact paid publication clocks remain, no flight/expiry/reload refunds");
                }
            }
            ledger(h);System.out.println("DREAMSEEKERS_ASH_EMBER_RESTART_UUID_PAID_CLOCK_NO_FLIGHT_ZONE_REPLAY_FRESH_FIVE_PULSES_PASS elapsedAtLoad="+elapsedAtLoad[0]+" oldZoneExpiresAt="+expected.getLong("oldZoneExpiresAt")+" freshLandedAt="+freshLandedAt[0]+" originalUUIDs="+KEYS.stream().map(key->bodies.get(key).getUUID()).toList());
            for(var body:bodies.values())body.discard();
            for(var p:PLAYERS.values()){membership(p,false);p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}PLAYERS.clear();
            PlayerServerEvents.players.removeIf(p->List.of(OWNER,ENEMY).contains(p.getGameProfile().getName()));PlayerServerEvents.rtsPlayers.removeIf(p->List.of(OWNER,ENEMY).contains(p.name));ResourcesServerEvents.resourcesList.removeIf(r->List.of(OWNER,ENEMY).contains(r.ownerName));
            for(long key:expected.getLongArray("forcedChunks")){var c=new ChunkPos(key);level.setChunkForced(c.x,c.z,false);}
        }).thenSucceed();
    }

    private static SkillResult cast(ServerLevel level,AshBoneWingUnit source) {
        return new SkillNetwork.GroundCast(new SkillGroundRequest(UnitSkill.ASH_EMBER_LANDING,source.getId(),source.getUUID(),source.rts().skills().revision(),GROUND)).execute(player(level,OWNER));
    }
    private static int cd(AshBoneWingUnit source){return source.rts().skills().cooldownTicks(UnitSkill.ASH_EMBER_LANDING);}
    private static AshEmberLanding.ZoneView zone(ServerLevel level,UUID id){return AshEmberLanding.zones(level).stream().filter(z->z.id().equals(id)).findFirst().orElse(null);}
    private static EmberLandingProjectile onlyFlight(GameTestHelper h,AshBoneWingUnit source) {
        var shots=h.getLevel().getEntitiesOfClass(EmberLandingProjectile.class,new AABB(ORIGIN,ORIGIN.offset(32,10,32)),e->e.getOwner()==source);
        h.assertTrue(shots.size()==1&&AshEmberLanding.activeFlightCount(source)==1,"exactly one actual published new skill flight for this source");var shot=shots.get(0);
        h.assertTrue(AshEmberLanding.flightValid(shot)&&shot.getDeltaMovement().lengthSqr()>0&&h.getLevel().getEntity(shot.getId())==shot&&h.getLevel().getEntity(shot.getUUID())==shot,"actual natural skill factory ID/UUID/owner/motion and committed flight authorization");return shot;
    }
    private static void zoneGeometry(GameTestHelper h,AshEmberLanding.ZoneView zone,AshBoneWingUnit source,LivingEntity target) {
        h.assertTrue(zone.sourceUuid().equals(source.getUUID())&&zone.owner().equals(OWNER)&&zone.expiresAt()==zone.landedAt()+100&&zone.center().distanceTo(Vec3.atBottomCenterOf(GROUND))<.5,"original actual fixed floor-collision zone, source owner and100 duration");
        h.assertTrue(zone.center().distanceToSqr(target.getBoundingBox().getCenter())<2.5*2.5&&h.getLevel().clip(new net.minecraft.world.level.ClipContext(zone.center(),target.getBoundingBox().getCenter(),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,source)).getType()==HitResult.Type.MISS,"original enemy target is actually inside pulse radius with native collider LOS");
    }
    private static void noOldReplay(GameTestHelper h,CompoundTag expected) {
        h.assertTrue(h.getLevel().getEntity(expected.getUUID("movingProjectile"))==null&&h.getLevel().getEntity(expected.getUUID("oldZoneId"))==null&&zone(h.getLevel(),expected.getUUID("oldZoneId"))==null&&zone(h.getLevel(),expected.getUUID("movingProjectile"))==null,"saved original carrier absent at observed loaded time and neither old carrier can replay a zone");
    }
    private static void permanent(GameTestHelper h,Mob body,CompoundTag state) {
        var movement=body.getAttribute(Attributes.MOVEMENT_SPEED);
        close(h,movement.getBaseValue(),state.getDouble("movementBase"),"original movement base unchanged");
        h.assertTrue(movement.getModifier(MS_TECH)!=null&&movement.getModifiers().size()==1&&movement.getModifier(BuffKind.MOVEMENT_SPEED.modifierId)==null,"only actual permanent movement research remains; no transient modifier");
        close(h,movement.getValue(),state.getDouble("movementBase")+.03,"actual original permanent movement modifier remains effective");
    }
    private static void putVec(CompoundTag tag,String key,Vec3 value){var list=new ListTag();list.add(DoubleTag.valueOf(value.x));list.add(DoubleTag.valueOf(value.y));list.add(DoubleTag.valueOf(value.z));tag.put(key,list);}
    private static Vec3 vec(CompoundTag tag,String key){var list=tag.getList(key,Tag.TAG_DOUBLE);return new Vec3(list.getDouble(0),list.getDouble(1),list.getDouble(2));}
    private static void owned(GameTestHelper h,Mob body,String owner,double x,double z,int role) {
        var unit=(Unit)body;unit.setOwnerName(owner);unit.setScenarioRoleIndex(role);unit.setOnDeathCommand("");
        body.setNoAi(true);body.setPersistenceRequired();body.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);
        body.getAttribute(AttributeRegistrar.ATTACK_RANGE.get()).setBaseValue(2);
        neutral(body);position(body,new Vec3(ORIGIN.getX()+x,ORIGIN.getY()+1,ORIGIN.getZ()+z));unit.setAnchor(body.blockPosition().below());
        body.getAttribute(Attributes.MOVEMENT_SPEED).addPermanentModifier(new AttributeModifier(MS_TECH,"actual permanent restart movement research",.03,AttributeModifier.Operation.ADDITION));
        h.assertTrue(h.getLevel().addFreshEntity(body),"original registered body enters ordinary world storage");
    }
    private static void durable(LivingEntity body){body.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);body.setHealth(1000);}
    private static void neutral(LivingEntity body) {
        body.getAttribute(Attributes.ARMOR).setBaseValue(0);body.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);
        body.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);body.setAbsorptionAmount(0);
        for(var attribute:List.of(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(),AttributeRegistrar.RANGED_DAMAGE_RESIST.get()))if(body.getAttribute(attribute)!=null)body.getAttribute(attribute).setBaseValue(0);
    }
    private static void published(GameTestHelper h,Entity body){h.assertTrue(body.tickCount>3&&h.getLevel().getEntity(body.getId())==body&&h.getLevel().getEntity(body.getUUID())==body,"real original ID/UUID publication and natural ticks: "+body.getUUID());}
    private static void position(Entity body,Vec3 pos){body.moveTo(pos.x,pos.y,pos.z);body.setDeltaMovement(Vec3.ZERO);}
    private static BlockPos at(int x,int y,int z){return ORIGIN.offset(x,y,z);}
    private static FakePlayer player(ServerLevel level,String name) {
        return PLAYERS.computeIfAbsent(name,key->{var p=new FakePlayer(level,new GameProfile(UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),key));p.connection=new SilentHandler(p);position(p,Vec3.atBottomCenterOf(at(key.equals(OWNER)?2:29,1,28)));level.addNewPlayer(p);PlayerServerEvents.players.add(p);membership(p,true);return p;});
    }
    /** Real local online-owner identity fixture, not a socket login or weakened visibility check. */
    @SuppressWarnings("unchecked") private static void membership(ServerPlayer player,boolean present) {
        try {
            var list=player.serverLevel().getServer().getPlayerList();
            var players=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
            var byId=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
            players.setAccessible(true);byId.setAccessible(true);
            List<ServerPlayer> actual=(List<ServerPlayer>)players.get(list);Map<UUID,ServerPlayer> ids=(Map<UUID,ServerPlayer>)byId.get(list);
            if(present){if(!actual.contains(player))actual.add(player);ids.put(player.getUUID(),player);}
            else{actual.remove(player);ids.remove(player.getUUID(),player);}
        }catch(ReflectiveOperationException error){throw new RuntimeException("real local PlayerList membership",error);}
    }
    private static void ledger(GameTestHelper h) {
        for(String owner:List.of(OWNER,ENEMY)) {
            var ownerPlayer=PLAYERS.get(owner);var online=h.getLevel().getServer().getPlayerList();
            h.assertTrue(ownerPlayer!=null&&online.getPlayer(ownerPlayer.getUUID())==ownerPlayer&&online.getPlayerByName(owner)==ownerPlayer&&online.getPlayers().stream().filter(p->p==ownerPlayer).count()==1,"actual single online owner UUID/name/list membership, not a fake visibility bypass: "+owner);
            var matching=ResourcesServerEvents.resourcesList.stream().filter(e->e.ownerName.equals(owner)).toList();
            h.assertTrue(matching.size()==1&&PlayerServerEvents.getRTSPlayer(owner)!=null,"one original bank and original living-owner RTS record, never recreated: "+owner);var r=matching.get(0);
            h.assertTrue(r.food==123&&r.wood==234&&r.ore==345&&r.emerald==456&&r.foodToAdd==0&&r.woodToAdd==0&&r.oreToAdd==0&&r.emeraldToAdd==0,"strict original balances and pending zero, no shadow-energy refund or fixture restoration: "+owner);
            for(var entity:UnitServerEvents.getAllUnits())if(entity instanceof Unit unit&&unit.getOwnerName().equals(owner))h.assertTrue(unit.getItems().isEmpty(),"declared actual original cargo stays empty: "+entity.getUUID());
        }
        System.out.println("ASH_EMBER_RESTART_LEDGER time="+h.getLevel().getGameTime()+" banks=123,234,345,456 pending=0 cargo=empty");
    }
    private static void arena(ServerLevel level) {
        for(int x=0;x<32;x++)for(int z=0;z<32;z++)level.setBlock(at(x,9,z),Blocks.STONE.defaultBlockState(),2);
        level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(ORIGIN.getX(),ORIGIN.getY()+1,ORIGIN.getZ(),ORIGIN.getX()+32,level.getMaxBuildHeight(),ORIGIN.getZ()+32)).forEach(Entity::discard);
        for(int x=0;x<32;x++)for(int z=0;z<32;z++) {
            level.setBlock(at(x,0,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=1;y<9;y++)level.setBlock(at(x,y,z),(x==0||x==31||z==0||z==31||z==16?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
        }
        level.getEntitiesOfClass(ItemEntity.class,new AABB(ORIGIN,ORIGIN.offset(32,10,32))).forEach(Entity::discard);
    }
    private static void close(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<.001,why+" expected="+expected+" actual="+actual);}
    private static final class SilentHandler extends ServerGamePacketListenerImpl {
        SilentHandler(ServerPlayer p){super(p.serverLevel().getServer(),new SilentConnection(),p);}
        @Override public void tick(){} @Override public void send(net.minecraft.network.protocol.Packet<?> p){}
        @Override public void send(net.minecraft.network.protocol.Packet<?> p,net.minecraft.network.PacketSendListener listener){}
    }
    private static final class SilentConnection extends net.minecraft.network.Connection {
        SilentConnection(){super(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);}
        @Override public void tick(){} @Override public void send(net.minecraft.network.protocol.Packet<?> p){}
        @Override public void send(net.minecraft.network.protocol.Packet<?> p,net.minecraft.network.PacketSendListener listener){}
    }
}
