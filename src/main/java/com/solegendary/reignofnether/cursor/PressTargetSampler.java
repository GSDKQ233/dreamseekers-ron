package com.solegendary.reignofnether.cursor;

import java.lang.ref.WeakReference;

/** Pick once per physical press, not once per render frame or per coordinate. An entity may
 * move under a stationary pointer between two clicks. Never retain the owning screen. */
public final class PressTargetSampler {
    public interface Picker {
        boolean overHud(int x, int y);
        void pickWorld(int x, int y);
    }

    private WeakReference<Object> last = new WeakReference<>(null);

    public void sample(Object press, double mouseX, double mouseY, Picker picker) {
        if (press == null || last.get() == press) return;
        last = new WeakReference<>(press);
        int x = (int)Math.floor(mouseX), y = (int)Math.floor(mouseY);
        if (!picker.overHud(x, y)) picker.pickWorld(x, y);
    }
}
