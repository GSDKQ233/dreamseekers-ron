package com.solegendary.reignofnether.research.economy;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.shared.PassiveOreBuilding;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.ExtendedResourcesSync;
import com.solegendary.reignofnether.registrars.PacketHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import java.util.*;
import java.util.function.Supplier;
public record PiglinEconomyStatusPacket(UUID stream,long order,UUID session,UUID epoch,String owner,ResourceLocation dimension,BlockPos origin,UUID instance,long capture,int boost,int cooldown,int exchangeCooldown,boolean researched,boolean active,String status,long request,UUID view) {
    public PiglinEconomyStatusPacket(FriendlyByteBuf b){this(b.readUUID(),b.readLong(),b.readUUID(),b.readUUID(),b.readUtf(256),b.readResourceLocation(),b.readBlockPos(),b.readUUID(),b.readLong(),b.readInt(),b.readInt(),b.readInt(),b.readBoolean(),b.readBoolean(),b.readUtf(96),b.readLong(),b.readUUID());}
    public void encode(FriendlyByteBuf b){b.writeUUID(stream);b.writeLong(order);b.writeUUID(session);b.writeUUID(epoch);b.writeUtf(owner,256);b.writeResourceLocation(dimension);b.writeBlockPos(origin);b.writeUUID(instance);b.writeLong(capture);b.writeInt(boost);b.writeInt(cooldown);b.writeInt(exchangeCooldown);b.writeBoolean(researched);b.writeBoolean(active);b.writeUtf(status,96);b.writeLong(request);b.writeUUID(view);}
    public static void send(ServerPlayer actor,Connection connection,BuildingPlacement p,String status,long request,UUID view) {
        if(!PiglinEconomyService.owns(actor,p))return;
        var token=EconomyActionGate.query(actor,connection);if(token==null)return;
        var account=ExtendedResourcesSync.account(p.ownerName);if(account==null)return;
        long order=ResearchQueueSync.nextSequence();if(order<0)return;
        var t=PassiveOreBuilding.traitState(p);
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(()->actor),new PiglinEconomyStatusPacket(ResearchQueueSync.snapshotSession(),order,token.session(),token.epoch(),p.ownerName,p.level.dimension().location(),p.originPos,ResearchPlacementIdentity.serverIdentity(p),p.getCaptureRevision(),t.getOverloadTicks(),t.getCooldownTicks(),PiglinEconomyService.exchangeRemaining(account,actor.getServer().overworld().getGameTime()),ResearchServerEvents.playerHasResearch(p.ownerName,CoreEconomyResearch.id("research_instant_smelting")),PassiveOreBuilding.isProductionActive(p),status,request,view));
        EconomyBalanceSync.send(actor);
    }
    public void handle(Supplier<NetworkEvent.Context> context){var c=context.get().getNetworkManager();context.get().enqueueWork(()->PiglinEconomyClient.accept(this,c));context.get().setPacketHandled(true);}
}
