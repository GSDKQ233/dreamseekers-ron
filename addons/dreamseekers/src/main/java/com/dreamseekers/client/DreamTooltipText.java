// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Client hover path only: splits logical text once at the current language and GUI scale. */
public final class DreamTooltipText {
    private DreamTooltipText() {}

    /**
     * RTS feedback line above the selection name; the vanilla action bar is drawn on top of that name.
     * The RTS line is only drawn with the HUD, so another open screen (the codex) or a hidden HUD keeps the action bar.
     */
    public static void hudMessage(Component message) {
        if (com.solegendary.reignofnether.hud.HudClientEvents.isHudInteractive())
            com.solegendary.reignofnether.hud.HudClientEvents.showTemporaryMessage(message.getString());
        else if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(message, true);
    }

    public static List<FormattedCharSequence> wrap(Component... components) { return wrap(List.of(components)); }

    public static List<FormattedCharSequence> wrap(List<? extends Component> components) {
        Minecraft mc = Minecraft.getInstance();
        int width = DreamTooltipLayout.wrapWidth(mc.getWindow().getGuiScaledWidth());
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component component : components) lines.addAll(mc.font.split(component, width));
        return lines;
    }
}
