// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.stone;
import com.solegendary.reignofnether.resources.ResourceCost;
/** Baseline recruitment, ranged-combat and projectile parameters for the stone spirit thrower.
 * These values remain subject to balance review; siege skills are configured separately. */
public final class StoneThrowerBalance {
    private StoneThrowerBalance() { }
    public static final ResourceCost COST=ResourceCost.Unit(60,0,40,20,2);
    public static final double MAX_HEALTH=36,DAMAGE=5,SPEED=.34,ARMOR=2,ATTACKS_PER_SECOND=.6,AGGRO=12,SIGHT=18,RANGE=10;
    public static final int WINDUP_TICKS=8,PROJECTILE_TTL=60;
    public static final float PROJECTILE_SPEED=.8f;
}
