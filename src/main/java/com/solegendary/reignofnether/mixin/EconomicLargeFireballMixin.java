package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.entities.GhastUnitFireball;
import com.solegendary.reignofnether.resources.kill.EconomicExplosionHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LargeFireball.class)
public abstract class EconomicLargeFireballMixin {
    @Redirect(method="onHit",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;explode(Lnet/minecraft/world/entity/Entity;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;)Lnet/minecraft/world/level/Explosion;"))
    private Explosion economicGhastExplosion(Level level,Entity source,double x,double y,double z,float radius,boolean fire,Level.ExplosionInteraction interaction) {
        if((Object)this instanceof GhastUnitFireball carrier)
            return EconomicExplosionHooks.projectile(carrier,source,()->level.explode(source,x,y,z,radius,fire,interaction));
        return level.explode(source,x,y,z,radius,fire,interaction);
    }
}
