// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.unit.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.pasterdream.procedures.ShadowGolemPr0Procedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** Native area skill is undefined for RTS. Do not schedule it; stale source callbacks also fail closed. */
@Mixin(value=ShadowGolemPr0Procedure.class,remap=false)
public abstract class ShadowGolemAreaProcedureMixin {
    @Inject(method="execute",at=@At("HEAD"),cancellable=true,remap=false)
    private static void dreamseekers$noNativeArea(LevelAccessor level,double x,double y,double z,Entity source,CallbackInfo ci){if(source instanceof ShadowGolemUnit)ci.cancel();}
    @Inject(method="lambda$execute$2",at=@At("HEAD"),cancellable=true,remap=false)
    private static void dreamseekers$noDelayedAnimation(Entity source,CallbackInfo ci){if(source instanceof ShadowGolemUnit)ci.cancel();}
    @Inject(method="lambda$execute$5",at=@At("HEAD"),cancellable=true,remap=false)
    private static void dreamseekers$noDelayedImpact(LevelAccessor level,double x,double y,double z,Entity source,CallbackInfo ci){if(source instanceof ShadowGolemUnit)ci.cancel();}
    // Only this fixed-beta predicate is the actual delayed AoE Entity query. Preserve all original wild/Player targets.
    @Inject(method="lambda$execute$3",at=@At("HEAD"),cancellable=true,remap=false)
    private static void dreamseekers$excludeControlledTargets(Entity target,CallbackInfoReturnable<Boolean> cir){
        if(target instanceof DreamRtsUnit||target instanceof DreamNoncombatUnit)cir.setReturnValue(false);
    }
}
