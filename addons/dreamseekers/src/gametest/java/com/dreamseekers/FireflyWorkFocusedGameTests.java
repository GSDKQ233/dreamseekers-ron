// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Optional diagnostic aliases; the ordinary aggregate runs the original tests only once. */
@GameTestHolder("dreamseekers_firefly_work")
@PrefixGameTestTemplate(false)
public final class FireflyWorkFocusedGameTests {
    private static final String BATCH="dreamseekers_firefly_work";
    @AfterBatch(batch=BATCH)
    public static void cleanup(ServerLevel level) {
        DreamseekersGameTests.cleanup(level);
        GardenWorkerGameTests.cleanup(level);
        FireflyEconomyGameTests.cleanup(level);
        FireflyRepairGameTests.cleanup(level);
        FireflyWorkerGameTests.cleanup(level);
    }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=340)
    public static void genuinelyFliesOverBarrier(GameTestHelper helper) { FireflyWorkerGameTests.genuinelyFliesOverTwoBlockBarrierWithoutCollision(helper); }
    @GameTest(template="unit_isolation_arena",batch=BATCH,timeoutTicks=490)
    public static void fixedCeilingAndOpenedRoute(GameTestHelper helper) { FireflyWorkerGameTests.lowFlightCeilingDoesNotRatchetAndRetriesWhenOpeningAppears(helper); }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1900)
    public static void paidRepairStopResume(GameTestHelper helper) { FireflyRepairGameTests.physicallyApproachesPaidRepairStopsAndResumesWithoutFreeRestoration(helper); }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1300)
    public static void linkedLogThenExplicitReturn(GameTestHelper helper) { DreamseekersGameTests.workerBuildsAndHarvestsLinkedLog(helper); }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=1500)
    public static void harvestReplantThenExplicitReturn(GameTestHelper helper) { GardenWorkerGameTests.gardenWorkerHarvestsReplantsAndDeliversFood(helper); }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=3000)
    public static void automaticThresholdReturnAndResume(GameTestHelper helper) { FireflyEconomyGameTests.thresholdAutomaticallyReturnsThenResumesFarmWithoutDuplicateCredit(helper); }
}
