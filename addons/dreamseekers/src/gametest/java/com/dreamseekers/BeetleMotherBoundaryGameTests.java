// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
@GameTestHolder("dreamseekers_mother_boundary") @PrefixGameTestTemplate(false)
public final class BeetleMotherBoundaryGameTests {
 @AfterBatch(batch="mother_movement_boundary") public static void cleanup(ServerLevel l){BeetleMotherMovementGameTests.cleanup(l);}
 @GameTest(template="mother_unit_arena",batch="mother_movement_boundary",timeoutTicks=500)
 public static void barrier(GameTestHelper h){BeetleMotherMovementGameTests.groundPathGoesAroundSolidBarrierWithoutFlightOrClipping(h);}
 @GameTest(template="mother_unit_arena",batch="mother_movement_boundary",timeoutTicks=900)
 public static void queue(GameTestHelper h){BeetleMotherMovementGameTests.queuedMovesHoldStopAndReplacementNeverResumeStalePath(h);}
 @GameTest(template="mother_unit_arena",batch="mother_movement_boundary",timeoutTicks=560)
 public static void terminalShift(GameTestHelper h){BeetleMotherMovementGameTests.diagonalTerminalNodeReallySettlesAndQueuedLegCanAdvance(h);}
 @GameTest(template="mother_unit_arena",batch="mother_movement_boundary",timeoutTicks=560)
 public static void reverseTerminalShift(GameTestHelper h){BeetleMotherMovementGameTests.reverseDiagonalAlsoMeetsBothArrivalPredicatesBeforeShift(h);}
 @GameTest(template="mother_unit_arena",batch="mother_movement_boundary",timeoutTicks=560)
 public static void attackMoveTerminalShift(GameTestHelper h){BeetleMotherMovementGameTests.unopposedAttackMoveUsesSameSafeTerminalPrecisionAndThenShift(h);}
}


