// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
@GameTestHolder("dreamseekers_mark_vision_focused")
@PrefixGameTestTemplate(false)
public final class ScoutMarkVisionFocusedGameTests {
    @AfterBatch(batch="dreamseekers_mark_vision_focused") public static void cleanup(ServerLevel level){ScoutMarkVisionGameTests.cleanup(level);}
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_mark_vision_focused",timeoutTicks=200)
    public static void realSightOwnerOnlyHeartbeatExpiryLossAndReconnect(GameTestHelper h){ScoutMarkVisionGameTests.realSightOwnerOnlyHeartbeatExpiryLossAndReconnect(h);}
}
