package com.solegendary.reignofnether.resources.kill;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.resources.Resources;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.Event;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/** A confirmed death-time standard-cost snapshot, not an automatic resource award. */
public final class ConfirmedRtsKillEvent extends Event {
    public record Snapshot(String killerOwner, ResourceLocation killerFaction, ResourceLocation rewardFaction, UUID accountEpoch,
                           UUID victimId, UUID economicValueId, String victimOwner,
                           int food, int wood, int ore, Set<ResourceLocation> research, boolean allResearchCheat) {
        public Snapshot { research = Set.copyOf(research); }
        public boolean hadResearch(ResourceLocation id) { return allResearchCheat || research.contains(id); }
        public long totalCost() { return (long)food + wood + ore; }
    }
    private final Snapshot snapshot;
    private final MinecraftServer server;
    private final LivingEntity victim;
    private final Resources account;
    private final BooleanSupplier valid;
    private boolean dispatching;
    private boolean attempting;
    ConfirmedRtsKillEvent(Snapshot snapshot, MinecraftServer server, LivingEntity victim,
                         Resources account, BooleanSupplier valid) {
        this.snapshot=snapshot; this.server=server; this.victim=victim; this.account=account; this.valid=valid;
    }
    public Snapshot snapshot() { return snapshot; }
    public boolean isClaimed() { return RtsKillValueClaims.isClaimed(victim); }
    void beginDispatch() { dispatching=true; }
    void endDispatch() { dispatching=false; }

    /** Exactly one eligible consumer may commit. Callback must be an atomic main-thread ledger
     * operation: return false only with ZERO mutation; true includes a committed fractional remainder.
     * Check faction/research before calling. No asynchronous callback or retained claim is permitted.
     * Exceptions are logged and consume value fail-closed because payout outcome is uncertain. */
    public boolean tryClaim(Predicate<Resources> atomicCredit) {
        if (atomicCredit == null || !dispatching || attempting || !server.isSameThread()
                || !valid.getAsBoolean() || isClaimed()) return false;
        UUID nonce = RtsKillValueClaims.reserve(victim,snapshot.killerOwner(),snapshot.accountEpoch());
        if (nonce == null) return false;
        attempting=true;
        boolean applied;
        try {
            applied=atomicCredit.test(account);
        } catch (RuntimeException failure) {
            lockValue(nonce,"uncertain");
            ReignOfNether.LOGGER.error("RTS kill economic claim failed; value locked to prevent duplicate payout: victim={} owner={}",
                    snapshot.victimId(), snapshot.killerOwner(), failure);
            return false;
        } finally { attempting=false; }
        if (!applied) {
            RtsKillValueClaims.rollback(victim,nonce);
            return false;
        }
        lockValue(nonce,"committed");
        return true;
    }
    private void lockValue(UUID nonce, String outcome) {
        try { RtsKillValueClaims.commit(victim,nonce,outcome); }
        catch (RuntimeException failure) {
            // The entity reservation remains fail-closed even if a save/listener fails.
            ReignOfNether.LOGGER.error("Could not synchronize RTS kill provenance to fallen hero storage: victim={} owner={}",
                    snapshot.victimId(), snapshot.killerOwner(), failure);
        }
    }
}
