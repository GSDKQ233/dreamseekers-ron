// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.shadow;
import com.solegendary.reignofnether.resources.ResourceCost;
/** Baseline recruitment, ranged-combat and projectile parameters for the weeping squeal ghost.
 * These values remain subject to balance review; skill parameters are defined separately. */
public final class WeepingSquealGhostBalance {
    private WeepingSquealGhostBalance() { }
    public static final ResourceCost COST=ResourceCost.Unit(180,0,120,45,4);
    public static final double MAX_HEALTH=100,DAMAGE=8,SPEED=.28,ARMOR=2,ATTACKS_PER_SECOND=.5,AGGRO=12,SIGHT=20,RANGE=10,KNOCKBACK_RESISTANCE=.4;
    public static final int WINDUP_TICKS=8,PROJECTILE_TTL=60;
    public static final float PROJECTILE_SPEED=.8f;
}
