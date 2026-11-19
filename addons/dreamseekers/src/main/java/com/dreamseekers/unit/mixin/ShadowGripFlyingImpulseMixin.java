// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.ShadowHandGrip;
import com.solegendary.reignofnether.unit.controls.FlyingUnitMoveControl;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** The one MOVE_TO autonomous acceleration write, not arbitrary velocity setters or the entire control tick. */
@Mixin(value=FlyingUnitMoveControl.class,remap=false)
public abstract class ShadowGripFlyingImpulseMixin {
    @Redirect(method="tick",remap=true,at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Mob;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V",remap=true),require=1)
    private void dreamseekers$rootNewFlightImpulse(Mob body,Vec3 accelerated) {
        if(!ShadowHandGrip.blocksAutonomousMovement(body))body.setDeltaMovement(accelerated);
    }
}
