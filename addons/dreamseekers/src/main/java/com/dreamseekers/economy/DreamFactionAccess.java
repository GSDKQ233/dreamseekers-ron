package com.dreamseekers.economy;

import com.dreamseekers.building.DreamBuildingSupport;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.RTSPlayer;

/** Content eligibility only, never actor ownership or payment authority. Native sandbox accounts use NONE. */
public final class DreamFactionAccess {
    private DreamFactionAccess() {}
    public static boolean allows(Faction faction) {
        return faction != null && (DreamBuildingSupport.FACTION.equals(faction.key) || faction == Factions.NONE);
    }
    public static boolean allows(RTSPlayer owner) { return owner != null && allows(owner.faction); }
}
