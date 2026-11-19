package com.dreamseekers.unit.mixin;

import com.dreamseekers.encounter.EncounterClient;
import com.solegendary.reignofnether.sandbox.SandboxClientEvents;
import com.solegendary.reignofnether.hud.buttons.UnitSpawnButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/** Presentation-only editor buttons; no faction/entity/production registration. */
@Mixin(value=SandboxClientEvents.class,remap=false)
public abstract class EncounterEditorButtonsMixin {
    @Inject(method="getUnitButtons",at=@At("RETURN"),cancellable=true)
    private static void encounters(CallbackInfoReturnable<List<UnitSpawnButton>> cir){cir.setReturnValue(EncounterClient.append(cir.getReturnValue()));}
}
