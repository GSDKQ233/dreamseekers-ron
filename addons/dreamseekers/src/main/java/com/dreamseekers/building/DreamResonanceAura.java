// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.building;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.economy.ResourceBuildingResearch;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;

/** Pure query; use the strongest completed same-owner dais in local range, not a sum.
 * Unlike paid pulse and ward, the pre-existing local work-rate aura does not need a network link. */
public final class DreamResonanceAura {
    public static final int HORIZONTAL_RADIUS=12,VERTICAL_RADIUS=8;
    public static final int BASE_PERCENT=10,UPGRADED_PERCENT=20;
    private DreamResonanceAura() {}
    public static boolean active(ProductionPlacement host) { return bonusPercent(host)>0; }
    public static int bonusPercent(ProductionPlacement host) {
        if(host==null||host.ownerName.isBlank()||!host.isBuilt||host.isDestroyedServerside||host.shouldBeDestroyed())return 0;
        var buildings=host.getLevel().isClientSide()?BuildingClientEvents.getBuildings():BuildingServerEvents.getBuildings();
        if(!buildings.contains(host)||!loaded(host))return 0;
        int percent=0;
        for(var candidate:buildings) {
            if(candidate.getBuilding()!=DreamseekersMod.RESONANCE_DAIS||candidate.getLevel()!=host.getLevel()
                    ||!candidate.ownerName.equals(host.ownerName)||!candidate.isBuilt||candidate.isDestroyedServerside)continue;
            if(!DreamFootprintRange.footprintsWithin(candidate,host,HORIZONTAL_RADIUS,VERTICAL_RADIUS))continue;
            if(!loaded(candidate)||candidate.shouldBeDestroyed())continue;
            if(ResourceBuildingResearch.isUpgraded(candidate))return UPGRADED_PERCENT;
            percent=BASE_PERCENT;
        }
        return percent;
    }
    private static boolean loaded(BuildingPlacement p) {
        return p.getLevel().hasChunksAt(p.minCorner,p.maxCorner);
    }
}
