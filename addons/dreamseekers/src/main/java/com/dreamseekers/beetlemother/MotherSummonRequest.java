// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.beetlemother;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
/** Typed reserve only. No approved summon count, cost, cap, lifetime, cooldown or effect exists. */
public record MotherSummonRequest(int schema,UUID requestId,Source caster,SelfTarget target) {
    public record Source(int entityId,UUID entityUuid,long ownershipRevision,ResourceLocation dimension) { }
    public record SelfTarget() { }
}
