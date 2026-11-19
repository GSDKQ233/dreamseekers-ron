// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
@GameTestHolder("dreamseekers_slime_focused")
@PrefixGameTestTemplate(false)
public final class PinkSlimeFocusedGameTests {
    @AfterBatch(batch="dreamseekers_slime_focused_0") public static void cleanup0(ServerLevel level) { PinkSlimeMovementGameTests.cleanup(level); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_slime_focused_0", timeoutTicks=500)
    public static void groundPathGoesAroundSolidBarrierWithoutFlightOrClipping(GameTestHelper h) throws Exception { PinkSlimeMovementGameTests.groundPathGoesAroundSolidBarrierWithoutFlightOrClipping(h); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_slime_focused_0", timeoutTicks=690)
    public static void enclosedGroundSlimeCannotPhaseAndRetriesOpenedExit(GameTestHelper h) throws Exception { PinkSlimeMovementGameTests.enclosedGroundSlimeCannotPhaseAndRetriesOpenedExit(h); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_slime_focused_0", timeoutTicks=780)
    public static void queuedMovesHoldStopAndReplacementNeverResumeStalePath(GameTestHelper h) throws Exception { PinkSlimeMovementGameTests.queuedMovesHoldStopAndReplacementNeverResumeStalePath(h); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_slime_focused_0", timeoutTicks=80)
    public static void malformedNonlivingMissingAndRemovedTargetsCannotCrashNoncombatCommands(GameTestHelper h) throws Exception { PinkSlimeMovementGameTests.malformedNonlivingMissingAndRemovedTargetsCannotCrashNoncombatCommands(h); }
    @AfterBatch(batch="dreamseekers_slime_focused_1") public static void cleanup1(ServerLevel level) { PinkSlimeProductionGameTests.cleanup(level); }
    @GameTest(template="dreamseekers_test_arena", batch="dreamseekers_slime_focused_1", timeoutTicks=750)
    public static void exactPaidSlimeQueueRequiresOwnCourtAndPausesWithoutLosingPayment(GameTestHelper h) throws Exception { PinkSlimeProductionGameTests.exactPaidSlimeQueueRequiresOwnCourtAndPausesWithoutLosingPayment(h); }
    @GameTest(template="dreamseekers_test_arena", batch="dreamseekers_slime_focused_1", timeoutTicks=720)
    public static void blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper h) throws Exception { PinkSlimeProductionGameTests.blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(h); }
    @GameTest(template="dreamseekers_test_arena", batch="dreamseekers_slime_focused_1", timeoutTicks=100)
    public static void populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper h) throws Exception { PinkSlimeProductionGameTests.populationResourcesAndRepeatedCancellationNeverDuplicate(h); }
    @GameTest(template="dreamseekers_test_arena", batch="dreamseekers_slime_focused_1", timeoutTicks=100)
    public static void actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper h) throws Exception { PinkSlimeProductionGameTests.actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(h); }
    @AfterBatch(batch="dreamseekers_slime_focused_2") public static void cleanup2(ServerLevel level) { PinkSlimeCommandAuthorityGameTests.cleanup(level); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_slime_focused_2", timeoutTicks=80)
    public static void authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(GameTestHelper h) throws Exception { PinkSlimeCommandAuthorityGameTests.authenticatedDenialsCannotSpoofSandboxOrCancelEnemyShiftQueue(h); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_slime_focused_2", timeoutTicks=80)
    public static void ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(GameTestHelper h) throws Exception { PinkSlimeCommandAuthorityGameTests.ownerExplicitAllyAndRealSandboxCarryActualSenderAndFilterIds(h); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_slime_focused_2", timeoutTicks=80)
    public static void buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(GameTestHelper h) throws Exception { PinkSlimeCommandAuthorityGameTests.buildingOnlyAbilitiesAndExplicitNonUnitCheatKeepTheirAuthorizedRoutes(h); }
    @AfterBatch(batch="dreamseekers_slime_focused_3") public static void cleanup3(ServerLevel level) { PinkSlimeSafetyGameTests.cleanup(level); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_slime_focused_3", timeoutTicks=180)
    public static void nativeImpulseSuppressedForPaidOnlyIncludingNbtAndWildControl(GameTestHelper h) throws Exception { PinkSlimeSafetyGameTests.nativeImpulseSuppressedForPaidOnlyIncludingNbtAndWildControl(h); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_slime_focused_3", timeoutTicks=100)
    public static void interactionsDamageAndDeathCannotYieldWildRewards(GameTestHelper h) throws Exception { PinkSlimeSafetyGameTests.interactionsDamageAndDeathCannotYieldWildRewards(h); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_slime_focused_3", timeoutTicks=240)
    public static void meleeIsRealAndSameFactionEnemiesAreNotAllies(GameTestHelper h) throws Exception { PinkSlimeSafetyGameTests.meleeIsRealAndSameFactionEnemiesAreNotAllies(h); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_slime_focused_3", timeoutTicks=80)
    public static void ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(GameTestHelper h) throws Exception { PinkSlimeSafetyGameTests.ownAlliedUnownedTargetsAndReservedSkillRemainFailClosed(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_slime_focused_3",timeoutTicks=300)
    public static void combatHoldRange(GameTestHelper h) throws Exception { PinkSlimeSafetyGameTests.holdWithEnemyNeverChasesButStillHitsInsideMeleeReach(h); }
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_slime_focused_1",timeoutTicks=340)
    public static void buildingCombatScope(GameTestHelper h) throws Exception { PinkSlimeProductionGameTests.buildingCombatRejectsFriendlyStaleCrossDimensionAndHoldMovement(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_slime_focused_3",timeoutTicks=220)
    public static void friendlyFollowRevoke(GameTestHelper h) throws Exception { PinkSlimeSafetyGameTests.friendlyFollowMovesAndRevokedAllianceCancelsItsPath(h); }
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_slime_focused_3",timeoutTicks=220)
    public static void revokedFollowCanReacquireNewEnemy(GameTestHelper h) throws Exception { PinkSlimeSafetyGameTests.revokedFollowAllowsNewHostileAutoAggroWithoutRevivingOldCommand(h); }
}
