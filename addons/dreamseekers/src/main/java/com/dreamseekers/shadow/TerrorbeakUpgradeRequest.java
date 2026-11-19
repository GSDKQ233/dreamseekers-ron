// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.shadow;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
/** Authenticated source identity for the paid same-owner form transition; policy lives in TerrorbeakUpgradeService. */
public record TerrorbeakUpgradeRequest(int schema, UUID requestId, Source source, UpgradeTarget target) {
    public record Source(int entityId, UUID entityUuid, long ownershipRevision, ResourceLocation dimension) { }
    public enum UpgradeTarget { CRAZY_TERRORBEAK }
}
