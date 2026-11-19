// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.*;
import com.dreamseekers.stone.HeavyStoneProjectile;
import com.dreamseekers.unit.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
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
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;

/** Two real JVMs and normal world storage. Manifest contains expectations, never reconstructed entities.
 * Before verify, read_saved_heavy_restart.py must independently prove the real in-flight UUID reached disk.
 * Startup need not beat twenty ticks: no-replay is proved by unchanged real victims, not absence-after-expiry alone.
 */
@GameTestHolder("dreamseekers_stone_heavy_shot_restart")
@PrefixGameTestTemplate(false)
public final class StoneHeavyShotSkillRestartGameTests {
    private static final String OWNER="HeavyStoneRestart_Owner",ENEMY="HeavyStoneRestart_Enemy";
    private static final BlockPos ORIGIN=new BlockPos(90112,90,90112);
    private static final File MANIFEST=new File("stone-heavy-shot-restart-expectations.nbt");
    private static final List<String> SOURCES=List.of("windupCaster","flightCaster","unusedCaster");
    private static final List<String> KEYS=List.of("windupCaster","flightCaster","unusedCaster","windupTarget","flightTarget","freshTarget","windupNear","flightNear","freshNear");
    private static final Map<String,FakePlayer> PLAYERS=new HashMap<>();
    private static final RestartProbe PROBE=new RestartProbe();
    private static boolean listening;

