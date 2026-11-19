// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.aaroncos.ControlledAaroncosHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** Fixed beta0.4.2 entry/captured-source guards; source-free callbacks remain original. */
@Mixin(value=net.pasterdream.procedures.AaroncosRighthandSkillVortexProcedure.class,remap=false)
public abstract class AaroncosRighthandSkillVortexIsolationMixin {
    @Inject(method="execute(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;)V",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private static void dreamseekers$source0(net.minecraft.world.level.LevelAccessor arg0,net.minecraft.world.entity.Entity arg1,CallbackInfo ci){if(arg1 instanceof ControlledAaroncosHand)ci.cancel();}
    @Inject(method="lambda$execute$2(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;)V",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private static void dreamseekers$source1(net.minecraft.world.level.LevelAccessor arg0,net.minecraft.world.entity.Entity arg1,CallbackInfo ci){if(arg1 instanceof ControlledAaroncosHand)ci.cancel();}
}
