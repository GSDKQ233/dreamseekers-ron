package com.solegendary.reignofnether.research.economy;
import com.solegendary.reignofnether.building.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;
public record PiglinEconomyServerboundPacket(Action action,ResourceLocation dimension,BlockPos origin,UUID instance,long capture,UUID epoch,UUID session,long sequence,UUID view) {
    public enum Action { QUERY, OVERLOAD, EXCHANGE }
    public record Payload(Action action,ResourceLocation dimension,BlockPos origin,UUID instance,long capture,UUID view){}
    public PiglinEconomyServerboundPacket(FriendlyByteBuf b){this(b.readEnum(Action.class),b.readResourceLocation(),b.readBlockPos(),b.readUUID(),b.readLong(),b.readUUID(),b.readUUID(),b.readLong(),b.readUUID());}
    public void encode(FriendlyByteBuf b){b.writeEnum(action);b.writeResourceLocation(dimension);b.writeBlockPos(origin);b.writeUUID(instance);b.writeLong(capture);b.writeUUID(epoch);b.writeUUID(session);b.writeLong(sequence);b.writeUUID(view);}
    public void handle(Supplier<NetworkEvent.Context> context){
        var actor=context.get().getSender();var connection=context.get().getNetworkManager();
        context.get().enqueueWork(()->{
            if(actor==null)return;
            BuildingPlacement p=BuildingServerEvents.getBuildings().stream().filter(b->b.originPos.equals(origin)&&b.level.dimension().location().equals(dimension)).findFirst().orElse(null);
            if(action==Action.QUERY){if(PiglinEconomyService.owns(actor,p))PiglinEconomyStatusPacket.send(actor,connection,p,"ready",0,view);return;}
            var fingerprint=new EconomyActionGate.Fingerprint("piglin_mine",new Payload(action,dimension,origin,instance,capture,view));
            String status=EconomyActionGate.execute(actor,connection,session,epoch,sequence,fingerprint,a->PiglinEconomyService.act(actor,p,a,action,instance,capture));
            if(PiglinEconomyService.owns(actor,p))PiglinEconomyStatusPacket.send(actor,connection,p,status,sequence,view);
            else EconomyActionResultPacket.send(actor,connection,status,sequence);
        });context.get().setPacketHandled(true);
    }
}
