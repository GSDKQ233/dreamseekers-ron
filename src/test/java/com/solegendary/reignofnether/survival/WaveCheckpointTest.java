package com.solegendary.reignofnether.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WaveCheckpointTest {
    @Test void startsAndClearsAreDifferentAndCannotClaimTheSameClearTwice() {
        var pending = new WaveCursor(3, 0, false);
        var active = pending.start();
        assertEquals(new WaveCursor(4, 3, false), active);
        var settled = active.clear();
        assertEquals(new WaveCursor(4, 0, false), settled);
        assertSame(settled, settled.clear());
        assertEquals(4, settled.start().active());
        assertEquals(4, active.start().active()); // an overlapping NIGHT adds wave 4, never reissues 3
    }

    @Test void lastWaveIsNotItsOwnSuccessorAndDoesNotGrantAVictory() {
        var waiting = new WaveCursor(30, 0, false);
        var fighting = waiting.start();
        assertEquals(30, waiting.forClient());
        assertEquals(WaveCursor.FINAL_ACTIVE, fighting.forClient());
        assertFalse(fighting.canStart());
        assertSame(fighting, fighting.start());
        var finished = fighting.clear();
        assertEquals(new WaveCursor(30, 0, true), finished);
        assertFalse(finished.canStart());
        assertEquals(WaveCursor.FINISHED, finished.forClient());
        assertSame(finished, finished.start());
        assertSame(finished, finished.clear());
    }

    @Test void legacyCursorIsNotEvidenceThatAWaveOrRewardCompleted() {
        CompoundTag old = new CompoundTag();
        old.putBoolean("isEnabled", true);
        old.putInt("waveNumber", 30);
        old.putString("difficulty", "HARD");
        old.putLong("randomSeed", 8729L);
        var data = SurvivalSaveData.load(old);
        assertEquals(new WaveCursor(30, 0, false), data.cursor());
        assertTrue(data.cursor().canStart());
        assertEquals(WaveDifficulty.HARD, data.difficulty);
        assertEquals(8729L, data.randomSeed);
        assertTrue(data.activeEnemies.isEmpty());
        assertNull(data.lastKill);
        old.putInt("waveNumber", Integer.MIN_VALUE);
        old.putString("difficulty", "unknown");
        assertEquals(new WaveCursor(1, 0, false), SurvivalSaveData.load(old).cursor());
        assertEquals(WaveDifficulty.BEGINNER, SurvivalSaveData.load(old).difficulty);
    }

    @Test void activeCheckpointRetainsExactBodiesEarlierPortalBudgetAndDeathLocation() {
        var id = UUID.randomUUID();
        var portal = new BlockPos(-9, 64, 120);
        var data = new SurvivalSaveData();
        data.isEnabled = true;
        data.setCursor(new WaveCursor(8, 7, false));
        data.activeEnemies.add(id);
        data.activePortals.put(portal, new SurvivalSaveData.PortalState(6, 2));
        data.portalSpawnTicks = 125;
        data.lastKill = new Vec3(1.25, 100.5, -9.5);
        var decoded = SurvivalSaveData.load(data.save(new CompoundTag()));
        assertEquals(data.cursor(), decoded.cursor());
        assertEquals(data.activeEnemies, decoded.activeEnemies);
        assertEquals(data.activePortals, decoded.activePortals);
        assertEquals(125, decoded.portalSpawnTicks);
        assertEquals(data.lastKill, decoded.lastKill);
        data.setCursor(data.cursor().clear());
        var cleared = SurvivalSaveData.load(data.save(new CompoundTag()));
        assertEquals(new WaveCursor(8, 0, false), cleared.cursor());
        assertTrue(cleared.activeEnemies.isEmpty() && cleared.activePortals.isEmpty());
        assertNull(cleared.lastKill);
    }
}
