// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.spore;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
/** Typed request contract only. No defined caster eligibility, cost, TTL, cap or zone effect yet. */
public record SporeSummonRequest(int schema,UUID requestId,Source caster,GroundTarget target) {
    public record Source(int entityId,UUID entityUuid,long ownershipRevision,ResourceLocation dimension) { }
    public record GroundTarget(BlockPos position) { }
}
