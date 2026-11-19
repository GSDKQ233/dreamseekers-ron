// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
@GameTestHolder("dreamseekers_stone_focused")
@PrefixGameTestTemplate(false)
public final class StoneThrowerFocusedGameTests {
    @GameTest(template="unit_isolation_arena",batch="stone_focus_0",timeoutTicks=500)
    public static void case0_groundPathGoesAroundSolidBarrierWithoutFlightOrClipping(GameTestHelper h) throws Exception {StoneThrowerMovementGameTests.groundPathGoesAroundSolidBarrierWithoutFlightOrClipping(h);}
    @GameTest(template="unit_isolation_arena",batch="stone_focus_0",timeoutTicks=690)
    public static void case0_enclosedGroundThrowerCannotPhaseAndRetriesOpenedExit(GameTestHelper h) throws Exception {StoneThrowerMovementGameTests.enclosedGroundThrowerCannotPhaseAndRetriesOpenedExit(h);}
    @GameTest(template="unit_isolation_arena",batch="stone_focus_0",timeoutTicks=780)
    public static void case0_queuedMovesHoldStopAndReplacementNeverResumeStalePath(GameTestHelper h) throws Exception {StoneThrowerMovementGameTests.queuedMovesHoldStopAndReplacementNeverResumeStalePath(h);}
    @GameTest(template="unit_isolation_arena",batch="stone_focus_0",timeoutTicks=80)
    public static void case0_malformedNonlivingMissingAndRemovedTargetsCannotCrashNoncombatCommands(GameTestHelper h) throws Exception {StoneThrowerMovementGameTests.malformedNonlivingMissingAndRemovedTargetsCannotCrashNoncombatCommands(h);}
    @AfterBatch(batch="stone_focus_0") public static void cleanup0(ServerLevel l){StoneThrowerMovementGameTests.cleanup(l);}
    @GameTest(template="unit_isolation_arena",batch="stone_focus_1",timeoutTicks=80)
    public static void case1_authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(GameTestHelper h) throws Exception {StoneThrowerCommandAuthorityGameTests.authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(h);}
    @GameTest(template="unit_isolation_arena",batch="stone_focus_1",timeoutTicks=80)
    public static void case1_ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(GameTestHelper h) throws Exception {StoneThrowerCommandAuthorityGameTests.ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(h);}
    @GameTest(template="unit_isolation_arena",batch="stone_focus_1",timeoutTicks=80)
    public static void case1_buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(GameTestHelper h) throws Exception {StoneThrowerCommandAuthorityGameTests.buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(h);}
    @AfterBatch(batch="stone_focus_1") public static void cleanup1(ServerLevel l){StoneThrowerCommandAuthorityGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="stone_focus_2",timeoutTicks=850)
    public static void case2_exactHallWhitelistAndFullPaidQueueProduceOnePublicStoneThrower(GameTestHelper h) throws Exception {StoneThrowerProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOnePublicStoneThrower(h);}
    @GameTest(template="dreamseekers_test_arena",batch="stone_focus_2",timeoutTicks=280)
    public static void case2_blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper h) throws Exception {StoneThrowerProductionGameTests.blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(h);}
    @GameTest(template="dreamseekers_test_arena",batch="stone_focus_2",timeoutTicks=100)
    public static void case2_populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper h) throws Exception {StoneThrowerProductionGameTests.populationResourcesAndRepeatedCancellationNeverDuplicate(h);}
    @GameTest(template="dreamseekers_test_arena",batch="stone_focus_2",timeoutTicks=100)
    public static void case2_actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper h) throws Exception {StoneThrowerProductionGameTests.actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(h);}
    @AfterBatch(batch="stone_focus_2") public static void cleanup2(ServerLevel l){StoneThrowerProductionGameTests.cleanup(l);}
    @GameTest(template="unit_isolation_arena",batch="stone_focus_3",timeoutTicks=520)
    public static void case3_realRangedGoalFiresPhysicalBoltAtAirEnemyWithoutFriendlyCollateral(GameTestHelper h) throws Exception {StoneThrowerCombatGameTests.realRangedGoalFiresPhysicalBoltAtAirEnemyWithoutFriendlyCollateral(h);}
    @GameTest(template="unit_isolation_arena",batch="stone_focus_3",timeoutTicks=280)
    public static void case3_allianceAndOwnershipChangesInvalidateInFlightButStopDoesNot(GameTestHelper h) throws Exception {StoneThrowerCombatGameTests.allianceAndOwnershipChangesInvalidateInFlightButStopDoesNot(h);}
    @GameTest(template="unit_isolation_arena",batch="stone_focus_3",timeoutTicks=260)
    public static void case3_freshLineOfSightRangeAndDisableEffectsGateEveryRelease(GameTestHelper h) throws Exception {StoneThrowerCombatGameTests.freshLineOfSightRangeAndDisableEffectsGateEveryRelease(h);}
    @GameTest(template="unit_isolation_arena",batch="stone_focus_3",timeoutTicks=180)
    public static void case3_spentInvalidAndReloadedProjectilesCannotBeRearmedOrDamageTwice(GameTestHelper h) throws Exception {StoneThrowerCombatGameTests.spentInvalidAndReloadedProjectilesCannotBeRearmedOrDamageTwice(h);}
    @GameTest(template="unit_isolation_arena",batch="stone_focus_3",timeoutTicks=280)
    public static void case3_lastWindupStunCannotLeakNewProjectileAndLaterReleaseRecovers(GameTestHelper h) throws Exception {StoneThrowerCombatGameTests.lastWindupStunCannotLeakNewProjectileAndLaterReleaseRecovers(h);}
    @GameTest(template="unit_isolation_arena",batch="stone_focus_3",timeoutTicks=280)
    public static void case3_stopAndSameTickRetargetResetRealRangedWindupWithoutCooldownReset(GameTestHelper h) throws Exception {StoneThrowerCombatGameTests.stopAndSameTickRetargetResetRealRangedWindupWithoutCooldownReset(h);}
    @AfterBatch(batch="stone_focus_3") public static void cleanup3(ServerLevel l){StoneThrowerCombatGameTests.cleanup(l);}
    @GameTest(template="stone_native_arena",batch="stone_focus_4",timeoutTicks=180)
    public static void case4_actualArrowHitsOnlyNewCombatWhileLegacyWorkerAndWildRetainImmunity(GameTestHelper h) throws Exception {StoneThrowerNativeGameTests.actualArrowHitsOnlyNewCombatWhileLegacyWorkerAndWildRetainImmunity(h);}
    @GameTest(template="stone_native_arena",batch="stone_focus_4",timeoutTicks=200)
    public static void case4_actualWildDeathGrowsOnlyWildAndSchedulesOriginalTerrainWhilePaidSourcesCannot(GameTestHelper h) throws Exception {StoneThrowerNativeGameTests.actualWildDeathGrowsOnlyWildAndSchedulesOriginalTerrainWhilePaidSourcesCannot(h);}
    @GameTest(template="stone_native_arena",batch="stone_focus_4",timeoutTicks=100)
    public static void case4_loadedScalePoisonCannotResizeEitherPaidIdentityAndNativeScaleStillWorks(GameTestHelper h) throws Exception {StoneThrowerNativeGameTests.loadedScalePoisonCannotResizeEitherPaidIdentityAndNativeScaleStillWorks(h);}
    @GameTest(template="stone_native_arena",batch="stone_focus_4",timeoutTicks=140)
    public static void case4_noNativeInteractionOrUnapprovedSiegeAndRealSenderReservedRequestFailsClosed(GameTestHelper h) throws Exception {StoneThrowerNativeGameTests.noNativeInteractionOrUnapprovedSiegeAndRealSenderReservedRequestFailsClosed(h);}
    @GameTest(template="stone_native_arena",batch="stone_focus_4",timeoutTicks=320)
    public static void case4_sameGroundEnemyExplicitAttackReleasesHoldThenTravelsAndDamagesWithoutMelee(GameTestHelper h) throws Exception {StoneThrowerNativeGameTests.sameGroundEnemyExplicitAttackReleasesHoldThenTravelsAndDamagesWithoutMelee(h);}
    @GameTest(template="stone_native_arena",batch="stone_focus_4",timeoutTicks=220)
    public static void case4_friendlyFollowMovesAndRevocationCancelsBeforeDistantTargetCanResume(GameTestHelper h) throws Exception {StoneThrowerNativeGameTests.friendlyFollowMovesAndRevocationCancelsBeforeDistantTargetCanResume(h);}
    @GameTest(template="stone_native_arena",batch="stone_focus_peaceful",timeoutTicks=160)
    public static void case4_peacefulKeepsPaidCombatWhileActualWildMonsterDespawns(GameTestHelper h) throws Exception {StoneThrowerNativeGameTests.peacefulKeepsPaidCombatWhileActualWildMonsterDespawns(h);}
    @GameTest(template="stone_native_arena",batch="stone_focus_4",timeoutTicks=180)
    public static void case4_gatewayIsolation(GameTestHelper h){StoneThrowerNativeGameTests.actualGatewayTeleportsVanillaButNotPaidStoneOrItsProjectile(h);}
    @AfterBatch(batch="stone_focus_4") public static void cleanup4(ServerLevel l){StoneThrowerNativeGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="stone_focus_5",timeoutTicks=760)
    public static void case5_exactHallWhitelistAndFullPaidQueueProduceOneLightBoneWing(GameTestHelper h) throws Exception {BoneWingProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneLightBoneWing(h);}
    @GameTest(template="dreamseekers_test_arena",batch="stone_focus_5",timeoutTicks=280)
    public static void case5_blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper h) throws Exception {BoneWingProductionGameTests.blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(h);}
    @GameTest(template="dreamseekers_test_arena",batch="stone_focus_5",timeoutTicks=100)
    public static void case5_populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper h) throws Exception {BoneWingProductionGameTests.populationResourcesAndRepeatedCancellationNeverDuplicate(h);}
    @GameTest(template="dreamseekers_test_arena",batch="stone_focus_5",timeoutTicks=100)
    public static void case5_actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper h) throws Exception {BoneWingProductionGameTests.actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(h);}
    @GameTest(template="dreamseekers_test_arena",batch="stone_focus_5",timeoutTicks=120)
    public static void case5_originalPayerRefundsBeforeTickAndMutableCostsCannotChangeReceipts(GameTestHelper h) throws Exception {BoneWingProductionGameTests.originalPayerRefundsBeforeTickAndMutableCostsCannotChangeReceipts(h);}
    @GameTest(template="dreamseekers_test_arena",batch="stone_focus_5",timeoutTicks=140)
    public static void case5_destructionSettlesOriginalPayerButDetachNeverRefundsOrRevives(GameTestHelper h) throws Exception {BoneWingProductionGameTests.destructionSettlesOriginalPayerButDetachNeverRefundsOrRevives(h);}
    @GameTest(template="dreamseekers_test_arena",batch="stone_focus_5",timeoutTicks=160)
    public static void case5_observedOwnerABAAndExternalQueueClearCannotReuseOldPaidActive(GameTestHelper h) throws Exception {BoneWingProductionGameTests.observedOwnerABAAndExternalQueueClearCannotReuseOldPaidActive(h);}
    @GameTest(template="dreamseekers_test_arena",batch="stone_focus_5",timeoutTicks=160)
    public static void case5_futureSchemaAtCompletionPausesNoScoreAndStillRefundsExactPayer(GameTestHelper h) throws Exception {BoneWingProductionGameTests.futureSchemaAtCompletionPausesNoScoreAndStillRefundsExactPayer(h);}
    @GameTest(template="dreamseekers_test_arena",batch="stone_focus_5",timeoutTicks=220)
    public static void case5_actualJoinEventCancellationCannotConsumePaidQueueOrScore(GameTestHelper h) throws Exception {BoneWingProductionGameTests.actualJoinEventCancellationCannotConsumePaidQueueOrScore(h);}
    @GameTest(template="dreamseekers_test_arena",batch="stone_focus_5",timeoutTicks=220)
    public static void case5_lateJoinCancellationRetiresGhostPopulationAndChunkTicket(GameTestHelper h) throws Exception {BoneWingProductionGameTests.lateJoinCancellationRetiresGhostPopulationAndChunkTicket(h);}
    @GameTest(template="dreamseekers_test_arena",batch="stone_focus_5",timeoutTicks=160)
    public static void case5_savedRefundSnapshotUsesOriginalPayerAndImmutablePayment(GameTestHelper h) throws Exception {BoneWingProductionGameTests.savedRefundSnapshotUsesOriginalPayerAndImmutablePayment(h);}
    @GameTest(template="dreamseekers_test_arena",batch="stone_focus_5",timeoutTicks=160)
    public static void case5_joinCallbackCannotReenterCompletionOrStartAnotherPayment(GameTestHelper h) throws Exception {BoneWingProductionGameTests.joinCallbackCannotReenterCompletionOrStartAnotherPayment(h);}
    @AfterBatch(batch="stone_focus_5") public static void cleanup5(ServerLevel l){BoneWingProductionGameTests.cleanup(l);}
    @AfterBatch(batch="stone_focus_peaceful") public static void cleanupPeaceful(ServerLevel l){StoneThrowerNativeGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="stone_focus_registry",timeoutTicks=100)
    public static void oldHallFactories(GameTestHelper h){ScaffoldBuildingGameTests.allEightRegisteredFactionFactoriesPaidRotationsAndCancel(h);}
    @AfterBatch(batch="stone_focus_registry") public static void cleanupRegistry(ServerLevel l){ScaffoldBuildingGameTests.cleanup(l);}
    @GameTest(template="unit_isolation_arena",batch="stone_focus_legacy",timeoutTicks=100)
    public static void oldWorkerDeathRegression(GameTestHelper h){UnitIsolationGameTests.stoneWorkerDeathDoesNotGrowSpiritsOrResurrectTerrain(h);}
    @AfterBatch(batch="stone_focus_legacy") public static void cleanupLegacy(ServerLevel l){UnitIsolationGameTests.cleanup(l);}
}
