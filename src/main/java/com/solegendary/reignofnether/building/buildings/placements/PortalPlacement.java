package com.solegendary.reignofnether.building.buildings.placements;

import static com.solegendary.reignofnether.building.BuildingUtils.getAbsoluteBlockData;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.survival.SurvivalServerEvents;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingBlockData;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.NetherZone;
import com.solegendary.reignofnether.building.buildings.piglins.PortalCivilian;
import com.solegendary.reignofnether.building.buildings.piglins.PortalMilitary;
import com.solegendary.reignofnether.building.buildings.piglins.PortalTransport;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;

public class PortalPlacement extends ProductionPlacement {
    public enum PortalType {
        BASIC, CIVILIAN, MILITARY, TRANSPORT
    }

    public final static float NON_NETHER_BUILD_TIME_MODIFIER = 2.0f;

    public BlockPos destination; // for transport portals

    public NetherZone netherConversionZone = null;

    public PortalPlacement(Building building, Level level, BlockPos originPos, Rotation rotation, String ownerName, ArrayList<BuildingBlock> blocks, boolean isCapitol) {
        super(building, level, originPos, rotation, ownerName, blocks, isCapitol);
        this.allowProdWhileBuilding = true;
    }

    public PortalType getPortalType() {
        if (getBuilding() instanceof PortalCivilian)
            return PortalType.CIVILIAN;
        else if (getBuilding() instanceof PortalMilitary)
            return PortalType.MILITARY;
        else if (getBuilding() instanceof PortalTransport)
            return PortalType.TRANSPORT;
        return PortalType.BASIC;
    }

    @Override
    public void tick(Level tickLevel) {
        super.tick(tickLevel);

        if (!this.getLevel().isClientSide() && this.getBlocksPlaced() >= getBlocksTotal() && this.getLevel()
                .getBlockState(this.centrePos)
                .isAir()) {
            this.getLevel().setBlockAndUpdate(this.centrePos, Blocks.FIRE.defaultBlockState());
        }
    }

    @Override
    public boolean shouldBeDestroyed() {
        boolean shouldBeDestroyed = (!getLevel().isClientSide() && SurvivalServerEvents.isRetiredWavePortal(this)) || super.shouldBeDestroyed();
        if (shouldBeDestroyed) {
            disconnectPortal();
        }
        return shouldBeDestroyed;
    }

    @Override
    public void destroy(ServerLevel serverLevel) {
        disconnectPortal();
        super.destroy(serverLevel);
        SurvivalServerEvents.retiredWavePortalRemoved(this);
    }

    public void disconnectPortal() {
        if (destination != null) {
            BuildingPlacement targetBuilding = BuildingUtils.findBuilding(getLevel(), destination);
            if (targetBuilding instanceof PortalPlacement targetPortal && getBuilding() instanceof PortalTransport) {
                targetPortal.destination = null;
            }
        }
        destination = null;
    }

    @Override
    public boolean canDestroyBlock(BlockPos relativeBp) {
        if (getBuilding() == Buildings.PORTAL_POCKET) {
            return true;
        } else {
            BlockPos worldBp = relativeBp.offset(this.originPos);
            Block block = this.getLevel().getBlockState(worldBp).getBlock();
            return block != Blocks.OBSIDIAN && block != Blocks.NETHER_PORTAL;
        }
    }

    public void changePortalStructure(PortalType portalType) {
        String newStructureName = "";
        switch (portalType) {
            case CIVILIAN -> {
                setBuilding(Buildings.PORTAL_CIVILIAN);
                newStructureName = PortalCivilian.structureName;
            }
            case MILITARY -> {
                setBuilding(Buildings.PORTAL_MILITARY);
                newStructureName = PortalMilitary.structureName;
            }
            case TRANSPORT -> {
                setBuilding(Buildings.PORTAL_TRANSPORT);
                newStructureName = PortalTransport.structureName;
            }
        }

        updateButtons();
        if (!newStructureName.isEmpty()) {
            ArrayList<BuildingBlock> newBlocks = BuildingBlockData.getBuildingBlocksFromNbt(newStructureName, this.getLevel());
            setBlocks(getAbsoluteBlockData(newBlocks, this.getLevel(), originPos, rotation));
            super.refreshBlocks();
        }
    }

    public void checkAndConsumeChestItems() {
        ManualChestDeposit.collect(this, false);
    }

    public boolean hasDestination() {
        return isBuilt &&
                getBuilding() instanceof PortalTransport &&
                destination != null &&
                !destination.equals(new BlockPos(0,0,0));
    }
}
