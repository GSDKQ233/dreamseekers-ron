package com.solegendary.reignofnether.orthoview;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraPanMathTest {
    @Test void everySupportedDurationEndsAtTheSamePosition() {
        for (int ticks : new int[]{1, 10, 20, 40, 100}) {
            float x = -120f, z = 47f;
            for (int i=0; i<ticks; i++) {
                x += CameraPanMath.step(-120f, 280f, ticks);
                z += CameraPanMath.step(47f, -153f, ticks);
            }
            assertEquals(280f, x, 0.001f, "x duration="+ticks);
            assertEquals(-153f, z, 0.001f, "z duration="+ticks);
        }
    }
    @Test void zoomUsesTheSameRequestedDurationAsPosition() {
        for (int ticks : new int[]{1, 10, 20, 40}) {
            float zoom = 30f;
            for (int i=0; i<ticks; i++) zoom += CameraPanMath.step(30f, 70f, ticks);
            assertEquals(70f, zoom, 0.001f);
        }
    }
    @Test void twentyTickDefaultAndStationaryMoveArePreserved() {
        assertEquals((100f-20f)/20f, CameraPanMath.step(20f,100f,20));
        assertEquals(0f, CameraPanMath.step(42f,42f,40));
    }
    @Test void invalidDurationCannotDivideByZeroAndLargeLocksCannotWrapNegative() {
        assertEquals(1, CameraPanMath.duration(0));
        assertEquals(1, CameraPanMath.duration(-10));
        assertEquals(12f, CameraPanMath.step(1f,13f,0));
        assertEquals(50, CameraPanMath.lockDuration(40,10));
        assertEquals(Integer.MAX_VALUE, CameraPanMath.lockDuration(Integer.MAX_VALUE,10));
    }
}
