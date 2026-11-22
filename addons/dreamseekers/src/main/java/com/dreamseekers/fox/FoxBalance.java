// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.fox;

import com.solegendary.reignofnether.resources.ResourceCost;

/** Baseline recruitment and attributes for the golden fox support unit.
 * These values remain subject to balance review; skill parameters are defined separately. */
public final class FoxBalance {
    private FoxBalance() { }
    public static final ResourceCost COST = ResourceCost.Unit(90, 0, 45, 20, 2);
    public static final double MAX_HEALTH = 32;
    public static final double SPEED = .42;
    public static final int SIGHT = 20;
}
