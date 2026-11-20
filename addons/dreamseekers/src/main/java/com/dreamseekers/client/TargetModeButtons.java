package com.dreamseekers.client;

import java.util.Objects;

/** Two-stage HUD dispatch: Pre may cancel an old mode, but the clicked button's Post
 * callback owns its own toggle. Never clear a same-button mode twice in one press. */
public final class TargetModeButtons {
    private TargetModeButtons() {}
    public static <T> T beforeHudClick(T current, boolean sameEnabledControl) {
        return sameEnabledControl ? current : null;
    }
    public static <T> T toggle(T current,T clicked) {
        return Objects.equals(current,clicked) ? null : clicked;
    }
}
