// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.economy;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.building.DreamTemplateModel;
import com.dreamseekers.building.DreamTemplateModel.LocalTechnology;
import com.dreamseekers.function.DreamFunctionalPlacement;
import com.solegendary.reignofnether.building.BuildingClientboundPacket;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.research.economy.EconomyBalanceSync;
import com.solegendary.reignofnether.research.economy.ResearchRefundProjection;
import com.solegendary.reignofnether.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * One paid local technology at an actual producer or Resonance Dais, not a player research.
 * These are farms and functional buildings, not RoN unit-production placements. Keep their original
 * types, workers, and lifecycle; use RoN's receipt and save projection rather than a parallel wallet.
 * Live work is not serialized (normal queue restart policy). Only the completed physical tier is.
 */
@Mod.EventBusSubscriber(modid="dreamseekers")
public final class ResourceBuildingResearch {
    public static final int WOOD=120, ORE=80, DURATION=600;
    public static final ResourceTransaction.Cost COST=new ResourceTransaction.Cost(0,WOOD,ORE);
    public static final int DAIS_BASE_SUPPLY=15, DAIS_UPGRADED_SUPPLY=30;
    private static final String PRODUCTION_PURPOSE="dreamseekers:production_methods/";
    private static final String DAIS_PURPOSE="dreamseekers:dais_resonance/";
    private static String purpose(LocalTechnology kind) {
        return switch(kind) {
            case PRODUCTION_METHODS -> PRODUCTION_PURPOSE;
            case DAIS_RESONANCE -> DAIS_PURPOSE;
        };
    }
    public record View(UUID instance, long capture, long revision, int remaining, boolean upgraded) {}
    private record Claim(String payer, UUID epoch, UUID instance, long capture, long ownerGeneration,
                         LocalTechnology technology, UUID receipt) {}
    /** Capability created only after successful server-side consumption; no packet, snapshot, or caller can mint it. */
    public static final class PaidCompletion {
        private final DreamTemplateModel.State target;
        private final LocalTechnology technology;
        private PaidCompletion(DreamTemplateModel.State target, LocalTechnology technology) {
            this.target=target;this.technology=technology;
        }
        public boolean authorizes(DreamTemplateModel.State model, LocalTechnology kind) {
            return target==model && technology==kind;
        }
    }
    private static final class Slot {
        long revision, lastTick=Long.MIN_VALUE;
        Claim claim;
        int remaining;
    }
    // Server thread only. Re-observing a restored object never reuses the previous live revision,
    // even if the physical building UUID was legitimately restored at the same origin.
    private static final Map<BuildingPlacement,Slot> SLOTS=new WeakHashMap<>();
    private static final Set<BuildingPlacement> TERMINAL=Collections.newSetFromMap(new WeakHashMap<>());
    private static long serial;
    private ResourceBuildingResearch() {}
    private static long nextRevision() { return serial==Long.MAX_VALUE ? -1 : ++serial; }

