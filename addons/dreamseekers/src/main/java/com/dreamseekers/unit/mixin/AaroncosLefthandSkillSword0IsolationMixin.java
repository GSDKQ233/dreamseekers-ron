// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.aaroncos.ControlledAaroncosHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** Fixed beta0.4.2 entry/captured-source guards; source-free callbacks remain original. */
@Mixin(value=net.pasterdream.procedures.AaroncosLefthandSkillSword0Procedure.class,remap=false)
public abstract class AaroncosLefthandSkillSword0IsolationMixin {
    @Inject(method="execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private static void dreamseekers$source0(net.minecraft.world.level.LevelAccessor arg0,double arg1,double arg2,double arg3,net.minecraft.world.entity.Entity arg4,CallbackInfo ci){if(arg4 instanceof ControlledAaroncosHand)ci.cancel();}
    @Inject(method="lambda$execute$0",at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private static void dreamseekers$target0(net.minecraft.world.entity.Entity target,CallbackInfoReturnable<Boolean> ci){if(target instanceof com.dreamseekers.unit.DreamRtsUnit||target instanceof com.dreamseekers.unit.DreamNoncombatUnit)ci.setReturnValue(false);}
}
