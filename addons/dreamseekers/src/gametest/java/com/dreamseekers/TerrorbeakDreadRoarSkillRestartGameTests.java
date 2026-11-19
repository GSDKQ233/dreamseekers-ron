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

/** Actual two-JVM storage, no test-deserialized bodies or artificial startup hook.
 * Save must occur before the observed 30-tick disarm deadline. Ordinary startup may already exceed
 * thirty ticks: loaded absence then is not evidence of revocation before that deadline. The stronger
 * behavior proof is a fresh original caster controlling the same original target BEFORE the old
 * 100-tick protection deadline. Immediate NBT revocation is a separate same-process focused test.
 */
@GameTestHolder("dreamseekers_terrorbeak_dread_roar_restart")
@PrefixGameTestTemplate(false)
public final class TerrorbeakDreadRoarSkillRestartGameTests {
    private static final String OWNER="DreadRestart_Owner",ENEMY="DreadRestart_Enemy";
    private static final BlockPos ORIGIN=new BlockPos(98304,90,98304);
    private static final File MANIFEST=new File("terrorbeak-dread-roar-restart-expectations.nbt");
    private static final UUID MS_TECH=UUID.fromString("43ee2617-3dd6-4d60-8c05-5f31504a56fd");
    private static final List<String> KEYS=List.of("paidCaster","unusedCaster","oldTarget","ordinaryVictim");
    private static final Map<String,FakePlayer> PLAYERS=new HashMap<>();

