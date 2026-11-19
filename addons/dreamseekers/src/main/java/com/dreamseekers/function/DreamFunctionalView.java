// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.function;
import com.dreamseekers.building.DreamBuildingSpec;
import net.minecraft.server.level.ServerPlayer;
/** Shared typed extension view; it does not imply a working service or a production queue. */
public interface DreamFunctionalView {
    DreamBuildingSpec spec();
    DreamFunctionState.Snapshot stateSnapshot();
    int lifecycleCount(DreamFunctionService.Lifecycle event);
    DreamFunctionResult request(ServerPlayer actor, DreamFunctionRequest request);
}
