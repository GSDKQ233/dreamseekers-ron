// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.aaroncos;

import com.dreamseekers.building.DreamBuildingMilestones;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;

/** A physical completed upgrade is required: a global Wind flag alone is not a standing tree. */
public final class AaroncosTraining {
    private AaroncosTraining() {}
    public static String missingRequirement(ProductionPlacement host) {
        return switch (DreamBuildingMilestones.missing(host.getLevel(), host.ownerName)) {
            case NONE -> null;
            case LABORATORY -> "dreamseekers.error.hands_laboratory";
            case UPGRADED_TREE -> "dreamseekers.error.hands_upgraded_tree";
        };
    }
    public static boolean hasUpgradedAnchor(ProductionPlacement host) {
        return DreamBuildingMilestones.hasUpgradedTreeAndLaboratory(host.getLevel(), host.ownerName);
    }
}
