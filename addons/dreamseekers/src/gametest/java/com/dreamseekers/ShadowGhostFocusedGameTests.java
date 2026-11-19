// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
/** New ghost + affected paid Hall lifecycle; not a full-faction aggregate. */
@GameTestHolder("dreamseekers_shadow_ghost_focused")
@PrefixGameTestTemplate(false)
public final class ShadowGhostFocusedGameTests {
    @GameTest(template="ghost_unit_arena",batch="ghost_focus_0",timeoutTicks=740)
    public static void case0_genuinelyFliesOverTwoBlockBarrierWithoutCollision(GameTestHelper h)throws Exception{ShadowGhostFlightGameTests.genuinelyFliesOverTwoBlockBarrierWithoutCollision(h);}
    @GameTest(template="ghost_unit_arena",batch="ghost_focus_0",timeoutTicks=820)
    public static void case0_sealedWallsCannotBeCrossedOrHarvestedThrough(GameTestHelper h)throws Exception{ShadowGhostFlightGameTests.sealedWallsCannotBeCrossedOrHarvestedThrough(h);}
    @GameTest(template="ghost_unit_arena",batch="ghost_focus_0",timeoutTicks=730)
    public static void case0_stopAndReplacementOrdersCancelFlightWithoutWildWandering(GameTestHelper h)throws Exception{ShadowGhostFlightGameTests.stopAndReplacementOrdersCancelFlightWithoutWildWandering(h);}
    @GameTest(template="ghost_unit_arena",batch="ghost_focus_0",timeoutTicks=890)
    public static void case0_lowFlightCeilingDoesNotRatchetAndRetriesWhenOpeningAppears(GameTestHelper h)throws Exception{ShadowGhostFlightGameTests.lowFlightCeilingDoesNotRatchetAndRetriesWhenOpeningAppears(h);}
    @GameTest(template="ghost_unit_arena",batch="ghost_focus_0",timeoutTicks=1120)
    public static void case0_queuedMoveThenHoldThenStopCannotResumeStaleFlight(GameTestHelper h)throws Exception{ShadowGhostFlightGameTests.queuedMoveThenHoldThenStopCannotResumeStaleFlight(h);}
    @GameTest(template="ghost_unit_arena",batch="ghost_focus_0",timeoutTicks=1060)
    public static void case0_sameTickABAndABAKeepFinalFlightDestination(GameTestHelper h)throws Exception{ShadowGhostFlightGameTests.sameTickABAndABAKeepFinalFlightDestination(h);}
    @GameTest(template="ghost_unit_arena",batch="ghost_focus_0",timeoutTicks=920)
    public static void case0_tallBodyCannotFitOneHighSlotButFitsOpenedTwoHighSlot(GameTestHelper h)throws Exception{ShadowGhostFlightGameTests.tallBodyCannotFitOneHighSlotButFitsOpenedTwoHighSlot(h);}
    @GameTest(template="ghost_unit_arena",batch="ghost_focus_0",timeoutTicks=500)
    public static void case0_malformedItemBoatMissingAndRemovedTargetsNeverBecomeFollowOrDamage(GameTestHelper h)throws Exception{ShadowGhostFlightGameTests.malformedItemBoatMissingAndRemovedTargetsNeverBecomeFollowOrDamage(h);}
    @AfterBatch(batch="ghost_focus_0") public static void cleanup0(ServerLevel l){ShadowGhostFlightGameTests.cleanup(l);}
    @GameTest(template="ghost_unit_arena",batch="ghost_focus_1",timeoutTicks=80)
    public static void case1_authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(GameTestHelper h)throws Exception{ShadowGhostCommandAuthorityGameTests.authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(h);}
    @GameTest(template="ghost_unit_arena",batch="ghost_focus_1",timeoutTicks=80)
    public static void case1_ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(GameTestHelper h)throws Exception{ShadowGhostCommandAuthorityGameTests.ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(h);}
    @GameTest(template="ghost_unit_arena",batch="ghost_focus_1",timeoutTicks=80)
    public static void case1_buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(GameTestHelper h)throws Exception{ShadowGhostCommandAuthorityGameTests.buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(h);}
    @AfterBatch(batch="ghost_focus_1") public static void cleanup1(ServerLevel l){ShadowGhostCommandAuthorityGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="ghost_focus_2",timeoutTicks=750)
    public static void case2_exactHallWhitelistAndFullPaidQueueProduceOneShadowGhost(GameTestHelper h)throws Exception{ShadowGhostProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneShadowGhost(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ghost_focus_2",timeoutTicks=280)
    public static void case2_blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper h)throws Exception{ShadowGhostProductionGameTests.blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ghost_focus_2",timeoutTicks=100)
    public static void case2_populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper h)throws Exception{ShadowGhostProductionGameTests.populationResourcesAndRepeatedCancellationNeverDuplicate(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ghost_focus_2",timeoutTicks=100)
    public static void case2_actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper h)throws Exception{ShadowGhostProductionGameTests.actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ghost_focus_2",timeoutTicks=340)
    public static void case2_buildingCombatRejectsFriendlyStaleCrossDimensionAndHoldMovement(GameTestHelper h)throws Exception{ShadowGhostProductionGameTests.buildingCombatRejectsFriendlyStaleCrossDimensionAndHoldMovement(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ghost_focus_2",timeoutTicks=140)
    public static void case2_buildingImpactRechecksWallStunFreezeAndAlliance(GameTestHelper h)throws Exception{ShadowGhostProductionGameTests.buildingImpactRechecksWallStunFreezeAndAlliance(h);}
    @AfterBatch(batch="ghost_focus_2") public static void cleanup2(ServerLevel l){ShadowGhostProductionGameTests.cleanup(l);}
    @GameTest(template="ghost_unit_arena",batch="ghost_focus_3",timeoutTicks=460)
    public static void case3_meleeIsRealAndSameFactionEnemiesAreNotAllies(GameTestHelper h)throws Exception{ShadowGhostSafetyGameTests.meleeIsRealAndSameFactionEnemiesAreNotAllies(h);}
    @GameTest(template="ghost_unit_arena",batch="ghost_focus_3",timeoutTicks=80)
    public static void case3_ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(GameTestHelper h)throws Exception{ShadowGhostSafetyGameTests.ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(h);}
    @GameTest(template="ghost_unit_arena",batch="ghost_focus_3",timeoutTicks=300)
    public static void case3_holdWithEnemyNeverChasesButStillHitsInsideMeleeReach(GameTestHelper h)throws Exception{ShadowGhostSafetyGameTests.holdWithEnemyNeverChasesButStillHitsInsideMeleeReach(h);}
    @GameTest(template="ghost_unit_arena",batch="ghost_focus_3",timeoutTicks=220)
    public static void case3_friendlyFollowMovesAndRevokedAllianceCancelsItsPath(GameTestHelper h)throws Exception{ShadowGhostSafetyGameTests.friendlyFollowMovesAndRevokedAllianceCancelsItsPath(h);}
    @GameTest(template="ghost_unit_arena",batch="ghost_focus_3",timeoutTicks=220)
    public static void case3_revokedFollowAllowsNewHostileAutoAggroWithoutRevivingOldCommand(GameTestHelper h)throws Exception{ShadowGhostSafetyGameTests.revokedFollowAllowsNewHostileAutoAggroWithoutRevivingOldCommand(h);}
    @GameTest(template="ghost_unit_arena",batch="ghost_focus_3",timeoutTicks=460)
    public static void case3_sameEnemyExplicitAttackReleasesHoldAndReallyPursues(GameTestHelper h)throws Exception{ShadowGhostSafetyGameTests.sameEnemyExplicitAttackReleasesHoldAndReallyPursues(h);}
    @GameTest(template="ghost_unit_arena",batch="ghost_focus_3",timeoutTicks=260)
    public static void case3_realFlightMeleeReachesAirborneEnemyWithoutContactDive(GameTestHelper h)throws Exception{ShadowGhostSafetyGameTests.realFlightMeleeReachesAirborneEnemyWithoutContactDive(h);}
    @GameTest(template="ghost_unit_arena",batch="ghost_focus_3",timeoutTicks=180)
    public static void case3_closeEnemyBehindSolidWallCannotBeHitEvenByDirectNativeEntry(GameTestHelper h)throws Exception{ShadowGhostSafetyGameTests.closeEnemyBehindSolidWallCannotBeHitEvenByDirectNativeEntry(h);}
    @AfterBatch(batch="ghost_focus_3") public static void cleanup3(ServerLevel l){ShadowGhostSafetyGameTests.cleanup(l);}
    @GameTest(template="ghost_unit_arena",batch="ghost_focus_4",timeoutTicks=120)
    public static void case4_nativeDiveAndPlayerGoalsAreReplacedWithoutChangingWildIdentity(GameTestHelper h)throws Exception{ShadowGhostNativeGameTests.nativeDiveAndPlayerGoalsAreReplacedWithoutChangingWildIdentity(h);}
    @GameTest(template="ghost_unit_arena",batch="ghost_focus_4",timeoutTicks=160)
    public static void case4_documentedNativeImmunitiesRemainButRealArrowsDamageBoth(GameTestHelper h)throws Exception{ShadowGhostNativeGameTests.documentedNativeImmunitiesRemainButRealArrowsDamageBoth(h);}
    @GameTest(template="ghost_unit_arena",batch="ghost_focus_4",timeoutTicks=460)
    public static void case4_interactionAndRealDeathCannotCreatePaidLootOrExperience(GameTestHelper h)throws Exception{ShadowGhostNativeGameTests.interactionAndRealDeathCannotCreatePaidLootOrExperience(h);}
    @GameTest(template="ghost_unit_arena",batch="ghost_focus_4",timeoutTicks=120)
    public static void case4_portalsForcedRidingAndLoadedNoAiCannotEscapePaidFlightPolicy(GameTestHelper h)throws Exception{ShadowGhostNativeGameTests.portalsForcedRidingAndLoadedNoAiCannotEscapePaidFlightPolicy(h);}
    @AfterBatch(batch="ghost_focus_4") public static void cleanup4(ServerLevel l){ShadowGhostNativeGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="ghost_focus_5",timeoutTicks=760)
    public static void case5_exactHallWhitelistAndFullPaidQueueProduceOneLightBoneWing(GameTestHelper h)throws Exception{BoneWingProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneLightBoneWing(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ghost_focus_5",timeoutTicks=280)
    public static void case5_blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper h)throws Exception{BoneWingProductionGameTests.blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ghost_focus_5",timeoutTicks=100)
    public static void case5_populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper h)throws Exception{BoneWingProductionGameTests.populationResourcesAndRepeatedCancellationNeverDuplicate(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ghost_focus_5",timeoutTicks=100)
    public static void case5_actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper h)throws Exception{BoneWingProductionGameTests.actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ghost_focus_5",timeoutTicks=120)
    public static void case5_originalPayerRefundsBeforeTickAndMutableCostsCannotChangeReceipts(GameTestHelper h)throws Exception{BoneWingProductionGameTests.originalPayerRefundsBeforeTickAndMutableCostsCannotChangeReceipts(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ghost_focus_5",timeoutTicks=140)
    public static void case5_destructionSettlesOriginalPayerButDetachNeverRefundsOrRevives(GameTestHelper h)throws Exception{BoneWingProductionGameTests.destructionSettlesOriginalPayerButDetachNeverRefundsOrRevives(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ghost_focus_5",timeoutTicks=160)
    public static void case5_observedOwnerABAAndExternalQueueClearCannotReuseOldPaidActive(GameTestHelper h)throws Exception{BoneWingProductionGameTests.observedOwnerABAAndExternalQueueClearCannotReuseOldPaidActive(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ghost_focus_5",timeoutTicks=160)
    public static void case5_futureSchemaAtCompletionPausesNoScoreAndStillRefundsExactPayer(GameTestHelper h)throws Exception{BoneWingProductionGameTests.futureSchemaAtCompletionPausesNoScoreAndStillRefundsExactPayer(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ghost_focus_5",timeoutTicks=220)
    public static void case5_actualJoinEventCancellationCannotConsumePaidQueueOrScore(GameTestHelper h)throws Exception{BoneWingProductionGameTests.actualJoinEventCancellationCannotConsumePaidQueueOrScore(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ghost_focus_5",timeoutTicks=220)
    public static void case5_lateJoinCancellationRetiresGhostPopulationAndChunkTicket(GameTestHelper h)throws Exception{BoneWingProductionGameTests.lateJoinCancellationRetiresGhostPopulationAndChunkTicket(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ghost_focus_5",timeoutTicks=160)
    public static void case5_savedRefundSnapshotUsesOriginalPayerAndImmutablePayment(GameTestHelper h)throws Exception{BoneWingProductionGameTests.savedRefundSnapshotUsesOriginalPayerAndImmutablePayment(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ghost_focus_5",timeoutTicks=160)
    public static void case5_joinCallbackCannotReenterCompletionOrStartAnotherPayment(GameTestHelper h)throws Exception{BoneWingProductionGameTests.joinCallbackCannotReenterCompletionOrStartAnotherPayment(h);}
    @AfterBatch(batch="ghost_focus_5") public static void cleanup5(ServerLevel l){BoneWingProductionGameTests.cleanup(l);}
    @GameTest(template="ghost_unit_arena",batch="ghost_peaceful",timeoutTicks=130)
    public static void nativePeaceful(GameTestHelper h){ShadowGhostNativeGameTests.paidPersistenceSurvivesPeacefulWhileOriginalMonsterDespawns(h);}
    @AfterBatch(batch="ghost_peaceful") public static void cleanupPeaceful(ServerLevel l){ShadowGhostNativeGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="ghost_registry",timeoutTicks=100)
    public static void oldEightFactories(GameTestHelper h){ScaffoldBuildingGameTests.allEightRegisteredFactionFactoriesPaidRotationsAndCancel(h);}
    @AfterBatch(batch="ghost_registry") public static void cleanupRegistry(ServerLevel l){ScaffoldBuildingGameTests.cleanup(l);}
}
