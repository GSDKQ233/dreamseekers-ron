package com.solegendary.reignofnether.faction;

import com.solegendary.reignofnether.unit.UnitAction;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.function.Function;

/** Client-side placement intent, cleared by cancellation and by any different cursor action. */
public final class StartFactionSelection {
    private ResourceLocation selected;

    /** Extra free-placement entries, not title panoramas, wave rosters, or preset-only buttons. */
    public static List<ResourceLocation> additionalFactionKeys(List<ResourceLocation> playable,
                                                               Function<ResourceLocation, Faction> lookup) {
        return playable.stream().distinct().filter(key -> {
            if ("reignofnether".equals(key.getNamespace())
                    && List.of("villagers", "monsters", "piglins", "none", "neutral", "random").contains(key.getPath()))
                return false;
            Faction faction = lookup.apply(key);
            return faction != null && faction.playable && faction.workerEntityType != null
                    && key.equals(faction.key);
        }).toList();
    }

    public void select(ResourceLocation faction) { selected = faction; }
    public ResourceLocation selected() { return selected; }
    public void onActionChanged(UnitAction action) {
        if (action != UnitAction.STARTRTS_REGISTERED_FACTION) selected = null;
    }
    public ResourceLocation resolve(UnitAction action) {
        if (action == null) return null;
        return switch (action) {
            case STARTRTS_VILLAGERS -> ResourceLocation.fromNamespaceAndPath("reignofnether", "villagers");
            case STARTRTS_MONSTERS -> ResourceLocation.fromNamespaceAndPath("reignofnether", "monsters");
            case STARTRTS_PIGLINS -> ResourceLocation.fromNamespaceAndPath("reignofnether", "piglins");
            case STARTRTS_REGISTERED_FACTION -> selected;
            default -> null;
        };
    }
}
