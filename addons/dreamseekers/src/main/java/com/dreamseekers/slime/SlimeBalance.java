// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.slime;
import com.solegendary.reignofnether.resources.ResourceCost;
/** Baseline recruitment and melee attributes for the pink slime.
 * These values remain subject to balance review; skill parameters are defined separately. */
public final class SlimeBalance {
    private SlimeBalance() { }
    public static final ResourceCost COST = ResourceCost.Unit(60, 0, 20, 18, 1);
    public static final double MAX_HEALTH = 50;
    public static final double SPEED = .28;
    public static final int SIGHT = 18;
    public static final double DAMAGE = 4;
    public static final double ARMOR = 1;
    public static final double ATTACKS_PER_SECOND = .8;
    public static final double AGGRO = 8;
}
