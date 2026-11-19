// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.registrars.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Isolated real-zone packet test. Only test-player membership uses reflection.
 * Fixture correction after particle1: native SIGHT_RANGE is clamped to [4,64], not zero.
 * Scouts at z12 use radius4: z8 same-x column is on the inclusive boundary; adjacent-x is outside.
 * Visibility comes from naturally rasterized minimum-radius owned scouts, never injected fog masks.
 */
@GameTestHolder("dreamseekers_ash_ember_particles_skill")
@PrefixGameTestTemplate(false)
public final class AshEmberParticleGameTests {
    private static final String BATCH="dreamseekers_ash_ember_particles_skill";
    private static Fixture fixture;
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel ignored){if(fixture!=null){fixture.close();fixture=null;}}

    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=700)
    public static void realPulsePacketsUseFloorColumnVisibleNonownerDistanceAndDimension(GameTestHelper h){
        fixture=new Fixture(h);var f=fixture;f.install();
        h.startSequence().thenWaitUntil(()->{
            for(Mob body:f.bodies)h.assertTrue(body.tickCount>3&&body.level() instanceof ServerLevel l&&l.getEntity(body.getUUID())==body&&l.getEntity(body.getId())==body,"genuine published naturally ticking sources and minimum-radius scouts in both dimensions");
        }).thenExecute(()->{
            FogOfWarServerEvents.setEnabled(true);h.assertTrue(FogOfWarServerEvents.isEnabled(),"actual public fog enabled before casts to establish natural boundary fixture");
        }).thenWaitUntil(()->{
            h.assertTrue(FogOfWarServerEvents.isFogActiveFor(f.hidden)&&f.visible(f.hidden,0)&&!f.visible(f.hidden,-1),"PRECAST actual radius4 boundary sees truncated0 and hides true -1");
            h.assertTrue(FogOfWarServerEvents.isFogActiveFor(f.visible)&&f.visible(f.visible,-1)&&!f.visible(f.visible,0),"PRECAST actual reverse boundary sees true -1 and hides truncated0");
            h.assertTrue(FogOfWarServerEvents.isFogActiveFor(f.netherVisible)&&f.visible(f.netherVisible,-1),"PRECAST real Nether scout supplies positive dimensionless native cache");
            h.assertTrue(f.visible(f.far,-1)&&f.far.blockPosition().distToCenterSqr(new Vec3(-.5,f.y+1,8.5))>512d*512d,"PRECAST far player truly sees intended floor but remains outside native particle reach");
            h.assertTrue(AshEmberLanding.activeFlightCount(f.overSource)==0&&AshEmberLanding.activeFlightCount(f.netherSource)==0&&f.zone(f.overSource)==null&&f.zone(f.netherSource)==null,"natural visibility pairs established before any projectile or zone exists");
        }).thenExecute(()->{
            FogOfWarServerEvents.setEnabled(false);
            h.assertTrue(!FogOfWarServerEvents.isEnabled(),"fog disabled only for genuine Nether ground publication after pre-cast natural visibility proof");
            f.cast(f.overSource,f.overOwner);f.cast(f.netherSource,f.netherOwner);
        }).thenWaitUntil(()->{
            f.overZone=f.zone(f.overSource);f.netherZone=f.zone(f.netherSource);
            h.assertTrue(f.overZone!=null&&f.netherZone!=null,"both independent real projectiles naturally collide into zones");
        }).thenExecute(()->{
            for(var z:List.of(f.overZone,f.netherZone))h.assertTrue(z.center().x>-1&&z.center().x<0&&BlockPos.containing(z.center()).getX()==-1&&(int)z.center().x==0,"real negative fractional collision has floor -1 and truncation0");
            h.assertTrue(f.over.getGameTime()<f.overZone.landedAt()+20&&f.nether.getGameTime()<f.netherZone.landedAt()+20,"both real zones still before first fixed20 pulse");
            FogOfWarServerEvents.setEnabled(true);h.assertTrue(FogOfWarServerEvents.isEnabled(),"actual public fog enabled on bounded RTS map");
        }).thenWaitUntil(()->{
            h.assertTrue(f.over.getGameTime()<f.overZone.landedAt()+20&&f.nether.getGameTime()<f.netherZone.landedAt()+20,"native raster must be ready before first unchanged pulse; no late acceptance");
            h.assertTrue(FogOfWarServerEvents.isFogActiveFor(f.hidden)&&f.visible(f.hidden,0)&&!f.visible(f.hidden,-1),"actual own radius4 boundary scout exposes truncation0 but hides true -1");
            h.assertTrue(FogOfWarServerEvents.isFogActiveFor(f.visible)&&f.visible(f.visible,-1)&&!f.visible(f.visible,0),"actual nonowner scout exposes true -1 but hides truncation0");
            h.assertTrue(FogOfWarServerEvents.isFogActiveFor(f.netherVisible)&&f.visible(f.netherVisible,-1),"actual dimensionless native cache is positive for Nether recipient; emitter must still fail closed");
            h.assertTrue(f.visible(f.far,-1)&&f.far.blockPosition().distToCenterSqr(f.overZone.center())>512d*512d,"real visible but farther than native extended particle radius");
        }).thenExecute(()->{f.record=true;})
          .thenWaitUntil(()->h.assertTrue(f.over.getGameTime()>=f.overZone.landedAt()+21&&f.nether.getGameTime()>=f.netherZone.landedAt()+21,"first actual END pulse completed in both dimensions"))
          .thenExecute(()->{
              h.assertTrue(f.failure==null,"packet codec recorder has no failure: "+f.failure);
              f.expect(f.hidden,f.overZone,0,"hidden actual floor cannot receive via visible truncated neighbour");
              f.expect(f.visible,f.overZone,1,"visible nonowner gets actual packet even though broken legacy truncated gate would reject it");
              f.expect(f.overOwner,f.overZone,1,"visible source owner retains normal particles");
              f.expect(f.far,f.overZone,0,"no extension beyond original512 reach");
              f.expect(f.netherVisible,f.netherZone,0,"fog-active non-overworld fails closed despite positive dimensionless native cache");
              f.expect(f.netherOwner,f.netherZone,0,"non-overworld restriction applies to owner too");
              h.assertTrue(f.overSource.rts().skills().cooldownTicks(UnitSkill.ASH_EMBER_LANDING)>0&&f.netherSource.rts().skills().cooldownTicks(UnitSkill.ASH_EMBER_LANDING)>0,"actual original skill clocks remain paid");
              System.out.println("DREAMSEEKERS_ASH_EMBER_LANDING_PASS REAL_PARTICLE_PACKETS_FLOOR_COLUMN_VISIBLE_NONOWNER_DISTANCE_DIMENSION");
          }).thenSucceed();
    }
    private record ParticleRead(long tick,double x,double y,double z,int count,float dx,float dy,float dz,boolean force){}
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final ServerLevel over,nether;final int y;
        final boolean oldFog,oldForce;final double oldSize,oldX,oldZ;
        final List<Mob> bodies=new ArrayList<>();final List<FakePlayer> players=new ArrayList<>();
        final Map<ServerLevel,List<ChunkPos>> forced=new IdentityHashMap<>();final Map<FakePlayer,List<ParticleRead>> packets=new IdentityHashMap<>();
        FakePlayer overOwner,netherOwner,hidden,visible,far,netherVisible;AshBoneWingUnit overSource,netherSource;
        AshEmberLanding.ZoneView overZone,netherZone;boolean record,closed;RuntimeException failure;
        Fixture(GameTestHelper h){this.h=h;over=h.getLevel();nether=over.getServer().getLevel(Level.NETHER);h.assertTrue(nether!=null,"real second server dimension exists");y=Math.max(65,h.absolutePos(BlockPos.ZERO).getY());oldFog=FogOfWarServerEvents.isEnabled();oldForce=over.getGameRules().getBoolean(GameRuleRegistrar.FORCE_FOG);oldSize=over.getWorldBorder().getSize();oldX=over.getWorldBorder().getCenterX();oldZ=over.getWorldBorder().getCenterZ();}
        void install(){
            over.getGameRules().getRule(GameRuleRegistrar.FORCE_FOG).set(false,over.getServer());over.getWorldBorder().setCenter(0,8);over.getWorldBorder().setSize(1280);FogOfWarServerEvents.setEnabled(false);
            room(over);room(nether);
            overOwner=player(over,"EmberPOwner",4.5);netherOwner=player(nether,"EmberPNOwner",4.5);
            hidden=player(over,"EmberPHidden",4.5);visible=player(over,"EmberPVisible",4.5);far=player(over,"EmberPFar",600.5);netherVisible=player(nether,"EmberPNVisible",4.5);
            overSource=source(over,overOwner);netherSource=source(nether,netherOwner);
            scout(over,hidden,0);scout(over,visible,-1);scout(over,far,-1);scout(nether,netherVisible,-1);
            FogOfWarServerEvents.invalidateRtsCache();
        }
        void room(ServerLevel level){
            var added=new ArrayList<ChunkPos>();forced.put(level,added);
            for(int x=-1;x<=0;x++)for(int z=0;z<=1;z++){var c=new ChunkPos(x,z);if(!level.getForcedChunks().contains(c.toLong())){level.setChunkForced(x,z,true);added.add(c);}}
            // Seal the known scene before clearing it so native Nether gravel cannot enter the flight.
            for(int x=-12;x<=5;x++)for(int z=2;z<=14;z++)level.setBlock(new BlockPos(x,y+9,z),Blocks.STONE.defaultBlockState(),2);
            for(int x=-12;x<=5;x++)for(int z=2;z<=14;z++){level.setBlock(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState(),2);for(int dy=1;dy<9;dy++)level.setBlock(new BlockPos(x,y+dy,z),Blocks.AIR.defaultBlockState(),2);}
        }
        FakePlayer player(ServerLevel level,String name,double x){
            var p=new FakePlayer(level,new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)),name));p.connection=new Recorder(p,this);p.setNoGravity(true);p.moveTo(x,y+3,12.5,0,0);players.add(p);packets.put(p,new ArrayList<>());level.addNewPlayer(p);membership(p,true);PlayerServerEvents.players.add(p);PlayerServerEvents.orthoviewPlayers.add(p);PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(name,DreamseekersMod.DREAMSEEKERS,p.getId(),0,true,-1L,-1L));ResourcesServerEvents.resourcesList.add(new Resources(name,123,234,345,456));return p;
        }
        AshBoneWingUnit source(ServerLevel level,FakePlayer owner){var source=DreamseekersMod.ASH_BONE_WING_UNIT.get().create(level);owned(level,source,owner,-7.5,8.5);source.getAttribute(AttributeRegistrar.SIGHT_RANGE.get()).setBaseValue(12);return source;}
        void scout(ServerLevel level,FakePlayer owner,int x){var scout=DreamseekersMod.PINK_CHICKEN_SCOUT.get().create(level);owned(level,scout,owner,x+.5,12.5);if(owner==far)scout.moveTo(x+.5,y+4,12.5,0,0);scout.getAttribute(AttributeRegistrar.SIGHT_RANGE.get()).setBaseValue(4);h.assertTrue(((Unit)scout).getSightRange()==4,"actual attribute produces native minimum4 sight; exact delta-z4 column visible, adjacent sqrt17 column hidden");}
        void owned(ServerLevel level,Mob body,FakePlayer owner,double x,double z){((Unit)body).setOwnerName(owner.getGameProfile().getName());body.setNoAi(true);body.setPersistenceRequired();body.moveTo(x,y+1,z,0,0);bodies.add(body);h.assertTrue(level.addFreshEntity(body),"real source/scout publication");}
        void cast(AshBoneWingUnit source,FakePlayer owner){var r=new SkillGroundRequest(UnitSkill.ASH_EMBER_LANDING,source.getId(),source.getUUID(),source.rts().skills().revision(),new BlockPos(-1,y+1,8));h.assertTrue(new SkillNetwork.GroundCast(r).execute(owner)==SkillResult.SUCCESS,"actual authenticated ground publication in "+source.level().dimension().location());h.assertTrue(AshEmberLanding.activeFlightCount(source)==1&&source.rts().skills().cooldownTicks(UnitSkill.ASH_EMBER_LANDING)==440,"real paid independent flight");}
        AshEmberLanding.ZoneView zone(AshBoneWingUnit source){for(var z:AshEmberLanding.zones((ServerLevel)source.level()))if(z.sourceUuid().equals(source.getUUID()))return z;return null;}
        boolean visible(ServerPlayer p,int x){return FogOfWarServerEvents.isBlockVisibleFor(p,x,8);}
        void receive(FakePlayer p,Packet<?> packet){if(!record||!(packet instanceof ClientboundLevelParticlesPacket raw)||raw.getParticle().getType()!=ParticleTypes.ASH)return;var buf=new FriendlyByteBuf(Unpooled.buffer());try{raw.write(buf);var decoded=new ClientboundLevelParticlesPacket(buf);h.assertTrue(buf.readableBytes()==0&&decoded.getParticle().getType()==ParticleTypes.ASH,"real emitted ASH packet survives actual vanilla wire codec");var read=new ParticleRead(p.serverLevel().getGameTime(),decoded.getX(),decoded.getY(),decoded.getZ(),decoded.getCount(),decoded.getXDist(),decoded.getYDist(),decoded.getZDist(),decoded.isOverrideLimiter());packets.get(p).add(read);System.out.println("ASH_EMBER_PARTICLE_WIRE recipient="+p.getGameProfile().getName()+" dimension="+p.level().dimension().location()+" "+read);}catch(RuntimeException error){failure=error;}finally{buf.release();}}
        void expect(FakePlayer p,AshEmberLanding.ZoneView z,int count,String why){var matching=new ArrayList<ParticleRead>();for(var r:packets.get(p))if(Math.abs(r.x-z.center().x)<.00001&&Math.abs(r.z-z.center().z)<.00001)matching.add(r);h.assertTrue(matching.size()==count,why+" count="+matching.size()+" reads="+matching);for(var r:matching)h.assertTrue(r.tick==z.landedAt()+20&&r.count==8&&!r.force&&Math.abs(r.y-z.center().y-.1)<.00001&&Math.abs(r.dx-1.5)<.00001&&Math.abs(r.dy-.1)<.00001&&Math.abs(r.dz-1.5)<.00001,"exact original pulse tick and unchanged wire payload");}
        @SuppressWarnings("unchecked") void membership(FakePlayer p,boolean present){try{var list=over.getServer().getPlayerList();var a=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");var b=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");a.setAccessible(true);b.setAccessible(true);var ps=(List<ServerPlayer>)a.get(list);var ids=(Map<UUID,ServerPlayer>)b.get(list);if(present){if(!ps.contains(p))ps.add(p);ids.put(p.getUUID(),p);}else{ps.remove(p);ids.remove(p.getUUID(),p);}}catch(ReflectiveOperationException e){throw new RuntimeException("actual test-player membership",e);}}
        public void close(){if(closed)return;closed=true;record=false;for(Mob body:bodies)if(!body.isRemoved())body.discard();for(FakePlayer p:players){membership(p,false);PlayerServerEvents.players.remove(p);PlayerServerEvents.orthoviewPlayers.remove(p);PlayerServerEvents.rtsPlayers.removeIf(r->r.name.equals(p.getGameProfile().getName()));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(p.getGameProfile().getName()));p.serverLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);FogOfWarServerEvents.onPlayerDisconnect(p.getUUID());}FogOfWarServerEvents.invalidateRtsCache();over.getGameRules().getRule(GameRuleRegistrar.FORCE_FOG).set(false,over.getServer());FogOfWarServerEvents.setEnabled(oldFog);over.getWorldBorder().setCenter(oldX,oldZ);over.getWorldBorder().setSize(oldSize);over.getGameRules().getRule(GameRuleRegistrar.FORCE_FOG).set(oldForce,over.getServer());for(var entry:forced.entrySet())for(var chunk:entry.getValue())entry.getKey().setChunkForced(chunk.x,chunk.z,false);}
    }
    private static final class Recorder extends ServerGamePacketListenerImpl {
        final FakePlayer player;final Fixture fixture;
        Recorder(FakePlayer p,Fixture f){super(p.serverLevel().getServer(),new QuietConnection(),p);player=p;fixture=f;}
        @Override public void tick(){}
        @Override public void send(Packet<?> p){fixture.receive(player,p);}
        @Override public void send(Packet<?> p,net.minecraft.network.PacketSendListener listener){fixture.receive(player,p);}
    }
    private static final class QuietConnection extends net.minecraft.network.Connection {
        QuietConnection(){super(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);}
        @Override public void tick(){}
        @Override public void send(Packet<?> p){}
        @Override public void send(Packet<?> p,net.minecraft.network.PacketSendListener listener){}
    }
}
