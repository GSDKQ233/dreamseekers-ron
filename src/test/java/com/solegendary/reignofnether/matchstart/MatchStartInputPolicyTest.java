package com.solegendary.reignofnether.matchstart;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.solegendary.reignofnether.matchstart.MatchStartInputPolicy.*;

class MatchStartInputPolicyTest {
    private static final Rect CLIP = new Rect(436,74,836,280);
    private boolean allow(Layer layer,int x,int y,boolean popup,Rect bounds) {
        return accepts(layer,x,y,CLIP,popup,bounds);
    }
    @Test void hiddenNinthReadyButtonCannotConsumeChatClick() {
        // Screen854x480, nine same-team slots, own ninth row: tile bounds810,295..832,317.
        Rect ready = new Rect(810,295,832,317);
        assertTrue(ready.contains(814,310));
        assertFalse(allow(Layer.ROSTER,814,310,false,EMPTY));
    }
    @Test void sameOwnControlScrolledIntoViewStillAcceptsExactlyOneClick() {
        Rect scrolledReady = new Rect(810,191,832,213);
        int emitted = scrolledReady.contains(814,200)
                && allow(Layer.ROSTER,814,200,false,EMPTY) ? 1 : 0;
        assertEquals(1,emitted);
    }
    @Test void rosterEdgesRowsAndSpectatorControlsShareTheSameClip() {
        assertFalse(allow(Layer.ROSTER,450,73,false,EMPTY));
        assertTrue(allow(Layer.ROSTER,436,74,false,EMPTY));
        assertFalse(allow(Layer.ROSTER,836,100,false,EMPTY));
        assertFalse(allow(Layer.ROSTER,800,280,false,EMPTY));
    }
    @Test void popoverConsumesItsBackgroundAndSuppressesUnderlyingRosterAndMap() {
        Rect popup = popoverBounds(777,45,16);
        assertTrue(popup.contains(700,100));
        assertTrue(allow(Layer.POPOVER,700,100,true,popup));
        assertFalse(allow(Layer.ROSTER,700,100,true,popup));
        assertFalse(allow(Layer.MAP,200,100,true,popup));
        assertFalse(allow(Layer.HEADER,700,30,true,popup));
        assertTrue(allow(Layer.HEADER,820,30,true,popup));
        assertFalse(allow(Layer.POPOVER,700,100,false,popup));
    }
    @Test void nonfiniteCoordinatesAndEmptyInitialClipCannotActivateControls() {
        assertFalse(accepts(Layer.ROSTER,10,10,EMPTY,false,EMPTY));
        assertFalse(accepts(Layer.HEADER,Double.NaN,10,CLIP,false,EMPTY));
        assertFalse(accepts(Layer.HEADER,10,Double.POSITIVE_INFINITY,CLIP,false,EMPTY));
    }
    @Test void screenActuallyWiresBothButtonsAndNonbuttonRowsToThePolicy() throws Exception {
        String s=Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/matchstart/MatchStartScreen.java"));
        assertTrue(s.contains("addHudButton(button, MatchStartInputPolicy.Layer.ROSTER)"));
        assertTrue(s.contains("&& acceptsRoster(mx, my)"));
        assertTrue(s.contains("if (left && acceptsRoster(mx, my))"));
        assertTrue(s.contains("if (dispatchButtons(mx, my, left, false)) return true;"));
        assertTrue(s.contains("dispatchButtons(mx, my, left, true)"));
        assertFalse(s.contains("b.checkClicked(mx, my, left)"));
    }
}
