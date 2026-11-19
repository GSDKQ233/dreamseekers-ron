package com.solegendary.reignofnether.building.data;

/**
 * Dependency-free regression tests. Run with a JDK without starting Forge:
 *
 * javac --release 17 -d build/ore-state-test \
 *   src/main/java/com/solegendary/reignofnether/building/data/OreProductionState.java \
 *   src/test/java/com/solegendary/reignofnether/building/data/OreProductionStateTest.java
 * java -cp build/ore-state-test \
 *   com.solegendary.reignofnether.building.data.OreProductionStateTest
 *
 * These tests exercise production timing only, not Minecraft integration.
 */
public final class OreProductionStateTest {
    private static int assertions;

    public static void main(String[] args) {
        firstPaymentRequiresFullCycle();
        repeatedCyclesHaveExactRate();
        inactiveTicksPauseProgress();
        activeOwnershipChangeRestartsCycle();
        inactiveOwnershipChangeRestartsCycle();
        losingOwnershipDiscardsProgress();
        blankAndNullOwnersNeverProduce();
        savedPartialCycleResumesWithoutCatchUp();
        invalidSavedProgressIsClamped();
        separateBuildingsHaveIndependentProgress();
        System.out.println("OreProductionStateTest: 10 scenarios passed (" + assertions + " assertions)");
    }

    private static void firstPaymentRequiresFullCycle() {
        OreProductionState state = new OreProductionState();
        equal(0, state.getElapsedTicks(), "new building starts empty");
        equal("", state.getOwner(), "new building has no owner");
        for (int tick = 1; tick < OreProductionState.INTERVAL_TICKS; tick++) {
            equal(0, state.tick("Alice", true), "no early payment at tick " + tick);
            equal(tick, state.getElapsedTicks(), "eligible tick advances once");
        }
        equal(1, state.tick("Alice", true), "full cycle pays once");
        equal(0, state.getElapsedTicks(), "payment resets cycle");
        equal(0, state.tick("Alice", true), "next tick does not pay twice");
    }

    private static void repeatedCyclesHaveExactRate() {
        OreProductionState state = new OreProductionState();
        int payments = 0;
        for (int tick = 0; tick < 100_003; tick++) {
            int payment = state.tick("Alice", true);
            truth(payment == 0 || payment == 1, "at most one payment per tick");
            truth(state.getElapsedTicks() >= 0 &&
                    state.getElapsedTicks() < OreProductionState.INTERVAL_TICKS,
                    "progress remains bounded");
            payments += payment;
        }
        equal(100_003 / OreProductionState.INTERVAL_TICKS, payments, "long-run exact cycle count");
        equal(100_003 % OreProductionState.INTERVAL_TICKS, state.getElapsedTicks(), "long-run remainder");
    }

    private static void inactiveTicksPauseProgress() {
        OreProductionState state = OreProductionState.fromSaved(17, "Alice");
        for (int tick = 0; tick < 500; tick++) {
            equal(0, state.tick("Alice", false), "inactive building does not pay");
        }
        equal(17, state.getElapsedTicks(), "inactivity preserves partial cycle");
        for (int tick = 0; tick < 22; tick++) {
            equal(0, state.tick("Alice", true), "resuming does not bank inactive ticks");
        }
        equal(1, state.tick("Alice", true), "resume completes remaining eligible ticks");
    }

    private static void activeOwnershipChangeRestartsCycle() {
        OreProductionState state = OreProductionState.fromSaved(39, "Alice");
        equal(0, state.tick("Bob", true), "new owner cannot inherit imminent payment");
        equal("Bob", state.getOwner(), "current owner recorded");
        equal(1, state.getElapsedTicks(), "capture tick is first new eligible tick");
        for (int tick = 0; tick < 38; tick++) {
            equal(0, state.tick("Bob", true), "new owner needs own full cycle");
        }
        equal(1, state.tick("Bob", true), "new owner full cycle pays");
    }

