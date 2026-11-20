package com.dreamseekers.building;
import com.dreamseekers.progression.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.keybinds.Keybinding;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import java.util.ArrayList;
/** Same saved laboratory identity and geometry, with actual paid repeatable research. */
public final class DreamLaboratory extends ProductionBuilding {
 public DreamLaboratory(){super(DreamBuildingSpec.RESONANCE_LABORATORY.structureName(),DreamBuildingSpec.RESONANCE_LABORATORY.newProvisionalCost(),false);
  name=DreamBuildingSpec.RESONANCE_LABORATORY.displayName();portraitBlock=DreamBuildingSpec.RESONANCE_LABORATORY.portraitBlock();icon=DreamBuildingSpec.RESONANCE_LABORATORY.icon();
  maxHealth=DreamBuildingSpec.PROVISIONAL_MAX_HEALTH;buildTimeModifier=DreamBuildingSpec.PROVISIONAL_BUILD_TIME_MODIFIER;startingBlockTypes.add(Blocks.STONE_BRICKS);DreamTemplateModel.bootstrap();startingBlockTypes.addAll(DreamBuildingSupport.restyledFoundationBlocks());canSetRallyPoint=false;
  productions=new DreamRepeatProductionList();DreamLaboratoryPlacement.bootstrap();}
 @Override public BuildingPlacement createBuildingPlacement(Level level,BlockPos pos,Rotation rotation,String owner){return new DreamLaboratoryPlacement(this,level,pos,rotation,owner);}
 @Override public BuildingPlacement createRestoredBuildingPlacement(Level level,BlockPos pos,Rotation rotation,String owner,com.solegendary.reignofnether.building.data.DataStorage saved){return new DreamLaboratoryPlacement(this,level,pos,rotation,owner,saved);}
 @Override public boolean acceptsInitialStructureSnapshot(net.minecraft.nbt.CompoundTag snapshot){return DreamTemplateModel.acceptsInitialSnapshot(snapshot);}
 /** Preview and new construction use the restyled template. */
 @Override public ArrayList<BuildingBlock> getRelativeBlockData(LevelAccessor level){return DreamTemplateModel.blocks(structureName,DreamTemplateModel.DYEDREAM);}
 @Override public BuildingPlaceButton getBuildButton(Keybinding key){return DreamBuildingSupport.button(this,key);}
 @Override public String getBuildAvailabilityError(String owner){return DreamBuildingSupport.availability(this,owner);}
 @Override public boolean requiresStrictPlacementOwnerCheck(){return true;}

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
