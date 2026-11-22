// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.aaroncos;
import com.solegendary.reignofnether.resources.ResourceCost;
/** Independent paid heavy units. Shadow cost is quoted and paid by their production recipe. */
public final class AaroncosBalance {
    private AaroncosBalance() { }
    public static ResourceCost newTrainingCost(){return ResourceCost.Unit(400,250,350,72,8);}
    /** Compatibility constant; each independently configurable unit must use newTrainingCost(). */
    public static final ResourceCost POPULATION_METADATA=newTrainingCost();
    public static final double LEFT_MAX_HEALTH=450,LEFT_DAMAGE=26,LEFT_ARMOR=7;
    public static final double RIGHT_MAX_HEALTH=420,RIGHT_DAMAGE=24,RIGHT_ARMOR=6;
    public static final double SPEED=.30,ATTACKS_PER_SECOND=.5,AGGRO=12,SIGHT=22,KNOCKBACK_RESISTANCE=.8;
}
