package com.solegendary.reignofnether.mixin;

import com.solegendary.reignofnether.resources.kill.EconomicCarrierIdentity;
import com.solegendary.reignofnether.resources.kill.EconomicProjectileOwnerView;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.UUID;

@Mixin(Projectile.class)
public abstract class EconomicProjectileOwnershipMixin implements EconomicProjectileOwnerView {
    @Shadow private UUID ownerUUID;
    @Override public UUID reignofnether$economicOwnerUUID(){return ownerUUID;}
    @Inject(method="setOwner",at=@At("RETURN"))
    private void economicOwnerSet(Entity owner,CallbackInfo ci) {
        EconomicCarrierIdentity.ownerSet((Projectile)(Object)this,owner);
    }
}
