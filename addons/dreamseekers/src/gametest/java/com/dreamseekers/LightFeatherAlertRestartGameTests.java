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
import java.io.File;
import java.util.*;

/** Genuine normal-save/two-JVM passive boundary. Real native goal attacks, no direct hurt,
 * HP/iframe/clock restoration, manual effect invocation or synthetic body reconstruction.
 * Original native NoAI=false is observed before containment. Own cooldown200 is not enlarged
 * for loading; original buff60 is proved active in prepare and actual normal saved clock.
 */
@GameTestHolder("dreamseekers_light_feather_alert_skill_restart")
@PrefixGameTestTemplate(false)
public final class LightFeatherAlertRestartGameTests {
    private static final String OWNER="FeatherRestart_Owner",ENEMY="FeatherRestart_Enemy";
    private static final BlockPos ORIGIN=new BlockPos(110592,90,110592);
    private static final File MANIFEST=new File("light-feather-alert-restart-expectations.nbt");
    private static final UUID MS_TECH=UUID.fromString("31ae48b7-50cc-4729-833a-8345a929823e");
    private static final List<String> KEYS=List.of("chicken","attacker","ownerReserve","enemyReserve");
    private static final Map<String,FakePlayer> PLAYERS=new HashMap<>();

    @GameTest(template="unit_isolation_arena",batch="dreamseekers_light_feather_alert_skill_restart",timeoutTicks=850)
    public static void originalRealDamagePaidCooldownPersistsButActiveAlertDoesNotReplayAndFreshDamageTriggers(GameTestHelper h) throws Exception {
        switch(System.getProperty("dreamseekers.lightFeatherAlert.test.phase","")) {
            case "prepare" -> prepare(h);
            case "verify" -> verify(h);
            default -> throw new IllegalArgumentException("Unknown light-feather-alert restart phase");
        }
    }

