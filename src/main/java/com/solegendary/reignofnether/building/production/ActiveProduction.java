package com.solegendary.reignofnether.building.production;

import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;

public class ActiveProduction {
    /** Public, runtime-only queue identity. Never a payment receipt or permission token. */
    public final java.util.UUID queueEntryId;
    /** The ordinary queue refunds the amount actually quoted for this job, not today's recipe price.
     * Dedicated addon/research receipts remain authoritative for their own orders. Never sent to the HUD. */
    public record NativeQuote(String payer, int food, int wood, int ore, int emerald) {}
    public final NativeQuote nativeQuote;
    public com.solegendary.reignofnether.research.economy.ReceiptResearchBinding receiptResearch;
    public boolean completed;
    long nextIndoorSpawnAttempt = Long.MIN_VALUE;
    private ProductionPause pause = ProductionPause.INITIAL;
    private String observedPause = "";
    public ProductionPause pause() { return pause; }

    // The server commits at most one observation after the complete tick, including spawn callbacks.
    public void beginPauseObservation() { observedPause = ""; }
    public boolean pauseBecause(String reasonKey) { observedPause = reasonKey; return false; }
    public boolean finishPauseObservation() {
        var next = pause.observe(observedPause);
        if (next == pause) return false;
        pause = next;
        return true;
    }

    /** Display only. Old/duplicate deltas cannot rewind work or attach to a same-recipe successor. */
    public boolean acceptPause(java.util.UUID entry, ProductionPause incoming, float remaining) {
        if (!queueEntryId.equals(entry) || incoming.revision() <= pause.revision()
                || !Float.isFinite(remaining) || remaining < 0 || completed) return false;
        pause = incoming;
        ticksLeft = remaining;
        workMirror = Float.NaN;
        return true;
    }

    public long workHundredths=-1;
    public float workMirror=Float.NaN;
    public float ticksLeft;
    public ProductionItem item;
    public ActiveProduction(ProductionItem item, boolean isClientside, String ownerName) {
        this(item, isClientside, ownerName, java.util.UUID.randomUUID());
    }

    /** Rebuild a client-side view with the identity issued by the server. */
    public ActiveProduction(ProductionItem item, String ownerName, java.util.UUID queueEntryId) {
        this(item, true, ownerName, queueEntryId);
    }

    private ActiveProduction(ProductionItem item, boolean isClientside, String ownerName, java.util.UUID queueEntryId) {
        this.queueEntryId = java.util.Objects.requireNonNull(queueEntryId);
        this.item = item;
        var cost = item.getCost(isClientside, ownerName);
        this.ticksLeft = cost.ticks;
        this.nativeQuote = isClientside ? null : new NativeQuote(ownerName, cost.food, cost.wood, cost.ore, cost.emerald);
    }

    public void complete(ProductionPlacement placement) {
        if(item instanceof com.solegendary.reignofnether.research.economy.ReceiptBackedResearchItem) {
            // Client completion is only visual; never invokes a nullable legacy callback or unit score.
            if(placement.getLevel().isClientSide())completed=true;
            else completed=com.solegendary.reignofnether.research.economy.ReceiptResearchService.complete(placement,this);
            return;
        }
        if (!placement.getLevel().isClientSide() && NativeProductionEligibility.error(item,placement,true)!=null) {
            return; // Keep the exact paid reservation; normal cancellation still refunds it.
        }
        if (!placement.getLevel().isClientSide() && NativeProductionCompletion.handles(placement,item)) {
            NativeProductionCompletion.complete(placement,this);
            return;
        }
        this.item.recordScore(placement);
        this.item.onComplete.accept(placement.getLevel(), placement);
        this.completed = true;
    }
}
