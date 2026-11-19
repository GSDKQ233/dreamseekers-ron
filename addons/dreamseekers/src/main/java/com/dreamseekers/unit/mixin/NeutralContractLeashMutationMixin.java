package com.dreamseekers.unit.mixin;
import com.dreamseekers.neutralcontract.NeutralContracts;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** A temporary player leash cannot be erased to resurrect an earlier wild claim. */
@Mixin(Mob.class)
public abstract class NeutralContractLeashMutationMixin {
    @Inject(method="setLeashedTo",at=@At("HEAD")) private void dreamseekers$contractLeash(Entity holder,boolean broadcast,CallbackInfo ci){var self=(Mob)(Object)this;if(!self.level().isClientSide()&&holder!=null&&self.getLeashHolder()!=holder)NeutralContracts.nativeOwnershipChanging(self);}
}
