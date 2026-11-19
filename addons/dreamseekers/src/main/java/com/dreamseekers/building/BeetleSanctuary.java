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

/** Original wing-shaped training hall, using actual addon entity production. */
public final class BeetleSanctuary extends ProductionBuilding {
    public static final String STRUCTURE = "beetle_sanctuary";
    public static final ResourceCost COST = ResourceCost.Building(0, 160, 50, 0);

    public BeetleSanctuary(ProductionItem... troops) {
        super(STRUCTURE, COST, false);
        if (troops.length == 0 || troops.length > 6)
            throw new IllegalArgumentException("Beetle Sanctuary needs one to six real unit productions");
        name = "Beetle Sanctuary";
        portraitBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("pasterdream", "calcite_tiles"));
        icon = ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/beetle_sanctuary.png");
        maxHealth = 220;
        buildTimeModifier = 0.7f;
        spawnRadiusOffset = 2;
        startingBlockTypes.add(Blocks.STONE_BRICKS);
        Keybinding[] slots = {Keybindings.abilitySlot1, Keybindings.abilitySlot2, Keybindings.abilitySlot3, Keybindings.abilitySlot4, Keybindings.abilitySlot5, Keybindings.abilitySlot6};
        for (int i = 0; i < troops.length; i++)
            productions.add(troops[i], slots[i]);
    }

    @Override public com.solegendary.reignofnether.building.BuildingPlacement createBuildingPlacement(
            net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,
            net.minecraft.world.level.block.Rotation rotation, String owner) {
        return new com.dreamseekers.production.DreamProductionPlacement(this,level,pos,rotation,owner,
                com.solegendary.reignofnether.building.BuildingUtils.getAbsoluteBlockData(getRelativeBlockData(level),level,pos,rotation),isCapitol);
    }

    @Override public ArrayList<BuildingBlock> getRelativeBlockData(LevelAccessor level) { return DreamBuildingSupport.load(STRUCTURE); }
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
