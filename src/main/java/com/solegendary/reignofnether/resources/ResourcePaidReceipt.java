package com.solegendary.reignofnether.resources;

import net.minecraft.resources.ResourceLocation;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable paid amounts, not a mutable quote. Presence in its payer account means unsettled. */
public record ResourcePaidReceipt(UUID id, UUID payerEpoch, String purpose,
                                  int food, int wood, int ore, int emerald,
                                  Map<ResourceLocation, Integer> extra) {
    public ResourcePaidReceipt {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(payerEpoch, "payerEpoch");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(extra, "extra");
        if (purpose.isBlank() || purpose.length() > 128 || food < 0 || wood < 0 || ore < 0 || emerald < 0
                || extra.size() > ResourceAccountCodec.MAX_EXTRA)
            throw new IllegalArgumentException("Invalid paid receipt");
        for (var entry : extra.entrySet())
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() < 0)
                throw new IllegalArgumentException("Invalid paid extension");
        extra = Map.copyOf(extra);
    }
}
