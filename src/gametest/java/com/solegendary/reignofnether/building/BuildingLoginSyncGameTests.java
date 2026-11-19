package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.building.custombuilding.*;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.research.economy.ResearchEconomyFixtures.Peer;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Real server player-registry and packet channel tests; not GUI/client rendering coverage. */
@GameTestHolder("api64_building_login_sync") @PrefixGameTestTemplate(false)
public final class BuildingLoginSyncGameTests {
    private static final Set<Fixture> OPEN = Collections.newSetFromMap(new IdentityHashMap<>());
    @AfterBatch(batch="building_login") public static void cleanup(ServerLevel level) { for (var f : List.copyOf(OPEN)) f.close(); }
    static final class Fixture implements AutoCloseable {
        final GameTestHelper h; final String owner; final Peer peer; final ServerPlayer player;
        final CustomBuilding custom; final BuildingPlacement placement; final List<String> received = new ArrayList<>();
        final int customId, placementId; boolean closed;
        Fixture(GameTestHelper h) throws Exception {
            this.h=h; owner="Login"+Math.abs(h.absolutePos(BlockPos.ZERO).asLong());
            peer=new Peer(h.getLevel(),owner); player=h.getLevel().getServer().getPlayerList().getPlayerByName(owner);
            BlockPos pos=h.absolutePos(new BlockPos(3,2,3)); h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
            custom=new CustomBuilding("LoginSync"+owner,new Vec3i(1,1,1),Blocks.STONE,new CompoundTag());
            CustomBuildingServerEvents.customBuildings.add(custom);
            placement=new BuildingPlacement(custom,h.getLevel(),pos,Rotation.NONE,owner,new ArrayList<>(List.of(new BuildingBlock(pos,Blocks.STONE.defaultBlockState()))),false);
            placement.isBuilt=true; BuildingServerEvents.getBuildings().add(placement);
            customId=id(new CustomBuildingClientboundPacket(owner,custom.name,BlockPos.ZERO,new CompoundTag(),new CompoundTag(),new CompoundTag()));
            placementId=id(new BuildingClientboundPacket(BuildingAction.PLACE_CUSTOM,BuildingClientboundPacket.EMPTY,custom.name,pos,Rotation.NONE,owner,-1,0,0,false,0,true,com.solegendary.reignofnether.building.buildings.placements.PortalPlacement.PortalType.BASIC,BlockPos.ZERO));
            OPEN.add(this); peer.drain();
        }
        void login() { BuildingServerEvents.onPlayerJoin(new PlayerEvent.PlayerLoggedInEvent(player)); }
        void collect() {
            for(Object packet:peer.drain()) if(packet instanceof ClientboundCustomPayloadPacket payload) {
                FriendlyByteBuf b=new FriendlyByteBuf(payload.getData().copy());
                try { int id=b.readVarInt();
                    if(id==customId) {var p=new CustomBuildingClientboundPacket(b);if(p.name.equals(custom.name)&&p.playerName.equals(owner))received.add("definition");}
                    else if(id==placementId) {var p=new BuildingClientboundPacket(b);if(p.buildingPos.equals(placement.originPos)&&p.action==BuildingAction.PLACE_CUSTOM)received.add("placement");}
                } finally {b.release();}
            }
        }
        void done(){close();h.succeed();}
        public void close(){if(closed)return;closed=true;OPEN.remove(this);BuildingLoginSync.logout(new PlayerEvent.PlayerLoggedOutEvent(player));BuildingServerEvents.getBuildings().remove(placement);CustomBuildingServerEvents.customBuildings.remove(custom);peer.close();}
    }
    static int id(Object packet){FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());try{PacketHandler.INSTANCE.encodeMessage(packet,b);return b.readVarInt();}finally{b.release();}}
    @GameTest(templateNamespace="api64_building_login_sync",template="ore_mine_test_arena",batch="building_login",timeoutTicks=90)
    public static void definitionsPrecedePlacementAndDuplicateLoginCoalesces(GameTestHelper h) throws Exception {
        Fixture f=new Fixture(h);h.assertTrue(!h.getLevel().getServer().isDedicatedServer(),"GameTest server exercises integrated-server delay");
        f.login();CustomBuildingServerEvents.onPlayerJoin(new PlayerEvent.PlayerLoggedInEvent(f.player));f.collect();h.assertTrue(f.received.isEmpty(),"no immediate integrated snapshot");
        h.runAfterDelay(18,()->{f.collect();h.assertTrue(f.received.isEmpty(),"no snapshot before20ticks");});
        h.runAfterDelay(23,()->{f.collect();h.assertTrue(f.received.equals(List.of("definition","placement")),"exactly one ordered snapshot after coalesced login");});
        h.runAfterDelay(45,()->{f.collect();h.assertTrue(f.received.equals(List.of("definition","placement")),"consumed snapshot cannot replay");f.done();});
    }
    @GameTest(templateNamespace="api64_building_login_sync",template="ore_mine_test_arena",batch="building_login_logout",timeoutTicks=70)
    public static void logoutCancelsPendingSnapshot(GameTestHelper h) throws Exception {
        Fixture f=new Fixture(h);f.login();BuildingLoginSync.logout(new PlayerEvent.PlayerLoggedOutEvent(f.player));
        h.runAfterDelay(30,()->{f.collect();h.assertTrue(f.received.isEmpty(),"logout revokes pending snapshot even before registry removal");f.done();});
    }
    @GameTest(templateNamespace="api64_building_login_sync",template="ore_mine_test_arena",batch="building_login_connection",timeoutTicks=70)
    public static void changedConnectionRevokesSnapshot(GameTestHelper h) throws Exception {
        Fixture f=new Fixture(h);f.login();var original=f.player.connection;f.player.connection=null;
        BuildingLoginSync.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->true,h.getLevel().getServer()));f.player.connection=original;
        h.runAfterDelay(30,()->{f.collect();h.assertTrue(f.received.isEmpty(),"connection replacement invalidates, restoring old reference does not revive");f.done();});
    }
    @GameTest(templateNamespace="api64_building_login_sync",template="ore_mine_test_arena",batch="building_login_disabled",timeoutTicks=70)
    public static void disablingSyncCancelsRatherThanDefers(GameTestHelper h) throws Exception {
        Fixture f=new Fixture(h);f.login();boolean old=PlayerServerEvents.rtsSyncingEnabled;
        try{PlayerServerEvents.rtsSyncingEnabled=false;BuildingLoginSync.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->true,h.getLevel().getServer()));}
        finally{PlayerServerEvents.rtsSyncingEnabled=old;}
        h.runAfterDelay(30,()->{f.collect();h.assertTrue(f.received.isEmpty(),"disabled job cannot revive when syncing enabled again");f.done();});
    }
    @AfterBatch(batch="building_login_disabled") public static void cleanupDisabled(ServerLevel level){cleanup(level);}
    @GameTest(templateNamespace="api64_building_login_sync",template="ore_mine_test_arena",batch="building_login_stop",timeoutTicks=70)
    public static void stoppingServerCancelsPendingSnapshot(GameTestHelper h) throws Exception {
        Fixture f=new Fixture(h);f.login();BuildingLoginSync.stopping(new ServerStoppingEvent(h.getLevel().getServer()));
        h.runAfterDelay(30,()->{f.collect();h.assertTrue(f.received.isEmpty(),"stopped server cannot publish stale login snapshot");f.done();});
    }
    @AfterBatch(batch="building_login_stop") public static void cleanupStop(ServerLevel level){cleanup(level);}
    @AfterBatch(batch="building_login_logout") public static void cleanupLogout(ServerLevel level){cleanup(level);}
    @AfterBatch(batch="building_login_connection") public static void cleanupConnection(ServerLevel level){cleanup(level);}
    @GameTest(templateNamespace="api64_building_login_sync",template="ore_mine_test_arena",batch="building_login_identity",timeoutTicks=70)
    public static void sameUuidDifferentPlayerCannotInheritSnapshot(GameTestHelper h) throws Exception {
        Fixture f=new Fixture(h);f.login();var server=h.getLevel().getServer();
        var replacement=new ServerPlayer(server,h.getLevel(),f.player.getGameProfile());
        Map<UUID,ServerPlayer> byId=net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(net.minecraft.server.players.PlayerList.class,server.getPlayerList(),"f_11197_");
        h.assertTrue(byId!=null&&byId.get(f.player.getUUID())==f.player,"exact original UUID registration");
        try {byId.put(f.player.getUUID(),replacement);BuildingLoginSync.tick(new TickEvent.ServerTickEvent(TickEvent.Phase.END,()->true,server));}
        finally{byId.put(f.player.getUUID(),f.player);}
        h.runAfterDelay(30,()->{f.collect();h.assertTrue(f.received.isEmpty(),"same UUID/name different session invalidates, cannot revive old job");f.done();});
    }
    @AfterBatch(batch="building_login_identity") public static void cleanupIdentity(ServerLevel level){cleanup(level);}

}
