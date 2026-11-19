package com.solegendary.reignofnether.building.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Makes the dependency-free regression suite part of the normal Gradle test task. */
class OreProductionStateJUnitTest {
    @Test
    void dependencyFreeRegressionSuite() {
        OreProductionStateTest.main(new String[0]);
    }

    @Test
    void savedOwnerMismatchCannotTransferAnAlmostCompleteCycle() {
        // A save may contain old timer ownership if ownership changed just before saving.
        OreProductionState restored = OreProductionState.fromSaved(39, "previous-owner");
        assertEquals(0, restored.tick("current-owner", true));
        assertEquals("current-owner", restored.getOwner());
        assertEquals(1, restored.getElapsedTicks());
        for (int tick = 1; tick < OreProductionState.INTERVAL_TICKS - 1; tick++) {
            assertEquals(0, restored.tick("current-owner", true));
        }
        assertEquals(1, restored.tick("current-owner", true));
    }

    @Test
    void repeatedSaveRestoreKeepsExactProgressWithoutOfflineCredit() {
        OreProductionState state = new OreProductionState();
        int payouts = 0;
        int activeTicks = 0;
        for (int tick = 0; tick < 10_001; tick++) {
            boolean active = tick % 7 != 0;
            if (active) {
                activeTicks++;
            }
            payouts += state.tick("owner", active);
            state = OreProductionState.fromSaved(state.getElapsedTicks(), state.getOwner());
            assertEquals(activeTicks / OreProductionState.INTERVAL_TICKS, payouts);
            assertEquals(activeTicks % OreProductionState.INTERVAL_TICKS, state.getElapsedTicks());
        }
    }
}