    public static boolean isDais(BuildingPlacement p) {
        return p instanceof DreamFunctionalPlacement && p.getBuilding()==DreamseekersMod.RESONANCE_DAIS;
    }
    public static boolean supports(BuildingPlacement p) { return model(p)!=null; }
    private static DreamTemplateModel.State model(BuildingPlacement p) {
        if(p==null)return null;
        boolean dais=isDais(p);
        if(!dais && DreamPassiveEconomy.legacyInterval(p.getBuilding())==0)return null;
        var state=p instanceof DreamGardenPlacement garden ? garden.resourceResearchModel()
            : p instanceof DreamFunctionalPlacement functional ? functional.resourceResearchModel() : null;
        return state!=null && state.localTechnology()==(dais ? LocalTechnology.DAIS_RESONANCE : LocalTechnology.PRODUCTION_METHODS)
                ? state : null;
    }
    public static UUID instance(BuildingPlacement p) {
        var state=model(p); return state==null?null:state.instance();
    }
    /** Client knows only the completed form on a visible, already instance-bound structure snapshot. */
    public static boolean isUpgraded(BuildingPlacement p) {
        var state=model(p); return state!=null && state.hasLocalTechnology();
    }
    private static boolean live(ServerLevel level,BuildingPlacement p) {
        return p!=null && p.getLevel()==level && level.dimension()==Level.OVERWORLD
            && level.getServer().isSameThread() && supports(p) && !TERMINAL.contains(p) && !p.isDestroyedServerside
            && BuildingServerEvents.getBuildings().contains(p);
    }
    private static boolean owns(ServerPlayer actor,BuildingPlacement p) {
        return actor!=null && live(actor.serverLevel(),p) && actor.getGameProfile().getName().equals(p.ownerName)
            && p.getCaptureRevision()>=0 && instance(p)!=null;
    }
    private static long generation(BuildingPlacement p) {
        return p instanceof DreamFunctionalPlacement functional ? functional.functionalOwnerRevision():p.getCaptureRevision();
    }
    /** Separate eligibility from ownership: a paid job remains cancellable if its producer is temporarily unavailable. */
    private static boolean available(ServerLevel level,BuildingPlacement p) {
        if(!p.isBuilt || !level.hasChunksAt(p.minCorner,p.maxCorner) || p.shouldBeDestroyed() || p.getHealth()<=0)
            return false;
        boolean dais=isDais(p);
        if(!dais && !p.getDataStorage().getData(DreamPassiveEconomy.CLOCK).supportsInterval(DreamPassiveEconomy.legacyInterval(p.getBuilding())))
            return false;
        if(p instanceof DreamGardenPlacement garden && !garden.migrationReady())return false;
        if(p instanceof DreamFunctionalPlacement functional) {
            // The shared lifecycle attaches backed state on the server. The Dais has no passive clock
            // that would otherwise have done this read before an owner query.
            if(functional.functionalOwnerRevision()<0)return false;
            var backing=functional.stateSnapshot();
            if(!backing.supported() || !p.ownerName.equals(backing.owner()) || !backing.built())return false;
        }
        var player=PlayerServerEvents.getRTSPlayer(p.ownerName);
        return dais ? DreamFactionAccess.allows(player)
            : DreamPassiveEconomy.supportsIncomeOwner(player) && DreamPassiveEconomy.factionPauseReason(p.getBuilding(),player)==null;
    }
    private static boolean sameClaimant(BuildingPlacement p,Claim c) {
        var account=ExtendedResourcesSync.account(c.payer);
        var state=model(p);
        return state!=null && c.technology==state.localTechnology() && c.instance.equals(state.instance())
            && c.capture==p.getCaptureRevision()
            && c.payer.equals(p.ownerName) && c.ownerGeneration==generation(p)
            && account!=null && c.epoch.equals(account.accountEpoch);
    }
    private static boolean matchesReceipt(ResourcePaidReceipt receipt,Claim c) {
        return receipt!=null && receipt.id().equals(c.receipt) && receipt.payerEpoch().equals(c.epoch)
            && receipt.purpose().equals(purpose(c.technology)+c.instance);
    }
    private static Slot checkedSlot(BuildingPlacement p) {
        Slot slot=SLOTS.computeIfAbsent(p,b->{var s=new Slot();s.revision=nextRevision();return s;});
        if(slot.claim!=null && !sameClaimant(p,slot.claim))
            retire(p.getLevel().getServer(),slot,true);
        return slot;
    }
    public static View view(ServerPlayer actor,BuildingPlacement p) {
        if(!owns(actor,p))return null;
        var slot=checkedSlot(p);
        if(slot.revision<0 || (slot.claim==null && !available(actor.serverLevel(),p)))return null;
        return new View(instance(p),p.getCaptureRevision(),slot.revision,slot.remaining,isUpgraded(p));
    }
    private static boolean exact(BuildingPlacement p,Slot slot,UUID id,long capture,long revision) {
        return id!=null && id.equals(instance(p)) && p.getCaptureRevision()==capture
            && slot.revision>=0 && slot.revision==revision;
    }
    public static String start(ServerPlayer actor,BuildingPlacement p,UUID id,long capture,long revision) {
        if(!owns(actor,p))return "forbidden";
        var slot=checkedSlot(p);
        if(!exact(p,slot,id,capture,revision))return "stale_building";
        if(slot.claim!=null || isUpgraded(p))return "duplicate";
        if(!available(actor.serverLevel(),p))return "forbidden";
        var account=ExtendedResourcesSync.account(p.ownerName);
        long generation=generation(p);
        if(account==null || generation<0)return "invalid_account";
        long token=nextRevision();
        if(token<0)return "stale_building";
        var technology=model(p).localTechnology();
        var result=ResourceTransaction.pay(actor.getServer(),account,account.accountEpoch,account.revision,purpose(technology)+id,COST);
        if(!result.applied())return result.status().name().toLowerCase(Locale.ROOT);
        slot.claim=new Claim(p.ownerName,account.accountEpoch,id,capture,generation,technology,result.receiptId());
        slot.lastTick=actor.serverLevel().getGameTime();slot.remaining=DURATION;slot.revision=token;
        return "started";
    }
    public static String cancel(ServerPlayer actor,BuildingPlacement p,UUID id,long capture,long revision) {
        if(!owns(actor,p))return "forbidden";
        var slot=checkedSlot(p);
        if(!exact(p,slot,id,capture,revision) || slot.claim==null)return "stale_building";
        return retire(actor.getServer(),slot,false)?"cancelled_refunded":"refund_pending";
    }
    private static void close(Slot slot) {
        slot.claim=null;slot.remaining=0;slot.revision=nextRevision();
    }
    private static void sync(MinecraftServer server,String payer) {
        var player=server.getPlayerList().getPlayerByName(payer);
        if(player!=null)EconomyBalanceSync.send(player);
    }
    /** Detach first. If the old payer cannot accept an exact refund the unrelated new owner never gets it. */
    private static boolean retire(MinecraftServer server,Slot slot,boolean notify) {
        var claim=slot.claim;close(slot);
        if(claim==null)return false;
        var r=ExtendedResourcesSync.account(claim.payer);
        if(r==null || !claim.epoch.equals(r.accountEpoch) || !matchesReceipt(r.paidReceipts.get(claim.receipt),claim))return false;
        boolean refunded=ResourceTransaction.refund(server,r,claim.epoch,r.revision,claim.receipt,10000).applied();
        if(notify && refunded)sync(server,claim.payer);
        return refunded;
    }

