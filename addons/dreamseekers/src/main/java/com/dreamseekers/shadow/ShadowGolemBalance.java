// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.shadow;
import com.solegendary.reignofnether.resources.ResourceCost;
/** Baseline ordinary-resource costs and heavy-melee attributes for the shadow golem.
 * These values remain subject to balance review; shadow-energy charges and siege skills are handled separately. */
public final class ShadowGolemBalance {
    private ShadowGolemBalance(){}
    public static final ResourceCost COST=ResourceCost.Unit(180,0,140,36,4);
    public static final double MAX_HEALTH=180,DAMAGE=16,SPEED=.28,ARMOR=6;
    public static final double ATTACKS_PER_SECOND=.5,AGGRO=10,KNOCKBACK_RESISTANCE=.5;
    public static final int SIGHT=18;
}
