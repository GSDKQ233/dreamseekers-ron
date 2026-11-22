// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.windknight;
import com.solegendary.reignofnether.resources.ResourceCost;
/** Baseline recruitment and melee attributes for the wind knight.
 * These values remain subject to balance review; dash and command parameters are defined separately. */
public final class WindKnightBalance {
    private WindKnightBalance() { }
    public static final ResourceCost COST=ResourceCost.Unit(140,0,100,32,3);
    public static final double MAX_HEALTH=120, DAMAGE=10, SPEED=.29, ARMOR=4;
    public static final double ATTACKS_PER_SECOND=.65, AGGRO=10, KNOCKBACK_RESISTANCE=.4;
    public static final int SIGHT=18;
}
