package com.dreamseekers.worldtree;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.research.economy.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;
import java.util.function.Supplier;
/** Addon transport; its mutations use the same connection/session/sequence authority as core research. */
public final class WorldTreePackets {
    public static final UUID ZERO=new UUID(0,0);
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("dreamseekers","world_tree"),()->"3","3"::equals,"3"::equals);
    private static boolean registered;
    public enum Action{QUERY,START,CANCEL,START_WIND}
    public static void register(){if(registered)return;registered=true;
        CHANNEL.messageBuilder(Request.class,0,NetworkDirection.PLAY_TO_SERVER).encoder(Request::encode).decoder(Request::new).consumerMainThread(Request::handle).add();
        CHANNEL.messageBuilder(Snapshot.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder(Snapshot::encode).decoder(Snapshot::new).consumerMainThread(Snapshot::handle).add();
    }
    public record Request(Action action,ResourceLocation dimension,BlockPos origin,UUID instance,long capture,UUID epoch,UUID session,long sequence){
        public Request(FriendlyByteBuf b){this(b.readEnum(Action.class),b.readResourceLocation(),b.readBlockPos(),b.readUUID(),b.readLong(),b.readUUID(),b.readUUID(),b.readLong());}
        public void encode(FriendlyByteBuf b){b.writeEnum(action);b.writeResourceLocation(dimension);b.writeBlockPos(origin);b.writeUUID(instance);b.writeLong(capture);b.writeUUID(epoch);b.writeUUID(session);b.writeLong(sequence);}
        public void handle(Supplier<NetworkEvent.Context> ctx){var context=ctx.get();var actor=context.getSender();var connection=context.getNetworkManager();context.enqueueWork(()->{
            if(actor==null)return;DreamAnchorPlacement found=null;
            for(var p:BuildingServerEvents.getBuildings())if(p instanceof DreamAnchorPlacement candidate&&p.originPos.equals(origin)&&p.getLevel().dimension().location().equals(dimension)){found=candidate;break;}
            final var p=found;
            if(action==Action.QUERY){if(p!=null&&DreamWorldTreeService.owns(actor,p))send(actor,connection,p,"ready",0);return;}
            record Payload(Action action,ResourceLocation dimension,BlockPos origin,UUID instance,long capture){}
            var fingerprint=new EconomyActionGate.Fingerprint("dream_world_tree",new Payload(action,dimension,origin,instance,capture));
            var result=EconomyActionGate.execute(actor,connection,session,epoch,sequence,fingerprint,account->{
                if(p==null||p.getCaptureRevision()<0||p.getCaptureRevision()!=capture||!DreamWorldTreeService.owns(actor,p))return "forbidden";
                return switch(action){case START->DreamWorldTreeService.start(actor,p,instance,WorldTreeModel.CURRENT_SHADOW);case START_WIND->DreamWorldTreeService.start(actor,p,instance,WorldTreeModel.CURRENT_WIND);case CANCEL->DreamWorldTreeService.cancel(actor,p,instance);default->"invalid_action";};
            });
            if(p!=null&&DreamWorldTreeService.owns(actor,p))send(actor,connection,p,result,sequence);else EconomyActionResultPacket.send(actor,connection,result,sequence);
        });context.setPacketHandled(true);}
    }
    public static void send(ServerPlayer actor,net.minecraft.network.Connection connection,DreamAnchorPlacement p,String status,long completed){
        if(!DreamWorldTreeService.owns(actor,p)||!p.tree().supported||p.getCaptureRevision()<0)return;
        var token=EconomyActionGate.query(actor,connection);if(token==null)return;long order=ResearchQueueSync.nextSequence();if(order<0)return;
        var s=p.tree();var packet=new Snapshot(ResearchQueueSync.snapshotSession(),order,token.session(),token.epoch(),actor.getName().getString(),completed,status,
            p.getLevel().dimension().location(),p.originPos,s.instance,p.getCaptureRevision(),s.phase,s.remaining,s.blockedReason,s.model,s.target);
        CHANNEL.send(PacketDistributor.PLAYER.with(()->actor),packet);EconomyBalanceSync.send(actor);
    }
    public record Snapshot(UUID stream,long order,UUID session,UUID epoch,String owner,long completed,String status,ResourceLocation dimension,BlockPos origin,
                           UUID instance,long capture,WorldTreeState.Phase phase,int remaining,String reason,WorldTreeModel model,WorldTreeModel target){
        public Snapshot(FriendlyByteBuf b){this(b.readUUID(),b.readLong(),b.readUUID(),b.readUUID(),b.readUtf(128),b.readLong(),b.readUtf(96),b.readResourceLocation(),b.readBlockPos(),b.readUUID(),b.readLong(),b.readEnum(WorldTreeState.Phase.class),b.readVarInt(),b.readUtf(128),b.readEnum(WorldTreeModel.class),b.readBoolean()?b.readEnum(WorldTreeModel.class):null);}
        public void encode(FriendlyByteBuf b){b.writeUUID(stream);b.writeLong(order);b.writeUUID(session);b.writeUUID(epoch);b.writeUtf(owner,128);b.writeLong(completed);b.writeUtf(status,96);b.writeResourceLocation(dimension);b.writeBlockPos(origin);b.writeUUID(instance);b.writeLong(capture);b.writeEnum(phase);b.writeVarInt(remaining);b.writeUtf(reason,128);b.writeEnum(model);b.writeBoolean(target!=null);if(target!=null)b.writeEnum(target);}
        public void handle(Supplier<NetworkEvent.Context> ctx){var context=ctx.get();var connection=context.getNetworkManager();context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->WorldTreeClient.accept(this,connection)));context.setPacketHandled(true);}
    }
}
