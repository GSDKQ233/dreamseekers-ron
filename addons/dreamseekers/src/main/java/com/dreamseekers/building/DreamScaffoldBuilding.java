// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.building;

import com.dreamseekers.function.DreamFunctionalPlacement;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.keybinds.Keybinding;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.Objects;

/**
 * A paid, normally constructed RoN building. Shadow mine/furnace now have explicit passive income.
 * Depot opts into native drop-off. Dedicated subclasses opt into reviewed roles; no generic production.
 */
public class DreamScaffoldBuilding extends Building {
    private final DreamBuildingSpec spec;

    public DreamScaffoldBuilding(DreamBuildingSpec spec) {
        super(Objects.requireNonNull(spec).structureName(), spec.newProvisionalCost(), false);
        // Register the persisted codec before ordinary world save-data decoding.
        DreamFunctionalPlacement.bootstrap();
        this.spec = spec;
        name = spec.displayName();
        portraitBlock = spec.portraitBlock();
        icon = spec.icon();
        maxHealth = DreamBuildingSpec.PROVISIONAL_MAX_HEALTH;
        buildTimeModifier = DreamBuildingSpec.PROVISIONAL_BUILD_TIME_MODIFIER;
        // All eight templates have stone-brick foundations at y=0.
        // Only the ordinary first foundation layer is instant; workers build the rest.
        startingBlockTypes.add(Blocks.STONE_BRICKS);
        DreamTemplateModel.bootstrap();
        // The restyled templates keep the same foundation cells in PasterDream stone.
        startingBlockTypes.addAll(DreamBuildingSupport.restyledFoundationBlocks());
        // Explicitly approved logistics; no inherited stockpile research or free resource generation.
        canAcceptResources = spec == DreamBuildingSpec.DREAM_DEPOT;
        // Defaults deliberately retained: no supply, abilities, self-build,
        // capture, production, extra sight range, targeting or damage source.
    }

    public DreamBuildingSpec spec() { return spec; }
    @Override public void tick(Level level, BuildingPlacement placement) {
        com.dreamseekers.economy.DreamPassiveEconomy.tick(level, placement);
    }

    @Override public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new DreamFunctionalPlacement(this, level, pos, rotation, ownerName);
    }

    @Override public BuildingPlacement createRestoredBuildingPlacement(Level level, BlockPos pos, Rotation rotation,
            String ownerName, com.solegendary.reignofnether.building.data.DataStorage saved) {
        return new DreamFunctionalPlacement(this, level, pos, rotation, ownerName, saved);
    }

    @Override public boolean acceptsInitialStructureSnapshot(net.minecraft.nbt.CompoundTag snapshot) {
        return DreamTemplateModel.acceptsInitialSnapshot(snapshot);
    }

    /** Preview and new construction use the restyled template. */
    @Override public ArrayList<BuildingBlock> getRelativeBlockData(LevelAccessor level) {
        return DreamTemplateModel.blocks(spec.structureName(), DreamTemplateModel.DYEDREAM);
    }

    @Override public BuildingPlaceButton getBuildButton(Keybinding key) {
        return DreamBuildingSupport.button(this, key);
    }

    @Override public String getBuildAvailabilityError(String owner) {
        return DreamBuildingSupport.availability(this, owner);
    }

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
