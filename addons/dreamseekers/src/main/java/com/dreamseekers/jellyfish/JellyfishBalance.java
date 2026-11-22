// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.jellyfish;
import com.solegendary.reignofnether.resources.ResourceCost;
/** Baseline recruitment and attributes for the jellyfish support unit.
 * These values remain subject to balance review; skill parameters are defined separately. */
public final class JellyfishBalance {
    private JellyfishBalance() { }
    public static final ResourceCost COST = ResourceCost.Unit(70, 0, 25, 16, 1);
    public static final double MAX_HEALTH = 30;
    public static final double SPEED = .40;
    public static final int SIGHT = 18;
}
