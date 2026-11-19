package com.solegendary.reignofnether.faction;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;

/** Normal-match random selection, deliberately independent of panorama and survival-wave rosters. */
public final class RandomStartFactionSelection {
    private RandomStartFactionSelection() {}

    public static List<Faction> candidates(List<ResourceLocation> playable,
                                           Function<ResourceLocation, Faction> lookup,
                                           Predicate<Faction> registeredStartAssets) {
        return playable.stream().filter(java.util.Objects::nonNull).distinct().filter(key ->
                !"reignofnether".equals(key.getNamespace())
                        || !List.of("none", "neutral", "random").contains(key.getPath()))
                .map(key -> {
                    Faction faction = lookup.apply(key);
                    return faction != null && key.equals(faction.key) ? faction : null;
                })
                .filter(java.util.Objects::nonNull)
                .filter(faction -> faction.playable && faction.workerEntityType != null
                        && faction.scoutEntityType != null && faction.capitolBuilding != null)
                .filter(registeredStartAssets).toList();
    }

    /** One bounded draw gives each distinct eligible faction equal probability. Empty rosters cannot start. */
    public static Faction select(List<Faction> candidates, IntUnaryOperator nextInt) {
        return candidates.isEmpty() ? null : candidates.get(nextInt.applyAsInt(candidates.size()));
    }
}
