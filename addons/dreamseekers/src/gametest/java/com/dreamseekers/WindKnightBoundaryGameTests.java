// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
@GameTestHolder("dreamseekers_wind_knight_boundary")
@PrefixGameTestTemplate(false)
public final class WindKnightBoundaryGameTests {
 @AfterBatch(batch="wind_geometry") public static void cleanup(ServerLevel level){WindKnightMovementGameTests.cleanup(level);WindKnightNativeGameTests.cleanup(level);WindKnightSafetyGameTests.cleanup(level);}
 @GameTest(template="unit_isolation_arena",batch="wind_geometry",timeoutTicks=760)
 public static void width(GameTestHelper h){WindKnightMovementGameTests.nativeWideBodyCannotUseOneBlockGapButUsesThreeBlockOpening(h);}
 @GameTest(template="unit_isolation_arena",batch="wind_geometry",timeoutTicks=760)
 public static void height(GameTestHelper h){WindKnightMovementGameTests.threeBlockCeilingStopsNativeTallBodyUntilActuallyRemoved(h);}
 @GameTest(template="unit_isolation_arena",batch="wind_geometry",timeoutTicks=300)
 public static void nativeArea(GameTestHelper h){WindKnightNativeGameTests.actualNativeDelayedAoeStillHitsWildSideButPaidBaseTicksCannotScheduleIt(h);}
 @GameTest(template="unit_isolation_arena",batch="wind_geometry",timeoutTicks=240)
 public static void sameEnemyHeldAttack(GameTestHelper h){WindKnightSafetyGameTests.sameEnemyExplicitAttackReleasesHoldAndReallyPursues(h);}
}
