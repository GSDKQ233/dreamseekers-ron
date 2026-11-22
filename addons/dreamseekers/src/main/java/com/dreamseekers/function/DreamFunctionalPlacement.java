// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.function;
import com.dreamseekers.building.DreamTemplateModel;
import net.minecraft.nbt.CompoundTag;
import com.dreamseekers.building.DreamBuildingSpec;
import com.dreamseekers.building.DreamScaffoldBuilding;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.data.DataStorage;
import com.solegendary.reignofnether.building.data.DataType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

/** Scaffold roles keep ordinary BuildingPlacement semantics (no production queue); their functions run through DreamFunctionLifecycle. */
public class DreamFunctionalPlacement extends BuildingPlacement implements DreamFunctionalView {
    /** Compatibility alias: the codec remains registered exactly once under the original key. */
    public static final DataType<DreamFunctionState> STATE = DreamFunctionLifecycle.STATE;
    private final DreamScaffoldBuilding definition;
    private final DreamFunctionLifecycle lifecycle;
    public static void bootstrap() { }
    // A tower uses the geometry-only protected constructor and its own model/snapshot implementation.
    private DreamTemplateModel.State template;
    public DreamFunctionalPlacement(DreamScaffoldBuilding building, Level level, BlockPos pos,
                                    Rotation rotation, String owner) {
        this(building, level, pos, rotation, owner, DreamTemplateModel.forBuilding(building.spec().structureName()).fresh(level));
    }
    public DreamFunctionalPlacement(DreamScaffoldBuilding building, Level level, BlockPos pos, Rotation rotation,
                                    String owner, DreamTemplateModel.State template) {
        this(building, level, pos, rotation, owner, template.absolute(level, pos, rotation));
        this.template = template;
        template.save(this, super.getDataStorage());
    }
    protected DreamFunctionalPlacement(DreamScaffoldBuilding building,Level level,BlockPos pos,
            Rotation rotation,String owner,java.util.ArrayList<com.solegendary.reignofnether.building.BuildingBlock> geometry) {
        super(building,level,pos,rotation,owner,geometry,false);
        definition = building;
        lifecycle = new DreamFunctionLifecycle(this, building.spec(), () -> super.getDataStorage());
    }
    // Ordinary revisions are fixed at construction; never look up an arbitrary CHANGE_STRUCTURE path.
    @Override public void changeStructure(String name) {
        if (template == null) super.changeStructure(name);
    }
    public DreamTemplateModel.State resourceResearchModel() { return template; }
    @Override public CompoundTag structureSnapshot() {
        return template == null ? super.structureSnapshot() : template.snapshot(this);
    }
    @Override public boolean acceptsStructureSnapshot(CompoundTag tag) {
        return template == null ? super.acceptsStructureSnapshot(tag) : template.accepts(tag);
    }
    @Override public void applyStructureSnapshot(CompoundTag tag, boolean initial) {
        if (template == null) { super.applyStructureSnapshot(tag, initial); return; }
        template.apply(this, tag, initial, this::restoreGeometry, value -> highestBlockCountReached = value);
    }
    @Override public void afterRestoredFromSave() {
        super.afterRestoredFromSave();
        if (template != null) highestBlockCountReached = template.restoredHighest(this);
    }
    @Override public DreamBuildingSpec spec() { return definition.spec(); }
    @Override public DataStorage getDataStorage() {
        if (lifecycle != null) lifecycle.synchronizeLifecycle();
        var storage = super.getDataStorage();
        if (template != null) template.save(this, storage);
        return storage;
    }
    @Override public DreamFunctionState.Snapshot stateSnapshot() { return lifecycle.stateSnapshot(); }
    /** Server-only transient actual-owner generation; unknown raw metadata cannot reset it. */
    public long functionalOwnerRevision() { return lifecycle.liveOwnerRevision(); }
    @Override public int lifecycleCount(DreamFunctionService.Lifecycle event) { return lifecycle.lifecycleCount(event); }
    @Override public DreamFunctionResult request(ServerPlayer actor, DreamFunctionRequest request) { return lifecycle.request(actor,request); }
    @Override public void onBuilt() {
        if (lifecycle.terminal()) return;
        super.onBuilt(); lifecycle.synchronizeLifecycle();
    }
    @Override public void tick(Level level) {
        if (lifecycle.terminal()) return;
        lifecycle.synchronizeLifecycle(); super.tick(level); lifecycle.synchronizeLifecycle();
    }
    @Override public void destroy(ServerLevel level) {
        if (lifecycle.beginDestroy(level)) super.destroy(level);
    }
    @Override public void onRemoved(RemovalReason reason) {
        com.dreamseekers.economy.ResourceBuildingResearch.detach(this);
        lifecycle.detach();
    }
}
