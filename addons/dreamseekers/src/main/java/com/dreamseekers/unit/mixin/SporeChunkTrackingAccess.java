// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Read-only recipient lookup; never adds tracking or updates a fog mask. */
@Mixin(ChunkMap.class)
public interface SporeChunkTrackingAccess {
    @Accessor("entityMap") Int2ObjectMap<?> dreamseekers$trackedEntities();
}
