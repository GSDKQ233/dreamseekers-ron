// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.production;

import com.dreamseekers.building.DreamHall;
import com.dreamseekers.building.DreamBuildingSpec;
import com.dreamseekers.function.*;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.data.DataStorage;
import com.solegendary.reignofnether.building.production.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.BuildingProductionClientboundPacket;
import com.solegendary.reignofnether.resources.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import java.util.*;

/** Hall-only production activation. Payment belongs to its original payer, never the current owner. */
public final class DreamHallPlacement extends DreamProductionPlacement implements DreamFunctionalView {
    private final DreamFunctionLifecycle lifecycle;
    private String observedUnitOwner;
    private DreamFunctionState.Snapshot lastSupportedUnitState;
    public DreamHallPlacement(DreamHall definition,Level level,BlockPos pos,Rotation rotation,String owner) {
        super(definition,level,pos,rotation,owner,
                BuildingUtils.getAbsoluteBlockData(definition.getRelativeBlockData(level),level,pos,rotation),false);
        lifecycle=new DreamFunctionLifecycle(this,definition.spec(),()->super.getDataStorage());
        observedUnitOwner=owner;
    }
    @Override public DreamBuildingSpec spec() { return DreamBuildingSpec.DREAM_HALL; }
    @Override public DataStorage getDataStorage() {
        synchronizeProductionLifecycle();
        return super.getDataStorage();
    }
    private void synchronizeProductionLifecycle() {
        if(lifecycle==null||!lifecycle.onServerThread()||lifecycle.terminal()||!lifecycle.live())return;
        if(!Objects.equals(observedUnitOwner,ownerName)) {
            advanceUnitBindingRevision();
            observedUnitOwner=ownerName;
            lastSupportedUnitState=null;
        }
        lifecycle.synchronizeLifecycle();
        var state=stateSnapshot();
        if(state.supported())lastSupportedUnitState=state;
    }
    @Override public long unitBindingRevision() {
        synchronizeProductionLifecycle();
        return super.unitBindingRevision();
    }
    @Override boolean quarantinesUnitBinding(DreamUnitReceipts.Binding binding) {
        // Retention is not completion authority. Only this exact runtime host's previous
        // supported identity can hold a paid entry while newer raw metadata is opaque.
        return lifecycle!=null&&!lifecycle.terminal()&&lifecycle.live()&&super.productionAvailable()
                &&!stateSnapshot().supported()&&lastSupportedUnitState!=null
                &&Objects.equals(lastSupportedUnitState.instanceId(),binding.placement())
                &&lastSupportedUnitState.ownerRevision()==binding.ownerRevision()
                &&lastSupportedUnitState.owner().equals(ownerName);
    }
    @Override public DreamFunctionState.Snapshot stateSnapshot() { return lifecycle.stateSnapshot(); }
    @Override public int lifecycleCount(DreamFunctionService.Lifecycle event) { return lifecycle.lifecycleCount(event); }
    @Override public DreamFunctionResult request(ServerPlayer actor,DreamFunctionRequest request) {
        synchronizeProductionLifecycle();
        return lifecycle.request(actor,request);
    }
    @Override public UUID unitPlacementIdentity(){return stateSnapshot().instanceId();}
    @Override public long unitOwnerRevision(){return stateSnapshot().ownerRevision();}
    @Override public boolean productionAvailable() {
        synchronizeProductionLifecycle();
        if(!super.productionAvailable()||lifecycle==null||!lifecycle.onServerThread()||lifecycle.terminal()||!lifecycle.live()
                ||isDestroyedServerside||!isBuilt||shouldBeDestroyed())return false;
        var state=stateSnapshot();
        return state.supported()&&state.instanceId()!=null&&state.owner().equals(ownerName)&&!ownerName.isBlank();
    }
    @Override protected void setDelayedRally(com.solegendary.reignofnether.unit.interfaces.Unit unit) {
        // A refused Forge spawn has no lifetime to receive a first rally, even if spawn returned an object.
        if(((net.minecraft.world.entity.Entity)unit).isAddedToWorld())super.setDelayedRally(unit);
    }
    @Override public void onBuilt() {
        if(lifecycle.terminal())return;
        super.onBuilt();synchronizeProductionLifecycle();
    }
    @Override public void tick(Level level) {
        if(lifecycle.terminal()||level!=getLevel())return;
        if(!level.isClientSide()) {
            if(!lifecycle.onServerThread()||!lifecycle.live())return;
            synchronizeProductionLifecycle();
        }
        super.tick(level);
    }
    @Override public void destroy(ServerLevel level) {
        if(!lifecycle.onServerThread()||lifecycle.terminal()||level!=getLevel())return;
        if(productionCallbackActive()){super.destroy(level);return;}
        if(lifecycle.beginDestroy(level))super.destroy(level);
    }
    @Override public void onRemoved(RemovalReason reason) {
        if(!lifecycle.onServerThread())return;
        if(!productionCallbackActive())lifecycle.detach();
        super.onRemoved(reason); // Clears pending first-rally handoffs, never refunds or writes saved state.
    }
}
