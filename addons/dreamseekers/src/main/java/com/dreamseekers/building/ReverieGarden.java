// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.building;
import net.minecraft.nbt.CompoundTag;

import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.buildings.shared.AbstractFarm;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;

/** Automatic food economy; decorative wheat is neither a crop nor a resource source. */
public final class ReverieGarden extends AbstractFarm {
    public static final String STRUCTURE = "reverie_garden";
    public static final ResourceCost COST = ResourceCost.Building(0, 200, 0, 8);

    public ReverieGarden() {
        super(STRUCTURE, COST, false);
        name = "Reverie Garden";
        portraitBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("pasterdream", "dyedream_leaves"));
        icon = ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/reverie_garden.png");
        maxHealth = 130;
        buildTimeModifier = 0.8f;
        explodeChance = 0;
        startingBlockTypes.add(Blocks.STONE_BRICKS);
        DreamTemplateModel.bootstrap();
        // Retain the existing foundation material contract. Dyedream dirt beds need no water.
        startingBlockTypes.add(BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("pasterdream", "dyedream_planks")));
    }

    @Override public boolean isCropSoil(BlockState state) {
        return super.isCropSoil(state) || BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(
                ResourceLocation.fromNamespaceAndPath("pasterdream", "dyedream_dirt"));
    }

    @Override public boolean allowsManualFarming() { return false; }
    @Override public boolean allowsCropReplant() { return false; }
    @Override public boolean allowsCropGrowth() { return false; }
    @Override public com.solegendary.reignofnether.building.BuildingPlacement createBuildingPlacement(
            net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.Rotation rotation, String owner) {
        return new com.dreamseekers.economy.DreamGardenPlacement(this, level, pos, rotation, owner);
    }
    @Override public com.solegendary.reignofnether.building.BuildingPlacement createRestoredBuildingPlacement(
            net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.Rotation rotation, String owner,
            com.solegendary.reignofnether.building.data.DataStorage savedData) {
        return new com.dreamseekers.economy.DreamGardenPlacement(this,level,pos,rotation,owner,savedData);
    }
    @Override public void tick(net.minecraft.world.level.Level level, com.solegendary.reignofnether.building.BuildingPlacement placement) {
        com.dreamseekers.economy.DreamPassiveEconomy.tick(level, placement);
    }
    @Override public boolean acceptsInitialStructureSnapshot(CompoundTag snapshot) {
        return DreamTemplateModel.GARDEN.validSnapshot(snapshot);
    }
    @Override public ArrayList<BuildingBlock> getRelativeBlockData(LevelAccessor level) {
        return DreamTemplateModel.GARDEN.blocks(DreamTemplateModel.Revision.REVIEWED);
    }
    @Override public BuildingPlaceButton getBuildButton(Keybinding key) { return DreamBuildingSupport.button(this, key); }
    @Override public String getBuildAvailabilityError(String owner) { return DreamBuildingSupport.availability(this, owner); }
    @Override public boolean requiresStrictPlacementOwnerCheck() { return true; }

    @Override public net.minecraft.resources.ResourceLocation getPortraitIcon(com.solegendary.reignofnether.building.BuildingPlacement placement) {
        return icon;
    }
    @Override public void onConstructionPayment(com.solegendary.reignofnether.building.BuildingPlacement p,int f,int w,int o) {
        com.dreamseekers.economy.DreamConstructionPayment.record(p,f,w,o);
    }
    @Override public com.solegendary.reignofnether.resources.ResourceCost takeConstructionRefundCost(com.solegendary.reignofnether.building.BuildingPlacement p) {
        return com.dreamseekers.economy.DreamConstructionPayment.take(p);
    }
}
