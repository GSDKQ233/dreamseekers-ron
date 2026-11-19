// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.building.DreamBuildingMilestones;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.SurvivalBeaconFaction;
import net.minecraft.server.level.ServerLevel;
import javax.annotation.Nullable;

public final class DreamseekersFaction extends Faction implements SurvivalBeaconFaction {
    public static final String BEACON_PREREQUISITE = "dreamseekers.beacon.survival_prerequisite";

    @Override @Nullable public String getSurvivalBeaconPrerequisiteError(ServerLevel level, String owner) {
        return DreamBuildingMilestones.hasUpgradedTreeAndLaboratory(level, owner) ? null : BEACON_PREREQUISITE;
    }
}
