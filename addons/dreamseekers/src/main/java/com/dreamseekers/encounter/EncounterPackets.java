package com.dreamseekers.encounter;

import com.dreamseekers.DreamseekersMod;
import com.solegendary.reignofnether.research.economy.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;
import java.util.function.Supplier;

public final class EncounterPackets {
    public static final UUID ZERO=new UUID(0,0);
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(DreamseekersMod.id("map_encounter"),()->"1","1"::equals,"1"::equals);
    private static boolean initialized;
    public record Payload(boolean query,ResourceLocation dimension,NeutralEncounters.Kind kind,BlockPos floor,UUID view){}
    @Mod.EventBusSubscriber(modid="dreamseekers",bus=Mod.EventBusSubscriber.Bus.MOD)
    public static class Setup {@SubscribeEvent public static void setup(FMLCommonSetupEvent e){e.enqueueWork(EncounterPackets::init);}}
    public static void init(){if(initialized)return;initialized=true;
        CHANNEL.messageBuilder(Request.class,0,NetworkDirection.PLAY_TO_SERVER).encoder(Request::encode).decoder(Request::new).consumerMainThread(Request::handle).add();
        CHANNEL.messageBuilder(Snapshot.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder(Snapshot::encode).decoder(Snapshot::new).consumerMainThread(Snapshot::handle).add();}
    public static String perform(ServerPlayer actor,Connection connection,Payload p,UUID session,UUID epoch,long sequence){
        if(!NeutralEncounters.authorized(actor,connection)||!actor.level().dimension().location().equals(p.dimension))return "denied";
        return EconomyActionGate.execute(actor,connection,session,epoch,sequence,new EconomyActionGate.Fingerprint("map_encounter",p),account->NeutralEncounters.place(actor,connection,p.kind,p.dimension,p.floor));}
    public record Request(Payload payload,UUID epoch,UUID session,long sequence){
        public Request(FriendlyByteBuf b){this(new Payload(b.readBoolean(),b.readResourceLocation(),b.readEnum(NeutralEncounters.Kind.class),b.readBlockPos(),b.readUUID()),b.readUUID(),b.readUUID(),b.readLong());}
        public void encode(FriendlyByteBuf b){b.writeBoolean(payload.query);b.writeResourceLocation(payload.dimension);b.writeEnum(payload.kind);b.writeBlockPos(payload.floor);b.writeUUID(payload.view);b.writeUUID(epoch);b.writeUUID(session);b.writeLong(sequence);}
        public void handle(Supplier<NetworkEvent.Context> ctx){var n=ctx.get();n.setPacketHandled(true);var actor=n.getSender();if(!NeutralEncounters.authorized(actor,n.getNetworkManager())||!actor.level().dimension().location().equals(payload.dimension))return;
            String status=payload.query?"ready":perform(actor,n.getNetworkManager(),payload,session,epoch,sequence);send(actor,n.getNetworkManager(),payload.view,status,payload.query?0:sequence);}
    }
    static void send(ServerPlayer actor,Connection connection,UUID view,String status,long request){if(!NeutralEncounters.authorized(actor,connection))return;var token=EconomyActionGate.query(actor,connection);if(token==null)return;
        long order=ResearchQueueSync.nextSequence();if(order<0)return;var d=EncounterLedger.get(actor.server);
        CHANNEL.send(PacketDistributor.PLAYER.with(()->actor),new Snapshot(ResearchQueueSync.snapshotSession(),order,token.session(),token.epoch(),actor.getGameProfile().getName(),actor.level().dimension().location(),view,d.supported(),d.count(),status,request));}
    public record Snapshot(UUID stream,long order,UUID session,UUID epoch,String owner,ResourceLocation dimension,UUID view,boolean supported,int count,String status,long request){
        public Snapshot(FriendlyByteBuf b){this(b.readUUID(),b.readLong(),b.readUUID(),b.readUUID(),b.readUtf(256),b.readResourceLocation(),b.readUUID(),b.readBoolean(),b.readVarInt(),b.readUtf(96),b.readLong());}
        public void encode(FriendlyByteBuf b){b.writeUUID(stream);b.writeLong(order);b.writeUUID(session);b.writeUUID(epoch);b.writeUtf(owner,256);b.writeResourceLocation(dimension);b.writeUUID(view);b.writeBoolean(supported);b.writeVarInt(count);b.writeUtf(status,96);b.writeLong(request);}
        public void handle(Supplier<NetworkEvent.Context> ctx){ctx.get().setPacketHandled(true);var source=ctx.get().getNetworkManager();net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->EncounterClient.accept(this,source));}
    }
}
