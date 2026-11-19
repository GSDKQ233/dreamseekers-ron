// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unit.mixin;
import com.dreamseekers.skill.SkillEffects;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Reconcile only tracked skill effects immediately after the actual server alliance mutation. */
@Mixin(value=AlliancesServerEvents.class,remap=false)
public abstract class SkillAllianceLifecycleMixin {
    @Inject(method={"removeAlliance","addAlliance"},at=@At("RETURN"))
    private static void dreamseekers$revoke(String first,String second,CallbackInfo ci) { SkillEffects.relationshipsChanged(); }
    @Inject(method={"resetAllAlliances","applyScenarioAlliances","applyCoopAlliances"},at=@At("RETURN"))
    private static void dreamseekers$rebuild(CallbackInfo ci) { SkillEffects.relationshipsChanged(); }
}
