// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.Set;
@Mixin(targets="net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface SporeTrackedEntityAccess {
    @Accessor("entity") Entity dreamseekers$trackedEntity();
    @Accessor("seenBy") Set<ServerPlayerConnection> dreamseekers$trackingConnections();
}
