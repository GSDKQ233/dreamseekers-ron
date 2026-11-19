// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
/** Ash foundation + narrowly affected existing Hall, skill, and shared impulse regressions. */
@GameTestHolder("dreamseekers_ash_bone_wing_focused") @PrefixGameTestTemplate(false)
public final class AshBoneWingFocusedGameTests {
    @GameTest(template="ash_unit_arena",batch="ash_flight",timeoutTicks=740)
    public static void flight_genuinelyFliesOverTwoBlockBarrierWithoutCollision(GameTestHelper h)throws Exception{AshBoneWingFlightGameTests.genuinelyFliesOverTwoBlockBarrierWithoutCollision(h);}
    @GameTest(template="ash_unit_arena",batch="ash_flight",timeoutTicks=820)
    public static void flight_sealedWallsCannotBeCrossedOrHarvestedThrough(GameTestHelper h)throws Exception{AshBoneWingFlightGameTests.sealedWallsCannotBeCrossedOrHarvestedThrough(h);}
    @GameTest(template="ash_unit_arena",batch="ash_flight",timeoutTicks=730)
    public static void flight_stopAndReplacementOrdersCancelFlightWithoutWildWandering(GameTestHelper h)throws Exception{AshBoneWingFlightGameTests.stopAndReplacementOrdersCancelFlightWithoutWildWandering(h);}
    @GameTest(template="ash_unit_arena",batch="ash_flight",timeoutTicks=890)
    public static void flight_lowFlightCeilingDoesNotRatchetAndRetriesWhenOpeningAppears(GameTestHelper h)throws Exception{AshBoneWingFlightGameTests.lowFlightCeilingDoesNotRatchetAndRetriesWhenOpeningAppears(h);}
    @GameTest(template="ash_unit_arena",batch="ash_flight",timeoutTicks=1120)
    public static void flight_queuedMoveThenHoldThenStopCannotResumeStaleFlight(GameTestHelper h)throws Exception{AshBoneWingFlightGameTests.queuedMoveThenHoldThenStopCannotResumeStaleFlight(h);}
    @GameTest(template="ash_unit_arena",batch="ash_flight",timeoutTicks=1060)
    public static void flight_sameTickABAndABAKeepFinalFlightDestination(GameTestHelper h)throws Exception{AshBoneWingFlightGameTests.sameTickABAndABAKeepFinalFlightDestination(h);}
    @GameTest(template="ash_unit_arena",batch="ash_flight",timeoutTicks=920)
    public static void flight_wideBodyRejectsOneHighAndTwoWideButFitsThreeWideTwoHigh(GameTestHelper h)throws Exception{AshBoneWingFlightGameTests.wideBodyRejectsOneHighAndTwoWideButFitsThreeWideTwoHigh(h);}
    @GameTest(template="ash_unit_arena",batch="ash_flight",timeoutTicks=500)
    public static void flight_malformedItemBoatMissingAndRemovedTargetsNeverBecomeFollowOrDamage(GameTestHelper h)throws Exception{AshBoneWingFlightGameTests.malformedItemBoatMissingAndRemovedTargetsNeverBecomeFollowOrDamage(h);}
    @AfterBatch(batch="ash_flight") public static void cleanup_flight(ServerLevel l){AshBoneWingFlightGameTests.cleanup(l);}
    @GameTest(template="ash_unit_arena",batch="ash_commandauthority",timeoutTicks=80)
    public static void commandauthority_authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(GameTestHelper h)throws Exception{AshBoneWingCommandAuthorityGameTests.authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(h);}
    @GameTest(template="ash_unit_arena",batch="ash_commandauthority",timeoutTicks=80)
    public static void commandauthority_ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(GameTestHelper h)throws Exception{AshBoneWingCommandAuthorityGameTests.ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(h);}
    @GameTest(template="ash_unit_arena",batch="ash_commandauthority",timeoutTicks=80)
    public static void commandauthority_buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(GameTestHelper h)throws Exception{AshBoneWingCommandAuthorityGameTests.buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(h);}
    @AfterBatch(batch="ash_commandauthority") public static void cleanup_commandauthority(ServerLevel l){AshBoneWingCommandAuthorityGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="ash_production",timeoutTicks=850)
    public static void production_exactHallWhitelistAndFullPaidQueueProduceOneAshBoneWing(GameTestHelper h)throws Exception{AshBoneWingProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneAshBoneWing(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ash_production",timeoutTicks=280)
    public static void production_blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper h)throws Exception{AshBoneWingProductionGameTests.blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ash_production",timeoutTicks=100)
    public static void production_populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper h)throws Exception{AshBoneWingProductionGameTests.populationResourcesAndRepeatedCancellationNeverDuplicate(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ash_production",timeoutTicks=100)
    public static void production_actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper h)throws Exception{AshBoneWingProductionGameTests.actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ash_production",timeoutTicks=180)
    public static void production_rangedUnitCannotAcquireOrDamageBuildings(GameTestHelper h)throws Exception{AshBoneWingProductionGameTests.rangedUnitCannotAcquireOrDamageBuildings(h);}
    @AfterBatch(batch="ash_production") public static void cleanup_production(ServerLevel l){AshBoneWingProductionGameTests.cleanup(l);}
    @GameTest(template="ash_unit_arena",batch="ash_safety",timeoutTicks=80)
    public static void safety_ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(GameTestHelper h)throws Exception{AshBoneWingSafetyGameTests.ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(h);}
    @GameTest(template="ash_unit_arena",batch="ash_safety",timeoutTicks=220)
    public static void safety_ashFollowMovesAndRevokedAllianceCancelsItsPath(GameTestHelper h)throws Exception{AshBoneWingSafetyGameTests.ashFollowMovesAndRevokedAllianceCancelsItsPath(h);}
    @GameTest(template="ash_unit_arena",batch="ash_safety",timeoutTicks=220)
    public static void safety_revokedFollowAllowsNewHostileAutoAggroWithoutRevivingOldCommand(GameTestHelper h)throws Exception{AshBoneWingSafetyGameTests.revokedFollowAllowsNewHostileAutoAggroWithoutRevivingOldCommand(h);}
    @GameTest(template="ash_unit_arena",batch="ash_safety",timeoutTicks=460)
    public static void safety_sameEnemyExplicitAttackReleasesHoldAndReallyPursues(GameTestHelper h)throws Exception{AshBoneWingSafetyGameTests.sameEnemyExplicitAttackReleasesHoldAndReallyPursues(h);}
    @AfterBatch(batch="ash_safety") public static void cleanup_safety(ServerLevel l){AshBoneWingSafetyGameTests.cleanup(l);}
    @GameTest(template="ash_unit_arena",batch="ash_combat",timeoutTicks=520)
    public static void combat_realRangedGoalFiresPhysicalBoltAtAirEnemyWithoutAshCollateral(GameTestHelper h)throws Exception{AshBoneWingCombatGameTests.realRangedGoalFiresPhysicalBoltAtAirEnemyWithoutAshCollateral(h);}
    @GameTest(template="ash_unit_arena",batch="ash_combat",timeoutTicks=280)
    public static void combat_allianceAndOwnershipChangesInvalidateInFlightButStopDoesNot(GameTestHelper h)throws Exception{AshBoneWingCombatGameTests.allianceAndOwnershipChangesInvalidateInFlightButStopDoesNot(h);}
    @GameTest(template="ash_unit_arena",batch="ash_combat",timeoutTicks=260)
    public static void combat_freshLineOfSightRangeAndDisableEffectsGateEveryRelease(GameTestHelper h)throws Exception{AshBoneWingCombatGameTests.freshLineOfSightRangeAndDisableEffectsGateEveryRelease(h);}
    @GameTest(template="ash_unit_arena",batch="ash_combat",timeoutTicks=180)
    public static void combat_spentInvalidAndReloadedProjectilesCannotBeRearmedOrDamageTwice(GameTestHelper h)throws Exception{AshBoneWingCombatGameTests.spentInvalidAndReloadedProjectilesCannotBeRearmedOrDamageTwice(h);}
    @GameTest(template="ash_unit_arena",batch="ash_combat",timeoutTicks=280)
    public static void combat_lastWindupStunCannotLeakNewProjectileAndLaterReleaseRecovers(GameTestHelper h)throws Exception{AshBoneWingCombatGameTests.lastWindupStunCannotLeakNewProjectileAndLaterReleaseRecovers(h);}
    @GameTest(template="ash_unit_arena",batch="ash_combat",timeoutTicks=280)
    public static void combat_stopAndSameTickRetargetResetRealRangedWindupWithoutCooldownReset(GameTestHelper h)throws Exception{AshBoneWingCombatGameTests.stopAndSameTickRetargetResetRealRangedWindupWithoutCooldownReset(h);}
    @GameTest(template="ash_unit_arena",batch="ash_combat",timeoutTicks=220)
    public static void combat_physicalGroundFireAndOriginalProjectileRemainSeparate(GameTestHelper h)throws Exception{AshBoneWingCombatGameTests.physicalGroundFireAndOriginalProjectileRemainSeparate(h);}
    @GameTest(template="ash_unit_arena",batch="ash_combat",timeoutTicks=200)
    public static void combat_identityCaptureForgedOwnerAndPortalPickupFailClosed(GameTestHelper h)throws Exception{AshBoneWingCombatGameTests.identityCaptureForgedOwnerAndPortalPickupFailClosed(h);}
    @GameTest(template="ash_unit_arena",batch="ash_combat",timeoutTicks=160)
    public static void combat_reentrantHurtCannotReplayConsumedProjectile(GameTestHelper h)throws Exception{AshBoneWingCombatGameTests.reentrantHurtCannotReplayConsumedProjectile(h);}
    @AfterBatch(batch="ash_combat") public static void cleanup_combat(ServerLevel l){AshBoneWingCombatGameTests.cleanup(l);}
    @GameTest(template="ash_unit_arena",batch="ash_native",timeoutTicks=120)
    public static void native_nativeRangedAndPlayerGoalsAreReplacedWithoutChangingWildIdentity(GameTestHelper h)throws Exception{AshBoneWingNativeGameTests.nativeRangedAndPlayerGoalsAreReplacedWithoutChangingWildIdentity(h);}
    @GameTest(template="ash_unit_arena",batch="ash_native",timeoutTicks=160)
    public static void native_noInventedDamageImmunitiesAndRealArrowsDamageBoth(GameTestHelper h)throws Exception{AshBoneWingNativeGameTests.noInventedDamageImmunitiesAndRealArrowsDamageBoth(h);}
    @GameTest(template="ash_unit_arena",batch="ash_native",timeoutTicks=120)
    public static void native_interactionAndRealDeathCannotCreatePaidLootOrExperience(GameTestHelper h)throws Exception{AshBoneWingNativeGameTests.interactionAndRealDeathCannotCreatePaidLootOrExperience(h);}
    @GameTest(template="ash_unit_arena",batch="ash_native_peaceful",timeoutTicks=130)
    public static void native_paidPersistenceSurvivesPeacefulWhileOriginalMonsterDespawns(GameTestHelper h)throws Exception{AshBoneWingNativeGameTests.paidPersistenceSurvivesPeacefulWhileOriginalMonsterDespawns(h);}
    @GameTest(template="ash_unit_arena",batch="ash_native",timeoutTicks=120)
    public static void native_portalsForcedRidingAndLoadedNoAiCannotEscapePaidFlightPolicy(GameTestHelper h)throws Exception{AshBoneWingNativeGameTests.portalsForcedRidingAndLoadedNoAiCannotEscapePaidFlightPolicy(h);}
    @GameTest(template="ash_unit_arena",batch="ash_native",timeoutTicks=120)
    public static void native_undeadStatusSemanticsAndPaidOnlyRandomImpulseArePreserved(GameTestHelper h)throws Exception{AshBoneWingNativeGameTests.undeadStatusSemanticsAndPaidOnlyRandomImpulseArePreserved(h);}
    @AfterBatch(batch="ash_native") public static void cleanup_native(ServerLevel l){AshBoneWingNativeGameTests.cleanup(l);}
    @AfterBatch(batch="ash_native_peaceful") public static void cleanup_peaceful(ServerLevel l){AshBoneWingNativeGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="ash_old_oldhall",timeoutTicks=760)
    public static void oldhall_exactHallWhitelistAndFullPaidQueueProduceOneLightBoneWing(GameTestHelper h)throws Exception{BoneWingProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneLightBoneWing(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ash_old_oldhall",timeoutTicks=280)
    public static void oldhall_blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper h)throws Exception{BoneWingProductionGameTests.blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ash_old_oldhall",timeoutTicks=100)
    public static void oldhall_populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper h)throws Exception{BoneWingProductionGameTests.populationResourcesAndRepeatedCancellationNeverDuplicate(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ash_old_oldhall",timeoutTicks=100)
    public static void oldhall_actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper h)throws Exception{BoneWingProductionGameTests.actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ash_old_oldhall",timeoutTicks=120)
    public static void oldhall_originalPayerRefundsBeforeTickAndMutableCostsCannotChangeReceipts(GameTestHelper h)throws Exception{BoneWingProductionGameTests.originalPayerRefundsBeforeTickAndMutableCostsCannotChangeReceipts(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ash_old_oldhall",timeoutTicks=140)
    public static void oldhall_destructionSettlesOriginalPayerButDetachNeverRefundsOrRevives(GameTestHelper h)throws Exception{BoneWingProductionGameTests.destructionSettlesOriginalPayerButDetachNeverRefundsOrRevives(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ash_old_oldhall",timeoutTicks=160)
    public static void oldhall_observedOwnerABAAndExternalQueueClearCannotReuseOldPaidActive(GameTestHelper h)throws Exception{BoneWingProductionGameTests.observedOwnerABAAndExternalQueueClearCannotReuseOldPaidActive(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ash_old_oldhall",timeoutTicks=160)
    public static void oldhall_futureSchemaAtCompletionPausesNoScoreAndStillRefundsExactPayer(GameTestHelper h)throws Exception{BoneWingProductionGameTests.futureSchemaAtCompletionPausesNoScoreAndStillRefundsExactPayer(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ash_old_oldhall",timeoutTicks=220)
    public static void oldhall_actualJoinEventCancellationCannotConsumePaidQueueOrScore(GameTestHelper h)throws Exception{BoneWingProductionGameTests.actualJoinEventCancellationCannotConsumePaidQueueOrScore(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ash_old_oldhall",timeoutTicks=220)
    public static void oldhall_lateJoinCancellationRetiresGhostPopulationAndChunkTicket(GameTestHelper h)throws Exception{BoneWingProductionGameTests.lateJoinCancellationRetiresGhostPopulationAndChunkTicket(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ash_old_oldhall",timeoutTicks=160)
    public static void oldhall_savedRefundSnapshotUsesOriginalPayerAndImmutablePayment(GameTestHelper h)throws Exception{BoneWingProductionGameTests.savedRefundSnapshotUsesOriginalPayerAndImmutablePayment(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ash_old_oldhall",timeoutTicks=160)
    public static void oldhall_joinCallbackCannotReenterCompletionOrStartAnotherPayment(GameTestHelper h)throws Exception{BoneWingProductionGameTests.joinCallbackCannotReenterCompletionOrStartAnotherPayment(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ash_old_registry",timeoutTicks=100)
    public static void registry_allEightRegisteredFactionFactoriesPaidRotationsAndCancel(GameTestHelper h)throws Exception{ScaffoldBuildingGameTests.allEightRegisteredFactionFactoriesPaidRotationsAndCancel(h);}
    @GameTest(template="ghost_unit_arena",batch="ash_old_oldghostskill",timeoutTicks=80)
    public static void oldghostskill_ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(GameTestHelper h)throws Exception{ShadowGhostSafetyGameTests.ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ash_old_oldghostprod",timeoutTicks=750)
    public static void oldghostprod_exactHallWhitelistAndFullPaidQueueProduceOneShadowGhost(GameTestHelper h)throws Exception{ShadowGhostProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneShadowGhost(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ash_old_oldfriendly",timeoutTicks=850)
    public static void oldfriendly_regression(GameTestHelper h)throws Exception{FriendlyGhostProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneFriendlyGhost(h);}
    @GameTest(template="ghost_unit_arena",batch="ash_old_oldghostcombat",timeoutTicks=260)
    public static void oldghostcombat_regression(GameTestHelper h)throws Exception{ShadowGhostSafetyGameTests.realFlightMeleeReachesAirborneEnemyWithoutContactDive(h);}
    @AfterBatch(batch="ash_old_oldhall") public static void cleanup_oldhall(ServerLevel l){BoneWingProductionGameTests.cleanup(l);}
    @AfterBatch(batch="ash_old_registry") public static void cleanup_registry(ServerLevel l){ScaffoldBuildingGameTests.cleanup(l);}
    @AfterBatch(batch="ash_old_oldghostskill") public static void cleanup_oldghostskill(ServerLevel l){ShadowGhostSafetyGameTests.cleanup(l);}
    @AfterBatch(batch="ash_old_oldghostprod") public static void cleanup_oldghostprod(ServerLevel l){ShadowGhostProductionGameTests.cleanup(l);}
    @AfterBatch(batch="ash_old_oldfriendly") public static void cleanup_oldfriendly(ServerLevel l){FriendlyGhostProductionGameTests.cleanup(l);}
    @AfterBatch(batch="ash_old_oldghostcombat") public static void cleanup_oldghostcombat(ServerLevel l){ShadowGhostSafetyGameTests.cleanup(l);}
    @GameTest(template="unit_isolation_arena",batch="ash_oldlightimpulse",timeoutTicks=140)
    public static void oldlight_nativeImpulse(GameTestHelper h)throws Exception{BoneWingCombatGameTests.nativeImpulseWildControlPersistenceInteractionsAndDeathAreIsolated(h);}
    @AfterBatch(batch="ash_oldlightimpulse") public static void cleanup_oldlight(ServerLevel l){BoneWingCombatGameTests.cleanup(l);}
    @GameTest(template="ash_unit_arena",batch="ash_flight",timeoutTicks=1800)
    public static void flight_fourDirectionsAndDiagonalActuallyFinishTheSelectedFootEndpoint(GameTestHelper h)throws Exception{AshBoneWingFlightGameTests.fourDirectionsAndDiagonalActuallyFinishTheSelectedFootEndpoint(h);}
    @GameTest(template="ash_unit_arena",batch="ash_flight",timeoutTicks=700)
    public static void flight_nativeDeferredRecomputeDoesNotDoubleShiftAndStopCannotReviveIt(GameTestHelper h)throws Exception{AshBoneWingFlightGameTests.nativeDeferredRecomputeDoesNotDoubleShiftAndStopCannotReviveIt(h);}
    @GameTest(template="dreamseekers_test_arena",batch="ash_oldhand",timeoutTicks=850)
    public static void oldhand_paidHallQueue(GameTestHelper h)throws Exception{ShadowHandProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneShadowHand(h);}
    @AfterBatch(batch="ash_oldhand") public static void cleanup_oldhand(ServerLevel l){ShadowHandProductionGameTests.cleanup(l);}
}
