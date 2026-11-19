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

/** Normal Anvil save across two JVMs: paid480 survives, transient20 does not replay.
 * The external read-only disk gate must establish savedTime < original releaseAt.
 * Startup may pass releaseAt; loaded absence then proves no replay, not cancellation before20.
 * No manual body reconstruction, saved-clock rewrite, HP reset or cooldown extension.
 */
@GameTestHolder("dreamseekers_shadow_golem_strike_skill_restart")
@PrefixGameTestTemplate(false)
public final class ShadowGolemStrikeRestartGameTests {
    private static final String OWNER="StrikeRestart_Owner",ENEMY="StrikeRestart_Enemy";
    private static final BlockPos ORIGIN=new BlockPos(118784,90,118784);
    private static final File MANIFEST=new File("shadow-golem-strike-restart-expectations.nbt");
    private static final UUID MS_TECH=UUID.fromString("4a29bd03-5c8b-49da-a5a7-814c3153c451");
    private static final List<String> KEYS=List.of("paidCaster","unusedCaster","target","ownerReserve","enemyReserve");
    private static final Map<String,FakePlayer> PLAYERS=new HashMap<>();

    @GameTest(template="unit_isolation_arena",batch="dreamseekers_shadow_golem_strike_skill_restart",timeoutTicks=850)
    public static void originalPaid480AndBodiesPersistButActive20NeverReplaysAndFreshRealReleaseWorks(GameTestHelper h) throws Exception {
        switch(System.getProperty("dreamseekers.shadowGolemStrike.test.phase","")) {
            case "prepare" -> prepare(h);
            case "verify" -> verify(h);
            default -> throw new IllegalArgumentException("Unknown shadow-golem-strike restart phase");
        }
    }
    private static void prepare(GameTestHelper h) {
        var level=h.getLevel();
        h.assertTrue(!MANIFEST.exists()&&PlayerServerEvents.getRTSPlayer(OWNER)==null&&PlayerServerEvents.getRTSPlayer(ENEMY)==null,"fresh normal-save fixture, not reused verify state");
        var expected=new CompoundTag();expected.putInt("format",1);expected.putLong("prepareJvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        List<Long> forced=new ArrayList<>();
        for(int x=ORIGIN.getX()>>4;x<=(ORIGIN.getX()+31)>>4;x++)for(int z=ORIGIN.getZ()>>4;z<=(ORIGIN.getZ()+31)>>4;z++) {
            long key=ChunkPos.asLong(x,z);h.assertTrue(!level.getForcedChunks().contains(key),"new isolated chunk tickets");level.setChunkForced(x,z,true);forced.add(key);
        }
        expected.putLongArray("forcedChunks",forced);arena(level);
        for(String name:List.of(OWNER,ENEMY)){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,DreamseekersMod.DREAMSEEKERS));ResourcesServerEvents.resourcesList.add(new Resources(name,123,234,345,456));player(level,name);}
        Map<String,Mob> bodies=new LinkedHashMap<>();
        var paid=DreamseekersMod.SHADOW_GOLEM_UNIT.get().create(level);owned(h,paid,OWNER,9.5,8.5,9301);damage(paid);bodies.put("paidCaster",paid);
        var unused=DreamseekersMod.SHADOW_GOLEM_UNIT.get().create(level);owned(h,unused,OWNER,15.5,8.5,9302);damage(unused);bodies.put("unusedCaster",unused);
        var target=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(level);owned(h,target,ENEMY,12.5,8.5,9303);durable(target);bodies.put("target",target);
        for(int i=0;i<2;i++){var reserve=DreamseekersMod.GOLDEN_FOX_UNIT.get().create(level);owned(h,reserve,i==0?OWNER:ENEMY,i==0?4.5:24.5,25.5,9304+i);bodies.put(i==0?"ownerReserve":"enemyReserve",reserve);}
        for(var entry:bodies.entrySet())expected.putUUID(entry.getKey(),entry.getValue().getUUID());
        h.startSequence().thenWaitUntil(()->{for(var body:bodies.values())published(h,body);for(var p:PLAYERS.values())published(h,p);})
          .thenExecute(()->{
            h.assertTrue(!com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.isEnabled(),"actual fog-off profile");ledger(h);
            long now=level.getGameTime();expected.putLong("reservationGameTime",now);expected.putLong("releaseAt",now+20);expected.putLong("cooldownUntil",now+480);
            h.assertTrue(cast(h,paid,target)==SkillResult.SUCCESS&&cd(paid)==480&&cd(unused)==0,"actual encoded owner request paid480, unused original untouched");
            h.assertTrue(ShadowGolemStrike.active(paid)&&ShadowGolemStrike.remainingTicks(paid)==20,"real active20 before normal shutdown");close(h,target.getHealth(),1000,"no premature hit");
            expected.putLong("snapshotGameTime",now);expected.putInt("snapshotCooldown",cd(paid));expected.putInt("snapshotWindup",ShadowGolemStrike.remainingTicks(paid));expected.putBoolean("snapshotActive",true);
            expected.putUUID("permanentMovementUuid",MS_TECH);
            for(var entry:bodies.entrySet()){
                var body=entry.getValue();var unit=(Unit)body;var state=new CompoundTag();
                state.putString("type",EntityType.getKey(body.getType()).toString());state.putString("class",body.getClass().getName());state.putString("owner",unit.getOwnerName());state.putInt("scenario",unit.getScenarioRoleIndex());
                state.putFloat("health",body.getHealth());state.putBoolean("noGravity",body.isNoGravity());state.putLong("anchor",unit.getAnchor().asLong());
                state.putDouble("movementBase",body.getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue());state.putDouble("maxHealthBase",body.getAttribute(Attributes.MAX_HEALTH).getBaseValue());
                h.assertTrue(body.isNoAi()&&body.getActiveEffects().isEmpty()&&body.getTarget()==null&&!unit.getAnchor().equals(BlockPos.ZERO),"original contained body, no potion/target, real anchor: "+entry.getKey());
                if(body instanceof ShadowGolemUnit golem){state.putInt("cooldown",cd(golem));state.putLong("revision",golem.rts().skills().revision());state.putDouble("attackDamage",golem.getUnitAttackDamage());}
                permanent(h,body,state);expected.put(entry.getKey()+"State",state);
            }
            ledger(h);try{NbtIo.writeCompressed(expected,MANIFEST);}catch(Exception e){throw new RuntimeException(e);}
            System.out.println("DREAMSEEKERS_SHADOW_GOLEM_STRIKE_RESTART_PREPARED reservedAt="+now+" releaseAt="+(now+20)+" cooldownUntil="+(now+480)+" active="+ShadowGolemStrike.active(paid)+" targetHp="+target.getHealth());
            // No charge clear, save invocation, body discard, reposition or clock mutation. Normal stop saves.
          }).thenSucceed();
    }
    private static void verify(GameTestHelper h) throws Exception {
        var level=h.getLevel();h.assertTrue(MANIFEST.isFile(),"original manifest exists");var expected=NbtIo.readCompressed(MANIFEST);
        h.assertTrue(expected.getInt("format")==1&&expected.getLong("prepareJvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"different real JVM, no reconstructed bodies");
        h.assertTrue(expected.getLong("releaseAt")==expected.getLong("reservationGameTime")+20&&expected.getLong("cooldownUntil")==expected.getLong("reservationGameTime")+480&&expected.getBoolean("snapshotActive")&&expected.getInt("snapshotWindup")==20&&expected.getInt("snapshotCooldown")==480,"original fixed20/480 snapshot, never extended for load");
        for(long key:expected.getLongArray("forcedChunks")){h.assertTrue(level.getForcedChunks().contains(key),"original tickets survive");var c=new ChunkPos(key);level.getChunk(c.x,c.z);}
        for(String name:List.of(OWNER,ENEMY))player(level,name);
        Map<String,Mob> bodies=new LinkedHashMap<>();long[] observed={-1},freshReserved={-1},paidReserved={-1};var probe=new RestartProbe(expected.getUUID("target"));MinecraftForge.EVENT_BUS.register(probe);
        h.startSequence().thenWaitUntil(()->{for(String key:KEYS){var body=level.getEntity(expected.getUUID(key));h.assertTrue(body instanceof Mob,"real original UUID from loader: "+key);published(h,body);}for(var p:PLAYERS.values())published(h,p);})
          .thenExecute(()->{
            long now=level.getGameTime();observed[0]=now;h.assertTrue(now>=expected.getLong("snapshotGameTime")&&now<expected.getLong("cooldownUntil"),"observation before original paid480 expires");
            for(String key:KEYS){
                var body=(Mob)level.getEntity(expected.getUUID(key));bodies.put(key,body);var unit=(Unit)body;var state=expected.getCompound(key+"State");
                h.assertTrue(!body.isNoAi()&&body.isNoGravity()==state.getBoolean("noGravity")&&body.getClass().getName().equals(state.getString("class"))&&EntityType.getKey(body.getType()).toString().equals(state.getString("type")),"native loaded AI/type/gravity observed BEFORE containment: "+key);
                h.assertTrue(unit.getOwnerName().equals(state.getString("owner"))&&unit.getScenarioRoleIndex()==state.getInt("scenario")&&unit.getAnchor().equals(BlockPos.of(state.getLong("anchor"))),"original owner/role/anchor: "+key);
                h.assertTrue(UnitServerEvents.getAllUnits().stream().filter(e->e.getUUID().equals(body.getUUID())).count()==1,"single original roster entry");close(h,body.getHealth(),state.getFloat("health"),"no restored HP or autonomous hit: "+key);close(h,body.getAttribute(Attributes.MAX_HEALTH).getBaseValue(),state.getDouble("maxHealthBase"),"original health attribute");
                h.assertTrue(body.getActiveEffects().isEmpty(),"no potion replay");permanent(h,body,state);
                if(body instanceof ShadowGolemUnit golem){h.assertTrue(!ShadowGolemStrike.active(golem)&&ShadowGolemStrike.remainingTicks(golem)==0,"no transient charge reconstructed");close(h,golem.getUnitAttackDamage(),state.getDouble("attackDamage"),"original paid attack attribute");h.assertTrue(golem.rts().skills().revision()>state.getLong("revision"),"ordinary load invalidates old request revision");}
            }
            var paid=(ShadowGolemUnit)bodies.get("paidCaster");var unused=(ShadowGolemUnit)bodies.get("unusedCaster");var target=bodies.get("target");
            h.assertTrue(cd(paid)==expected.getLong("cooldownUntil")-now&&cd(unused)==0,"exact original paid absolute clock, unused body still unused");
            for(var body:bodies.values())body.setNoAi(true);h.assertTrue(cast(h,paid,target)==SkillResult.COOLDOWN,"saved charge cannot refund paid cooldown");
            close(h,target.getHealth(),1000,"old windup has not delivered any hit on load");ledger(h);
            System.out.println("SHADOW_GOLEM_STRIKE_RESTART_LOADED observedAt="+now+" originalReleaseAt="+expected.getLong("releaseAt")+" cooldownUntil="+expected.getLong("cooldownUntil")+" paidRemaining="+cd(paid));
          }).thenIdle(21).thenExecute(()->{
            var paid=(ShadowGolemUnit)bodies.get("paidCaster");var unused=(ShadowGolemUnit)bodies.get("unusedCaster");var target=bodies.get("target");
            h.assertTrue(level.getGameTime()>expected.getLong("releaseAt")&&!ShadowGolemStrike.active(paid)&&probe.hits.isEmpty(),"natural post-load21 has no old release replay");close(h,target.getHealth(),1000,"old saved target remains undamaged");
            freshReserved[0]=level.getGameTime();h.assertTrue(cast(h,unused,target)==SkillResult.SUCCESS&&cd(unused)==480&&ShadowGolemStrike.remainingTicks(unused)==20,"fresh original unused caster reserves actual20/480");
          }).thenIdle(19).thenExecute(()->{close(h,bodies.get("target").getHealth(),1000,"fresh19 has no early impact");h.assertTrue(probe.hits.isEmpty(),"no early dedicated damage receipt");})
          .thenWaitUntil(()->h.assertTrue(bodies.get("target").getHealth()<1000,"natural twentieth source tick delivers real skill hurt"))
          .thenExecute(()->{
            var paid=(ShadowGolemUnit)bodies.get("paidCaster");var unused=(ShadowGolemUnit)bodies.get("unusedCaster");
            close(h,bodies.get("target").getHealth(),985,"one actual150 percent attack10 physical hit");h.assertTrue(probe.hits.size()==1&&probe.hits.get(0).time()==freshReserved[0]+20&&probe.hits.get(0).source().equals(unused.getUUID()),"exact real20 event time and original unused source UUID");
            h.assertTrue(cd(paid)==Math.max(0,expected.getLong("cooldownUntil")-level.getGameTime())&&!ShadowGolemStrike.active(paid)&&!ShadowGolemStrike.active(unused),"fresh release never refreshes old paid clock");
            System.out.println("SHADOW_GOLEM_STRIKE_RESTART_FRESH_RELEASE reservedAt="+freshReserved[0]+" hitAt="+probe.hits.get(0).time()+" targetHp="+bodies.get("target").getHealth());
          }).thenWaitUntil(()->{close(h,bodies.get("target").getHealth(),985,"no extra native or old-charge hit while waiting");h.assertTrue(level.getGameTime()>=expected.getLong("cooldownUntil"),"wait natural original480 expiry");})
          .thenExecute(()->{var paid=(ShadowGolemUnit)bodies.get("paidCaster");paidReserved[0]=level.getGameTime();h.assertTrue(cd(paid)==0&&cast(h,paid,bodies.get("target"))==SkillResult.SUCCESS&&cd(paid)==480,"original paid caster finally recasts after natural expiry, no deadline rewrite");})
          .thenWaitUntil(()->h.assertTrue(bodies.get("target").getHealth()<985,"new paid source real20 release"))
          .thenExecute(()->{
            close(h,bodies.get("target").getHealth(),970,"second independent actual15 hit");h.assertTrue(probe.hits.size()==2&&probe.hits.get(1).time()==paidReserved[0]+20&&probe.hits.get(1).source().equals(bodies.get("paidCaster").getUUID()),"second exact new20 event and correct original paid source");
            for(String key:KEYS){var body=bodies.get(key);permanent(h,body,expected.getCompound(key+"State"));if(!key.equals("target"))close(h,body.getHealth(),expected.getCompound(key+"State").getFloat("health"),"only declared two target hits change HP");}
            ledger(h);System.out.println("DREAMSEEKERS_SHADOW_GOLEM_STRIKE_RESTART_UUID_PAID480_NO_WINDUP_REPLAY_FRESH20_PASS loadedAt="+observed[0]+" originalCooldownUntil="+expected.getLong("cooldownUntil")+" freshReservedAt="+freshReserved[0]+" paidReservedAt="+paidReserved[0]);
            MinecraftForge.EVENT_BUS.unregister(probe);
            for(var body:bodies.values())body.discard();for(var p:PLAYERS.values()){membership(p,false);p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}PLAYERS.clear();
            PlayerServerEvents.players.removeIf(p->List.of(OWNER,ENEMY).contains(p.getGameProfile().getName()));PlayerServerEvents.rtsPlayers.removeIf(p->List.of(OWNER,ENEMY).contains(p.name));ResourcesServerEvents.resourcesList.removeIf(r->List.of(OWNER,ENEMY).contains(r.ownerName));
            for(long key:expected.getLongArray("forcedChunks")){var c=new ChunkPos(key);level.setChunkForced(c.x,c.z,false);}
          }).thenSucceed();
    }
    private record Receipt(long time,UUID source){}
    public static final class RestartProbe {
        final UUID target;final List<Receipt> hits=new ArrayList<>();RestartProbe(UUID target){this.target=target;}
        @SubscribeEvent public void shadowGolemRestartRecordDedicatedDamage(LivingDamageEvent e){if(e.getEntity().getUUID().equals(target)&&ShadowGolemStrike.isStrike(e.getSource())&&!e.isCanceled()&&e.getAmount()>0)hits.add(new Receipt(e.getEntity().level().getGameTime(),e.getSource().getEntity().getUUID()));}
    }
    private static int cd(ShadowGolemUnit source){return source.rts().skills().cooldownTicks(UnitSkill.SHADOW_GOLEM_STRIKE);}
    private static SkillResult cast(GameTestHelper h,ShadowGolemUnit source,LivingEntity target){
        var packet=new SkillNetwork.Cast(new SkillRequest(UnitSkill.SHADOW_GOLEM_STRIKE,source.getId(),source.getUUID(),source.rts().skills().revision(),target.getId(),target.getUUID()));
        var bytes=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());SkillNetwork.Cast decoded;
        try{packet.encode(bytes);decoded=SkillNetwork.Cast.decode(bytes);h.assertTrue(decoded.equals(packet)&&bytes.readableBytes()==0,"real entity request codec consumes exact bytes");}finally{bytes.release();}
        return decoded.execute(player(h.getLevel(),source.getOwnerName()));
    }
    private static void permanent(GameTestHelper h,Mob body,CompoundTag state){
        var movement=body.getAttribute(Attributes.MOVEMENT_SPEED);close(h,movement.getBaseValue(),state.getDouble("movementBase"),"original movement base");
        h.assertTrue(movement.getModifier(MS_TECH)!=null&&movement.getModifiers().size()==1&&movement.getModifier(BuffKind.MOVEMENT_SPEED.modifierId)==null,"exact independent permanent modifier only, no transient charge stat");
        close(h,movement.getValue(),state.getDouble("movementBase")+.03,"effective original permanent-aware movement");
    }
    private static void damage(LivingEntity source){source.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(10);if(source.getAttribute(Attributes.ATTACK_DAMAGE)!=null)source.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(10);source.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);source.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get()).setBaseValue(1);}
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
        System.out.println("SHADOW_GOLEM_STRIKE_RESTART_LEDGER time="+h.getLevel().getGameTime()+" banks=123,234,345,456 pending=0 cargo=empty");
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
