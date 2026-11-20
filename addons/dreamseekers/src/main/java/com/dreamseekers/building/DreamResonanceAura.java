// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.building;

import com.dreamseekers.DreamseekersMod;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;

/** Pure query; overlapping completed same-owner dais contribute at most one local 10% bonus.
 * Range is footprint to footprint, so a host is covered on every side of the dais alike. */
public final class DreamResonanceAura {
    public static final int HORIZONTAL_RADIUS=12,VERTICAL_RADIUS=8;
    private DreamResonanceAura() {}
    public static boolean active(ProductionPlacement host) {
        if(host==null||host.ownerName.isBlank()||!host.isBuilt||host.isDestroyedServerside||host.shouldBeDestroyed())return false;
        var buildings=host.getLevel().isClientSide()?BuildingClientEvents.getBuildings():BuildingServerEvents.getBuildings();
        if(!buildings.contains(host)||!loaded(host))return false;
        for(var candidate:buildings) {
            if(candidate.getBuilding()!=DreamseekersMod.RESONANCE_DAIS||candidate.getLevel()!=host.getLevel()
                    ||!candidate.ownerName.equals(host.ownerName)||!candidate.isBuilt||candidate.isDestroyedServerside
                    ||candidate.shouldBeDestroyed()||!loaded(candidate))continue;
            if(DreamFootprintRange.footprintsWithin(candidate,host,HORIZONTAL_RADIUS,VERTICAL_RADIUS))return true;
        }
        return false;
    }
    private static boolean loaded(BuildingPlacement p) {
        return p.getLevel().hasChunksAt(p.minCorner,p.maxCorner);
    }
}
