// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
/** Friendly foundation plus affected Hall whitelist, no full-faction claim. */
@GameTestHolder("dreamseekers_friendly_ghost_focused") @PrefixGameTestTemplate(false)
public final class FriendlyGhostFocusedGameTests {
    @GameTest(template="friendly_unit_arena",batch="friendly_flight",timeoutTicks=1120)
    public static void flight_queuedMoveThenHoldThenStopCannotResumeStaleFlight(GameTestHelper h)throws Exception{FriendlyGhostFlightGameTests.queuedMoveThenHoldThenStopCannotResumeStaleFlight(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_flight",timeoutTicks=1060)
    public static void flight_sameTickABAndABAKeepFinalFlightDestination(GameTestHelper h)throws Exception{FriendlyGhostFlightGameTests.sameTickABAndABAKeepFinalFlightDestination(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_flight",timeoutTicks=920)
    public static void flight_tallBodyCannotFitOneHighSlotButFitsOpenedTwoHighSlot(GameTestHelper h)throws Exception{FriendlyGhostFlightGameTests.tallBodyCannotFitOneHighSlotButFitsOpenedTwoHighSlot(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_flight",timeoutTicks=500)
    public static void flight_malformedItemBoatMissingAndRemovedTargetsNeverBecomeFollowOrDamage(GameTestHelper h)throws Exception{FriendlyGhostFlightGameTests.malformedItemBoatMissingAndRemovedTargetsNeverBecomeFollowOrDamage(h);}
    @AfterBatch(batch="friendly_flight") public static void cleanup_flight(ServerLevel l){FriendlyGhostFlightGameTests.cleanup(l);}
    @GameTest(template="friendly_unit_arena",batch="friendly_commandauthority",timeoutTicks=80)
    public static void commandauthority_authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(GameTestHelper h)throws Exception{FriendlyGhostCommandAuthorityGameTests.authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_commandauthority",timeoutTicks=80)
    public static void commandauthority_ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(GameTestHelper h)throws Exception{FriendlyGhostCommandAuthorityGameTests.ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_commandauthority",timeoutTicks=80)
    public static void commandauthority_buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(GameTestHelper h)throws Exception{FriendlyGhostCommandAuthorityGameTests.buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(h);}
    @AfterBatch(batch="friendly_commandauthority") public static void cleanup_commandauthority(ServerLevel l){FriendlyGhostCommandAuthorityGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="friendly_production",timeoutTicks=850)
    public static void production_exactHallWhitelistAndFullPaidQueueProduceOneFriendlyGhost(GameTestHelper h)throws Exception{FriendlyGhostProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneFriendlyGhost(h);}
    @GameTest(template="dreamseekers_test_arena",batch="friendly_production",timeoutTicks=280)
    public static void production_blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper h)throws Exception{FriendlyGhostProductionGameTests.blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(h);}
    @GameTest(template="dreamseekers_test_arena",batch="friendly_production",timeoutTicks=100)
    public static void production_populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper h)throws Exception{FriendlyGhostProductionGameTests.populationResourcesAndRepeatedCancellationNeverDuplicate(h);}
    @GameTest(template="dreamseekers_test_arena",batch="friendly_production",timeoutTicks=100)
    public static void production_actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper h)throws Exception{FriendlyGhostProductionGameTests.actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(h);}
    @GameTest(template="dreamseekers_test_arena",batch="friendly_production",timeoutTicks=180)
    public static void production_rangedUnitCannotAcquireOrDamageBuildings(GameTestHelper h)throws Exception{FriendlyGhostProductionGameTests.rangedUnitCannotAcquireOrDamageBuildings(h);}
    @AfterBatch(batch="friendly_production") public static void cleanup_production(ServerLevel l){FriendlyGhostProductionGameTests.cleanup(l);}
    @GameTest(template="friendly_unit_arena",batch="friendly_safety",timeoutTicks=80)
    public static void safety_ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(GameTestHelper h)throws Exception{FriendlyGhostSafetyGameTests.ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_safety",timeoutTicks=220)
    public static void safety_friendlyFollowMovesAndRevokedAllianceCancelsItsPath(GameTestHelper h)throws Exception{FriendlyGhostSafetyGameTests.friendlyFollowMovesAndRevokedAllianceCancelsItsPath(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_safety",timeoutTicks=220)
    public static void safety_revokedFollowAllowsNewHostileAutoAggroWithoutRevivingOldCommand(GameTestHelper h)throws Exception{FriendlyGhostSafetyGameTests.revokedFollowAllowsNewHostileAutoAggroWithoutRevivingOldCommand(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_safety",timeoutTicks=460)
    public static void safety_sameEnemyExplicitAttackReleasesHoldAndReallyPursues(GameTestHelper h)throws Exception{FriendlyGhostSafetyGameTests.sameEnemyExplicitAttackReleasesHoldAndReallyPursues(h);}
    @AfterBatch(batch="friendly_safety") public static void cleanup_safety(ServerLevel l){FriendlyGhostSafetyGameTests.cleanup(l);}
    @GameTest(template="friendly_unit_arena",batch="friendly_combat",timeoutTicks=520)
    public static void combat_realRangedGoalFiresPhysicalBoltAtAirEnemyWithoutFriendlyCollateral(GameTestHelper h)throws Exception{FriendlyGhostCombatGameTests.realRangedGoalFiresPhysicalBoltAtAirEnemyWithoutFriendlyCollateral(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_combat",timeoutTicks=280)
    public static void combat_allianceAndOwnershipChangesInvalidateInFlightButStopDoesNot(GameTestHelper h)throws Exception{FriendlyGhostCombatGameTests.allianceAndOwnershipChangesInvalidateInFlightButStopDoesNot(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_combat",timeoutTicks=260)
    public static void combat_freshLineOfSightRangeAndDisableEffectsGateEveryRelease(GameTestHelper h)throws Exception{FriendlyGhostCombatGameTests.freshLineOfSightRangeAndDisableEffectsGateEveryRelease(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_combat",timeoutTicks=180)
    public static void combat_spentInvalidAndReloadedProjectilesCannotBeRearmedOrDamageTwice(GameTestHelper h)throws Exception{FriendlyGhostCombatGameTests.spentInvalidAndReloadedProjectilesCannotBeRearmedOrDamageTwice(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_combat",timeoutTicks=280)
    public static void combat_lastWindupStunCannotLeakNewProjectileAndLaterReleaseRecovers(GameTestHelper h)throws Exception{FriendlyGhostCombatGameTests.lastWindupStunCannotLeakNewProjectileAndLaterReleaseRecovers(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_combat",timeoutTicks=280)
    public static void combat_stopAndSameTickRetargetResetRealRangedWindupWithoutCooldownReset(GameTestHelper h)throws Exception{FriendlyGhostCombatGameTests.stopAndSameTickRetargetResetRealRangedWindupWithoutCooldownReset(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_combat",timeoutTicks=220)
    public static void combat_physicalGroundFireAndOriginalWaveRemainSeparate(GameTestHelper h)throws Exception{FriendlyGhostCombatGameTests.physicalGroundFireAndOriginalWaveRemainSeparate(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_combat",timeoutTicks=200)
    public static void combat_identityCaptureForgedOwnerAndPortalPickupFailClosed(GameTestHelper h)throws Exception{FriendlyGhostCombatGameTests.identityCaptureForgedOwnerAndPortalPickupFailClosed(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_combat",timeoutTicks=160)
    public static void combat_reentrantHurtCannotReplayConsumedProjectile(GameTestHelper h)throws Exception{FriendlyGhostCombatGameTests.reentrantHurtCannotReplayConsumedProjectile(h);}
    @AfterBatch(batch="friendly_combat") public static void cleanup_combat(ServerLevel l){FriendlyGhostCombatGameTests.cleanup(l);}
    @GameTest(template="friendly_unit_arena",batch="friendly_native",timeoutTicks=120)
    public static void native_nativeOwnerAndRandomGoalsAreReplacedWithoutChangingWildIdentity(GameTestHelper h)throws Exception{FriendlyGhostNativeGameTests.nativeOwnerAndRandomGoalsAreReplacedWithoutChangingWildIdentity(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_native",timeoutTicks=160)
    public static void native_documentedNativeImmunitiesRemainButRealArrowsDamageBoth(GameTestHelper h)throws Exception{FriendlyGhostNativeGameTests.documentedNativeImmunitiesRemainButRealArrowsDamageBoth(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_native",timeoutTicks=120)
    public static void native_interactionAndRealDeathCannotCreatePaidLootOrExperience(GameTestHelper h)throws Exception{FriendlyGhostNativeGameTests.interactionAndRealDeathCannotCreatePaidLootOrExperience(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_native_peaceful",timeoutTicks=130)
    public static void native_paidAndOriginalAnimalPersistInPeacefulWithoutConversion(GameTestHelper h)throws Exception{FriendlyGhostNativeGameTests.paidAndOriginalAnimalPersistInPeacefulWithoutConversion(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_native",timeoutTicks=120)
    public static void native_portalsForcedRidingAndLoadedNoAiCannotEscapePaidFlightPolicy(GameTestHelper h)throws Exception{FriendlyGhostNativeGameTests.portalsForcedRidingAndLoadedNoAiCannotEscapePaidFlightPolicy(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_native",timeoutTicks=100)
    public static void native_directTameAndHostilePetNbtCannotReplaceRtsOwnerOrEnableBreeding(GameTestHelper h)throws Exception{FriendlyGhostNativeGameTests.directTameAndHostilePetNbtCannotReplaceRtsOwnerOrEnableBreeding(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_native",timeoutTicks=130)
    public static void native_originalWandImmediateAreaExcludesPaidButRetainsWildMagicDamage(GameTestHelper h)throws Exception{FriendlyGhostNativeGameTests.originalWandImmediateAreaExcludesPaidButRetainsWildMagicDamage(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_native",timeoutTicks=150)
    public static void native_originalOneTickWandCallbackSpawnsAndTamesWildButNotPaid(GameTestHelper h)throws Exception{FriendlyGhostNativeGameTests.originalOneTickWandCallbackSpawnsAndTamesWildButNotPaid(h);}
    @AfterBatch(batch="friendly_native") public static void cleanup_native(ServerLevel l){FriendlyGhostNativeGameTests.cleanup(l);}
    @AfterBatch(batch="friendly_native_peaceful") public static void cleanup_peaceful(ServerLevel l){FriendlyGhostNativeGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="friendly_oldhall",timeoutTicks=760)
    public static void oldhall_exactHallWhitelistAndFullPaidQueueProduceOneLightBoneWing(GameTestHelper h)throws Exception{BoneWingProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneLightBoneWing(h);}
    @GameTest(template="dreamseekers_test_arena",batch="friendly_oldhall",timeoutTicks=280)
    public static void oldhall_blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper h)throws Exception{BoneWingProductionGameTests.blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(h);}
    @GameTest(template="dreamseekers_test_arena",batch="friendly_oldhall",timeoutTicks=100)
    public static void oldhall_populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper h)throws Exception{BoneWingProductionGameTests.populationResourcesAndRepeatedCancellationNeverDuplicate(h);}
    @GameTest(template="dreamseekers_test_arena",batch="friendly_oldhall",timeoutTicks=100)
    public static void oldhall_actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper h)throws Exception{BoneWingProductionGameTests.actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(h);}
    @GameTest(template="dreamseekers_test_arena",batch="friendly_oldhall",timeoutTicks=120)
    public static void oldhall_originalPayerRefundsBeforeTickAndMutableCostsCannotChangeReceipts(GameTestHelper h)throws Exception{BoneWingProductionGameTests.originalPayerRefundsBeforeTickAndMutableCostsCannotChangeReceipts(h);}
    @GameTest(template="dreamseekers_test_arena",batch="friendly_oldhall",timeoutTicks=140)
    public static void oldhall_destructionSettlesOriginalPayerButDetachNeverRefundsOrRevives(GameTestHelper h)throws Exception{BoneWingProductionGameTests.destructionSettlesOriginalPayerButDetachNeverRefundsOrRevives(h);}
    @GameTest(template="dreamseekers_test_arena",batch="friendly_oldhall",timeoutTicks=160)
    public static void oldhall_observedOwnerABAAndExternalQueueClearCannotReuseOldPaidActive(GameTestHelper h)throws Exception{BoneWingProductionGameTests.observedOwnerABAAndExternalQueueClearCannotReuseOldPaidActive(h);}
    @GameTest(template="dreamseekers_test_arena",batch="friendly_oldhall",timeoutTicks=160)
    public static void oldhall_futureSchemaAtCompletionPausesNoScoreAndStillRefundsExactPayer(GameTestHelper h)throws Exception{BoneWingProductionGameTests.futureSchemaAtCompletionPausesNoScoreAndStillRefundsExactPayer(h);}
    @GameTest(template="dreamseekers_test_arena",batch="friendly_oldhall",timeoutTicks=220)
    public static void oldhall_actualJoinEventCancellationCannotConsumePaidQueueOrScore(GameTestHelper h)throws Exception{BoneWingProductionGameTests.actualJoinEventCancellationCannotConsumePaidQueueOrScore(h);}
    @GameTest(template="dreamseekers_test_arena",batch="friendly_oldhall",timeoutTicks=220)
    public static void oldhall_lateJoinCancellationRetiresGhostPopulationAndChunkTicket(GameTestHelper h)throws Exception{BoneWingProductionGameTests.lateJoinCancellationRetiresGhostPopulationAndChunkTicket(h);}
    @GameTest(template="dreamseekers_test_arena",batch="friendly_oldhall",timeoutTicks=160)
    public static void oldhall_savedRefundSnapshotUsesOriginalPayerAndImmutablePayment(GameTestHelper h)throws Exception{BoneWingProductionGameTests.savedRefundSnapshotUsesOriginalPayerAndImmutablePayment(h);}
    @GameTest(template="dreamseekers_test_arena",batch="friendly_oldhall",timeoutTicks=160)
    public static void oldhall_joinCallbackCannotReenterCompletionOrStartAnotherPayment(GameTestHelper h)throws Exception{BoneWingProductionGameTests.joinCallbackCannotReenterCompletionOrStartAnotherPayment(h);}
    @GameTest(template="dreamseekers_test_arena",batch="friendly_registry",timeoutTicks=100)
    public static void registry_allEightRegisteredFactionFactoriesPaidRotationsAndCancel(GameTestHelper h)throws Exception{ScaffoldBuildingGameTests.allEightRegisteredFactionFactoriesPaidRotationsAndCancel(h);}
    @GameTest(template="ghost_unit_arena",batch="friendly_oldghostskill",timeoutTicks=80)
    public static void oldghostskill_ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(GameTestHelper h)throws Exception{ShadowGhostSafetyGameTests.ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(h);}
    @GameTest(template="dreamseekers_test_arena",batch="friendly_oldghostprod",timeoutTicks=750)
    public static void oldghostprod_exactHallWhitelistAndFullPaidQueueProduceOneShadowGhost(GameTestHelper h)throws Exception{ShadowGhostProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneShadowGhost(h);}
    @AfterBatch(batch="friendly_oldhall") public static void cleanup_oldhall(ServerLevel l){BoneWingProductionGameTests.cleanup(l);}
    @AfterBatch(batch="friendly_registry") public static void cleanup_registry(ServerLevel l){ScaffoldBuildingGameTests.cleanup(l);}
    @AfterBatch(batch="friendly_oldghostskill") public static void cleanup_oldghostskill(ServerLevel l){ShadowGhostSafetyGameTests.cleanup(l);}
    @AfterBatch(batch="friendly_oldghostprod") public static void cleanup_oldghostprod(ServerLevel l){ShadowGhostProductionGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="friendly_oldterror",timeoutTicks=1150)
    public static void oldterror_paidHallQueue(GameTestHelper h){TerrorbeakProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOnePublicTerrorbeak(h);}
    @AfterBatch(batch="friendly_oldterror") public static void cleanup_oldterror(ServerLevel l){TerrorbeakProductionGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="friendly_oldsqueal",timeoutTicks=850)
    public static void oldsqueal_paidHallQueue(GameTestHelper h){ShadowSquealGhostProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneShadowSquealGhost(h);}
    @AfterBatch(batch="friendly_oldsqueal") public static void cleanup_oldsqueal(ServerLevel l){ShadowSquealGhostProductionGameTests.cleanup(l);}
    @GameTest(template="friendly_unit_arena",batch="friendly_flight",timeoutTicks=740)
    public static void flight_genuinelyFliesOverTwoBlockBarrierWithoutCollision(GameTestHelper h){FriendlyGhostFlightGameTests.genuinelyFliesOverTwoBlockBarrierWithoutCollision(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_flight",timeoutTicks=820)
    public static void flight_sealedWallsCannotBeCrossedOrHarvestedThrough(GameTestHelper h){FriendlyGhostFlightGameTests.sealedWallsCannotBeCrossedOrHarvestedThrough(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_flight",timeoutTicks=730)
    public static void flight_stopAndReplacementOrdersCancelFlightWithoutWildWandering(GameTestHelper h){FriendlyGhostFlightGameTests.stopAndReplacementOrdersCancelFlightWithoutWildWandering(h);}
    @GameTest(template="friendly_unit_arena",batch="friendly_flight",timeoutTicks=890)
    public static void flight_lowFlightCeilingDoesNotRatchetAndRetriesWhenOpeningAppears(GameTestHelper h){FriendlyGhostFlightGameTests.lowFlightCeilingDoesNotRatchetAndRetriesWhenOpeningAppears(h);}
}
