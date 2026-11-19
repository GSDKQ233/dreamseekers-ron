// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.NativeOrdinaryDebtHit;
import com.solegendary.reignofnether.api.PrimaryUnitHitHooks;
import com.solegendary.reignofnether.unit.units.neutral.BeeUnit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
/** Preserve the already-created economic wrapper; scope only its one native sting invocation. */
@Mixin(value=BeeUnit.class,remap=false)
public abstract class OrdinaryBeeDebtHitMixin {
    @Redirect(method="doHurtTarget",remap=true,at=@At(value="INVOKE",
            target="Lcom/solegendary/reignofnether/api/PrimaryUnitHitHooks$Impact;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z",remap=false),require=1)
    private boolean dreamseekers$exactSting(PrimaryUnitHitHooks.Impact economic,DamageSource damage,float amount,Entity victim) {
        return NativeOrdinaryDebtHit.hurt((BeeUnit)(Object)this,victim,damage,()->economic.hurt(damage,amount));
    }
}
