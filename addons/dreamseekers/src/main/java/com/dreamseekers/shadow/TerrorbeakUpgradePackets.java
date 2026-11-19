// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.shadow;
import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.unit.TerrorbeakUnit;
import com.solegendary.reignofnether.research.economy.*;
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
/** Appended independent channel; all purchase sequences still use the global economy gate. */
public final class TerrorbeakUpgradePackets {
    private static final String VERSION="1";
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(DreamseekersMod.id("terrorbeak_upgrade"),()->VERSION,VERSION::equals,VERSION::equals);
    public static final UUID ZERO=new UUID(0,0);
    private static boolean registered;
    private TerrorbeakUpgradePackets(){}
    @Mod.EventBusSubscriber(modid="dreamseekers",bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent public static void setup(FMLCommonSetupEvent e){e.enqueueWork(TerrorbeakUpgradePackets::init);}
    }
    public static void init(){if(registered)return;registered=true;
        CHANNEL.messageBuilder(Request.class,0,NetworkDirection.PLAY_TO_SERVER).encoder(Request::encode).decoder(Request::new).consumerMainThread(Request::handle).add();
        CHANNEL.messageBuilder(Snapshot.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder(Snapshot::encode).decoder(Snapshot::new).consumerMainThread(Snapshot::handle).add();
    }
    public record Payload(boolean query,ResourceLocation dimension,int id,UUID uuid,long ownerRevision,UUID view){}
    public record Request(Payload payload,UUID epoch,UUID session,long sequence) {
        public Request(FriendlyByteBuf b){this(new Payload(b.readBoolean(),b.readResourceLocation(),b.readVarInt(),b.readUUID(),b.readLong(),b.readUUID()),b.readUUID(),b.readUUID(),b.readLong());}
        public void encode(FriendlyByteBuf b){b.writeBoolean(payload.query);b.writeResourceLocation(payload.dimension);b.writeVarInt(payload.id);b.writeUUID(payload.uuid);b.writeLong(payload.ownerRevision);b.writeUUID(payload.view);b.writeUUID(epoch);b.writeUUID(session);b.writeLong(sequence);}
        public void handle(Supplier<NetworkEvent.Context> ctx){var n=ctx.get();n.setPacketHandled(true);ServerPlayer p=n.getSender();if(p==null||!p.level().dimension().location().equals(payload.dimension))return;
            var found=p.serverLevel().getEntity(payload.id);TerrorbeakUnit source=found instanceof TerrorbeakUnit u&&u.getUUID().equals(payload.uuid)&&u.getOwnerName().equals(p.getGameProfile().getName())?u:null;
            if(payload.query){if(source!=null)send(p,n.getNetworkManager(),source,payload,"ready",0);return;}
            var fingerprint=new EconomyActionGate.Fingerprint("terrorbeak_upgrade",payload);
            String result=EconomyActionGate.execute(p,n.getNetworkManager(),session,epoch,sequence,fingerprint,account->{
                if(source==null)return "invalid_source";
                var request=new TerrorbeakUpgradeRequest(1,payload.view,new TerrorbeakUpgradeRequest.Source(payload.id,payload.uuid,payload.ownerRevision,payload.dimension),TerrorbeakUpgradeRequest.UpgradeTarget.CRAZY_TERRORBEAK);
                return TerrorbeakUpgradeService.request(p,request).name().toLowerCase(Locale.ROOT);
            });
            // Completion response retains the original source/view even after that body is retired.
            send(p,n.getNetworkManager(),source,payload,result,sequence);EconomyBalanceSync.send(p);
        }
    }
    private static void send(ServerPlayer actor,Connection connection,TerrorbeakUnit source,Payload payload,String status,long request){
        var token=EconomyActionGate.query(actor,connection);if(token==null)return;long order=ResearchQueueSync.nextSequence();if(order<0)return;
        long owner=source==null?-1:source.rts().ownershipRevision;
        boolean available=source!=null&&TerrorbeakUpgradeService.mayOffer(actor,source);
        CHANNEL.send(PacketDistributor.PLAYER.with(()->actor),new Snapshot(ResearchQueueSync.snapshotSession(),order,token.session(),token.epoch(),
            actor.getGameProfile().getName(),payload.dimension,payload.id,payload.uuid,payload.view,owner,available,status,request));
        EconomyBalanceSync.send(actor);
    }
    public record Snapshot(UUID stream,long order,UUID session,UUID epoch,String owner,ResourceLocation dimension,int id,UUID uuid,UUID view,long ownerRevision,boolean available,String status,long request){
        public Snapshot(FriendlyByteBuf b){this(b.readUUID(),b.readLong(),b.readUUID(),b.readUUID(),b.readUtf(256),b.readResourceLocation(),b.readVarInt(),b.readUUID(),b.readUUID(),b.readLong(),b.readBoolean(),b.readUtf(96),b.readLong());}
        public void encode(FriendlyByteBuf b){b.writeUUID(stream);b.writeLong(order);b.writeUUID(session);b.writeUUID(epoch);b.writeUtf(owner,256);b.writeResourceLocation(dimension);b.writeVarInt(id);b.writeUUID(uuid);b.writeUUID(view);b.writeLong(ownerRevision);b.writeBoolean(available);b.writeUtf(status,96);b.writeLong(request);}
        public void handle(Supplier<NetworkEvent.Context> ctx){ctx.get().setPacketHandled(true);var connection=ctx.get().getNetworkManager();net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->TerrorbeakUpgradeClient.accept(this,connection));}
    }
}
