// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.shadow;
import com.solegendary.reignofnether.resources.ResourceCost;
/** Baseline ordinary-resource costs and melee attributes for the shadow ghost.
 * These values remain subject to balance review; shadow-energy charges and skills are handled separately. */
public final class ShadowGhostBalance {
    private ShadowGhostBalance() { }
    public static final ResourceCost COST=ResourceCost.Unit(70,0,30,20,2);
    public static final double MAX_HEALTH=40,DAMAGE=6,SPEED=.45,ARMOR=0,ATTACKS_PER_SECOND=.8,AGGRO=10;
    public static final int SIGHT=18;
}
