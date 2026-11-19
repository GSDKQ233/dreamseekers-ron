package com.solegendary.reignofnether.mixin;
import com.solegendary.reignofnether.resources.kill.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.EvokerFangs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;
@Mixin(EvokerFangs.class)
public abstract class EconomicFangOwnershipMixin implements EconomicFangOwnerView {
 @Shadow private UUID ownerUUID;
 public UUID reignofnether$economicFangOwnerUUID(){return ownerUUID;}
 @Inject(method="setOwner",at=@At("RETURN"))
 private void economicOwner(LivingEntity owner,CallbackInfo ci){EconomicFangHooks.ownerSet((EvokerFangs)(Object)this,owner);}
}
