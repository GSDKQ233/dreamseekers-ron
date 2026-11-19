// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.function;

import com.dreamseekers.building.DreamBuildingSupport;
import com.dreamseekers.building.DreamBuildingSpec;
import java.util.function.Supplier;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.data.DataStorage;
import com.solegendary.reignofnether.building.data.DataType;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import static com.dreamseekers.function.DreamFunctionResult.Status.*;
import static com.dreamseekers.function.DreamFunctionService.Lifecycle.*;

/** Shared server-only lifecycle; raw storage is dynamic because the ordinary loader replaces it. */
public final class DreamFunctionLifecycle {
    public static final DataType<DreamFunctionState> STATE = DataType.createRegistered(
            ResourceLocation.fromNamespaceAndPath("dreamseekers", "function_state"),
            (tag, server) -> DreamFunctionState.read(tag), DreamFunctionState::write, DreamFunctionState::new);
    private final BuildingPlacement placement;
    private final DreamBuildingSpec spec;
    private final Supplier<DataStorage> rawStorage;
    private final DreamFunctionService service = DreamFunctionService.UNAVAILABLE;
    private final int[] lifecycleCounts = new int[DreamFunctionService.Lifecycle.values().length];
    private boolean attached;
    private boolean destroyed;
    private boolean detached;
    private boolean synchronizing;
    // Runtime authority is independent of replaceable/opaque saved metadata. Never serialized.
    private String observedActualOwner;
    private long liveOwnerRevision;
    private boolean ownerRevisionExhausted;


    public DreamFunctionLifecycle(BuildingPlacement placement, DreamBuildingSpec spec, Supplier<DataStorage> rawStorage) {
        this.placement = placement;
        this.spec = spec;
        this.rawStorage = rawStorage;
        this.observedActualOwner = placement.ownerName;
    }
    /** A negative revision is unavailable authority, never a reusable binding token. */
    public long liveOwnerRevision() {
        synchronizeLifecycle();
        return onServerThread() && live() && !terminal() && !ownerRevisionExhausted
                ? liveOwnerRevision : -1L;
    }
    private void observeActualOwner() {
        if (java.util.Objects.equals(observedActualOwner, placement.ownerName)) return;
        observedActualOwner = placement.ownerName;
        if (liveOwnerRevision == Long.MAX_VALUE) ownerRevisionExhausted = true;
        else liveOwnerRevision++;
    }
    public boolean terminal() { return destroyed || detached; }
    public boolean onServerThread() {
        return placement.getLevel() instanceof ServerLevel server && server.getServer().isSameThread();
    }
    public boolean live() { return BuildingServerEvents.getBuildings().contains(placement); }
    private DreamFunctionState state() { return rawStorage.get().getData(STATE); }
    public DreamFunctionState.Snapshot stateSnapshot() { return state().snapshot(); }
    public int lifecycleCount(DreamFunctionService.Lifecycle event) { return lifecycleCounts[event.ordinal()]; }
    private DreamFunctionService.Context context() {
        var snapshot = state().snapshot();
        return new DreamFunctionService.Context(snapshot.instanceId(), spec.role(),
                placement.getLevel().dimension().location(), placement.originPos.immutable(), snapshot.owner(), snapshot.built(), snapshot.ownerRevision());
    }
    private void emit(DreamFunctionService.Lifecycle event) {
        if (lifecycleCounts[event.ordinal()] < Integer.MAX_VALUE) lifecycleCounts[event.ordinal()]++;
        // Unknown state and never-attached cancellations never reach an extension.
        // The service always receives an instance identity; counts remain transient diagnostics.
        if (state().supported() && state().snapshot().instanceId() != null)
            service.onLifecycle(context(), event);
    }
    public void synchronizeLifecycle() {
        if (!onServerThread() || !live() || destroyed || detached || synchronizing) return;
        synchronizing = true;
        try {
            // Observe even when the raw state has a future schema and cannot be rewritten.
            observeActualOwner();
            DreamFunctionState state = state();
            if (!attached) {
                boolean restored = state.restored();
                state.attach(placement.ownerName, false);
                attached = true;
                emit(restored ? RESTORED : ATTACHED);
            }
            if (state.observeOwner(placement.ownerName)) emit(OWNER_CHANGED);
            if (state.observeBuilt(placement.isBuilt)) emit(placement.isBuilt ? COMPLETED : DEACTIVATED);
        } finally { synchronizing = false; }
    }
    /** False means no base destruction is allowed; never attach a never-live cancellation. */
    public boolean beginDestroy(ServerLevel level) {
        if (terminal() || !onServerThread() || level != placement.getLevel()) return false;
        observeActualOwner();
        if (attached && state().observeOwner(placement.ownerName)) emit(OWNER_CHANGED);
        destroyed = true;
        emit(DreamFunctionService.Lifecycle.DESTROYED);
        return true;
    }
    /** Detach must not refund, mutate SavedData, or poison storage shared with a reload replacement. */
    public void detach() {
        if (!onServerThread() || terminal()) return;
        detached = true;
        if (attached) emit(DETACHED);
        attached = false;
    }
    /**
     * Server command boundary. A future packet must pass its authenticated Context#getSender(),
     * never an owner string supplied by the client. This version has no packet or action buttons.
     */
    public DreamFunctionResult request(ServerPlayer actor, DreamFunctionRequest request) {
        if (!onServerThread()) return DreamFunctionResult.of(NOT_SERVER);
        if (destroyed || placement.isDestroyedServerside) return DreamFunctionResult.of(DreamFunctionResult.Status.DESTROYED);
        if (detached || !live()) return DreamFunctionResult.of(NOT_LIVE);
        if (actor == null || actor.serverLevel() != placement.getLevel()
                || !actor.getGameProfile().getName().equals(placement.ownerName)) return DreamFunctionResult.of(UNAUTHORIZED);
        var player = PlayerServerEvents.getRTSPlayer(placement.ownerName);
        if (!com.dreamseekers.economy.DreamFactionAccess.allows(player))
            return DreamFunctionResult.of(WRONG_FACTION);
        synchronizeLifecycle();
        if (!state().supported()) return DreamFunctionResult.of(UNSUPPORTED_SCHEMA);
        if (!placement.isBuilt || placement.shouldBeDestroyed()) return DreamFunctionResult.of(NOT_BUILT);
        if (request == null || !request.valid()) return DreamFunctionResult.of(INVALID_REQUEST);
        if (request.role() != spec.role()) return DreamFunctionResult.of(WRONG_ROLE);
        return service.request(context(), request);
    }
}
