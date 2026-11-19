// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.production;

import com.dreamseekers.DreamseekersMod;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.production.*;
import com.solegendary.reignofnether.research.economy.*;
import com.solegendary.reignofnether.resources.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Ledger is durable refund authority; binding is only a runtime queue/placement association.
 * No queue is persisted. Save projection refunds exact original payment on the detached account copy. */
@Mod.EventBusSubscriber(modid="dreamseekers")
public final class DreamUnitReceipts {
    public static final String PREFIX="dreamseekers:unit/";
    public record Binding(UUID receipt, String payer, UUID epoch, UUID placement, long capture, long ownerRevision, long bindingRevision, int work) {}
    private DreamUnitReceipts() {}
    public static boolean supported(ProductionItem item) {
        if(!(item instanceof DreamUnitProduction)||item==DreamseekersMod.LEGACY_WORKER_PRODUCTION
                ||DreamseekersMod.DREAM_ANCHOR==null||DreamseekersMod.BEETLE_SANCTUARY==null||DreamseekersMod.DREAM_HALL==null)return false;
        return DreamseekersMod.DREAM_ANCHOR.productions.get().contains(item)
            ||DreamseekersMod.BEETLE_SANCTUARY.productions.get().contains(item)
            ||DreamseekersMod.DREAM_HALL.productions.get().contains(item);
    }
    private static String purpose(ProductionItem item) {
        ResourceLocation id=ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(item);
        return id==null?"":PREFIX+id;
    }
    public static boolean recognized(ResourcePaidReceipt receipt) {
        if(!receipt.purpose().startsWith(PREFIX))return false;
        ResourceLocation id=ResourceLocation.tryParse(receipt.purpose().substring(PREFIX.length()));
        return id!=null&&supported(ReignOfNetherRegistries.PRODUCTION_ITEM.get(id));
    }
    public record Payment(Binding binding, String refusal) {
        static Payment refused(String key) { return new Payment(null, key); }
    }

    private static String refusal(ResourceTransaction.Status status, Resources balance, ResourceTransaction.Cost quote) {
        if (status == ResourceTransaction.Status.INSUFFICIENT_RESOURCES) {
            // Inspect the exact rejected debit on the same server thread. Do not re-quote after rejection.
            if (balance.food < quote.food()) return "server.resources.reignofnether.not_enough_food";
            if (balance.wood < quote.wood()) return "server.resources.reignofnether.not_enough_wood";
            if (balance.ore < quote.ore()) return "server.resources.reignofnether.not_enough_ore";
            if (balance.emerald < quote.emerald()) return "server.resources.reignofnether.not_enough_emerald";
            var shadow = com.dreamseekers.economy.DreamPassiveEconomy.SHADOW;
            if (balance.extra.getOrDefault(shadow,0) < quote.extra().getOrDefault(shadow,0))
                return "dreamseekers.production.not_enough_shadow";
            return "research.economy.insufficient_resources";
        }
        return switch (status) {
            case INVALID_ACCOUNT -> "research.economy.invalid_account";
            case STALE_ACCOUNT -> "research.economy.stale_account";
            case RECEIPT_LIMIT -> "research.economy.receipt_limit";
            case UNKNOWN_EXTENSION -> "research.economy.unknown_extension";
            case INVALID_VALUE -> "research.economy.invalid_value";
            default -> "dreamseekers.production.payment_unavailable";
        };
    }

    public static Binding pay(DreamProductionPlacement host, ProductionItem item) {
        return reserve(host, item).binding();
    }

