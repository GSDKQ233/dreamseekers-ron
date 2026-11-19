package com.solegendary.reignofnether.mixin;
import com.solegendary.reignofnether.resources.kill.EconomicHurtInvocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=LivingEntity.class,priority=2000)
public abstract class EconomicHurtInvocationMixin {
 @Inject(method="hurt",at=@At("HEAD"))
 private void economicEnter(DamageSource damage,float amount,CallbackInfoReturnable<Boolean> ci){EconomicHurtInvocation.enter((LivingEntity)(Object)this);}
 @Inject(method="hurt",at=@At("RETURN"))
 private void economicLeave(DamageSource damage,float amount,CallbackInfoReturnable<Boolean> ci){EconomicHurtInvocation.leave();}
}
