// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;

import com.dreamseekers.unit.NativeGoldenFoxHooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.pasterdream.entity.GoldenFoxEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The paid ground unit uses ordinary mob damage; original wild NPC immunity stays intact. */
@Mixin(GoldenFoxEntity.class)
public abstract class GoldenFoxDamageMixin extends PathfinderMob {
    protected GoldenFoxDamageMixin(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void dreamseekers$ordinaryPaidDamage(DamageSource source, float amount,
                                                CallbackInfoReturnable<Boolean> callback) {
        if (NativeGoldenFoxHooks.isPaidUnit(this)) callback.setReturnValue(super.hurt(source, amount));
    }
}
