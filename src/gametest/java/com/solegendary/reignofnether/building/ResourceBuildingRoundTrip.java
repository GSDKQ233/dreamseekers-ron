// SPDX-License-Identifier: GPL-3.0-only
package com.solegendary.reignofnether.building;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/**
 * The normal save writer and row/data decoders, then the same per-row factory and field assignment
 * as BuildingServerEvents.loadBuildingsAndNetherZones. Do not reload all players' live buildings
 * underneath other tests. This is a same-process NBT round trip, not a two-server restart.
 */
public final class ResourceBuildingRoundTrip {
    private ResourceBuildingRoundTrip() {}

    public static BuildingPlacement reload(ServerLevel level, BuildingPlacement old) {
        BuildingServerEvents.saveBuildings(level);
        var wire = BuildingSaveData.getInstance(level).save(new CompoundTag());
        var data = BuildingSaveData.load(wire, level.getServer());
        var row = data.buildings.stream().filter(b -> b.originPos.equals(old.originPos)
                && b.ownerName.equals(old.ownerName) && b.building == old.getBuilding()).findFirst().orElseThrow();
        old.onRemoved(BuildingPlacement.RemovalReason.RELOAD);
        if (!BuildingServerEvents.getBuildings().remove(old)) throw new AssertionError("detached before reload");
        var p = row.building.createRestoredBuildingPlacement(level, row.originPos, row.rotation, row.ownerName, row.dataStorage);
        if (p == null || p == old) throw new AssertionError("missing fresh restore factory result");
        p.dataStorage = row.dataStorage; // package-private: the real per-placement load boundary
        p.isBuilt = row.isBuilt;
        p.partialBlocksDestroyed = row.partialBlocksDestroyed;
        p.scenarioRoleIndex = row.scenarioRoleIndex;
        BuildingServerEvents.getBuildings().add(p);
        p.afterRestoredFromSave();
        return p;
    }
}
