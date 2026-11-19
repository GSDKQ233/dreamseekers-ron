package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.fogofwar.PlayerChunksClientboundPacket;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.*;

/** Candidate interaction regression: foreign-world fog bypass must not authorize other players' vision tables. */
@GameTestHolder("api65_world_table") @PrefixGameTestTemplate(false)
public final class Api65FogTableDimensionGameTests {
    private static final String BATCH="api65_world_foreign_table";
    private static Object field(String name)throws Exception {var f=FogOfWarServerEvents.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    private static void field(String name,Object value)throws Exception {var f=FogOfWarServerEvents.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
    private static final class FogState implements AutoCloseable {
        private final Object enabled,level,ticks;private final Map<String,Map<Object,Object>> maps=new HashMap<>();
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
    private static PlayerChunksClientboundPacket response(ResearchEconomyFixtures.Peer peer){
        int id=ResearchEconomyFixtures.discriminator(new PlayerChunksClientboundPacket(Map.of(),Map.of()));
        for(Object raw:peer.drain())if(raw instanceof ClientboundCustomPayloadPacket payload){var buf=new FriendlyByteBuf(payload.getData().copy());try{if(buf.readVarInt()==id)return new PlayerChunksClientboundPacket(buf);}finally{buf.release();}}
        throw new AssertionError("actual recipient connection got no table packet");
    }
    private static PlayerChunksClientboundPacket send(ResearchEconomyFixtures.Peer peer,Map<UUID,Set<ChunkPos>> maps){
        peer.drain();PlayerChunksClientboundPacket.send(peer.actor,maps,maps);return response(peer);
    }
    @GameTest(templateNamespace="api65_world_table",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void foreignDimensionTableAudiencePreservesIdentityPolicy(GameTestHelper h)throws Exception {
        ServerLevel foreign=h.getLevel().getServer().getLevel(Level.NETHER);
        String owner="ForeignTable"+Math.abs(h.absolutePos(BlockPos.ZERO).asLong());var rts=RTSPlayer.getNewBot(owner,Factions.VILLAGERS);
        try(var fog=new FogState(h.getLevel());var peer=new ResearchEconomyFixtures.Peer(foreign,owner)){
            h.assertTrue(peer.actor.serverLevel()==foreign&&foreign!=h.getLevel(),"actual recipient is attached to a different ServerLevel");
            UUID me=peer.actor.getUUID(),ally=UUID.randomUUID(),enemy=UUID.randomUUID();
            Set<ChunkPos> union=Set.of(new ChunkPos(1,1),new ChunkPos(2,2));
            Map<UUID,Set<ChunkPos>> maps=Map.of(me,union,ally,Set.of(new ChunkPos(2,2)),enemy,Set.of(new ChunkPos(999,999)));
            PlayerServerEvents.rtsPlayers.add(rts);FogOfWarServerEvents.invalidateRtsCache();
            h.assertTrue(!FogOfWarServerEvents.isFogActiveFor(peer.actor)&&FogOfWarServerEvents.isBlockVisibleFor(peer.actor,999,999),"foreign-world entity/chunk fog remains bypassed; audience authorization is a separate concern");
            for(GameType mode:List.of(GameType.SURVIVAL,GameType.SPECTATOR)){
                peer.actor.setGameMode(mode);var reply=send(peer,maps);
                h.assertTrue(reply.liveChunks.equals(Map.of(me,union))&&reply.edgeChunks.equals(Map.of(me,union)),"foreign active RTS "+mode+" receives own already-ally-unioned table only, never enemy/other-player maps");
            }
            var absent=send(peer,Map.of(enemy,Set.of(new ChunkPos(999,999))));
            h.assertTrue(absent.liveChunks.isEmpty()&&absent.edgeChunks.isEmpty(),"missing own foreign-world table fails closed, not an all-player fallback");
            PlayerServerEvents.rtsPlayers.remove(rts);FogOfWarServerEvents.invalidateRtsCache();
            for(GameType mode:List.of(GameType.SURVIVAL,GameType.SPECTATOR)){
                peer.actor.setGameMode(mode);var reply=send(peer,maps);
                h.assertTrue(reply.liveChunks.equals(maps)&&reply.edgeChunks.equals(maps),"ordinary non-RTS "+mode+" retains unrestricted observer overlay policy");
            }
            PlayerServerEvents.rtsPlayers.add(rts);FogOfWarServerEvents.invalidateRtsCache();field("enabled",false);
            var disabled=send(peer,maps);h.assertTrue(disabled.liveChunks.equals(maps)&&disabled.edgeChunks.equals(maps),"fog disabled retains unrestricted overlay policy even for foreign active RTS");
        }finally{PlayerServerEvents.rtsPlayers.remove(rts);FogOfWarServerEvents.invalidateRtsCache();}h.succeed();
    }
    @GameTest(templateNamespace="api65_world_table",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    @SuppressWarnings("unchecked")
    public static void periodicForeignOrthoviewRecipientNeverReceivesEnemyTable(GameTestHelper h)throws Exception {
        ServerLevel home=h.getLevel(),foreign=home.getServer().getLevel(Level.NETHER);String suffix=Long.toString(Math.abs(h.absolutePos(BlockPos.ZERO).asLong()));
        String viewerName="ForeignView"+suffix,enemyName="ForeignEnemy"+suffix;var viewerRts=RTSPlayer.getNewBot(viewerName,Factions.VILLAGERS);var enemyRts=RTSPlayer.getNewBot(enemyName,Factions.VILLAGERS);
        BlockPos pos=h.absolutePos(new BlockPos(4,2,4));var sight=new BuildingPlacement(Buildings.BARRACKS,home,pos,Rotation.NONE,enemyName,new ArrayList<>(List.of(new BuildingBlock(pos,Blocks.STONE.defaultBlockState()))),false);sight.isBuilt=true;
        try(var fog=new FogState(home);var viewer=new ResearchEconomyFixtures.Peer(foreign,viewerName);var enemy=new ResearchEconomyFixtures.Peer(home,enemyName)){
            viewer.actor.setGameMode(GameType.SPECTATOR);enemy.actor.setGameMode(GameType.SPECTATOR);
            PlayerServerEvents.rtsPlayers.add(viewerRts);PlayerServerEvents.rtsPlayers.add(enemyRts);PlayerServerEvents.orthoviewPlayers.add(viewer.actor);BuildingServerEvents.getBuildings().add(sight);FogOfWarServerEvents.invalidateRtsCache();
            viewer.drain();enemy.drain();field("ticksUntilUpdate",0);
            FogOfWarServerEvents.onWorldTick(new TickEvent.LevelTickEvent(LogicalSide.SERVER,TickEvent.Phase.END,home,()->true));
            Map<UUID,Set<ChunkPos>> source=(Map<UUID,Set<ChunkPos>>)field("playerEdgeChunks");
            h.assertTrue(source.containsKey(enemy.actor.getUUID())&&!source.get(enemy.actor.getUUID()).isEmpty(),"actual home-world enemy sight table exists, so an empty-source fixture cannot pass");
            h.assertTrue(!source.containsKey(viewer.actor.getUUID()),"dimension-specific world visibility cleared the foreign viewer's own current mask");
            var reply=response(viewer);
            h.assertTrue(reply.liveChunks.isEmpty()&&reply.edgeChunks.isEmpty(),"real periodic orthoview send cannot reinterpret foreign-world bypass as all-player table permission");
        }finally{BuildingServerEvents.getBuildings().remove(sight);PlayerServerEvents.rtsPlayers.remove(viewerRts);PlayerServerEvents.rtsPlayers.remove(enemyRts);PlayerServerEvents.orthoviewPlayers.removeIf(p->p.getName().getString().equals(viewerName));FogOfWarServerEvents.invalidateRtsCache();}h.succeed();
    }
}
