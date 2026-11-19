// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.unit.BeetleMotherUnit;
import com.dreamseekers.unit.BeetleUnit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelAccessor;
import net.pasterdream.procedures.BlackBeetleMotherPr0Procedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** Exact beta0.4.2 source and query-target isolation; all wild-only callbacks stay original. */
@Mixin(value=BlackBeetleMotherPr0Procedure.class,remap=false)
public abstract class BeetleMotherSummonProcedureMixin {
    @Inject(method="execute",at=@At("HEAD"),cancellable=true,remap=false)
    private static void dreamseekers$denyNativeFreeSummon(LevelAccessor world,double x,double y,double z,Entity source,CallbackInfo callback){
        if(source instanceof BeetleMotherUnit)callback.cancel();
    }
    @Inject(method="lambda$execute$2",at=@At("HEAD"),cancellable=true,remap=false)
    private static void dreamseekers$excludePaidBeetleTargets(Entity target,CallbackInfoReturnable<Boolean> callback){
        if(target instanceof BeetleUnit)callback.setReturnValue(false);
    }
}
