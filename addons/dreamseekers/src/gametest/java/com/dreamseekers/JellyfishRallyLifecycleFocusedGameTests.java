// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
@GameTestHolder("dreamseekers_jellyfish_rally_lifecycle_focused")
@PrefixGameTestTemplate(false)
public final class JellyfishRallyLifecycleFocusedGameTests {
 @AfterBatch(batch="jellyfish_rally_lifecycle_focused") public static void cleanup(ServerLevel level){DreamRallyLifecycleGameTests.cleanup(level);}
 @GameTest(template="dreamseekers_test_arena",batch="jellyfish_rally_lifecycle_focused",timeoutTicks=220)
 public static void actualStopReplacementOwnerCycleAndRemovalCancelPendingHandoff(GameTestHelper h){DreamRallyLifecycleGameTests.actualStopReplacementOwnerCycleAndRemovalCancelPendingHandoff(h);}
 @GameTest(template="dreamseekers_test_arena",batch="jellyfish_rally_lifecycle_focused",timeoutTicks=180)
 public static void detachedProducerAndBoundedUnpublishedTimeoutNeverRestoreCommands(GameTestHelper h){DreamRallyLifecycleGameTests.detachedProducerAndBoundedUnpublishedTimeoutNeverRestoreCommands(h);}
 @GameTest(template="dreamseekers_test_arena",batch="jellyfish_rally_lifecycle_focused",timeoutTicks=950)
 public static void realProductionSnapshotsTwoLegRallyAndStopClearsRemainingLeg(GameTestHelper h){DreamRallyLifecycleGameTests.realProductionSnapshotsTwoLegRallyAndStopClearsRemainingLeg(h);}
 @GameTest(template="dreamseekers_test_arena",batch="jellyfish_rally_lifecycle_focused",timeoutTicks=220)
 public static void enqueuedImmediateOrdersWinBeforeProductionTickAndCannotLeaveStaleSecondLeg(GameTestHelper h){DreamRallyLifecycleGameTests.enqueuedImmediateOrdersWinBeforeProductionTickAndCannotLeaveStaleSecondLeg(h);}
}
