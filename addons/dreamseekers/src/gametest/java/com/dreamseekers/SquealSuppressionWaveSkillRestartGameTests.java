// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.*;
import com.dreamseekers.shadow.SuppressionWaveProjectile;
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
import net.minecraft.world.entity.projectile.Projectile;
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
import java.io.File;
import java.util.*;

/** Two genuine JVMs and normal Anvil storage, never reconstructed test bodies.
 * The external read-only gate must prove the original moving projectile and original paid bodies
 * actually reached disk while the genuine suppression deadline was still in the future.
 * Loading deliberately observes the original paid-unit NoAI=false policy before isolating ordinary AI.
 */
@GameTestHolder("dreamseekers_squeal_suppression_wave_restart")
@PrefixGameTestTemplate(false)
public final class SquealSuppressionWaveSkillRestartGameTests {
    private static final String OWNER="SuppressionRestart_Owner",ENEMY="SuppressionRestart_Enemy";
    private static final BlockPos ORIGIN=new BlockPos(96256,90,96256);
    private static final File MANIFEST=new File("squeal-suppression-wave-restart-expectations.nbt");
    private static final UUID APS_TECH=UUID.fromString("c0e375e6-0258-44d2-9a89-df0b7950e4ac");
    private static final List<String> SOURCES=List.of("spentCaster","flightCaster","unusedCaster");
    private static final List<String> TARGETS=List.of("oldTarget","flightTarget","freshTarget","flightNear","freshNear");
    private static final List<String> KEYS=List.of("spentCaster","flightCaster","unusedCaster","oldTarget","flightTarget","freshTarget","flightNear","freshNear");
    private static final Map<String,FakePlayer> PLAYERS=new HashMap<>();
    private static final RestartProbe PROBE=new RestartProbe();
    private static boolean listening;

