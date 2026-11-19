package com.solegendary.reignofnether.fogofwar;

import java.util.*;

/** Identity-based table audience, not a world-update visibility bypass. Own table already contains allied vision. */
public final class FogRecipientPolicy {
    private FogRecipientPolicy() {}
    public static <T> Map<UUID, Set<T>> select(UUID viewer, boolean fogRestricted, Map<UUID, Set<T>> source) {
        Map<UUID, Set<T>> result = new HashMap<>();
        source.forEach((id, values) -> {
            if (!fogRestricted || id.equals(viewer)) result.put(id, Set.copyOf(values));
        });
        return Map.copyOf(result);
    }
}
