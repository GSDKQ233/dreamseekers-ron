package com.solegendary.reignofnether.matchstart;

/** Screen-independent geometry and bounded scrolling, also used by small-window regression tests. */
public record MatchResultsLayout(int left, int top, int width, int height, int bodyTop,
                                 int bodyBottom, boolean compact, int contentHeight) {
    public static MatchResultsLayout create(int screenWidth, int screenHeight, int contentHeight) {
        int width = Math.max(1, Math.min(680, screenWidth - 16));
        int height = Math.max(1, Math.min(Math.max(100, contentHeight + 82), screenHeight - 16));
        int left = Math.max(0, (screenWidth - width) / 2), top = Math.max(0, (screenHeight - height) / 2);
        int bodyTop = Math.min(top + height, top + 44);
        int bodyBottom = Math.max(bodyTop, top + height - 34);
        return new MatchResultsLayout(left, top, width, height, bodyTop, bodyBottom,
                width < 520, Math.max(0, contentHeight));
    }
    public int viewportHeight() { return Math.max(0, bodyBottom - bodyTop); }
    public int maxScroll() { return Math.max(0, contentHeight - viewportHeight()); }
    public double clampScroll(double value) {
        return Double.isFinite(value) ? Math.max(0, Math.min(maxScroll(), value)) : 0;
    }
}
