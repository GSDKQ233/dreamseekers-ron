// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.client;

import java.util.Locale;

/** Pure tooltip/badge presentation policy shared by the addon's HUD; no client bootstrap. */
public final class DreamTooltipLayout {
    private DreamTooltipLayout() {}

    /** Same scaled-pixel budget as the skill, research and building tooltips. */
    public static int wrapWidth(int guiScaledWidth) {
        if (guiScaledWidth <= 30) return 1;
        return Math.min(310, guiScaledWidth - 30);
    }

    /** Whole values print without a fraction ("8"); real fractions keep one decimal ("9.6"). */
    public static String number(double value) {
        double rounded = Math.rint(value * 10.0) / 10.0;
        if (rounded == Math.rint(rounded)) return Long.toString((long) rounded);
        return String.format(Locale.ROOT, "%.1f", rounded);
    }

    /** Remaining-cooldown badge; the unit keeps it distinct from a bare cost number. */
    public static String cooldownBadge(long remainingTicks) {
        return (Math.max(0, remainingTicks) + 19) / 20 + "s";
    }

    /** Fits text to a pixel budget, ending a cut text with an ellipsis instead of a silent cut. */
    public static String ellipsize(String text, int maxWidth, java.util.function.ToIntFunction<String> width,
                                   java.util.function.BiFunction<String, Integer, String> prefixByWidth) {
        if (width.applyAsInt(text) <= maxWidth) return text;
        String ellipsis = "…";
        return prefixByWidth.apply(text, Math.max(0, maxWidth - width.applyAsInt(ellipsis))) + ellipsis;
    }

    /** While a target mode is armed, every ability-slot key belongs to an ability button's own toggle. */
    public static boolean keepsTargetMode(int keyCode, int... abilitySlotKeys) {
        for (int slot : abilitySlotKeys) if (slot == keyCode) return true;
        return false;
    }
}
