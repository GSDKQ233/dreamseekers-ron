package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import java.util.UUID;
import java.util.function.Supplier;

public record ResearchEconomyServerboundPacket(Action action, ResourceLocation dimension, BlockPos origin,
        ResourceLocation item, UUID receipt, UUID instance, long capture, UUID epoch, UUID session, long sequence) {
    public enum Action { QUERY, START, CANCEL }
    public static final UUID ZERO=new UUID(0,0);
    public record Payload(Action action,ResourceLocation dimension,BlockPos origin,ResourceLocation item,
                          UUID receipt,UUID instance,long capture) {}
    public ResearchEconomyServerboundPacket(FriendlyByteBuf b) {
        this(b.readEnum(Action.class),b.readResourceLocation(),b.readBlockPos(),b.readResourceLocation(),
             b.readUUID(),b.readUUID(),b.readLong(),b.readUUID(),b.readUUID(),b.readLong());
    }
    public void encode(FriendlyByteBuf b) {
        b.writeEnum(action); b.writeResourceLocation(dimension); b.writeBlockPos(origin); b.writeResourceLocation(item);
        b.writeUUID(receipt); b.writeUUID(instance); b.writeLong(capture); b.writeUUID(epoch); b.writeUUID(session); b.writeLong(sequence);
    }
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        var actor=ctx.get().getSender();var connection=ctx.get().getNetworkManager();
        ctx.get().enqueueWork(()-> {
            if(actor==null)return;
            ProductionPlacement selected=null;
            for(var building:BuildingServerEvents.getBuildings())
                if(building instanceof ProductionPlacement candidate && candidate.originPos.equals(origin)
                    && candidate.getLevel().dimension().location().equals(dimension)) { selected=candidate; break; }
            final ProductionPlacement p=selected;
            if(action==Action.QUERY) {
                if(p!=null && ReceiptResearchService.owns(actor,p))ResearchQueueSync.send(actor,connection,p,"ready",0);
                return; // failed query discloses neither session nor account
            }
            var payload=new EconomyActionGate.Fingerprint("research",new Payload(action,dimension,origin,item,receipt,instance,capture));
            String status=EconomyActionGate.execute(actor,connection,session,epoch,sequence,payload,account->{
                if(p==null||!ReceiptResearchService.owns(actor,p))return "forbidden";
                if(action==Action.START) {
                    var production=ReignOfNetherRegistries.PRODUCTION_ITEM.get(item);
                    return production instanceof ReceiptBackedResearchItem research
                        ? ReceiptResearchService.start(actor,p,research,instance,capture,epoch,account.revision):"forbidden";
                }
                return ReceiptResearchService.cancel(actor,p,receipt,instance,capture,epoch,account.revision);
            });
            if(p!=null&&ReceiptResearchService.owns(actor,p))ResearchQueueSync.send(actor,connection,p,status,sequence);
            else EconomyActionResultPacket.send(actor,connection,status,sequence);
        });
        ctx.get().setPacketHandled(true);
    }
}
