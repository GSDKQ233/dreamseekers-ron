package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.resources.kill.EconomicExplosionHooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Explosion.class)
public abstract class EconomicExplosionMixin {
    @Redirect(method="explode",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean economicHurt(Entity victim,DamageSource damage,float amount) {
        return EconomicExplosionHooks.hurt((Explosion)(Object)this,victim,damage,amount);
    }
}
