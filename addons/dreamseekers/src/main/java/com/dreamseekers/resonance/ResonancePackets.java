package com.dreamseekers.resonance;

import com.dreamseekers.DreamseekersMod;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.research.economy.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;
import java.util.function.Supplier;

/** Owner-private state; no client supplied amount, destination owner or target list. */
public final class ResonancePackets {
    public static final UUID ZERO=new UUID(0,0);
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(DreamseekersMod.id("resonance_network"),()->"2","2"::equals,"2"::equals);
    private static boolean registered;
    private ResonancePackets(){}
    public static void register(){if(registered)return;registered=true;ResonanceState.bootstrap();
        CHANNEL.messageBuilder(Request.class,0,NetworkDirection.PLAY_TO_SERVER).encoder(Request::encode).decoder(Request::new).consumerMainThread(Request::handle).add();
        CHANNEL.messageBuilder(Snapshot.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder(Snapshot::encode).decoder(Snapshot::new).consumerMainThread(Snapshot::handle).add();}
    public enum Action { QUERY, PULSE }
    public record Payload(Action action,ResourceLocation dimension,BlockPos origin,UUID view,UUID instance,long ownerRevision,long capture,long generation){}
    public record Request(Payload payload,UUID epoch,UUID session,long sequence){
        public Request(FriendlyByteBuf b){this(new Payload(b.readEnum(Action.class),b.readResourceLocation(),b.readBlockPos(),b.readUUID(),b.readUUID(),b.readLong(),b.readLong(),b.readLong()),b.readUUID(),b.readUUID(),b.readLong());}
        public void encode(FriendlyByteBuf b){var p=payload;b.writeEnum(p.action());b.writeResourceLocation(p.dimension());b.writeBlockPos(p.origin());b.writeUUID(p.view());b.writeUUID(p.instance());
            b.writeLong(p.ownerRevision());b.writeLong(p.capture());b.writeLong(p.generation());b.writeUUID(epoch);b.writeUUID(session);b.writeLong(sequence);}
        public void handle(Supplier<NetworkEvent.Context> supplier){var c=supplier.get();var actor=c.getSender();var connection=c.getNetworkManager();c.enqueueWork(()->{
            var p=payload;if(actor==null||!actor.level().dimension().location().equals(p.dimension()))return;
            var host=BuildingServerEvents.getBuildings().stream().filter(b->b.level==actor.level()&&b.originPos.equals(p.origin())).findFirst().orElse(null);
            if(p.action()==Action.QUERY){if(ResonanceNetwork.owns(actor,host))send(actor,connection,host,p.view(),"ready",0);return;}
            String status=EconomyActionGate.execute(actor,connection,session,epoch,sequence,new EconomyActionGate.Fingerprint("resonance_pulse",p),
                account->ResonanceNetwork.pulse(actor,host,p.instance(),p.ownerRevision(),p.capture(),p.generation()));
            if(ResonanceNetwork.owns(actor,host))send(actor,connection,host,p.view(),status,sequence);
            else EconomyActionResultPacket.send(actor,connection,status,sequence);
        });c.setPacketHandled(true);}
    }
    public record Snapshot(UUID stream,long order,UUID session,UUID epoch,String owner,ResourceLocation dimension,BlockPos origin,UUID view,
        UUID instance,long ownerRevision,long capture,long generation,int rank,int active,int total,int limit,int cooldown,boolean supported,boolean tuned,int pulseRange,String status,long request){
        /** Source-compatible baseline fixture constructor; network sends always compute the server range. */
        public Snapshot(UUID stream,long order,UUID session,UUID epoch,String owner,ResourceLocation dimension,BlockPos origin,UUID view,
                UUID instance,long ownerRevision,long capture,long generation,int rank,int active,int total,int limit,int cooldown,boolean supported,boolean tuned,String status,long request) {
            this(stream,order,session,epoch,owner,dimension,origin,view,instance,ownerRevision,capture,generation,rank,active,total,limit,cooldown,supported,tuned,ResonanceNetwork.PULSE_RANGE,status,request);
        }
        public Snapshot(FriendlyByteBuf b){this(b.readUUID(),b.readLong(),b.readUUID(),b.readUUID(),b.readUtf(256),b.readResourceLocation(),b.readBlockPos(),b.readUUID(),
            b.readUUID(),b.readLong(),b.readLong(),b.readLong(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readBoolean(),b.readVarInt(),b.readUtf(96),b.readLong());}
        public boolean valid(){return ownerRevision>=0&&capture>=0&&generation>=0&&rank>=0&&rank<=active&&active>=0&&active<=limit&&total>=active&&(limit==4||limit==6)&&cooldown>=0&&(pulseRange==12||pulseRange==14);}
        public void encode(FriendlyByteBuf b){b.writeUUID(stream);b.writeLong(order);b.writeUUID(session);b.writeUUID(epoch);b.writeUtf(owner,256);b.writeResourceLocation(dimension);b.writeBlockPos(origin);b.writeUUID(view);
            b.writeUUID(instance);b.writeLong(ownerRevision);b.writeLong(capture);b.writeLong(generation);b.writeVarInt(rank);b.writeVarInt(active);b.writeVarInt(total);b.writeVarInt(limit);b.writeVarInt(cooldown);
            b.writeBoolean(supported);b.writeBoolean(tuned);b.writeVarInt(pulseRange);b.writeUtf(status,96);b.writeLong(request);}
        public void handle(Supplier<NetworkEvent.Context> supplier){var c=supplier.get();var connection=c.getNetworkManager();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->ResonanceClient.accept(this,connection)));c.setPacketHandled(true);}
    }
    public static void send(ServerPlayer actor,Connection connection,BuildingPlacement p,UUID view,String status,long request){
        if(!ResonanceNetwork.owns(actor,p))return;var token=EconomyActionGate.query(actor,connection);var state=ResonanceNetwork.view(p);if(token==null||state==null)return;
        long order=ResearchQueueSync.nextSequence();if(order<0)return;
        CHANNEL.send(PacketDistributor.PLAYER.with(()->actor),new Snapshot(ResearchQueueSync.snapshotSession(),order,token.session(),token.epoch(),p.ownerName,p.level.dimension().location(),p.originPos,view,
            state.instance(),state.ownerRevision(),state.capture(),state.generation(),state.rank(),state.active(),state.total(),state.limit(),state.cooldown(),state.supported(),state.tuned(),ResonanceNetwork.pulseRange(p.ownerName,false),status,request));
        EconomyBalanceSync.send(actor);
    }
}