    public static void tick(ServerLevel level,BuildingPlacement p) {
        var slot=SLOTS.get(p); // no new state allocation or building scan for idle producers
        if(slot==null || slot.claim==null || !live(level,p))return;
        Claim claim=slot.claim;
        if(!sameClaimant(p,claim) || isUpgraded(p)) {retire(level.getServer(),slot,true);return;}
        long now=level.getGameTime();
        if(slot.lastTick==now)return;
        slot.lastTick=now;
        if(!available(level,p))return;
        var account=ExtendedResourcesSync.account(claim.payer);
        if(account==null || !account.extendedStateSupported())return;
        var paid=account.paidReceipts.get(claim.receipt);
        if(!matchesReceipt(paid,claim)) {close(slot);return;}
        if(paid.food()!=COST.food() || paid.wood()!=COST.wood() || paid.ore()!=COST.ore()
                || paid.emerald()!=COST.emerald() || !paid.extra().equals(COST.extra())) {
            retire(level.getServer(),slot,true);return;
        }
        if(slot.remaining>1){slot.remaining--;return;}
        // No callback or client input between consuming the exact paid receipt and the one-bit local commit.
        var state=model(p);
        if(!ResourceTransaction.consumeReceipt(level.getServer(),account,claim.epoch,account.revision,claim.receipt).applied())return;
        state.commitLocalTechnology(new PaidCompletion(state,claim.technology));close(slot);
        BuildingClientboundPacket.syncBuilding(p); // physical UUID + existing visibility/level filtering
        sync(level.getServer(),claim.payer);
    }
    /** Non-destructive detach hook may not touch balances or persisted state; orphan reconciliation owns refunds. */
    public static void detach(BuildingPlacement p) {
        if(p.getLevel() instanceof ServerLevel level && level.getServer().isSameThread() && supports(p)) {
            TERMINAL.add(p);SLOTS.remove(p);
        }
    }
    private static boolean recognized(ResourcePaidReceipt receipt) {
        String value=receipt.purpose();
        String prefix=value.startsWith(DAIS_PURPOSE) ? DAIS_PURPOSE : PRODUCTION_PURPOSE;
        if(!value.startsWith(prefix) || value.length()!=prefix.length()+36)return false;
        String suffix=value.substring(prefix.length());
        try {return UUID.fromString(suffix).toString().equals(suffix);}
        catch(IllegalArgumentException ignored){return false;}
    }
    @SubscribeEvent public static void projectSave(ResourceSaveProjectionEvent event) {
        for(var receipt:List.copyOf(event.copy.paidReceipts.values()))
            if(recognized(receipt))ResearchRefundProjection.refund(event.copy,receipt);
    }
    @SubscribeEvent public static void retryDetached(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || event.getServer().getTickCount()%20!=0)return;
        Set<UUID> attached=new HashSet<>();
        for(var entry:SLOTS.entrySet()) {
            Slot slot=entry.getValue();var p=entry.getKey();
            if(p==null || slot.claim==null)continue;
            if(p.getLevel() instanceof ServerLevel level && live(level,p) && sameClaimant(p,slot.claim))
                attached.add(slot.claim.receipt);
            else retire(event.getServer(),slot,true);
        }
        for(var account:List.copyOf(ResourcesServerEvents.resourcesList)) {
            for(var receipt:List.copyOf(account.paidReceipts.values())) {
                if(!attached.contains(receipt.id()) && recognized(receipt) && receipt.payerEpoch().equals(account.accountEpoch)
                        && ResourceTransaction.refund(event.getServer(),account,account.accountEpoch,account.revision,receipt.id(),10000).applied())
                    sync(event.getServer(),account.ownerName);
            }
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SLOTS.clear();TERMINAL.clear();serial=0; }
}
