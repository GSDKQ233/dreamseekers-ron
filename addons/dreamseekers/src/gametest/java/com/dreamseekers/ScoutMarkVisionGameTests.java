// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.scout.ScoutBalance;
import com.dreamseekers.scout.ScoutMarkNetwork;
import com.dreamseekers.scout.ScoutMarkState;
import com.dreamseekers.unit.BeetleUnit;
import com.dreamseekers.unit.PinkChickenScout;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Real RoN visibility raster + real ticking, with local recording fake players, no socket/client. */
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ScoutMarkVisionGameTests {
    private static final String BATCH = "dreamseekers_scout_mark_real_vision";
    private static Fixture fixture;
    @AfterBatch(batch = BATCH) public static void cleanup(ServerLevel level) {
        if (fixture != null) { fixture.close(); fixture = null; }
    }

    @GameTest(template = "unit_isolation_arena", batch = BATCH, timeoutTicks = 200)
    public static void realSightOwnerOnlyHeartbeatExpiryLossAndReconnect(GameTestHelper h) {
        fixture = new Fixture(h); Fixture f = fixture; f.install();
        PinkChickenScout scout = f.scout(4, 5); BeetleUnit enemy = f.enemy(10, 5);
        PinkChickenScout blocked = f.scout(4, 12); BeetleUnit blockedEnemy = f.enemy(10, 12);
        f.recomputeVision();
        h.assertTrue(UnitServerEvents.getAllUnits().contains(scout), "actual scout participates in ordinary RoN viewer collection");
        h.assertTrue(FogOfWarServerEvents.isFogActiveFor(f.owner), "owner is a real registered RTS player with fog enabled");
        h.assertTrue(FogOfWarServerEvents.isBlockVisibleFor(f.owner, enemy.getBlockX(), enemy.getBlockZ()), "real scout sight raster exposes nearby enemy column");
        h.assertTrue(!FogOfWarServerEvents.isBlockVisibleFor(f.owner, scout.getBlockX()+80, scout.getBlockZ()), "raster does not expose distant map columns");
        h.assertTrue(scout.scoutMark().tryCast(f.owner, enemy, 0) == ScoutMarkState.Result.SUCCESS, "cast succeeds through actual positive fog masks");
        h.assertTrue(blocked.scoutMark().tryCast(f.owner, blockedEnemy, 0) == ScoutMarkState.Result.SUCCESS, "second visible mark succeeds");
        h.assertTrue(f.ownerWire.marks.stream().anyMatch(p -> enemy.getUUID().equals(p.targetUuid())
                        && p.remainingTicks() == ScoutBalance.MARK_DURATION_TICKS)
                        && f.ownerWire.marks.stream().anyMatch(p -> blockedEnemy.getUUID().equals(p.targetUuid())),
                "actual encoded channel emitted both positive owner target packets");
        int immediatelySent = f.ownerWire.marks.size();
        for(int i=0;i<20;i++) new ScoutMarkNetwork.Cast(scout.getId(),scout.getUUID(),enemy.getId(),enemy.getUUID(),0).execute(f.owner);
        h.assertTrue(f.ownerWire.marks.size() == immediatelySent, "replay spam emits no duplicate state packets");
        h.assertTrue(f.otherWire.marks.isEmpty(), "no observer receives mark target information");
        // Read-only tracing preserves the original world-time assertions and entity motion.
        long castGameTime=f.level.getGameTime();
        String[] lastState={""};
        System.out.println("SCOUT_REAL_VISION_DIAGNOSTIC cast "+f.describe(scout,enemy,castGameTime));
        for(int tick=1;tick<=145;tick++) {
            final int sample=tick;
            h.runAtTickTime(sample,()->{
                String state=scout.scoutMark().hasActiveMark()+"/"+scout.scoutMark().eligibility(f.owner,enemy)+"/"+f.ownerWire.marks.size();
                if(!state.equals(lastState[0])||sample==25||sample==99||sample==100||sample==120||sample==125)
                    System.out.println("SCOUT_REAL_VISION_DIAGNOSTIC sample="+sample+" "+f.describe(scout,enemy,castGameTime));
                lastState[0]=state;
            });
        }
        h.runAtTickTime(25, () -> {
            h.assertTrue(scout.scoutMark().hasActiveMark() && scout.scoutMark().validateActiveFor(f.owner), "live online-owner mark survives genuine unit ticks");
            h.assertTrue(f.ownerWire.marks.stream().anyMatch(p -> enemy.getUUID().equals(p.targetUuid()) && p.remainingTicks() < ScoutBalance.MARK_DURATION_TICKS), "owner receives refreshed decreasing-duration state");
            for(int y=1;y<5;y++) f.set(7,y,12,Blocks.STONE);
        });
        h.runAtTickTime(30, () -> {
            h.assertTrue(!blocked.scoutMark().hasActiveMark(), "LOS loss clears server mark on next tick even though column remains in fog sight");
            h.assertTrue(f.last(blocked).targetUuid() == null, "owner receives explicit removal instead of hidden target continuation");
            h.assertTrue(f.otherWire.marks.isEmpty(), "ordinary observer remains unaware of all markers");
        });
        h.runAtTickTime(100, () -> h.assertTrue(scout.scoutMark().hasActiveMark() && scout.scoutMark().remainingTicks() <= 21,
                "unchanged valid observation survives near its real duration, not spuriously lost offline"));
        h.runAtTickTime(125, () -> {
            h.assertTrue(!scout.scoutMark().hasActiveMark() && f.last(scout).targetUuid() == null, "normal server ticking expires mark and explicitly removes owner indicator");
            h.assertTrue(scout.scoutMark().cooldownTicks() > 0, "expiry does not refund cooldown");
            PinkChickenScout logout = f.scout(4,18); BeetleUnit logoutEnemy = f.enemy(10,18); f.recomputeVision();
            h.assertTrue(logout.scoutMark().tryCast(f.owner,logoutEnemy,0)==ScoutMarkState.Result.SUCCESS,"fresh mark for reconnect fixture");
            f.logoutScout=logout;
            f.membership(f.owner,false);
        });
        h.runAtTickTime(130, () -> {
            h.assertTrue(!f.logoutScout.scoutMark().hasActiveMark(), "owner logout removes live observation");
            f.membership(f.owner,true); f.recomputeVision();
        });
        h.runAtTickTime(145, () -> {
            var sync=f.last(f.logoutScout);
            h.assertTrue(sync.targetUuid()==null && sync.cooldownTicks()>0 && sync.revision()==1,
                    "reconnect owner resync preserves cooldown/revision without reviving old mark");
            h.assertTrue(f.otherWire.marks.isEmpty(), "all state packets stayed owner-only including resync and removal");
            System.out.println("DREAMSEEKERS_SCOUT_MARK_REAL_VISION_OWNER_PRIVATE_TICKS_RECONNECT_PASS packets="+f.ownerWire.marks.size());
            f.close(); fixture=null; h.succeed();
        });
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h; final ServerLevel level; final FakePlayer owner; final FakePlayer other;
        final RecordingHandler ownerWire; final RecordingHandler otherWire;
        final List<Entity> entities = new ArrayList<>();
        final Field enabled; final boolean oldEnabled; final Field serverLevel; final Object oldLevel;
        final Method recompute; boolean closed; PinkChickenScout logoutScout;
        Fixture(GameTestHelper h) {
            this.h=h;this.level=h.getLevel();
            try {
                enabled=FogOfWarServerEvents.class.getDeclaredField("enabled"); enabled.setAccessible(true);oldEnabled=enabled.getBoolean(null);
                serverLevel=FogOfWarServerEvents.class.getDeclaredField("serverLevel");serverLevel.setAccessible(true);oldLevel=serverLevel.get(null);
                recompute=FogOfWarServerEvents.class.getDeclaredMethod("updatePlayerBrightChunks");recompute.setAccessible(true);
                owner=actor("MarkVisionOwner");other=actor("MarkVisionOther");
                ownerWire=new RecordingHandler(owner);otherWire=new RecordingHandler(other);owner.connection=ownerWire;other.connection=otherWire;
            }catch(ReflectiveOperationException e){throw new RuntimeException("real vision test fixture",e);}
        }
        // Assigned to the outer cleanup handle before the first mutation: failed setup also rolls back.
        void install() {
            try {
                for(FakePlayer p:List.of(owner,other)) {
                    p.moveTo(h.absolutePos(new BlockPos(2,2,2)),0,0);level.addNewPlayer(p);membership(p,true);
                    PlayerServerEvents.players.add(p);PlayerServerEvents.orthoviewPlayers.add(p);
                }
                PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(owner.getGameProfile().getName(),DreamseekersMod.DREAMSEEKERS,owner.getId(),0,true,-1L,-1L));
                FogOfWarServerEvents.invalidateRtsCache();enabled.setBoolean(null,true);serverLevel.set(null,level);
                // One-time fixture isolation from the normal world's natural falling gravel.
                // Keep the mark/LOS/timing behavior unchanged; never clear obstacles during observation.
                BlockPos base=h.absolutePos(BlockPos.ZERO);
                for(int x=0;x<24;x++)for(int z=0;z<24;z++)set(x,10,z,Blocks.STONE);
                level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class,
                        new net.minecraft.world.phys.AABB(base.getX(),base.getY()+1,base.getZ(),
                                base.getX()+24,level.getMaxBuildHeight(),base.getZ()+24)).forEach(Entity::discard);
                for(int x=0;x<24;x++)for(int z=0;z<24;z++) {set(x,0,z,Blocks.STONE);for(int y=1;y<10;y++)set(x,y,z,Blocks.AIR);}
            }catch(ReflectiveOperationException e){throw new RuntimeException("install real vision fixture",e);}
        }
        FakePlayer actor(String name){return new FakePlayer(level,new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)),name));}
        void set(int x,int y,int z,net.minecraft.world.level.block.Block block){level.setBlockAndUpdate(h.absolutePos(new BlockPos(x,y,z)),block.defaultBlockState());}
        PinkChickenScout scout(int x,int z){var s=DreamseekersMod.PINK_CHICKEN_SCOUT.get().create(level);s.setOwnerName(owner.getGameProfile().getName());place(s,x,z);return s;}
        BeetleUnit enemy(int x,int z){var e=DreamseekersMod.BEETLE.get().create(level);e.setOwnerName("MarkVisionEnemy");e.setNoAi(true);place(e,x,z);return e;}
        void place(Mob e,int x,int z){BlockPos p=h.absolutePos(new BlockPos(x,1,z));e.moveTo(p.getX()+.5,p.getY()+.4,p.getZ()+.5,0,0);e.setPersistenceRequired();entities.add(e);h.assertTrue(level.addFreshEntity(e),"vision fixture entity spawned");}
        void recomputeVision(){try{recompute.invoke(null);}catch(ReflectiveOperationException e){throw new RuntimeException("actual visibility raster",e);}}
        String describe(PinkChickenScout s,BeetleUnit e,long castTime){
            var tag=new net.minecraft.nbt.CompoundTag();s.scoutMark().save(tag);
            var ray=level.clip(new net.minecraft.world.level.ClipContext(s.getEyePosition(),e.getEyePosition(),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,s));
            var online=level.getServer().getPlayerList().getPlayerByName(s.getOwnerName());
            return "worldTick="+h.getTick()+" gameTime="+level.getGameTime()+" castTime="+castTime
                    +" active="+s.scoutMark().hasActiveMark()+" remaining="+s.scoutMark().remainingTicks()
                    +" durationPredicate="+(s.scoutMark().remainingTicks()<=21)+" eligibility="+s.scoutMark().eligibility(owner,e)
                    +" validate="+s.scoutMark().validateActiveFor(owner)+" online="+(online==owner)
                    +" owner="+s.getOwnerName()+" ownerUUID="+(online==null?null:online.getUUID())
                    +" scout="+s.position()+"/tick"+s.tickCount+"/hp"+s.getHealth()+"/alive"+s.isAlive()+"/removed"+s.isRemoved()
                    +" target="+e.position()+"/tick"+e.tickCount+"/hp"+e.getHealth()+"/alive"+e.isAlive()+"/removed"+e.isRemoved()
                    +" targetLookup="+(level.getEntity(e.getUUID())==e)+" targetOwner="+e.getOwnerName()
                    +" los="+s.hasLineOfSight(e)+" fog="+FogOfWarServerEvents.isBlockVisibleFor(owner,e.getBlockX(),e.getBlockZ())
                    +" ray="+ray.getType()+"@"+ray.getBlockPos()+":"+level.getBlockState(ray.getBlockPos())
                    +" packets="+ownerWire.marks.size()+" last="+last(s)+" saved="+tag;
        }
        ScoutMarkNetwork.Sync last(PinkChickenScout scout){return ownerWire.marks.stream().filter(p->p.scoutUuid().equals(scout.getUUID())).reduce((a,b)->b).orElseThrow();}
        @SuppressWarnings("unchecked") void membership(ServerPlayer actor,boolean present){
            try{
                var list=level.getServer().getPlayerList();Field p=PlayerList.class.getDeclaredField("players"),u=PlayerList.class.getDeclaredField("playersByUUID");p.setAccessible(true);u.setAccessible(true);
                List<ServerPlayer> players=(List<ServerPlayer>)p.get(list);Map<UUID,ServerPlayer> ids=(Map<UUID,ServerPlayer>)u.get(list);
                if(present){if(!players.contains(actor))players.add(actor);ids.put(actor.getUUID(),actor);}else{players.remove(actor);ids.remove(actor.getUUID(),actor);}
            }catch(ReflectiveOperationException e){throw new RuntimeException("local fake membership",e);}
        }
        @Override public void close(){
            if(closed)return;closed=true;
            try {
            // Remove test RTS registration before discarding its last unit: cleanup is not an in-match defeat.
            PlayerServerEvents.rtsPlayers.removeIf(r->r.name.equals(owner.getGameProfile().getName()));
            for(Entity e:entities)if(!e.isRemoved())e.discard();
            for(FakePlayer p:List.of(owner,other)){
                membership(p,false);PlayerServerEvents.players.remove(p);PlayerServerEvents.orthoviewPlayers.remove(p);
                level.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);
                PlayerServerEvents.rtsPlayers.removeIf(r->r.name.equals(p.getGameProfile().getName()));
                for(String field:List.of("playerLiveChunks","playerEdgeChunks","playerEdgeMasks","pendingResends","isRtsPlayerCache"))try{
                    Field map=FogOfWarServerEvents.class.getDeclaredField(field);map.setAccessible(true);((Map<?,?>)map.get(null)).remove(p.getUUID());
                }catch(ReflectiveOperationException e){throw new RuntimeException(e);}
            }
            } finally {
                try{enabled.setBoolean(null,oldEnabled);serverLevel.set(null,oldLevel);}catch(IllegalAccessException e){throw new RuntimeException(e);}
                FogOfWarServerEvents.invalidateRtsCache();
            }
        }
    }
    private static final class RecordingHandler extends ServerGamePacketListenerImpl {
        final RecordingConnection wire;
        final List<ScoutMarkNetwork.Sync> marks;
        RecordingHandler(ServerPlayer actor){this(actor,new RecordingConnection());}
        private RecordingHandler(ServerPlayer actor,RecordingConnection wire){
            super(actor.serverLevel().getServer(),wire,actor);this.wire=wire;this.marks=wire.marks;
        }
        @Override public void tick() { }
        @Override public void send(Packet<?> packet){wire.send(packet);}
        @Override public void send(Packet<?> packet,@Nullable PacketSendListener listener){wire.send(packet,listener);}
    }
    /** Forge PacketDistributor.PLAYER sends directly through handler.connection, not handler.send. */
    private static final class RecordingConnection extends Connection {
        final List<ScoutMarkNetwork.Sync> marks=new ArrayList<>();
        RecordingConnection(){super(PacketFlow.SERVERBOUND);}
        @Override public void tick(){ }
        @Override public void send(Packet<?> packet){record(packet);}
        @Override public void send(Packet<?> packet,@Nullable PacketSendListener listener){record(packet);}
        private void record(Packet<?> packet){
            if(packet instanceof ClientboundCustomPayloadPacket payload && payload.getIdentifier().equals(DreamseekersMod.id("scout_mark"))){
                FriendlyByteBuf bytes=new FriendlyByteBuf(payload.getData().copy());
                try{int discriminator=bytes.readVarInt();if(discriminator==1)marks.add(ScoutMarkNetwork.Sync.decode(bytes));}
                finally{bytes.release();}
            }
        }
    }
}