    @GameTest(template="unit_isolation_arena",batch="dreamseekers_stone_heavy_shot_restart",timeoutTicks=1450)
    public static void realRestartKeepsOriginalBodiesAndAbsolutePaidClockWithoutWindupOrProjectileReplay(GameTestHelper h) throws Exception {
        switch(System.getProperty("dreamseekers.stoneHeavyShot.test.phase","")) {
            case "prepare"->prepare(h);case "verify"->verify(h);
            default->throw new IllegalArgumentException("Unknown heavy-shot restart phase");
        }
    }
    private static void prepare(GameTestHelper h) {
        var level=h.getLevel();h.assertTrue(!MANIFEST.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null&&PlayerServerEvents.getRTSPlayer(ENEMY)==null,"fresh independent prepare world; never reuse a consumed verification");
        CompoundTag expected=new CompoundTag();expected.putInt("format",1);expected.putLong("prepareJvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        List<Long> forced=new ArrayList<>();for(int x=5632;x<=5633;x++)for(int z=5632;z<=5633;z++){long key=ChunkPos.asLong(x,z);h.assertTrue(!level.getForcedChunks().contains(key),"only new fixture chunk tickets");level.setChunkForced(x,z,true);forced.add(key);}expected.putLongArray("forcedChunks",forced);arena(level);
        for(String name:List.of(OWNER,ENEMY)){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,123,234,345,456));player(level,name);}
        Map<String,Mob> bodies=new LinkedHashMap<>();
        for(int i=0;i<3;i++) {
            int z=5+10*i;var source=DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get().create(level);owned(h,source,OWNER,4.5,z+.5,4001+i,false);damage(source,10);source.setAnchor(at(4,0,z));bodies.put(SOURCES.get(i),source);
            var target=DreamseekersMod.JELLYFISH_UNIT.get().create(level);owned(h,target,ENEMY,13.5,z+.5,4004+i,true);durable(target);bodies.put(KEYS.get(3+i),target);
            var near=DreamseekersMod.JELLYFISH_UNIT.get().create(level);owned(h,near,ENEMY,14.4,z+1,4007+i,true);durable(near);bodies.put(KEYS.get(6+i),near);
        }
        for(var entry:bodies.entrySet())expected.putUUID(entry.getKey(),entry.getValue().getUUID());
        var flight=(StoneSpiritThrowerUnit)bodies.get("flightCaster");var charging=(StoneSpiritThrowerUnit)bodies.get("windupCaster");var trace=watch(flight,bodies.get("flightTarget"),bodies.get("flightNear"));var windupTrace=watch(charging,bodies.get("windupTarget"),bodies.get("windupNear"));long[] castAt={0};
        var seq=h.startSequence().thenWaitUntil(()->{
            for(var body:bodies.values())published(h,body);for(var p:PLAYERS.values())published(h,p);
            for(String key:SOURCES){var source=(StoneSpiritThrowerUnit)bodies.get(key);h.assertTrue(source.onGround()&&source.verticalCollision&&Math.abs(source.getBoundingBox().minY-(ORIGIN.getY()+1))<.001,"real native grounded body before containment, never fake onGround/zero-gravity");}
        }).thenExecute(()->{
            h.assertTrue(!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isEnabled(),"actual restart profile uses fog off");
            for(String key:SOURCES)bodies.get(key).setNoAi(true); // Fixed save fixture: prevent unrelated ordinary AI, not a gameplay NoAI restriction.
            ledger(h);castAt[0]=level.getGameTime();h.assertTrue(cast(level,flight,bodies.get("flightTarget"))==SkillResult.SUCCESS,"actual owner-authenticated flight source pays original cooldown");
        });
        for(int tick=1;tick<=19;tick++){final int elapsed=tick;seq.thenIdle(1).thenExecute(()->{
            h.assertTrue(level.getGameTime()-castAt[0]==elapsed&&StoneHeavyShot.remainingTicks(flight)==20-elapsed&&cd(flight)==360-elapsed,"exact original natural-tick release and paid-clock countdown");
            h.assertTrue(trace.shots.isEmpty()&&bodies.get("flightTarget").getHealth()==1000&&bodies.get("flightNear").getHealth()==1000,"no ordinary/heavy or immediate HP leak before twenty ticks");
        });}
        seq.thenIdle(2).thenExecute(()->{
            h.assertTrue(level.getGameTime()-castAt[0]==21&&trace.shots.size()==1&&trace.shots.get(0) instanceof HeavyStoneProjectile,"one actual factory heavy exists at fixed tick twenty-one");
            var shot=(HeavyStoneProjectile)trace.shots.get(0);h.assertTrue(trace.joinAt.get(shot)==castAt[0]+20&&shot.getOwner()==flight&&shot.tickCount>0&&shot.position().distanceToSqr(trace.starts.get(shot))>.01
                    &&shot.getDeltaMovement().lengthSqr()>0&&!shot.isRemoved()&&level.getEntity(shot.getUUID())==shot&&level.getEntity(shot.getId())==shot,"real published projectile has already travelled naturally, never hand-armed, teleported or suspended");
            h.assertTrue(trace.impacts==0&&trace.incoming.isEmpty()&&bodies.get("flightTarget").getHealth()==1000&&bodies.get("flightNear").getHealth()==1000,"normal-save observation is genuinely before old projectile impact");
            h.assertTrue(cast(level,charging,bodies.get("windupTarget"))==SkillResult.SUCCESS&&StoneHeavyShot.remainingTicks(charging)==20&&cd(charging)==360&&windupTrace.shots.isEmpty(),"a second real source has an uncompleted paid windup at shutdown snapshot");
            h.assertTrue(!StoneHeavyShot.active(flight)&&cd(flight)==339&&cd((StoneSpiritThrowerUnit)bodies.get("unusedCaster"))==0,"released versus winding versus genuinely unused sources remain distinct");
            expected.putLong("snapshotGameTime",level.getGameTime());expected.putLong("windupReleaseAt",level.getGameTime()+20);expected.putUUID("snapshotProjectile",shot.getUUID());expected.putString("snapshotProjectileType",EntityType.getKey(shot.getType()).toString());expected.putLong("flightCastAt",castAt[0]);expected.putLong("flightJoinAt",trace.joinAt.get(shot));
            for(var entry:bodies.entrySet()) {
                Mob body=entry.getValue();var unit=(Unit)body;CompoundTag state=new CompoundTag();state.putFloat("health",body.getHealth());state.putString("owner",unit.getOwnerName());state.putInt("scenario",unit.getScenarioRoleIndex());state.putString("type",EntityType.getKey(body.getType()).toString());state.putString("class",body.getClass().getName());
                if(body instanceof StoneSpiritThrowerUnit source){state.putInt("cooldown",cd(source));state.putLong("revision",source.rts().skills().revision());state.putLong("anchor",source.getAnchor().asLong());h.assertTrue(!source.getAnchor().equals(BlockPos.ZERO)&&!source.rts().hold,"real original anchor and raw hold were never overwritten by derived windup hold");}
                expected.put(entry.getKey()+"State",state);
            }
            ledger(h);try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception failure){throw new RuntimeException(failure);}
            System.out.println("DREAMSEEKERS_STONE_HEAVY_RESTART_PREPARED time="+level.getGameTime()+" windup20/CD360 flightCD339 projectile="+shot.getUUID()+" pos="+shot.position()+" velocity="+shot.getDeltaMovement()+" tick="+shot.tickCount+" originals="+KEYS.stream().map(key->key+":"+bodies.get(key).getUUID()).toList());
            // Leave real bodies, real moving shot and forced chunks for ordinary shutdown/save. External read-only audit must confirm actual disk state before verify.
        }).thenSucceed();
    }
    private static void verify(GameTestHelper h) throws Exception {
        var level=h.getLevel();h.assertTrue(MANIFEST.isFile(),"original preparation expectation manifest exists");var expected=NbtIo.readCompressed(MANIFEST);
        h.assertTrue(expected.getInt("format")==1&&expected.getLong("prepareJvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"actual different JVM, never same-process deserialization or body reconstruction");
        for(long key:expected.getLongArray("forcedChunks")){h.assertTrue(level.getForcedChunks().contains(key),"original forced chunk persisted naturally");var chunk=new ChunkPos(key);level.getChunk(chunk.x,chunk.z);}for(String name:List.of(OWNER,ENEMY))player(level,name);
        Map<String,Mob> bodies=new LinkedHashMap<>();Map<String,RestartTrace> traces=new LinkedHashMap<>();long[] loadedElapsed={0},freshAt={0};
        var seq=h.startSequence().thenWaitUntil(()->{for(String key:KEYS){var body=level.getEntity(expected.getUUID(key));h.assertTrue(body instanceof Mob,"ordinary world loader supplies original body UUID: "+key);published(h,body);}for(var p:PLAYERS.values())published(h,p);})
          .thenExecute(()->{
              long elapsed=level.getGameTime()-expected.getLong("snapshotGameTime");loadedElapsed[0]=elapsed;
              h.assertTrue(elapsed>=0&&elapsed<339,"observe original paid cooldown before its natural deadline, not an invented pre-twenty-tick startup claim: "+elapsed);
              for(String key:KEYS) {
                  var body=(Mob)level.getEntity(expected.getUUID(key));bodies.put(key,body);var state=expected.getCompound(key+"State");var unit=(Unit)body;
                  h.assertTrue(!body.isNoAi()&&unit.getOwnerName().equals(state.getString("owner"))&&unit.getScenarioRoleIndex()==state.getInt("scenario")&&EntityType.getKey(body.getType()).toString().equals(state.getString("type"))&&body.getClass().getName().equals(state.getString("class")),"real original type/class/owner/role survive and paid-unit loader restores active AI: "+key);
                  h.assertTrue(UnitServerEvents.getAllUnits().stream().filter(e->e.getUUID().equals(body.getUUID())).count()==1,"one actual original roster body, not a replacement: "+key);close(h,body.getHealth(),state.getFloat("health"),"real old target/source HP proves no saved windup/projectile attack replay: "+key);
                  if(body instanceof StoneSpiritThrowerUnit source){h.assertTrue(!StoneHeavyShot.active(source)&&StoneHeavyShot.remainingTicks(source)==0&&!source.getHoldPosition(),"no transient charge/derived hold restored: "+key);h.assertTrue(cd(source)==Math.max(0,state.getInt("cooldown")-elapsed)&&source.rts().skills().revision()>=state.getLong("revision"),"original paid absolute clock/replay revision, never refund or restart: "+key);h.assertTrue(source.getAnchor().equals(BlockPos.of(state.getLong("anchor")))&&!source.getAnchor().equals(BlockPos.ZERO),"original nonzero anchor survives without restoration fiction: "+key);}
              }
              h.assertTrue(level.getEntity(expected.getUUID("snapshotProjectile"))==null,"the independently audited real saved projectile has no surviving published launch authority in this JVM");
              // Only AFTER proving actual active-AI loading, original identity/HP/CD and old-shot revocation:
              // contain unrelated ordinary/healing AI for the fresh manual-skill positive control. This is not a persisted NoAI claim.
              // Heavy deliberately permits NoAI; the original loaded source must still naturally reserve/release/hit below.
              for(var body:bodies.values())body.setNoAi(true);
              for(int i=0;i<3;i++){String key=SOURCES.get(i);traces.put(key,watch((StoneSpiritThrowerUnit)bodies.get(key),bodies.get(KEYS.get(3+i)),bodies.get(KEYS.get(6+i))));}
              h.assertTrue(cast(level,(StoneSpiritThrowerUnit)bodies.get("windupCaster"),bodies.get("windupTarget"))==SkillResult.COOLDOWN&&cast(level,(StoneSpiritThrowerUnit)bodies.get("flightCaster"),bodies.get("flightTarget"))==SkillResult.COOLDOWN,"both original paid sources reject free recast after restart");
              var unused=(StoneSpiritThrowerUnit)bodies.get("unusedCaster");freshAt[0]=level.getGameTime();h.assertTrue(cd(unused)==0&&cast(level,unused,bodies.get("freshTarget"))==SkillResult.SUCCESS,"genuinely unused original loaded source makes a fresh authenticated cast");ledger(h);
          });
        for(int tick=1;tick<=19;tick++){final int elapsed=tick;seq.thenIdle(1).thenExecute(()->{
            var fresh=(StoneSpiritThrowerUnit)bodies.get("unusedCaster");h.assertTrue(level.getGameTime()-freshAt[0]==elapsed&&StoneHeavyShot.remainingTicks(fresh)==20-elapsed&&cd(fresh)==360-elapsed&&traces.get("unusedCaster").shots.isEmpty(),"fresh original loaded source obeys exact full windup without ordinary/skill projectile leak");
            close(h,bodies.get("freshTarget").getHealth(),1000,"fresh reservation cannot do immediate damage");
        });}
        seq.thenIdle(2).thenExecute(()->{
            var trace=traces.get("unusedCaster");h.assertTrue(trace.shots.size()==1&&trace.shots.get(0) instanceof HeavyStoneProjectile&&trace.joinAt.get(trace.shots.get(0))==freshAt[0]+20,"new real factory shot alone publishes at exact new twentieth tick");
            h.assertTrue(level.getEntity(trace.shots.get(0).getUUID())==trace.shots.get(0)&&trace.shots.get(0).getOwner()==bodies.get("unusedCaster"),"fresh launched projectile is actually published with original loaded owner");
        }).thenIdle(24).thenExecute(()->{
            var trace=traces.get("unusedCaster");var shot=trace.shots.get(0);
            h.assertTrue(level.getGameTime()-freshAt[0]==45&&trace.impacts==1&&bodies.get("freshTarget").getUUID().equals(trace.hitVictim)&&shot.isRemoved()&&trace.travel>2,"new projectile really flies and physically hits original loaded target within fixed forty-five ticks");
            h.assertTrue(trace.hp!=null&&trace.sampleAt==trace.impactAt&&trace.incomingBeforeSample.size()==2&&trace.incomingBeforeSample.stream().allMatch(read->read.actor==bodies.get("unusedCaster")&&read.direct==shot),"real Level END HP snapshot has exactly current Heavy primary/secondary provenance, no non-Heavy contamination");
            close(h,1000-trace.hp.get(bodies.get("freshTarget").getUUID()),15,"fresh actual post-load primary is released ten times 1.5");close(h,1000-trace.hp.get(bodies.get("freshNear").getUUID()),3,"fresh actual post-load secondary is released ten times .3");
            for(String key:List.of("windupCaster","flightCaster")){var source=(StoneSpiritThrowerUnit)bodies.get(key);var state=expected.getCompound(key+"State");h.assertTrue(!StoneHeavyShot.active(source)&&traces.get(key).shots.isEmpty()&&cd(source)==Math.max(0,expected.getLong("snapshotGameTime")+state.getInt("cooldown")-level.getGameTime()),"old sources never emit delayed replacement/replay and retain original deadline: "+key);}
            for(String key:List.of("windupTarget","windupNear","flightTarget","flightNear"))close(h,bodies.get(key).getHealth(),expected.getCompound(key+"State").getFloat("health"),"old saved targets remain undamaged through fresh real positive control: "+key);
            ledger(h);System.out.println("DREAMSEEKERS_STONE_HEAVY_RESTART_UUID_ABSOLUTE_CD_NO_REPLAY_PASS elapsedAtLoad="+loadedElapsed[0]+" freshJoin="+trace.joinAt.get(shot)+" freshImpact="+trace.impactAt+" savedProjectile="+expected.getUUID("snapshotProjectile")+" freshProjectile="+shot.getUUID()+" originalUUIDs="+KEYS.stream().map(key->bodies.get(key).getUUID()).toList());
            MinecraftForge.EVENT_BUS.unregister(PROBE);listening=false;PROBE.traces.clear();for(var body:bodies.values())body.discard();for(var p:PLAYERS.values())p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);PLAYERS.clear();PlayerServerEvents.players.removeIf(p->List.of(OWNER,ENEMY).contains(p.getGameProfile().getName()));PlayerServerEvents.rtsPlayers.removeIf(p->List.of(OWNER,ENEMY).contains(p.name));ResourcesServerEvents.resourcesList.removeIf(r->List.of(OWNER,ENEMY).contains(r.ownerName));for(long key:expected.getLongArray("forcedChunks")){var chunk=new ChunkPos(key);level.setChunkForced(chunk.x,chunk.z,false);}
        }).thenSucceed();
    }
    private record Incoming(UUID victim,Entity actor,Entity direct) { }
    private static final class RestartTrace {
        final StoneSpiritThrowerUnit source;final List<LivingEntity> targets;final List<Projectile> shots=new ArrayList<>();final Map<Projectile,Long> joinAt=new IdentityHashMap<>();final Map<Projectile,Vec3> starts=new IdentityHashMap<>();final List<Incoming> incoming=new ArrayList<>();List<Incoming> incomingBeforeSample;Map<UUID,Float> hp;int impacts;long impactAt,sampleAt;UUID hitVictim;double travel;
        RestartTrace(StoneSpiritThrowerUnit source,LivingEntity...targets){this.source=source;this.targets=List.of(targets);}
    }
    private static RestartTrace watch(StoneSpiritThrowerUnit source,LivingEntity...targets){if(!listening){MinecraftForge.EVENT_BUS.register(PROBE);listening=true;}var trace=new RestartTrace(source,targets);if(PROBE.traces.put(source.getUUID(),trace)!=null)throw new IllegalStateException("duplicate actual source observer");return trace;}
    private static final class RestartProbe {
        final Map<UUID,RestartTrace> traces=new HashMap<>();
        @SubscribeEvent(priority=EventPriority.LOWEST) public void join(EntityJoinLevelEvent event){if(!(event.getEntity() instanceof Projectile shot)||shot.getOwner()==null)return;var trace=traces.get(shot.getOwner().getUUID());if(trace==null)return;trace.shots.add(shot);trace.joinAt.put(shot,event.getLevel().getGameTime());trace.starts.put(shot,shot.position());}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void impact(ProjectileImpactEvent event){if(!(event.getProjectile() instanceof HeavyStoneProjectile shot)||shot.getOwner()==null)return;var trace=traces.get(shot.getOwner().getUUID());if(trace==null)return;trace.impacts++;trace.impactAt=shot.level().getGameTime();trace.hitVictim=event.getRayTraceResult() instanceof EntityHitResult hit?hit.getEntity().getUUID():null;trace.travel=Math.max(trace.travel,event.getRayTraceResult().getLocation().distanceTo(trace.starts.get(shot)));}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void damage(LivingDamageEvent event){for(var trace:traces.values())if(trace.targets.contains(event.getEntity()))trace.incoming.add(new Incoming(event.getEntity().getUUID(),event.getSource().getEntity(),event.getSource().getDirectEntity()));}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void end(TickEvent.LevelTickEvent event){if(event.phase!=TickEvent.Phase.END||event.level.isClientSide())return;for(var trace:traces.values())if(trace.source.level()==event.level&&trace.hp==null&&trace.impacts>0&&trace.shots.size()==1&&trace.shots.get(0).isRemoved()){Map<UUID,Float> hp=new LinkedHashMap<>();for(var target:trace.targets)hp.put(target.getUUID(),target.getHealth());trace.hp=Map.copyOf(hp);trace.sampleAt=event.level.getGameTime();trace.incomingBeforeSample=List.copyOf(trace.incoming);}}
    }
    private static SkillResult cast(ServerLevel level,StoneSpiritThrowerUnit source,LivingEntity target){return new SkillNetwork.Cast(new SkillRequest(UnitSkill.STONE_HEAVY_SHOT,source.getId(),source.getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID())).execute(player(level,OWNER));}
    private static int cd(StoneSpiritThrowerUnit source){return source.rts().skills().cooldownTicks(UnitSkill.STONE_HEAVY_SHOT);}
    private static void owned(GameTestHelper h,Mob body,String owner,double x,double z,int scenario,boolean noAi){var unit=(Unit)body;unit.setOwnerName(owner);unit.setScenarioRoleIndex(scenario);body.setNoAi(noAi);body.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);body.moveTo(ORIGIN.getX()+x,ORIGIN.getY()+1,ORIGIN.getZ()+z,0,0);body.setDeltaMovement(Vec3.ZERO);neutral(body);h.assertTrue(h.getLevel().addFreshEntity(body),"original real RTS body enters ordinary world entity storage");}
    private static void damage(LivingEntity source,double amount){source.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(amount);if(source.getAttribute(Attributes.ATTACK_DAMAGE)!=null)source.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(amount);if(source.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get())!=null)source.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);}
    private static void neutral(LivingEntity target){target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);target.setAbsorptionAmount(0);for(var attribute:List.of(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(),AttributeRegistrar.RANGED_DAMAGE_RESIST.get()))if(target.getAttribute(attribute)!=null)target.getAttribute(attribute).setBaseValue(0);}
    private static void durable(LivingEntity target){target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);}
    private static void published(GameTestHelper h,Entity body){h.assertTrue(body.tickCount>3&&h.getLevel().getEntity(body.getId())==body&&h.getLevel().getEntity(body.getUUID())==body,"actual original loaded ID/UUID and natural entity ticks: "+body.getUUID());}
    private static BlockPos at(int x,int y,int z){return ORIGIN.offset(x,y,z);}
    private static FakePlayer player(ServerLevel level,String name){return PLAYERS.computeIfAbsent(name,key->{var player=new FakePlayer(level,new GameProfile(UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),key));player.connection=new SilentHandler(player);player.moveTo(ORIGIN.getX()+(key.equals(OWNER)?2.5:29.5),ORIGIN.getY()+1,ORIGIN.getZ()+29.5,0,0);level.addNewPlayer(player);PlayerServerEvents.players.add(player);return player;});}
    private static void ledger(GameTestHelper h){for(String owner:List.of(OWNER,ENEMY)){var resources=ResourcesServerEvents.resourcesList.stream().filter(r->r.ownerName.equals(owner)).findFirst().orElse(null);h.assertTrue(resources!=null&&resources.food==123&&resources.wood==234&&resources.ore==345&&resources.emerald==456&&resources.foodToAdd==0&&resources.woodToAdd==0&&resources.oreToAdd==0&&resources.emeraldToAdd==0,"original exact bank/pending, never recreated or refilled: "+owner);for(var body:UnitServerEvents.getAllUnits())if(body instanceof Unit unit&&unit.getOwnerName().equals(owner))h.assertTrue(unit.getItems().isEmpty(),"declared empty cargo remains empty: "+body.getUUID());}System.out.println("STONE_HEAVY_RESTART_LEDGER time="+h.getLevel().getGameTime()+" banks=123,234,345,456 pending=0 cargo=empty");}
    private static void arena(ServerLevel level){for(int x=0;x<32;x++)for(int z=0;z<32;z++)level.setBlock(at(x,9,z),Blocks.STONE.defaultBlockState(),2);level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(ORIGIN.getX(),ORIGIN.getY()+1,ORIGIN.getZ(),ORIGIN.getX()+32,level.getMaxBuildHeight(),ORIGIN.getZ()+32)).forEach(Entity::discard);for(int x=0;x<32;x++)for(int z=0;z<32;z++){level.setBlock(at(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<9;y++)level.setBlock(at(x,y,z),(x==0||x==31||z==0||z==31||z==10||z==20?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}level.getEntitiesOfClass(ItemEntity.class,new AABB(ORIGIN,ORIGIN.offset(32,10,32))).forEach(Entity::discard);}
    private static void close(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<.001,why+" expected="+expected+" actual="+actual);}
    private static final class SilentHandler extends ServerGamePacketListenerImpl{SilentHandler(ServerPlayer player){super(player.serverLevel().getServer(),new SilentConnection(),player);}@Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> packet){}@Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){}}
    private static final class SilentConnection extends net.minecraft.network.Connection{SilentConnection(){super(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);}@Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> packet){}@Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){}}
}