    private static void prepare(GameTestHelper h) {
        var level=h.getLevel();
        h.assertTrue(!MANIFEST.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null&&PlayerServerEvents.getRTSPlayer(ENEMY)==null,"fresh normal-save fixture, not consumed verify state");
        var expected=new CompoundTag();expected.putInt("format",1);expected.putLong("prepareJvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        List<Long> forced=new ArrayList<>();
        for(int x=ORIGIN.getX()>>4;x<=(ORIGIN.getX()+31)>>4;x++)for(int z=ORIGIN.getZ()>>4;z<=(ORIGIN.getZ()+31)>>4;z++) {
            long key=ChunkPos.asLong(x,z);h.assertTrue(!level.getForcedChunks().contains(key),"only new fixture chunk tickets");level.setChunkForced(x,z,true);forced.add(key);
        }
        expected.putLongArray("forcedChunks",forced);arena(level);
        for(String name:List.of(OWNER,ENEMY)){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,123,234,345,456));player(level,name);}
        Map<String,Mob> bodies=new LinkedHashMap<>();
        var chicken=DreamseekersMod.PINK_CHICKEN_SCOUT.get().create(level);owned(h,chicken,OWNER,12.1,8.5,9201);durable(chicken);bodies.put("chicken",chicken);
        var attacker=DreamseekersMod.SHADOW_GHOST_UNIT.get().create(level);owned(h,attacker,ENEMY,11,8.5,9202);damage(attacker);bodies.put("attacker",attacker);
        for(int i=0;i<2;i++){var reserve=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(level);owned(h,reserve,i==0?OWNER:ENEMY,i==0?4.5:24.5,25.5,9203+i);bodies.put(i==0?"ownerReserve":"enemyReserve",reserve);}
        for(var entry:bodies.entrySet())expected.putUUID(entry.getKey(),entry.getValue().getUUID());
        h.startSequence().thenWaitUntil(()->{for(var body:bodies.values())published(h,body);for(var p:PLAYERS.values())published(h,p);})
          .thenExecute(()->{
            h.assertTrue(!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isEnabled(),"actual fog-off profile");ledger(h);
            h.assertTrue(chicken.lightFeatherAlert().cooldownTicks()==0&&!chicken.lightFeatherAlert().active(),"original unused passive");
            attack(h,attacker,chicken);
          }).thenWaitUntil(()->h.assertTrue(chicken.getHealth()<1000,"real command/native goal must actually deal HP damage"))
          .thenExecute(()->{
            long now=level.getGameTime(),deadline=chicken.lightFeatherAlert().cooldownDeadline(),hit=deadline-200;
            close(h,chicken.getHealth(),990,"one natural enemy physical hit of10");h.assertTrue(hit<=now&&now-hit<=1&&chicken.lightFeatherAlert().active(),"first actual write triggered same-tick passive, observed within next tick");
            close(h,SkillEffects.bonus(chicken,BuffKind.MOVEMENT_SPEED),.2,"real passive20");
            expected.putLong("hitGameTime",hit);expected.putLong("buffExpiresAt",hit+60);expected.putLong("cooldownUntil",deadline);
            stop(h,attacker);
          }).thenWaitUntil(()->h.assertTrue(!UnitServerEvents.hasPendingImmediateAction(attacker.getId())&&attacker.getTarget()==null,"real STOP consumed before normal save; no latent old target"))
          .thenExecute(()->{
            long now=level.getGameTime();h.assertTrue(now<expected.getLong("buffExpiresAt")&&chicken.lightFeatherAlert().active(),"short effect active BEFORE original60 deadline at snapshot");
            close(h,chicken.getHealth(),990,"stopped attacker causes no second preparation hit");
            expected.putLong("snapshotGameTime",now);expected.putInt("snapshotCooldown",chicken.lightFeatherAlert().cooldownTicks());expected.putBoolean("snapshotActive",true);
            expected.putUUID("permanentMovementUuid",MS_TECH);expected.putUUID("transientMovementUuid",BuffKind.MOVEMENT_SPEED.modifierId);
            for(var entry:bodies.entrySet()){
                var body=entry.getValue();var unit=(Unit)body;var state=new CompoundTag();
                state.putString("type",EntityType.getKey(body.getType()).toString());state.putString("class",body.getClass().getName());state.putString("owner",unit.getOwnerName());state.putInt("scenario",unit.getScenarioRoleIndex());
                state.putFloat("health",body.getHealth());state.putBoolean("noGravity",body.isNoGravity());state.putLong("anchor",unit.getAnchor().asLong());
                state.putDouble("movementBase",body.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue());state.putDouble("maxHealthBase",body.getAttribute(Attributes.MAX_HEALTH).getBaseValue());
                h.assertTrue(body.isNoAi()&&body.getActiveEffects().isEmpty()&&body.getTarget()==null,"original contained body with no native potion/target: "+entry.getKey());
                if(body instanceof PinkChickenScout){state.putDouble("flyingBase",body.getAttribute(Attributes.FLYING_SPEED).getBaseValue());state.putLong("cooldownUntil",chicken.lightFeatherAlert().cooldownDeadline());}
                permanent(h,body,state,entry.getKey().equals("chicken"));expected.put(entry.getKey()+"State",state);
            }
            ledger(h);try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception e){throw new RuntimeException(e);}
            System.out.println("DREAMSEEKERS_LIGHT_FEATHER_RESTART_PREPARED hitAt="+expected.getLong("hitGameTime")+" snapshotAt="+now+" buffExpiresAt="+expected.getLong("buffExpiresAt")+" cooldownUntil="+expected.getLong("cooldownUntil")+" hp="+chicken.getHealth());
          }).thenSucceed();
    }

    private static void verify(GameTestHelper h) throws Exception {
        var level=h.getLevel();h.assertTrue(MANIFEST.isFile(),"original manifest exists");var expected=NbtIo.readCompressed(MANIFEST);
        h.assertTrue(expected.getInt("format")==1&&expected.getLong("prepareJvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"different genuine JVM, never reconstructed original bodies");
        h.assertTrue(expected.getLong("buffExpiresAt")==expected.getLong("hitGameTime")+60&&expected.getLong("cooldownUntil")==expected.getLong("hitGameTime")+200&&expected.getBoolean("snapshotActive"),"original declared60/200 timeline");
        for(long key:expected.getLongArray("forcedChunks")){h.assertTrue(level.getForcedChunks().contains(key),"original chunk tickets survive");var c=new ChunkPos(key);level.getChunk(c.x,c.z);}
        for(String name:List.of(OWNER,ENEMY))player(level,name);
        Map<String,Mob> bodies=new LinkedHashMap<>();long[] observed={-1},freshHit={-1};
        h.startSequence().thenWaitUntil(()->{for(String key:KEYS){var body=level.getEntity(expected.getUUID(key));h.assertTrue(body instanceof Mob,"real original UUID from loader: "+key);published(h,body);}for(var p:PLAYERS.values())published(h,p);})
          .thenExecute(()->{
            long now=level.getGameTime();observed[0]=now;h.assertTrue(now>=expected.getLong("snapshotGameTime")&&now<expected.getLong("cooldownUntil"),"actual loaded observation before original200 deadline, never enlarged startup window");
            for(String key:KEYS){
                var body=(Mob)level.getEntity(expected.getUUID(key));bodies.put(key,body);var unit=(Unit)body;var state=expected.getCompound(key+"State");
                h.assertTrue(!body.isNoAi()&&body.isNoGravity()==state.getBoolean("noGravity")&&body.getClass().getName().equals(state.getString("class"))&&EntityType.getKey(body.getType()).toString().equals(state.getString("type")),"observe original native loaded AI/type/gravity BEFORE containment: "+key);
                h.assertTrue(unit.getOwnerName().equals(state.getString("owner"))&&unit.getScenarioRoleIndex()==state.getInt("scenario")&&unit.getAnchor().equals(BlockPos.of(state.getLong("anchor"))),"original owner/role/anchor: "+key);
                h.assertTrue(UnitServerEvents.getAllUnits().stream().filter(e->e.getUUID().equals(body.getUUID())).count()==1,"one original roster body");close(h,body.getHealth(),state.getFloat("health"),"no reload HP restoration or autonomous hit: "+key);
                h.assertTrue(body.getActiveEffects().isEmpty(),"no native potion replay");permanent(h,body,state,false);
            }
            var chicken=(PinkChickenScout)bodies.get("chicken");h.assertTrue(!chicken.lightFeatherAlert().active()&&chicken.lightFeatherAlert().cooldownDeadline()==expected.getLong("cooldownUntil")&&chicken.lightFeatherAlert().cooldownTicks()==expected.getLong("cooldownUntil")-now,"loaded passive short effect absent, exact original paid cooldown retained");
            close(h,SkillEffects.bonus(chicken,BuffKind.MOVEMENT_SPEED),0,"no replay movement bonus");ledger(h);
            System.out.println("LIGHT_FEATHER_RESTART_LOADED_BOUNDARY observedAt="+now+" cooldownUntil="+expected.getLong("cooldownUntil")+" elapsed="+(now-expected.getLong("snapshotGameTime")));
            for(var body:bodies.values())body.setNoAi(true);attack(h,(ShadowGhostUnit)bodies.get("attacker"),chicken);
          }).thenWaitUntil(()->h.assertTrue(bodies.get("chicken").getHealth()<990,"real new native hit while saved cooldown remains"))
          .thenExecute(()->{
            long now=level.getGameTime();var chicken=(PinkChickenScout)bodies.get("chicken");
            h.assertTrue(now<expected.getLong("cooldownUntil"),"negative real hit observed before original paid deadline");close(h,chicken.getHealth(),980,"second real10 HP hit, no helper damage");
            h.assertTrue(!chicken.lightFeatherAlert().active()&&chicken.lightFeatherAlert().cooldownDeadline()==expected.getLong("cooldownUntil"),"real injury cannot reset/refund/extend original cooldown");
            stop(h,(ShadowGhostUnit)bodies.get("attacker"));System.out.println("LIGHT_FEATHER_RESTART_NEGATIVE_HIT observedAt="+now+" cooldownUntil="+expected.getLong("cooldownUntil")+" hp="+chicken.getHealth());
          }).thenWaitUntil(()->{
            var chicken=(PinkChickenScout)bodies.get("chicken");h.assertTrue(!UnitServerEvents.hasPendingImmediateAction(bodies.get("attacker").getId()),"negative-hit STOP consumed");close(h,chicken.getHealth(),980,"no extra attacks during natural cooldown wait");h.assertTrue(level.getGameTime()>=expected.getLong("cooldownUntil"),"naturally wait original200 deadline, no clock/iframe reset");
          }).thenExecute(()->{var chicken=(PinkChickenScout)bodies.get("chicken");h.assertTrue(!chicken.lightFeatherAlert().active()&&chicken.lightFeatherAlert().cooldownTicks()==0,"expiry alone never triggers passive");attack(h,(ShadowGhostUnit)bodies.get("attacker"),chicken);})
          .thenWaitUntil(()->h.assertTrue(bodies.get("chicken").getHealth()<980,"fresh native hit after original200 triggers a new paid period"))
          .thenExecute(()->{
            var chicken=(PinkChickenScout)bodies.get("chicken");freshHit[0]=chicken.lightFeatherAlert().cooldownDeadline()-200;
            close(h,chicken.getHealth(),970,"third real10 HP hit");h.assertTrue(freshHit[0]>=expected.getLong("cooldownUntil")&&level.getGameTime()-freshHit[0]<=1&&chicken.lightFeatherAlert().active(),"fresh actual hit establishes its own200 and active60");
            close(h,SkillEffects.bonus(chicken,BuffKind.MOVEMENT_SPEED),.2,"fresh passive20 restored through real new damage");stop(h,(ShadowGhostUnit)bodies.get("attacker"));
            System.out.println("LIGHT_FEATHER_RESTART_FRESH_HIT hitAt="+freshHit[0]+" observedAt="+level.getGameTime()+" cooldownUntil="+chicken.lightFeatherAlert().cooldownDeadline()+" hp="+chicken.getHealth());
          }).thenWaitUntil(()->h.assertTrue(level.getGameTime()>freshHit[0]+60,"natural fresh60 expiry"))
          .thenExecute(()->{
            var chicken=(PinkChickenScout)bodies.get("chicken");close(h,chicken.getHealth(),970,"no post-hit duplicate damage");h.assertTrue(!chicken.lightFeatherAlert().active()&&chicken.lightFeatherAlert().cooldownDeadline()==freshHit[0]+200,"fresh expiry clears buff without refunding own200");
            for(String key:KEYS){var body=bodies.get(key);permanent(h,body,expected.getCompound(key+"State"),false);if(!key.equals("chicken"))close(h,body.getHealth(),expected.getCompound(key+"State").getFloat("health"),"only declared chicken hits change HP");}
            ledger(h);System.out.println("DREAMSEEKERS_LIGHT_FEATHER_RESTART_UUID_PAID_CLOCK_NO_BUFF_REPLAY_FRESH_HIT_PASS loadedAt="+observed[0]+" originalCooldownUntil="+expected.getLong("cooldownUntil")+" freshHitAt="+freshHit[0]);
            for(var body:bodies.values())body.discard();for(var p:PLAYERS.values()){membership(p,false);p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}PLAYERS.clear();
            PlayerServerEvents.players.removeIf(p->List.of(OWNER,ENEMY).contains(p.getGameProfile().getName()));PlayerServerEvents.rtsPlayers.removeIf(p->List.of(OWNER,ENEMY).contains(p.name));ResourcesServerEvents.resourcesList.removeIf(r->List.of(OWNER,ENEMY).contains(r.ownerName));
            for(long key:expected.getLongArray("forcedChunks")){var c=new ChunkPos(key);level.setChunkForced(c.x,c.z,false);}
          }).thenSucceed();
    }
    private static void attack(GameTestHelper h,ShadowGhostUnit actor,PinkChickenScout target){actor.setNoAi(false);order(h,actor,UnitAction.ATTACK,target);}
    private static void stop(GameTestHelper h,ShadowGhostUnit actor){actor.setNoAi(true);order(h,actor,UnitAction.STOP,null);}
    private static void damage(LivingEntity source){source.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(10);if(source.getAttribute(Attributes.ATTACK_DAMAGE)!=null)source.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10);source.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);source.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).setBaseValue(1);}
    private static void order(GameTestHelper h,Mob source,UnitAction action,Entity target){
        try{String owner=((Unit)source).getOwnerName();var sender=player(h.getLevel(),owner);var connection=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);connection.setListener(sender.connection);var ctor=net.minecraftforge.network.NetworkEvent.Context.class.getDeclaredConstructor(net.minecraft.network.Connection.class,net.minecraftforge.network.NetworkDirection.class,int.class);ctor.setAccessible(true);var context=ctor.newInstance(connection,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER,-1);h.assertTrue(context.getSender()==sender,"real packet owner context");h.assertTrue(new com.solegendary.reignofnether.unit.packets.UnitActionServerboundPacket(owner,action,target==null?-1:target.getId(),new int[]{source.getId()},BlockPos.ZERO,BlockPos.ZERO,false).handle(()->context),"real authenticated native order");}catch(ReflectiveOperationException e){throw new RuntimeException(e);}
    }
    private static void permanent(GameTestHelper h,Mob body,CompoundTag state,boolean active){
        var movement=body.getAttribute(Attributes.MOVEMENT_SPEED);close(h,movement.getBaseValue(),state.getDouble("movementBase"),"original movement base");
        h.assertTrue(movement.getModifier(MS_TECH)!=null&&movement.getModifiers().size()==(active?2:1)&&(movement.getModifier(BuffKind.MOVEMENT_SPEED.modifierId)!=null)==active,"exact permanent plus only expected transient modifier");
        close(h,movement.getValue(),(state.getDouble("movementBase")+.03)*(active?1.2:1),"native effective movement modifier");
        if(body instanceof PinkChickenScout){var flying=body.getAttribute(Attributes.FLYING_SPEED);close(h,flying.getBaseValue(),state.getDouble("flyingBase"),"original flying base");h.assertTrue((flying.getModifier(BuffKind.MOVEMENT_SPEED.modifierId)!=null)==active&&flying.getModifiers().size()==(active?1:0),"flying transient lifetime");close(h,flying.getValue(),state.getDouble("flyingBase")*(active?1.2:1),"native effective flying modifier");}
    }
    private static void putVec(CompoundTag tag,String key,Vec3 value){var list=new ListTag();list.add(DoubleTag.valueOf(value.x));list.add(DoubleTag.valueOf(value.y));list.add(DoubleTag.valueOf(value.z));tag.put(key,list);}
    private static Vec3 vec(CompoundTag tag,String key){var list=tag.getList(key,Tag.TAG_DOUBLE);return new Vec3(list.getDouble(0),list.getDouble(1),list.getDouble(2));}
    private static void owned(GameTestHelper h,Mob body,String owner,double x,double z,int role) {
        var unit=(Unit)body;unit.setOwnerName(owner);unit.setScenarioRoleIndex(role);unit.setOnDeathCommand("");
        body.setNoAi(true);body.setPersistenceRequired();body.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);
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
        System.out.println("LIGHT_FEATHER_RESTART_LEDGER time="+h.getLevel().getGameTime()+" banks=123,234,345,456 pending=0 cargo=empty");
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
