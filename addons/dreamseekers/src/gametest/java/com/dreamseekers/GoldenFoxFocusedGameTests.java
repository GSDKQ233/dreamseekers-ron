// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
/** Focused aliases delegate unchanged assertions to the aggregate suite. */
@GameTestHolder("dreamseekers_fox_focused")
@PrefixGameTestTemplate(false)
public final class GoldenFoxFocusedGameTests {
    @AfterBatch(batch="dreamseekers_fox_focused_0") public static void cleanup0(ServerLevel level) { GoldenFoxMovementGameTests.cleanup(level); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_fox_focused_0", timeoutTicks=500)
    public static void groundPathGoesAroundSolidBarrierWithoutFlightOrClipping(GameTestHelper h) throws Exception { GoldenFoxMovementGameTests.groundPathGoesAroundSolidBarrierWithoutFlightOrClipping(h); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_fox_focused_0", timeoutTicks=690)
    public static void enclosedGroundFoxCannotPhaseAndRetriesOpenedExit(GameTestHelper h) throws Exception { GoldenFoxMovementGameTests.enclosedGroundFoxCannotPhaseAndRetriesOpenedExit(h); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_fox_focused_0", timeoutTicks=780)
    public static void queuedMovesHoldStopAndReplacementNeverResumeStalePath(GameTestHelper h) throws Exception { GoldenFoxMovementGameTests.queuedMovesHoldStopAndReplacementNeverResumeStalePath(h); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_fox_focused_0", timeoutTicks=250)
    public static void hostileCommandsCannotAttackOrTrackAndAllianceLossStopsFollow(GameTestHelper h) throws Exception { GoldenFoxMovementGameTests.hostileCommandsCannotAttackOrTrackAndAllianceLossStopsFollow(h); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_fox_focused_0", timeoutTicks=300)
    public static void nativeNoAiCannotReturnThroughNbtAndWildNpcRemainsStationary(GameTestHelper h) throws Exception { GoldenFoxMovementGameTests.nativeNoAiCannotReturnThroughNbtAndWildNpcRemainsStationary(h); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_fox_focused_0", timeoutTicks=80)
    public static void malformedNonlivingMissingAndRemovedTargetsCannotCrashNoncombatCommands(GameTestHelper h) throws Exception { GoldenFoxMovementGameTests.malformedNonlivingMissingAndRemovedTargetsCannotCrashNoncombatCommands(h); }
    @AfterBatch(batch="dreamseekers_fox_focused_1") public static void cleanup1(ServerLevel level) { GoldenFoxProductionGameTests.cleanup(level); }
    @GameTest(template="dreamseekers_test_arena", batch="dreamseekers_fox_focused_1", timeoutTicks=750)
    public static void exactPaidFoxQueueRequiresOwnCourtAndPausesWithoutLosingPayment(GameTestHelper h) throws Exception { GoldenFoxProductionGameTests.exactPaidFoxQueueRequiresOwnCourtAndPausesWithoutLosingPayment(h); }
    @GameTest(template="dreamseekers_test_arena", batch="dreamseekers_fox_focused_1", timeoutTicks=720)
    public static void blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper h) throws Exception { GoldenFoxProductionGameTests.blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(h); }
    @GameTest(template="dreamseekers_test_arena", batch="dreamseekers_fox_focused_1", timeoutTicks=100)
    public static void populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper h) throws Exception { GoldenFoxProductionGameTests.populationResourcesAndRepeatedCancellationNeverDuplicate(h); }
    @GameTest(template="dreamseekers_test_arena", batch="dreamseekers_fox_focused_1", timeoutTicks=100)
    public static void actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper h) throws Exception { GoldenFoxProductionGameTests.actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(h); }
    @AfterBatch(batch="dreamseekers_fox_focused_2") public static void cleanup2(ServerLevel level) { GoldenFoxInteractionGameTests.cleanup(level); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_fox_focused_2", timeoutTicks=70)
    public static void ownerEnemyAndRealAllyCannotTradeCaptureOrDuplicatePaidFox(GameTestHelper h) throws Exception { GoldenFoxInteractionGameTests.ownerEnemyAndRealAllyCannotTradeCaptureOrDuplicatePaidFox(h); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_fox_focused_2", timeoutTicks=70)
    public static void directDelayedCallbacksCannotRewardLiveUnownedTransferredOrRemovedPaidFox(GameTestHelper h) throws Exception { GoldenFoxInteractionGameTests.directDelayedCallbacksCannotRewardLiveUnownedTransferredOrRemovedPaidFox(h); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_fox_focused_2", timeoutTicks=70)
    public static void originalWildFoxKeepsAllTradesAndRealDelayedRewards(GameTestHelper h) throws Exception { GoldenFoxInteractionGameTests.originalWildFoxKeepsAllTradesAndRealDelayedRewards(h); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_fox_focused_2", timeoutTicks=140)
    public static void paidFoxTakesOrdinaryDamageAndDeathLootIsEmptyWhileWildImmunitiesRemain(GameTestHelper h) throws Exception { GoldenFoxInteractionGameTests.paidFoxTakesOrdinaryDamageAndDeathLootIsEmptyWhileWildImmunitiesRemain(h); }
    @AfterBatch(batch="dreamseekers_fox_focused_3") public static void cleanup3(ServerLevel level) { GoldenFoxCommandAuthorityGameTests.cleanup(level); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_fox_focused_3", timeoutTicks=80)
    public static void authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(GameTestHelper h) throws Exception { GoldenFoxCommandAuthorityGameTests.authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(h); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_fox_focused_3", timeoutTicks=80)
    public static void ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(GameTestHelper h) throws Exception { GoldenFoxCommandAuthorityGameTests.ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(h); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_fox_focused_3", timeoutTicks=80)
    public static void buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(GameTestHelper h) throws Exception { GoldenFoxCommandAuthorityGameTests.buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(h); }
}
