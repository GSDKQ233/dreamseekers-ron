// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.building;

import com.dreamseekers.function.DreamFunctionalPlacement;
import com.dreamseekers.production.DreamHallPlacement;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.production.*;
import com.solegendary.reignofnether.keybinds.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import java.util.ArrayList;

/** Existing dream_hall identity/geometry, with only explicitly supplied paid recipes enabled. */
public final class DreamHall extends ProductionBuilding {
    public DreamHall(ProductionItem boneWing,ProductionItem windKnight,ProductionItem stoneThrower,ProductionItem beetleMother,ProductionItem shadowGhost,ProductionItem shadowSquealGhost,ProductionItem terrorbeak,ProductionItem friendlyGhost,ProductionItem shadowHand,ProductionItem ashBoneWing,ProductionItem shadowGolem,ProductionItem weepingSquealGhost,ProductionItem leftHand,ProductionItem rightHand) {
        super(DreamBuildingSpec.DREAM_HALL.structureName(), DreamBuildingSpec.DREAM_HALL.newProvisionalCost(), false);
        DreamFunctionalPlacement.bootstrap();
        name=spec().displayName(); portraitBlock=spec().portraitBlock(); icon=spec().icon();
        maxHealth=DreamBuildingSpec.DREAM_HALL.maxHealth();
        buildTimeModifier=DreamBuildingSpec.DREAM_HALL.buildTimeModifier();
        startingBlockTypes.add(Blocks.STONE_BRICKS);
        spawnRadiusOffset=2;
        productions=new com.dreamseekers.production.DreamPagedProductionItemList();
        productions.add(boneWing,Keybindings.abilitySlot1);
        productions.add(windKnight,Keybindings.abilitySlot2);
        productions.add(stoneThrower,Keybindings.abilitySlot3);
        productions.add(beetleMother,Keybindings.abilitySlot4);
        productions.add(shadowGhost,Keybindings.abilitySlot5);
        productions.add(shadowSquealGhost,Keybindings.abilitySlot6);
        productions.add(terrorbeak,Keybindings.abilitySlot1); // page 2 Q; complete server whitelist remains unchanged by page
        productions.add(friendlyGhost,Keybindings.abilitySlot2); // page 2 W
        productions.add(shadowHand,Keybindings.abilitySlot3); // page 2 E
        productions.add(ashBoneWing,Keybindings.abilitySlot4); // page 2 R
        productions.add(shadowGolem,Keybindings.abilitySlot5); // page 2 T
        productions.add(weepingSquealGhost,Keybindings.abilitySlot6); // page 2 Y
        productions.add(leftHand,Keybindings.abilitySlot1); // page 3 Q
        productions.add(rightHand,Keybindings.abilitySlot2); // page 3 W
    }
    public DreamBuildingSpec spec() { return DreamBuildingSpec.DREAM_HALL; }
    @Override public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String owner) {
        return new DreamHallPlacement(this,level,pos,rotation,owner);
    }
    @Override public ArrayList<BuildingBlock> getRelativeBlockData(LevelAccessor level) { return DreamBuildingSupport.load(spec().structureName()); }
    @Override public BuildingPlaceButton getBuildButton(Keybinding key) { return DreamBuildingSupport.button(this,key); }
    @Override public String getBuildAvailabilityError(String owner) { return DreamBuildingSupport.availability(this,owner); }
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