    @GameTest(template="unit_isolation_arena",batch="dreamseekers_squeal_suppression_wave_restart",timeoutTicks=1450)
    public static void originalUuidAbsolutePaidClockAndPermanentApsSurviveButSavedMovingWaveAndSuppressionNeverReplay(GameTestHelper h) throws Exception {
        switch(System.getProperty("dreamseekers.squealSuppressionWave.test.phase","")) {
            case "prepare"->prepare(h);case "verify"->verify(h);
            default->throw new IllegalArgumentException("Unknown suppression restart phase");
        }
    }
    private static void prepare(GameTestHelper h) {
        var level=h.getLevel();h.assertTrue(!MANIFEST.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null&&PlayerServerEvents.getRTSPlayer(ENEMY)==null,"new independent prepare world, never reuse a consumed verification");
        CompoundTag expected=new CompoundTag();expected.putInt("format",1);expected.putLong("prepareJvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        List<Long> forced=new ArrayList<>();for(int x=6016;x<=6017;x++)for(int z=6016;z<=6017;z++){long key=ChunkPos.asLong(x,z);h.assertTrue(!level.getForcedChunks().contains(key),"only new fixture chunk tickets");level.setChunkForced(x,z,true);forced.add(key);}expected.putLongArray("forcedChunks",forced);arena(level);
        for(String name:List.of(OWNER,ENEMY)){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,123,234,345,456));player(level,name);}
        Map<String,ShadowSquealGhostUnit> bodies=new LinkedHashMap<>();
        for(int i=0;i<3;i++){int z=5+10*i;var source=create(h,OWNER,2.5,z+.5,6001+i);source.setAnchor(at(2,0,z));bodies.put(SOURCES.get(i),source);}
        for(int i=0;i<TARGETS.size();i++){String key=TARGETS.get(i);int lane=i<3?i:i-2;double x=i<3?21.5:22.2,z=5.5+lane*10+(i<3?0:.7);var target=create(h,ENEMY,x,z,6004+i);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);target.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).setBaseValue(.5);target.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).addPermanentModifier(new AttributeModifier(APS_TECH,"actual permanent restart attack research",.1,AttributeModifier.Operation.ADDITION));bodies.put(key,target);}
        for(var entry:bodies.entrySet())expected.putUUID(entry.getKey(),entry.getValue().getUUID());
        var spent=bodies.get("spentCaster");var flight=bodies.get("flightCaster");var old=bodies.get("oldTarget");var spentTrace=watch(spent,old);var flightTrace=watch(flight,bodies.get("flightTarget"),bodies.get("flightNear"));long[] firstCastAt={-1},flightCastAt={-1};
        h.startSequence().thenWaitUntil(()->{for(var body:bodies.values())published(h,body);for(var p:PLAYERS.values())published(h,p);})
          .thenExecute(()->{
              h.assertTrue(!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isEnabled(),"actual isolated restart profile uses fog off");ledger(h);
              position(spent,12.5,5.5);firstCastAt[0]=level.getGameTime();h.assertTrue(cast(level,spent,old)==SkillResult.SUCCESS&&cd(spent)==360,"actual owner-authenticated first wave pays original CD360");sameTickShot(h,spentTrace,firstCastAt[0]);
          }).thenWaitUntil(()->{
              h.assertTrue(spentTrace.hp!=null,"wait for actual physical primary impact and same-impact Level END HP");completed(h,spentTrace,old);close(h,1000-spentTrace.hp.get(old.getUUID()),8,"first actual eight HP loss authorizes the saved negative");h.assertTrue(SquealSuppressionWave.hasDebuff(old)&&SquealSuppressionWave.remainingDebuffTicks(old)>0,"original genuine negative remains live before shutdown snapshot");
          }).thenExecute(()->{
              position(spent,2.5,5.5);spent.setUnitAttackTargetForced(null);h.assertTrue(spent.distanceTo(old)>spent.getAttackRange()&&spent.getTarget()==null&&!spent.getHoldPosition(),"after real impact source is back at original distant home, no saved ordinary attack target");
              position(flight,12.5,15.5);flightCastAt[0]=level.getGameTime();h.assertTrue(cast(level,flight,bodies.get("flightTarget"))==SkillResult.SUCCESS&&cd(flight)==360,"second actual immediate cast publishes a genuine independently paid wave");sameTickShot(h,flightTrace,flightCastAt[0]);
              position(flight,2.5,15.5);flight.setUnitAttackTargetForced(null); // source movement after committed launch does not suspend/redirect the real wave
          }).thenIdle(1).thenExecute(()->{
              var shot=(SuppressionWaveProjectile)flightTrace.shots.get(0);h.assertTrue(level.getGameTime()==flightCastAt[0]+1&&shot.tickCount>0&&shot.position().distanceToSqr(flightTrace.starts.get(shot))>.01&&shot.getDeltaMovement().lengthSqr()>0,"normal-save shot has already advanced naturally with nonzero motion; no suspension/teleport/velocity setter");
              h.assertTrue(!shot.isRemoved()&&level.getEntity(shot.getId())==shot&&level.getEntity(shot.getUUID())==shot&&shot.getOwner()==flight&&SquealSuppressionWave.flightValid(shot),"exact original published in-flight authority before ordinary shutdown");
              h.assertTrue(flightTrace.impacts==0&&flightTrace.incoming.isEmpty()&&bodies.get("flightTarget").getHealth()==1000&&bodies.get("flightNear").getHealth()==1000,"old original flight victims genuinely unhit at save snapshot");
              int remaining=SquealSuppressionWave.remainingDebuffTicks(old);h.assertTrue(remaining>0&&remaining<=80&&cd(spent)==360-(level.getGameTime()-firstCastAt[0])&&cd(flight)==359&&cd(bodies.get("unusedCaster"))==0,"old negative and two distinct paid clocks remain live; unused original source still free");
              close(h,old.getAttackCooldownMultiplier(),1.25,"genuine saved negative is active in the real normal attack multiplier");
              expected.putLong("snapshotGameTime",level.getGameTime());expected.putLong("debuffExpiresAt",level.getGameTime()+remaining);expected.putInt("snapshotDebuffRemaining",remaining);expected.putUUID("snapshotProjectile",shot.getUUID());expected.putString("snapshotProjectileType",EntityType.getKey(shot.getType()).toString());expected.putLong("flightCastAt",flightCastAt[0]);expected.putLong("flightJoinAt",flightTrace.joinAt.get(shot));expected.putUUID("permanentApsUuid",APS_TECH);
              for(var entry:bodies.entrySet()){
                  var body=entry.getValue();var state=new CompoundTag();state.putFloat("health",body.getHealth());state.putString("owner",body.getOwnerName());state.putInt("scenario",body.getScenarioRoleIndex());state.putString("type",EntityType.getKey(body.getType()).toString());state.putString("class",body.getClass().getName());state.putDouble("apsBase",body.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getBaseValue());state.putInt("cooldown",cd(body));state.putLong("revision",body.rts().skills().revision());state.putLong("anchor",body.getAnchor().asLong());
                  h.assertTrue(body.isNoAi()&&body.getActiveEffects().isEmpty(),"controlled ordinary AI and no native potion were ever substituted for suppression");
                  if(SOURCES.contains(entry.getKey()))h.assertTrue(!body.getAnchor().equals(BlockPos.ZERO)&&body.getTarget()==null&&body.distanceTo(bodies.get(entry.getKey().equals("spentCaster")?"oldTarget":entry.getKey().equals("flightCaster")?"flightTarget":"freshTarget"))>body.getAttackRange(),"all original casters are safely outside ordinary attack range at shutdown, retaining original nonzero anchors");
                  else h.assertTrue(body.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getModifier(APS_TECH)!=null,"real permanent target APS research exists");
                  expected.put(entry.getKey()+"State",state);
              }
              ledger(h);try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception failure){throw new RuntimeException(failure);}
              System.out.println("DREAMSEEKERS_SQUEAL_SUPPRESSION_RESTART_PREPARED time="+level.getGameTime()+" debuffRemaining="+remaining+" debuffExpiresAt="+expected.getLong("debuffExpiresAt")+" savedProjectile="+shot.getUUID()+" pos="+shot.position()+" motion="+shot.getDeltaMovement()+" tick="+shot.tickCount+" originals="+KEYS.stream().map(key->key+":"+bodies.get(key).getUUID()).toList());
              // Leave the real moving wave, live negative, bodies and tickets to ordinary shutdown/save.
          }).thenSucceed();
    }
    private static void verify(GameTestHelper h) throws Exception {
        var level=h.getLevel();h.assertTrue(MANIFEST.isFile(),"original prepare expectation manifest exists");var expected=NbtIo.readCompressed(MANIFEST);
        h.assertTrue(expected.getInt("format")==1&&expected.getLong("prepareJvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"real different JVM, no in-process deserialize or invented replacement bodies");
        for(long key:expected.getLongArray("forcedChunks")){h.assertTrue(level.getForcedChunks().contains(key),"original forced chunk persisted");var c=new ChunkPos(key);level.getChunk(c.x,c.z);}for(String name:List.of(OWNER,ENEMY))player(level,name);
        Map<String,ShadowSquealGhostUnit> bodies=new LinkedHashMap<>();Map<String,RestartTrace> traces=new LinkedHashMap<>();long[] loadedElapsed={-1},freshAt={-1};
        h.startSequence().thenWaitUntil(()->{for(String key:KEYS){var body=level.getEntity(expected.getUUID(key));h.assertTrue(body instanceof ShadowSquealGhostUnit,"ordinary loader supplies the original registered body: "+key);published(h,body);}for(var p:PLAYERS.values())published(h,p);})
          .thenExecute(()->{
              long elapsed=level.getGameTime()-expected.getLong("snapshotGameTime");loadedElapsed[0]=elapsed;h.assertTrue(elapsed>=0&&level.getGameTime()<expected.getLong("debuffExpiresAt"),"absence observed before original suppression expiry, not absence after natural timeout: elapsed="+elapsed+" savedRemaining="+expected.getInt("snapshotDebuffRemaining"));
              for(String key:KEYS){
                  var body=(ShadowSquealGhostUnit)level.getEntity(expected.getUUID(key));bodies.put(key,body);var state=expected.getCompound(key+"State");
                  h.assertTrue(!body.isNoAi()&&body.getClass().getName().equals(state.getString("class"))&&EntityType.getKey(body.getType()).toString().equals(state.getString("type"))&&body.getOwnerName().equals(state.getString("owner"))&&body.getScenarioRoleIndex()==state.getInt("scenario"),"real paid loader restores active AI plus original exact type/class/owner/role: "+key);
                  h.assertTrue(UnitServerEvents.getAllUnits().stream().filter(e->e.getUUID().equals(body.getUUID())).count()==1,"exactly one original roster object, no reconstructed replacement: "+key);close(h,body.getHealth(),state.getFloat("health"),"pre-containment actual original HP unchanged by save/load ordinary or skill replay: "+key);
                  h.assertTrue(!SquealSuppressionWave.hasDebuff(body)&&SquealSuppressionWave.remainingDebuffTicks(body)==0&&body.getActiveEffects().isEmpty(),"old managed negative is not restored as a runtime lease or native potion: "+key);close(h,body.getAttackCooldownMultiplier(),1,"normal native multiplier has no old suppression: "+key);
                  h.assertTrue(cd(body)==Math.max(0,state.getInt("cooldown")-elapsed)&&body.rts().skills().revision()>=state.getLong("revision"),"original paid/unused absolute CD and replay revision: "+key);close(h,body.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getBaseValue(),state.getDouble("apsBase"),"original APS base survives unchanged: "+key);
                  if(SOURCES.contains(key))h.assertTrue(body.getAnchor().equals(BlockPos.of(state.getLong("anchor")))&&!body.getAnchor().equals(BlockPos.ZERO),"original nonzero anchor survives without a manufactured restore");
                  else {var aps=body.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get());h.assertTrue(aps.getModifier(APS_TECH)!=null&&aps.getModifiers().size()==1,"only actual permanent APS modifier persists on original target");close(h,aps.getValue(),.6,"saved permanent APS research alone retained");}
              }
              h.assertTrue(level.getEntity(expected.getUUID("snapshotProjectile"))==null,"independently audited original moving saved wave has no surviving published authority in this JVM");
              // AFTER all original identities, actual NoAI=false load policy, HP, clocks and revoked old-shot observations.
              // Prevent unrelated normal attacks; the actual new skill below still advances naturally while NoAI=true.
              for(var body:bodies.values())body.setNoAi(true);
              traces.put("spentCaster",watch(bodies.get("spentCaster"),bodies.get("oldTarget")));traces.put("flightCaster",watch(bodies.get("flightCaster"),bodies.get("flightTarget"),bodies.get("flightNear")));traces.put("unusedCaster",watch(bodies.get("unusedCaster"),bodies.get("freshTarget"),bodies.get("freshNear")));
              position(bodies.get("spentCaster"),12.5,5.5);position(bodies.get("flightCaster"),12.5,15.5);
              h.assertTrue(cast(level,bodies.get("spentCaster"),bodies.get("oldTarget"))==SkillResult.COOLDOWN&&cast(level,bodies.get("flightCaster"),bodies.get("flightTarget"))==SkillResult.COOLDOWN,"old paid sources reject free post-restart recasts through the actual authenticated service");
              position(bodies.get("spentCaster"),2.5,5.5);position(bodies.get("flightCaster"),2.5,15.5);position(bodies.get("unusedCaster"),12.5,25.5);
              freshAt[0]=level.getGameTime();h.assertTrue(cd(bodies.get("unusedCaster"))==0&&cast(level,bodies.get("unusedCaster"),bodies.get("freshTarget"))==SkillResult.SUCCESS,"genuinely unused original loaded source makes a new authenticated wave cast");sameTickShot(h,traces.get("unusedCaster"),freshAt[0]);ledger(h);
          }).thenWaitUntil(()->{
              var trace=traces.get("unusedCaster");h.assertTrue(trace!=null&&trace.hp!=null,"new original loaded source must really fly and hit original loaded target");completed(h,trace,bodies.get("freshTarget"));h.assertTrue(trace.impactAt-freshAt[0]>0&&trace.impactAt-freshAt[0]<=25,"fresh real physical travel finishes within fixed twenty-five ticks");
              close(h,1000-trace.hp.get(bodies.get("freshTarget").getUUID()),8,"fresh primary actual HP is the launched snapshot ten times .8");close(h,1000-trace.hp.get(bodies.get("freshNear").getUUID()),8,"fresh nonPrimary secondary actual HP is same snapshot ten times .8");
              h.assertTrue(trace.incomingBeforeSample.size()==2&&trace.incomingBeforeSample.stream().allMatch(read->read.actor==bodies.get("unusedCaster")&&read.direct==trace.shots.get(0)),"both real HP samples have exact current skill provenance without unrelated ordinary contamination");
              h.assertTrue(trace.debuffRemaining.get(bodies.get("freshTarget").getUUID())==80&&trace.debuffRemaining.get(bodies.get("freshNear").getUUID())==80,"both own fresh HP receipts install original full eighty ticks at same-impact END");
          }).thenExecute(()->{position(bodies.get("unusedCaster"),2.5,25.5);ledger(h);})
          .thenIdle(81).thenExecute(()->{
              h.assertTrue(level.getGameTime()>expected.getLong("debuffExpiresAt"),"observation now also crosses the original short-state deadline without replay");
              var freshTrace=traces.get("unusedCaster");for(String key:List.of("freshTarget","freshNear")){var target=bodies.get(key);h.assertTrue(!SquealSuppressionWave.hasDebuff(target),"new genuine temporary negative expires without replay");close(h,target.getAttackCooldownMultiplier(),1,"original engine normal multiplier restored after new effect expiry");close(h,target.getHealth(),freshTrace.hp.get(target.getUUID()),"fresh skill was the only controlled HP change");close(h,target.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).getValue(),.6,"permanent APS research untouched after removal");}
              for(String key:List.of("oldTarget","flightTarget","flightNear")){close(h,bodies.get(key).getHealth(),expected.getCompound(key+"State").getFloat("health"),"old original victims stay unchanged throughout new positive and original deadline");h.assertTrue(!SquealSuppressionWave.hasDebuff(bodies.get(key)),"old negative or flying authority never returns later");}
              for(String key:List.of("spentCaster","flightCaster")){var body=bodies.get(key);long expiry=expected.getLong("snapshotGameTime")+expected.getCompound(key+"State").getInt("cooldown");h.assertTrue(traces.get(key).shots.isEmpty()&&cd(body)==Math.max(0,expiry-level.getGameTime()),"old sources never emit replacement/replayed shots and retain exact paid clock");}
              h.assertTrue(cd(bodies.get("unusedCaster"))==Math.max(0,freshAt[0]+360-level.getGameTime()),"fresh loaded source spends and retains its own real paid deadline");ledger(h);
              System.out.println("DREAMSEEKERS_SQUEAL_SUPPRESSION_RESTART_UUID_ABSOLUTE_CD_NO_REPLAY_PASS elapsedAtLoad="+loadedElapsed[0]+" finalElapsed="+(level.getGameTime()-expected.getLong("snapshotGameTime"))+" freshJoin="+freshTrace.joinAt.get(freshTrace.shots.get(0))+" freshImpact="+freshTrace.impactAt+" savedProjectile="+expected.getUUID("snapshotProjectile")+" freshProjectile="+freshTrace.shots.get(0).getUUID()+" originalUUIDs="+KEYS.stream().map(key->bodies.get(key).getUUID()).toList());
              MinecraftForge.EVENT_BUS.unregister(PROBE);listening=false;PROBE.traces.clear();for(var body:bodies.values())body.discard();for(var p:PLAYERS.values())p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);PLAYERS.clear();PlayerServerEvents.players.removeIf(p->List.of(OWNER,ENEMY).contains(p.getGameProfile().getName()));PlayerServerEvents.rtsPlayers.removeIf(p->List.of(OWNER,ENEMY).contains(p.name));ResourcesServerEvents.resourcesList.removeIf(r->List.of(OWNER,ENEMY).contains(r.ownerName));for(long key:expected.getLongArray("forcedChunks")){var c=new ChunkPos(key);level.setChunkForced(c.x,c.z,false);}
          }).thenSucceed();
    }
    private record Incoming(UUID victim,Entity actor,Entity direct) { }
    private static final class RestartTrace {
        final ShadowSquealGhostUnit source;final List<LivingEntity> targets;final List<Projectile> shots=new ArrayList<>();final Map<Projectile,Long> joinAt=new IdentityHashMap<>();final Map<Projectile,Vec3> starts=new IdentityHashMap<>();final List<Incoming> incoming=new ArrayList<>();List<Incoming> incomingBeforeSample;Map<UUID,Float> hp;Map<UUID,Integer> debuffRemaining;int impacts;long impactAt,sampleAt;UUID hitVictim;double travel;
        RestartTrace(ShadowSquealGhostUnit source,LivingEntity...targets){this.source=source;this.targets=List.of(targets);}
    }
    private static RestartTrace watch(ShadowSquealGhostUnit source,LivingEntity...targets){if(!listening){MinecraftForge.EVENT_BUS.register(PROBE);listening=true;}var trace=new RestartTrace(source,targets);if(PROBE.traces.put(source.getUUID(),trace)!=null)throw new IllegalStateException("duplicate actual source observer");return trace;}
    private static final class RestartProbe {
        final Map<UUID,RestartTrace> traces=new HashMap<>();
        @SubscribeEvent(priority=EventPriority.LOWEST) public void join(EntityJoinLevelEvent event){if(!(event.getEntity() instanceof Projectile shot)||shot.getOwner()==null)return;var trace=traces.get(shot.getOwner().getUUID());if(trace==null)return;trace.shots.add(shot);trace.joinAt.put(shot,event.getLevel().getGameTime());trace.starts.put(shot,shot.position());}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void impact(ProjectileImpactEvent event){if(!(event.getProjectile() instanceof SuppressionWaveProjectile shot)||shot.getOwner()==null)return;var trace=traces.get(shot.getOwner().getUUID());if(trace==null)return;trace.impacts++;trace.impactAt=shot.level().getGameTime();trace.hitVictim=event.getRayTraceResult() instanceof EntityHitResult hit?hit.getEntity().getUUID():null;trace.travel=Math.max(trace.travel,event.getRayTraceResult().getLocation().distanceTo(trace.starts.get(shot)));}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void damage(LivingDamageEvent event){for(var trace:traces.values())if(trace.targets.contains(event.getEntity()))trace.incoming.add(new Incoming(event.getEntity().getUUID(),event.getSource().getEntity(),event.getSource().getDirectEntity()));}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void end(TickEvent.LevelTickEvent event){if(event.phase!=TickEvent.Phase.END||event.level.isClientSide())return;for(var trace:traces.values())if(trace.source.level()==event.level&&trace.hp==null&&trace.impacts>0&&trace.shots.size()==1&&trace.shots.get(0).isRemoved()){Map<UUID,Float> hp=new LinkedHashMap<>();Map<UUID,Integer> debuffs=new LinkedHashMap<>();for(var target:trace.targets){hp.put(target.getUUID(),target.getHealth());debuffs.put(target.getUUID(),SquealSuppressionWave.remainingDebuffTicks(target));}trace.hp=Map.copyOf(hp);trace.debuffRemaining=Map.copyOf(debuffs);trace.sampleAt=event.level.getGameTime();trace.incomingBeforeSample=List.copyOf(trace.incoming);}}
    }
    private static void sameTickShot(GameTestHelper h,RestartTrace trace,long castAt){h.assertTrue(trace.shots.size()==1&&trace.shots.get(0) instanceof SuppressionWaveProjectile&&trace.joinAt.get(trace.shots.get(0))==castAt&&trace.shots.get(0).getOwner()==trace.source,"exact one dedicated physical skill projectile published at true cast tick, never ordinary factory");}
    private static void completed(GameTestHelper h,RestartTrace trace,LivingEntity primary){h.assertTrue(trace.shots.size()==1&&trace.shots.get(0) instanceof SuppressionWaveProjectile&&trace.shots.get(0).isRemoved()&&trace.impacts==1&&primary.getUUID().equals(trace.hitVictim)&&trace.travel>2&&trace.hp!=null&&trace.sampleAt==trace.impactAt,"genuine single consumed physical projectile and same-impact END actual HP sample");h.assertTrue(trace.incomingBeforeSample.size()==trace.targets.size()&&trace.incomingBeforeSample.stream().allMatch(read->read.actor==trace.source&&read.direct==trace.shots.get(0)),"all observed incoming damage has exact same real source/projectile, no unrelated HP borrowing");}
    private static SkillResult cast(ServerLevel level,ShadowSquealGhostUnit source,LivingEntity target){return new SkillNetwork.Cast(new SkillRequest(UnitSkill.SQUEAL_SUPPRESSION_WAVE,source.getId(),source.getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID())).execute(player(level,OWNER));}
    private static int cd(ShadowSquealGhostUnit source){return source.rts().skills().cooldownTicks(UnitSkill.SQUEAL_SUPPRESSION_WAVE);}
    private static ShadowSquealGhostUnit create(GameTestHelper h,String owner,double x,double z,int role){var body=DreamseekersMod.SHADOW_SQUEAL_GHOST_UNIT.get().create(h.getLevel());body.setOwnerName(owner);body.setScenarioRoleIndex(role);body.setNoAi(true);body.setPersistenceRequired();body.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);body.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(10);body.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);body.getAttribute(Attributes.ARMOR).setBaseValue(0);body.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);body.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);body.setAbsorptionAmount(0);for(var attribute:List.of(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(),AttributeRegistrar.RANGED_DAMAGE_RESIST.get()))if(body.getAttribute(attribute)!=null)body.getAttribute(attribute).setBaseValue(0);position(body,x,z);h.assertTrue(h.getLevel().addFreshEntity(body),"original registered RTS body enters ordinary real world storage");return body;}
    private static void published(GameTestHelper h,Entity body){h.assertTrue(body.tickCount>3&&h.getLevel().getEntity(body.getId())==body&&h.getLevel().getEntity(body.getUUID())==body,"actual original ID/UUID publication and natural entity ticks: "+body.getUUID());}
    private static void position(Entity body,double x,double z){body.moveTo(ORIGIN.getX()+x,ORIGIN.getY()+1,ORIGIN.getZ()+z,0,0);body.setDeltaMovement(Vec3.ZERO);}
    private static BlockPos at(int x,int y,int z){return ORIGIN.offset(x,y,z);}
    private static FakePlayer player(ServerLevel level,String name){return PLAYERS.computeIfAbsent(name,key->{var p=new FakePlayer(level,new GameProfile(UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),key));p.connection=new SilentHandler(p);position(p,key.equals(OWNER)?2.5:29.5,29.5);level.addNewPlayer(p);PlayerServerEvents.players.add(p);return p;});}
    private static void ledger(GameTestHelper h){for(String owner:List.of(OWNER,ENEMY)){var resources=ResourcesServerEvents.resourcesList.stream().filter(r->r.ownerName.equals(owner)).findFirst().orElse(null);h.assertTrue(resources!=null&&resources.food==123&&resources.wood==234&&resources.ore==345&&resources.emerald==456&&resources.foodToAdd==0&&resources.woodToAdd==0&&resources.oreToAdd==0&&resources.emeraldToAdd==0,"original exact bank/pending, never recreated/refilled: "+owner);for(var body:UnitServerEvents.getAllUnits())if(body instanceof Unit unit&&unit.getOwnerName().equals(owner))h.assertTrue(unit.getItems().isEmpty(),"declared empty cargo remains empty: "+body.getUUID());}System.out.println("SQUEAL_SUPPRESSION_RESTART_LEDGER time="+h.getLevel().getGameTime()+" banks=123,234,345,456 pending=0 cargo=empty");}
    private static void arena(ServerLevel level){for(int x=0;x<32;x++)for(int z=0;z<32;z++)level.setBlock(at(x,9,z),Blocks.STONE.defaultBlockState(),2);level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(ORIGIN.getX(),ORIGIN.getY()+1,ORIGIN.getZ(),ORIGIN.getX()+32,level.getMaxBuildHeight(),ORIGIN.getZ()+32)).forEach(Entity::discard);for(int x=0;x<32;x++)for(int z=0;z<32;z++){level.setBlock(at(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<9;y++)level.setBlock(at(x,y,z),(x==0||x==31||z==0||z==31||z==10||z==20?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}level.getEntitiesOfClass(ItemEntity.class,new AABB(ORIGIN,ORIGIN.offset(32,10,32))).forEach(Entity::discard);}
    private static void close(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<.001,why+" expected="+expected+" actual="+actual);}
    private static final class SilentHandler extends ServerGamePacketListenerImpl{SilentHandler(ServerPlayer p){super(p.serverLevel().getServer(),new SilentConnection(),p);}@Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> packet){}@Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){}}
    private static final class SilentConnection extends net.minecraft.network.Connection{SilentConnection(){super(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);}@Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> packet){}@Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){}}
}
