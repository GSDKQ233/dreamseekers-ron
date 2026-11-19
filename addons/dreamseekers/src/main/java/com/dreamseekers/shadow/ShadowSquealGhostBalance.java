// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.shadow;
import com.solegendary.reignofnether.resources.ResourceCost;
/** Baseline recruitment, ranged-combat and projectile parameters for the shadow squeal ghost.
 * These values remain subject to balance review; skill parameters are defined separately. */
public final class ShadowSquealGhostBalance {
    private ShadowSquealGhostBalance() { }
    public static final ResourceCost COST=ResourceCost.Unit(90,0,50,30,2);
    public static final double MAX_HEALTH=40,DAMAGE=6,SPEED=.28,ARMOR=0,ATTACKS_PER_SECOND=.5,AGGRO=12,SIGHT=20,RANGE=10;
    public static final int WINDUP_TICKS=8,PROJECTILE_TTL=60;
    public static final float PROJECTILE_SPEED=.8f;
}
