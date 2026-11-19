// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.AbstractMeleeAttackUnitGoal;
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

/** Two real JVMs and normal Anvil storage, not test-deserialized replacement entities.
 * read_saved_corrosion_restart.py must prove original bodies/paid deadlines reached disk while
 * the observed arm and debuff deadlines were still in the future. Real loaded AI policy is asserted
 * before controlled ordinary-goal probes; those probes are not autonomous combat cadence claims.
 */
@GameTestHolder("dreamseekers_shadow_corrosion_touch_restart")
@PrefixGameTestTemplate(false)
public final class ShadowCorrosionTouchSkillRestartGameTests {
    private static final String OWNER="CorrosionRestart_Owner",ENEMY="CorrosionRestart_Enemy";
    private static final BlockPos ORIGIN=new BlockPos(94208,90,94208);
    private static final File MANIFEST=new File("shadow-corrosion-touch-restart-expectations.nbt");
    private static final UUID ARMOR_TECH=UUID.fromString("fa804c55-c383-421b-8795-18660e479628");
    private static final List<String> SOURCES=List.of("armedCaster","spentCaster","unusedCaster");
    private static final List<String> KEYS=List.of("armedCaster","spentCaster","unusedCaster","oldTarget","freshTarget");
    private static final Map<String,FakePlayer> PLAYERS=new HashMap<>();

