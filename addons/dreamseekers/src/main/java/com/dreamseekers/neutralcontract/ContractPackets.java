package com.dreamseekers.neutralcontract;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.unit.FireflyWorker;
import com.solegendary.reignofnether.research.economy.*;
import net.minecraft.network.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;
import java.util.function.Supplier;

/** Dedicated bounded protocol, sharing the existing connection/account replay window. */
public final class ContractPackets {
    public static final UUID ZERO=new UUID(0,0);
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(DreamseekersMod.id("neutral_contract"),()->"1","1"::equals,"1"::equals);
    private static final String CLOSED="neutral_contract_closed";
    private static boolean initialized;
    private ContractPackets(){}
    public enum Action { QUERY, START, CANCEL }
    public record Payload(Action action,ResourceLocation dimension,int id,UUID uuid,long ownerRevision,UUID view,int targetId,UUID targetUuid){}
    @Mod.EventBusSubscriber(modid="dreamseekers",bus=Mod.EventBusSubscriber.Bus.MOD)
    public static class Setup {@SubscribeEvent public static void setup(FMLCommonSetupEvent e){e.enqueueWork(ContractPackets::init);}}
    public static void init(){if(initialized)return;initialized=true;
        CHANNEL.messageBuilder(Request.class,0,NetworkDirection.PLAY_TO_SERVER).encoder(Request::encode).decoder(Request::new).consumerMainThread(Request::handle).add();
        CHANNEL.messageBuilder(Snapshot.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder(Snapshot::encode).decoder(Snapshot::new).consumerMainThread(Snapshot::handle).add();
    }
    public record Request(Payload payload,UUID epoch,UUID session,long sequence){
        public Request(FriendlyByteBuf b){this(new Payload(b.readEnum(Action.class),b.readResourceLocation(),b.readVarInt(),b.readUUID(),b.readLong(),b.readUUID(),b.readVarInt(),b.readUUID()),b.readUUID(),b.readUUID(),b.readLong());}
        public void encode(FriendlyByteBuf b){b.writeEnum(payload.action);b.writeResourceLocation(payload.dimension);b.writeVarInt(payload.id);b.writeUUID(payload.uuid);b.writeLong(payload.ownerRevision);b.writeUUID(payload.view);b.writeVarInt(payload.targetId);b.writeUUID(payload.targetUuid);b.writeUUID(epoch);b.writeUUID(session);b.writeLong(sequence);}
        public void handle(Supplier<NetworkEvent.Context> ctx){
            var n=ctx.get();n.setPacketHandled(true);
            var actor=n.getSender();var connection=n.getNetworkManager();
            if(actor==null||connection==null)return;
            if(payload.action==Action.QUERY){
                send(actor,connection,findWorker(actor,payload),payload.view,"ready",0);
                return; // a view query has no pending action and never discloses a foreign worker
            }
            if(sequence<=0)return;
            // Consume the owner's sequence even if the worker disappeared while this request was in flight.
            String status=EconomyActionGate.execute(actor,connection,session,epoch,sequence,new EconomyActionGate.Fingerprint("neutral_contract",payload),account->{
                var worker=findWorker(actor,payload);
                if(!NeutralContracts.owns(actor,worker))return CLOSED;
                if(payload.action==Action.CANCEL)return NeutralContracts.cancel(actor,worker,payload.ownerRevision);
                var target=actor.serverLevel().getEntity(payload.targetId);
                if(!(target instanceof LivingEntity living)||!living.getUUID().equals(payload.targetUuid))return "invalid_target";
                return NeutralContracts.begin(actor,connection,worker,payload.ownerRevision,living,payload.view);
            });
            // A delayed request must never retire a reused sequence in a *new* session or account epoch.
            var token=EconomyActionGate.query(actor,connection);
            if(token==null||!token.session().equals(session)||!token.epoch().equals(epoch))return;
            // Actions and their callbacks can retire ownership. Never attach details from the old view.
            var worker=findWorker(actor,payload);
            if(CLOSED.equals(status)||!NeutralContracts.owns(actor,worker)){
                EconomyActionResultPacket.send(actor,connection,CLOSED,sequence);
                return;
            }
            sendSnapshot(actor,worker,payload.view,status,sequence,token);
        }
    }
    private static FireflyWorker findWorker(ServerPlayer actor,Payload payload){
        if(!actor.serverLevel().dimension().location().equals(payload.dimension))return null;
        var found=actor.serverLevel().getEntity(payload.id);
        return found instanceof FireflyWorker worker&&worker.getUUID().equals(payload.uuid)?worker:null;
    }
    public static void send(ServerPlayer actor,Connection connection,FireflyWorker worker,UUID view,String status,long request){
        if(!NeutralContracts.owns(actor,worker))return;var token=EconomyActionGate.query(actor,connection);if(token==null)return;
        sendSnapshot(actor,worker,view,status,request,token);
    }
    private static void sendSnapshot(ServerPlayer actor,FireflyWorker worker,UUID view,String status,long request,EconomyActionGate.Token token){
        long order=ResearchQueueSync.nextSequence();if(order<0)return;
        CHANNEL.send(PacketDistributor.PLAYER.with(()->actor),new Snapshot(ResearchQueueSync.snapshotSession(),order,token.session(),token.epoch(),
            actor.getGameProfile().getName(),worker.level().dimension().location(),worker.getId(),worker.getUUID(),view,worker.rts().ownershipRevision,
            NeutralContracts.available(actor,worker),NeutralContracts.active(worker),NeutralContracts.remaining(worker),NeutralContracts.used(worker),ContractResearch.capacity(worker.getOwnerName(),false),status,request));
        EconomyBalanceSync.send(actor);
    }
    public record Snapshot(UUID stream,long order,UUID session,UUID epoch,String owner,ResourceLocation dimension,int id,UUID uuid,UUID view,long ownerRevision,
        boolean available,boolean active,int remaining,int used,int capacity,String status,long request){
        public Snapshot(FriendlyByteBuf b){this(b.readUUID(),b.readLong(),b.readUUID(),b.readUUID(),b.readUtf(256),b.readResourceLocation(),b.readVarInt(),b.readUUID(),b.readUUID(),b.readLong(),b.readBoolean(),b.readBoolean(),b.readInt(),b.readInt(),b.readInt(),b.readUtf(96),b.readLong());}
        public void encode(FriendlyByteBuf b){b.writeUUID(stream);b.writeLong(order);b.writeUUID(session);b.writeUUID(epoch);b.writeUtf(owner,256);b.writeResourceLocation(dimension);b.writeVarInt(id);b.writeUUID(uuid);b.writeUUID(view);b.writeLong(ownerRevision);b.writeBoolean(available);b.writeBoolean(active);b.writeInt(remaining);b.writeInt(used);b.writeInt(capacity);b.writeUtf(status,96);b.writeLong(request);}
        public boolean valid(){return ownerRevision>=0&&remaining>=-1&&remaining<=NeutralContracts.CHANNEL_TICKS&&used>=0&&used<=ContractLedger.LIMIT+64&&(capacity==3||capacity==5);}
        public void handle(Supplier<NetworkEvent.Context> ctx){ctx.get().setPacketHandled(true);var source=ctx.get().getNetworkManager();net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->ContractClient.accept(this,source));}
    }
}
