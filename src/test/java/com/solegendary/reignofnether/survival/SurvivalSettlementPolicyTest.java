package com.solegendary.reignofnether.survival;

import com.solegendary.reignofnether.player.RTSPlayerScores;
import com.solegendary.reignofnether.player.RTSPlayerScoresEnum;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SurvivalSettlementPolicyTest {
    @Test void failedFinalOverlapLeavesActive29And30RetryEvenAfterClearing29() {
        var old = new WaveCursor(30,29,false);
        assertSame(old,old.afterDispatch(false));
        assertEquals(30,old.forClient());
        assertEquals(new WaveCursor(30,0,false),old.clear());
        var last = old.clear().afterDispatch(true);
        assertEquals(WaveCursor.FINAL_ACTIVE,last.forClient());
        assertTrue(last.clear().finished());
        assertFalse(last.clear().canStart());
    }

    @Test void issuedOverlappingOrdinaryWavesStillAccumulateOneClear() {
        var old = new WaveCursor(8,7,false);
        assertSame(old,old.afterDispatch(false));
        var next = old.afterDispatch(true);
        assertEquals(new WaveCursor(9,8,false),next);
        assertEquals(new WaveCursor(9,0,false),next.clear());
    }

    @Test void terminalWinSnapshotsWinnerSetLocksBothScoreWritersAndResetReopensThem() {
        var previous = SurvivalResult.current();
        try {
            SurvivalResult.bind(SurvivalResult.OPEN);
            var old = new WaveCursor(8,7,false);
            var scores = new RTSPlayerScores();
            var key = RTSPlayerScoresEnum.ORES_HARVESTED;
            scores.addToScore(key,3); scores.addHarvestScore(key,4);
            Set<String> winners = new HashSet<>(Set.of("A","B"));
            assertTrue(SurvivalResult.finishOnce(SurvivalResult.Kind.BEACON_VICTORY,winners,95));
            winners.clear();
            assertEquals(Set.of("A","B"),SurvivalResult.current().winners());
            assertFalse(SurvivalResult.finishOnce(SurvivalResult.Kind.ALL_DEFEATED,Set.of(),999));
            assertEquals(95L,SurvivalResult.current().elapsed());
            assertEquals(SurvivalResult.BEACON_FREEPLAY,SurvivalResult.current().clientStatus(old));
            scores.addToScore(key); scores.addHarvestScore(key,Long.MAX_VALUE);
            assertEquals(7,scores.getScoreList().get(key).intValue());
            SurvivalResult.bind(SurvivalResult.OPEN);
            assertFalse(SurvivalResult.isAfterMatch());
            assertEquals(8,SurvivalResult.current().clientStatus(old));
            scores.addHarvestScore(key,Long.MAX_VALUE);
            assertEquals(Integer.MAX_VALUE,scores.getScoreList().get(key).intValue());
        } finally { SurvivalResult.bind(previous); }
    }

    @Test void lastWaveCompleteIsNotTerminalAndDefeatNeverHasWinners() {
        var previous=SurvivalResult.current();
        try {
            SurvivalResult.bind(SurvivalResult.OPEN);
            assertFalse(SurvivalResult.finishOnce(SurvivalResult.Kind.BEACON_VICTORY,Set.of(),100));
            var wavesDone = new WaveCursor(30,0,true);
            assertEquals(WaveCursor.FINISHED,SurvivalResult.current().clientStatus(wavesDone));
            assertFalse(SurvivalResult.isAfterMatch());
            assertTrue(SurvivalResult.finishOnce(SurvivalResult.Kind.ALL_DEFEATED,Set.of("stale"),123));
            assertTrue(SurvivalResult.current().winners().isEmpty());
            assertEquals(SurvivalResult.DEFEAT_FREEPLAY,SurvivalResult.current().clientStatus(wavesDone));
            assertFalse(SurvivalResult.finishOnce(SurvivalResult.Kind.BEACON_VICTORY,Set.of("late"),124));
        } finally { SurvivalResult.bind(previous); }
    }
}
