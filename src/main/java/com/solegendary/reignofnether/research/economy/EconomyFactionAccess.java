package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;

/** Registered sandbox accounts may test each economy; spectator fallback NONE is not an account. */
public final class EconomyFactionAccess {
    private EconomyFactionAccess() {}
    public static boolean permits(Faction actual, Faction required) {
        return actual != null && required != null
                && (actual == required || (Factions.NONE != null && actual == Factions.NONE));
    }
    public static boolean serverOwner(String owner, Faction required) {
        var player = PlayerServerEvents.getRTSPlayer(owner);
        return player != null && permits(player.faction, required);
    }
}
