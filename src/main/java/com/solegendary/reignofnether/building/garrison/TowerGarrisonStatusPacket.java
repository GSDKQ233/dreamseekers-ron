package com.solegendary.reignofnether.building.garrison;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.research.economy.*;
import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import java.util.*;
import java.util.function.Supplier;

/** Aggregate private management view; allies' identities and orders are never transmitted. */
public record TowerGarrisonStatusPacket(UUID stream,long order,UUID session,UUID epoch,String viewer,String owner,
        ResourceLocation dimension,BlockPos origin,UUID view,UUID instance,long capture,
        int capacity,int occupied,int mine,int approaching,String status,long request) {
    public TowerGarrisonStatusPacket(FriendlyByteBuf b) {
        this(b.readUUID(),b.readLong(),b.readUUID(),b.readUUID(),b.readUtf(256),b.readUtf(256),b.readResourceLocation(),
            b.readBlockPos(),b.readUUID(),b.readUUID(),b.readLong(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readUtf(96),b.readLong());
    }
    public void encode(FriendlyByteBuf b) {
        b.writeUUID(stream);b.writeLong(order);b.writeUUID(session);b.writeUUID(epoch);b.writeUtf(viewer,256);b.writeUtf(owner,256);
        b.writeResourceLocation(dimension);b.writeBlockPos(origin);b.writeUUID(view);b.writeUUID(instance);b.writeLong(capture);
        b.writeVarInt(capacity);b.writeVarInt(occupied);b.writeVarInt(mine);b.writeVarInt(approaching);b.writeUtf(status,96);b.writeLong(request);
    }
    public boolean validOccupancy() {
        return capture>=0&&capacity==3&&occupied>=0&&occupied<=4096&&mine>=0&&mine<=occupied
            &&approaching>=0&&approaching<=4096;
    }
    public static void send(ServerPlayer actor,Connection connection,BuildingPlacement tower,UUID view,String status,long request) {
        if(!TowerGarrisonService.accessible(actor,tower))return;
        var token=EconomyActionGate.query(actor,connection);if(token==null)return;
        long order=ResearchQueueSync.nextSequence();if(order<0)return;
        var counts=TowerGarrisonService.counts(tower,actor.getName().getString());
        var packet=new TowerGarrisonStatusPacket(ResearchQueueSync.snapshotSession(),order,token.session(),token.epoch(),
            actor.getName().getString(),tower.ownerName,tower.level.dimension().location(),tower.originPos,view,
            ResearchPlacementIdentity.serverIdentity(tower),tower.getCaptureRevision(),3,
            counts.occupied(),counts.mine(),counts.approaching(),status,request);
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(()->actor),packet);EconomyBalanceSync.send(actor);
    }
    public void handle(Supplier<NetworkEvent.Context> context) {
        var connection=context.get().getNetworkManager();
        context.get().enqueueWork(()->TowerGarrisonClient.accept(this,connection));context.get().setPacketHandled(true);
    }
}
