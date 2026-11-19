// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.aaroncos.ControlledAaroncosHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** Fixed beta0.4.2 entry/captured-source guards; source-free callbacks remain original. */
@Mixin(value=net.pasterdream.procedures.AaroncosRighthandSkillMagicballProcedure.class,remap=false)
public abstract class AaroncosRighthandSkillMagicballIsolationMixin {
    @Inject(method="execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private static void dreamseekers$source0(net.minecraft.world.level.LevelAccessor arg0,double arg1,double arg2,double arg3,net.minecraft.world.entity.Entity arg4,CallbackInfo ci){if(arg4 instanceof ControlledAaroncosHand)ci.cancel();}
    @Inject(method="lambda$execute$6(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;)V",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private static void dreamseekers$source1(net.minecraft.world.level.LevelAccessor arg0,net.minecraft.world.entity.Entity arg1,CallbackInfo ci){if(arg1 instanceof ControlledAaroncosHand)ci.cancel();}
    @Inject(method="lambda$execute$5(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private static void dreamseekers$source2(net.minecraft.world.level.LevelAccessor arg0,double arg1,double arg2,double arg3,net.minecraft.world.entity.Entity arg4,CallbackInfo ci){if(arg4 instanceof ControlledAaroncosHand)ci.cancel();}
}
