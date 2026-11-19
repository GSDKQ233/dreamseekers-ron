// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
@GameTestHolder("dreamseekers_jellyfish_focused")
@PrefixGameTestTemplate(false)
public final class JellyfishFocusedGameTests {
    @AfterBatch(batch="dreamseekers_jellyfish_focused_0") public static void cleanup0(ServerLevel level) { JellyfishFlightGameTests.cleanup(level); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_jellyfish_focused_0",timeoutTicks=740)
    public static void genuinelyFliesOverTwoBlockBarrierWithoutCollision(GameTestHelper h) throws Exception { JellyfishFlightGameTests.genuinelyFliesOverTwoBlockBarrierWithoutCollision(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_jellyfish_focused_0",timeoutTicks=1420)
    public static void sealedWallsCannotBeCrossedOrHarvestedThrough(GameTestHelper h) throws Exception { JellyfishFlightGameTests.sealedWallsCannotBeCrossedOrHarvestedThrough(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_jellyfish_focused_0",timeoutTicks=730)
    public static void stopAndReplacementOrdersCancelFlightWithoutWildWandering(GameTestHelper h) throws Exception { JellyfishFlightGameTests.stopAndReplacementOrdersCancelFlightWithoutWildWandering(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_jellyfish_focused_0",timeoutTicks=890)
    public static void lowFlightCeilingDoesNotRatchetAndRetriesWhenOpeningAppears(GameTestHelper h) throws Exception { JellyfishFlightGameTests.lowFlightCeilingDoesNotRatchetAndRetriesWhenOpeningAppears(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_jellyfish_focused_0",timeoutTicks=1120)
    public static void queuedMoveThenHoldThenStopCannotResumeStaleFlight(GameTestHelper h) throws Exception { JellyfishFlightGameTests.queuedMoveThenHoldThenStopCannotResumeStaleFlight(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_jellyfish_focused_0",timeoutTicks=560)
    public static void noncombatAttackAndHostileFollowDoNotBecomeHiddenEnemyTracking(GameTestHelper h) throws Exception { JellyfishFlightGameTests.noncombatAttackAndHostileFollowDoNotBecomeHiddenEnemyTracking(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_jellyfish_focused_0",timeoutTicks=1660)
    public static void sameTickABAndABAKeepFinalFlightDestination(GameTestHelper h) throws Exception { JellyfishFlightGameTests.sameTickABAndABAKeepFinalFlightDestination(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_jellyfish_focused_0",timeoutTicks=920)
    public static void adultBodyCannotFitOneBlockGapButFitsOpenedTwoBlockLowRoof(GameTestHelper h) throws Exception { JellyfishFlightGameTests.adultBodyCannotFitOneBlockGapButFitsOpenedTwoBlockLowRoof(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_jellyfish_focused_0",timeoutTicks=500)
    public static void malformedItemBoatMissingAndRemovedTargetsNeverBecomeFollowOrDamage(GameTestHelper h) throws Exception { JellyfishFlightGameTests.malformedItemBoatMissingAndRemovedTargetsNeverBecomeFollowOrDamage(h); }
    @AfterBatch(batch="dreamseekers_jellyfish_focused_1") public static void cleanup1(ServerLevel level) { JellyfishProductionGameTests.cleanup(level); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_jellyfish_focused_1",timeoutTicks=750)
    public static void exactPaidJellyfishQueueRequiresOwnCourtAndPausesWithoutLosingPayment(GameTestHelper h) throws Exception { JellyfishProductionGameTests.exactPaidJellyfishQueueRequiresOwnCourtAndPausesWithoutLosingPayment(h); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_jellyfish_focused_1",timeoutTicks=720)
    public static void blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper h) throws Exception { JellyfishProductionGameTests.blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(h); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_jellyfish_focused_1",timeoutTicks=100)
    public static void populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper h) throws Exception { JellyfishProductionGameTests.populationResourcesAndRepeatedCancellationNeverDuplicate(h); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_jellyfish_focused_1",timeoutTicks=100)
    public static void actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper h) throws Exception { JellyfishProductionGameTests.actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(h); }
    @AfterBatch(batch="dreamseekers_jellyfish_focused_2") public static void cleanup2(ServerLevel level) { JellyfishCommandAuthorityGameTests.cleanup(level); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_jellyfish_focused_2",timeoutTicks=80)
    public static void authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(GameTestHelper h) throws Exception { JellyfishCommandAuthorityGameTests.authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_jellyfish_focused_2",timeoutTicks=80)
    public static void ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(GameTestHelper h) throws Exception { JellyfishCommandAuthorityGameTests.ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_jellyfish_focused_2",timeoutTicks=80)
    public static void buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(GameTestHelper h) throws Exception { JellyfishCommandAuthorityGameTests.buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(h); }
    @AfterBatch(batch="dreamseekers_jellyfish_focused_3") public static void cleanup3(ServerLevel level) { JellyfishIsolationGameTests.cleanup(level); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_jellyfish_focused_3",timeoutTicks=60)
    public static void paidScoutKeepsNativeFlightButCannotAttackBreedOrConsumeInteractions(GameTestHelper h) throws Exception { JellyfishIsolationGameTests.paidScoutKeepsNativeFlightButCannotAttackBreedOrConsumeInteractions(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_jellyfish_focused_3",timeoutTicks=70)
    public static void paidSupportDeathAndReloadCannotRestoreAnimalRewards(GameTestHelper h) throws Exception { JellyfishIsolationGameTests.paidSupportDeathAndReloadCannotRestoreAnimalRewards(h); }
    @AfterBatch(batch="dreamseekers_jellyfish_focused_4") public static void cleanup4(ServerLevel level) { JellyfishSafetyGameTests.cleanup(level); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_jellyfish_focused_4",timeoutTicks=180)
    public static void nativeFloatImpulseOnlySuppressedForPaidSubtypeAndNeverReturnsAfterLoad(GameTestHelper h) throws Exception { JellyfishSafetyGameTests.nativeFloatImpulseOnlySuppressedForPaidSubtypeAndNeverReturnsAfterLoad(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_jellyfish_focused_4",timeoutTicks=100)
    public static void ordinaryDamageAndActualDeathRemainSafeWithoutInventedInvulnerability(GameTestHelper h) throws Exception { JellyfishSafetyGameTests.ordinaryDamageAndActualDeathRemainSafeWithoutInventedInvulnerability(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_jellyfish_focused_4",timeoutTicks=120)
    public static void reservedProtectionAndHealAreTypedUnavailableAndNeverGrantEffectsOrResources(GameTestHelper h) throws Exception { JellyfishSafetyGameTests.reservedProtectionAndHealAreTypedUnavailableAndNeverGrantEffectsOrResources(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_jellyfish_focused_4",timeoutTicks=220)
    public static void friendlyFollowMovesButRevokedAllianceCannotTrackOrDamage(GameTestHelper h) throws Exception { JellyfishSafetyGameTests.friendlyFollowMovesButRevokedAllianceCannotTrackOrDamage(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_jellyfish_focused_4",timeoutTicks=920)
    public static void stopClearsDeferredNavigationTargetForAddonButNotWildControl(GameTestHelper h) throws Exception { JellyfishSafetyGameTests.stopClearsDeferredNavigationTargetForAddonButNotWildControl(h); }
    @AfterBatch(batch="dreamseekers_jellyfish_focused_5") public static void cleanup5(ServerLevel level) { DreamRallyLifecycleGameTests.cleanup(level); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_jellyfish_focused_5",timeoutTicks=220)
    public static void actualStopReplacementOwnerCycleAndRemovalCancelPendingHandoff(GameTestHelper h) { DreamRallyLifecycleGameTests.actualStopReplacementOwnerCycleAndRemovalCancelPendingHandoff(h); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_jellyfish_focused_5",timeoutTicks=180)
    public static void detachedProducerAndBoundedUnpublishedTimeoutNeverRestoreCommands(GameTestHelper h) { DreamRallyLifecycleGameTests.detachedProducerAndBoundedUnpublishedTimeoutNeverRestoreCommands(h); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_jellyfish_focused_5",timeoutTicks=950)
    public static void realProductionSnapshotsTwoLegRallyAndStopClearsRemainingLeg(GameTestHelper h) { DreamRallyLifecycleGameTests.realProductionSnapshotsTwoLegRallyAndStopClearsRemainingLeg(h); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_jellyfish_focused_5",timeoutTicks=220)
    public static void enqueuedImmediateOrdersWinBeforeProductionTickAndCannotLeaveStaleSecondLeg(GameTestHelper h) { DreamRallyLifecycleGameTests.enqueuedImmediateOrdersWinBeforeProductionTickAndCannotLeaveStaleSecondLeg(h); }
}
