// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.function;
import com.dreamseekers.building.DreamBuildingSpec;
import com.dreamseekers.building.DreamScaffoldBuilding;
import com.dreamseekers.building.DreamTemplateModel;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.data.DataStorage;
import com.solegendary.reignofnether.building.data.DataType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

/** The seven dormant roles retain ordinary BuildingPlacement semantics, not production. */
public class DreamFunctionalPlacement extends BuildingPlacement implements DreamFunctionalView {
    /** Compatibility alias: the codec remains registered exactly once under the original key. */
    public static final DataType<DreamFunctionState> STATE = DreamFunctionLifecycle.STATE;
    private final DreamScaffoldBuilding definition;
    private final DreamFunctionLifecycle lifecycle;
    public static void bootstrap() { }
    private DreamTemplateModel.State template;
    public DreamFunctionalPlacement(DreamScaffoldBuilding building, Level level, BlockPos pos,
                                    Rotation rotation, String owner) {
        this(building,level,pos,rotation,owner,DreamTemplateModel.DYEDREAM);
    }
    /** Saved-entry route: a placement saved before the template revision existed keeps its original template. */
    public DreamFunctionalPlacement(DreamScaffoldBuilding building, Level level, BlockPos pos,
                                    Rotation rotation, String owner, DataStorage saved) {
        this(building,level,pos,rotation,owner,DreamTemplateModel.restoredRevision(saved));
    }
    private DreamFunctionalPlacement(DreamScaffoldBuilding building, Level level, BlockPos pos,
                                    Rotation rotation, String owner, int revision) {
        this(building,level,pos,rotation,owner,BuildingUtils.getAbsoluteBlockData(
                DreamTemplateModel.blocks(building.spec().structureName(),revision),level,pos,rotation));
        template = new DreamTemplateModel.State(building.spec().structureName(),revision);
        if (revision == DreamTemplateModel.DYEDREAM && !level.isClientSide())
            DreamTemplateModel.write(super.getDataStorage(),revision);
    }
    public int templateRevision() { return template == null ? 0 : template.revision(); }
    @Override public CompoundTag structureSnapshot() {
        return template == null ? super.structureSnapshot() : template.snapshot(getLevel().isClientSide(),
                getHighestBlockCountReached(),getBlocksPlaced(),partialBlocksDestroyed);
    }
    @Override public boolean acceptsStructureSnapshot(CompoundTag snapshot) {
        return template == null ? super.acceptsStructureSnapshot(snapshot) : template.accepts(snapshot);
    }
    @Override public void applyStructureSnapshot(CompoundTag snapshot, boolean initial) {
        if (template == null) { super.applyStructureSnapshot(snapshot,initial); return; }
        if (!getLevel().isClientSide()) return;
        boolean legacy = DreamTemplateModel.validLegacySnapshot(snapshot);
        if (template.switchFor(snapshot,initial))
            restoreGeometry(BuildingUtils.getAbsoluteBlockData(template.blocks(),getLevel(),originPos,rotation),
                    legacy ? snapshot.getInt("highest") : 2, legacy ? snapshot.getDouble("partial") : 0);
        if (legacy) setServerBlocksPlaced(snapshot.getInt("placed"));
    }
    protected DreamFunctionalPlacement(DreamScaffoldBuilding building,Level level,BlockPos pos,
            Rotation rotation,String owner,java.util.ArrayList<com.solegendary.reignofnether.building.BuildingBlock> geometry) {
        super(building,level,pos,rotation,owner,geometry,false);
        definition = building;
        lifecycle = new DreamFunctionLifecycle(this, building.spec(), () -> super.getDataStorage());
    }
    @Override public DreamBuildingSpec spec() { return definition.spec(); }
    @Override public DataStorage getDataStorage() {
        if (lifecycle != null) lifecycle.synchronizeLifecycle();
        return super.getDataStorage();
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
    @Override public void onRemoved(RemovalReason reason) { lifecycle.detach(); }
}
