package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.fogofwar.*;
import com.solegendary.reignofnether.player.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.*;
import net.minecraftforge.fml.LogicalSide;
import java.lang.reflect.*;
import java.util.*;

/** Real handler/codec/tracking tests. No new API65 production symbol is needed on the API64 baseline. */
@GameTestHolder("api65_world") @PrefixGameTestTemplate(false)
public final class Api65WorldNetworkGameTests {
    private static final String BATCH="api65_world_network";
    private static String owner(GameTestHelper h,String prefix){return prefix+Math.abs(h.absolutePos(BlockPos.ZERO).asLong());}
    private static Object field(String name)throws Exception {var f=FogOfWarServerEvents.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    private static void field(String name,Object value)throws Exception {var f=FogOfWarServerEvents.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
    private static final class FogState implements AutoCloseable {
        private final Object enabled,level,ticks; private final Map<String,Map<Object,Object>> maps=new HashMap<>();
        @SuppressWarnings("unchecked") FogState(ServerLevel current)throws Exception {
            enabled=field("enabled");level=field("serverLevel");ticks=field("ticksUntilUpdate");
            for(String name:List.of("playerLiveChunks","playerEdgeChunks","playerEdgeMasks","pendingResends","isRtsPlayerCache")){
                Map<Object,Object> map=(Map<Object,Object>)field(name);maps.put(name,new HashMap<>(map));map.clear();
            }
            field("serverLevel",current);field("enabled",true);
        }
        @SuppressWarnings("unchecked") public void close()throws Exception {
            field("enabled",enabled);field("serverLevel",level);field("ticksUntilUpdate",ticks);
            for(var entry:maps.entrySet()){Map<Object,Object> map=(Map<Object,Object>)field(entry.getKey());map.clear();map.putAll(entry.getValue());}
        }
    }
    private static void tickFog(ServerLevel level)throws Exception {
        field("ticksUntilUpdate",0);
        FogOfWarServerEvents.onWorldTick(new TickEvent.LevelTickEvent(LogicalSide.SERVER,TickEvent.Phase.END,level,()->true));
    }
    private static void send(ResearchEconomyFixtures.Peer peer,BuildingServerboundPacket packet){
        FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());try{packet.encode(b);var decoded=new BuildingServerboundPacket(b);if(b.readableBytes()!=0)throw new AssertionError("request bytes left");decoded.handle(()->peer.context);}finally{b.release();}peer.channel.runPendingTasks();
    }
    private static PlayerChunksClientboundPacket visionReply(ResearchEconomyFixtures.Peer peer){
        int id=ResearchEconomyFixtures.discriminator(new PlayerChunksClientboundPacket(Map.of(),Map.of()));
        for(Object raw:peer.drain())if(raw instanceof ClientboundCustomPayloadPacket payload){var buf=new FriendlyByteBuf(payload.getData().copy());try{if(buf.readVarInt()==id)return new PlayerChunksClientboundPacket(buf);}finally{buf.release();}}
        throw new AssertionError("actual viewer channel contained no vision-table response");
    }
    @GameTest(templateNamespace="api65_world",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void fogCommandRequiresSameOpFourAsPacket(GameTestHelper h)throws Exception {
        try(var peer=new ResearchEconomyFixtures.Peer(h.getLevel(),owner(h,"WorldFogCmd"))){
            var command=h.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild("rts-fog");
            h.assertTrue(command!=null,"actual registered command exists");
            for(int permission=0;permission<4;permission++)h.assertTrue(!command.canUse(peer.actor.createCommandSourceStack().withPermission(permission)),"permission "+permission+" cannot enter fog command");
            h.assertTrue(command.canUse(peer.actor.createCommandSourceStack().withPermission(4)),"OP4 positive control remains");
        }h.succeed();
    }
    @GameTest(templateNamespace="api65_world",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void malformedRallySchemaCannotReplaceValidTarget(GameTestHelper h)throws Exception {
        String owner=owner(h,"WorldRally");BlockPos pos=h.absolutePos(new BlockPos(3,2,3));
        var p=new ProductionPlacement(Buildings.BARRACKS,h.getLevel(),pos,Rotation.NONE,owner,new ArrayList<>(List.of(new BuildingBlock(pos,Blocks.STONE.defaultBlockState()))),false);p.isBuilt=true;BuildingServerEvents.getBuildings().add(p);
        Pig first=EntityType.PIG.create(h.getLevel()),second=EntityType.PIG.create(h.getLevel());first.setPos(pos.getX()+2,pos.getY(),pos.getZ());second.setPos(pos.getX()+3,pos.getY(),pos.getZ());h.getLevel().addFreshEntity(first);h.getLevel().addFreshEntity(second);
        try(var peer=new ResearchEconomyFixtures.Peer(h.getLevel(),owner)){
            p.setRallyPointEntity(first);
            send(peer,new BuildingServerboundPacket(BuildingAction.SET_RALLY_POINT_ENTITY,"",pos,BlockPos.ZERO,Rotation.NONE,owner,new int[]{second.getId(),first.getId()},false));
            h.assertTrue(p.getRallyPointEntity()==first,"malformed multi-target rally must not mutate valid existing target");
            send(peer,new BuildingServerboundPacket(BuildingAction.SET_RALLY_POINT_ENTITY,"",pos,BlockPos.ZERO,Rotation.NONE,owner,new int[0],false));
            h.assertTrue(p.getRallyPointEntity()==first,"empty rally payload remains a no-op");
            send(peer,new BuildingServerboundPacket(BuildingAction.SET_RALLY_POINT_ENTITY,"",pos,BlockPos.ZERO,Rotation.NONE,owner,new int[]{second.getId()},false));
            h.assertTrue(p.getRallyPointEntity()==second,"exactly one live same-Level target positive control");
        }finally{BuildingServerEvents.getBuildings().remove(p);first.discard();second.discard();}h.succeed();
    }
    @GameTest(templateNamespace="api65_world",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void visionTablesFollowViewerPolicyWithoutEnemyMaps(GameTestHelper h)throws Exception {
        String owner=owner(h,"WorldTables");var rts=RTSPlayer.getNewBot(owner,Factions.VILLAGERS);
        try(var fog=new FogState(h.getLevel());var peer=new ResearchEconomyFixtures.Peer(h.getLevel(),owner)){
            UUID me=peer.actor.getUUID(),ally=UUID.randomUUID(),enemy=UUID.randomUUID();
            Set<ChunkPos> union=Set.of(new ChunkPos(1,1),new ChunkPos(2,2));
            Map<UUID,Set<ChunkPos>> maps=Map.of(me,union,ally,Set.of(new ChunkPos(2,2)),enemy,Set.of(new ChunkPos(999,999)));
            PlayerServerEvents.rtsPlayers.add(rts);FogOfWarServerEvents.invalidateRtsCache();
            for(GameType mode:List.of(GameType.SURVIVAL,GameType.SPECTATOR)){
                peer.actor.setGameMode(mode);peer.drain();PlayerChunksClientboundPacket.send(peer.actor,maps,maps);var response=visionReply(peer);
                h.assertTrue(response.liveChunks.equals(Map.of(me,union))&&response.edgeChunks.equals(Map.of(me,union)),"RTS "+mode+" gets only its own already-allied-union table");
            }
            PlayerServerEvents.rtsPlayers.remove(rts);FogOfWarServerEvents.invalidateRtsCache();
            peer.drain();PlayerChunksClientboundPacket.send(peer.actor,maps,maps);
            h.assertTrue(visionReply(peer).liveChunks.equals(maps),"ordinary non-RTS observer retains existing unrestricted overlay policy");
            PlayerServerEvents.rtsPlayers.add(rts);FogOfWarServerEvents.invalidateRtsCache();field("enabled",false);
            peer.drain();PlayerChunksClientboundPacket.send(peer.actor,maps,maps);
            h.assertTrue(visionReply(peer).liveChunks.equals(maps),"fog-disabled observer retains unrestricted policy");
        }finally{PlayerServerEvents.rtsPlayers.remove(rts);FogOfWarServerEvents.invalidateRtsCache();}h.succeed();
    }
    private static Object tracked(ServerLevel level,Pig pig)throws Exception {
        Object chunkMap=level.getChunkSource().chunkMap;
        for(Field f:ChunkMap.class.getDeclaredFields())if(it.unimi.dsi.fastutil.ints.Int2ObjectMap.class.isAssignableFrom(f.getType())){
            f.setAccessible(true);var map=(it.unimi.dsi.fastutil.ints.Int2ObjectMap<?>)f.get(chunkMap);Object value=map.get(pig.getId());if(value!=null)return value;
        }throw new AssertionError("real live entity not in vanilla ChunkMap tracker");
    }
    private static void update(Object tracked,ServerPlayer player)throws Exception {var method=tracked.getClass().getDeclaredMethod("updatePlayer",ServerPlayer.class);method.setAccessible(true);method.invoke(tracked,player);}
    private static Set<?> seenBy(Object tracked)throws Exception {for(Field f:tracked.getClass().getDeclaredFields())if(Set.class.isAssignableFrom(f.getType())){f.setAccessible(true);return (Set<?>)f.get(tracked);}throw new AssertionError("vanilla seenBy missing");}
    @GameTest(templateNamespace="api65_world",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void stationaryAllianceSightLossUnpairsRealTracker(GameTestHelper h)throws Exception {
        String owner=owner(h,"WorldWatch"),ally=owner(h,"WorldSight");var rts=RTSPlayer.getNewBot(owner,Factions.VILLAGERS);
        BlockPos pos=h.absolutePos(new BlockPos(6,2,6));var sight=new BuildingPlacement(Buildings.BARRACKS,h.getLevel(),pos,Rotation.NONE,ally,new ArrayList<>(List.of(new BuildingBlock(pos,Blocks.STONE.defaultBlockState()))),false);sight.isBuilt=true;
        Pig pig=EntityType.PIG.create(h.getLevel());pig.setNoAi(true);pig.setPos(pos.getX()+1,pos.getY(),pos.getZ());
        try(var fog=new FogState(h.getLevel());var peer=new ResearchEconomyFixtures.Peer(h.getLevel(),owner)){
            peer.actor.setPos(pos.getX(),pos.getY(),pos.getZ());PlayerServerEvents.rtsPlayers.add(rts);PlayerServerEvents.orthoviewPlayers.add(peer.actor);BuildingServerEvents.getBuildings().add(sight);AlliancesServerEvents.addAlliance(owner,ally);h.getLevel().addFreshEntity(pig);
            Object tracker=tracked(h.getLevel(),pig);tickFog(h.getLevel());
            h.assertTrue(FogOfWarServerEvents.isBlockVisibleFor(peer.actor,pig.getBlockX(),pig.getBlockZ()),"actual ally mask supplies sight positive control");update(tracker,peer.actor);
            h.assertTrue(seenBy(tracker).contains(peer.actor.connection),"vanilla pairing positive control");
            AlliancesServerEvents.removeAlliance(owner,ally);tickFog(h.getLevel());
            h.assertTrue(!FogOfWarServerEvents.isBlockVisibleFor(peer.actor,pig.getBlockX(),pig.getBlockZ()),"no own/FPS bubble remains after alliance removal");
            h.assertTrue(!seenBy(tracker).contains(peer.actor.connection),"stationary viewer/entity must unpair when only sight mask changes");
            AlliancesServerEvents.addAlliance(owner,ally);tickFog(h.getLevel());
            h.assertTrue(seenBy(tracker).contains(peer.actor.connection),"restored allied sight repairs pairing without moving either entity");
            field("enabled",false);update(tracker,peer.actor);
            h.assertTrue(seenBy(tracker).contains(peer.actor.connection),"fog-disabled vanilla tracker positive control");
            PlayerServerEvents.orthoviewPlayers.remove(peer.actor);
        }finally{BuildingServerEvents.getBuildings().remove(sight);PlayerServerEvents.rtsPlayers.remove(rts);PlayerServerEvents.orthoviewPlayers.removeIf(p->p.getName().getString().equals(owner));AlliancesServerEvents.removeAlliance(owner,ally);pig.discard();FogOfWarServerEvents.invalidateRtsCache();}h.succeed();
    }
}
