package com.solegendary.reignofnether.building;

/** Pure policy for server-resolved workers selected by a placement request. */
public final class BuildingPlacementWorkerAccess {
    private BuildingPlacementWorkerAccess() {}

    /** All identity and permission arguments are resolved by the server. */
    public static boolean canControl(String sender, String workerOwner, boolean strictPlacement,
                                     boolean senderIsSandbox, boolean anyoneIsSandbox, boolean hasAllyControl) {
        if (strictPlacement)
            return OreMinePlacementAccess.canPlaceFor(sender, workerOwner, senderIsSandbox, hasAllyControl);
        if (sender == null || sender.isBlank()) return false;
        // Ordinary/custom placement retains its existing global Sandbox contract,
        // including neutral workers. Strict buildings retain sender-only Sandbox.
        return sender.equals(workerOwner) || anyoneIsSandbox || hasAllyControl;
    }
}
