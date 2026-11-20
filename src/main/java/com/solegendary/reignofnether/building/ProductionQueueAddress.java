package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import javax.annotation.Nullable;
import java.util.List;

/** Current queue messages address the placement origin. A legacy corner is accepted only when unique. */
final class ProductionQueueAddress {
    private ProductionQueueAddress() {}

    @Nullable static ProductionPlacement resolve(Level level, BlockPos address, List<BuildingPlacement> placements) {
        BuildingPlacement found = null;
        for (BuildingPlacement p : placements) {
            if (p.getLevel() != level || !p.originPos.equals(address)) continue;
            if (found != null && found != p) return null;
            found = p;
        }
        if (found == null) {
            for (BuildingPlacement p : placements) {
                if (p.getLevel() != level || !p.isPosInsideBuilding(address)) continue;
                if (found != null && found != p) return null;
                found = p;
            }
        }
        return found instanceof ProductionPlacement host ? host : null;
    }
}
