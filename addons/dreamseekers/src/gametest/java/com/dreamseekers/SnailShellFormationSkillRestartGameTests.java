// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.*;
import com.solegendary.reignofnether.unit.units.monsters.SkeletonUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;

/** Prepare and verify use two ordinary server JVMs. Manifest has expectations only, never entity NBT.
 * Verify resolves original UUIDs from normal storage; it never creates or deserializes replacement bodies.
 */
@GameTestHolder("dreamseekers_snail_shell_formation_restart")
@PrefixGameTestTemplate(false)
public final class SnailShellFormationSkillRestartGameTests {
    private static final String OWNER="SnailFormationRestart_Owner",ENEMY="SnailFormationRestart_Enemy";
    private static final BlockPos ORIGIN=new BlockPos(81920,90,81920);
    private static final File MANIFEST=new File("snail-shell-formation-restart-expectations.nbt");
    private static final List<String> KEYS=List.of("activePlain","activeNativeShell","unusedCaster","oldTarget","freshTarget","oldShooter","freshShooter");
    private static final Map<String,FakePlayer> PLAYERS=new HashMap<>();

    @GameTest(template="unit_isolation_arena",batch="dreamseekers_snail_shell_formation_restart",timeoutTicks=1450)
    public static void normalRestartKeepsOriginalUuidPaidClockAnchorAndOldShellButNeverRestoresFormation(GameTestHelper h) throws Exception {
        switch(System.getProperty("dreamseekers.snailShellFormation.test.phase","")) {
            case "prepare"->prepare(h);case "verify"->verify(h);
            default->throw new IllegalArgumentException("Unknown snail shell formation restart phase");
        }
    }
    private static void prepare(GameTestHelper h) {
        ServerLevel level=h.getLevel();
        h.assertTrue(!MANIFEST.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null&&PlayerServerEvents.getRTSPlayer(ENEMY)==null,"fresh isolated prepare world; never reuse consumed verification");
        CompoundTag expected=new CompoundTag();expected.putInt("format",1);expected.putLong("prepareJvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        List<Long> forced=new ArrayList<>();
        for(int x=5120;x<=5121;x++)for(int z=5120;z<=5121;z++){long key=ChunkPos.asLong(x,z);h.assertTrue(!level.getForcedChunks().contains(key),"only newly created fixture chunk tickets");level.setChunkForced(x,z,true);forced.add(key);}
        expected.putLongArray("forcedChunks",forced);arena(level);
        for(String owner:List.of(OWNER,ENEMY)){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(owner,123,234,345,456));player(level,owner);}
        Map<String,Mob> bodies=new LinkedHashMap<>();
        for(int i=0;i<3;i++){var snail=DreamseekersMod.BASALT_SNAIL.get().create(level);owned(h,snail,OWNER,12,1,8+i*9,3001+i);snail.setAnchor(at(12,0,8+i*9));neutral(snail);bodies.put(KEYS.get(i),snail);}
        var oldTarget=DreamseekersMod.FIREFLY_WORKER.get().create(level);owned(h,oldTarget,OWNER,12,1,6,3004);neutral(oldTarget);durable(oldTarget);bodies.put("oldTarget",oldTarget);
        var freshTarget=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(level);owned(h,freshTarget,OWNER,12,1,24,3005);neutral(freshTarget);durable(freshTarget);bodies.put("freshTarget",freshTarget);
        for(int i=0;i<2;i++){var shooter=EntityRegistrar.SKELETON_UNIT.get().create(level);owned(h,shooter,ENEMY,6,1,6+18*i,3006+i);shooter.setupEquipmentAndUpgradesServer();damage(shooter,10);bodies.put(i==0?"oldShooter":"freshShooter",shooter);}
        for(var entry:bodies.entrySet())expected.putUUID(entry.getKey(),entry.getValue().getUUID());
        h.startSequence().thenWaitUntil(()->{for(Mob body:bodies.values())published(h,body);for(var p:PLAYERS.values())published(h,p);})
          .thenExecute(()->{
              ledger(h);var plain=(BasaltSnailUnit)bodies.get("activePlain");var nativeShell=(BasaltSnailUnit)bodies.get("activeNativeShell");var unused=(BasaltSnailUnit)bodies.get("unusedCaster");
              float health=nativeShell.getHealth();nativeShell.invulnerableTime=0;h.assertTrue(nativeShell.hurt(nativeShell.damageSources().generic(),2),"actual native reactive-shell trigger before ordinary save");close(h,health-nativeShell.getHealth(),2,"native shell never retroactively protects its own first generic hit");
              h.assertTrue(nativeShell.getShellCooldown()==200&&nativeShell.getEffect(MobEffects.DAMAGE_RESISTANCE).getDuration()==80&&nativeShell.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier()==0&&nativeShell.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getDuration()==80&&nativeShell.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier()==2,"original native shell genuinely exists at exact I80/III80/CD200 before snapshot");
              h.assertTrue(cast(level,plain)==SkillResult.SUCCESS&&cast(level,nativeShell)==SkillResult.SUCCESS,"two actual authenticated self casts, not injected runtime records");
              h.assertTrue(SnailShellFormation.remainingTicks(plain)==100&&SnailShellFormation.remainingTicks(nativeShell)==100&&cd(plain)==480&&cd(nativeShell)==480,"normal shutdown snapshot contains live hundred-tick formations and paid absolute cooldowns");
              h.assertTrue(!SnailShellFormation.active(unused)&&cd(unused)==0,"third original body remains genuinely unused");
              expected.putLong("snapshotGameTime",level.getGameTime());expected.putLong("oldFormationExpiry",level.getGameTime()+100);
              for(var entry:bodies.entrySet()) {
                  Mob body=entry.getValue();var unit=(Unit)body;CompoundTag state=new CompoundTag();state.putString("owner",unit.getOwnerName());state.putString("type",EntityType.getKey(body.getType()).toString());state.putString("class",body.getClass().getName());state.putInt("scenario",unit.getScenarioRoleIndex());state.putFloat("health",body.getHealth());
                  if(body instanceof BasaltSnailUnit snail){state.putInt("cooldown",cd(snail));state.putLong("revision",snail.rts().skills().revision());state.putLong("anchor",snail.getAnchor().asLong());state.putInt("shellCooldown",snail.getShellCooldown());state.putInt("resistanceTicks",snail.hasEffect(MobEffects.DAMAGE_RESISTANCE)?snail.getEffect(MobEffects.DAMAGE_RESISTANCE).getDuration():0);state.putInt("slowTicks",snail.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)?snail.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getDuration():0);h.assertTrue(!snail.getAnchor().equals(BlockPos.ZERO),"snapshot preserves actual nonzero anchor, never temporary null/zero override");}
                  expected.put(entry.getKey()+"State",state);
              }
              ledger(h);try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception failure){throw new RuntimeException(failure);}
              System.out.println("DREAMSEEKERS_SNAIL_SHELL_FORMATION_RESTART_PREPARED time="+level.getGameTime()+" CD480 live100 nativeShell80/200 originalUUIDs="+KEYS.stream().map(key->key+":"+bodies.get(key).getUUID()).toList());
              // Leave real bodies and forced chunks intact. Ordinary server shutdown performs persistence.
          }).thenSucceed();
    }
    private static void verify(GameTestHelper h) throws Exception {
        ServerLevel level=h.getLevel();h.assertTrue(MANIFEST.isFile(),"ordinary prepare expectation manifest exists");CompoundTag expected=NbtIo.readCompressed(MANIFEST);
        h.assertTrue(expected.getInt("format")==1&&expected.getLong("prepareJvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"verify is genuinely a different JVM, not an NBT roundtrip or class reconstruction");
        for(long key:expected.getLongArray("forcedChunks")){h.assertTrue(level.getForcedChunks().contains(key),"ordinary startup restored original persistent chunk ticket");var chunk=new ChunkPos(key);level.getChunk(chunk.x,chunk.z);}
        for(String owner:List.of(OWNER,ENEMY))player(level,owner);
        Map<String,Mob> bodies=new LinkedHashMap<>();AbstractArrow[] arrows=new AbstractArrow[2];Vec3[] starts=new Vec3[2];double[] travel=new double[2];float[] healthBefore=new float[2];long[] freshAt={0},elapsedAtLoad={0};
        var seq=h.startSequence().thenWaitUntil(()->{for(String key:KEYS){Entity body=level.getEntity(expected.getUUID(key));h.assertTrue(body instanceof Mob,"normal entity storage loaded exact original UUID: "+key);published(h,body);}for(var p:PLAYERS.values())published(h,p);})
          .thenExecute(()->{
              long elapsed=level.getGameTime()-expected.getLong("snapshotGameTime");elapsedAtLoad[0]=elapsed;
              h.assertTrue(elapsed>=0&&elapsed<80,"load observation precedes original native-shell and formation deadlines, so absence cannot be mistaken for ordinary expiry: elapsed="+elapsed);
              for(String key:KEYS) {
                  Mob body=(Mob)level.getEntity(expected.getUUID(key));bodies.put(key,body);var state=expected.getCompound(key+"State");var unit=(Unit)body;
                  h.assertTrue(unit.getOwnerName().equals(state.getString("owner"))&&unit.getScenarioRoleIndex()==state.getInt("scenario")&&EntityType.getKey(body.getType()).toString().equals(state.getString("type"))&&body.getClass().getName().equals(state.getString("class")),"ordinary original owner, type, class and scenario survive: "+key);
                  h.assertTrue(UnitServerEvents.getAllUnits().stream().filter(e->e.getUUID().equals(body.getUUID())).count()==1,"exactly one ordinary roster body for saved original UUID: "+key);close(h,body.getHealth(),state.getFloat("health"),"normal save/load has no replay or incidental health mutation: "+key);
                  if(body instanceof BasaltSnailUnit snail){h.assertTrue(!SnailShellFormation.active(snail)&&SnailShellFormation.remainingTicks(snail)==0&&!snail.getHoldPosition(),"normally loaded source never restores transient formation or derived hold before original deadline: "+key);int remain=(int)Math.max(0,state.getInt("cooldown")-elapsed);h.assertTrue(cd(snail)==remain&&snail.rts().skills().revision()>=state.getLong("revision"),"paid absolute cooldown and replay generation persist without refund/clock restart: "+key);h.assertTrue(snail.getAnchor().equals(BlockPos.of(state.getLong("anchor")))&&!snail.getAnchor().equals(BlockPos.ZERO),"actual original nonzero anchor survives ordinary loading: "+key);}
              }
              var nativeShell=(BasaltSnailUnit)bodies.get("activeNativeShell");var shellState=expected.getCompound("activeNativeShellState");
              h.assertTrue(nativeShell.getShellCooldown()>=shellState.getInt("shellCooldown")-elapsed-1&&nativeShell.getShellCooldown()<=shellState.getInt("shellCooldown"),"original shell counter follows native per-entity ticking, never new-skill cleanup reset");
              h.assertTrue(nativeShell.hasEffect(MobEffects.DAMAGE_RESISTANCE)&&nativeShell.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier()==0&&nativeShell.getEffect(MobEffects.DAMAGE_RESISTANCE).getDuration()>=shellState.getInt("resistanceTicks")-elapsed-1&&nativeShell.getEffect(MobEffects.DAMAGE_RESISTANCE).getDuration()<=shellState.getInt("resistanceTicks")&&nativeShell.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)&&nativeShell.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier()==2&&nativeShell.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getDuration()>=shellState.getInt("slowTicks")-elapsed-1&&nativeShell.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getDuration()<=shellState.getInt("slowTicks"),"normal native I/III effects remain present under their own loader and ticking, not cleared with formation");
              var old=(BasaltSnailUnit)bodies.get("activePlain");var unused=(BasaltSnailUnit)bodies.get("unusedCaster");h.assertTrue(cast(level,old)==SkillResult.COOLDOWN&&cast(level,nativeShell)==SkillResult.COOLDOWN,"both original paid loaded sources reject free recast");
              h.assertTrue(cd(unused)==0&&cast(level,unused)==SkillResult.SUCCESS,"genuinely unused original loaded source can make a new real authenticated cast");freshAt[0]=level.getGameTime();
              for(int i=0;i<2;i++){var shooter=(SkeletonUnit)bodies.get(i==0?"oldShooter":"freshShooter");var target=bodies.get(i==0?"oldTarget":"freshTarget");starts[i]=shooter.position();healthBefore[i]=target.getHealth();target.invulnerableTime=0;shooter.performUnitRangedAttack(target,1);var found=level.getEntitiesOfClass(AbstractArrow.class,shooter.getBoundingBox().inflate(2),arrow->arrow.getOwner()==shooter);h.assertTrue(found.size()==1&&target.getHealth()==healthBefore[i],"actual original loaded native factory publishes one travelling Arrow without immediate damage: lane="+i);arrows[i]=found.get(0);}
              ledger(h);
          });
        for(int i=0;i<35;i++)seq.thenIdle(1).thenExecute(()->{for(int lane=0;lane<2;lane++)travel[lane]=Math.max(travel[lane],arrows[lane].position().distanceTo(starts[lane]));});
        seq.thenExecute(()->{
            for(int i=0;i<2;i++)h.assertTrue(travel[i]>2&&arrows[i].isRemoved(),"real original loaded Arrow factory, natural flight and completed impact: lane="+i);
            close(h,healthBefore[0]-bodies.get("oldTarget").getHealth(),10,"old saved formation never protects an ally after normal restart");
            close(h,healthBefore[1]-bodies.get("freshTarget").getHealth(),8,"only new real post-load cast grants twenty-percent protection to actual physical primary");
            System.out.println("SNAIL_SHELL_RESTART_REAL_ARROWS travel="+Arrays.toString(travel)+" oldLoss="+(healthBefore[0]-bodies.get("oldTarget").getHealth())+" freshLoss="+(healthBefore[1]-bodies.get("freshTarget").getHealth()));
        }).thenWaitUntil(()->h.assertTrue(level.getGameTime()>=expected.getLong("oldFormationExpiry"),"observe through original pre-shutdown formation deadline"))
          .thenExecute(()->{
              for(String key:List.of("activePlain","activeNativeShell")){var snail=(BasaltSnailUnit)bodies.get(key);var state=expected.getCompound(key+"State");h.assertTrue(!SnailShellFormation.active(snail)&&cd(snail)==Math.max(0,expected.getLong("snapshotGameTime")+state.getInt("cooldown")-level.getGameTime()),"old deadline never replays formation or restarts original paid clock: "+key);h.assertTrue(snail.getAnchor().equals(BlockPos.of(state.getLong("anchor"))),"old effect expiry never overwrites original anchor: "+key);}
              var fresh=(BasaltSnailUnit)bodies.get("unusedCaster");h.assertTrue(SnailShellFormation.remainingTicks(fresh)==Math.max(0,100-(level.getGameTime()-freshAt[0]))&&cd(fresh)==480-(level.getGameTime()-freshAt[0]),"fresh independent post-load cast keeps its own deadline");
              ledger(h);System.out.println("DREAMSEEKERS_SNAIL_SHELL_FORMATION_RESTART_UUID_CD_ANCHOR_NATIVE_SHELL_NO_REPLAY_PASS elapsedAtLoad="+elapsedAtLoad[0]+" finalElapsed="+(level.getGameTime()-expected.getLong("snapshotGameTime"))+" originals="+KEYS.stream().map(key->key+":"+bodies.get(key).getUUID()).toList());
              for(Mob body:bodies.values())body.discard();for(var player:PLAYERS.values())player.serverLevel().removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);PLAYERS.clear();PlayerServerEvents.players.removeIf(p->List.of(OWNER,ENEMY).contains(p.getGameProfile().getName()));PlayerServerEvents.rtsPlayers.removeIf(p->List.of(OWNER,ENEMY).contains(p.name));ResourcesServerEvents.resourcesList.removeIf(r->List.of(OWNER,ENEMY).contains(r.ownerName));for(long key:expected.getLongArray("forcedChunks")){var chunk=new ChunkPos(key);level.setChunkForced(chunk.x,chunk.z,false);}
          }).thenSucceed();
    }
    private static SkillResult cast(ServerLevel level,BasaltSnailUnit source){return SkillService.request(player(level,OWNER),new SkillRequest(UnitSkill.SNAIL_SHELL_FORMATION,source.getId(),source.getUUID(),source.rts().skills().revision(),source.getId(),source.getUUID()));}
    private static int cd(BasaltSnailUnit source){return source.rts().skills().cooldownTicks(UnitSkill.SNAIL_SHELL_FORMATION);}
    private static void owned(GameTestHelper h,Mob body,String owner,int x,int y,int z,int scenario){h.assertTrue(body instanceof Unit,"real registered original RTS body");var unit=(Unit)body;unit.setOwnerName(owner);unit.setScenarioRoleIndex(scenario);body.setNoAi(true);body.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);position(body,Vec3.atBottomCenterOf(at(x,y,z)));h.assertTrue(h.getLevel().addFreshEntity(body),"real prepare body enters ordinary entity storage");}
    private static void damage(LivingEntity body,double amount){body.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(amount);if(body.getAttribute(Attributes.ATTACK_DAMAGE)!=null)body.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(amount);var crit=body.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get());if(crit!=null)crit.setBaseValue(0);}
    private static void neutral(LivingEntity body){body.getAttribute(Attributes.ARMOR).setBaseValue(0);body.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);body.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);body.setAbsorptionAmount(0);for(var attr:List.of(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get(),AttributeRegistrar.RANGED_DAMAGE_RESIST.get()))if(body.getAttribute(attr)!=null)body.getAttribute(attr).setBaseValue(0);}
    private static void durable(LivingEntity body){body.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);body.setHealth(1000);}
    private static void position(Entity body,Vec3 position){body.moveTo(position.x,position.y,position.z);body.setDeltaMovement(Vec3.ZERO);}
    private static void published(GameTestHelper h,Entity body){h.assertTrue(body.tickCount>3&&h.getLevel().getEntity(body.getId())==body&&h.getLevel().getEntity(body.getUUID())==body,"actual original ID/UUID publication and natural entity ticks: "+body.getUUID()+" ticks="+body.tickCount);}
    private static BlockPos at(int x,int y,int z){return ORIGIN.offset(x,y,z);}
    private static FakePlayer player(ServerLevel level,String name){return PLAYERS.computeIfAbsent(name,key->{var player=new FakePlayer(level,new GameProfile(UUID.nameUUIDFromBytes(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),key));player.connection=new SilentHandler(player);position(player,Vec3.atBottomCenterOf(at(key.equals(OWNER)?2:29,1,29)));level.addNewPlayer(player);PlayerServerEvents.players.add(player);return player;});}
    private static void ledger(GameTestHelper h){for(String owner:List.of(OWNER,ENEMY)){var resources=ResourcesServerEvents.resourcesList.stream().filter(r->r.ownerName.equals(owner)).findFirst().orElse(null);h.assertTrue(resources!=null,"original normally loaded resource account exists, never recreate on verify");h.assertTrue(resources.food==123&&resources.wood==234&&resources.ore==345&&resources.emerald==456&&resources.foodToAdd==0&&resources.woodToAdd==0&&resources.oreToAdd==0&&resources.emeraldToAdd==0,"strict original bank and pending balances survive without tolerance or refill: "+owner);for(var body:UnitServerEvents.getAllUnits())if(body instanceof Unit unit&&unit.getOwnerName().equals(owner))h.assertTrue(unit.getItems().isEmpty(),"declared empty cargo remains empty, not erased/recreated on save: "+body.getUUID());}System.out.println("SNAIL_SHELL_RESTART_LEDGER time="+h.getLevel().getGameTime()+" banks=123,234,345,456 pending=0 cargo=empty");}
    private static void arena(ServerLevel level){for(int x=0;x<32;x++)for(int z=0;z<32;z++)level.setBlock(at(x,9,z),Blocks.STONE.defaultBlockState(),2);level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,new AABB(ORIGIN.getX(),ORIGIN.getY()+1,ORIGIN.getZ(),ORIGIN.getX()+32,level.getMaxBuildHeight(),ORIGIN.getZ()+32)).forEach(Entity::discard);for(int x=0;x<32;x++)for(int z=0;z<32;z++){level.setBlock(at(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<9;y++)level.setBlock(at(x,y,z),(x==0||x==31||z==0||z==31?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}level.getEntitiesOfClass(ItemEntity.class,new AABB(ORIGIN,ORIGIN.offset(32,10,32))).forEach(Entity::discard);}
    private static void close(GameTestHelper h,double actual,double expected,String why){h.assertTrue(Double.isFinite(actual)&&Math.abs(actual-expected)<.001,why+" expected="+expected+" actual="+actual);}
    private static final class SilentHandler extends ServerGamePacketListenerImpl{SilentHandler(ServerPlayer player){super(player.serverLevel().getServer(),new SilentConnection(),player);}@Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> packet){}@Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){}}
    private static final class SilentConnection extends net.minecraft.network.Connection{SilentConnection(){super(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);}@Override public void tick(){}@Override public void send(net.minecraft.network.protocol.Packet<?> packet){}@Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){}}
}
