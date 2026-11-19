// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.OrdinaryAttackGate;
import com.solegendary.reignofnether.unit.units.neutral.LlamaUnit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** LlamaUnit delegates its ordinary factory to this native method; wild llamas stay exact legacy. */
@Mixin(Llama.class)
public abstract class DreadRoarLlamaLaunchMixin {
    @Redirect(method="spit",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"),require=1)
    private boolean dreamseekers$ordinarySpit(Level level,Entity projectile) {
        return (Object)this instanceof LlamaUnit source
                ?OrdinaryAttackGate.publish(source,projectile,()->level.addFreshEntity(projectile)):level.addFreshEntity(projectile);
    }
}
