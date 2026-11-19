// SPDX-License-Identifier: GPL-3.0-only
package com.solegendary.reignofnether.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Pure clock/arithmetic checks, not a Minecraft GUI/network substitute. */
class ActiveClientTickClockJUnitTest {
    private static ActiveClientTickClock connected() {
        var clock = new ActiveClientTickClock();
        assertTrue(clock.context(new Object(), new Object()));
        return clock;
    }
    private static void advance(ActiveClientTickClock clock, int ticks) {
        for (int i = 0; i < ticks; i++) assertFalse(clock.endTick(false));
    }
    private static void setTicks(ActiveClientTickClock clock, long ticks) throws Exception {
        var field = ActiveClientTickClock.class.getDeclaredField("ticks");
        field.setAccessible(true);
        field.setLong(clock, ticks);
    }

    @Test void missingLevelOrConnectionNeverAdvances() {
        var clock = new ActiveClientTickClock();
        advance(clock, 100);
        clock.context(new Object(), null); advance(clock, 100);
        clock.context(null, new Object()); advance(clock, 100);
        assertEquals(0, clock.now());
    }

    @Test void frozenOrRewoundVisualTimeCannotAffect20Poll60TtlOr600Cooldown() {
        class VisualLevel { long gameTime = 6000; }
        var level = new VisualLevel(); var connection = new Object();
        var clock = new ActiveClientTickClock(); clock.context(level, connection);
        long received = clock.now(), queried = clock.now();
        for (int elapsed = 1; elapsed <= 600; elapsed++) {
            level.gameTime = elapsed < 300 ? 6000 : 100; // frozen daytime, then backwards time command
            assertFalse(clock.context(level, connection));
            assertFalse(clock.endTick(false));
            assertEquals(elapsed >= 20, clock.queryDue(queried, 20));
            assertEquals(elapsed <= 60, clock.fresh(received, 60));
            assertEquals(600 - elapsed, clock.remaining(received, 600));
        }
        assertEquals(600, clock.now());
    }

    @Test void pauseDoesNotConsumeAnyUiElapsedTime() {
        var clock = connected(); advance(clock, 19); long received = clock.now();
        for (int i = 0; i < 1200; i++) assertFalse(clock.endTick(true));
        assertEquals(19, clock.now()); assertFalse(clock.queryDue(0, 20));
        assertTrue(clock.fresh(received, 60)); assertEquals(600, clock.remaining(received, 600));
        advance(clock, 1); assertTrue(clock.queryDue(0, 20));
        assertEquals(599, clock.remaining(received, 600));
    }

    @Test void sameIdentityDoesNotResetButReplacementLevelDoes() {
        var clock = new ActiveClientTickClock(); var level = new Object(); var connection = new Object();
        assertTrue(clock.context(level, connection)); advance(clock, 37);
        assertFalse(clock.context(level, connection)); assertEquals(37, clock.now());
        assertTrue(clock.context(new Object(), connection)); assertEquals(0, clock.now());
        assertTrue(clock.queryDue(Long.MIN_VALUE, 20));
    }

    @Test void disconnectAndReconnectResetEvenWhenLevelIdentityIsReused() {
        var clock = new ActiveClientTickClock(); var level = new Object(); var connection = new Object();
        clock.context(level, connection); advance(clock, 37);
        assertTrue(clock.context(level, null)); assertEquals(0, clock.now());
        advance(clock, 80); assertEquals(0, clock.now());
        assertTrue(clock.context(level, new Object())); assertEquals(0, clock.now());
        advance(clock, 1); assertEquals(1, clock.now());
    }

    @Test void clearOnLoginOrLogoutDropsAllClockContext() {
        var clock = connected(); advance(clock, 45); clock.clear();
        advance(clock, 50); assertEquals(0, clock.now());
        assertFalse(clock.context(null, null));
        assertTrue(clock.context(new Object(), new Object()));
        assertTrue(clock.queryDue(Long.MIN_VALUE, 20));
    }

    @Test void equalityCannotKeepAReplacedLevelOrConnectionAlive() {
        class Equal { @Override public boolean equals(Object other) { return other instanceof Equal; }
            @Override public int hashCode() { return 1; } }
        var clock = new ActiveClientTickClock(); var level = new Equal(); var connection = new Equal();
        clock.context(level, connection); advance(clock, 9);
        assertTrue(clock.context(new Equal(), connection)); assertEquals(0, clock.now());
        advance(clock, 9); assertTrue(clock.context(level, new Equal())); assertEquals(0, clock.now());
    }

    @Test void queryAndTtlBoundariesMatchExisting20And60TickPolicy() {
        var clock = connected(); assertTrue(clock.queryDue(Long.MIN_VALUE, 20));
        advance(clock, 19); assertFalse(clock.queryDue(0, 20));
        advance(clock, 1); assertTrue(clock.queryDue(0, 20));
        advance(clock, 40); assertTrue(clock.fresh(0, 60));
        advance(clock, 1); assertFalse(clock.fresh(0, 60));
    }

