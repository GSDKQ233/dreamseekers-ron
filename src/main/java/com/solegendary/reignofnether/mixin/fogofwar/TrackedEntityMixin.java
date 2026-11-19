package com.solegendary.reignofnether.mixin.fogofwar;

import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// gate entity packets behind fog; target by name since TrackedEntity is package-private
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityMixin implements com.solegendary.reignofnether.fogofwar.FogEntityTracking.Entry {

    @Shadow @Final Entity entity;
    @Shadow public abstract void removePlayer(ServerPlayer player);
    @Shadow public abstract void updatePlayer(ServerPlayer player);

    @Override public void reignofnether$refreshFogViewer(ServerPlayer viewer) { updatePlayer(viewer); }

    @Inject(method = "updatePlayer", at = @At("HEAD"), cancellable = true)
    private void reignofnether$gateEntityVisibility(ServerPlayer player, CallbackInfo ci) {
        if (!FogOfWarServerEvents.isFogActiveFor(player)) return;
        // block-level gate: an enemy entity hides unless its own column is inside the viewer's circle
        BlockPos bp = entity.blockPosition();
        if (FogOfWarServerEvents.isBlockVisibleFor(player, bp.getX(), bp.getZ())) return;
        // Cancellation alone leaves an old seenBy entry broadcasting hidden entity data forever.
        removePlayer(player);
        ci.cancel();
    }
}
