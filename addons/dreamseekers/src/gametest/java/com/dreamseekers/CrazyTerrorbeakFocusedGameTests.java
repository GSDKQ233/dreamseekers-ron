// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
/** Upgrade-branch foundation, negative production and narrowly affected old variant boundaries; not full faction regression. */
@GameTestHolder("dreamseekers_crazy_terrorbeak_focused") @PrefixGameTestTemplate(false)
public final class CrazyTerrorbeakFocusedGameTests {
    @GameTest(template="crazy_unit_arena",batch="crazy_movement",timeoutTicks=600)
    public static void movement_solidBarrierRequiresGroundDetour(GameTestHelper h)throws Exception{CrazyTerrorbeakMovementGameTests.solidBarrierRequiresGroundDetour(h);}
    @GameTest(template="crazy_unit_arena",batch="crazy_movement",timeoutTicks=650)
    public static void movement_bodyNeedsBothWidthAndFourBlockHeadroom(GameTestHelper h)throws Exception{CrazyTerrorbeakMovementGameTests.bodyNeedsBothWidthAndFourBlockHeadroom(h);}
    @GameTest(template="crazy_unit_arena",batch="crazy_movement",timeoutTicks=900)
    public static void movement_shiftHoldStopAndSameTickAbaKeepFinalIntent(GameTestHelper h)throws Exception{CrazyTerrorbeakMovementGameTests.shiftHoldStopAndSameTickAbaKeepFinalIntent(h);}
    @GameTest(template="crazy_unit_arena",batch="crazy_movement",timeoutTicks=500)
    public static void movement_attackMoveWithoutEnemiesThenShiftUsesGroundController(GameTestHelper h)throws Exception{CrazyTerrorbeakMovementGameTests.attackMoveWithoutEnemiesThenShiftUsesGroundController(h);}
    @AfterBatch(batch="crazy_movement") public static void cleanup_movement(ServerLevel l){CrazyTerrorbeakMovementGameTests.cleanup(l);}
    @GameTest(template="crazy_unit_arena",batch="crazy_commandauthority",timeoutTicks=80)
    public static void commandauthority_authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(GameTestHelper h)throws Exception{CrazyTerrorbeakCommandAuthorityGameTests.authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(h);}
    @GameTest(template="crazy_unit_arena",batch="crazy_commandauthority",timeoutTicks=80)
    public static void commandauthority_ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(GameTestHelper h)throws Exception{CrazyTerrorbeakCommandAuthorityGameTests.ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(h);}
    @GameTest(template="crazy_unit_arena",batch="crazy_commandauthority",timeoutTicks=80)
    public static void commandauthority_buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(GameTestHelper h)throws Exception{CrazyTerrorbeakCommandAuthorityGameTests.buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(h);}
    @AfterBatch(batch="crazy_commandauthority") public static void cleanup_commandauthority(ServerLevel l){CrazyTerrorbeakCommandAuthorityGameTests.cleanup(l);}
    @GameTest(template="crazy_unit_arena",batch="crazy_safety",timeoutTicks=80)
    public static void safety_ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(GameTestHelper h)throws Exception{CrazyTerrorbeakSafetyGameTests.ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(h);}
    @GameTest(template="crazy_unit_arena",batch="crazy_safety",timeoutTicks=350)
    public static void safety_sameTargetExplicitAttackReleasesHoldAndAllianceStopsDamage(GameTestHelper h)throws Exception{CrazyTerrorbeakSafetyGameTests.sameTargetExplicitAttackReleasesHoldAndAllianceStopsDamage(h);}
    @GameTest(template="crazy_unit_arena",batch="crazy_safety",timeoutTicks=320)
    public static void safety_holdAttacksOnlyActualMeleeReachWithoutChasing(GameTestHelper h)throws Exception{CrazyTerrorbeakSafetyGameTests.holdAttacksOnlyActualMeleeReachWithoutChasing(h);}
    @GameTest(template="crazy_unit_arena",batch="crazy_safety",timeoutTicks=220)
    public static void safety_followRevocationCancelsOrdinaryPath(GameTestHelper h)throws Exception{CrazyTerrorbeakSafetyGameTests.followRevocationCancelsOrdinaryPath(h);}
    @GameTest(template="crazy_unit_arena",batch="crazy_safety",timeoutTicks=100)
    public static void safety_malformedTargetsAndInteractionsNeverInventEffects(GameTestHelper h)throws Exception{CrazyTerrorbeakSafetyGameTests.malformedTargetsAndInteractionsNeverInventEffects(h);}
    @GameTest(template="crazy_unit_arena",batch="crazy_safety",timeoutTicks=100)
    public static void safety_finalDamageRejectsUnpublishedStunnedFrozenAndOccludedTargets(GameTestHelper h)throws Exception{CrazyTerrorbeakSafetyGameTests.finalDamageRejectsUnpublishedStunnedFrozenAndOccludedTargets(h);}
    @AfterBatch(batch="crazy_safety") public static void cleanup_safety(ServerLevel l){CrazyTerrorbeakSafetyGameTests.cleanup(l);}
    @GameTest(template="crazy_unit_arena",batch="crazy_native",timeoutTicks=310)
    public static void native_rejectedDamageStillRoarsForWildButPaidNeverSchedules(GameTestHelper h)throws Exception{CrazyTerrorbeakNativeGameTests.rejectedDamageStillRoarsForWildButPaidNeverSchedules(h);}
    @GameTest(template="crazy_unit_arena",batch="crazy_native",timeoutTicks=180)
    public static void native_contactAndActualDeathRewardKeepWildSanPositive(GameTestHelper h)throws Exception{CrazyTerrorbeakNativeGameTests.contactAndActualDeathRewardKeepWildSanPositive(h);}
    @GameTest(template="crazy_unit_arena",batch="crazy_native",timeoutTicks=160)
    public static void native_wildHelpGoalCannotReplaceRtsSelectionOrMove(GameTestHelper h)throws Exception{CrazyTerrorbeakNativeGameTests.wildHelpGoalCannotReplaceRtsSelectionOrMove(h);}
    @GameTest(template="crazy_unit_arena",batch="crazy_native_peaceful",timeoutTicks=160)
    public static void native_nbtBodyAndPeacefulRemainPaidGroundIdentity(GameTestHelper h)throws Exception{CrazyTerrorbeakNativeGameTests.nbtBodyAndPeacefulRemainPaidGroundIdentity(h);}
    @GameTest(template="crazy_unit_arena",batch="crazy_native",timeoutTicks=130)
    public static void native_portalsAndForcedVehiclesCannotTransportPaidUnit(GameTestHelper h)throws Exception{CrazyTerrorbeakNativeGameTests.portalsAndForcedVehiclesCannotTransportPaidUnit(h);}
    @GameTest(template="crazy_unit_arena",batch="crazy_native",timeoutTicks=130)
    public static void native_documentedNativeImmunitiesDoNotInventGhostDamageImmunity(GameTestHelper h)throws Exception{CrazyTerrorbeakNativeGameTests.documentedNativeImmunitiesDoNotInventGhostDamageImmunity(h);}
    @GameTest(template="crazy_unit_arena",batch="crazy_native",timeoutTicks=100)
    public static void native_exactDelayedCallbackRejectsPaidEvenAfterOwnerChanges(GameTestHelper h)throws Exception{CrazyTerrorbeakNativeGameTests.exactDelayedCallbackRejectsPaidEvenAfterOwnerChanges(h);}
    @AfterBatch(batch="crazy_native") public static void cleanup_native(ServerLevel l){CrazyTerrorbeakNativeGameTests.cleanup(l);}
    @AfterBatch(batch="crazy_native_peaceful") public static void cleanup_peaceful(ServerLevel l){CrazyTerrorbeakNativeGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="crazy_building",timeoutTicks=340)
    public static void building_buildingCombatRejectsFriendlyStaleCrossDimensionAndHoldMovement(GameTestHelper h)throws Exception{CrazyTerrorbeakBuildingGameTests.buildingCombatRejectsFriendlyStaleCrossDimensionAndHoldMovement(h);}
    @GameTest(template="dreamseekers_test_arena",batch="crazy_building",timeoutTicks=130)
    public static void building_noRecipeButtonOrForgedFreeQueueForUpgradeFoundation(GameTestHelper h)throws Exception{CrazyTerrorbeakBuildingGameTests.noRecipeButtonOrForgedFreeQueueForUpgradeFoundation(h);}
    @AfterBatch(batch="crazy_building") public static void cleanup_building(ServerLevel l){CrazyTerrorbeakBuildingGameTests.cleanup(l);}
    @GameTest(template="crazy_unit_arena",batch="crazy_upgrade",timeoutTicks=100)
    public static void upgrade_disconnectedFakePlayerUpgradeNeverChargesOrReplaces(GameTestHelper h)throws Exception{CrazyTerrorbeakUpgradeGameTests.disconnectedFakePlayerUpgradeNeverChargesOrReplaces(h);}
    @GameTest(template="crazy_unit_arena",batch="crazy_upgrade",timeoutTicks=100)
    public static void upgrade_malformedIdentitySchemaDimensionAndWrongSubtypeFailClosed(GameTestHelper h)throws Exception{CrazyTerrorbeakUpgradeGameTests.malformedIdentitySchemaDimensionAndWrongSubtypeFailClosed(h);}
    @GameTest(template="crazy_unit_arena",batch="crazy_upgrade",timeoutTicks=100)
    public static void upgrade_currentOwnerNotFactionOrAllianceAndObservedAbaBoundAuthority(GameTestHelper h)throws Exception{CrazyTerrorbeakUpgradeGameTests.currentOwnerNotFactionOrAllianceAndObservedAbaBoundAuthority(h);}
    @GameTest(template="crazy_unit_arena",batch="crazy_upgrade",timeoutTicks=100)
    public static void upgrade_nbtFlagsCannotEnableProviderOrPromoteOriginalSource(GameTestHelper h)throws Exception{CrazyTerrorbeakUpgradeGameTests.nbtFlagsCannotEnableProviderOrPromoteOriginalSource(h);}
    @AfterBatch(batch="crazy_upgrade") public static void cleanup_upgrade(ServerLevel l){CrazyTerrorbeakUpgradeGameTests.cleanup(l);}
    @GameTest(template="terror_unit_arena",batch="crazy_old_0",timeoutTicks=310)
    public static void old0_rejectedDamageStillRoarsForWildButPaidNeverSchedules(GameTestHelper h)throws Exception{TerrorbeakNativeGameTests.rejectedDamageStillRoarsForWildButPaidNeverSchedules(h);}
    @GameTest(template="terror_unit_arena",batch="crazy_old_0",timeoutTicks=180)
    public static void old0_contactAndActualDeathRewardKeepWildSanPositive(GameTestHelper h)throws Exception{TerrorbeakNativeGameTests.contactAndActualDeathRewardKeepWildSanPositive(h);}
    @GameTest(template="terror_unit_arena",batch="crazy_old_0",timeoutTicks=160)
    public static void old0_wildHelpGoalCannotReplaceRtsSelectionOrMove(GameTestHelper h)throws Exception{TerrorbeakNativeGameTests.wildHelpGoalCannotReplaceRtsSelectionOrMove(h);}
    @GameTest(template="terror_unit_arena",batch="crazy_old_0_peaceful",timeoutTicks=160)
    public static void old0_nbtBodyAndPeacefulRemainPaidGroundIdentity(GameTestHelper h)throws Exception{TerrorbeakNativeGameTests.nbtBodyAndPeacefulRemainPaidGroundIdentity(h);}
    @GameTest(template="terror_unit_arena",batch="crazy_old_0",timeoutTicks=130)
    public static void old0_portalsAndForcedVehiclesCannotTransportPaidUnit(GameTestHelper h)throws Exception{TerrorbeakNativeGameTests.portalsAndForcedVehiclesCannotTransportPaidUnit(h);}
    @AfterBatch(batch="crazy_old_0") public static void cleanup_old0(ServerLevel l){TerrorbeakNativeGameTests.cleanup(l);}
    @AfterBatch(batch="crazy_old_0_peaceful") public static void cleanup_oldpeaceful0(ServerLevel l){TerrorbeakNativeGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="crazy_old_1",timeoutTicks=1150)
    public static void old1_exactHallWhitelistAndFullPaidQueueProduceOnePublicTerrorbeak(GameTestHelper h)throws Exception{TerrorbeakProductionGameTests.exactHallWhitelistAndFullPaidQueueProduceOnePublicTerrorbeak(h);}
    @AfterBatch(batch="crazy_old_1") public static void cleanup_old1(ServerLevel l){TerrorbeakProductionGameTests.cleanup(l);}
    @GameTest(template="terror_unit_arena",batch="crazy_old_2",timeoutTicks=350)
    public static void old2_sameTargetExplicitAttackReleasesHoldAndAllianceStopsDamage(GameTestHelper h)throws Exception{TerrorbeakSafetyGameTests.sameTargetExplicitAttackReleasesHoldAndAllianceStopsDamage(h);}
    @AfterBatch(batch="crazy_old_2") public static void cleanup_old2(ServerLevel l){TerrorbeakSafetyGameTests.cleanup(l);}
    @GameTest(template="ash_unit_arena",batch="crazy_old_3",timeoutTicks=160)
    public static void old3_ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(GameTestHelper h)throws Exception{AshBoneWingSafetyGameTests.ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(h);}
    @AfterBatch(batch="crazy_old_3") public static void cleanup_old3(ServerLevel l){AshBoneWingSafetyGameTests.cleanup(l);}
}
