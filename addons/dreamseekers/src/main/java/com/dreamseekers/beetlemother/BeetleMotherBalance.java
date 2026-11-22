// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.beetlemother;
import com.solegendary.reignofnether.resources.ResourceCost;
/** Paid RTS swarm commander; native boss abilities and summon limits remain isolated. */
public final class BeetleMotherBalance {
    private BeetleMotherBalance() { }
    public static final ResourceCost COST=ResourceCost.Unit(180,0,140,36,4);
    public static final double MAX_HEALTH=200,DAMAGE=14,SPEED=.30,ARMOR=4;
    public static final double ATTACKS_PER_SECOND=.55,AGGRO=10,KNOCKBACK_RESISTANCE=.3;
    public static final int SIGHT=18;
}
