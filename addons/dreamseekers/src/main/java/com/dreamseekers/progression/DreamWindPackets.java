package com.dreamseekers.progression;
import com.dreamseekers.DreamseekersMod;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;
import java.util.function.Supplier;
/** Read-only private progression transport. Purchases keep the existing authenticated research nonce protocol. */
public final class DreamWindPackets {
 public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(DreamseekersMod.id("wind_progression"),()->"1","1"::equals,"1"::equals);
 private static boolean registered;private static UUID stream=UUID.randomUUID();private static long order;
 private static final Map<net.minecraft.network.Connection,Long> queried=new WeakHashMap<>();
 private DreamWindPackets(){}
 public static void register(){if(registered)return;registered=true;
  CHANNEL.messageBuilder(Query.class,0,NetworkDirection.PLAY_TO_SERVER).encoder((p,b)->{}).decoder(b->new Query()).consumerMainThread(Query::handle).add();
  CHANNEL.messageBuilder(Snapshot.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder(Snapshot::encode).decoder(Snapshot::new).consumerMainThread(Snapshot::handle).add();}
 public record Query(){public void handle(Supplier<NetworkEvent.Context> supplier){var c=supplier.get();var actor=c.getSender();var connection=c.getNetworkManager();c.enqueueWork(()->{
  if(actor==null)return;long now=actor.serverLevel().getGameTime();Long last=queried.get(connection);if(last!=null&&now>=last&&now-last<10)return;queried.put(connection,now);send(actor);
 });c.setPacketHandled(true);}}
 public static void sendToOwner(MinecraftServer server,String owner){var actor=server.getPlayerList().getPlayerByName(owner);if(actor!=null)send(actor);}
 public static void send(ServerPlayer actor){
  if(actor.getServer()==null||!actor.getServer().isSameThread()||actor.getServer().getPlayerList().getPlayerByName(actor.getName().getString())!=actor||PlayerServerEvents.getRTSPlayer(actor.getName().getString())==null)return;
  var r=ExtendedResourcesSync.account(actor.getName().getString());if(r==null)return;var s=DreamWindProgression.read(r);
  if(order==Long.MAX_VALUE)return;
  CHANNEL.send(PacketDistributor.PLAYER.with(()->actor),new Snapshot(stream,++order,r.ownerName,r.accountEpoch,s.supported(),s.wind(),s.costMask(),s.trainMask(),s.researchMask()));
 }
 public record Snapshot(UUID stream,long order,String owner,UUID epoch,boolean supported,boolean wind,int cost,int train,int research){
  public Snapshot(FriendlyByteBuf b){this(b.readUUID(),b.readLong(),b.readUtf(128),b.readUUID(),b.readBoolean(),b.readBoolean(),b.readUnsignedByte(),b.readUnsignedByte(),b.readUnsignedByte());}
  public void encode(FriendlyByteBuf b){b.writeUUID(stream);b.writeLong(order);b.writeUtf(owner,128);b.writeUUID(epoch);b.writeBoolean(supported);b.writeBoolean(wind);b.writeByte(cost);b.writeByte(train);b.writeByte(research);}
  public void handle(Supplier<NetworkEvent.Context> supplier){var c=supplier.get();var connection=c.getNetworkManager();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->DreamWindClient.accept(this,connection)));c.setPacketHandled(true);}
 }
}
