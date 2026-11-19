// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
/** Summoning-commander ranged foundation; not full-faction regression or playable summon system. */
@GameTestHolder("dreamseekers_weeping_squeal_focused") @PrefixGameTestTemplate(false)
public final class WeepingSquealGhostFocusedGameTests {
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_flight",timeoutTicks=740)
    public static void flight_genuinelyFliesOverTwoBlockBarrierWithoutCollision(GameTestHelper h)throws Exception{WeepingSquealGhostFlightGameTests.genuinelyFliesOverTwoBlockBarrierWithoutCollision(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_flight",timeoutTicks=820)
    public static void flight_sealedWallsCannotBeCrossedOrHarvestedThrough(GameTestHelper h)throws Exception{WeepingSquealGhostFlightGameTests.sealedWallsCannotBeCrossedOrHarvestedThrough(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_flight",timeoutTicks=730)
    public static void flight_stopAndReplacementOrdersCancelFlightWithoutWildWandering(GameTestHelper h)throws Exception{WeepingSquealGhostFlightGameTests.stopAndReplacementOrdersCancelFlightWithoutWildWandering(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_flight",timeoutTicks=890)
    public static void flight_lowFlightCeilingDoesNotRatchetAndRetriesWhenOpeningAppears(GameTestHelper h)throws Exception{WeepingSquealGhostFlightGameTests.lowFlightCeilingDoesNotRatchetAndRetriesWhenOpeningAppears(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_flight",timeoutTicks=1120)
    public static void flight_queuedMoveThenHoldThenStopCannotResumeStaleFlight(GameTestHelper h)throws Exception{WeepingSquealGhostFlightGameTests.queuedMoveThenHoldThenStopCannotResumeStaleFlight(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_flight",timeoutTicks=1060)
    public static void flight_sameTickABAndABAKeepFinalFlightDestination(GameTestHelper h)throws Exception{WeepingSquealGhostFlightGameTests.sameTickABAndABAKeepFinalFlightDestination(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_flight",timeoutTicks=920)
    public static void flight_tallBodyCannotFitOneHighSlotButFitsOpenedTwoHighSlot(GameTestHelper h)throws Exception{WeepingSquealGhostFlightGameTests.tallBodyCannotFitOneHighSlotButFitsOpenedTwoHighSlot(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_flight",timeoutTicks=500)
    public static void flight_malformedItemBoatMissingAndRemovedTargetsNeverBecomeFollowOrDamage(GameTestHelper h)throws Exception{WeepingSquealGhostFlightGameTests.malformedItemBoatMissingAndRemovedTargetsNeverBecomeFollowOrDamage(h);}
    @AfterBatch(batch="weeping_focus_flight") public static void cleanup_flight(ServerLevel l){WeepingSquealGhostFlightGameTests.cleanup(l);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_commandauthority",timeoutTicks=80)
    public static void commandauthority_authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(GameTestHelper h)throws Exception{WeepingSquealGhostCommandAuthorityGameTests.authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_commandauthority",timeoutTicks=80)
    public static void commandauthority_ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(GameTestHelper h)throws Exception{WeepingSquealGhostCommandAuthorityGameTests.ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_commandauthority",timeoutTicks=80)
    public static void commandauthority_buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(GameTestHelper h)throws Exception{WeepingSquealGhostCommandAuthorityGameTests.buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(h);}
    @AfterBatch(batch="weeping_focus_commandauthority") public static void cleanup_commandauthority(ServerLevel l){WeepingSquealGhostCommandAuthorityGameTests.cleanup(l);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_safety",timeoutTicks=180)
    public static void safety_legalRepeatedSummonReserveLeavesRealCommandsCooldownPopulationAndWorldUntouched(GameTestHelper h)throws Exception{WeepingSquealGhostSafetyGameTests.legalRepeatedSummonReserveLeavesRealCommandsCooldownPopulationAndWorldUntouched(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_safety",timeoutTicks=160)
    public static void safety_ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(GameTestHelper h)throws Exception{WeepingSquealGhostSafetyGameTests.ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_safety",timeoutTicks=220)
    public static void safety_friendlyFollowMovesAndRevokedAllianceCancelsItsPath(GameTestHelper h)throws Exception{WeepingSquealGhostSafetyGameTests.friendlyFollowMovesAndRevokedAllianceCancelsItsPath(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_safety",timeoutTicks=220)
    public static void safety_revokedFollowAllowsNewHostileAutoAggroWithoutRevivingOldCommand(GameTestHelper h)throws Exception{WeepingSquealGhostSafetyGameTests.revokedFollowAllowsNewHostileAutoAggroWithoutRevivingOldCommand(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_safety",timeoutTicks=460)
    public static void safety_sameEnemyExplicitAttackReleasesHoldAndReallyPursues(GameTestHelper h)throws Exception{WeepingSquealGhostSafetyGameTests.sameEnemyExplicitAttackReleasesHoldAndReallyPursues(h);}
    @AfterBatch(batch="weeping_focus_safety") public static void cleanup_safety(ServerLevel l){WeepingSquealGhostSafetyGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="weeping_focus_production",timeoutTicks=1350)
    public static void production_exactHallWhitelistAndFullPaidQueueProduceOneWeepingSquealGhost(GameTestHelper h)throws Exception{WeepingSquealGhostProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneWeepingSquealGhost(h);}
    @GameTest(template="dreamseekers_test_arena",batch="weeping_focus_production",timeoutTicks=280)
    public static void production_blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper h)throws Exception{WeepingSquealGhostProductionGameTests.blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(h);}
    @GameTest(template="dreamseekers_test_arena",batch="weeping_focus_production",timeoutTicks=100)
    public static void production_populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper h)throws Exception{WeepingSquealGhostProductionGameTests.populationResourcesAndRepeatedCancellationNeverDuplicate(h);}
    @GameTest(template="dreamseekers_test_arena",batch="weeping_focus_production",timeoutTicks=100)
    public static void production_actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper h)throws Exception{WeepingSquealGhostProductionGameTests.actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(h);}
    @GameTest(template="dreamseekers_test_arena",batch="weeping_focus_production",timeoutTicks=180)
    public static void production_rangedUnitCannotAcquireOrDamageBuildings(GameTestHelper h)throws Exception{WeepingSquealGhostProductionGameTests.rangedUnitCannotAcquireOrDamageBuildings(h);}
    @AfterBatch(batch="weeping_focus_production") public static void cleanup_production(ServerLevel l){WeepingSquealGhostProductionGameTests.cleanup(l);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_combat",timeoutTicks=520)
    public static void combat_realRangedGoalFiresPhysicalBoltAtAirEnemyWithoutFriendlyCollateral(GameTestHelper h)throws Exception{WeepingSquealGhostCombatGameTests.realRangedGoalFiresPhysicalBoltAtAirEnemyWithoutFriendlyCollateral(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_combat",timeoutTicks=280)
    public static void combat_allianceAndOwnershipChangesInvalidateInFlightButStopDoesNot(GameTestHelper h)throws Exception{WeepingSquealGhostCombatGameTests.allianceAndOwnershipChangesInvalidateInFlightButStopDoesNot(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_combat",timeoutTicks=260)
    public static void combat_freshLineOfSightRangeAndDisableEffectsGateEveryRelease(GameTestHelper h)throws Exception{WeepingSquealGhostCombatGameTests.freshLineOfSightRangeAndDisableEffectsGateEveryRelease(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_combat",timeoutTicks=180)
    public static void combat_spentInvalidAndReloadedProjectilesCannotBeRearmedOrDamageTwice(GameTestHelper h)throws Exception{WeepingSquealGhostCombatGameTests.spentInvalidAndReloadedProjectilesCannotBeRearmedOrDamageTwice(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_combat",timeoutTicks=280)
    public static void combat_lastWindupStunCannotLeakNewProjectileAndLaterReleaseRecovers(GameTestHelper h)throws Exception{WeepingSquealGhostCombatGameTests.lastWindupStunCannotLeakNewProjectileAndLaterReleaseRecovers(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_combat",timeoutTicks=280)
    public static void combat_stopAndSameTickRetargetResetRealRangedWindupWithoutCooldownReset(GameTestHelper h)throws Exception{WeepingSquealGhostCombatGameTests.stopAndSameTickRetargetResetRealRangedWindupWithoutCooldownReset(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_combat",timeoutTicks=220)
    public static void combat_physicalGroundFireAndOriginalWaveRemainSeparate(GameTestHelper h)throws Exception{WeepingSquealGhostCombatGameTests.physicalGroundFireAndOriginalWaveRemainSeparate(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_combat",timeoutTicks=200)
    public static void combat_identityCaptureForgedOwnerAndPortalPickupFailClosed(GameTestHelper h)throws Exception{WeepingSquealGhostCombatGameTests.identityCaptureForgedOwnerAndPortalPickupFailClosed(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_combat",timeoutTicks=160)
    public static void combat_reentrantHurtCannotReplayConsumedProjectile(GameTestHelper h)throws Exception{WeepingSquealGhostCombatGameTests.reentrantHurtCannotReplayConsumedProjectile(h);}
    @AfterBatch(batch="weeping_focus_combat") public static void cleanup_combat(ServerLevel l){WeepingSquealGhostCombatGameTests.cleanup(l);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_native",timeoutTicks=120)
    public static void native_nativeDiveAndPlayerGoalsAreReplacedWithoutChangingWildIdentity(GameTestHelper h)throws Exception{WeepingSquealGhostNativeGameTests.nativeDiveAndPlayerGoalsAreReplacedWithoutChangingWildIdentity(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_native",timeoutTicks=160)
    public static void native_documentedNativeImmunitiesRemainButRealArrowsDamageBoth(GameTestHelper h)throws Exception{WeepingSquealGhostNativeGameTests.documentedNativeImmunitiesRemainButRealArrowsDamageBoth(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_native",timeoutTicks=120)
    public static void native_interactionAndRealDeathCannotCreatePaidLootOrExperience(GameTestHelper h)throws Exception{WeepingSquealGhostNativeGameTests.interactionAndRealDeathCannotCreatePaidLootOrExperience(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_native_peaceful",timeoutTicks=130)
    public static void native_paidPersistenceSurvivesPeacefulWhileOriginalMonsterDespawns(GameTestHelper h)throws Exception{WeepingSquealGhostNativeGameTests.paidPersistenceSurvivesPeacefulWhileOriginalMonsterDespawns(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_native",timeoutTicks=120)
    public static void native_portalsForcedRidingAndLoadedNoAiCannotEscapePaidFlightPolicy(GameTestHelper h)throws Exception{WeepingSquealGhostNativeGameTests.portalsForcedRidingAndLoadedNoAiCannotEscapePaidFlightPolicy(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_native",timeoutTicks=140)
    public static void native_rejectedWildDamageNaturallySpawnsExactlyThreeChildrenAndResetsAtThirty(GameTestHelper h)throws Exception{WeepingSquealGhostNativeGameTests.rejectedWildDamageNaturallySpawnsExactlyThreeChildrenAndResetsAtThirty(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_native",timeoutTicks=150)
    public static void native_removedWildStillSummonsButPaidBlankRestoredRemovedSourcesNeverQueue(GameTestHelper h)throws Exception{WeepingSquealGhostNativeGameTests.removedWildStillSummonsButPaidBlankRestoredRemovedSourcesNeverQueue(h);}
    @GameTest(template="weeping_unit_arena",batch="weeping_focus_native",timeoutTicks=120)
    public static void native_exactStaleResetCallbackNeverWritesPaidScoreButWildStillResets(GameTestHelper h)throws Exception{WeepingSquealGhostNativeGameTests.exactStaleResetCallbackNeverWritesPaidScoreButWildStillResets(h);}
    @AfterBatch(batch="weeping_focus_native") public static void cleanup_native(ServerLevel l){WeepingSquealGhostNativeGameTests.cleanup(l);}
    @AfterBatch(batch="weeping_focus_native_peaceful") public static void cleanup_peaceful(ServerLevel l){WeepingSquealGhostNativeGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="weeping_old_0",timeoutTicks=760)
    public static void old0_exactHallWhitelistAndFullPaidQueueProduceOneLightBoneWing(GameTestHelper h)throws Exception{BoneWingProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneLightBoneWing(h);}
    @GameTest(template="dreamseekers_test_arena",batch="weeping_old_0",timeoutTicks=280)
    public static void old0_blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper h)throws Exception{BoneWingProductionGameTests.blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(h);}
    @GameTest(template="dreamseekers_test_arena",batch="weeping_old_0",timeoutTicks=100)
    public static void old0_populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper h)throws Exception{BoneWingProductionGameTests.populationResourcesAndRepeatedCancellationNeverDuplicate(h);}
    @GameTest(template="dreamseekers_test_arena",batch="weeping_old_0",timeoutTicks=100)
    public static void old0_actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper h)throws Exception{BoneWingProductionGameTests.actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(h);}
    @GameTest(template="dreamseekers_test_arena",batch="weeping_old_0",timeoutTicks=120)
    public static void old0_originalPayerRefundsBeforeTickAndMutableCostsCannotChangeReceipts(GameTestHelper h)throws Exception{BoneWingProductionGameTests.originalPayerRefundsBeforeTickAndMutableCostsCannotChangeReceipts(h);}
    @GameTest(template="dreamseekers_test_arena",batch="weeping_old_0",timeoutTicks=140)
    public static void old0_destructionSettlesOriginalPayerButDetachNeverRefundsOrRevives(GameTestHelper h)throws Exception{BoneWingProductionGameTests.destructionSettlesOriginalPayerButDetachNeverRefundsOrRevives(h);}
    @GameTest(template="dreamseekers_test_arena",batch="weeping_old_0",timeoutTicks=160)
    public static void old0_observedOwnerABAAndExternalQueueClearCannotReuseOldPaidActive(GameTestHelper h)throws Exception{BoneWingProductionGameTests.observedOwnerABAAndExternalQueueClearCannotReuseOldPaidActive(h);}
    @GameTest(template="dreamseekers_test_arena",batch="weeping_old_0",timeoutTicks=160)
    public static void old0_futureSchemaAtCompletionPausesNoScoreAndStillRefundsExactPayer(GameTestHelper h)throws Exception{BoneWingProductionGameTests.futureSchemaAtCompletionPausesNoScoreAndStillRefundsExactPayer(h);}
    @GameTest(template="dreamseekers_test_arena",batch="weeping_old_0",timeoutTicks=220)
    public static void old0_actualJoinEventCancellationCannotConsumePaidQueueOrScore(GameTestHelper h)throws Exception{BoneWingProductionGameTests.actualJoinEventCancellationCannotConsumePaidQueueOrScore(h);}
    @GameTest(template="dreamseekers_test_arena",batch="weeping_old_0",timeoutTicks=220)
    public static void old0_lateJoinCancellationRetiresGhostPopulationAndChunkTicket(GameTestHelper h)throws Exception{BoneWingProductionGameTests.lateJoinCancellationRetiresGhostPopulationAndChunkTicket(h);}
    @GameTest(template="dreamseekers_test_arena",batch="weeping_old_0",timeoutTicks=160)
    public static void old0_savedRefundSnapshotUsesOriginalPayerAndImmutablePayment(GameTestHelper h)throws Exception{BoneWingProductionGameTests.savedRefundSnapshotUsesOriginalPayerAndImmutablePayment(h);}
    @GameTest(template="dreamseekers_test_arena",batch="weeping_old_0",timeoutTicks=160)
    public static void old0_joinCallbackCannotReenterCompletionOrStartAnotherPayment(GameTestHelper h)throws Exception{BoneWingProductionGameTests.joinCallbackCannotReenterCompletionOrStartAnotherPayment(h);}
    @AfterBatch(batch="weeping_old_0") public static void cleanup_old0(ServerLevel l){BoneWingProductionGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="weeping_old_1",timeoutTicks=1350)
    public static void old1_exactHallWhitelistAndFullPaidQueueProduceOnePublicShadowGolem(GameTestHelper h)throws Exception{ShadowGolemProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOnePublicShadowGolem(h);}
    @AfterBatch(batch="weeping_old_1") public static void cleanup_old1(ServerLevel l){ShadowGolemProductionGameTests.cleanup(l);}
    @GameTest(template="squeal_unit_arena",batch="weeping_old_2",timeoutTicks=220)
    public static void old2_physicalGroundFireAndOriginalWaveRemainSeparate(GameTestHelper h)throws Exception{ShadowSquealGhostCombatGameTests.physicalGroundFireAndOriginalWaveRemainSeparate(h);}
    @AfterBatch(batch="weeping_old_2") public static void cleanup_old2(ServerLevel l){ShadowSquealGhostCombatGameTests.cleanup(l);}
    @GameTest(template="friendly_unit_arena",batch="weeping_old_3",timeoutTicks=220)
    public static void old3_physicalGroundFireAndOriginalWaveRemainSeparate(GameTestHelper h)throws Exception{FriendlyGhostCombatGameTests.physicalGroundFireAndOriginalWaveRemainSeparate(h);}
    @AfterBatch(batch="weeping_old_3") public static void cleanup_old3(ServerLevel l){FriendlyGhostCombatGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="weeping_old_4",timeoutTicks=130)
    public static void old4_noRecipeButtonOrForgedFreeQueueForUpgradeFoundation(GameTestHelper h)throws Exception{CrazyTerrorbeakBuildingGameTests.noRecipeButtonOrForgedFreeQueueForUpgradeFoundation(h);}
    @AfterBatch(batch="weeping_old_4") public static void cleanup_old4(ServerLevel l){CrazyTerrorbeakBuildingGameTests.cleanup(l);}
    @GameTest(template="squeal_unit_arena",batch="weeping_old_5",timeoutTicks=160)
    public static void old5_ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(GameTestHelper h)throws Exception{ShadowSquealGhostSafetyGameTests.ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(h);}
    @AfterBatch(batch="weeping_old_5") public static void cleanup_old5(ServerLevel l){ShadowSquealGhostSafetyGameTests.cleanup(l);}
}
