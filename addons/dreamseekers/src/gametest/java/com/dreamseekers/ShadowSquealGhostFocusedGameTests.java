// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
/** New ranged unit and affected Hall/typed-ghost service; not a full-faction aggregate. */
@GameTestHolder("dreamseekers_shadow_squeal_focused") @PrefixGameTestTemplate(false)
public final class ShadowSquealGhostFocusedGameTests {
    @GameTest(template="squeal_unit_arena",batch="squeal_flight",timeoutTicks=740)
    public static void flight_genuinelyFliesOverTwoBlockBarrierWithoutCollision(GameTestHelper h)throws Exception{ShadowSquealGhostFlightGameTests.genuinelyFliesOverTwoBlockBarrierWithoutCollision(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_flight",timeoutTicks=820)
    public static void flight_sealedWallsCannotBeCrossedOrHarvestedThrough(GameTestHelper h)throws Exception{ShadowSquealGhostFlightGameTests.sealedWallsCannotBeCrossedOrHarvestedThrough(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_flight",timeoutTicks=730)
    public static void flight_stopAndReplacementOrdersCancelFlightWithoutWildWandering(GameTestHelper h)throws Exception{ShadowSquealGhostFlightGameTests.stopAndReplacementOrdersCancelFlightWithoutWildWandering(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_flight",timeoutTicks=890)
    public static void flight_lowFlightCeilingDoesNotRatchetAndRetriesWhenOpeningAppears(GameTestHelper h)throws Exception{ShadowSquealGhostFlightGameTests.lowFlightCeilingDoesNotRatchetAndRetriesWhenOpeningAppears(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_flight",timeoutTicks=1120)
    public static void flight_queuedMoveThenHoldThenStopCannotResumeStaleFlight(GameTestHelper h)throws Exception{ShadowSquealGhostFlightGameTests.queuedMoveThenHoldThenStopCannotResumeStaleFlight(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_flight",timeoutTicks=1060)
    public static void flight_sameTickABAndABAKeepFinalFlightDestination(GameTestHelper h)throws Exception{ShadowSquealGhostFlightGameTests.sameTickABAndABAKeepFinalFlightDestination(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_flight",timeoutTicks=920)
    public static void flight_tallBodyCannotFitOneHighSlotButFitsOpenedTwoHighSlot(GameTestHelper h)throws Exception{ShadowSquealGhostFlightGameTests.tallBodyCannotFitOneHighSlotButFitsOpenedTwoHighSlot(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_flight",timeoutTicks=500)
    public static void flight_malformedItemBoatMissingAndRemovedTargetsNeverBecomeFollowOrDamage(GameTestHelper h)throws Exception{ShadowSquealGhostFlightGameTests.malformedItemBoatMissingAndRemovedTargetsNeverBecomeFollowOrDamage(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_commandauthority",timeoutTicks=80)
    public static void commandauthority_authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(GameTestHelper h)throws Exception{ShadowSquealGhostCommandAuthorityGameTests.authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_commandauthority",timeoutTicks=80)
    public static void commandauthority_ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(GameTestHelper h)throws Exception{ShadowSquealGhostCommandAuthorityGameTests.ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_commandauthority",timeoutTicks=80)
    public static void commandauthority_buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(GameTestHelper h)throws Exception{ShadowSquealGhostCommandAuthorityGameTests.buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(h);}
    @GameTest(template="dreamseekers_test_arena",batch="squeal_production",timeoutTicks=850)
    public static void production_exactHallWhitelistAndFullPaidQueueProduceOneShadowSquealGhost(GameTestHelper h)throws Exception{ShadowSquealGhostProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneShadowSquealGhost(h);}
    @GameTest(template="dreamseekers_test_arena",batch="squeal_production",timeoutTicks=280)
    public static void production_blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper h)throws Exception{ShadowSquealGhostProductionGameTests.blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(h);}
    @GameTest(template="dreamseekers_test_arena",batch="squeal_production",timeoutTicks=100)
    public static void production_populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper h)throws Exception{ShadowSquealGhostProductionGameTests.populationResourcesAndRepeatedCancellationNeverDuplicate(h);}
    @GameTest(template="dreamseekers_test_arena",batch="squeal_production",timeoutTicks=100)
    public static void production_actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper h)throws Exception{ShadowSquealGhostProductionGameTests.actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(h);}
    @GameTest(template="dreamseekers_test_arena",batch="squeal_production",timeoutTicks=180)
    public static void production_rangedUnitCannotAcquireOrDamageBuildings(GameTestHelper h)throws Exception{ShadowSquealGhostProductionGameTests.rangedUnitCannotAcquireOrDamageBuildings(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_safety",timeoutTicks=80)
    public static void safety_ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(GameTestHelper h)throws Exception{ShadowSquealGhostSafetyGameTests.ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_safety",timeoutTicks=220)
    public static void safety_friendlyFollowMovesAndRevokedAllianceCancelsItsPath(GameTestHelper h)throws Exception{ShadowSquealGhostSafetyGameTests.friendlyFollowMovesAndRevokedAllianceCancelsItsPath(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_safety",timeoutTicks=220)
    public static void safety_revokedFollowAllowsNewHostileAutoAggroWithoutRevivingOldCommand(GameTestHelper h)throws Exception{ShadowSquealGhostSafetyGameTests.revokedFollowAllowsNewHostileAutoAggroWithoutRevivingOldCommand(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_safety",timeoutTicks=460)
    public static void safety_sameEnemyExplicitAttackReleasesHoldAndReallyPursues(GameTestHelper h)throws Exception{ShadowSquealGhostSafetyGameTests.sameEnemyExplicitAttackReleasesHoldAndReallyPursues(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_combat",timeoutTicks=520)
    public static void combat_realRangedGoalFiresPhysicalBoltAtAirEnemyWithoutFriendlyCollateral(GameTestHelper h)throws Exception{ShadowSquealGhostCombatGameTests.realRangedGoalFiresPhysicalBoltAtAirEnemyWithoutFriendlyCollateral(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_combat",timeoutTicks=280)
    public static void combat_allianceAndOwnershipChangesInvalidateInFlightButStopDoesNot(GameTestHelper h)throws Exception{ShadowSquealGhostCombatGameTests.allianceAndOwnershipChangesInvalidateInFlightButStopDoesNot(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_combat",timeoutTicks=260)
    public static void combat_freshLineOfSightRangeAndDisableEffectsGateEveryRelease(GameTestHelper h)throws Exception{ShadowSquealGhostCombatGameTests.freshLineOfSightRangeAndDisableEffectsGateEveryRelease(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_combat",timeoutTicks=180)
    public static void combat_spentInvalidAndReloadedProjectilesCannotBeRearmedOrDamageTwice(GameTestHelper h)throws Exception{ShadowSquealGhostCombatGameTests.spentInvalidAndReloadedProjectilesCannotBeRearmedOrDamageTwice(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_combat",timeoutTicks=280)
    public static void combat_lastWindupStunCannotLeakNewProjectileAndLaterReleaseRecovers(GameTestHelper h)throws Exception{ShadowSquealGhostCombatGameTests.lastWindupStunCannotLeakNewProjectileAndLaterReleaseRecovers(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_combat",timeoutTicks=280)
    public static void combat_stopAndSameTickRetargetResetRealRangedWindupWithoutCooldownReset(GameTestHelper h)throws Exception{ShadowSquealGhostCombatGameTests.stopAndSameTickRetargetResetRealRangedWindupWithoutCooldownReset(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_combat",timeoutTicks=220)
    public static void combat_physicalGroundFireAndOriginalWaveRemainSeparate(GameTestHelper h)throws Exception{ShadowSquealGhostCombatGameTests.physicalGroundFireAndOriginalWaveRemainSeparate(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_combat",timeoutTicks=200)
    public static void combat_identityCaptureForgedOwnerAndPortalPickupFailClosed(GameTestHelper h)throws Exception{ShadowSquealGhostCombatGameTests.identityCaptureForgedOwnerAndPortalPickupFailClosed(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_combat",timeoutTicks=160)
    public static void combat_reentrantHurtCannotReplayConsumedProjectile(GameTestHelper h)throws Exception{ShadowSquealGhostCombatGameTests.reentrantHurtCannotReplayConsumedProjectile(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_native",timeoutTicks=120)
    public static void native_nativeDiveAndPlayerGoalsAreReplacedWithoutChangingWildIdentity(GameTestHelper h)throws Exception{ShadowSquealGhostNativeGameTests.nativeDiveAndPlayerGoalsAreReplacedWithoutChangingWildIdentity(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_native",timeoutTicks=160)
    public static void native_documentedNativeImmunitiesRemainButRealArrowsDamageBoth(GameTestHelper h)throws Exception{ShadowSquealGhostNativeGameTests.documentedNativeImmunitiesRemainButRealArrowsDamageBoth(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_native",timeoutTicks=120)
    public static void native_interactionAndRealDeathCannotCreatePaidLootOrExperience(GameTestHelper h)throws Exception{ShadowSquealGhostNativeGameTests.interactionAndRealDeathCannotCreatePaidLootOrExperience(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_peaceful",timeoutTicks=130)
    public static void peaceful_paidPersistenceSurvivesPeacefulWhileOriginalMonsterDespawns(GameTestHelper h)throws Exception{ShadowSquealGhostNativeGameTests.paidPersistenceSurvivesPeacefulWhileOriginalMonsterDespawns(h);}
    @GameTest(template="squeal_unit_arena",batch="squeal_native",timeoutTicks=120)
    public static void native_portalsForcedRidingAndLoadedNoAiCannotEscapePaidFlightPolicy(GameTestHelper h)throws Exception{ShadowSquealGhostNativeGameTests.portalsForcedRidingAndLoadedNoAiCannotEscapePaidFlightPolicy(h);}
    @GameTest(template="dreamseekers_test_arena",batch="squeal_oldhall",timeoutTicks=760)
    public static void oldhall_exactHallWhitelistAndFullPaidQueueProduceOneLightBoneWing(GameTestHelper h)throws Exception{BoneWingProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneLightBoneWing(h);}
    @GameTest(template="dreamseekers_test_arena",batch="squeal_oldhall",timeoutTicks=280)
    public static void oldhall_blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper h)throws Exception{BoneWingProductionGameTests.blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(h);}
    @GameTest(template="dreamseekers_test_arena",batch="squeal_oldhall",timeoutTicks=100)
    public static void oldhall_populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper h)throws Exception{BoneWingProductionGameTests.populationResourcesAndRepeatedCancellationNeverDuplicate(h);}
    @GameTest(template="dreamseekers_test_arena",batch="squeal_oldhall",timeoutTicks=100)
    public static void oldhall_actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper h)throws Exception{BoneWingProductionGameTests.actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(h);}
    @GameTest(template="dreamseekers_test_arena",batch="squeal_oldhall",timeoutTicks=120)
    public static void oldhall_originalPayerRefundsBeforeTickAndMutableCostsCannotChangeReceipts(GameTestHelper h)throws Exception{BoneWingProductionGameTests.originalPayerRefundsBeforeTickAndMutableCostsCannotChangeReceipts(h);}
    @GameTest(template="dreamseekers_test_arena",batch="squeal_oldhall",timeoutTicks=140)
    public static void oldhall_destructionSettlesOriginalPayerButDetachNeverRefundsOrRevives(GameTestHelper h)throws Exception{BoneWingProductionGameTests.destructionSettlesOriginalPayerButDetachNeverRefundsOrRevives(h);}
    @GameTest(template="dreamseekers_test_arena",batch="squeal_oldhall",timeoutTicks=160)
    public static void oldhall_observedOwnerABAAndExternalQueueClearCannotReuseOldPaidActive(GameTestHelper h)throws Exception{BoneWingProductionGameTests.observedOwnerABAAndExternalQueueClearCannotReuseOldPaidActive(h);}
    @GameTest(template="dreamseekers_test_arena",batch="squeal_oldhall",timeoutTicks=160)
    public static void oldhall_futureSchemaAtCompletionPausesNoScoreAndStillRefundsExactPayer(GameTestHelper h)throws Exception{BoneWingProductionGameTests.futureSchemaAtCompletionPausesNoScoreAndStillRefundsExactPayer(h);}
    @GameTest(template="dreamseekers_test_arena",batch="squeal_oldhall",timeoutTicks=220)
    public static void oldhall_actualJoinEventCancellationCannotConsumePaidQueueOrScore(GameTestHelper h)throws Exception{BoneWingProductionGameTests.actualJoinEventCancellationCannotConsumePaidQueueOrScore(h);}
    @GameTest(template="dreamseekers_test_arena",batch="squeal_oldhall",timeoutTicks=220)
    public static void oldhall_lateJoinCancellationRetiresGhostPopulationAndChunkTicket(GameTestHelper h)throws Exception{BoneWingProductionGameTests.lateJoinCancellationRetiresGhostPopulationAndChunkTicket(h);}
    @GameTest(template="dreamseekers_test_arena",batch="squeal_oldhall",timeoutTicks=160)
    public static void oldhall_savedRefundSnapshotUsesOriginalPayerAndImmutablePayment(GameTestHelper h)throws Exception{BoneWingProductionGameTests.savedRefundSnapshotUsesOriginalPayerAndImmutablePayment(h);}
    @GameTest(template="dreamseekers_test_arena",batch="squeal_oldhall",timeoutTicks=160)
    public static void oldhall_joinCallbackCannotReenterCompletionOrStartAnotherPayment(GameTestHelper h)throws Exception{BoneWingProductionGameTests.joinCallbackCannotReenterCompletionOrStartAnotherPayment(h);}
    @GameTest(template="dreamseekers_test_arena",batch="squeal_registry",timeoutTicks=100)
    public static void registry_allEightRegisteredFactionFactoriesPaidRotationsAndCancel(GameTestHelper h)throws Exception{ScaffoldBuildingGameTests.allEightRegisteredFactionFactoriesPaidRotationsAndCancel(h);}
    @GameTest(template="ghost_unit_arena",batch="squeal_oldghostskill",timeoutTicks=80)
    public static void oldghostskill_ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(GameTestHelper h)throws Exception{ShadowGhostSafetyGameTests.ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(h);}
    @GameTest(template="dreamseekers_test_arena",batch="squeal_oldghostprod",timeoutTicks=750)
    public static void oldghostprod_exactHallWhitelistAndFullPaidQueueProduceOneShadowGhost(GameTestHelper h)throws Exception{ShadowGhostProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneShadowGhost(h);}
    @AfterBatch(batch="squeal_flight") public static void cleanup_flight(ServerLevel l){ShadowSquealGhostFlightGameTests.cleanup(l);}
    @AfterBatch(batch="squeal_commandauthority") public static void cleanup_commandauthority(ServerLevel l){ShadowSquealGhostCommandAuthorityGameTests.cleanup(l);}
    @AfterBatch(batch="squeal_production") public static void cleanup_production(ServerLevel l){ShadowSquealGhostProductionGameTests.cleanup(l);}
    @AfterBatch(batch="squeal_safety") public static void cleanup_safety(ServerLevel l){ShadowSquealGhostSafetyGameTests.cleanup(l);}
    @AfterBatch(batch="squeal_combat") public static void cleanup_combat(ServerLevel l){ShadowSquealGhostCombatGameTests.cleanup(l);}
    @AfterBatch(batch="squeal_native") public static void cleanup_native(ServerLevel l){ShadowSquealGhostNativeGameTests.cleanup(l);}
    @AfterBatch(batch="squeal_peaceful") public static void cleanup_peaceful(ServerLevel l){ShadowSquealGhostNativeGameTests.cleanup(l);}
    @AfterBatch(batch="squeal_oldhall") public static void cleanup_oldhall(ServerLevel l){BoneWingProductionGameTests.cleanup(l);}
    @AfterBatch(batch="squeal_registry") public static void cleanup_registry(ServerLevel l){ScaffoldBuildingGameTests.cleanup(l);}
    @AfterBatch(batch="squeal_oldghostskill") public static void cleanup_oldghostskill(ServerLevel l){ShadowGhostSafetyGameTests.cleanup(l);}
    @AfterBatch(batch="squeal_oldghostprod") public static void cleanup_oldghostprod(ServerLevel l){ShadowGhostProductionGameTests.cleanup(l);}
}
