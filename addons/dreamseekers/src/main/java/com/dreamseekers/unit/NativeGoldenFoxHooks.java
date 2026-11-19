// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit;

import net.minecraft.world.entity.Entity;

/** Class-scoped native NPC isolation; ownership, death or removal cannot reopen trades. */
public final class NativeGoldenFoxHooks {
    private NativeGoldenFoxHooks() { }

    public static boolean isPaidUnit(Entity entity) {
        return entity instanceof GoldenFoxUnit;
    }
}
