package com.solegendary.reignofnether.matchstart;

/** Pointer hit policy shared by the lobby's rendered controls and its row/spectator actions. */
public final class MatchStartInputPolicy {
    private MatchStartInputPolicy() { }
    public enum Layer { HEADER, MAP, ROSTER, POPOVER }
    public record Rect(int left, int top, int right, int bottom) {
        public boolean contains(double x, double y) {
            return Double.isFinite(x) && Double.isFinite(y)
                    && x >= left && x < right && y >= top && y < bottom;
        }
    }
    public static final Rect EMPTY = new Rect(0, 0, 0, 0);
    /** Mirrors GameruleClient.renderGamerulesGUI's frame, including its labels/background. */
    public static Rect popoverBounds(int xTR, int yTR, int buttonCount) {
        int x = xTR - 145 - 8, y = yTR - 36;
        return new Rect(x, y, x + 145, y + Math.max(0, buttonCount * 18 - 12));
    }
    public static boolean accepts(Layer layer, double x, double y, Rect rosterClip,
                                  boolean popoverOpen, Rect popoverBounds) {
        if (layer == null || !Double.isFinite(x) || !Double.isFinite(y)) return false;
        if (popoverOpen) {
            if (popoverBounds.contains(x, y)) return layer == Layer.POPOVER;
            return layer == Layer.HEADER;
        }
        return layer != Layer.POPOVER && (layer != Layer.ROSTER || rosterClip.contains(x, y));
    }
}
