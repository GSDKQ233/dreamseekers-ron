// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
/** New unit cases plus affected existing Hall receipt suite, not a full faction aggregate. */
@GameTestHolder("dreamseekers_wind_knight_focused")
@PrefixGameTestTemplate(false)
public final class WindKnightFocusedGameTests {
    @AfterBatch(batch="dreamseekers_wind_knight_focused_0") public static void cleanup0(ServerLevel level) { WindKnightMovementGameTests.cleanup(level); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_wind_knight_focused_0",timeoutTicks=500)
    public static void case0_groundPathGoesAroundSolidBarrierWithoutFlightOrClipping(GameTestHelper h) throws Exception { WindKnightMovementGameTests.groundPathGoesAroundSolidBarrierWithoutFlightOrClipping(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_wind_knight_focused_0",timeoutTicks=690)
    public static void case0_enclosedGroundSlimeCannotPhaseAndRetriesOpenedExit(GameTestHelper h) throws Exception { WindKnightMovementGameTests.enclosedGroundSlimeCannotPhaseAndRetriesOpenedExit(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_wind_knight_focused_0",timeoutTicks=780)
    public static void case0_queuedMovesHoldStopAndReplacementNeverResumeStalePath(GameTestHelper h) throws Exception { WindKnightMovementGameTests.queuedMovesHoldStopAndReplacementNeverResumeStalePath(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_wind_knight_focused_0",timeoutTicks=80)
    public static void case0_malformedNonlivingMissingAndRemovedTargetsCannotCrashNoncombatCommands(GameTestHelper h) throws Exception { WindKnightMovementGameTests.malformedNonlivingMissingAndRemovedTargetsCannotCrashNoncombatCommands(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_wind_knight_focused_0",timeoutTicks=760)
    public static void case0_nativeWideBodyCannotUseOneBlockGapButUsesThreeBlockOpening(GameTestHelper h) throws Exception { WindKnightMovementGameTests.nativeWideBodyCannotUseOneBlockGapButUsesThreeBlockOpening(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_wind_knight_focused_0",timeoutTicks=760)
    public static void case0_threeBlockCeilingStopsNativeTallBodyUntilActuallyRemoved(GameTestHelper h) throws Exception { WindKnightMovementGameTests.threeBlockCeilingStopsNativeTallBodyUntilActuallyRemoved(h); }
    @AfterBatch(batch="dreamseekers_wind_knight_focused_1") public static void cleanup1(ServerLevel level) { WindKnightCommandAuthorityGameTests.cleanup(level); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_wind_knight_focused_1",timeoutTicks=80)
    public static void case1_authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(GameTestHelper h) throws Exception { WindKnightCommandAuthorityGameTests.authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_wind_knight_focused_1",timeoutTicks=80)
    public static void case1_ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(GameTestHelper h) throws Exception { WindKnightCommandAuthorityGameTests.ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_wind_knight_focused_1",timeoutTicks=80)
    public static void case1_buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(GameTestHelper h) throws Exception { WindKnightCommandAuthorityGameTests.buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(h); }
    @AfterBatch(batch="dreamseekers_wind_knight_focused_2") public static void cleanup2(ServerLevel level) { WindKnightProductionGameTests.cleanup(level); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_wind_knight_focused_2",timeoutTicks=1050)
    public static void case2_exactHallWhitelistAndFullPaidQueueProduceOneLightWindKnight(GameTestHelper h) throws Exception { WindKnightProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneLightWindKnight(h); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_wind_knight_focused_2",timeoutTicks=280)
    public static void case2_blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper h) throws Exception { WindKnightProductionGameTests.blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(h); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_wind_knight_focused_2",timeoutTicks=100)
    public static void case2_populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper h) throws Exception { WindKnightProductionGameTests.populationResourcesAndRepeatedCancellationNeverDuplicate(h); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_wind_knight_focused_2",timeoutTicks=100)
    public static void case2_actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper h) throws Exception { WindKnightProductionGameTests.actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(h); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_wind_knight_focused_2",timeoutTicks=340)
    public static void case2_buildingCombatRejectsFriendlyStaleCrossDimensionAndHoldMovement(GameTestHelper h) throws Exception { WindKnightProductionGameTests.buildingCombatRejectsFriendlyStaleCrossDimensionAndHoldMovement(h); }
    @AfterBatch(batch="dreamseekers_wind_knight_focused_3") public static void cleanup3(ServerLevel level) { WindKnightSafetyGameTests.cleanup(level); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_wind_knight_focused_3",timeoutTicks=100)
    public static void case3_interactionsDamageAndDeathCannotYieldWildRewards(GameTestHelper h) throws Exception { WindKnightSafetyGameTests.interactionsDamageAndDeathCannotYieldWildRewards(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_wind_knight_focused_3",timeoutTicks=240)
    public static void case3_meleeIsRealAndSameFactionEnemiesAreNotAllies(GameTestHelper h) throws Exception { WindKnightSafetyGameTests.meleeIsRealAndSameFactionEnemiesAreNotAllies(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_wind_knight_focused_3",timeoutTicks=80)
    public static void case3_ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(GameTestHelper h) throws Exception { WindKnightSafetyGameTests.ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_wind_knight_focused_3",timeoutTicks=300)
    public static void case3_holdWithEnemyNeverChasesButStillHitsInsideMeleeReach(GameTestHelper h) throws Exception { WindKnightSafetyGameTests.holdWithEnemyNeverChasesButStillHitsInsideMeleeReach(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_wind_knight_focused_3",timeoutTicks=220)
    public static void case3_friendlyFollowMovesAndRevokedAllianceCancelsItsPath(GameTestHelper h) throws Exception { WindKnightSafetyGameTests.friendlyFollowMovesAndRevokedAllianceCancelsItsPath(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_wind_knight_focused_3",timeoutTicks=220)
    public static void case3_revokedFollowAllowsNewHostileAutoAggroWithoutRevivingOldCommand(GameTestHelper h) throws Exception { WindKnightSafetyGameTests.revokedFollowAllowsNewHostileAutoAggroWithoutRevivingOldCommand(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_wind_knight_focused_3",timeoutTicks=240)
    public static void case3_sameEnemyExplicitAttackReleasesHoldAndReallyPursues(GameTestHelper h) throws Exception { WindKnightSafetyGameTests.sameEnemyExplicitAttackReleasesHoldAndReallyPursues(h); }
    @AfterBatch(batch="dreamseekers_wind_knight_focused_4") public static void cleanup4(ServerLevel level) { WindKnightNativeGameTests.cleanup(level); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_wind_knight_focused_4",timeoutTicks=300)
    public static void case4_actualNativeDelayedAoeStillHitsWildSideButPaidBaseTicksCannotScheduleIt(GameTestHelper h) throws Exception { WindKnightNativeGameTests.actualNativeDelayedAoeStillHitsWildSideButPaidBaseTicksCannotScheduleIt(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_wind_knight_focused_4",timeoutTicks=160)
    public static void case4_nativeHelpGoalAlertsWildSiblingButCannotRecruitPaidSubclassAgainstPlayer(GameTestHelper h) throws Exception { WindKnightNativeGameTests.nativeHelpGoalAlertsWildSiblingButCannotRecruitPaidSubclassAgainstPlayer(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_wind_knight_focused_4",timeoutTicks=160)
    public static void case4_realDimensionsNbtAndPeacefulRetainPaidIdentityWithoutNativeTimer(GameTestHelper h) throws Exception { WindKnightNativeGameTests.realDimensionsNbtAndPeacefulRetainPaidIdentityWithoutNativeTimer(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_wind_knight_focused_4",timeoutTicks=160)
    public static void case4_wildDeathStillDropsNativeCrystalWhilePaidDeathDropsNothing(GameTestHelper h) throws Exception { WindKnightNativeGameTests.wildDeathStillDropsNativeCrystalWhilePaidDeathDropsNothing(h); }
    @AfterBatch(batch="dreamseekers_wind_knight_focused_5") public static void cleanup5(ServerLevel level) { BoneWingProductionGameTests.cleanup(level); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_wind_knight_focused_5",timeoutTicks=760)
    public static void case5_exactHallWhitelistAndFullPaidQueueProduceOneLightBoneWing(GameTestHelper h) throws Exception { BoneWingProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneLightBoneWing(h); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_wind_knight_focused_5",timeoutTicks=280)
    public static void case5_blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper h) throws Exception { BoneWingProductionGameTests.blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(h); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_wind_knight_focused_5",timeoutTicks=100)
    public static void case5_populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper h) throws Exception { BoneWingProductionGameTests.populationResourcesAndRepeatedCancellationNeverDuplicate(h); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_wind_knight_focused_5",timeoutTicks=100)
    public static void case5_actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper h) throws Exception { BoneWingProductionGameTests.actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(h); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_wind_knight_focused_5",timeoutTicks=120)
    public static void case5_originalPayerRefundsBeforeTickAndMutableCostsCannotChangeReceipts(GameTestHelper h) throws Exception { BoneWingProductionGameTests.originalPayerRefundsBeforeTickAndMutableCostsCannotChangeReceipts(h); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_wind_knight_focused_5",timeoutTicks=140)
    public static void case5_destructionSettlesOriginalPayerButDetachNeverRefundsOrRevives(GameTestHelper h) throws Exception { BoneWingProductionGameTests.destructionSettlesOriginalPayerButDetachNeverRefundsOrRevives(h); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_wind_knight_focused_5",timeoutTicks=160)
    public static void case5_observedOwnerABAAndExternalQueueClearCannotReuseOldPaidActive(GameTestHelper h) throws Exception { BoneWingProductionGameTests.observedOwnerABAAndExternalQueueClearCannotReuseOldPaidActive(h); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_wind_knight_focused_5",timeoutTicks=160)
    public static void case5_futureSchemaAtCompletionPausesNoScoreAndStillRefundsExactPayer(GameTestHelper h) throws Exception { BoneWingProductionGameTests.futureSchemaAtCompletionPausesNoScoreAndStillRefundsExactPayer(h); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_wind_knight_focused_5",timeoutTicks=220)
    public static void case5_actualJoinEventCancellationCannotConsumePaidQueueOrScore(GameTestHelper h) throws Exception { BoneWingProductionGameTests.actualJoinEventCancellationCannotConsumePaidQueueOrScore(h); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_wind_knight_focused_5",timeoutTicks=220)
    public static void case5_lateJoinCancellationRetiresGhostPopulationAndChunkTicket(GameTestHelper h) throws Exception { BoneWingProductionGameTests.lateJoinCancellationRetiresGhostPopulationAndChunkTicket(h); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_wind_knight_focused_5",timeoutTicks=160)
    public static void case5_savedRefundSnapshotUsesOriginalPayerAndImmutablePayment(GameTestHelper h) throws Exception { BoneWingProductionGameTests.savedRefundSnapshotUsesOriginalPayerAndImmutablePayment(h); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_wind_knight_focused_5",timeoutTicks=160)
    public static void case5_joinCallbackCannotReenterCompletionOrStartAnotherPayment(GameTestHelper h) throws Exception { BoneWingProductionGameTests.joinCallbackCannotReenterCompletionOrStartAnotherPayment(h); }
    @AfterBatch(batch="wind_hall_registry") public static void cleanupRegistry(ServerLevel level){ScaffoldBuildingGameTests.cleanup(level); }
    @GameTest(template="dreamseekers_test_arena",batch="wind_hall_registry",timeoutTicks=100)
    public static void hallRegistry(GameTestHelper h){ScaffoldBuildingGameTests.allEightRegisteredFactionFactoriesPaidRotationsAndCancel(h); }
}
