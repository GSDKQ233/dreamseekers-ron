package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.resources.kill.EconomicExplosionHooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class EconomicLevelExplosionMixin {
    private static final String DEEPEST="explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZLnet/minecraft/world/level/Level$ExplosionInteraction;Z)Lnet/minecraft/world/level/Explosion;";
    @Inject(method=DEEPEST,at=@At("HEAD"))
    private void economicEnter(Entity source,DamageSource damage,ExplosionDamageCalculator calculator,double x,double y,double z,float radius,boolean fire,Level.ExplosionInteraction interaction,boolean particles,CallbackInfoReturnable<Explosion> ci) {
        EconomicExplosionHooks.enter();
    }
    @Inject(method=DEEPEST,at=@At("RETURN"))
    private void economicLeave(Entity source,DamageSource damage,ExplosionDamageCalculator calculator,double x,double y,double z,float radius,boolean fire,Level.ExplosionInteraction interaction,boolean particles,CallbackInfoReturnable<Explosion> ci) {
        EconomicExplosionHooks.leave();
    }
    @Redirect(method=DEEPEST,at=@At(value="NEW",target="net/minecraft/world/level/Explosion"))
    private Explosion economicConstruct(Level level,Entity source,DamageSource damage,ExplosionDamageCalculator calculator,double x,double y,double z,float radius,boolean fire,Explosion.BlockInteraction interaction) {
        Explosion explosion=new Explosion(level,source,damage,calculator,x,y,z,radius,fire,interaction);
        EconomicExplosionHooks.constructed(explosion,level,source);
        return explosion;
    }
}
