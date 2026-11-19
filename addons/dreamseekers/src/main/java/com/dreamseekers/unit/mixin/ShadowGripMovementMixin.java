// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.ShadowHandGrip;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** Only these original autonomous inputs change. Travel physics, prior velocity and liquid rise remain native. */
@Mixin(LivingEntity.class)
public abstract class ShadowGripMovementMixin {
    @Shadow protected abstract void jumpFromGround();
    @Redirect(method="aiStep",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;travel(Lnet/minecraft/world/phys/Vec3;)V"),require=1)
    private void dreamseekers$rootTravelInput(LivingEntity body,Vec3 input) {
        body.travel(ShadowHandGrip.blocksAutonomousMovement(body)?Vec3.ZERO:input);
    }
    @Redirect(method="aiStep",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;jumpFromGround()V"),require=1)
    private void dreamseekers$rootNewGroundJump(LivingEntity body) {
        // The actual receiver is this. Keep virtual dispatch for Rabbit/Slime when not rooted.
        if(!ShadowHandGrip.blocksAutonomousMovement(body))jumpFromGround();
    }
}
