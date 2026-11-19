// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.aaroncos;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
/** Contract only: does not bind units, create a partner, charge, share HP, or choose training mode. */
public record AaroncosPairRequest(int schema,UUID requestId,Intent intent,Source left,Source right) {
    public enum Intent { ESTABLISH_PAIR }
    public record Source(int entityId,UUID entityUuid,long ownershipRevision,ResourceLocation dimension) { }
}
