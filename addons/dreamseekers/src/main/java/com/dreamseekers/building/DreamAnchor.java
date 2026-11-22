// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.building;

import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.resources.ResourceCost;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import java.util.ArrayList;

/** A genuine RoN capitol: worker queue, rally point, drop-off and population. */
public final class DreamAnchor extends ProductionBuilding {
    public static final String STRUCTURE = "dream_anchor";
    public static final ResourceCost COST = ResourceCost.Building(0, 400, 100, 15);

    public DreamAnchor(ProductionItem worker) {
        super(STRUCTURE, COST, true);
        name = "Dream Anchor";
        portraitBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("pasterdream", "dyedream_planks"));
        icon = ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/dream_anchor.png");
        maxHealth = 380;
        buildTimeModifier = 0.4f;
        canAcceptResources = true;
        spawnRadiusOffset = 2;
        startingBlockTypes.add(Blocks.STONE_BRICKS); // Legacy base foundation remains supported.
        // New PD-decorated bases must seed real foundation blocks before native destruction checks.
        startingBlockTypes.add(BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(
                "pasterdream", "bricks_dyedreamquartz_block")));
        productions.add(worker, Keybindings.abilitySlot1);
    }

    @Override public com.solegendary.reignofnether.building.BuildingPlacement createBuildingPlacement(
            net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.Rotation rotation, String owner) {
        return new com.dreamseekers.worldtree.DreamAnchorPlacement(this,level,pos,rotation,owner,
                com.solegendary.reignofnether.building.BuildingUtils.getAbsoluteBlockData(getRelativeBlockData(level),level,pos,rotation),isCapitol);
    }

    @Override public com.solegendary.reignofnether.building.BuildingPlacement createRestoredBuildingPlacement(
            net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.Rotation rotation,String owner,
            com.solegendary.reignofnether.building.data.DataStorage savedData){
        // Absence predates the upgrade subsystem, and is always legacy base (never today's default).
        boolean present=false;for(var entry:savedData.write())if(entry instanceof net.minecraft.nbt.CompoundTag tag&&"dreamseekers:world_tree".equals(tag.getString("key")))present=true;
        if(!present){var legacy=new com.dreamseekers.worldtree.WorldTreeState();legacy.model=com.dreamseekers.worldtree.WorldTreeModel.LEGACY_BASE;savedData.setData(com.dreamseekers.worldtree.DreamWorldTreeService.STATE,legacy);}
        var state=savedData.getData(com.dreamseekers.worldtree.DreamWorldTreeService.STATE);
        var descriptor=state.supported?state.model:com.dreamseekers.worldtree.WorldTreeModel.LEGACY_BASE;
        return new com.dreamseekers.worldtree.DreamAnchorPlacement(this,level,pos,rotation,owner,
            com.solegendary.reignofnether.building.BuildingUtils.getAbsoluteBlockData(descriptor.blocks(),level,pos,rotation),isCapitol);
    }

    @Override public boolean acceptsInitialStructureSnapshot(net.minecraft.nbt.CompoundTag snapshot){return com.dreamseekers.worldtree.DreamAnchorPlacement.validSnapshot(snapshot);}
    @Override public int getUpgradeLevel(com.solegendary.reignofnether.building.BuildingPlacement p){return com.dreamseekers.worldtree.DreamWorldTreeService.isAscended(p)?1:0;}
    @Override public String getUpgradedStructureName(int level){return level==1?com.dreamseekers.worldtree.DreamWorldTreeService.STRUCTURE:STRUCTURE;}
    @Override public ArrayList<BuildingBlock> getRelativeBlockData(LevelAccessor level) { return com.dreamseekers.worldtree.WorldTreeModel.CURRENT_BASE.blocks(); }
    @Override public BuildingPlaceButton getBuildButton(Keybinding key) { return DreamBuildingSupport.button(this, key); }
    @Override public String getBuildAvailabilityError(String owner) { return DreamBuildingSupport.availability(this, owner); }
    @Override public boolean requiresStrictPlacementOwnerCheck() { return true; }

    @Override public net.minecraft.resources.ResourceLocation getPortraitIcon(com.solegendary.reignofnether.building.BuildingPlacement placement) {
        if (placement instanceof com.dreamseekers.worldtree.DreamAnchorPlacement anchor) {
            return switch (anchor.model().branch) {
                case SHADOW, COMBINED -> ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/dream_anchor_shadow.png");
                case WIND -> ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/dream_anchor_wind.png");
                default -> icon;
            };
        }
        return icon;
    }
    @Override public void onConstructionPayment(com.solegendary.reignofnether.building.BuildingPlacement p,int f,int w,int o) {
        com.dreamseekers.economy.DreamConstructionPayment.record(p,f,w,o);
    }
    @Override public com.solegendary.reignofnether.resources.ResourceCost takeConstructionRefundCost(com.solegendary.reignofnether.building.BuildingPlacement p) {
        return com.dreamseekers.economy.DreamConstructionPayment.take(p);
    }
}