    private static void inactiveOwnershipChangeRestartsCycle() {
        OreProductionState state = OreProductionState.fromSaved(39, "Alice");
        equal(0, state.tick("Bob", false), "inactive ownership change does not pay");
        equal("Bob", state.getOwner(), "inactive ownership change is observed");
        equal(0, state.getElapsedTicks(), "inactive ownership change clears progress");
        equal(0, state.tick("Alice", true), "returning ownership does not restore old progress");
        equal(1, state.getElapsedTicks(), "returning owner starts fresh");
    }

    private static void losingOwnershipDiscardsProgress() {
        OreProductionState state = OreProductionState.fromSaved(39, "Alice");
        equal(0, state.tick("", false), "loss of ownership does not pay");
        equal(0, state.getElapsedTicks(), "loss of ownership clears progress immediately");
        equal(0, state.tick("Alice", true), "reclaim does not pay immediately");
        equal(1, state.getElapsedTicks(), "reclaim starts a fresh cycle");
    }

    private static void blankAndNullOwnersNeverProduce() {
        OreProductionState state = new OreProductionState();
        for (int tick = 0; tick < 500; tick++) {
            equal(0, state.tick(null, true), "null owner cannot produce even if active");
            equal(0, state.tick(" \t", true), "blank owner cannot produce even if active");
            equal(0, state.tick("", false), "unowned inactive building cannot produce");
        }
        equal(0, state.getElapsedTicks(), "unowned ticks never accumulate");
        equal("", state.getOwner(), "null and blank normalize to unowned");
    }

    private static void savedPartialCycleResumesWithoutCatchUp() {
        OreProductionState before = OreProductionState.fromSaved(23, "Alice");
        OreProductionState after = OreProductionState.fromSaved(before.getElapsedTicks(), before.getOwner());
        equal(23, after.getElapsedTicks(), "save/load retains elapsed ticks");
        equal("Alice", after.getOwner(), "save/load retains production owner");
        for (int tick = 0; tick < 16; tick++) {
            equal(0, after.tick("Alice", true), "load grants no catch-up or early payment");
        }
        equal(1, after.tick("Alice", true), "load resumes remaining cycle exactly");
    }

    private static void invalidSavedProgressIsClamped() {
        equal(0, OreProductionState.fromSaved(-1, "Alice").getElapsedTicks(), "negative saved progress clamps to zero");
        equal(0, OreProductionState.fromSaved(Integer.MIN_VALUE, "Alice").getElapsedTicks(), "minimum integer safely clamps");
        equal(39, OreProductionState.fromSaved(40, "Alice").getElapsedTicks(), "complete saved cycle clamps below interval");
        equal(39, OreProductionState.fromSaved(Integer.MAX_VALUE, "Alice").getElapsedTicks(), "maximum integer safely clamps");
        equal(0, OreProductionState.fromSaved(39, null).getElapsedTicks(), "missing saved owner discards partial cycle");
        equal(0, OreProductionState.fromSaved(39, "  ").getElapsedTicks(), "blank saved owner discards partial cycle");
        equal("", OreProductionState.fromSaved(39, null).getOwner(), "missing saved owner normalizes");
    }

    private static void separateBuildingsHaveIndependentProgress() {
        OreProductionState first = OreProductionState.fromSaved(39, "Alice");
        OreProductionState second = OreProductionState.fromSaved(12, "Alice");
        equal(1, first.tick("Alice", true), "first building completes cycle");
        equal(12, second.getElapsedTicks(), "first building cannot advance second");
        equal(0, second.tick("Alice", true), "second building retains its own cycle");
        equal(13, second.getElapsedTicks(), "second building advances independently");
        equal(0, first.getElapsedTicks(), "second building cannot advance first");
    }

    private static void equal(int expected, int actual, String message) {
        assertions++;
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void equal(String expected, String actual, String message) {
        assertions++;
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": expected '" + expected + "', got '" + actual + "'");
        }
    }

    private static void truth(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
