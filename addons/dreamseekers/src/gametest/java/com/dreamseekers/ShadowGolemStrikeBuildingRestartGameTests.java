// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.UnitAction;
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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.io.File;
import java.util.*;

import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.dreamseekers.production.DreamProductionPlacement;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.core.Direction;
/** Separate genuine building-windup normal-save pair. Native BuildingSaveData plus original
 * block/entity Anvil bytes must be preserved and read-only gated before second JVM startup.
 * ServerStarted constructs a NEW placement object from its saved registry key; verification
 * resolves that native object by origin/owner/type/data/blocks, never installs a substitute.
 * SavedTime must precede ORIGINAL20 release. Loading after20 proves no replay, not early revoke.
 */
@GameTestHolder("dreamseekers_shadow_golem_strike_building_restart")
@PrefixGameTestTemplate(false)
public final class ShadowGolemStrikeBuildingRestartGameTests {
 private static final String OWNER="StrikeBuildingRestart_Owner",ENEMY="StrikeBuildingRestart_Enemy";
 private static final BlockPos ORIGIN=new BlockPos(131072,300,131072);
 private static final File MANIFEST=new File("shadow-golem-strike-building-restart-expectations.nbt");
 private static final UUID MS_TECH=UUID.fromString("16377e66-4da3-4c28-9f42-3f5cc639a038");
 private static final List<String> KEYS=List.of("paidCaster","ownerReserve","enemyReserve");
 private static final Map<String,FakePlayer> PLAYERS=new HashMap<>();
 private static final Set<BlockPos> FIXTURE_BLOCKS=new HashSet<>();
 @GameTest(template="unit_isolation_arena",batch="dreamseekers_shadow_golem_strike_building_restart",timeoutTicks=900)
 public static void nativeSavedBuildingAndBlocksPersistButPaidWindupNeverReplaysAndFreshBuildingReleaseWorks(GameTestHelper h)throws Exception{
  switch(System.getProperty("dreamseekers.shadowGolemStrikeBuilding.test.phase","")){
   case "prepare"->prepare(h);case "verify"->verify(h);default->throw new IllegalArgumentException("Unknown building restart phase");
  }
 }
 private static void prepare(GameTestHelper h){
  var level=h.getLevel();h.assertTrue(!MANIFEST.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null&&PlayerServerEvents.getRTSPlayer(ENEMY)==null,"fresh building restart profile only");
  var expected=new CompoundTag();expected.putInt("format",1);expected.putLong("prepareJvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
  List<Long> forced=new ArrayList<>();for(int x=8192;x<=8195;x++)for(int z=8192;z<=8195;z++){long key=ChunkPos.asLong(x,z);h.assertTrue(!level.getForcedChunks().contains(key),"new isolated ticket");level.setChunkForced(x,z,true);forced.add(key);}expected.putLongArray("forcedChunks",forced);arena(level);
  for(String owner:List.of(OWNER,ENEMY)){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(owner,123,234,345,456));player(level,owner);}
  var ownerAnchor=install(h,DreamseekersMod.DREAM_ANCHOR,at(4,0,40),OWNER,9401);
  var enemyAnchor=install(h,DreamseekersMod.DREAM_ANCHOR,at(40,0,40),ENEMY,9402);
  var target=install(h,DreamseekersMod.BEETLE_SANCTUARY,at(24,0,8),ENEMY,9403);
  Map<String,BuildingPlacement> buildings=new LinkedHashMap<>();buildings.put("ownerAnchor",ownerAnchor);buildings.put("enemyAnchor",enemyAnchor);buildings.put("targetBuilding",target);
  var caster=DreamseekersMod.SHADOW_GOLEM_UNIT.get().create(level);var contact=placeCasterAtExposedContact(h,caster,target);owned(h,caster,OWNER,caster.getX()-ORIGIN.getX(),caster.getZ()-ORIGIN.getZ(),9411);
  Map<String,Mob> bodies=new LinkedHashMap<>();bodies.put("paidCaster",caster);
  for(int i=0;i<2;i++){var e=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(level);owned(h,e,i==0?OWNER:ENEMY,i==0?6.5:54.5,28.5,9412+i);bodies.put(i==0?"ownerReserve":"enemyReserve",e);}
  expected.putLong("contact",contact.asLong());for(var row:bodies.entrySet())expected.putUUID(row.getKey(),row.getValue().getUUID());
  var diagnostics=new BuildingWindupDiagnostics(level,caster,contact,buildings);MinecraftForge.EVENT_BUS.register(diagnostics);diagnostics.sample("REGISTER",true);
  h.startSequence().thenWaitUntil(()->{for(var e:bodies.values())published(h,e);for(var p:PLAYERS.values())published(h,p);diagnostics.requireGeometry(h,"PREPARE_READY");h.assertTrue(buildings.values().stream().allMatch(b->b.getBlocksPlaced()>0&&b.isBuilt),"native templates entered normal building ticks");})
   .thenIdle(40).thenExecute(()->{
    h.assertTrue(!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isEnabled(),"actual fog-off isolated profile");ledger(h);
    diagnostics.sample("PREPAY",true);diagnostics.requireGeometry(h,"PREPAY");
    System.out.println("GOLEM_BUILDING_RESTART_PREPAY_CONDITIONS time="+level.getGameTime()+" noCollision="+level.noCollision(caster)+" contactPlaced="+target.isPosPartOfBuilding(contact,true)+" complete="+(target.getBlocksPlaced()==target.getBlocksTotal())+" targetPlaced="+target.getBlocksPlaced()+" targetTotal="+target.getBlocksTotal()+" targetPartial="+target.partialBlocksDestroyed+" targetLastAttacker="+BuildingWindupDiagnostics.actor(target.lastAttacker)+" "+diagnostics.casterState());
    h.assertTrue(level.noCollision(caster)&&target.isPosPartOfBuilding(contact,true)&&target.getBlocksPlaced()==target.getBlocksTotal(),"collision-safe original ground caster and complete native target");
    h.assertTrue(caster.getUnitAttackDamage()==16&&target.getMeleeDamageMult()>0&&caster.getUnitAttackDamage()*target.getMeleeDamageMult()*Math.max(2,caster.getBuildingDamageMultiplier())>=target.getHealthPerBlock()/2,"unchanged normal AD16/building health/coeff permits real block destruction; never rescale to fit");
    var roof=target.getBlocks().stream().map(BuildingBlock::getBlockPos)
      .filter(p->target.isPosPartOfBuilding(p,true)&&!level.getBlockState(p).getCollisionShape(level,p,net.minecraft.world.phys.shapes.CollisionContext.of(caster)).isEmpty()
        &&caster.position().distanceToSqr(Vec3.atCenterOf(p))>9)
      .max(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ)).orElseThrow();
    h.assertTrue(roof.getY()>contact.getY(),"actual native template roof above original exposed wall contact");
    var selection=GolemStrikeTargetSelection.<LivingEntity,BuildingPlacement>choose(null,()->
      GolemStrikeTargetSelection.building(caster,target,roof,
        b->BuildingServerEvents.getBuildings().stream().anyMatch(current->current==b),
        p->com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isBlockVisibleFor(PLAYERS.get(OWNER),p.getX(),p.getZ())));
    h.assertTrue(selection!=null&&selection.unit()==null&&selection.building()==target
      &&selection.contact().equals(contact),"actual shared selector roof fallback equals SAME original nearwall contact; no geometry change");
    BlockPos selectedContact=selection.contact();
    h.assertTrue(expected.getLong("contact")==selectedContact.asLong(),"original saved contact exactly equals selector output");
    System.out.println("GOLEM_BUILDING_CLIENT_SELECTION_NATIVE roof="+roof+" selected="+selectedContact+" sameOriginal=true");
    long now=level.getGameTime();expected.putLong("reservationGameTime",now);expected.putLong("snapshotGameTime",now);expected.putLong("releaseAt",now+20);expected.putLong("cooldownUntil",now+480);
    h.assertTrue(cast(h,caster,target,selectedContact)==SkillResult.SUCCESS&&cd(caster)==480&&ShadowGolemStrike.remainingTicks(caster)==20,"actual encoded building request reserves paid20/480");
    expected.putBoolean("snapshotActive",ShadowGolemStrike.active(caster));expected.putInt("snapshotWindup",20);expected.putInt("snapshotCooldown",480);expected.putUUID("permanentMovementUuid",MS_TECH);
    for(var row:bodies.entrySet())expected.put(row.getKey()+"State",bodyState(h,row.getValue()));
    for(var row:buildings.entrySet())expected.put(row.getKey(),buildingState(h,row.getValue()));
    expected.put("worldBlocks",worldBlocks(h,buildings.values()));
    try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception e){throw new RuntimeException(e);}
    System.out.println("DREAMSEEKERS_SHADOW_GOLEM_BUILDING_RESTART_PREPARED reservedAt="+now+" releaseAt="+(now+20)+" cooldownUntil="+(now+480)+" blocks="+target.getBlocksPlaced()+" partial="+target.partialBlocksDestroyed+" active="+ShadowGolemStrike.active(caster));
    // Normal server stopping performs BuildingSaveData and Anvil saves. No manual save/clear/replacement.
   }).thenSucceed();
 }
 private static void verify(GameTestHelper h)throws Exception{
  var level=h.getLevel();h.assertTrue(MANIFEST.isFile(),"original normal-save manifest");var expected=NbtIo.readCompressed(MANIFEST);
  h.assertTrue(expected.getInt("format")==1&&expected.getLong("prepareJvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"genuinely new JVM");
  h.assertTrue(expected.getLong("releaseAt")==expected.getLong("reservationGameTime")+20&&expected.getLong("cooldownUntil")==expected.getLong("reservationGameTime")+480&&expected.getBoolean("snapshotActive"),"original20/480 never enlarged");
  for(long key:expected.getLongArray("forcedChunks")){h.assertTrue(level.getForcedChunks().contains(key),"original saved tickets");var c=new ChunkPos(key);level.getChunk(c.x,c.z);}for(String owner:List.of(OWNER,ENEMY))player(level,owner);
  Map<String,Mob> bodies=new LinkedHashMap<>();Map<String,BuildingPlacement> buildings=new LinkedHashMap<>();long[] loaded={-1},fresh={-1};int[] before={0};BuildingReleaseProbe[] probe={null};BlockPos contact=BlockPos.of(expected.getLong("contact"));
  var diagnostics=new BuildingWindupDiagnostics(level,expected.getUUID("paidCaster"),contact,expected);MinecraftForge.EVENT_BUS.register(diagnostics);diagnostics.sample("VERIFY_REGISTER_BEFORE_CONTAIN",true);
  h.startSequence().thenWaitUntil(()->{for(String key:KEYS){var e=level.getEntity(expected.getUUID(key));h.assertTrue(e instanceof Mob,"native original body UUID: "+key);published(h,e);}for(var p:PLAYERS.values())published(h,p);})
   .thenExecute(()->{
    diagnostics.requireGeometry(h,"LOADED_NATIVE_NOAI_FALSE_BEFORE_CONTAIN");loaded[0]=level.getGameTime();h.assertTrue(loaded[0]<expected.getLong("cooldownUntil"),"loaded before original480 without extending clock");
    for(String key:List.of("ownerAnchor","enemyAnchor","targetBuilding")){var state=expected.getCompound(key);var origin=BlockPos.of(state.getLong("origin"));var matches=BuildingServerEvents.getBuildings().stream().filter(b->b.getLevel()==level&&b.originPos.equals(origin)).toList();h.assertTrue(matches.size()==1,"native ServerStarted loader created one registered original-origin placement: "+key);var b=matches.get(0);verifyBuilding(h,b,state);buildings.put(key,b);}
    unchangedBlocks(h,expected);
    for(String key:KEYS){var e=(Mob)level.getEntity(expected.getUUID(key));var state=expected.getCompound(key+"State");bodies.put(key,e);var u=(Unit)e;
     h.assertTrue(!e.isNoAi()&&!e.isNoGravity()&&e.getClass().getName().equals(state.getString("class"))&&EntityType.getKey(e.getType()).toString().equals(state.getString("type")),"observe native loaded ground AI before containment: "+key);
     h.assertTrue(u.getOwnerName().equals(state.getString("owner"))&&u.getScenarioRoleIndex()==state.getInt("scenario")&&u.getAnchor().asLong()==state.getLong("anchor"),"original identity/owner/role/anchor");close(h,e.getHealth(),state.getFloat("health"),"original body HP no restoration");permanent(h,e,state);h.assertTrue(e.getActiveEffects().isEmpty(),"no potion substitute");
    }
    var caster=(ShadowGolemUnit)bodies.get("paidCaster");h.assertTrue(caster.getUnitAttackDamage()==expected.getCompound("paidCasterState").getDouble("attackDamage")&&caster.getUnitAttackDamage()==16,"native loaded actual AD remains saved16, no attribute rewrite");h.assertTrue(cd(caster)==expected.getLong("cooldownUntil")-loaded[0]&&!ShadowGolemStrike.active(caster)&&ShadowGolemStrike.remainingTicks(caster)==0,"original paid480 but no restored building charge");
    h.assertTrue(caster.rts().skills().revision()>expected.getCompound("paidCasterState").getLong("revision"),"native load advances revision");for(var body:bodies.values())body.setNoAi(true);
    h.assertTrue(cast(h,caster,buildings.get("targetBuilding"),contact)==SkillResult.COOLDOWN,"load cannot refund old paid reservation");ledger(h);
    System.out.println("SHADOW_GOLEM_BUILDING_RESTART_LOADED observedAt="+loaded[0]+" originalReleaseAt="+expected.getLong("releaseAt")+" cooldownUntil="+expected.getLong("cooldownUntil")+" blocks="+buildings.get("targetBuilding").getBlocksPlaced());
   }).thenIdle(21).thenExecute(()->{diagnostics.requireGeometry(h,"LOADED21");unchangedBlocks(h,expected);var caster=(ShadowGolemUnit)bodies.get("paidCaster");h.assertTrue(level.getGameTime()>expected.getLong("releaseAt")&&!ShadowGolemStrike.active(caster),"old pending charge cannot replay after natural loaded21");})
   .thenWaitUntil(()->{diagnostics.requireGeometry(h,"NATURAL480_WAIT");unchangedBlocks(h,expected);h.assertTrue(level.getGameTime()>=expected.getLong("cooldownUntil"),"wait original absolute480, no clock edit");})
   .thenExecute(()->{diagnostics.requireGeometry(h,"BEFORE_FRESH_CAST");var b=buildings.get("targetBuilding");verifyBuilding(h,b,expected.getCompound("targetBuilding"));var caster=(ShadowGolemUnit)bodies.get("paidCaster");h.assertTrue(caster.getUnitAttackDamage()==expected.getCompound("paidCasterState").getDouble("attackDamage")&&caster.getUnitAttackDamage()==16,"fresh building release starts from unchanged saved16 actual AD");fresh[0]=level.getGameTime();before[0]=b.getBlocksPlaced();probe[0]=new BuildingReleaseProbe(level,b,before[0]);diagnostics.freshReleaseAt(fresh[0]+20,probe[0]);MinecraftForge.EVENT_BUS.register(probe[0]);h.assertTrue(cd(caster)==0&&cast(h,caster,b,contact)==SkillResult.SUCCESS&&ShadowGolemStrike.remainingTicks(caster)==20&&cd(caster)==480,"fresh real building request on native-loaded original body and placement");})
   .thenIdle(19).thenExecute(()->{diagnostics.requireGeometry(h,"FRESH19");unchangedBlocks(h,expected);h.assertTrue(buildings.get("targetBuilding").getBlocksPlaced()==before[0],"fresh19 no early block write");})
   .thenWaitUntil(()->{diagnostics.requireGeometry(h,"FRESH20_WAIT");h.assertTrue(buildings.get("targetBuilding").getBlocksPlaced()<before[0]&&probe[0].firstChangedGameTime>=0,"natural new20 releases actual building block damage");})
   .thenExecute(()->{
    diagnostics.requireGeometry(h,"FRESH20_RESULT");var b=buildings.get("targetBuilding");var caster=(ShadowGolemUnit)bodies.get("paidCaster");h.assertTrue(probe[0].firstChangedGameTime==fresh[0]+20&&level.getGameTime()>=fresh[0]+20&&level.getGameTime()<=fresh[0]+21&&!ShadowGolemStrike.active(caster)&&cd(caster)==fresh[0]+480-level.getGameTime(),"new20 actual block removal and own exact paid480");
    h.assertTrue(!b.isDestroyedServerside&&b.ownerName.equals(ENEMY)&&b.originPos.asLong()==expected.getCompound("targetBuilding").getLong("origin"),"same native loaded placement remains lawful");
    for(String key:KEYS){close(h,bodies.get(key).getHealth(),expected.getCompound(key+"State").getFloat("health"),"no unrelated body damage");permanent(h,bodies.get(key),expected.getCompound(key+"State"));}
    MinecraftForge.EVENT_BUS.unregister(probe[0]);MinecraftForge.EVENT_BUS.unregister(diagnostics);ledger(h);System.out.println("DREAMSEEKERS_SHADOW_GOLEM_BUILDING_RESTART_NATIVE_SAVE_NO_REPLAY_FRESH20_PASS loadedAt="+loaded[0]+" originalCooldownUntil="+expected.getLong("cooldownUntil")+" freshReservedAt="+fresh[0]+" hitAt="+probe[0].firstChangedGameTime+" observedAt="+level.getGameTime()+" blocksBefore="+before[0]+" blocksAfter="+b.getBlocksPlaced());
    // Test completion does not replace or manually resave any original body/building.
   }).thenSucceed();
 }
 /** Observation only: no AI/goal/attribute/velocity/clock/world mutation. START/END snapshots
  * expose changed state within a natural tick; they are not a call-stack or every intra-tick-write trace. */
 public static final class BuildingWindupDiagnostics {
  private final ServerLevel level;private ShadowGolemUnit caster;private final BlockPos contact;
  private final UUID casterUuid;private final Map<String,BlockPos> origins=new LinkedHashMap<>();
  private String geometryViolation=null,lastGeometry=null;private long contactRequiredUntil=Long.MAX_VALUE;private BuildingReleaseProbe releaseProbe;
  private final Map<String,BuildingPlacement> buildings;private final Map<String,String> previous=new HashMap<>();
  BuildingWindupDiagnostics(ServerLevel level,ShadowGolemUnit caster,BlockPos contact,Map<String,BuildingPlacement> buildings){this.level=level;this.caster=caster;this.casterUuid=caster.getUUID();this.contact=contact;this.buildings=new LinkedHashMap<>(buildings);for(var row:buildings.entrySet())origins.put(row.getKey(),row.getValue().originPos.immutable());}
  BuildingWindupDiagnostics(ServerLevel level,UUID casterUuid,BlockPos contact,CompoundTag expected){this.level=level;this.casterUuid=casterUuid;this.contact=contact;this.buildings=new LinkedHashMap<>();for(String key:List.of("ownerAnchor","enemyAnchor","targetBuilding"))origins.put(key,BlockPos.of(expected.getCompound(key).getLong("origin")));}
  private void resolveNative(){if(caster==null&&level.getEntity(casterUuid) instanceof ShadowGolemUnit original)caster=original;for(var row:origins.entrySet())if(!buildings.containsKey(row.getKey())){var found=BuildingServerEvents.getBuildings().stream().filter(b->b.getLevel()==level&&b.originPos.equals(row.getValue())).toList();if(found.size()==1)buildings.put(row.getKey(),found.get(0));}}
  void freshReleaseAt(long time,BuildingReleaseProbe probe){contactRequiredUntil=time;releaseProbe=probe;}
  void requireGeometry(GameTestHelper h,String checkpoint){sample(checkpoint,false);h.assertTrue(caster!=null&&buildings.containsKey("targetBuilding"),"actual original source/registered building available for geometry: "+checkpoint);h.assertTrue(geometryViolation==null,"strict naturally observed geometry remained isolated: "+checkpoint+" firstViolation="+geometryViolation);}
  private void geometry(String phase,boolean force){if(caster==null||!caster.isAddedToWorld())return;var target=buildings.get("targetBuilding");if(target==null)return;
   var foot=caster.getOnPos();boolean inside=target.isPosInsideBuilding(foot,2),collision=level.noCollision(caster);double distance=caster.position().distanceToSqr(Vec3.atCenterOf(contact));
   var goal=caster.getAttackBuildingGoal();boolean attacking=goal instanceof com.solegendary.reignofnether.unit.goals.MeleeAttackBuildingGoal melee&&melee.getBuildingTarget()==target&&melee.isAttacking();
   var hit=level.clip(new net.minecraft.world.level.ClipContext(caster.getEyePosition(),Vec3.atCenterOf(contact),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,caster));boolean clip=hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(contact);boolean placed=target.isPosPartOfBuilding(contact,true),contactRequired=releaseProbe==null||releaseProbe.firstChangedGameTime!=contactRequiredUntil;
   String state="foot="+foot+" ordinaryInsideExpanded2="+inside+" actualOrdinaryAttacking="+attacking+" noCollision="+collision+" skillDistanceSquared="+distance+" clipContact="+clip+" contactPlaced="+placed+" contactRequired="+contactRequired+" "+casterState();
   boolean valid=!inside&&!attacking&&collision&&Double.isFinite(distance)&&distance<=9&&(!contactRequired||(clip&&placed));
   if(force||!state.equals(lastGeometry)||!valid){System.out.println("GOLEM_BUILDING_RESTART_GEOMETRY time="+level.getGameTime()+" phase="+phase+" valid="+valid+" "+state);lastGeometry=state;}
   if(!valid&&geometryViolation==null)geometryViolation="time="+level.getGameTime()+" phase="+phase+" "+state;
  }
  @SubscribeEvent public void shadowGolemBuildingRestartDiagnosticNaturalLevelTick(net.minecraftforge.event.TickEvent.LevelTickEvent event){if(event.level==level)sample(event.phase.name(),false);}
  static String actor(Entity entity){return entity==null?"null":entity.getType()+"/"+entity.getUUID()+"/id="+entity.getId();}
  private static String building(BuildingPlacement b){return b==null?"null":ReignOfNetherRegistries.BUILDING.getKey(b.getBuilding())+"/origin="+b.originPos+"/owner="+b.ownerName+"/instance="+System.identityHashCode(b);}
  private static String ordinaryCounter(Object goal){if(goal==null)return "null";for(Class<?> c=goal.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField("ticksToNextBlockBreak");f.setAccessible(true);return String.valueOf(f.get(goal));}catch(NoSuchFieldException ignored){}catch(ReflectiveOperationException failure){return "read-unavailable:"+failure.getClass().getSimpleName();}return "field-not-present:"+goal.getClass().getName();}
  String casterState(){var goal=caster.getAttackBuildingGoal();var target=goal instanceof com.solegendary.reignofnether.unit.goals.MeleeAttackBuildingGoal melee?melee.getBuildingTarget():null;return "caster="+actor(caster)+" position="+caster.position()+" noAI="+caster.isNoAi()+" onGround="+caster.onGround()+" delta="+caster.getDeltaMovement()+" ordinaryBuildingGoal="+(goal==null?"null":goal.getClass().getName())+" ordinaryBuildingTarget="+building(target)+" ticksToNextBlockBreak="+ordinaryCounter(goal)+" attackCooldown="+caster.getAttackCooldown()+" unitTarget="+actor(caster.getTarget())+" skillCD="+cd(caster)+" commandRevision="+caster.rts().commandRevision+" contact="+contact;}
  void sample(String phase,boolean force){resolveNative();geometry(phase,force);if(caster==null)return;for(var row:buildings.entrySet()){var b=row.getValue();String state="placed="+b.getBlocksPlaced()+" total="+b.getBlocksTotal()+" partial="+Double.toHexString(b.partialBlocksDestroyed)+" lastAttacker="+actor(b.lastAttacker);String prior=previous.put(row.getKey(),state);if(force||!state.equals(prior))System.out.println("GOLEM_BUILDING_RESTART_NATIVE_PLACEMENT_DIAGNOSTIC time="+level.getGameTime()+" phase="+phase+" key="+row.getKey()+" building="+building(b)+" prior={"+prior+"} current={"+state+"} "+casterState());}}
 }
 public static final class BuildingReleaseProbe {
  final ServerLevel level;final BuildingPlacement building;final int originalCount;long firstChangedGameTime=-1;
  BuildingReleaseProbe(ServerLevel l,BuildingPlacement b,int count){level=l;building=b;originalCount=count;}
  @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST) public void shadowGolemBuildingRestartObserveExactNativeReleaseEnd(net.minecraftforge.event.TickEvent.LevelTickEvent event){if(event.level==level&&event.phase==net.minecraftforge.event.TickEvent.Phase.END&&firstChangedGameTime<0&&building.getBlocksPlaced()<originalCount)firstChangedGameTime=level.getGameTime();}
 }
 private static BuildingPlacement install(GameTestHelper h,Building definition,BlockPos origin,String owner,int role){
  var level=h.getLevel();var blocks=BuildingUtils.getAbsoluteBlockData(definition.getRelativeBlockData(level),level,origin,Rotation.NONE);
  for(var block:blocks){var p=block.getBlockPos();h.assertTrue(level.getBlockState(p).isAir(),"native template may only occupy preflight-proven air, never replace natural terrain");h.assertTrue(p.getX()>ORIGIN.getX()&&p.getX()<ORIGIN.getX()+63&&p.getZ()>ORIGIN.getZ()&&p.getZ()<ORIGIN.getZ()+63&&p.getY()>=ORIGIN.getY()&&p.getY()<ORIGIN.getY()+16,"complete original template stays within one-time isolated fixture");level.setBlock(p,block.getBlockState(),2);FIXTURE_BLOCKS.add(p.immutable());}
  var b=BuildingUtils.getNewBuildingPlacement(definition,level,origin,Rotation.NONE,owner,false);h.assertTrue(b instanceof DreamProductionPlacement,"real registered definition factory, not test subclass");BuildingServerEvents.getBuildings().add(b);b.scenarioRoleIndex=role;b.onBuilt();return b;
 }
 private static BlockPos placeCasterAtExposedContact(GameTestHelper h,ShadowGolemUnit caster,BuildingPlacement b){
  for(var block:b.getBlocks())if(!block.getBlockState().isAir()&&block.getBlockPos().getY()>=ORIGIN.getY()+1&&block.getBlockPos().getY()<=ORIGIN.getY()+2){var contact=block.getBlockPos();for(var d:List.of(Direction.WEST,Direction.EAST,Direction.NORTH,Direction.SOUTH)){
   caster.moveTo(contact.getX()+.5+d.getStepX()*2.75,ORIGIN.getY()+1,contact.getZ()+.5+d.getStepZ()*2.75);
   if(!h.getLevel().noCollision(caster)||caster.position().distanceToSqr(Vec3.atCenterOf(contact))>9||b.isPosInsideBuilding(caster.getOnPos(),2))continue;
   if(caster.getAttackBuildingGoal() instanceof com.solegendary.reignofnether.unit.goals.MeleeAttackBuildingGoal ordinary&&ordinary.getBuildingTarget()==b&&ordinary.isAttacking())continue;
   var hit=h.getLevel().clip(new net.minecraft.world.level.ClipContext(caster.getEyePosition(),Vec3.atCenterOf(contact),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,caster));
   if(hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(contact))return contact;
  }}throw new IllegalStateException("No actual exposed native full-template contact outside native ordinary building range but within actual skill3, collision/clip legal; do not excavate/shrink geometry or disable ordinary AI");
 }
 private static CompoundTag buildingState(GameTestHelper h,BuildingPlacement b){
  var t=new CompoundTag();t.putString("key",ReignOfNetherRegistries.BUILDING.getKey(b.getBuilding()).toString());t.putString("class",b.getClass().getName());t.putString("owner",b.ownerName);t.putLong("origin",b.originPos.asLong());t.putInt("scenario",b.scenarioRoleIndex);t.putString("rotation",b.rotation.name());t.putBoolean("built",b.isBuilt);t.putDouble("partial",b.partialBlocksDestroyed);t.putInt("placed",b.getBlocksPlaced());t.putInt("total",b.getBlocksTotal());t.putDouble("healthPerBlock",b.getHealthPerBlock());t.putDouble("meleeCoefficient",b.getMeleeDamageMult());h.assertTrue(((DreamProductionPlacement)b).productionQueue.isEmpty(),"no unpaid new economy or queue fixture");return t;
 }
 private static void verifyBuilding(GameTestHelper h,BuildingPlacement b,CompoundTag t){
  h.assertTrue(b instanceof DreamProductionPlacement&&b.getClass().getName().equals(t.getString("class"))&&ReignOfNetherRegistries.BUILDING.getKey(b.getBuilding()).toString().equals(t.getString("key"))&&b.ownerName.equals(t.getString("owner"))&&b.originPos.asLong()==t.getLong("origin")&&b.rotation.name().equals(t.getString("rotation"))&&b.scenarioRoleIndex==t.getInt("scenario")&&b.isBuilt==t.getBoolean("built")&&!b.isDestroyedServerside,"native registered key/placement owner/origin/rotation/role/built identity");
  h.assertTrue(b.getBlocksPlaced()==t.getInt("placed")&&b.getBlocksTotal()==t.getInt("total"),"original complete registered block counts");close(h,b.partialBlocksDestroyed,t.getDouble("partial"),"no pending charge fractional debit");close(h,b.getHealthPerBlock(),t.getDouble("healthPerBlock"),"native original per-block health");close(h,b.getMeleeDamageMult(),t.getDouble("meleeCoefficient"),"native coefficient unchanged");h.assertTrue(((DreamProductionPlacement)b).productionQueue.isEmpty(),"native production data remains empty");
 }
 private static ListTag worldBlocks(GameTestHelper h,Collection<BuildingPlacement> buildings){var list=new ListTag();var seen=new HashSet<BlockPos>(FIXTURE_BLOCKS);for(var b:buildings)for(var block:b.getBlocks())seen.add(block.getBlockPos());for(var p:seen){var t=new CompoundTag();t.putInt("x",p.getX());t.putInt("y",p.getY());t.putInt("z",p.getZ());t.put("state",NbtUtils.writeBlockState(h.getLevel().getBlockState(p)));list.add(t);}return list;}
 private static void unchangedBlocks(GameTestHelper h,CompoundTag expected){for(var raw:expected.getList("worldBlocks",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;var p=new BlockPos(t.getInt("x"),t.getInt("y"),t.getInt("z"));h.assertTrue(NbtUtils.writeBlockState(h.getLevel().getBlockState(p)).equals(t.getCompound("state")),"original world block unchanged before fresh release: "+p);}}
 private static CompoundTag bodyState(GameTestHelper h,Mob e){var u=(Unit)e;var t=new CompoundTag();t.putString("class",e.getClass().getName());t.putString("type",EntityType.getKey(e.getType()).toString());t.putString("owner",u.getOwnerName());t.putInt("scenario",u.getScenarioRoleIndex());t.putFloat("health",e.getHealth());t.putLong("anchor",u.getAnchor().asLong());t.putDouble("movementBase",e.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue());if(e instanceof ShadowGolemUnit g){t.putLong("revision",g.rts().skills().revision());t.putDouble("attackDamage",g.getUnitAttackDamage());}permanent(h,e,t);return t;}
 private static void permanent(GameTestHelper h,Mob e,CompoundTag t){var a=e.getAttribute(Attributes.MOVEMENT_SPEED);h.assertTrue(a.getModifiers().size()==1&&a.getModifier(MS_TECH)!=null,"one genuine permanent modifier only");close(h,a.getBaseValue(),t.getDouble("movementBase"),"original base");close(h,a.getValue(),t.getDouble("movementBase")+.03,"permanent-aware value");}
 private static int cd(ShadowGolemUnit source){return source.rts().skills().cooldownTicks(UnitSkill.SHADOW_GOLEM_STRIKE);}
 private static SkillResult cast(GameTestHelper h,ShadowGolemUnit source,BuildingPlacement b,BlockPos contact){var p=new SkillNetwork.BuildingCast(new SkillBuildingRequest(UnitSkill.SHADOW_GOLEM_STRIKE,source.getId(),source.getUUID(),source.rts().skills().revision(),b.originPos,contact));var bytes=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());SkillNetwork.BuildingCast decoded;try{p.encode(bytes);decoded=SkillNetwork.BuildingCast.decode(bytes);h.assertTrue(decoded.equals(p)&&bytes.readableBytes()==0,"actual server building request wire codec");}finally{bytes.release();}return decoded.execute(player(h.getLevel(),source.getOwnerName()));}
    private static void owned(GameTestHelper h,Mob body,String owner,double x,double z,int role) {
        var unit=(Unit)body;unit.setOwnerName(owner);unit.setScenarioRoleIndex(role);unit.setOnDeathCommand("");
        body.setNoAi(true);body.setPersistenceRequired();body.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);
        neutral(body);position(body,new Vec3(ORIGIN.getX()+x,ORIGIN.getY()+1,ORIGIN.getZ()+z));unit.setAnchor(body.blockPosition().below());
        body.getAttribute(Attributes.MOVEMENT_SPEED).addPermanentModifier(new AttributeModifier(MS_TECH,"actual permanent restart movement research",.03,AttributeModifier.Operation.ADDITION));
        support(h.getLevel(),body.blockPosition().below());
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
        return PLAYERS.computeIfAbsent(name,key->{var p=new FakePlayer(level,new GameProfile(UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),key));p.connection=new SilentHandler(p);position(p,Vec3.atBottomCenterOf(at(key.equals(OWNER)?2:29,1,28)));if(!MANIFEST.exists())support(level,p.blockPosition().below());level.addNewPlayer(p);PlayerServerEvents.players.add(p);membership(p,true);return p;});
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
        System.out.println("SHADOW_GOLEM_BUILDING_RESTART_LEDGER time="+h.getLevel().getGameTime()+" banks=123,234,345,456 pending=0 cargo=empty");
    }
    private static void arena(ServerLevel level){
      if(ORIGIN.getY()+16>=level.getMaxBuildHeight())throw new IllegalStateException("Insufficient legal high-air fixture height; never dig terrain to compensate");
      // Read-only high-air preflight: no broad clearing, roof, basement or natural-block replacement.
      for(int x=1;x<63;x++)for(int z=1;z<63;z++)for(int y=0;y<16;y++)if(!level.getBlockState(at(x,y,z)).isAir())throw new IllegalStateException("High-air fixture contains preexisting blocks at "+at(x,y,z));
    }
    private static void support(ServerLevel level,BlockPos center){
      for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){var p=center.offset(x,0,z);if(!level.getBlockState(p).isAir()&&!FIXTURE_BLOCKS.contains(p))throw new IllegalStateException("Refuse replacement of non-fixture support block: "+p);}
      for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){var p=center.offset(x,0,z);if(level.getBlockState(p).isAir())level.setBlock(p,Blocks.STONE.defaultBlockState(),2);FIXTURE_BLOCKS.add(p.immutable());}
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
