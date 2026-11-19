package com.dreamseekers.unit.mixin;

import com.dreamseekers.client.NativeContractVisibilityPolicy;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.pasterdream.entity.BasaltSnailEntity;
import net.pasterdream.entity.BlackBeetleEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-list-only distance extension for original contract targets under the RTS camera.
 * Does not change models, dimensions, AI, tracking, frustum, or first-person rendering.
 */
@Mixin(Entity.class)
public abstract class NativeContractRenderDistanceMixin {
    @Inject(method = "shouldRenderAtSqrDistance(D)Z", at = @At("RETURN"), cancellable = true)
    private void dreamseekers$allowNativeContractRtsDistance(double distanceSquared,
                                                            CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValue() || !OrthoviewClientEvents.isEnabled()) return;
        Entity entity = (Entity) (Object) this;
        boolean beetle = entity.getClass() == BlackBeetleEntity.class;
        boolean snail = entity.getClass() == BasaltSnailEntity.class;
        if (!beetle && !snail) return; // Not subclasses, paid RTS bodies, mothers, or other mobs.
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (!NativeContractVisibilityPolicy.isExactNativeTarget(id.getNamespace(), id.getPath(), beetle, snail)) return;
        // Editor ForgeData is server-owned and need not be synced. Natural targets qualify too.
        // Only extend an already tracked, visible entity; subsequent renderer/frustum gates remain.
        if (NativeContractVisibilityPolicy.renderAtDistance(callback.getReturnValue(), true, true,
                !entity.isAlive() || entity.isInvisible(),
                entity.level().getWorldBorder().isWithinBounds(entity.getOnPos()),
                FogOfWarClientEvents.isInBrightChunk(entity), distanceSquared)) {
            callback.setReturnValue(true);
        }
    }
}
