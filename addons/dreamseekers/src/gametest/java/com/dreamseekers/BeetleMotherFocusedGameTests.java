// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
/** New mother cases and directly affected Hall/old paid-beetle regressions; not the complete faction suite. */
@GameTestHolder("dreamseekers_mother_focused")
@PrefixGameTestTemplate(false)
public final class BeetleMotherFocusedGameTests {
    @GameTest(template="mother_unit_arena",batch="mother_focus_0",timeoutTicks=500)
    public static void case0_groundPathGoesAroundSolidBarrierWithoutFlightOrClipping(GameTestHelper h)throws Exception{BeetleMotherMovementGameTests.groundPathGoesAroundSolidBarrierWithoutFlightOrClipping(h);}
    @GameTest(template="mother_unit_arena",batch="mother_focus_0",timeoutTicks=690)
    public static void case0_enclosedGroundMotherCannotPhaseAndRetriesOpenedExit(GameTestHelper h)throws Exception{BeetleMotherMovementGameTests.enclosedGroundMotherCannotPhaseAndRetriesOpenedExit(h);}
    @GameTest(template="mother_unit_arena",batch="mother_focus_0",timeoutTicks=900)
    public static void case0_queuedMovesHoldStopAndReplacementNeverResumeStalePath(GameTestHelper h)throws Exception{BeetleMotherMovementGameTests.queuedMovesHoldStopAndReplacementNeverResumeStalePath(h);}
    @GameTest(template="mother_unit_arena",batch="mother_focus_0",timeoutTicks=80)
    public static void case0_malformedNonlivingMissingAndRemovedTargetsCannotCrashNoncombatCommands(GameTestHelper h)throws Exception{BeetleMotherMovementGameTests.malformedNonlivingMissingAndRemovedTargetsCannotCrashNoncombatCommands(h);}
    @GameTest(template="mother_unit_arena",batch="mother_focus_0",timeoutTicks=760)
    public static void case0_nativeWideBodyCannotUseOneBlockGapButUsesThreeBlockOpening(GameTestHelper h)throws Exception{BeetleMotherMovementGameTests.nativeWideBodyCannotUseOneBlockGapButUsesThreeBlockOpening(h);}
    @GameTest(template="mother_unit_arena",batch="mother_focus_0",timeoutTicks=560)
    public static void case0_diagonalTerminalNodeReallySettlesAndQueuedLegCanAdvance(GameTestHelper h)throws Exception{BeetleMotherMovementGameTests.diagonalTerminalNodeReallySettlesAndQueuedLegCanAdvance(h);}
    @GameTest(template="mother_unit_arena",batch="mother_focus_0",timeoutTicks=560)
    public static void case0_reverseDiagonalAlsoMeetsBothArrivalPredicatesBeforeShift(GameTestHelper h)throws Exception{BeetleMotherMovementGameTests.reverseDiagonalAlsoMeetsBothArrivalPredicatesBeforeShift(h);}
    @GameTest(template="mother_unit_arena",batch="mother_focus_0",timeoutTicks=560)
    public static void case0_unopposedAttackMoveUsesSameSafeTerminalPrecisionAndThenShift(GameTestHelper h)throws Exception{BeetleMotherMovementGameTests.unopposedAttackMoveUsesSameSafeTerminalPrecisionAndThenShift(h);}
    @AfterBatch(batch="mother_focus_0") public static void cleanup0(ServerLevel l){BeetleMotherMovementGameTests.cleanup(l);}
    @GameTest(template="mother_unit_arena",batch="mother_focus_1",timeoutTicks=80)
    public static void case1_authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(GameTestHelper h)throws Exception{BeetleMotherCommandAuthorityGameTests.authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(h);}
    @GameTest(template="mother_unit_arena",batch="mother_focus_1",timeoutTicks=80)
    public static void case1_ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(GameTestHelper h)throws Exception{BeetleMotherCommandAuthorityGameTests.ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(h);}
    @GameTest(template="mother_unit_arena",batch="mother_focus_1",timeoutTicks=80)
    public static void case1_buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(GameTestHelper h)throws Exception{BeetleMotherCommandAuthorityGameTests.buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(h);}
    @AfterBatch(batch="mother_focus_1") public static void cleanup1(ServerLevel l){BeetleMotherCommandAuthorityGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="mother_focus_2",timeoutTicks=1150)
    public static void case2_exactHallWhitelistAndFullPaidQueueProduceOnePublicBeetleMother(GameTestHelper h)throws Exception{BeetleMotherProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOnePublicBeetleMother(h);}
    @GameTest(template="dreamseekers_test_arena",batch="mother_focus_2",timeoutTicks=280)
    public static void case2_blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper h)throws Exception{BeetleMotherProductionGameTests.blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(h);}
    @GameTest(template="dreamseekers_test_arena",batch="mother_focus_2",timeoutTicks=100)
    public static void case2_populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper h)throws Exception{BeetleMotherProductionGameTests.populationResourcesAndRepeatedCancellationNeverDuplicate(h);}
    @GameTest(template="dreamseekers_test_arena",batch="mother_focus_2",timeoutTicks=100)
    public static void case2_actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper h)throws Exception{BeetleMotherProductionGameTests.actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(h);}
    @GameTest(template="dreamseekers_test_arena",batch="mother_focus_2",timeoutTicks=340)
    public static void case2_buildingCombatRejectsFriendlyStaleCrossDimensionAndHoldMovement(GameTestHelper h)throws Exception{BeetleMotherProductionGameTests.buildingCombatRejectsFriendlyStaleCrossDimensionAndHoldMovement(h);}
    @AfterBatch(batch="mother_focus_2") public static void cleanup2(ServerLevel l){BeetleMotherProductionGameTests.cleanup(l);}
    @GameTest(template="mother_unit_arena",batch="mother_focus_3",timeoutTicks=100)
    public static void case3_interactionsDamageAndDeathCannotYieldWildRewards(GameTestHelper h)throws Exception{BeetleMotherSafetyGameTests.interactionsDamageAndDeathCannotYieldWildRewards(h);}
    @GameTest(template="mother_unit_arena",batch="mother_focus_3",timeoutTicks=240)
    public static void case3_meleeIsRealAndSameFactionEnemiesAreNotAllies(GameTestHelper h)throws Exception{BeetleMotherSafetyGameTests.meleeIsRealAndSameFactionEnemiesAreNotAllies(h);}
    @GameTest(template="mother_unit_arena",batch="mother_focus_3",timeoutTicks=80)
    public static void case3_ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(GameTestHelper h)throws Exception{BeetleMotherSafetyGameTests.ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(h);}
    @GameTest(template="mother_unit_arena",batch="mother_focus_3",timeoutTicks=300)
    public static void case3_holdWithEnemyNeverChasesButStillHitsInsideMeleeReach(GameTestHelper h)throws Exception{BeetleMotherSafetyGameTests.holdWithEnemyNeverChasesButStillHitsInsideMeleeReach(h);}
    @GameTest(template="mother_unit_arena",batch="mother_focus_3",timeoutTicks=220)
    public static void case3_friendlyFollowMovesAndRevokedAllianceCancelsItsPath(GameTestHelper h)throws Exception{BeetleMotherSafetyGameTests.friendlyFollowMovesAndRevokedAllianceCancelsItsPath(h);}
    @GameTest(template="mother_unit_arena",batch="mother_focus_3",timeoutTicks=220)
    public static void case3_revokedFollowAllowsNewHostileAutoAggroWithoutRevivingOldCommand(GameTestHelper h)throws Exception{BeetleMotherSafetyGameTests.revokedFollowAllowsNewHostileAutoAggroWithoutRevivingOldCommand(h);}
    @GameTest(template="mother_unit_arena",batch="mother_focus_3",timeoutTicks=240)
    public static void case3_sameEnemyExplicitAttackReleasesHoldAndReallyPursues(GameTestHelper h)throws Exception{BeetleMotherSafetyGameTests.sameEnemyExplicitAttackReleasesHoldAndReallyPursues(h);}
    @AfterBatch(batch="mother_focus_3") public static void cleanup3(ServerLevel l){BeetleMotherSafetyGameTests.cleanup(l);}
    @GameTest(template="mother_native_arena",batch="mother_focus_4",timeoutTicks=320)
    public static void case4_realWildSummonAndBuffExcludePaidBeetlesWhilePaidMotherNeverSchedules(GameTestHelper h)throws Exception{BeetleMotherNativeGameTests.realWildSummonAndBuffExcludePaidBeetlesWhilePaidMotherNeverSchedules(h);}
    @GameTest(template="mother_native_arena",batch="mother_focus_4",timeoutTicks=160)
    public static void case4_nativeHelpGoalRetainsWildPositiveButCannotRecruitPaidMother(GameTestHelper h)throws Exception{BeetleMotherNativeGameTests.nativeHelpGoalRetainsWildPositiveButCannotRecruitPaidMother(h);}
    @GameTest(template="mother_native_arena",batch="mother_focus_4",timeoutTicks=180)
    public static void case4_ordinaryNbtBodyAndPeacefulKeepPaidNonHeroIdentity(GameTestHelper h)throws Exception{BeetleMotherNativeGameTests.ordinaryNbtBodyAndPeacefulKeepPaidNonHeroIdentity(h);}
    @GameTest(template="mother_native_arena",batch="mother_focus_4",timeoutTicks=100)
    public static void case4_nativeBossbarCallbacksAreInstanceIsolated(GameTestHelper h)throws Exception{BeetleMotherNativeGameTests.nativeBossbarCallbacksAreInstanceIsolated(h);}
    @GameTest(template="mother_native_arena",batch="mother_focus_4",timeoutTicks=160)
    public static void case4_actualWildDeathKeepsKeysShellsAndVocalcordsButPaidDeathGivesNothing(GameTestHelper h)throws Exception{BeetleMotherNativeGameTests.actualWildDeathKeepsKeysShellsAndVocalcordsButPaidDeathGivesNothing(h);}
    @GameTest(template="mother_native_arena",batch="mother_focus_4",timeoutTicks=130)
    public static void case4_portalCallbacksAndForcedVehiclesCannotBypassInstanceRestriction(GameTestHelper h)throws Exception{BeetleMotherNativeGameTests.portalCallbacksAndForcedVehiclesCannotBypassInstanceRestriction(h);}
    @AfterBatch(batch="mother_focus_4") public static void cleanup4(ServerLevel l){BeetleMotherNativeGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="mother_focus_5",timeoutTicks=760)
    public static void case5_exactHallWhitelistAndFullPaidQueueProduceOneLightBoneWing(GameTestHelper h)throws Exception{BoneWingProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneLightBoneWing(h);}
    @GameTest(template="dreamseekers_test_arena",batch="mother_focus_5",timeoutTicks=280)
    public static void case5_blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper h)throws Exception{BoneWingProductionGameTests.blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(h);}
    @GameTest(template="dreamseekers_test_arena",batch="mother_focus_5",timeoutTicks=100)
    public static void case5_populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper h)throws Exception{BoneWingProductionGameTests.populationResourcesAndRepeatedCancellationNeverDuplicate(h);}
    @GameTest(template="dreamseekers_test_arena",batch="mother_focus_5",timeoutTicks=100)
    public static void case5_actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper h)throws Exception{BoneWingProductionGameTests.actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(h);}
    @GameTest(template="dreamseekers_test_arena",batch="mother_focus_5",timeoutTicks=120)
    public static void case5_originalPayerRefundsBeforeTickAndMutableCostsCannotChangeReceipts(GameTestHelper h)throws Exception{BoneWingProductionGameTests.originalPayerRefundsBeforeTickAndMutableCostsCannotChangeReceipts(h);}
    @GameTest(template="dreamseekers_test_arena",batch="mother_focus_5",timeoutTicks=140)
    public static void case5_destructionSettlesOriginalPayerButDetachNeverRefundsOrRevives(GameTestHelper h)throws Exception{BoneWingProductionGameTests.destructionSettlesOriginalPayerButDetachNeverRefundsOrRevives(h);}
    @GameTest(template="dreamseekers_test_arena",batch="mother_focus_5",timeoutTicks=160)
    public static void case5_observedOwnerABAAndExternalQueueClearCannotReuseOldPaidActive(GameTestHelper h)throws Exception{BoneWingProductionGameTests.observedOwnerABAAndExternalQueueClearCannotReuseOldPaidActive(h);}
    @GameTest(template="dreamseekers_test_arena",batch="mother_focus_5",timeoutTicks=160)
    public static void case5_futureSchemaAtCompletionPausesNoScoreAndStillRefundsExactPayer(GameTestHelper h)throws Exception{BoneWingProductionGameTests.futureSchemaAtCompletionPausesNoScoreAndStillRefundsExactPayer(h);}
    @GameTest(template="dreamseekers_test_arena",batch="mother_focus_5",timeoutTicks=220)
    public static void case5_actualJoinEventCancellationCannotConsumePaidQueueOrScore(GameTestHelper h)throws Exception{BoneWingProductionGameTests.actualJoinEventCancellationCannotConsumePaidQueueOrScore(h);}
    @GameTest(template="dreamseekers_test_arena",batch="mother_focus_5",timeoutTicks=220)
    public static void case5_lateJoinCancellationRetiresGhostPopulationAndChunkTicket(GameTestHelper h)throws Exception{BoneWingProductionGameTests.lateJoinCancellationRetiresGhostPopulationAndChunkTicket(h);}
    @GameTest(template="dreamseekers_test_arena",batch="mother_focus_5",timeoutTicks=160)
    public static void case5_savedRefundSnapshotUsesOriginalPayerAndImmutablePayment(GameTestHelper h)throws Exception{BoneWingProductionGameTests.savedRefundSnapshotUsesOriginalPayerAndImmutablePayment(h);}
    @GameTest(template="dreamseekers_test_arena",batch="mother_focus_5",timeoutTicks=160)
    public static void case5_joinCallbackCannotReenterCompletionOrStartAnotherPayment(GameTestHelper h)throws Exception{BoneWingProductionGameTests.joinCallbackCannotReenterCompletionOrStartAnotherPayment(h);}
    @AfterBatch(batch="mother_focus_5") public static void cleanup5(ServerLevel l){BoneWingProductionGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="mother_focus_registry",timeoutTicks=100)
    public static void oldHallFactories(GameTestHelper h){ScaffoldBuildingGameTests.allEightRegisteredFactionFactoriesPaidRotationsAndCancel(h);}
    @AfterBatch(batch="mother_focus_registry") public static void cleanupRegistry(ServerLevel l){ScaffoldBuildingGameTests.cleanup(l);}
    @GameTest(template="mother_unit_arena",batch="mother_focus_legacy",timeoutTicks=100)
    public static void oldBeetleIsolation(GameTestHelper h){UnitIsolationGameTests.beetleCannotBeTamedBredOrFarmedForNativeCarapace(h);}
    @AfterBatch(batch="mother_focus_legacy") public static void cleanupLegacy(ServerLevel l){UnitIsolationGameTests.cleanup(l);}
}
