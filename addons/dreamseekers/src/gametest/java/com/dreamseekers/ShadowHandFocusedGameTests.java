// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
@GameTestHolder("dreamseekers_shadow_hand_focused") @PrefixGameTestTemplate(false)
public final class ShadowHandFocusedGameTests {
    @GameTest(template="hand_unit_arena",batch="hand_flight",timeoutTicks=740)
    public static void flight_genuinelyFliesOverTwoBlockBarrierWithoutCollision(GameTestHelper h)throws Exception{ShadowHandFlightGameTests.genuinelyFliesOverTwoBlockBarrierWithoutCollision(h);}
    @GameTest(template="hand_unit_arena",batch="hand_flight",timeoutTicks=820)
    public static void flight_sealedWallsCannotBeCrossedOrHarvestedThrough(GameTestHelper h)throws Exception{ShadowHandFlightGameTests.sealedWallsCannotBeCrossedOrHarvestedThrough(h);}
    @GameTest(template="hand_unit_arena",batch="hand_flight",timeoutTicks=730)
    public static void flight_stopAndReplacementOrdersCancelFlightWithoutWildWandering(GameTestHelper h)throws Exception{ShadowHandFlightGameTests.stopAndReplacementOrdersCancelFlightWithoutWildWandering(h);}
    @GameTest(template="hand_unit_arena",batch="hand_flight",timeoutTicks=890)
    public static void flight_lowFlightCeilingDoesNotRatchetAndRetriesWhenOpeningAppears(GameTestHelper h)throws Exception{ShadowHandFlightGameTests.lowFlightCeilingDoesNotRatchetAndRetriesWhenOpeningAppears(h);}
    @GameTest(template="hand_unit_arena",batch="hand_flight",timeoutTicks=1120)
    public static void flight_queuedMoveThenHoldThenStopCannotResumeStaleFlight(GameTestHelper h)throws Exception{ShadowHandFlightGameTests.queuedMoveThenHoldThenStopCannotResumeStaleFlight(h);}
    @GameTest(template="hand_unit_arena",batch="hand_flight",timeoutTicks=1060)
    public static void flight_sameTickABAndABAKeepFinalFlightDestination(GameTestHelper h)throws Exception{ShadowHandFlightGameTests.sameTickABAndABAKeepFinalFlightDestination(h);}
    @GameTest(template="hand_unit_arena",batch="hand_flight",timeoutTicks=920)
    public static void flight_tallBodyCannotFitOneHighSlotButFitsOpenedTwoHighSlot(GameTestHelper h)throws Exception{ShadowHandFlightGameTests.tallBodyCannotFitOneHighSlotButFitsOpenedTwoHighSlot(h);}
    @GameTest(template="hand_unit_arena",batch="hand_flight",timeoutTicks=500)
    public static void flight_malformedItemBoatMissingAndRemovedTargetsNeverBecomeFollowOrDamage(GameTestHelper h)throws Exception{ShadowHandFlightGameTests.malformedItemBoatMissingAndRemovedTargetsNeverBecomeFollowOrDamage(h);}
    @AfterBatch(batch="hand_flight") public static void cleanup_flight(ServerLevel l){ShadowHandFlightGameTests.cleanup(l);}
    @GameTest(template="hand_unit_arena",batch="hand_commandauthority",timeoutTicks=80)
    public static void commandauthority_authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(GameTestHelper h)throws Exception{ShadowHandCommandAuthorityGameTests.authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(h);}
    @GameTest(template="hand_unit_arena",batch="hand_commandauthority",timeoutTicks=80)
    public static void commandauthority_ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(GameTestHelper h)throws Exception{ShadowHandCommandAuthorityGameTests.ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(h);}
    @GameTest(template="hand_unit_arena",batch="hand_commandauthority",timeoutTicks=80)
    public static void commandauthority_buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(GameTestHelper h)throws Exception{ShadowHandCommandAuthorityGameTests.buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(h);}
    @AfterBatch(batch="hand_commandauthority") public static void cleanup_commandauthority(ServerLevel l){ShadowHandCommandAuthorityGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="hand_production",timeoutTicks=850)
    public static void production_exactHallWhitelistAndFullPaidQueueProduceOneShadowHand(GameTestHelper h)throws Exception{ShadowHandProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneShadowHand(h);}
    @GameTest(template="dreamseekers_test_arena",batch="hand_production",timeoutTicks=280)
    public static void production_blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper h)throws Exception{ShadowHandProductionGameTests.blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(h);}
    @GameTest(template="dreamseekers_test_arena",batch="hand_production",timeoutTicks=100)
    public static void production_populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper h)throws Exception{ShadowHandProductionGameTests.populationResourcesAndRepeatedCancellationNeverDuplicate(h);}
    @GameTest(template="dreamseekers_test_arena",batch="hand_production",timeoutTicks=100)
    public static void production_actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper h)throws Exception{ShadowHandProductionGameTests.actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(h);}
    @GameTest(template="dreamseekers_test_arena",batch="hand_production",timeoutTicks=340)
    public static void production_buildingCombatRejectsFriendlyStaleCrossDimensionAndHoldMovement(GameTestHelper h)throws Exception{ShadowHandProductionGameTests.buildingCombatRejectsFriendlyStaleCrossDimensionAndHoldMovement(h);}
    @GameTest(template="dreamseekers_test_arena",batch="hand_production",timeoutTicks=140)
    public static void production_buildingImpactRechecksWallStunFreezeAndAlliance(GameTestHelper h)throws Exception{ShadowHandProductionGameTests.buildingImpactRechecksWallStunFreezeAndAlliance(h);}
    @AfterBatch(batch="hand_production") public static void cleanup_production(ServerLevel l){ShadowHandProductionGameTests.cleanup(l);}
    @GameTest(template="hand_unit_arena",batch="hand_safety",timeoutTicks=460)
    public static void safety_meleeIsRealAndSameFactionEnemiesAreNotAllies(GameTestHelper h)throws Exception{ShadowHandSafetyGameTests.meleeIsRealAndSameFactionEnemiesAreNotAllies(h);}
    @GameTest(template="hand_unit_arena",batch="hand_safety",timeoutTicks=80)
    public static void safety_ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(GameTestHelper h)throws Exception{ShadowHandSafetyGameTests.ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(h);}
    @GameTest(template="hand_unit_arena",batch="hand_safety",timeoutTicks=300)
    public static void safety_holdWithEnemyNeverChasesButStillHitsInsideMeleeReach(GameTestHelper h)throws Exception{ShadowHandSafetyGameTests.holdWithEnemyNeverChasesButStillHitsInsideMeleeReach(h);}
    @GameTest(template="hand_unit_arena",batch="hand_safety",timeoutTicks=220)
    public static void safety_friendlyFollowMovesAndRevokedAllianceCancelsItsPath(GameTestHelper h)throws Exception{ShadowHandSafetyGameTests.friendlyFollowMovesAndRevokedAllianceCancelsItsPath(h);}
    @GameTest(template="hand_unit_arena",batch="hand_safety",timeoutTicks=220)
    public static void safety_revokedFollowAllowsNewHostileAutoAggroWithoutRevivingOldCommand(GameTestHelper h)throws Exception{ShadowHandSafetyGameTests.revokedFollowAllowsNewHostileAutoAggroWithoutRevivingOldCommand(h);}
    @GameTest(template="hand_unit_arena",batch="hand_safety",timeoutTicks=460)
    public static void safety_sameEnemyExplicitAttackReleasesHoldAndReallyPursues(GameTestHelper h)throws Exception{ShadowHandSafetyGameTests.sameEnemyExplicitAttackReleasesHoldAndReallyPursues(h);}
    @GameTest(template="hand_unit_arena",batch="hand_safety",timeoutTicks=260)
    public static void safety_realFlightMeleeReachesAirborneEnemyWithoutContactDive(GameTestHelper h)throws Exception{ShadowHandSafetyGameTests.realFlightMeleeReachesAirborneEnemyWithoutContactDive(h);}
    @GameTest(template="hand_unit_arena",batch="hand_safety",timeoutTicks=180)
    public static void safety_closeEnemyBehindSolidWallCannotBeHitEvenByDirectNativeEntry(GameTestHelper h)throws Exception{ShadowHandSafetyGameTests.closeEnemyBehindSolidWallCannotBeHitEvenByDirectNativeEntry(h);}
    @AfterBatch(batch="hand_safety") public static void cleanup_safety(ServerLevel l){ShadowHandSafetyGameTests.cleanup(l);}
    @GameTest(template="hand_unit_arena",batch="hand_native",timeoutTicks=120)
    public static void native_nativeDiveAndPlayerGoalsAreReplacedWithoutChangingWildIdentity(GameTestHelper h)throws Exception{ShadowHandNativeGameTests.nativeDiveAndPlayerGoalsAreReplacedWithoutChangingWildIdentity(h);}
    @GameTest(template="hand_unit_arena",batch="hand_native",timeoutTicks=160)
    public static void native_documentedNativeImmunitiesRemainButRealArrowsDamageBoth(GameTestHelper h)throws Exception{ShadowHandNativeGameTests.documentedNativeImmunitiesRemainButRealArrowsDamageBoth(h);}
    @GameTest(template="hand_unit_arena",batch="hand_native",timeoutTicks=120)
    public static void native_interactionAndRealDeathCannotCreatePaidLootOrExperience(GameTestHelper h)throws Exception{ShadowHandNativeGameTests.interactionAndRealDeathCannotCreatePaidLootOrExperience(h);}
    @GameTest(template="hand_unit_arena",batch="hand_native_peaceful",timeoutTicks=130)
    public static void native_paidPersistenceSurvivesPeacefulWhileOriginalMonsterDespawns(GameTestHelper h)throws Exception{ShadowHandNativeGameTests.paidPersistenceSurvivesPeacefulWhileOriginalMonsterDespawns(h);}
    @GameTest(template="hand_unit_arena",batch="hand_native",timeoutTicks=120)
    public static void native_portalsForcedRidingAndLoadedNoAiCannotEscapePaidFlightPolicy(GameTestHelper h)throws Exception{ShadowHandNativeGameTests.portalsForcedRidingAndLoadedNoAiCannotEscapePaidFlightPolicy(h);}
    @GameTest(template="hand_unit_arena",batch="hand_native",timeoutTicks=150)
    public static void native_hurtBeforeDamageAcceptanceCannotInjectNativeDashOrAnimation(GameTestHelper h)throws Exception{ShadowHandNativeGameTests.hurtBeforeDamageAcceptanceCannotInjectNativeDashOrAnimation(h);}
    @GameTest(template="hand_unit_arena",batch="hand_native",timeoutTicks=100)
    public static void native_contactSanIsPaidScopedAndOrdinaryMagicRemainsEffective(GameTestHelper h)throws Exception{ShadowHandNativeGameTests.contactSanIsPaidScopedAndOrdinaryMagicRemainsEffective(h);}
    @AfterBatch(batch="hand_native") public static void cleanup_native(ServerLevel l){ShadowHandNativeGameTests.cleanup(l);}
    @AfterBatch(batch="hand_native_peaceful") public static void cleanup_peaceful(ServerLevel l){ShadowHandNativeGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="hand_oldhall",timeoutTicks=760)
    public static void oldhall_exactHallWhitelistAndFullPaidQueueProduceOneLightBoneWing(GameTestHelper h)throws Exception{BoneWingProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneLightBoneWing(h);}
    @GameTest(template="dreamseekers_test_arena",batch="hand_oldhall",timeoutTicks=280)
    public static void oldhall_blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper h)throws Exception{BoneWingProductionGameTests.blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(h);}
    @GameTest(template="dreamseekers_test_arena",batch="hand_oldhall",timeoutTicks=100)
    public static void oldhall_populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper h)throws Exception{BoneWingProductionGameTests.populationResourcesAndRepeatedCancellationNeverDuplicate(h);}
    @GameTest(template="dreamseekers_test_arena",batch="hand_oldhall",timeoutTicks=100)
    public static void oldhall_actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper h)throws Exception{BoneWingProductionGameTests.actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(h);}
    @GameTest(template="dreamseekers_test_arena",batch="hand_oldhall",timeoutTicks=120)
    public static void oldhall_originalPayerRefundsBeforeTickAndMutableCostsCannotChangeReceipts(GameTestHelper h)throws Exception{BoneWingProductionGameTests.originalPayerRefundsBeforeTickAndMutableCostsCannotChangeReceipts(h);}
    @GameTest(template="dreamseekers_test_arena",batch="hand_oldhall",timeoutTicks=140)
    public static void oldhall_destructionSettlesOriginalPayerButDetachNeverRefundsOrRevives(GameTestHelper h)throws Exception{BoneWingProductionGameTests.destructionSettlesOriginalPayerButDetachNeverRefundsOrRevives(h);}
    @GameTest(template="dreamseekers_test_arena",batch="hand_oldhall",timeoutTicks=160)
    public static void oldhall_observedOwnerABAAndExternalQueueClearCannotReuseOldPaidActive(GameTestHelper h)throws Exception{BoneWingProductionGameTests.observedOwnerABAAndExternalQueueClearCannotReuseOldPaidActive(h);}
    @GameTest(template="dreamseekers_test_arena",batch="hand_oldhall",timeoutTicks=160)
    public static void oldhall_futureSchemaAtCompletionPausesNoScoreAndStillRefundsExactPayer(GameTestHelper h)throws Exception{BoneWingProductionGameTests.futureSchemaAtCompletionPausesNoScoreAndStillRefundsExactPayer(h);}
    @GameTest(template="dreamseekers_test_arena",batch="hand_oldhall",timeoutTicks=220)
    public static void oldhall_actualJoinEventCancellationCannotConsumePaidQueueOrScore(GameTestHelper h)throws Exception{BoneWingProductionGameTests.actualJoinEventCancellationCannotConsumePaidQueueOrScore(h);}
    @GameTest(template="dreamseekers_test_arena",batch="hand_oldhall",timeoutTicks=220)
    public static void oldhall_lateJoinCancellationRetiresGhostPopulationAndChunkTicket(GameTestHelper h)throws Exception{BoneWingProductionGameTests.lateJoinCancellationRetiresGhostPopulationAndChunkTicket(h);}
    @GameTest(template="dreamseekers_test_arena",batch="hand_oldhall",timeoutTicks=160)
    public static void oldhall_savedRefundSnapshotUsesOriginalPayerAndImmutablePayment(GameTestHelper h)throws Exception{BoneWingProductionGameTests.savedRefundSnapshotUsesOriginalPayerAndImmutablePayment(h);}
    @GameTest(template="dreamseekers_test_arena",batch="hand_oldhall",timeoutTicks=160)
    public static void oldhall_joinCallbackCannotReenterCompletionOrStartAnotherPayment(GameTestHelper h)throws Exception{BoneWingProductionGameTests.joinCallbackCannotReenterCompletionOrStartAnotherPayment(h);}
    @GameTest(template="dreamseekers_test_arena",batch="hand_registry",timeoutTicks=100)
    public static void registry_allEightRegisteredFactionFactoriesPaidRotationsAndCancel(GameTestHelper h)throws Exception{ScaffoldBuildingGameTests.allEightRegisteredFactionFactoriesPaidRotationsAndCancel(h);}
    @GameTest(template="ghost_unit_arena",batch="hand_oldghostskill",timeoutTicks=80)
    public static void oldghostskill_ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(GameTestHelper h)throws Exception{ShadowGhostSafetyGameTests.ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(h);}
    @GameTest(template="dreamseekers_test_arena",batch="hand_oldghostprod",timeoutTicks=750)
    public static void oldghostprod_exactHallWhitelistAndFullPaidQueueProduceOneShadowGhost(GameTestHelper h)throws Exception{ShadowGhostProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneShadowGhost(h);}
    @AfterBatch(batch="hand_oldhall") public static void cleanup_oldhall(ServerLevel l){BoneWingProductionGameTests.cleanup(l);}
    @AfterBatch(batch="hand_registry") public static void cleanup_registry(ServerLevel l){ScaffoldBuildingGameTests.cleanup(l);}
    @AfterBatch(batch="hand_oldghostskill") public static void cleanup_oldghostskill(ServerLevel l){ShadowGhostSafetyGameTests.cleanup(l);}
    @AfterBatch(batch="hand_oldghostprod") public static void cleanup_oldghostprod(ServerLevel l){ShadowGhostProductionGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="hand_oldfriendly",timeoutTicks=850)
    public static void oldfriendly_regression(GameTestHelper h)throws Exception{FriendlyGhostProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneFriendlyGhost(h);}
    @AfterBatch(batch="hand_oldfriendly") public static void cleanup_oldfriendly(ServerLevel l){FriendlyGhostProductionGameTests.cleanup(l);}
    @GameTest(template="ghost_unit_arena",batch="hand_oldghostcombat",timeoutTicks=260)
    public static void oldghostcombat_regression(GameTestHelper h)throws Exception{ShadowGhostSafetyGameTests.realFlightMeleeReachesAirborneEnemyWithoutContactDive(h);}
    @AfterBatch(batch="hand_oldghostcombat") public static void cleanup_oldghostcombat(ServerLevel l){ShadowGhostSafetyGameTests.cleanup(l);}
}
