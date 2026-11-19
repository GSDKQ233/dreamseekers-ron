// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.function;

import com.dreamseekers.building.DreamBuildingSpec.Role;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** One integrated extension seam; lifecycle is invoked by real placed buildings on the server thread. */
public interface DreamFunctionService {
    enum Lifecycle { ATTACHED, RESTORED, OWNER_CHANGED, COMPLETED, DEACTIVATED, DESTROYED, DETACHED }
    record Context(UUID instanceId, Role role, ResourceLocation dimension, BlockPos origin,
                   String owner, boolean completed, long ownerRevision) {}

    void onLifecycle(Context context, Lifecycle event);
    DreamFunctionResult request(Context context, DreamFunctionRequest request);

    /** No runtime provider registry: enabling any gameplay requires an explicit reviewed implementation. */
    DreamFunctionService UNAVAILABLE = new DreamFunctionService() {
        @Override public void onLifecycle(Context context, Lifecycle event) { /* intentionally no effects */ }
        @Override public DreamFunctionResult request(Context context, DreamFunctionRequest request) {
            return DreamFunctionResult.of(DreamFunctionResult.Status.UNAVAILABLE);
        }
    };
}
