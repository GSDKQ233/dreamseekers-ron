// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.building;
import com.solegendary.reignofnether.building.data.DataStorage;
import net.minecraft.nbt.CompoundTag;

import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.economy.ResourceBuildingResearch;
import com.dreamseekers.function.DreamFunctionalPlacement;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.keybinds.Keybinding;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
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
        DreamTemplateModel.bootstrap();
        this.spec = spec;
        name = spec.displayName();
        portraitBlock = spec.portraitBlock();
        icon = spec.icon();
        maxHealth = spec.maxHealth();
        buildTimeModifier = spec.buildTimeModifier();
        // All eight templates have stone-brick foundations at y=0.
        // Only the ordinary first foundation layer is instant; workers build the rest.
        startingBlockTypes.add(Blocks.STONE_BRICKS);
        // Explicitly approved logistics; no inherited stockpile research or free resource generation.
        canAcceptResources = spec == DreamBuildingSpec.DREAM_DEPOT;
        // Defaults deliberately retained: no native abilities or self-build,
        // capture, production, extra sight range, targeting or damage source.
    }

    public DreamBuildingSpec spec() { return spec; }
    @Override public int getPopulationSupply(BuildingPlacement p) {
        if (spec != DreamBuildingSpec.RESONANCE_DAIS) return super.getPopulationSupply(p);
        if (p == null || p.getBuilding() != this || !p.isBuilt || p.ownerName.isBlank()
                || p.isDestroyedServerside || p.shouldBeDestroyed() || p.getHealth() <= 0
                || ResourceBuildingResearch.instance(p) == null) return 0;
        // Capacity is physical, not an aura slot. Don't drop it when the client's camera unloads the footprint.
        return ResourceBuildingResearch.isUpgraded(p) ? ResourceBuildingResearch.DAIS_UPGRADED_SUPPLY : cost.population;
    }

    @Override public void tick(Level level, BuildingPlacement placement) {
        if (spec == DreamBuildingSpec.RESONANCE_DAIS) {
            if (level instanceof ServerLevel server) ResourceBuildingResearch.tick(server, placement);
        } else DreamPassiveEconomy.tick(level, placement);
    }

    @Override public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new DreamFunctionalPlacement(this, level, pos, rotation, ownerName);
    }

    @Override public BuildingPlacement createRestoredBuildingPlacement(Level level, BlockPos pos, Rotation rotation,
                                                                      String ownerName, DataStorage saved) {
        return new DreamFunctionalPlacement(this, level, pos, rotation, ownerName,
                DreamTemplateModel.forBuilding(spec.structureName()).restore(saved));
    }

    @Override public boolean acceptsInitialStructureSnapshot(CompoundTag snapshot) {
        return DreamTemplateModel.forBuilding(spec.structureName()).validSnapshot(snapshot);
    }

    @Override public ArrayList<BuildingBlock> getRelativeBlockData(LevelAccessor level) {
        return DreamTemplateModel.forBuilding(spec.structureName()).blocks(DreamTemplateModel.Revision.REVIEWED);
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