    @GameTest(template="unit_isolation_arena",batch="dreamseekers_shadow_corrosion_touch_restart",timeoutTicks=1450)
    public static void originalUuidAndAbsoluteCooldownSurviveRealRestartButSavedArmAndCorrosionNeverReplay(GameTestHelper h) throws Exception {
        switch(System.getProperty("dreamseekers.shadowCorrosionTouch.test.phase","")) {
            case "prepare"->prepare(h);case "verify"->verify(h);
            default->throw new IllegalArgumentException("Unknown corrosion restart phase");
        }
    }
    private static void prepare(GameTestHelper h) {
        var level=h.getLevel();h.assertTrue(!MANIFEST.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null&&PlayerServerEvents.getRTSPlayer(ENEMY)==null,"new isolated prepare world, never reuse already-consumed verification");
        var expected=new CompoundTag();expected.putInt("format",1);expected.putLong("prepareJvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        List<Long> forced=new ArrayList<>();for(int x=5888;x<=5889;x++)for(int z=5888;z<=5889;z++){long key=ChunkPos.asLong(x,z);h.assertTrue(!level.getForcedChunks().contains(key),"only new fixture chunk tickets");level.setChunkForced(x,z,true);forced.add(key);}expected.putLongArray("forcedChunks",forced);arena(level);
        for(String name:List.of(OWNER,ENEMY)){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,123,234,345,456));player(level,name);}
        Map<String,Mob> bodies=new LinkedHashMap<>();
        for(int i=0;i<3;i++){var source=DreamseekersMod.SHADOW_GHOST_UNIT.get().create(level);owned(h,source,OWNER,4,5+10*i,5001+i);damage(source,10);source.setAnchor(at(4,0,5+10*i));bodies.put(SOURCES.get(i),source);}
        for(int i=0;i<2;i++){var target=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(level);owned(h,target,ENEMY,24,15+10*i,5004+i);target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);target.getAttribute(Attributes.ARMOR).setBaseValue(6);target.getAttribute(Attributes.ARMOR).addPermanentModifier(new AttributeModifier(ARMOR_TECH,"actual permanent restart armor research",2,AttributeModifier.Operation.ADDITION));bodies.put(KEYS.get(3+i),target);}
        for(var entry:bodies.entrySet())expected.putUUID(entry.getKey(),entry.getValue().getUUID());
        h.startSequence().thenWaitUntil(()->{for(var body:bodies.values())published(h,body);for(var p:PLAYERS.values())published(h,p);})
          .thenExecute(()->{
              h.assertTrue(!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isEnabled(),"actual restart profile uses fog off");ledger(h);
              var armed=(ShadowGhostUnit)bodies.get("armedCaster");var spent=(ShadowGhostUnit)bodies.get("spentCaster");var old=bodies.get("oldTarget");
              h.assertTrue(cast(level,armed)==SkillResult.SUCCESS&&cast(level,spent)==SkillResult.SUCCESS,"two actual authenticated self casts pay independent CD320");
              double ordinary=nativeDamage(spent,old);close(h,hit(h,spent,old),ordinary,"real ordinary goal HP uses original permanent eight armor before corrosion");
              h.assertTrue(arm(armed)==100&&arm(spent)==0&&ShadowCorrosionTouch.remainingDebuffTicks(old)==120&&old.getArmorValue()==6,"genuine saved pending arm and installed after-hit debuff are both live");
              position(spent,Vec3.atBottomCenterOf(at(4,1,15)));spent.setHoldPosition(false);
              h.assertTrue(spent.distanceToSqr(old)>100&&spent.getTarget()==null&&!spent.getHoldPosition(),"normal-save fixture has no lingering close automatic ordinary target/hold");
              expected.putLong("snapshotGameTime",level.getGameTime());expected.putLong("armExpiresAt",level.getGameTime()+100);expected.putLong("debuffExpiresAt",level.getGameTime()+120);expected.putUUID("permanentArmorUuid",ARMOR_TECH);expected.putUUID("transientArmorUuid",ShadowCorrosionTouch.MODIFIER_ID);
              for(var entry:bodies.entrySet()){
                  var body=entry.getValue();var unit=(Unit)body;var state=new CompoundTag();state.putString("type",EntityType.getKey(body.getType()).toString());state.putString("class",body.getClass().getName());state.putString("owner",unit.getOwnerName());state.putInt("scenario",unit.getScenarioRoleIndex());state.putFloat("health",body.getHealth());state.putDouble("armorBase",body.getAttribute(Attributes.ARMOR).getBaseValue());
                  if(body instanceof ShadowGhostUnit source){state.putInt("cooldown",cd(source));state.putLong("revision",source.rts().skills().revision());state.putLong("anchor",source.getAnchor().asLong());h.assertTrue(!source.getAnchor().equals(BlockPos.ZERO),"actual nonzero original anchor saved");}
                  else h.assertTrue(body.getAttribute(Attributes.ARMOR).getModifier(ARMOR_TECH)!=null,"actual permanent target research exists");
                  expected.put(entry.getKey()+"State",state);
              }
              h.assertTrue(cd(armed)==320&&cd(spent)==320&&cd((ShadowGhostUnit)bodies.get("unusedCaster"))==0,"paid versus unused original casters remain distinct");ledger(h);
              try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception error){throw new RuntimeException(error);}
              System.out.println("DREAMSEEKERS_SHADOW_CORROSION_RESTART_PREPARED time="+level.getGameTime()+" arm100/debuff120/CD320 originals="+KEYS.stream().map(key->key+":"+bodies.get(key).getUUID()).toList());
              // Leave bodies/tickets to actual normal shutdown. External read-only disk gate runs before verify.
          }).thenSucceed();
    }
    private static void verify(GameTestHelper h) throws Exception {
        var level=h.getLevel();h.assertTrue(MANIFEST.isFile(),"original prepare expectation manifest exists");var expected=NbtIo.readCompressed(MANIFEST);
        h.assertTrue(expected.getInt("format")==1&&expected.getLong("prepareJvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"actual distinct JVM, not same-process reload fixture");
        for(long key:expected.getLongArray("forcedChunks")){h.assertTrue(level.getForcedChunks().contains(key),"original forced chunk survives normal save");var c=new ChunkPos(key);level.getChunk(c.x,c.z);}for(String name:List.of(OWNER,ENEMY))player(level,name);
        Map<String,Mob> bodies=new LinkedHashMap<>();long[] elapsedAtLoad={-1},freshAt={-1};float[] oldAfterProbes={0},freshAfterProbes={0};
        h.startSequence().thenWaitUntil(()->{for(String key:KEYS){var body=level.getEntity(expected.getUUID(key));h.assertTrue(body instanceof Mob,"real ordinary world loader returns original UUID: "+key);published(h,body);}for(var p:PLAYERS.values())published(h,p);})
          .thenExecute(()->{
              long elapsed=level.getGameTime()-expected.getLong("snapshotGameTime");elapsedAtLoad[0]=elapsed;
              h.assertTrue(elapsed>=0&&level.getGameTime()<expected.getLong("armExpiresAt")&&level.getGameTime()<expected.getLong("debuffExpiresAt"),"observe original saved short-state deadlines still in future, not absence after natural expiry: elapsed="+elapsed);
              for(String key:KEYS){
                  var body=(Mob)level.getEntity(expected.getUUID(key));bodies.put(key,body);var unit=(Unit)body;var state=expected.getCompound(key+"State");
                  h.assertTrue(!body.isNoAi()&&body.getClass().getName().equals(state.getString("class"))&&EntityType.getKey(body.getType()).toString().equals(state.getString("type"))&&unit.getOwnerName().equals(state.getString("owner"))&&unit.getScenarioRoleIndex()==state.getInt("scenario"),"actual paid-loader restores AI=false flag to active AI and exact original type/class/owner/role: "+key);
                  h.assertTrue(UnitServerEvents.getAllUnits().stream().filter(e->e.getUUID().equals(body.getUUID())).count()==1,"exactly one actual original roster body: "+key);
                  close(h,body.getHealth(),state.getFloat("health"),"no autonomous save/load attack or skill replay altered original HP: "+key);close(h,body.getAttribute(Attributes.ARMOR).getBaseValue(),state.getDouble("armorBase"),"original native armor base unchanged: "+key);
                  h.assertTrue(!ShadowCorrosionTouch.hasDebuff(body)&&body.getAttribute(Attributes.ARMOR).getModifier(ShadowCorrosionTouch.MODIFIER_ID)==null,"no saved transient armor modifier/lease reappears before old expiry: "+key);
                  if(body instanceof ShadowGhostUnit source){h.assertTrue(arm(source)==0&&cd(source)==Math.max(0,state.getInt("cooldown")-elapsed)&&source.rts().skills().revision()>=state.getLong("revision"),"no arm replay but exact original paid absolute cooldown: "+key);h.assertTrue(source.getAnchor().equals(BlockPos.of(state.getLong("anchor")))&&!source.getAnchor().equals(BlockPos.ZERO),"original nonzero anchor survives without invented restore");}
                  else h.assertTrue(body.getArmorValue()==8&&body.getAttribute(Attributes.ARMOR).getModifier(ARMOR_TECH)!=null&&body.getAttribute(Attributes.ARMOR).getModifiers().size()==1,"original target permanent armor alone survives");
              }
              // AFTER all original identity/AI/HP/clock/short-state observations: isolate unrelated ordinary AI.
              // Corrosion permits NoAI manual use; genuine installed-goal probes below supply independent positives.
              for(var body:bodies.values())body.setNoAi(true);
              var armed=(ShadowGhostUnit)bodies.get("armedCaster");var spent=(ShadowGhostUnit)bodies.get("spentCaster");var unused=(ShadowGhostUnit)bodies.get("unusedCaster");var old=bodies.get("oldTarget");var fresh=bodies.get("freshTarget");
              h.assertTrue(cast(level,armed)==SkillResult.COOLDOWN&&cast(level,spent)==SkillResult.COOLDOWN,"both original paid sources reject free rearm after true restart");
              double oldBase=nativeDamage(armed,old);close(h,hit(h,armed,old),oldBase,"saved but unspent source's actual ordinary hit cannot replay touch");
              close(h,hit(h,spent,old),oldBase,"already-spent source's actual ordinary hit also cannot replay touch");
              h.assertTrue(old.getArmorValue()==8&&!ShadowCorrosionTouch.hasDebuff(old),"both old sources leave current original armor intact");
              double first=nativeDamage(unused,fresh);freshAt[0]=level.getGameTime();h.assertTrue(cd(unused)==0&&cast(level,unused)==SkillResult.SUCCESS,"genuinely unused original loaded source makes fresh real authenticated self cast");
              close(h,hit(h,unused,fresh),first,"fresh first ordinary HP remains original pre-corrosion base");
              h.assertTrue(arm(unused)==0&&ShadowCorrosionTouch.remainingDebuffTicks(fresh)==120&&fresh.getArmorValue()==6,"fresh ordinary actual receipt alone grants one new live corrosion");
              double second=nativeDamage(unused,fresh);close(h,hit(h,unused,fresh),second,"fresh next ordinary hit independently uses new actual armor");h.assertTrue(second>first&&ShadowCorrosionTouch.remainingDebuffTicks(fresh)==120,"later ordinary contact neither reuses arm nor refreshes original deadline");
              oldAfterProbes[0]=old.getHealth();freshAfterProbes[0]=fresh.getHealth();for(int i=0;i<3;i++){var source=(ShadowGhostUnit)bodies.get(SOURCES.get(i));position(source,Vec3.atBottomCenterOf(at(4,1,5+10*i)));source.setHoldPosition(false);}
              ledger(h);
          }).thenIdle(121).thenExecute(()->{
              h.assertTrue(level.getGameTime()==freshAt[0]+121&&!ShadowCorrosionTouch.hasDebuff(bodies.get("freshTarget"))&&bodies.get("freshTarget").getArmorValue()==8,"new genuine temporary armor expires normally without deleting permanent research");
              close(h,bodies.get("oldTarget").getHealth(),oldAfterProbes[0],"old saved target never receives delayed replay");close(h,bodies.get("freshTarget").getHealth(),freshAfterProbes[0],"fixed controlled new ordinary hits are the only HP changes");
              for(String key:List.of("armedCaster","spentCaster")){var source=(ShadowGhostUnit)bodies.get(key);long expiry=expected.getLong("snapshotGameTime")+expected.getCompound(key+"State").getInt("cooldown");h.assertTrue(arm(source)==0&&cd(source)==Math.max(0,expiry-level.getGameTime())&&!ShadowCorrosionTouch.hasDebuff(bodies.get("oldTarget")),"old deadlines never revive arm/debuff or refund original paid clock");}
              ledger(h);System.out.println("DREAMSEEKERS_SHADOW_CORROSION_RESTART_UUID_CD_NO_ARM_OR_ARMOR_REPLAY_PASS elapsedAtLoad="+elapsedAtLoad[0]+" finalElapsed="+(level.getGameTime()-expected.getLong("snapshotGameTime"))+" originalUUIDs="+KEYS.stream().map(key->bodies.get(key).getUUID()).toList());
              for(var body:bodies.values())body.discard();for(var p:PLAYERS.values())p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);PLAYERS.clear();PlayerServerEvents.players.removeIf(p->List.of(OWNER,ENEMY).contains(p.getGameProfile().getName()));PlayerServerEvents.rtsPlayers.removeIf(p->List.of(OWNER,ENEMY).contains(p.name));ResourcesServerEvents.resourcesList.removeIf(r->List.of(OWNER,ENEMY).contains(r.ownerName));for(long key:expected.getLongArray("forcedChunks")){var c=new ChunkPos(key);level.setChunkForced(c.x,c.z,false);}
          }).thenSucceed();
    }

    private static SkillResult cast(ServerLevel level,ShadowGhostUnit source){return new SkillNetwork.Cast(new SkillRequest(UnitSkill.SHADOW_CORROSION_TOUCH,source.getId(),source.getUUID(),source.rts().skills().revision(),source.getId(),source.getUUID())).execute(player(level,OWNER));}
    private static int cd(ShadowGhostUnit source){return source.rts().skills().cooldownTicks(UnitSkill.SHADOW_CORROSION_TOUCH);}
    private static int arm(ShadowGhostUnit source){return ShadowCorrosionTouch.remainingArmTicks(source);}
    private static double nativeDamage(ShadowGhostUnit source,LivingEntity target){return source.getUnitAttackDamage()*(1-((Unit)target).getUnitPhysicalArmorPercentage());}
    private static void owned(GameTestHelper h,Mob body,String owner,int x,int z,int role){var unit=(Unit)body;unit.setOwnerName(owner);unit.setScenarioRoleIndex(role);body.setNoAi(true);body.setPersistenceRequired();body.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);neutral(body);position(body,Vec3.atBottomCenterOf(at(x,1,z)));h.assertTrue(h.getLevel().addFreshEntity(body),"original registered body enters ordinary real world storage");}
    private static void damage(LivingEntity source,double amount){source.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(amount);if(source.getAttribute(Attributes.ATTACK_DAMAGE)!=null)source.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(amount);if(source.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get())!=null)source.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);}
    private static void neutral(LivingEntity target){target.getAttribute(Attributes.ARMOR).setBaseValue(0);target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);target.setAbsorptionAmount(0);for(var attribute:List.of(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(),AttributeRegistrar.RANGED_DAMAGE_RESIST.get()))if(target.getAttribute(attribute)!=null)target.getAttribute(attribute).setBaseValue(0);}
    /** Controlled genuine installed goal, not direct doHurtTarget/handmade skill scope or natural APS claim. */
    private static double hit(GameTestHelper h,ShadowGhostUnit source,LivingEntity target){h.assertTrue(source.getAttackGoal() instanceof TeamSafeMeleeAttackGoal,"real installed original goal after normal load");var goal=(AbstractMeleeAttackUnitGoal)source.getAttackGoal();double gap=source.getBbWidth()/2.0+target.getBbWidth()/2.0+.1;position(source,target.position().add(-gap,0,0));source.setNoAi(true);source.setUnitAttackTargetForced(target);source.setHoldPosition(true);h.assertTrue(source.getTarget()==target&&!source.getBoundingBox().intersects(target.getBoundingBox())&&source.hasLineOfSight(target),"real exact legal native melee stance");for(int i=0;i<(int)Math.ceil(source.getAttackCooldown())+2;i++)goal.tickAttackCooldown();target.invulnerableTime=0;float before=target.getHealth();goal.start();goal.tick();goal.stop();source.setUnitAttackTargetForced(null);return before-target.getHealth();}
    private static void published(GameTestHelper h,Entity body){h.assertTrue(body.tickCount>3&&h.getLevel().getEntity(body.getId())==body&&h.getLevel().getEntity(body.getUUID())==body,"real original ID/UUID publication and natural ticks: "+body.getUUID());}
    private static void position(Entity body,Vec3 pos){body.moveTo(pos.x,pos.y,pos.z);body.setDeltaMovement(Vec3.ZERO);}
    private static BlockPos at(int x,int y,int z){return ORIGIN.offset(x,y,z);}
    private static FakePlayer player(ServerLevel level,String name){return PLAYERS.computeIfAbsent(name,key->{var p=new FakePlayer(level,new GameProfile(UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),key));p.connection=new SilentHandler(p);position(p,Vec3.atBottomCenterOf(at(key.equals(OWNER)?2:29,1,28)));level.addNewPlayer(p);PlayerServerEvents.players.add(p);return p;});}
    private static void ledger(GameTestHelper h){for(String owner:List.of(OWNER,ENEMY)){var r=ResourcesServerEvents.resourcesList.stream().filter(e->e.ownerName.equals(owner)).findFirst().orElse(null);h.assertTrue(r!=null&&r.food==123&&r.wood==234&&r.ore==345&&r.emerald==456&&r.foodToAdd==0&&r.woodToAdd==0&&r.oreToAdd==0&&r.emeraldToAdd==0,"exact original bank and pending balances, never recreated: "+owner);for(var entity:UnitServerEvents.getAllUnits())if(entity instanceof Unit unit&&unit.getOwnerName().equals(owner))h.assertTrue(unit.getItems().isEmpty(),"original declared cargo remains empty: "+entity.getUUID());}System.out.println("SHADOW_CORROSION_RESTART_LEDGER time="+h.getLevel().getGameTime()+" banks=123,234,345,456 pending=0 cargo=empty");}
    private static void arena(ServerLevel level){for(int x=0;x<32;x++)for(int z=0;z<32;z++)level.setBlock(at(x,9,z),Blocks.STONE.defaultBlockState(),2);level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(ORIGIN.getX(),ORIGIN.getY()+1,ORIGIN.getZ(),ORIGIN.getX()+32,level.getMaxBuildHeight(),ORIGIN.getZ()+32)).forEach(Entity::discard);for(int x=0;x<32;x++)for(int z=0;z<32;z++){level.setBlock(at(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<9;y++)level.setBlock(at(x,y,z),(x==0||x==31||z==0||z==31||z==10||z==20?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}level.getEntitiesOfClass(ItemEntity.class,new AABB(ORIGIN,ORIGIN.offset(32,10,32))).forEach(Entity::discard);}
    private static void close(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<.001,why+" expected="+expected+" actual="+actual);}
    private static final class SilentHandler extends ServerGamePacketListenerImpl{SilentHandler(ServerPlayer p){super(p.serverLevel().getServer(),new SilentConnection(),p);}@Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> p){}@Override public void send(net.minecraft.network.protocol.Packet<?> p,net.minecraft.network.PacketSendListener listener){}}
    private static final class SilentConnection extends net.minecraft.network.Connection{SilentConnection(){super(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);}@Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> p){}@Override public void send(net.minecraft.network.protocol.Packet<?> p,net.minecraft.network.PacketSendListener listener){}}
}
