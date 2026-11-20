package com.dreamseekers.building;
import com.dreamseekers.function.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.data.DataStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
/** Preserves the scaffold lifecycle codec; old completed labs gain only the explicitly whitelisted queues. */
public final class DreamLaboratoryPlacement extends ProductionPlacement implements DreamFunctionalView {
 private final DreamFunctionLifecycle lifecycle;
 public static void bootstrap(){DreamFunctionalPlacement.bootstrap();}
 public DreamLaboratoryPlacement(DreamLaboratory definition,Level level,BlockPos pos,Rotation rotation,String owner){this(definition,level,pos,rotation,owner,DreamTemplateModel.DYEDREAM);}
 /** Saved-entry route: a laboratory saved before the template revision existed keeps its original template. */
 public DreamLaboratoryPlacement(DreamLaboratory definition,Level level,BlockPos pos,Rotation rotation,String owner,DataStorage saved){this(definition,level,pos,rotation,owner,DreamTemplateModel.restoredRevision(saved));}
 private DreamLaboratoryPlacement(DreamLaboratory definition,Level level,BlockPos pos,Rotation rotation,String owner,int revision){super(definition,level,pos,rotation,owner,BuildingUtils.getAbsoluteBlockData(DreamTemplateModel.blocks(DreamBuildingSpec.RESONANCE_LABORATORY.structureName(),revision),level,pos,rotation),false);lifecycle=new DreamFunctionLifecycle(this,DreamBuildingSpec.RESONANCE_LABORATORY,()->super.getDataStorage());withTemplate(DreamBuildingSpec.RESONANCE_LABORATORY.structureName(),revision);}
    private DreamTemplateModel.State template;
    /** Opt-in for a building with a restyled template; the anchor keeps its own model. */
    private DreamLaboratoryPlacement withTemplate(String base,int revision) {
        template=new DreamTemplateModel.State(base,revision);
        if(revision==DreamTemplateModel.DYEDREAM&&!getLevel().isClientSide())
            DreamTemplateModel.write(super.getDataStorage(),revision);
        return this;
    }
    public int templateRevision(){return template==null?0:template.revision();}
    @Override public net.minecraft.nbt.CompoundTag structureSnapshot() {
        return template==null?super.structureSnapshot():template.snapshot(getLevel().isClientSide(),
                getHighestBlockCountReached(),getBlocksPlaced(),partialBlocksDestroyed);
    }
    @Override public boolean acceptsStructureSnapshot(net.minecraft.nbt.CompoundTag snapshot) {
        return template==null?super.acceptsStructureSnapshot(snapshot):template.accepts(snapshot);
    }
    @Override public void applyStructureSnapshot(net.minecraft.nbt.CompoundTag snapshot,boolean initial) {
        if(template==null){super.applyStructureSnapshot(snapshot,initial);return;}
        if(!getLevel().isClientSide())return;
        boolean legacy=DreamTemplateModel.validLegacySnapshot(snapshot);
        if(template.switchFor(snapshot,initial))
            restoreGeometry(com.solegendary.reignofnether.building.BuildingUtils.getAbsoluteBlockData(template.blocks(),getLevel(),originPos,rotation),
                    legacy?snapshot.getInt("highest"):2,legacy?snapshot.getDouble("partial"):0);
        if(legacy)setServerBlocksPlaced(snapshot.getInt("placed"));
    }
 @Override public DreamBuildingSpec spec(){return DreamBuildingSpec.RESONANCE_LABORATORY;}
 @Override public DataStorage getDataStorage(){if(lifecycle!=null)lifecycle.synchronizeLifecycle();return super.getDataStorage();}
 @Override public DreamFunctionState.Snapshot stateSnapshot(){return lifecycle.stateSnapshot();}
 @Override public int lifecycleCount(DreamFunctionService.Lifecycle event){return lifecycle.lifecycleCount(event);}
 @Override public DreamFunctionResult request(ServerPlayer actor,DreamFunctionRequest request){return lifecycle.request(actor,request);}
 public boolean available(){lifecycle.synchronizeLifecycle();var s=stateSnapshot();return lifecycle.onServerThread()&&lifecycle.live()&&!lifecycle.terminal()&&s.supported()&&s.owner().equals(ownerName)&&isBuilt&&!isDestroyedServerside&&!shouldBeDestroyed()&&getLevel().isLoaded(minCorner)&&getLevel().isLoaded(maxCorner);}
 @Override public void tick(Level level){if(lifecycle.terminal())return;lifecycle.synchronizeLifecycle();super.tick(level);}
 @Override public void onBuilt(){if(lifecycle.terminal())return;super.onBuilt();lifecycle.synchronizeLifecycle();}
 @Override public void destroy(ServerLevel level){if(lifecycle.beginDestroy(level))super.destroy(level);}
 @Override public void onRemoved(RemovalReason reason){lifecycle.detach();productionQueue.clear();super.onRemoved(reason);}
}
