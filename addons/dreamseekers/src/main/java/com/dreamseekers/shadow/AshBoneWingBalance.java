// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.shadow;
import com.solegendary.reignofnether.resources.ResourceCost;
/** Baseline ordinary-resource costs, dimensions and ranged-combat parameters for ash bone wings.
 * These values remain subject to balance review; skills and shadow-energy charges are handled separately. */
public final class AshBoneWingBalance {
    private AshBoneWingBalance() { }
    public static final ResourceCost COST=ResourceCost.Unit(145,0,95,35,3);
    public static final float WIDTH=1.7f*1.5f,HEIGHT=.8f*1.5f;
    public static final double MAX_HEALTH=75,DAMAGE=9,SPEED=.28,ARMOR=0,ATTACKS_PER_SECOND=.5,AGGRO=12,SIGHT=20,RANGE=10;
    public static final int WINDUP_TICKS=8,PROJECTILE_TTL=60;
    public static final float PROJECTILE_SPEED=.8f;
}