    public static Payment reserve(DreamProductionPlacement host, ProductionItem item) {
        if(!supported(item))return Payment.refused("dreamseekers.error.wrong_producer");
        Resources r=ExtendedResourcesSync.account(host.ownerName);
        if(r==null)return Payment.refused("research.economy.invalid_account");
        ResourceCost c=item.getCost(false,host.ownerName);
        if(c.ticks<=0||c.population<0)return Payment.refused("research.economy.invalid_value");
        long bindingRevision=host.unitBindingRevision();
        UUID instance=host.unitPlacementIdentity();
        long ownerRevision=host.unitOwnerRevision();
        if(instance==null||ownerRevision<0||bindingRevision<0||host.getCaptureRevision()<0)
            return Payment.refused("dreamseekers.error.unavailable_producer");
        var quote = new ResourceTransaction.Cost(c.food,c.wood,c.ore,c.emerald,
                ((DreamUnitProduction)item).extraCost(false,host.ownerName));
        var result=ResourceTransaction.pay(host.getLevel().getServer(),r,r.accountEpoch,r.revision,purpose(item),quote);
        if(!result.applied())return Payment.refused(refusal(result.status(),r,quote));
        return new Payment(new Binding(result.receiptId(),host.ownerName,r.accountEpoch,instance,host.getCaptureRevision(),
                ownerRevision,bindingRevision,c.ticks),null);
    }
    private static boolean validClaim(DreamProductionPlacement host,ActiveProduction active,Binding b) {
        if(b==null)return false;
        // Observe the current owner before any short-circuit, including opaque A -> B -> A.
        long revision=host.unitBindingRevision();
        if(revision<0||revision!=b.bindingRevision||host.getCaptureRevision()<0
                ||!b.payer.equals(host.ownerName)||host.getCaptureRevision()!=b.capture)return false;
        Resources r=ExtendedResourcesSync.account(b.payer);
        if(r==null||!r.accountEpoch.equals(b.epoch))return false;
        ResourcePaidReceipt receipt=r.paidReceipts.get(b.receipt);
        return receipt!=null&&receipt.payerEpoch().equals(b.epoch)&&receipt.purpose().equals(purpose(active.item));
    }
    public static boolean valid(DreamProductionPlacement host,ActiveProduction active,Binding b) {
        return validClaim(host,active,b)&&Objects.equals(host.unitPlacementIdentity(),b.placement)
                &&host.unitOwnerRevision()==b.ownerRevision;
    }
    static boolean retainedWhileOpaque(DreamProductionPlacement host,ActiveProduction active,Binding b) {
        // Called only for the exact IdentityHashMap binding still in its original queue.
        return validClaim(host,active,b)&&host.quarantinesUnitBinding(b);
    }
    public static boolean consume(DreamProductionPlacement host,ActiveProduction active,Binding b) {
        if(!valid(host,active,b))return false;
        Resources r=ExtendedResourcesSync.account(b.payer);
        return ResourceTransaction.consumeReceipt(host.getLevel().getServer(),r,b.epoch,r.revision,b.receipt).applied();
    }
    public static boolean refund(MinecraftServer server,Binding b) {
        Resources r=ExtendedResourcesSync.account(b.payer);
        if(r==null||!r.accountEpoch.equals(b.epoch))return false;
        ResourcePaidReceipt receipt=r.paidReceipts.get(b.receipt);
        if(receipt==null)return true;
        if(!recognized(receipt))return false;
        boolean applied=ResourceTransaction.refund(server,r,b.epoch,r.revision,b.receipt,10000).applied();
        if(applied)sync(server,b.payer);return applied;
    }
    public static void sync(MinecraftServer server,String owner) {
        var actor=server.getPlayerList().getPlayerByName(owner);
        if(actor!=null)EconomyBalanceSync.send(actor);
    }
    @SubscribeEvent public static void projectSavedUnitRefunds(ResourceSaveProjectionEvent event) {
        for(var receipt:List.copyOf(event.copy.paidReceipts.values()))
            if(recognized(receipt))ResearchRefundProjection.refund(event.copy,receipt);
    }
    @SubscribeEvent public static void retryDetachedUnitRefunds(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||event.getServer().getTickCount()%20!=0)return;
        Set<UUID> attached=new HashSet<>();
        for(var building:BuildingServerEvents.getBuildings())if(building instanceof DreamProductionPlacement host)
            attached.addAll(host.attachedUnitReceipts());
        for(var r:List.copyOf(ResourcesServerEvents.resourcesList))for(var receipt:List.copyOf(r.paidReceipts.values())) {
            if(!recognized(receipt)||attached.contains(receipt.id())||!receipt.payerEpoch().equals(r.accountEpoch))continue;
            if(ResourceTransaction.refund(event.getServer(),r,r.accountEpoch,r.revision,receipt.id(),10000).applied())sync(event.getServer(),r.ownerName);
        }
    }
}