    @GameTest(template="unit_isolation_arena",batch="dreamseekers_terrorbeak_dread_roar_restart",timeoutTicks=1450)
    public static void realRestartKeepsOriginalBodiesAndPaidClockButCannotInheritOldRecontrolProtection(GameTestHelper h) throws Exception {
        switch(System.getProperty("dreamseekers.terrorbeakDreadRoar.test.phase","")) {
            case "prepare"->prepare(h);case "verify"->verify(h);
            default->throw new IllegalArgumentException("Unknown dread-roar restart phase");
        }
    }
    private static void prepare(GameTestHelper h) {
        var level=h.getLevel();h.assertTrue(!MANIFEST.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null&&PlayerServerEvents.getRTSPlayer(ENEMY)==null,"fresh isolated normal-save fixture, never reuse consumed verify state");
        var expected=new CompoundTag();expected.putInt("format",1);expected.putLong("prepareJvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        List<Long> forced=new ArrayList<>();for(int x=6144;x<=6145;x++)for(int z=6144;z<=6145;z++){long key=ChunkPos.asLong(x,z);h.assertTrue(!level.getForcedChunks().contains(key),"new fixture tickets only");level.setChunkForced(x,z,true);forced.add(key);}expected.putLongArray("forcedChunks",forced);arena(level);
        for(String name:List.of(OWNER,ENEMY)){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,123,234,345,456));player(level,name);}
        Map<String,Mob> bodies=new LinkedHashMap<>();
        for(int i=0;i<2;i++){var caster=DreamseekersMod.TERRORBEAK_UNIT.get().create(level);owned(h,caster,OWNER,4.5,5.5+10*i,7001+i);caster.setAnchor(at(4,0,5+10*i));caster.getAttribute(Attributes.MOVEMENT_SPEED).addPermanentModifier(new AttributeModifier(MS_TECH,"actual permanent restart movement research",.03,AttributeModifier.Operation.ADDITION));bodies.put(KEYS.get(i),caster);}
        var target=DreamseekersMod.SHADOW_GHOST_UNIT.get().create(level);owned(h,target,ENEMY,24.5,5.5,7003);damage(target,10);durable(target);bodies.put("oldTarget",target);
        var victim=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(level);owned(h,victim,OWNER,24.5,15.5,7004);durable(victim);bodies.put("ordinaryVictim",victim);
        for(var entry:bodies.entrySet())expected.putUUID(entry.getKey(),entry.getValue().getUUID());
        h.startSequence().thenWaitUntil(()->{for(var body:bodies.values())published(h,body);for(var p:PLAYERS.values())published(h,p);})
          .thenExecute(()->{
              h.assertTrue(!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isEnabled(),"actual isolated world is fog off, no fabricated visibility");ledger(h);
              var paid=(TerrorbeakUnit)bodies.get("paidCaster");var unused=(TerrorbeakUnit)bodies.get("unusedCaster");position(paid,22,5.5);
              h.assertTrue(cast(level,paid)==SkillResult.SUCCESS&&cd(paid)==400&&cd(unused)==0,"one real self cast installs a negative and pays CD400; original unused caster stays free");
              h.assertTrue(TerrorbeakDreadRoar.remainingDisarmTicks(target)==30&&TerrorbeakDreadRoar.remainingRecontrolTicks(target)==100,"genuine untouched thirty-tick disarm and one-hundred-tick protection start together");
              close(h,target.getHealth(),1000,"roar itself causes no HP damage");close(h,victim.getHealth(),1000,"no ordinary test hit occurred before save");
              position(paid,4.5,5.5);paid.setUnitAttackTargetForced(null);h.assertTrue(TerrorbeakDreadRoar.remainingDisarmTicks(target)==30&&TerrorbeakDreadRoar.remainingRecontrolTicks(target)==100,"fixture home relocation preserves actual source-owned effect without a distance tether");
              expected.putLong("snapshotGameTime",level.getGameTime());expected.putLong("disarmExpiresAt",level.getGameTime()+30);expected.putLong("protectionExpiresAt",level.getGameTime()+100);expected.putUUID("permanentMovementUuid",MS_TECH);expected.putUUID("transientMovementUuid",BuffKind.MOVEMENT_SPEED.modifierId);
              for(var entry:bodies.entrySet()){
                  var body=entry.getValue();var unit=(Unit)body;var state=new CompoundTag();state.putFloat("health",body.getHealth());state.putString("owner",unit.getOwnerName());state.putInt("scenario",unit.getScenarioRoleIndex());state.putString("type",EntityType.getKey(body.getType()).toString());state.putString("class",body.getClass().getName());state.putBoolean("noGravity",body.isNoGravity());state.putDouble("movementBase",body.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue());
                  h.assertTrue(body.isNoAi()&&body.getActiveEffects().isEmpty(),"declared ordinary-AI isolation and no substituted persistent native potion");
                  if(body instanceof TerrorbeakUnit source){state.putInt("cooldown",cd(source));state.putLong("revision",source.rts().skills().revision());state.putLong("anchor",source.getAnchor().asLong());h.assertTrue(!source.getAnchor().equals(BlockPos.ZERO)&&source.getTarget()==null&&source.distanceTo(target)>10,"original home/empty attack intent prevents normal load-time ordinary attacks");close(h,TerrorbeakDreadRoar.predatoryBonus(source),0,"this restart fixture is not a claimed saved-active-predatory chase");}
                  expected.put(entry.getKey()+"State",state);
              }
              ledger(h);try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception failure){throw new RuntimeException(failure);}
              System.out.println("DREAMSEEKERS_TERROR_DREAD_RESTART_PREPARED time="+level.getGameTime()+" disarm30/protection100/CD400 originals="+KEYS.stream().map(key->key+":"+bodies.get(key).getUUID()).toList());
              // No pre-clear: real bodies and active runtime state are left to normal stop/save. External read-only disk gate follows.
          }).thenSucceed();
    }
    private static void verify(GameTestHelper h) throws Exception {
        var level=h.getLevel();h.assertTrue(MANIFEST.isFile(),"original prepare manifest exists");var expected=NbtIo.readCompressed(MANIFEST);
        h.assertTrue(expected.getInt("format")==1&&expected.getLong("prepareJvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"different real JVM, never manual deserialize or replacement entities");
        for(long key:expected.getLongArray("forcedChunks")){h.assertTrue(level.getForcedChunks().contains(key),"original forced chunk survives normal save");var c=new ChunkPos(key);level.getChunk(c.x,c.z);}for(String name:List.of(OWNER,ENEMY))player(level,name);
        Map<String,Mob> bodies=new LinkedHashMap<>();long[] elapsedAtLoad={-1},freshAt={-1};float[] victimBefore={-1};
        h.startSequence().thenWaitUntil(()->{for(String key:KEYS){var body=level.getEntity(expected.getUUID(key));h.assertTrue(body instanceof Mob,"real ordinary loader returns original UUID: "+key);published(h,body);}for(var p:PLAYERS.values())published(h,p);})
          .thenExecute(()->{
              long elapsed=level.getGameTime()-expected.getLong("snapshotGameTime");elapsedAtLoad[0]=elapsed;
              h.assertTrue(elapsed>=0&&level.getGameTime()<expected.getLong("protectionExpiresAt"),"original recontrol deadline must still be future; no startup-window extension or after-expiry false positive: elapsed="+elapsed);
              for(String key:KEYS){
                  var body=(Mob)level.getEntity(expected.getUUID(key));bodies.put(key,body);var unit=(Unit)body;var state=expected.getCompound(key+"State");
                  h.assertTrue(!body.isNoAi()&&body.isNoGravity()==state.getBoolean("noGravity")&&body.getClass().getName().equals(state.getString("class"))&&EntityType.getKey(body.getType()).toString().equals(state.getString("type"))&&unit.getOwnerName().equals(state.getString("owner"))&&unit.getScenarioRoleIndex()==state.getInt("scenario"),"real paid loader NoAI=false and original exact gravity/type/class/owner/role: "+key);
                  h.assertTrue(UnitServerEvents.getAllUnits().stream().filter(e->e.getUUID().equals(body.getUUID())).count()==1,"one actual original roster body, not reconstructed: "+key);close(h,body.getHealth(),state.getFloat("health"),"original HP unchanged before ordinary-AI isolation: "+key);
                  h.assertTrue(!TerrorbeakDreadRoar.hasDisarm(body)&&TerrorbeakDreadRoar.remainingRecontrolTicks(body)==0&&body.getActiveEffects().isEmpty(),"no old short map/protection/native potion present at actual loaded time: "+key);close(h,body.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue(),state.getDouble("movementBase"),"original movement base unchanged: "+key);h.assertTrue(body.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(BuffKind.MOVEMENT_SPEED.modifierId)==null,"no serialized transient movement contribution");
                  if(body instanceof TerrorbeakUnit source){h.assertTrue(cd(source)==Math.max(0,state.getInt("cooldown")-elapsed)&&source.rts().skills().revision()>=state.getLong("revision"),"original absolute paid/unused cooldown and revision retained");h.assertTrue(source.getAnchor().equals(BlockPos.of(state.getLong("anchor")))&&!source.getAnchor().equals(BlockPos.ZERO),"original nonzero anchor survives without restore fiction");var movement=source.getAttribute(Attributes.MOVEMENT_SPEED);h.assertTrue(movement.getModifier(MS_TECH)!=null&&movement.getModifiers().size()==1,"only actual permanent movement research remains");close(h,movement.getValue(),state.getDouble("movementBase")+.03,"permanent additive movement value unchanged");close(h,TerrorbeakDreadRoar.predatoryBonus(source),0,"no new valid chase, and no replayed predatory contribution");}
              }
              // A normal startup may exceed the old thirty ticks. Record the boundary; never claim earlier revocation from this observation.
              System.out.println("TERROR_DREAD_RESTART_LOADED_BOUNDARY elapsed="+elapsed+" observedBeforeOldDisarmDeadline="+(level.getGameTime()<expected.getLong("disarmExpiresAt"))+" observedBeforeOldProtectionDeadline=true");
              // AFTER original UUID/AI/HP/clock/permanent-state observations, contain unrelated ordinary AI for controlled original-goal probes.
              for(var body:bodies.values())body.setNoAi(true);
              var paid=(TerrorbeakUnit)bodies.get("paidCaster");var unused=(TerrorbeakUnit)bodies.get("unusedCaster");var target=(ShadowGhostUnit)bodies.get("oldTarget");var victim=bodies.get("ordinaryVictim");
              h.assertTrue(cast(level,paid)==SkillResult.COOLDOWN,"original paid source cannot regain a free post-load cast");
              position(target,23.5,15.5);position(unused,21,15.5);freshAt[0]=level.getGameTime();
              h.assertTrue(freshAt[0]<expected.getLong("protectionExpiresAt")&&cd(unused)==0&&cast(level,unused)==SkillResult.SUCCESS,"original UNUSED source really controls SAME original target BEFORE old protection expiry, proving old protection was not inherited");
              h.assertTrue(TerrorbeakDreadRoar.remainingDisarmTicks(target)==30&&TerrorbeakDreadRoar.remainingRecontrolTicks(target)==100&&cd(unused)==400,"fresh actual control receives independent new thirty/one-hundred deadlines and paid cooldown");
              position(unused,4.5,15.5);unused.setUnitAttackTargetForced(null);victimBefore[0]=victim.getHealth();h.assertTrue(victim.invulnerableTime==0,"unhurt genuine victim has no iframe masking a blocked ordinary attempt");
              close(h,ordinary(h,target,victim),0,"fresh control blocks actual installed ordinary goal, not a direct injected permission or gigantic CD");ledger(h);
          }).thenIdle(29).thenExecute(()->{
              var target=(ShadowGhostUnit)bodies.get("oldTarget");var victim=bodies.get("ordinaryVictim");h.assertTrue(level.getGameTime()==freshAt[0]+29&&TerrorbeakDreadRoar.remainingDisarmTicks(target)==1&&TerrorbeakDreadRoar.remainingRecontrolTicks(target)==71,"exact fresh final controlled tick with independently ticking protection");h.assertTrue(victim.invulnerableTime==0,"late blocked-probe victim still has no immunity-frame explanation");close(h,ordinary(h,target,victim),0,"same real ordinary entry remains blocked at new tick twenty-nine");close(h,victim.getHealth(),victimBefore[0],"roar and blocked ordinary probes caused no HP damage");
          }).thenIdle(1).thenExecute(()->{
              var target=(ShadowGhostUnit)bodies.get("oldTarget");var victim=bodies.get("ordinaryVictim");h.assertTrue(level.getGameTime()==freshAt[0]+30&&!TerrorbeakDreadRoar.hasDisarm(target)&&TerrorbeakDreadRoar.remainingRecontrolTicks(target)==70,"at exact thirty the control is gone but protection remains");h.assertTrue(victim.invulnerableTime==0,"true ordinary positive is not masked by earlier hurt");close(h,ordinary(h,target,victim),10,"same original installed goal now causes actual normal ten HP at control expiry");close(h,victim.getHealth(),victimBefore[0]-10,"actual HP positive, not HUD/control-query-only evidence");ledger(h);
          }).thenIdle(71).thenExecute(()->{
              h.assertTrue(level.getGameTime()==freshAt[0]+101&&level.getGameTime()>expected.getLong("protectionExpiresAt"),"observation crosses both old and new protection deadlines");
              for(String key:KEYS){var body=bodies.get(key);h.assertTrue(!TerrorbeakDreadRoar.hasDisarm(body)&&TerrorbeakDreadRoar.remainingRecontrolTicks(body)==0,"no delayed old or fresh short-state replay");close(h,body.getHealth(),key.equals("ordinaryVictim")?victimBefore[0]-10:expected.getCompound(key+"State").getFloat("health"),"only declared post-control ordinary positive changed HP: "+key);if(body instanceof TerrorbeakUnit source){long expiry=key.equals("paidCaster")?expected.getLong("snapshotGameTime")+expected.getCompound(key+"State").getInt("cooldown"):freshAt[0]+400;h.assertTrue(cd(source)==Math.max(0,expiry-level.getGameTime()),"paid original and fresh clocks remain exact through observed deadlines");h.assertTrue(source.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(MS_TECH)!=null&&source.getAttribute(Attributes.MOVEMENT_SPEED).getModifier(BuffKind.MOVEMENT_SPEED.modifierId)==null,"no permanent movement loss or delayed passive modifier");}}
              ledger(h);System.out.println("DREAMSEEKERS_TERROR_DREAD_RESTART_UUID_CD_PROTECTION_FRESH_CONTROL_PASS elapsedAtLoad="+elapsedAtLoad[0]+" freshAt="+freshAt[0]+" oldDisarmExpiresAt="+expected.getLong("disarmExpiresAt")+" oldProtectionExpiresAt="+expected.getLong("protectionExpiresAt")+" finalElapsed="+(level.getGameTime()-expected.getLong("snapshotGameTime"))+" originalUUIDs="+KEYS.stream().map(key->bodies.get(key).getUUID()).toList());
              for(var body:bodies.values())body.discard();for(var p:PLAYERS.values())p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);PLAYERS.clear();PlayerServerEvents.players.removeIf(p->List.of(OWNER,ENEMY).contains(p.getGameProfile().getName()));PlayerServerEvents.rtsPlayers.removeIf(p->List.of(OWNER,ENEMY).contains(p.name));ResourcesServerEvents.resourcesList.removeIf(r->List.of(OWNER,ENEMY).contains(r.ownerName));for(long key:expected.getLongArray("forcedChunks")){var c=new ChunkPos(key);level.setChunkForced(c.x,c.z,false);}
          }).thenSucceed();
    }
    private static SkillResult cast(ServerLevel level,TerrorbeakUnit source){return new SkillNetwork.Cast(new SkillRequest(UnitSkill.TERRORBEAK_DREAD_ROAR,source.getId(),source.getUUID(),source.rts().skills().revision(),source.getId(),source.getUUID())).execute(player(level,OWNER));}
    private static int cd(TerrorbeakUnit source){return source.rts().skills().cooldownTicks(UnitSkill.TERRORBEAK_DREAD_ROAR);}
    /** Controlled real installed ordinary-goal entry; not a full autonomous cadence claim or fake hurt/scope. */
    private static double ordinary(GameTestHelper h,ShadowGhostUnit attacker,LivingEntity victim){h.assertTrue(attacker.getAttackGoal() instanceof TeamSafeMeleeAttackGoal,"original paid installed melee goal");var goal=(AbstractMeleeAttackUnitGoal)attacker.getAttackGoal();double gap=attacker.getBbWidth()/2.0+victim.getBbWidth()/2.0+.1;attacker.moveTo(victim.getX()-gap,victim.getY(),victim.getZ(),0,0);attacker.setDeltaMovement(Vec3.ZERO);attacker.setUnitAttackTargetForced(victim);attacker.setHoldPosition(true);h.assertTrue(attacker.getTarget()==victim&&!attacker.getBoundingBox().intersects(victim.getBoundingBox())&&attacker.hasLineOfSight(victim),"genuine collision-free ordinary stance and LOS");for(int i=0;i<(int)Math.ceil(attacker.getAttackCooldown())+2;i++)goal.tickAttackCooldown();float before=victim.getHealth();goal.start();goal.tick();goal.stop();attacker.setUnitAttackTargetForced(null);return before-victim.getHealth();}
    private static void owned(GameTestHelper h,Mob body,String owner,double x,double z,int role){var unit=(Unit)body;unit.setOwnerName(owner);unit.setScenarioRoleIndex(role);body.setNoAi(true);body.setPersistenceRequired();body.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);neutral(body);position(body,x,z);h.assertTrue(h.getLevel().addFreshEntity(body),"original registered body enters ordinary world storage");}
    private static void damage(LivingEntity source,double value){source.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(value);if(source.getAttribute(Attributes.ATTACK_DAMAGE)!=null)source.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(value);if(source.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get())!=null)source.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);}
    private static void neutral(LivingEntity body){body.getAttribute(Attributes.ARMOR).setBaseValue(0);body.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);body.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);body.setAbsorptionAmount(0);for(var attribute:List.of(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(),AttributeRegistrar.RANGED_DAMAGE_RESIST.get()))if(body.getAttribute(attribute)!=null)body.getAttribute(attribute).setBaseValue(0);}
    private static void durable(LivingEntity body){body.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);body.setHealth(1000);}
    private static void published(GameTestHelper h,Entity body){h.assertTrue(body.tickCount>3&&h.getLevel().getEntity(body.getId())==body&&h.getLevel().getEntity(body.getUUID())==body,"genuine original ID/UUID publication and natural ticks: "+body.getUUID());}
    private static void position(Entity body,double x,double z){body.moveTo(ORIGIN.getX()+x,ORIGIN.getY()+1,ORIGIN.getZ()+z,0,0);body.setDeltaMovement(Vec3.ZERO);}
    private static BlockPos at(int x,int y,int z){return ORIGIN.offset(x,y,z);}
    private static FakePlayer player(ServerLevel level,String name){return PLAYERS.computeIfAbsent(name,key->{var p=new FakePlayer(level,new GameProfile(UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),key));p.connection=new SilentHandler(p);position(p,key.equals(OWNER)?2.5:29.5,29.5);level.addNewPlayer(p);PlayerServerEvents.players.add(p);return p;});}
    private static void ledger(GameTestHelper h){for(String owner:List.of(OWNER,ENEMY)){var r=ResourcesServerEvents.resourcesList.stream().filter(v->v.ownerName.equals(owner)).findFirst().orElse(null);h.assertTrue(r!=null&&r.food==123&&r.wood==234&&r.ore==345&&r.emerald==456&&r.foodToAdd==0&&r.woodToAdd==0&&r.oreToAdd==0&&r.emeraldToAdd==0,"unchanged real banks/pending, no refill or recreated ledger: "+owner);for(var body:UnitServerEvents.getAllUnits())if(body instanceof Unit unit&&unit.getOwnerName().equals(owner))h.assertTrue(unit.getItems().isEmpty(),"declared empty carried items: "+body.getUUID());}System.out.println("TERROR_DREAD_RESTART_LEDGER time="+h.getLevel().getGameTime()+" banks=123,234,345,456 pending=0 cargo=empty");}
    private static void arena(ServerLevel level){for(int x=0;x<32;x++)for(int z=0;z<32;z++)level.setBlock(at(x,9,z),Blocks.STONE.defaultBlockState(),2);level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(ORIGIN.getX(),ORIGIN.getY()+1,ORIGIN.getZ(),ORIGIN.getX()+32,level.getMaxBuildHeight(),ORIGIN.getZ()+32)).forEach(Entity::discard);for(int x=0;x<32;x++)for(int z=0;z<32;z++){level.setBlock(at(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<9;y++)level.setBlock(at(x,y,z),(x==0||x==31||z==0||z==31||z==10?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}level.getEntitiesOfClass(ItemEntity.class,new AABB(ORIGIN,ORIGIN.offset(32,10,32))).forEach(Entity::discard);}
    private static void close(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<.001,why+" expected="+expected+" actual="+actual);}
    private static final class SilentHandler extends ServerGamePacketListenerImpl{SilentHandler(ServerPlayer p){super(p.serverLevel().getServer(),new SilentConnection(),p);}@Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> packet){}@Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){}}
    private static final class SilentConnection extends net.minecraft.network.Connection{SilentConnection(){super(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);}@Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> packet){}@Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){}}
}
