package com.dreamseekers.building;
import net.minecraft.nbt.CompoundTag;
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
 private final DreamTemplateModel.State template;
 public DreamLaboratoryPlacement(DreamLaboratory definition,Level level,BlockPos pos,Rotation rotation,String owner){
     this(definition,level,pos,rotation,owner,DreamTemplateModel.LABORATORY.fresh(level));
 }
 public DreamLaboratoryPlacement(DreamLaboratory definition,Level level,BlockPos pos,Rotation rotation,String owner,DreamTemplateModel.State template){
     super(definition,level,pos,rotation,owner,template.absolute(level,pos,rotation),false);
     this.template=template;
     template.save(this,super.getDataStorage());
     lifecycle=new DreamFunctionLifecycle(this,DreamBuildingSpec.RESONANCE_LABORATORY,()->super.getDataStorage());
 }
    // Ordinary revisions are fixed at construction; never look up an arbitrary CHANGE_STRUCTURE path.
    @Override public void changeStructure(String name) {
    }
    @Override public CompoundTag structureSnapshot() {
        return template.snapshot(this);
    }
    @Override public boolean acceptsStructureSnapshot(CompoundTag tag) {
        return template.accepts(tag);
    }
    @Override public void applyStructureSnapshot(CompoundTag tag, boolean initial) {
        template.apply(this, tag, initial, this::restoreGeometry, value -> highestBlockCountReached = value);
    }
    @Override public void afterRestoredFromSave() {
        super.afterRestoredFromSave();
        highestBlockCountReached = template.restoredHighest(this);
    }
 @Override public DreamBuildingSpec spec(){return DreamBuildingSpec.RESONANCE_LABORATORY;}
 @Override public DataStorage getDataStorage(){if(lifecycle!=null)lifecycle.synchronizeLifecycle();
     var storage=super.getDataStorage();if(template!=null)template.save(this,storage);return storage;}
 @Override public DreamFunctionState.Snapshot stateSnapshot(){return lifecycle.stateSnapshot();}
 @Override public int lifecycleCount(DreamFunctionService.Lifecycle event){return lifecycle.lifecycleCount(event);}
 @Override public DreamFunctionResult request(ServerPlayer actor,DreamFunctionRequest request){return lifecycle.request(actor,request);}
 public boolean available(){lifecycle.synchronizeLifecycle();var s=stateSnapshot();return lifecycle.onServerThread()&&lifecycle.live()&&!lifecycle.terminal()&&s.supported()&&s.owner().equals(ownerName)&&isBuilt&&!isDestroyedServerside&&!shouldBeDestroyed()&&getLevel().isLoaded(minCorner)&&getLevel().isLoaded(maxCorner);}
 @Override public void tick(Level level){if(lifecycle.terminal())return;lifecycle.synchronizeLifecycle();super.tick(level);}
 @Override public void onBuilt(){if(lifecycle.terminal())return;super.onBuilt();lifecycle.synchronizeLifecycle();}
 @Override public void destroy(ServerLevel level){if(lifecycle.beginDestroy(level))super.destroy(level);}
 @Override public void onRemoved(RemovalReason reason){lifecycle.detach();productionQueue.clear();super.onRemoved(reason);}
}
