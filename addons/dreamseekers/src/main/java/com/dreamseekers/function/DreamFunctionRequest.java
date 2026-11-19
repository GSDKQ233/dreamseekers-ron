// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.function;

import com.dreamseekers.building.DreamBuildingSpec.Role;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Typed future commands, not enabled abilities. None reserves or transfers any resource. */
public sealed interface DreamFunctionRequest {
    Role role();
    boolean valid();

    enum LogisticsOperation { GATHER, DROPOFF }
    record Logistics(UUID worker, BlockPos source, ResourceLocation resource, int amount,
                     LogisticsOperation operation) implements DreamFunctionRequest {
        public Role role() { return Role.LOGISTICS; }
        public boolean valid() { return worker != null && source != null && resource != null && amount > 0 && operation != null; }
    }
    record OreExtraction(BlockPos source, int amount) implements DreamFunctionRequest {
        public Role role() { return Role.ORE_EXTRACTION; }
        public boolean valid() { return source != null && amount > 0; }
    }
    /** Expected-balance/idempotency contract only; no ledger, rate or spend is implemented. */
    record ShadowAtomic(UUID transactionId, long expectedBalance, long delta) implements DreamFunctionRequest {
        public Role role() { return Role.SHADOW_ENERGY; }
        public boolean valid() {
            if (transactionId == null || expectedBalance < 0 || delta == 0) return false;
            try { return Math.addExact(expectedBalance, delta) >= 0; }
            catch (ArithmeticException ignored) { return false; }
        }
    }
    record Production(ResourceLocation recipe) implements DreamFunctionRequest {
        public Role role() { return Role.PRODUCTION; }
        public boolean valid() { return recipe != null; }
    }
    record Research(ResourceLocation technology) implements DreamFunctionRequest {
        public Role role() { return Role.RESEARCH; }
        public boolean valid() { return technology != null; }
    }
    enum ContractOperation { INSPECT, TAME, FORM_CONTRACT }
    record Contract(UUID target, ResourceLocation contract, ContractOperation operation) implements DreamFunctionRequest {
        public Contract(UUID target, ResourceLocation contract) { this(target, contract, ContractOperation.FORM_CONTRACT); }
        public Role role() { return Role.CONTRACTS; }
        public boolean valid() { return target != null && operation != null && (operation != ContractOperation.FORM_CONTRACT || contract != null); }
    }
    enum ResonanceOperation { JOIN, LEAVE, APPLY_EFFECT }
    record Resonance(BlockPos peer, ResourceLocation effect, ResonanceOperation operation) implements DreamFunctionRequest {
        public Resonance(BlockPos peer, ResourceLocation effect) { this(peer, effect, ResonanceOperation.APPLY_EFFECT); }
        public Role role() { return Role.RESONANCE; }
        public boolean valid() { return peer != null && operation != null && (operation != ResonanceOperation.APPLY_EFFECT || effect != null); }
    }
    enum DefenseOperation { CHECK_TARGET, CHECK_LINE_OF_SIGHT }
    record Defense(UUID target, DefenseOperation operation) implements DreamFunctionRequest {
        public Defense(UUID target) { this(target, DefenseOperation.CHECK_TARGET); }
        public Role role() { return Role.DEFENSE; }
        public boolean valid() { return target != null && operation != null; }
    }
}
