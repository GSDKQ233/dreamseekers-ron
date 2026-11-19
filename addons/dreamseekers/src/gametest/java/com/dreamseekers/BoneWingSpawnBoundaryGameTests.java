// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
@GameTestHolder("dreamseekers_bone_wing_spawn_boundary")
@PrefixGameTestTemplate(false)
public final class BoneWingSpawnBoundaryGameTests {
    @AfterBatch(batch="dreamseekers_bone_wing_spawn_boundary") public static void cleanup(ServerLevel l) {BoneWingProductionGameTests.cleanup(l);BoneWingCombatGameTests.cleanup(l);}
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_bone_wing_spawn_boundary",timeoutTicks=220)
    public static void actualJoinEventCancellationCannotConsumePaidQueueOrScore(GameTestHelper h) {BoneWingProductionGameTests.actualJoinEventCancellationCannotConsumePaidQueueOrScore(h);}
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_bone_wing_spawn_boundary",timeoutTicks=220)
    public static void lateJoinCancellationRetiresGhostPopulationAndChunkTicket(GameTestHelper h) {BoneWingProductionGameTests.lateJoinCancellationRetiresGhostPopulationAndChunkTicket(h);}
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_bone_wing_spawn_boundary",timeoutTicks=160)
    public static void savedRefundSnapshotUsesOriginalPayerAndImmutablePayment(GameTestHelper h) {BoneWingProductionGameTests.savedRefundSnapshotUsesOriginalPayerAndImmutablePayment(h);}
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_bone_wing_spawn_boundary",timeoutTicks=280)
    public static void stopAndSameTickRetargetResetRealRangedWindupWithoutCooldownReset(GameTestHelper h) throws Exception {BoneWingCombatGameTests.stopAndSameTickRetargetResetRealRangedWindupWithoutCooldownReset(h);}
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_bone_wing_spawn_boundary",timeoutTicks=160)
    public static void joinCallbackCannotReenterCompletionOrStartAnotherPayment(GameTestHelper h) {BoneWingProductionGameTests.joinCallbackCannotReenterCompletionOrStartAnotherPayment(h);}
}
