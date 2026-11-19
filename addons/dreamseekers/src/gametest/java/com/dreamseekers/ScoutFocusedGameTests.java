// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
/** Focused aliases only; normal aggregate executes each original test once. */
@GameTestHolder("dreamseekers_scout_focused")
@PrefixGameTestTemplate(false)
public final class ScoutFocusedGameTests {
    @AfterBatch(batch="dreamseekers_scout_focused_0") public static void cleanup0(ServerLevel level) { PinkChickenFlightGameTests.cleanup(level); }
    @GameTest(template = "unit_isolation_arena", batch="dreamseekers_scout_focused_0", timeoutTicks = 340)
    public static void genuinelyFliesOverTwoBlockBarrierWithoutCollision(GameTestHelper h) throws Exception { PinkChickenFlightGameTests.genuinelyFliesOverTwoBlockBarrierWithoutCollision(h); }
    @GameTest(template = "unit_isolation_arena", batch="dreamseekers_scout_focused_0", timeoutTicks = 420)
    public static void sealedWallsCannotBeCrossedOrHarvestedThrough(GameTestHelper h) throws Exception { PinkChickenFlightGameTests.sealedWallsCannotBeCrossedOrHarvestedThrough(h); }
    @GameTest(template = "unit_isolation_arena", batch="dreamseekers_scout_focused_0", timeoutTicks = 330)
    public static void stopAndReplacementOrdersCancelFlightWithoutWildWandering(GameTestHelper h) throws Exception { PinkChickenFlightGameTests.stopAndReplacementOrdersCancelFlightWithoutWildWandering(h); }
    @GameTest(template = "unit_isolation_arena", batch="dreamseekers_scout_focused_0", timeoutTicks = 490)
    public static void lowFlightCeilingDoesNotRatchetAndRetriesWhenOpeningAppears(GameTestHelper h) throws Exception { PinkChickenFlightGameTests.lowFlightCeilingDoesNotRatchetAndRetriesWhenOpeningAppears(h); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_scout_focused_0", timeoutTicks=560)
    public static void queuedMoveThenHoldThenStopCannotResumeStaleFlight(GameTestHelper h) throws Exception { PinkChickenFlightGameTests.queuedMoveThenHoldThenStopCannotResumeStaleFlight(h); }
    @GameTest(template="unit_isolation_arena", batch="dreamseekers_scout_focused_0", timeoutTicks=160)
    public static void noncombatAttackAndHostileFollowDoNotBecomeHiddenEnemyTracking(GameTestHelper h) throws Exception { PinkChickenFlightGameTests.noncombatAttackAndHostileFollowDoNotBecomeHiddenEnemyTracking(h); }
    @AfterBatch(batch="dreamseekers_scout_focused_1") public static void cleanup1(ServerLevel level) { PinkChickenProductionGameTests.cleanup(level); }
    @GameTest(template="dreamseekers_test_arena", batch="dreamseekers_scout_focused_1", timeoutTicks=650)
    public static void exactPaidScoutQueueRequiresOwnCourtAndPausesWithoutLosingPayment(GameTestHelper h) throws Exception { PinkChickenProductionGameTests.exactPaidScoutQueueRequiresOwnCourtAndPausesWithoutLosingPayment(h); }
    @GameTest(template="dreamseekers_test_arena", batch="dreamseekers_scout_focused_1", timeoutTicks=620)
    public static void blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(GameTestHelper h) throws Exception { PinkChickenProductionGameTests.blockedOrFloodedSpawnExitPausesThenReopensWithoutExtraCharge(h); }
    @GameTest(template="dreamseekers_test_arena", batch="dreamseekers_scout_focused_1", timeoutTicks=100)
    public static void populationResourcesAndRepeatedCancellationNeverDuplicate(GameTestHelper h) throws Exception { PinkChickenProductionGameTests.populationResourcesAndRepeatedCancellationNeverDuplicate(h); }
    @GameTest(template="dreamseekers_test_arena", batch="dreamseekers_scout_focused_1", timeoutTicks=100)
    public static void actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(GameTestHelper h) throws Exception { PinkChickenProductionGameTests.actualProductionPacketRejectsForeignSenderSandboxBypassAndWrongDimension(h); }
    @AfterBatch(batch="dreamseekers_scout_focused_2") public static void cleanup2(ServerLevel level) { PinkChickenIsolationGameTests.cleanup(level); }
    @GameTest(template = "unit_isolation_arena", batch="dreamseekers_scout_focused_2", timeoutTicks = 60)
    public static void paidScoutKeepsNativeFlightButCannotAttackBreedOrConsumeInteractions(GameTestHelper h) throws Exception { PinkChickenIsolationGameTests.paidScoutKeepsNativeFlightButCannotAttackBreedOrConsumeInteractions(h); }
    @GameTest(template = "unit_isolation_arena", batch="dreamseekers_scout_focused_2", timeoutTicks = 60)
    public static void nativeEggRedirectIsTransformedAndIndependentOfOwnership(GameTestHelper h) throws Exception { PinkChickenIsolationGameTests.nativeEggRedirectIsTransformedAndIndependentOfOwnership(h); }
    @GameTest(template = "unit_isolation_arena", batch="dreamseekers_scout_focused_2", timeoutTicks = 70)
    public static void paidScoutDeathAndReloadCannotRestoreAnimalRewards(GameTestHelper h) throws Exception { PinkChickenIsolationGameTests.paidScoutDeathAndReloadCannotRestoreAnimalRewards(h); }
    @AfterBatch(batch="dreamseekers_scout_focused_3") public static void cleanup3(ServerLevel level) { ScoutMarkGameTests.cleanup(level); }
    @GameTest(template = "unit_isolation_arena", batch="dreamseekers_scout_focused_3", timeoutTicks = 40)
    public static void exactOwnerAndIdentityAreRequiredAndReplayCannotRefresh(GameTestHelper h) throws Exception { ScoutMarkGameTests.exactOwnerAndIdentityAreRequiredAndReplayCannotRefresh(h); }
    @GameTest(template = "unit_isolation_arena", batch="dreamseekers_scout_focused_3", timeoutTicks = 40)
    public static void alliedOwnedNeutralAndRawTargetsRejectedButSameFactionEnemyAllowed(GameTestHelper h) throws Exception { ScoutMarkGameTests.alliedOwnedNeutralAndRawTargetsRejectedButSameFactionEnemyAllowed(h); }
    @GameTest(template = "unit_isolation_arena", batch="dreamseekers_scout_focused_3", timeoutTicks = 40)
    public static void lineOfSightRangeInvisibilityAndAuthoritativeFogAreRequired(GameTestHelper h) throws Exception { ScoutMarkGameTests.lineOfSightRangeInvisibilityAndAuthoritativeFogAreRequired(h); }
    @GameTest(template = "unit_isolation_arena", batch="dreamseekers_scout_focused_3", timeoutTicks = 40)
    public static void targetAndCasterOwnershipAllianceDeathAndLossInvalidate(GameTestHelper h) throws Exception { ScoutMarkGameTests.targetAndCasterOwnershipAllianceDeathAndLossInvalidate(h); }
    @GameTest(template = "unit_isolation_arena", batch="dreamseekers_scout_focused_3", timeoutTicks = 600)
    public static void worldTicksExpireMarkAndCooldownWithoutReloadExtension(GameTestHelper h) throws Exception { ScoutMarkGameTests.worldTicksExpireMarkAndCooldownWithoutReloadExtension(h); }
    @GameTest(template = "unit_isolation_arena", batch="dreamseekers_scout_focused_3", timeoutTicks = 40)
    public static void packetCodecAndCorruptNbtAreBounded(GameTestHelper h) throws Exception { ScoutMarkGameTests.packetCodecAndCorruptNbtAreBounded(h); }
    @AfterBatch(batch="dreamseekers_scout_focused_4") public static void cleanup4(ServerLevel level) { ScoutMarkVisionGameTests.cleanup(level); }
    @GameTest(template = "unit_isolation_arena", batch="dreamseekers_scout_focused_4", timeoutTicks = 200)
    public static void realSightOwnerOnlyHeartbeatExpiryLossAndReconnect(GameTestHelper h) throws Exception { ScoutMarkVisionGameTests.realSightOwnerOnlyHeartbeatExpiryLossAndReconnect(h); }
}
