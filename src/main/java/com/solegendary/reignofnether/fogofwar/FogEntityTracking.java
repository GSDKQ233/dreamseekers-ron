package com.solegendary.reignofnether.fogofwar;

import net.minecraft.server.level.ServerPlayer;

/** Implemented on the real ChunkMap by the registered common mixin. */
public interface FogEntityTracking {
    void reignofnether$refreshFogTracking(ServerPlayer viewer);
    interface Entry {
        void reignofnether$refreshFogViewer(ServerPlayer viewer);
    }
}
