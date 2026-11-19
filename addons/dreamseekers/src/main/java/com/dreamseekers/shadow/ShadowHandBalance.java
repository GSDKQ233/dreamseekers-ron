// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.shadow;
import com.solegendary.reignofnether.resources.ResourceCost;
/** Baseline ordinary-resource costs and melee attributes for the shadow hand.
 * These values remain subject to balance review; shadow-energy charges and skills are handled separately. */
public final class ShadowHandBalance {
    private ShadowHandBalance() { }
    public static final ResourceCost COST=ResourceCost.Unit(95,0,55,30,2);
    public static final double MAX_HEALTH=55,DAMAGE=8,SPEED=.28,ARMOR=0,ATTACKS_PER_SECOND=.8,AGGRO=10;
    public static final int SIGHT=18;
    public static final double KNOCKBACK_RESISTANCE=.2;
    /** Match native registered .6x.8 times1.5 once, not a double-scaled collision. */
    public static final float WIDTH=.6f*1.5f,HEIGHT=.8f*1.5f;
}
