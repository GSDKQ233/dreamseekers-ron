package com.dreamseekers.unit.mixin;
import com.dreamseekers.neutralcontract.NeutralContracts;
import net.minecraft.world.entity.TamableAnimal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;
/** Only active exact native contract claims are invalidated; ordinary taming behavior is unchanged. */
@Mixin(TamableAnimal.class)
public abstract class NeutralContractTameMutationMixin {
    @Inject(method="setTame",at=@At("HEAD")) private void dreamseekers$contractTame(boolean tame,CallbackInfo ci){var self=(TamableAnimal)(Object)this;if(!self.level().isClientSide()&&self.isTame()!=tame)NeutralContracts.nativeOwnershipChanging(self);}
    @Inject(method="setOwnerUUID",at=@At("HEAD")) private void dreamseekers$contractOwner(UUID owner,CallbackInfo ci){var self=(TamableAnimal)(Object)this;if(!self.level().isClientSide()&&!Objects.equals(self.getOwnerUUID(),owner))NeutralContracts.nativeOwnershipChanging(self);}
}
