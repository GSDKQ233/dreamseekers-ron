package com.dreamseekers.skill;

import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import net.minecraft.network.chat.Component;
import java.util.function.Supplier;

/** Client input/render adapter for abilities with authoritative per-owner snapshots. */
public final class SnapshotSkillButton {
    private SnapshotSkillButton() {}
    public static void waiting() {
        HudClientEvents.showTemporaryMessage(Component.translatable("ability.dreamseekers.unit_skill.awaiting_state").getString());
    }
    public static void cooling(SkillReadiness state) {
        HudClientEvents.showTemporaryMessage(Component.translatable("hud.buttons.reignofnether.on_cooldown",state.seconds()).getString());
    }
    public static AbilityButton bind(AbilityButton button, Supplier<SkillReadiness> state) {
        Runnable ordinary = button.onLeftClick;
        button.bottomLeftText = () -> state.get().badge();
        // '?' is not a full cooldown. Do not let the native numerical compatibility
        // value turn an unknown snapshot into a fictitious number of seconds.
        button.onLeftClick = () -> {
            SkillReadiness status = state.get();
            if (status.waiting()) waiting();
            else if (!status.ready()) cooling(status);
            else ordinary.run();
        };
        return button;
    }
}
