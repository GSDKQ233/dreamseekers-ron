package com.dreamseekers.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TargetModeButtonsTest {
    @Test void secondSameButtonClickTurnsModeOffInPostNotBackOn() {
        var first=TargetModeButtons.toggle(null,"beetle");
        var afterPre=TargetModeButtons.beforeHudClick(first,true);
        assertEquals("beetle",afterPre);
        assertNull(TargetModeButtons.toggle(afterPre,"beetle"));
    }
    @Test void differentControlsAndDisabledButtonsDoNotKeepPriorMode() {
        String different=TargetModeButtons.beforeHudClick("beetle",false);
        assertNull(different);
        assertEquals("snail",TargetModeButtons.toggle(different,"snail"));
        assertNull(TargetModeButtons.beforeHudClick("contract-worker",false));
    }
}
