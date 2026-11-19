// SPDX-License-Identifier: GPL-3.0-only
package com.solegendary.reignofnether.keybinds;

import java.util.function.IntConsumer;

/** Event-time inventory context, independent of the last rendered HUD snapshot. */
public final class InventoryKeyReleaseGate {
    private InventoryKeyReleaseGate() { }

    /** Building production owns overlapping keys; stale/deselected inventory buttons must not fire. */
    public static boolean dispatch(boolean topdownScreen, boolean hasPlayer, boolean hasSelectedBuilding,
                                   boolean hudEntityStillSelected, boolean inventoryVisible, boolean textInputFocused,
                                   int keyCode, IntConsumer inventoryDispatch) {
        if (!topdownScreen || !hasPlayer || hasSelectedBuilding || !hudEntityStillSelected || !inventoryVisible || textInputFocused)
            return false;
        inventoryDispatch.accept(keyCode);
        return true;
    }
}
