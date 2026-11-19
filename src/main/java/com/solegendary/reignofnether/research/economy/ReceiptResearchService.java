package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.production.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Actor checks and ledger mutations run in the same server task. No client-supplied price. */
public final class ReceiptResearchService {
    public static final String PURPOSE="research/";
    private ReceiptResearchService() {}
    public static boolean owns(ServerPlayer actor, ProductionPlacement p) {
        return actor!=null && actor.getServer().isSameThread() && actor.serverLevel()==p.getLevel()
            && actor.getName().getString().equals(p.ownerName)
            && PlayerServerEvents.getRTSPlayer(p.ownerName)!=null
            && BuildingServerEvents.getBuildings().contains(p) && !p.isDestroyedServerside
            && p.getHealth()>0 && !p.shouldBeDestroyed();
    }
    public static boolean bindingValid(ProductionPlacement p, ActiveProduction a) {
        var b=a.receiptResearch;
        if(b==null || !(a.item instanceof ReceiptBackedResearchItem item)
                || !p.ownerName.equals(b.payer()) || p.getCaptureRevision()<0
                || p.getCaptureRevision()!=b.captureRevision()
                || !ResearchPlacementIdentity.serverIdentity(p).equals(b.placementInstance())) return false;
        Resources r=ExtendedResourcesSync.account(b.payer());
        if(r==null || !r.accountEpoch.equals(b.epoch()) || PlayerServerEvents.getRTSPlayer(b.payer())==null) return false;
        var receipt=r.paidReceipts.get(b.receiptId());
        return receipt!=null && receipt.payerEpoch().equals(b.epoch()) && receipt.purpose().equals(PURPOSE+item.id());
    }
    public static String start(ServerPlayer actor, ProductionPlacement p, ReceiptBackedResearchItem item,
            UUID instance, long capture, UUID epoch, long revision) {
        if(!owns(actor,p) || !p.isBuilt || p.getCaptureRevision()<0
                || !ResearchPlacementIdentity.serverIdentity(p).equals(instance)
                || p.getCaptureRevision()!=capture || !item.validHostAndPrerequisite(p)) return "forbidden";
        if(item.hasCompleted(p.ownerName) || item.itemIsBeingProduced(false,p.ownerName)) return "duplicate";
        if(p.productionQueue.size()>=ResearchQueueClientboundPacket.MAX_QUEUE) return "queue_full";
        Resources r=ExtendedResourcesSync.account(p.ownerName);
        // Construct before paying; no callbacks after debit and before queue attachment.
        ActiveProduction a=new ActiveProduction(item,false,p.ownerName);
        if(!Float.isFinite(a.ticksLeft) || a.ticksLeft<=0) return "invalid_duration";
        var paid=ResourceTransaction.pay(actor.getServer(),r,epoch,revision,PURPOSE+item.id(),item.quote(p));
        if(!paid.applied()) return paid.status().name().toLowerCase(Locale.ROOT);
        a.receiptResearch=new ReceiptResearchBinding(paid.receiptId(),p.ownerName,epoch,instance,capture);
        p.productionQueue.add(a);
        return "started";
    }
    public static String cancel(ServerPlayer actor, ProductionPlacement p, UUID receipt,
            UUID instance, long capture, UUID epoch, long revision) {
        if(!owns(actor,p) || !ResearchPlacementIdentity.serverIdentity(p).equals(instance)
                || p.getCaptureRevision()!=capture) return "forbidden";
        Resources r=ExtendedResourcesSync.account(p.ownerName);
        if(r==null || !r.accountEpoch.equals(epoch) || r.revision!=revision) return "stale_account";
        for(var a:List.copyOf(p.productionQueue)) {
            var b=a.receiptResearch;
            if(b!=null && b.receiptId().equals(receipt) && b.payer().equals(p.ownerName)
                    && b.epoch().equals(epoch) && b.placementInstance().equals(instance)
                    && b.captureRevision()==capture) return detachAndRefund(p,a);
        }
        return "unknown_receipt";
    }
    public static String detachAndRefund(ProductionPlacement p, ActiveProduction a) {
        if(!p.productionQueue.remove(a)) return "unknown_receipt";
        var b=a.receiptResearch;
        if(b==null) return "invalid_binding";
        Resources r=ExtendedResourcesSync.account(b.payer());
        if(r==null || !r.accountEpoch.equals(b.epoch())) return "refund_pending_account";
        var receipt=r.paidReceipts.get(b.receiptId());
        if(receipt==null || !recognized(receipt)) return "refund_pending_definition";
        var result=ResourceTransaction.refund(p.getLevel().getServer(),r,b.epoch(),r.revision,b.receiptId(),10000);
        String status=result.applied()?"cancelled_refunded":"refund_pending_"+result.status().name().toLowerCase(Locale.ROOT);
        ResearchQueueSync.notifyOwner(p.getLevel().getServer(),b.payer(),status);
        return status;
    }
    public static boolean complete(ProductionPlacement p, ActiveProduction a) {
        if(!bindingValid(p,a)) return false;
        var b=a.receiptResearch; var item=(ReceiptBackedResearchItem)a.item;
        if(!p.isBuilt||p.getHealth()<=0||p.shouldBeDestroyed()||!item.validHostAndPrerequisite(p))return false;
        Resources r=ExtendedResourcesSync.account(b.payer());
        if(item.hasCompleted(b.payer())||!item.acceptsPaidCompletion(p,a)) return false;
        var result=ResourceTransaction.consumeReceipt(p.getLevel().getServer(),r,b.epoch(),r.revision,b.receiptId());
        if(!result.applied()) return false;
        a.completed=true;
        item.commitPaidCompletion(p,a,r);
        item.publishPaidCompletion(p,a);
        return true;
    }
    public static void validateQueued(ProductionPlacement p) {
        for(var a:List.copyOf(p.productionQueue)) if(a.receiptResearch!=null && (!bindingValid(p,a)
                || a.item instanceof ReceiptBackedResearchItem item && item.isQueuedPurchaseObsolete(p,a))) detachAndRefund(p,a);
    }
    public static boolean recognized(ResourcePaidReceipt receipt) {
        if(!receipt.purpose().startsWith(PURPOSE)) return false;
        ResourceLocation id=ResourceLocation.tryParse(receipt.purpose().substring(PURPOSE.length()));
        return id!=null && ReignOfNetherRegistries.PRODUCTION_ITEM.get(id) instanceof ReceiptBackedResearchItem;
    }
    public static void projectResearchRefunds(Resources copy) {
        for(var receipt:List.copyOf(copy.paidReceipts.values()))
            if(recognized(receipt)) ResearchRefundProjection.refund(copy,receipt);
    }
    public static void retryDetachedRefunds(MinecraftServer server) {
        Set<UUID> active=new HashSet<>();
        for(var building:BuildingServerEvents.getBuildings()) if(building instanceof ProductionPlacement p)
            for(var a:p.productionQueue) if(a.receiptResearch!=null) active.add(a.receiptResearch.receiptId());
        for(var r:List.copyOf(ResourcesServerEvents.resourcesList))
            for(var receipt:List.copyOf(r.paidReceipts.values()))
                if(recognized(receipt) && !active.contains(receipt.id())
                        && ResourceTransaction.refund(server,r,r.accountEpoch,r.revision,receipt.id(),10000).applied())
                    ResearchQueueSync.notifyOwner(server,r.ownerName,"cancelled_refunded");
    }
}
