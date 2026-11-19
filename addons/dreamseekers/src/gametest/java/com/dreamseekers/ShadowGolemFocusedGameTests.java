// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
/** Heavy ground foundation + directly affected Hall/area-filter and old variant contracts, not complete faction regression. */
@GameTestHolder("dreamseekers_shadow_golem_focused") @PrefixGameTestTemplate(false)
public final class ShadowGolemFocusedGameTests {
    @GameTest(template="golem_unit_arena",batch="golem_focus_movement",timeoutTicks=720)
    public static void movement_solidBarrierRequiresGroundDetour(GameTestHelper h)throws Exception{ShadowGolemMovementGameTests.solidBarrierRequiresGroundDetour(h);}
    @GameTest(template="golem_unit_arena",batch="golem_focus_movement",timeoutTicks=750)
    public static void movement_bodyNeedsBothWidthAndFourBlockHeadroom(GameTestHelper h)throws Exception{ShadowGolemMovementGameTests.bodyNeedsBothWidthAndFourBlockHeadroom(h);}
    @GameTest(template="golem_unit_arena",batch="golem_focus_movement",timeoutTicks=1080)
    public static void movement_shiftHoldStopAndSameTickAbaKeepFinalIntent(GameTestHelper h)throws Exception{ShadowGolemMovementGameTests.shiftHoldStopAndSameTickAbaKeepFinalIntent(h);}
    @GameTest(template="golem_unit_arena",batch="golem_focus_movement",timeoutTicks=610)
    public static void movement_attackMoveWithoutEnemiesThenShiftUsesGroundController(GameTestHelper h)throws Exception{ShadowGolemMovementGameTests.attackMoveWithoutEnemiesThenShiftUsesGroundController(h);}
    @AfterBatch(batch="golem_focus_movement") public static void cleanup_movement(ServerLevel l){ShadowGolemMovementGameTests.cleanup(l);}
    @GameTest(template="golem_unit_arena",batch="golem_focus_commandauthority",timeoutTicks=80)
    public static void commandauthority_authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(GameTestHelper h)throws Exception{ShadowGolemCommandAuthorityGameTests.authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(h);}
    @GameTest(template="golem_unit_arena",batch="golem_focus_commandauthority",timeoutTicks=80)
    public static void commandauthority_ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(GameTestHelper h)throws Exception{ShadowGolemCommandAuthorityGameTests.ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(h);}
    @GameTest(template="golem_unit_arena",batch="golem_focus_commandauthority",timeoutTicks=80)
    public static void commandauthority_buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(GameTestHelper h)throws Exception{ShadowGolemCommandAuthorityGameTests.buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(h);}
    @AfterBatch(batch="golem_focus_commandauthority") public static void cleanup_commandauthority(ServerLevel l){ShadowGolemCommandAuthorityGameTests.cleanup(l);}
    @GameTest(template="golem_unit_arena",batch="golem_focus_safety",timeoutTicks=80)
    public static void safety_ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(GameTestHelper h)throws Exception{ShadowGolemSafetyGameTests.ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(h);}
    @GameTest(template="golem_unit_arena",batch="golem_focus_safety",timeoutTicks=350)
    public static void safety_sameTargetExplicitAttackReleasesHoldAndAllianceStopsDamage(GameTestHelper h)throws Exception{ShadowGolemSafetyGameTests.sameTargetExplicitAttackReleasesHoldAndAllianceStopsDamage(h);}
    @GameTest(template="golem_unit_arena",batch="golem_focus_safety",timeoutTicks=320)
    public static void safety_holdAttacksOnlyActualMeleeReachWithoutChasing(GameTestHelper h)throws Exception{ShadowGolemSafetyGameTests.holdAttacksOnlyActualMeleeReachWithoutChasing(h);}
    @GameTest(template="golem_unit_arena",batch="golem_focus_safety",timeoutTicks=220)
    public static void safety_followRevocationCancelsOrdinaryPath(GameTestHelper h)throws Exception{ShadowGolemSafetyGameTests.followRevocationCancelsOrdinaryPath(h);}
    @GameTest(template="golem_unit_arena",batch="golem_focus_safety",timeoutTicks=100)
    public static void safety_malformedTargetsAndInteractionsNeverInventEffects(GameTestHelper h)throws Exception{ShadowGolemSafetyGameTests.malformedTargetsAndInteractionsNeverInventEffects(h);}
    @GameTest(template="golem_unit_arena",batch="golem_focus_safety",timeoutTicks=100)
    public static void safety_finalDamageRejectsUnpublishedStunnedFrozenAndOccludedTargets(GameTestHelper h)throws Exception{ShadowGolemSafetyGameTests.finalDamageRejectsUnpublishedStunnedFrozenAndOccludedTargets(h);}
    @AfterBatch(batch="golem_focus_safety") public static void cleanup_safety(ServerLevel l){ShadowGolemSafetyGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="golem_focus_production",timeoutTicks=1350)
    public static void production_exactHallWhitelistAndFullPaidQueueProduceOnePublicShadowGolem(GameTestHelper h)throws Exception{ShadowGolemProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOnePublicShadowGolem(h);}
    @GameTest(template="dreamseekers_test_arena",batch="golem_focus_production",timeoutTicks=280)
    public static void production_blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper h)throws Exception{ShadowGolemProductionGameTests.blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(h);}
    @GameTest(template="dreamseekers_test_arena",batch="golem_focus_production",timeoutTicks=100)
    public static void production_populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper h)throws Exception{ShadowGolemProductionGameTests.populationResourcesAndRepeatedCancellationNeverDuplicate(h);}
    @GameTest(template="dreamseekers_test_arena",batch="golem_focus_production",timeoutTicks=100)
    public static void production_actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper h)throws Exception{ShadowGolemProductionGameTests.actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(h);}
    @GameTest(template="dreamseekers_test_arena",batch="golem_focus_production",timeoutTicks=340)
    public static void production_buildingCombatRejectsFriendlyStaleCrossDimensionAndHoldMovement(GameTestHelper h)throws Exception{ShadowGolemProductionGameTests.buildingCombatRejectsFriendlyStaleCrossDimensionAndHoldMovement(h);}
    @AfterBatch(batch="golem_focus_production") public static void cleanup_production(ServerLevel l){ShadowGolemProductionGameTests.cleanup(l);}
    @GameTest(template="golem_unit_arena",batch="golem_focus_native",timeoutTicks=180)
    public static void native_realWildFortyFourTickAreaHitsCowButNeverControlledCombatWorkerOrNoncombat(GameTestHelper h)throws Exception{ShadowGolemNativeGameTests.realWildFortyFourTickAreaHitsCowButNeverControlledCombatWorkerOrNoncombat(h);}
    @GameTest(template="golem_unit_arena",batch="golem_focus_native",timeoutTicks=160)
    public static void native_paidAndBlankSourceNeverChargeScheduleOrHitEvenWithForgedReadyTimer(GameTestHelper h)throws Exception{ShadowGolemNativeGameTests.paidAndBlankSourceNeverChargeScheduleOrHitEvenWithForgedReadyTimer(h);}
    @GameTest(template="golem_unit_arena",batch="golem_focus_native",timeoutTicks=120)
    public static void native_exactSourceCallbacksRemainInertForRemovedAndRestoredPaidInstances(GameTestHelper h)throws Exception{ShadowGolemNativeGameTests.exactSourceCallbacksRemainInertForRemovedAndRestoredPaidInstances(h);}
    @GameTest(template="golem_unit_arena",batch="golem_focus_native",timeoutTicks=140)
    public static void native_onlyNewArrowDamageIsNormalizedOtherNativeExclusionsAndWildChargeRemain(GameTestHelper h)throws Exception{ShadowGolemNativeGameTests.onlyNewArrowDamageIsNormalizedOtherNativeExclusionsAndWildChargeRemain(h);}
    @GameTest(template="golem_unit_arena",batch="golem_focus_native",timeoutTicks=150)
    public static void native_realDeathDropsOnlyOriginalLootAndExperience(GameTestHelper h)throws Exception{ShadowGolemNativeGameTests.realDeathDropsOnlyOriginalLootAndExperience(h);}
    @GameTest(template="golem_unit_arena",batch="golem_focus_native_peaceful",timeoutTicks=120)
    public static void native_nbtPeacefulAndGoalsKeepActualGroundIdentityWithoutNativeTimer(GameTestHelper h)throws Exception{ShadowGolemNativeGameTests.nbtPeacefulAndGoalsKeepActualGroundIdentityWithoutNativeTimer(h);}
    @GameTest(template="golem_unit_arena",batch="golem_focus_native",timeoutTicks=130)
    public static void native_portalsAndForcedVehiclesCannotTransportPaidUnit(GameTestHelper h)throws Exception{ShadowGolemNativeGameTests.portalsAndForcedVehiclesCannotTransportPaidUnit(h);}
    @GameTest(template="golem_unit_arena",batch="golem_focus_native",timeoutTicks=150)
    public static void native_actualTravelingArrowsDamageOnlyNormalizedRtsVariant(GameTestHelper h)throws Exception{ShadowGolemNativeGameTests.actualTravelingArrowsDamageOnlyNormalizedRtsVariant(h);}
    @AfterBatch(batch="golem_focus_native") public static void cleanup_native(ServerLevel l){ShadowGolemNativeGameTests.cleanup(l);}
    @AfterBatch(batch="golem_focus_native_peaceful") public static void cleanup_peaceful(ServerLevel l){ShadowGolemNativeGameTests.cleanup(l);}
    @GameTest(template="golem_unit_arena",batch="golem_focus_boundary",timeoutTicks=1800)
    public static void boundary_cardinalAndDiagonalMovesReachBothExistingArrivalPredicates(GameTestHelper h)throws Exception{ShadowGolemBoundaryGameTests.cardinalAndDiagonalMovesReachBothExistingArrivalPredicates(h);}
    @GameTest(template="golem_unit_arena",batch="golem_focus_boundary",timeoutTicks=450)
    public static void boundary_realDelayedRecomputeAndStopNeverReviveLastNodeButNewMoveStillWorks(GameTestHelper h)throws Exception{ShadowGolemBoundaryGameTests.realDelayedRecomputeAndStopNeverReviveLastNodeButNewMoveStillWorks(h);}
    @AfterBatch(batch="golem_focus_boundary") public static void cleanup_boundary(ServerLevel l){ShadowGolemBoundaryGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="golem_old_0",timeoutTicks=760)
    public static void old0_exactHallWhitelistAndFullPaidQueueProduceOneLightBoneWing(GameTestHelper h)throws Exception{BoneWingProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneLightBoneWing(h);}
    @GameTest(template="dreamseekers_test_arena",batch="golem_old_0",timeoutTicks=280)
    public static void old0_blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper h)throws Exception{BoneWingProductionGameTests.blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(h);}
    @GameTest(template="dreamseekers_test_arena",batch="golem_old_0",timeoutTicks=100)
    public static void old0_populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper h)throws Exception{BoneWingProductionGameTests.populationResourcesAndRepeatedCancellationNeverDuplicate(h);}
    @GameTest(template="dreamseekers_test_arena",batch="golem_old_0",timeoutTicks=100)
    public static void old0_actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper h)throws Exception{BoneWingProductionGameTests.actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(h);}
    @GameTest(template="dreamseekers_test_arena",batch="golem_old_0",timeoutTicks=120)
    public static void old0_originalPayerRefundsBeforeTickAndMutableCostsCannotChangeReceipts(GameTestHelper h)throws Exception{BoneWingProductionGameTests.originalPayerRefundsBeforeTickAndMutableCostsCannotChangeReceipts(h);}
    @GameTest(template="dreamseekers_test_arena",batch="golem_old_0",timeoutTicks=140)
    public static void old0_destructionSettlesOriginalPayerButDetachNeverRefundsOrRevives(GameTestHelper h)throws Exception{BoneWingProductionGameTests.destructionSettlesOriginalPayerButDetachNeverRefundsOrRevives(h);}
    @GameTest(template="dreamseekers_test_arena",batch="golem_old_0",timeoutTicks=160)
    public static void old0_observedOwnerABAAndExternalQueueClearCannotReuseOldPaidActive(GameTestHelper h)throws Exception{BoneWingProductionGameTests.observedOwnerABAAndExternalQueueClearCannotReuseOldPaidActive(h);}
    @GameTest(template="dreamseekers_test_arena",batch="golem_old_0",timeoutTicks=160)
    public static void old0_futureSchemaAtCompletionPausesNoScoreAndStillRefundsExactPayer(GameTestHelper h)throws Exception{BoneWingProductionGameTests.futureSchemaAtCompletionPausesNoScoreAndStillRefundsExactPayer(h);}
    @GameTest(template="dreamseekers_test_arena",batch="golem_old_0",timeoutTicks=220)
    public static void old0_actualJoinEventCancellationCannotConsumePaidQueueOrScore(GameTestHelper h)throws Exception{BoneWingProductionGameTests.actualJoinEventCancellationCannotConsumePaidQueueOrScore(h);}
    @GameTest(template="dreamseekers_test_arena",batch="golem_old_0",timeoutTicks=220)
    public static void old0_lateJoinCancellationRetiresGhostPopulationAndChunkTicket(GameTestHelper h)throws Exception{BoneWingProductionGameTests.lateJoinCancellationRetiresGhostPopulationAndChunkTicket(h);}
    @GameTest(template="dreamseekers_test_arena",batch="golem_old_0",timeoutTicks=160)
    public static void old0_savedRefundSnapshotUsesOriginalPayerAndImmutablePayment(GameTestHelper h)throws Exception{BoneWingProductionGameTests.savedRefundSnapshotUsesOriginalPayerAndImmutablePayment(h);}
    @GameTest(template="dreamseekers_test_arena",batch="golem_old_0",timeoutTicks=160)
    public static void old0_joinCallbackCannotReenterCompletionOrStartAnotherPayment(GameTestHelper h)throws Exception{BoneWingProductionGameTests.joinCallbackCannotReenterCompletionOrStartAnotherPayment(h);}
    @AfterBatch(batch="golem_old_0") public static void cleanup_old0(ServerLevel l){BoneWingProductionGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="golem_old_1",timeoutTicks=850)
    public static void old1_exactHallWhitelistAndFullPaidQueueProduceOneAshBoneWing(GameTestHelper h)throws Exception{AshBoneWingProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOneAshBoneWing(h);}
    @AfterBatch(batch="golem_old_1") public static void cleanup_old1(ServerLevel l){AshBoneWingProductionGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="golem_old_2",timeoutTicks=130)
    public static void old2_noRecipeButtonOrForgedFreeQueueForUpgradeFoundation(GameTestHelper h)throws Exception{CrazyTerrorbeakBuildingGameTests.noRecipeButtonOrForgedFreeQueueForUpgradeFoundation(h);}
    @AfterBatch(batch="golem_old_2") public static void cleanup_old2(ServerLevel l){CrazyTerrorbeakBuildingGameTests.cleanup(l);}
    @GameTest(template="crazy_unit_arena",batch="golem_old_3",timeoutTicks=100)
    public static void old3_disconnectedFakePlayerUpgradeNeverChargesOrReplaces(GameTestHelper h)throws Exception{CrazyTerrorbeakUpgradeGameTests.disconnectedFakePlayerUpgradeNeverChargesOrReplaces(h);}
    @AfterBatch(batch="golem_old_3") public static void cleanup_old3(ServerLevel l){CrazyTerrorbeakUpgradeGameTests.cleanup(l);}
    @GameTest(template="ash_unit_arena",batch="golem_old_4",timeoutTicks=160)
    public static void old4_ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(GameTestHelper h)throws Exception{AshBoneWingSafetyGameTests.ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(h);}
    @AfterBatch(batch="golem_old_4") public static void cleanup_old4(ServerLevel l){AshBoneWingSafetyGameTests.cleanup(l);}
}
