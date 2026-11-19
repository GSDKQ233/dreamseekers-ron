package com.dreamseekers;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.buffer.Unpooled;
import net.minecraft.network.*;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.core.BlockPos;
import net.minecraftforge.common.util.FakePlayerFactory;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.resources.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Real connected embedded server peer/codec fixture, not a remote socket or rendered Minecraft client. */
final class PassiveIncomePeer implements AutoCloseable {
 final ServerPlayer actor;final Connection connection;final EmbeddedChannel channel;final ServerGamePacketListenerImpl previous;final PlayerRegistration registration;private boolean closed;
 PassiveIncomePeer(ServerLevel level,String owner){
  actor=FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes(owner.getBytes(StandardCharsets.UTF_8)),owner));previous=actor.connection;
  connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);
  PlayerRegistration attached=null;
  try {
   connection.setProtocol(ConnectionProtocol.PLAY);actor.connection=new ServerGamePacketListenerImpl(level.getServer(),connection,actor);connection.setListener(actor.connection);
   attached=new PlayerRegistration(actor);
   if(!connection.isConnected()||connection.getPacketListener()!=actor.connection||level.getServer().getPlayerList().getPlayerByName(owner)!=actor||level.getServer().getPlayerList().getPlayer(actor.getUUID())!=actor)throw new AssertionError("online owner exact connection/name/UUID peer not established");
   registration=attached;
  }catch(RuntimeException|Error failure){
   try{if(attached!=null)attached.close();}catch(RuntimeException|Error cleanup){failure.addSuppressed(cleanup);}
   try{channel.finishAndReleaseAll();}catch(RuntimeException|Error cleanup){failure.addSuppressed(cleanup);}
   finally{actor.connection=previous;}
   failure.printStackTrace();throw failure;
  }
 }
 List<Object> drain(){channel.runPendingTasks();List<Object> output=new ArrayList<>();Object p;while((p=channel.readOutbound())!=null)output.add(p);return output;}
 static int discriminator(Object packet){var b=new FriendlyByteBuf(Unpooled.buffer());try{PacketHandler.INSTANCE.encodeMessage(packet,b);return b.readVarInt();}finally{b.release();}}
 static boolean hasFwo(List<Object> packets,Resources account){
  int id=discriminator(new ResourcesClientboundPacket(ResourcesAction.SYNC,account.ownerName,0,0,0,0,BlockPos.ZERO,""));ResourcesClientboundPacket last=null;
  for(Object p:packets)if(p instanceof ClientboundCustomPayloadPacket c){var b=new FriendlyByteBuf(c.getData().copy());try{if(b.readVarInt()==id){b.markReaderIndex();var action=b.readEnum(ResourcesAction.class);b.resetReaderIndex();var result=new ResourcesClientboundPacket(b);if(action==ResourcesAction.SYNC&&result.ownerName.equals(account.ownerName))last=result;}}finally{b.release();}}
  return last!=null&&last.food==account.food&&last.wood==account.wood&&last.ore==account.ore&&last.emerald==account.emerald;
 }
 static boolean hasExtra(List<Object> packets,Resources account){
  int id=discriminator(new ExtendedResourcesClientboundPacket(UUID.randomUUID(),0,account.ownerName,account));ExtendedResourcesClientboundPacket last=null;
  for(Object p:packets)if(p instanceof ClientboundCustomPayloadPacket c){var b=new FriendlyByteBuf(c.getData().copy());try{if(b.readVarInt()==id){var result=new ExtendedResourcesClientboundPacket(b);if(result.owner.equals(account.ownerName))last=result;}}finally{b.release();}}
  return last!=null&&last.present&&last.epoch.equals(account.accountEpoch)&&last.revision==account.revision&&last.balances.equals(account.extra);
 }
 public void close(){if(closed)return;closed=true;try{registration.close();}finally{try{channel.finishAndReleaseAll();}finally{actor.connection=previous;}}}

    /** Test-only registry attachment: Forge getPlayers() is an unmodifiable view.
     * Never calls a fake login or relaxes product sender/connection/account authority.
     * Backing fields verified in this pinned1.20.1 mapping: f_11196_=players, f_11197_=playersByUUID. */
    private static final class PlayerRegistration implements AutoCloseable {
        private final net.minecraft.server.players.PlayerList list;
        private final List<ServerPlayer> players;
        private final Map<UUID,ServerPlayer> byId;
        private final ServerPlayer actor;
        private final boolean addedList,addedMap;
        private boolean closed;
        PlayerRegistration(ServerPlayer actor) {
            if(!actor.getServer().isSameThread())throw new AssertionError("peer registration must use real server thread");
            this.actor=actor;list=actor.getServer().getPlayerList();
            players=net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(net.minecraft.server.players.PlayerList.class,list,"f_11196_");
            byId=net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(net.minecraft.server.players.PlayerList.class,list,"f_11197_");
            if(players==null||byId==null)throw new AssertionError("pinned player registry fields unavailable");
            ServerPlayer named=list.getPlayerByName(actor.getName().getString()),identified=byId.get(actor.getUUID());
            if((named!=null&&named!=actor)||(identified!=null&&identified!=actor))throw new AssertionError("never replace another registered player identity");
            if(players.stream().filter(p->p==actor).count()>1)throw new AssertionError("duplicate live player registry entry");
            addedList=!players.contains(actor);addedMap=identified==null;
            if(addedList)players.add(actor);
            if(addedMap)byId.put(actor.getUUID(),actor);
            if(list.getPlayerByName(actor.getName().getString())!=actor||list.getPlayer(actor.getUUID())!=actor) {
                close();throw new AssertionError("native name and UUID lookups must resolve exact peer");
            }
        }
        @Override public void close(){
            if(closed)return;closed=true;
            if(addedMap)byId.remove(actor.getUUID(),actor);
            if(addedList)players.removeIf(p->p==actor);
        }
    }
}
