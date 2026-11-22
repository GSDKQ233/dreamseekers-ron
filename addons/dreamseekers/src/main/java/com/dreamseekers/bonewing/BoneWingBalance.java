// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.bonewing;
import com.solegendary.reignofnether.resources.ResourceCost;
/** Baseline recruitment, ranged-combat and projectile parameters for bone-wing units.
 * These values remain subject to balance review; skill parameters are defined separately. */
public final class BoneWingBalance {
    private BoneWingBalance() { }
    public static final ResourceCost COST=ResourceCost.Unit(100,0,60,24,2);
    public static final double MAX_HEALTH=45,DAMAGE=6,SPEED=.42,ARMOR=0,ATTACKS_PER_SECOND=.5,AGGRO=12,SIGHT=20,RANGE=10;
    public static final int WINDUP_TICKS=8,PROJECTILE_TTL=60;
    public static final float PROJECTILE_SPEED=.8f;
}
