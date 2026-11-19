// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.aaroncos.ControlledAaroncosHand;
import net.minecraft.world.level.LevelAccessor;
import net.pasterdream.procedures.AaroncosLefthandDeathProcedure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
/** Death procedure has no source: suppress only this exact dying controlled instance's invocation. */
@Mixin(net.pasterdream.entity.AaroncosRighthand0Entity.class)
public abstract class AaroncosRightDeathExplosionMixin {
    @Redirect(method="tickDeath",at=@At(value="INVOKE",target="Lnet/pasterdream/procedures/AaroncosLefthandDeathProcedure;execute(Lnet/minecraft/world/level/LevelAccessor;DDD)V",remap=false),require=1)
    private void dreamseekers$noPaidDeathExplosion(LevelAccessor world,double x,double y,double z){
        if(!((Object)this instanceof ControlledAaroncosHand))AaroncosLefthandDeathProcedure.execute(world,x,y,z);
    }
}
