package com.solegendary.reignofnether.hud;

import java.util.function.ToIntFunction;

/** Pure display formatting: no production timing, identity or queue mutation. */
public final class ProductionProgressLabel {
    private static final String ELLIPSIS = "\u2026";
    private ProductionProgressLabel() {}

    /** Keep the percentage first, then fit the localized name using the actual renderer's width.
     * Truncation removes whole Unicode code points, never half a UTF-16 surrogate pair.
     * A pathologically narrow panel that cannot fit the percentage shows no partial percentage.
     */
    public static String fit(int percentage, String localizedName, int maxWidth, ToIntFunction<String> width) {
        if (maxWidth <= 0) return "";
        String prefix = Math.max(0, Math.min(100, percentage)) + "% ";
        return fitWithPrefix(prefix, localizedName, maxWidth, width);
    }

    /** A held paid job is not complete. Give the reason, not a rounded "100%". */
    public static String fitStatus(String status, int maxWidth, ToIntFunction<String> width) {
        return fitWithPrefix("", status, maxWidth, width);
    }

    public static int percent(float remainingFraction, boolean waitingForServer) {
        return Math.max(0, Math.min(waitingForServer ? 99 : 100, Math.round(100 - remainingFraction * 100)));
    }

    private static String fitWithPrefix(String prefix, String text, int maxWidth, ToIntFunction<String> width) {
        if (maxWidth <= 0 || width.applyAsInt(prefix) > maxWidth) return "";
        String name = text == null ? "" : text;
        String full = prefix + name;
        if (width.applyAsInt(full) <= maxWidth) return full;
        if (width.applyAsInt(prefix + ELLIPSIS) > maxWidth) return prefix;
        int end = name.length();
        while (end > 0) {
            end = name.offsetByCodePoints(end, -1);
            String candidate = prefix + name.substring(0, end) + ELLIPSIS;
            if (width.applyAsInt(candidate) <= maxWidth) return candidate;
        }
        return prefix + ELLIPSIS;
    }
}
