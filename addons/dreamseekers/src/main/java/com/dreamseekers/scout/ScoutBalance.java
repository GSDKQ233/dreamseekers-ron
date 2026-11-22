// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.scout;

import com.solegendary.reignofnether.resources.ResourceCost;

/** Baseline recruitment, vision and marking parameters for the noncombat scout.
 * These values remain subject to balance review; marks grant no damage or economy bonus. */
public final class ScoutBalance {
    private ScoutBalance() { }
    public static final ResourceCost COST = ResourceCost.Unit(65, 0, 15, 16, 1);
    public static final double MAX_HEALTH = 22;
    public static final double SPEED = .58;
    public static final int SIGHT = 24;
    public static final int MARK_RANGE = 20;
    public static final int MARK_COOLDOWN_TICKS = 400;
    public static final int MARK_DURATION_TICKS = 120;
}
