// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.function;
import com.dreamseekers.building.DreamBuildingSpec;
import com.dreamseekers.building.DreamScaffoldBuilding;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.data.DataStorage;
import com.solegendary.reignofnether.building.data.DataType;
import net.minecraft.core.BlockPos;
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
    public DreamFunctionalPlacement(DreamScaffoldBuilding building, Level level, BlockPos pos,
                                    Rotation rotation, String owner) {
        this(building,level,pos,rotation,owner,
                BuildingUtils.getAbsoluteBlockData(building.getRelativeBlockData(level),level,pos,rotation));
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
