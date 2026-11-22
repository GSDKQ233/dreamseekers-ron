// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.shadow;
import com.solegendary.reignofnether.resources.ResourceCost;
/** Baseline ordinary-resource costs and melee attributes for terrorbeaks.
 * These values remain subject to balance review; shadow-energy charges and upgrades are handled separately. */
public final class TerrorbeakBalance {
    private TerrorbeakBalance(){}
    public static final ResourceCost COST=ResourceCost.Unit(130,0,85,28,3);
    public static final double MAX_HEALTH=100,DAMAGE=10,SPEED=.36,ARMOR=1;
    public static final double ATTACKS_PER_SECOND=.65,AGGRO=10,KNOCKBACK_RESISTANCE=.2;
    public static final int SIGHT=18;
}
