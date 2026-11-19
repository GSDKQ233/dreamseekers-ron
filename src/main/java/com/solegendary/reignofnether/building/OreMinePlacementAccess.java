package com.solegendary.reignofnether.building;

/** Narrow authorization policy for player-requested passive mine placement. */
public final class OreMinePlacementAccess {
    private OreMinePlacementAccess() {
    }

    /**
     * Both permission flags must be resolved by the server for the actual packet
     * sender, never supplied by the client or inferred from another player's
     * sandbox status. Unowned debug placements should use administrator commands.
     */
    public static boolean canPlaceFor(String sender, String owner,
                                      boolean senderIsSandbox, boolean hasAllyControl) {
        if (sender == null || sender.isBlank() || owner == null || owner.isBlank()) {
            return false;
        }
        return sender.equals(owner) || senderIsSandbox || hasAllyControl;
    }
}
