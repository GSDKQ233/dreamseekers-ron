package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.resources.ExtendedResourcesSync;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.UUID;
import java.util.function.Supplier;

/** Owner-private action outcome when its former building no longer exists. */
public record EconomyActionResultPacket(UUID stream,long order,UUID session,UUID epoch,String owner,long request,String status) {
    public EconomyActionResultPacket(FriendlyByteBuf b){this(b.readUUID(),b.readLong(),b.readUUID(),b.readUUID(),b.readUtf(256),b.readLong(),b.readUtf(96));}
    public void encode(FriendlyByteBuf b){b.writeUUID(stream);b.writeLong(order);b.writeUUID(session);b.writeUUID(epoch);b.writeUtf(owner,256);b.writeLong(request);b.writeUtf(status,96);}
    public static void send(ServerPlayer actor,Connection connection,String status,long request) {
        var token=EconomyActionGate.query(actor,connection);if(token==null)return;
        long order=ResearchQueueSync.nextSequence();if(order<0)return;
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(()->actor),new EconomyActionResultPacket(
            ResearchQueueSync.snapshotSession(),order,token.session(),token.epoch(),actor.getName().getString(),request,status));
        EconomyBalanceSync.send(actor);
    }
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        var connection=ctx.get().getNetworkManager();
        ctx.get().enqueueWork(()->ResearchQueueClient.acceptResult(this,connection));ctx.get().setPacketHandled(true);
    }
}
