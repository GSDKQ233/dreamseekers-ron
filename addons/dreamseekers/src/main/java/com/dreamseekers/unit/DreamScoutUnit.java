// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit;

/** Compatibility alias retaining the verified scout's contract and public static helper. */
public interface DreamScoutUnit extends DreamNoncombatUnit {
    static boolean isSettledRtsResource(net.minecraft.world.item.ItemStack stack) {
        return DreamNoncombatUnit.isSettledRtsResource(stack);
    }
}
