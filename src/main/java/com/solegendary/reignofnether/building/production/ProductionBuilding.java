package com.solegendary.reignofnether.building.production;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.resources.ResourceCost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

public abstract class ProductionBuilding extends Building {
    public ProductionItemList productions = new ProductionItemList();
    public boolean canSetRallyPoint = true;
    public float spawnRadiusOffset = 1.0F;

    public ProductionBuilding(String structureName, ResourceCost cost, boolean isCapitol) {
        super(structureName, cost, isCapitol);
    }

    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new ProductionPlacement(this, level, pos, rotation, ownerName, BuildingUtils.getAbsoluteBlockData(this.getRelativeBlockData(level), level, pos, rotation), this.isCapitol);
    }

    public BlockPos getDefaultOutdoorSpawnPoint(BlockPos minCorner, ProductionPlacement pp) {
        return minCorner.offset((int) -spawnRadiusOffset, 0, (int) -spawnRadiusOffset);
    }

    /** No terrain or no candidate within this attempt's work budget means hold the paid order. */
    @javax.annotation.Nullable
    public BlockPos getIndoorSpawnPoint(ServerLevel level, BuildingPlacement placement) {
        BlockPos centre = placement.centrePos;
        if (!level.hasChunkAt(centre)) return null;
        var y = IndoorSpawnSearch.find(centre.getY(), level.getMinBuildHeight(), level.getMaxBuildHeight(),
                floor -> !level.getBlockState(new BlockPos(centre.getX(), floor, centre.getZ())).isAir());
        if (y.isEmpty()) return null;
        return adjustIndoorSpawnPoint(new BlockPos(centre.getX(), y.getAsInt(), centre.getZ()), placement);
    }

    protected BlockPos adjustIndoorSpawnPoint(BlockPos grounded, BuildingPlacement placement) {
        return grounded;
    }
}
