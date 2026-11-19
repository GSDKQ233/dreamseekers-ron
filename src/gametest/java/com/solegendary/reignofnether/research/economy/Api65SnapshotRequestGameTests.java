package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.sandbox.SandboxServer;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.*;

/** One bounded real-server handler/recipient probe; EmbeddedChannels are not two running Minecraft clients. */
@GameTestHolder("api65_snapshot_request") @PrefixGameTestTemplate(false)
public final class Api65SnapshotRequestGameTests {
    private static Object fogField(String name) throws Exception {
        var field=FogOfWarServerEvents.class.getDeclaredField(name);field.setAccessible(true);return field.get(null);
    }
    private static void fogField(String name,Object value) throws Exception {
        var field=FogOfWarServerEvents.class.getDeclaredField(name);field.setAccessible(true);field.set(null,value);
    }
    private static final class FogState implements AutoCloseable {
        private final Object enabled,level,ticks;
        private final Map<String,Map<Object,Object>> previous=new HashMap<>();
        @SuppressWarnings("unchecked") FogState(ServerLevel current) throws Exception {
            enabled=fogField("enabled");level=fogField("serverLevel");ticks=fogField("ticksUntilUpdate");
            for(String name:List.of("playerLiveChunks","playerEdgeChunks","playerEdgeMasks","pendingResends","isRtsPlayerCache")){
                Map<Object,Object> map=(Map<Object,Object>)fogField(name);previous.put(name,new HashMap<>(map));map.clear();
            }
            fogField("serverLevel",current);fogField("enabled",true);
        }
        @SuppressWarnings("unchecked") public void close() throws Exception {
            fogField("enabled",enabled);fogField("serverLevel",level);fogField("ticksUntilUpdate",ticks);
            for(var entry:previous.entrySet()){
                Map<Object,Object> map=(Map<Object,Object>)fogField(entry.getKey());map.clear();map.putAll(entry.getValue());
            }
        }
    }
    private static ProductionPlacement placement(ServerLevel level,BlockPos pos,String owner) {
        var value=new ProductionPlacement(Buildings.BARRACKS,level,pos,Rotation.NONE,owner,
                new ArrayList<>(List.of(new BuildingBlock(pos,Blocks.STONE.defaultBlockState()))),false);
        value.isBuilt=true;return value;
    }
    private static void recalculateFog(ServerLevel level) throws Exception {
        fogField("ticksUntilUpdate",0);
        FogOfWarServerEvents.onWorldTick(new TickEvent.LevelTickEvent(LogicalSide.SERVER,TickEvent.Phase.END,level,()->true));
    }
    private static void request(GameTestHelper h,ResearchEconomyFixtures.Peer peer,BlockPos pos) {
        var packet=new BuildingServerboundPacket(BuildingAction.REQUEST_REPLACEMENT,"",pos,BlockPos.ZERO,
                Rotation.NONE,"",new int[0],false);
        FriendlyByteBuf wire=new FriendlyByteBuf(Unpooled.buffer());
        try {
            // The real registered encoder supplies discriminator + payload; use the registered decoder/handler
            // with the existing authenticated server-side connection context, as other packet GameTests do.
            PacketHandler.INSTANCE.encodeMessage(packet,wire);
            h.assertTrue(wire.readVarInt()==ResearchEconomyFixtures.discriminator(packet),"registered request discriminator round trip");
            var decoded=new BuildingServerboundPacket(wire);
            h.assertTrue(wire.readableBytes()==0&&decoded.action==BuildingAction.REQUEST_REPLACEMENT,
                    "exact registered read-only request payload is decoded");
            decoded.handle(()->peer.context);
        } finally {wire.release();}
        peer.channel.runPendingTasks();
    }
    private static List<BuildingClientboundPacket> snapshots(GameTestHelper h,ResearchEconomyFixtures.Peer peer) {
        int discriminator=ResearchEconomyFixtures.discriminator(new BuildingClientboundPacket(
                BuildingAction.PLACE,BuildingClientboundPacket.EMPTY,"",BlockPos.ZERO));
        List<BuildingClientboundPacket> result=new ArrayList<>();
        for(Object raw:peer.drain()) if(raw instanceof ClientboundCustomPayloadPacket packet){
            var wire=new FriendlyByteBuf(packet.getData().copy());
            try {
                if(wire.readVarInt()==discriminator){
                    result.add(new BuildingClientboundPacket(wire));
                    h.assertTrue(wire.readableBytes()==0,"snapshot payload including native state is fully decoded");
                }
            } finally {wire.release();}
        }
        return result;
    }
    private static BuildingClientboundPacket oneSnapshot(GameTestHelper h,ResearchEconomyFixtures.Peer peer,
                                                          BuildingPlacement target,String reason) {
        var replies=snapshots(h,peer);h.assertTrue(replies.size()==1,reason+": exactly one snapshot");
        var reply=replies.get(0);
        h.assertTrue(reply.action==BuildingAction.PLACE&&reply.buildingPos.equals(target.originPos)
                        &&reply.ownerName.equals(target.ownerName),reason+": authoritative enemy origin/owner");
        h.assertTrue(reply.nativeState.getInt("version")==1&&reply.nativeState.getBoolean("built")
                        &&reply.nativeState.getString("owner").equals(target.ownerName),reason+": absolute native state included");
        return reply;
    }
    @GameTest(templateNamespace="api65_snapshot_request",template="ore_mine_test_arena",
            batch="api65_snapshot_request",timeoutTicks=160)
    public static void readOnlyReplacementIsRequesterScopedAndRespectsVisibilityAndLevel(GameTestHelper h) throws Exception {
        ServerLevel home=h.getLevel(),foreign=home.getServer().getLevel(Level.NETHER);
        h.assertTrue(home==BuildingServerEvents.getServerLevel()&&foreign!=null&&foreign!=home,
                "native map and a distinct real dimension exist");
        BlockPos arena=h.absolutePos(new BlockPos(4,2,4)),pos=new BlockPos(arena.getX(),64,arena.getZ()),sightPos=pos.offset(-3,0,0);
        h.assertTrue(!home.isOutsideBuildHeight(pos)&&!foreign.isOutsideBuildHeight(pos),"shared Y64 is valid in both dimensions");
        var homeBefore=home.getBlockState(pos);var sightBefore=home.getBlockState(sightPos);var foreignBefore=foreign.getBlockState(pos);
        String suffix=Long.toUnsignedString(h.absolutePos(BlockPos.ZERO).asLong(),36);
        String requesterName="Req"+suffix,observerName="Obs"+suffix,enemyName="Foe"+suffix;
        RTSPlayer requesterRts=RTSPlayer.getNewBot(requesterName,Factions.VILLAGERS);
        boolean syncBefore=PlayerServerEvents.rtsSyncingEnabled;List<BuildingPlacement> fixtures=new ArrayList<>();
        try(var fog=new FogState(home);var requester=new ResearchEconomyFixtures.Peer(home,requesterName);
            var observer=new ResearchEconomyFixtures.Peer(home,observerName)) {
            h.assertTrue(!SandboxServer.isAnyoneASandboxPlayer(),"no sandbox grant masks the enemy read-only positive control");
            PlayerServerEvents.rtsPlayers.add(requesterRts);PlayerServerEvents.orthoviewPlayers.add(requester.actor);
            PlayerServerEvents.rtsSyncingEnabled=true;FogOfWarServerEvents.invalidateRtsCache();
            requester.actor.setGameMode(GameType.SURVIVAL);observer.actor.setGameMode(GameType.SPECTATOR);
            requester.actor.setPos(pos.getX(),64,pos.getZ());observer.actor.setPos(pos.getX()+1,64,pos.getZ());
            home.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());home.setBlockAndUpdate(sightPos,Blocks.STONE.defaultBlockState());
            foreign.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
            h.assertTrue(home.getBlockState(pos).is(Blocks.STONE)&&foreign.getBlockState(pos).is(Blocks.STONE),
                    "both dimensions have real same-coordinate sentinels before requests");
            var target=placement(home,pos,enemyName);var sight=placement(home,sightPos,requesterName);
            var foreignTarget=placement(foreign,pos,enemyName);fixtures.addAll(List.of(target,sight,foreignTarget));
            BuildingServerEvents.getBuildings().add(target);BuildingServerEvents.getBuildings().add(sight);
            h.assertTrue(!requesterName.equals(enemyName)&&!AlliancesServerEvents.canControlAlly(requesterName,enemyName)
                            &&!SandboxServer.isSandboxPlayer(requesterName),"enemy snapshot requester has no owner/ally/editor mutation grant");
            recalculateFog(home);
            h.assertTrue(FogOfWarServerEvents.isFogActiveFor(requester.actor)
                            &&FogOfWarServerEvents.canPlayerSeeBuilding(requester.actor,target),"actual own-building sight reveals enemy building");
            h.assertTrue(!FogOfWarServerEvents.isFogActiveFor(observer.actor),"ordinary non-RTS observer keeps existing unrestricted policy");
            requester.drain();observer.drain();
            BuildingClientboundPacket.sendSnapshotTo(observer.actor,target);
            oneSnapshot(h,observer,target,"second receiver channel positive control");
            h.assertTrue(snapshots(h,requester).isEmpty(),"direct observer control did not address requester");

            request(h,requester,pos);
            var first=oneSnapshot(h,requester,target,"read-only visible enemy request");
            h.assertTrue(snapshots(h,observer).isEmpty(),"REQUEST_REPLACEMENT must not broadcast to the ready second receiver");

            BuildingServerEvents.getBuildings().remove(sight);recalculateFog(home);
            h.assertTrue(!FogOfWarServerEvents.canPlayerSeeBuilding(requester.actor,target),"actual recalculated mask hides enemy with no orthoview FPS bubble");
            requester.drain();observer.drain();request(h,requester,pos);
            h.assertTrue(snapshots(h,requester).isEmpty()&&snapshots(h,observer).isEmpty(),"hidden active-RTS request sends no snapshot to either receiver");
            request(h,observer,pos);
            oneSnapshot(h,observer,target,"ordinary observer read-only exception");
            h.assertTrue(snapshots(h,requester).isEmpty(),"observer request is still requester-only");

            BuildingServerEvents.getBuildings().add(sight);recalculateFog(home);
            h.assertTrue(FogOfWarServerEvents.canPlayerSeeBuilding(requester.actor,target),"local visibility restored before dimension-negative check");
            BuildingServerEvents.getBuildings().remove(target);BuildingServerEvents.getBuildings().add(foreignTarget);
            h.assertTrue(BuildingUtils.findBuilding(home,pos)==null&&BuildingUtils.findBuilding(foreign,pos)==foreignTarget,
                    "only a real foreign-Level placement occupies requested coordinates");
            requester.drain();observer.drain();request(h,requester,pos);
            h.assertTrue(snapshots(h,requester).isEmpty()&&snapshots(h,observer).isEmpty(),"same-coordinate foreign placement cannot satisfy local read-only request");
            BuildingClientboundPacket.sendSnapshotTo(requester.actor,foreignTarget);
            h.assertTrue(snapshots(h,requester).isEmpty(),"targeted snapshot helper independently rejects different-Level recipient");

            BuildingServerEvents.getBuildings().remove(foreignTarget);BuildingServerEvents.getBuildings().add(target);
            request(h,requester,pos);var restored=oneSnapshot(h,requester,target,"local positive after rejected requests");
            h.assertTrue(snapshots(h,observer).isEmpty()&&restored.nativeState.equals(first.nativeState),
                    "repeat authoritative reply is idempotent and still single-recipient");
            h.assertTrue(target.ownerName.equals(enemyName)&&target.isBuilt&&BuildingServerEvents.getBuildings().contains(target)
                            &&home.getBlockState(pos).is(Blocks.STONE)&&foreign.getBlockState(pos).is(Blocks.STONE),
                    "read-only requests did not transfer ownership, destroy placement, or mutate either world sentinel");
        } finally {
            BuildingServerEvents.getBuildings().removeAll(fixtures);PlayerServerEvents.rtsPlayers.remove(requesterRts);
            PlayerServerEvents.orthoviewPlayers.removeIf(p->p.getName().getString().equals(requesterName));
            PlayerServerEvents.rtsSyncingEnabled=syncBefore;FogOfWarServerEvents.invalidateRtsCache();
            home.setBlockAndUpdate(pos,homeBefore);home.setBlockAndUpdate(sightPos,sightBefore);foreign.setBlockAndUpdate(pos,foreignBefore);
        }
        h.succeed();
    }
}
