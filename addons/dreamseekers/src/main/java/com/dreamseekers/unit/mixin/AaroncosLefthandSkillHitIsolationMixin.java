// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.aaroncos.ControlledAaroncosHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** Fixed beta0.4.2 entry/captured-source guards; source-free callbacks remain original. */
@Mixin(value=net.pasterdream.procedures.AaroncosLefthandSkillHitProcedure.class,remap=false)
public abstract class AaroncosLefthandSkillHitIsolationMixin {
    @Inject(method="execute(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;)V",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private static void dreamseekers$source0(net.minecraft.world.level.LevelAccessor arg0,net.minecraft.world.entity.Entity arg1,CallbackInfo ci){if(arg1 instanceof ControlledAaroncosHand)ci.cancel();}
    @Inject(method="lambda$execute$14(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/LevelAccessor;)V",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private static void dreamseekers$source1(net.minecraft.world.entity.Entity arg0,net.minecraft.world.level.LevelAccessor arg1,CallbackInfo ci){if(arg0 instanceof ControlledAaroncosHand)ci.cancel();}
    @Inject(method="lambda$execute$13(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;)V",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private static void dreamseekers$source2(net.minecraft.world.level.LevelAccessor arg0,net.minecraft.world.entity.Entity arg1,CallbackInfo ci){if(arg1 instanceof ControlledAaroncosHand)ci.cancel();}
    @Inject(method="lambda$execute$10(Lnet/minecraft/world/entity/Entity;)V",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private static void dreamseekers$source3(net.minecraft.world.entity.Entity arg0,CallbackInfo ci){if(arg0 instanceof ControlledAaroncosHand)ci.cancel();}
    @Inject(method="lambda$execute$9(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/LevelAccessor;)V",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private static void dreamseekers$source4(net.minecraft.world.entity.Entity arg0,net.minecraft.world.level.LevelAccessor arg1,CallbackInfo ci){if(arg0 instanceof ControlledAaroncosHand)ci.cancel();}
    @Inject(method="lambda$execute$8(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;)V",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private static void dreamseekers$source5(net.minecraft.world.level.LevelAccessor arg0,net.minecraft.world.entity.Entity arg1,CallbackInfo ci){if(arg1 instanceof ControlledAaroncosHand)ci.cancel();}
    @Inject(method="lambda$execute$5(Lnet/minecraft/world/entity/Entity;)V",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private static void dreamseekers$source6(net.minecraft.world.entity.Entity arg0,CallbackInfo ci){if(arg0 instanceof ControlledAaroncosHand)ci.cancel();}
    @Inject(method="lambda$execute$4(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/LevelAccessor;)V",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private static void dreamseekers$source7(net.minecraft.world.entity.Entity arg0,net.minecraft.world.level.LevelAccessor arg1,CallbackInfo ci){if(arg0 instanceof ControlledAaroncosHand)ci.cancel();}
    @Inject(method="lambda$execute$3(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;)V",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private static void dreamseekers$source8(net.minecraft.world.level.LevelAccessor arg0,net.minecraft.world.entity.Entity arg1,CallbackInfo ci){if(arg1 instanceof ControlledAaroncosHand)ci.cancel();}
    @Inject(method="lambda$execute$0(Lnet/minecraft/world/entity/Entity;)V",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private static void dreamseekers$source9(net.minecraft.world.entity.Entity arg0,CallbackInfo ci){if(arg0 instanceof ControlledAaroncosHand)ci.cancel();}
    @Inject(method="lambda$execute$1",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private static void dreamseekers$target0(net.minecraft.world.entity.Entity target,CallbackInfoReturnable<Boolean> ci){if(target instanceof com.dreamseekers.unit.DreamRtsUnit||target instanceof com.dreamseekers.unit.DreamNoncombatUnit)ci.setReturnValue(false);}
    @Inject(method="lambda$execute$6",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private static void dreamseekers$target1(net.minecraft.world.entity.Entity target,CallbackInfoReturnable<Boolean> ci){if(target instanceof com.dreamseekers.unit.DreamRtsUnit||target instanceof com.dreamseekers.unit.DreamNoncombatUnit)ci.setReturnValue(false);}
    @Inject(method="lambda$execute$11",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private static void dreamseekers$target2(net.minecraft.world.entity.Entity target,CallbackInfoReturnable<Boolean> ci){if(target instanceof com.dreamseekers.unit.DreamRtsUnit||target instanceof com.dreamseekers.unit.DreamNoncombatUnit)ci.setReturnValue(false);}
}