    @Test void malformedStampsDoNotMakeSnapshotsFreshOrUnlockCooldowns() {
        var clock = connected(); advance(clock, 20);
        for (long invalid : new long[] {-1, Long.MIN_VALUE, 21, Long.MAX_VALUE}) {
            assertFalse(clock.fresh(invalid, 60));
            assertEquals(600, clock.remaining(invalid, 600));
            assertTrue(clock.queryDue(invalid, 20));
        }
        assertFalse(clock.fresh(0, -1));
        assertThrows(IllegalArgumentException.class, () -> clock.queryDue(0, 0));
    }

    @Test void relativeCooldownCannotOverflowBeforeTheClockRollsOver() throws Exception {
        var clock = connected(); setTicks(clock, Long.MAX_VALUE - 2);
        long received = clock.now();
        assertEquals(600, clock.remaining(received, 600));
        advance(clock, 2);
        assertEquals(Long.MAX_VALUE, clock.now());
        assertEquals(598, clock.remaining(received, 600));
        assertTrue(clock.fresh(received, 60));
    }

    @Test void rolloverSignalsMandatoryViewInvalidationAndSafelyRestarts() throws Exception {
        var clock = connected(); setTicks(clock, Long.MAX_VALUE);
        assertFalse(clock.endTick(true)); assertEquals(Long.MAX_VALUE, clock.now());
        assertTrue(clock.endTick(false)); assertEquals(0, clock.now());
        assertFalse(clock.fresh(Long.MAX_VALUE, 60));
        assertEquals(600, clock.remaining(Long.MAX_VALUE, 600));
        advance(clock, 1); assertEquals(1, clock.now());
    }

    @Test void separateClientsNeverMultiplyOrShareTickProgress() {
        var a = connected(); var b = connected(); advance(a, 20);
        assertEquals(20, a.now()); assertEquals(0, b.now());
        b.clear(); assertEquals(20, a.now());
    }

    @Test void sameViewCanPollAfterMutationAndCountDown600TicksWithoutReselection() {
        var clock = connected(); Object view = new Object(); Object originalView = view;
        long queried = clock.now(), received = clock.now(); int limit = 4, cooldown = 600, polls = 0;
        for (int elapsed = 1; elapsed <= 600; elapsed++) {
            assertFalse(clock.endTick(false));
            if (clock.queryDue(queried, 20)) {
                queried = clock.now(); polls++; // authoritative reply on the same view, not a new node
                limit = 6; cooldown = 600 - elapsed; received = clock.now();
            }
            assertSame(originalView, view);
            assertTrue(clock.fresh(received, 60));
            assertEquals(600 - elapsed, clock.remaining(received, cooldown));
            if (elapsed >= 20) assertEquals(6, limit);
        }
        assertEquals(30, polls); assertEquals(0, clock.remaining(received, cooldown));
    }

    @Test void pausedPacketArrivalCanStampAViewWithoutAdvancingOrBlockingAck() {
        var clock = connected(); advance(clock, 7);
        for (int i = 0; i < 100; i++) clock.endTick(true);
        long received = clock.now(); // ACK handling itself has no pause/elapsed prerequisite
        assertEquals(7, received); assertTrue(clock.fresh(received, 60));
        advance(clock, 20); assertTrue(clock.queryDue(received, 20));
    }

    @Test void fortyActiveTickRetryNeverLoopsAtPausedModuloBoundary() {
        var clock = connected(); int retries = 0;
        for (int elapsed = 1; elapsed <= 80; elapsed++) {
            assertFalse(clock.endTick(false));
            if (clock.now() > 0 && clock.now() % 40 == 0) retries++;
            if (elapsed == 40) {
                for (int pausedFrames = 0; pausedFrames < 1000; pausedFrames++) {
                    boolean paused = true;
                    assertFalse(clock.endTick(paused));
                    if (!paused && clock.now() > 0 && clock.now() % 40 == 0) retries++;
                }
                assertEquals(40, clock.now()); assertEquals(1, retries);
            }
        }
        assertEquals(2, retries);
    }

    @Test void largestValidStampDifferenceAndDurationsDoNotWrap() throws Exception {
        var clock = connected(); setTicks(clock, Long.MAX_VALUE);
        assertTrue(clock.queryDue(0, Long.MAX_VALUE));
        assertTrue(clock.fresh(0, Long.MAX_VALUE));
        assertFalse(clock.fresh(0, Long.MAX_VALUE - 1));
        assertEquals(0, clock.remaining(0, Long.MAX_VALUE));
        assertEquals(0, clock.remaining(0, 600));
        assertEquals(Long.MAX_VALUE, clock.remaining(clock.now(), Long.MAX_VALUE));
    }
}
