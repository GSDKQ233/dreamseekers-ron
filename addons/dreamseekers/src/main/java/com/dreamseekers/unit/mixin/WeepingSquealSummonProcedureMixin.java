// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.unit.WeepingSquealGhostUnit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.pasterdream.procedures.ShadowSquealGhost0Pr0Procedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Exact fixed native source gate. Three world-only spawn callbacks have no source identity:
 * never schedule them for paid instances; do not globally disable wild callbacks. */
@Mixin(value=ShadowSquealGhost0Pr0Procedure.class,remap=false)
public abstract class WeepingSquealSummonProcedureMixin {
    @Inject(method="execute",at=@At("HEAD"),cancellable=true,remap=false)
    private static void dreamseekers$denyFreeSummons(LevelAccessor world,double x,double y,double z,Entity source,CallbackInfo ci){
        if(source instanceof WeepingSquealGhostUnit)ci.cancel();
    }
    @Inject(method="lambda$execute$3",at=@At("HEAD"),cancellable=true,remap=false)
    private static void dreamseekers$denyStaleScoreReset(Entity source,CallbackInfo ci){
        if(source instanceof WeepingSquealGhostUnit)ci.cancel();
    }
}
