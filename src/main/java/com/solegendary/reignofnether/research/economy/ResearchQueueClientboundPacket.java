package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionPause;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.resources.Resources;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

/** Private complete queue snapshot. Receipt identity is sent only to its exact owner. */
public final class ResearchQueueClientboundPacket {
    public static final int MAX_QUEUE=4096;
    public record Entry(ResourceLocation item,float ticks,UUID receipt,UUID queueEntryId) {}
    public final UUID session,epoch,instance,actionSession;
    public final long requestSequence;
    public final long sequence,revision,capture;
    public final String owner,status;
    public final ResourceLocation dimension;
    public final BlockPos origin;
    public final List<Entry> entries;
    public final ProductionPause headPause;
    public ResearchQueueClientboundPacket(UUID session,long sequence,String status,ProductionPlacement p,Resources r,EconomyActionGate.Token token,long requestSequence) {
        if(token==null)throw new IllegalArgumentException("Missing action session");
        actionSession=token.session();this.requestSequence=requestSequence;
        this.session=session; this.sequence=sequence; this.status=status;
        owner=p.ownerName; epoch=r.accountEpoch; revision=r.revision;
        dimension=p.getLevel().dimension().location(); origin=p.originPos;
        instance=ResearchPlacementIdentity.serverIdentity(p); capture=p.getCaptureRevision();
        List<Entry> entries=new ArrayList<>();
        for(var active:p.productionQueue) {
            ResourceLocation id=ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(active.item);
            // Unknown/custom entries cannot be reconstructed safely: do not partially replace this queue.
            if(id==null || !Float.isFinite(active.ticksLeft) || active.ticksLeft<0) throw new IllegalArgumentException("Unrepresentable queue");
            entries.add(new Entry(id,active.ticksLeft,active.receiptResearch==null?null:active.receiptResearch.receiptId(),active.queueEntryId));
        }
        if(entries.size()>MAX_QUEUE) throw new IllegalArgumentException("Queue too large");
        this.entries=List.copyOf(entries);
        this.headPause=p.productionQueue.isEmpty()?ProductionPause.INITIAL:p.productionQueue.get(0).pause();
    }
    public ResearchQueueClientboundPacket(FriendlyByteBuf b) {
        session=b.readUUID();sequence=b.readLong();actionSession=b.readUUID();requestSequence=b.readLong();owner=b.readUtf(256);status=b.readUtf(96);
        epoch=b.readUUID();revision=b.readLong();dimension=b.readResourceLocation();origin=b.readBlockPos();
        instance=b.readUUID();capture=b.readLong();
        int count=b.readVarInt();
        if(sequence<0||revision<0||capture<0||count<0||count>MAX_QUEUE) throw new IllegalArgumentException("Bad research snapshot");
        List<Entry> entries=new ArrayList<>(); Set<UUID> ids=new HashSet<>(),queueIds=new HashSet<>();
        for(int i=0;i<count;i++) {
            var id=b.readResourceLocation();float ticks=b.readFloat();UUID receipt=b.readBoolean()?b.readUUID():null;UUID queueId=b.readUUID();
            if(!Float.isFinite(ticks)||ticks<0||(receipt!=null&&!ids.add(receipt))||!queueIds.add(queueId)) throw new IllegalArgumentException("Bad queue entry");
            entries.add(new Entry(id,ticks,receipt,queueId));
        }
        this.entries=List.copyOf(entries);
        headPause=ProductionPause.decode(b);
        if(entries.isEmpty()&&headPause.revision()!=0)throw new IllegalArgumentException("State has no queue head");
    }
    public void encode(FriendlyByteBuf b) {
        b.writeUUID(session);b.writeLong(sequence);b.writeUUID(actionSession);b.writeLong(requestSequence);b.writeUtf(owner,256);b.writeUtf(status,96);
        b.writeUUID(epoch);b.writeLong(revision);b.writeResourceLocation(dimension);b.writeBlockPos(origin);
        b.writeUUID(instance);b.writeLong(capture);b.writeVarInt(entries.size());
        for(var e:entries) { b.writeResourceLocation(e.item());b.writeFloat(e.ticks());b.writeBoolean(e.receipt()!=null);if(e.receipt()!=null)b.writeUUID(e.receipt());b.writeUUID(e.queueEntryId()); }
        headPause.encode(b);
    }
    public void handle(Supplier<NetworkEvent.Context> ctx) {
        var connection=ctx.get().getNetworkManager();
        ctx.get().enqueueWork(()->ResearchQueueClient.accept(this,connection));ctx.get().setPacketHandled(true);
    }
}
