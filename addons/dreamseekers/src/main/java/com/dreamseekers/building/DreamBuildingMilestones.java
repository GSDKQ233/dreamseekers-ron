// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.building;

import com.dreamseekers.worldtree.DreamAnchorPlacement;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import net.minecraft.world.level.Level;

/** Shared by the paired-hand recipes and the Survival beacon. A remembered Wind entitlement
 * or a paid upgrade in progress is not a standing upgraded World Tree. */
public final class DreamBuildingMilestones {
    private DreamBuildingMilestones() {}
    public enum Missing { NONE, LABORATORY, UPGRADED_TREE }

    public static boolean hasUpgradedTreeAndLaboratory(Level level, String owner) {
        return missing(level, owner) == Missing.NONE;
    }

    /** Report the laboratory first when neither requirement has been met. */
    public static Missing missing(Level level, String owner) {
        if (level == null || owner == null || owner.isBlank()) return Missing.LABORATORY;
        boolean client = level.isClientSide();
        var live = client ? BuildingClientEvents.getBuildings() : BuildingServerEvents.getBuildings();
        boolean tree = false, laboratory = false;
        for (BuildingPlacement placement : live) {
            if (!(placement instanceof DreamAnchorPlacement) && !(placement instanceof DreamLaboratoryPlacement))
                continue;
            if (placement.getLevel() != level || !owner.equals(placement.ownerName)
                    || !placement.isBuilt || placement.isDestroyedServerside
                    || !level.hasChunksAt(placement.minCorner, placement.maxCorner)
                    || placement.shouldBeDestroyed() || placement.getHealth() <= 0) continue;
            if (placement instanceof DreamLaboratoryPlacement lab) {
                // The server also checks attachment and supported lifecycle state.
                laboratory |= client || lab.available();
            } else if (placement instanceof DreamAnchorPlacement anchor) {
                // A second paid upgrade never removes the first standing branch. Unsupported
                // or still-base placements (including a first purchase in flight) don't count.
                tree |= anchor.hasStandingUpgrade() && (client || anchor.productionAvailable());
            }
            if (tree && laboratory) return Missing.NONE;
        }
        return laboratory ? Missing.UPGRADED_TREE : Missing.LABORATORY;
    }

}
