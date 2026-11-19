// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.unit.TerrorbeakUnit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.pasterdream.procedures.TerrorbeakPr2Procedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Native hurt invokes this BEFORE accepting damage. Cancel before scoreboard, AoE, SAN or delayed work. */
@Mixin(value=TerrorbeakPr2Procedure.class,remap=false)
public abstract class TerrorbeakRoarProcedureMixin {
    @Inject(method="execute",at=@At("HEAD"),cancellable=true,remap=false)
    private static void dreamseekers$noNativeRoar(LevelAccessor world,double x,double y,double z,Entity source,CallbackInfo ci){
        if(source instanceof TerrorbeakUnit)ci.cancel();
    }
    @Inject(method="lambda$execute$2",at=@At("HEAD"),cancellable=true,remap=false)
    private static void dreamseekers$noNativeDelayedScore(Entity source,CallbackInfo ci){
        if(source instanceof TerrorbeakUnit)ci.cancel();
    }
}
