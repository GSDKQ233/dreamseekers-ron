package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
@GameTestHolder("dreamseekers_ash_boundary") @PrefixGameTestTemplate(false)
public final class AshBoneWingBoundaryGameTests {
 @GameTest(template="ash_unit_arena",batch="ash_boundary",timeoutTicks=1800)
 public static void wideEndpoint(GameTestHelper h){AshBoneWingFlightGameTests.fourDirectionsAndDiagonalActuallyFinishTheSelectedFootEndpoint(h);}
 @GameTest(template="ash_unit_arena",batch="ash_boundary",timeoutTicks=920)
 public static void bodyGap(GameTestHelper h){AshBoneWingFlightGameTests.wideBodyRejectsOneHighAndTwoWideButFitsThreeWideTwoHigh(h);}
 @AfterBatch(batch="ash_boundary") public static void cleanup(ServerLevel l){AshBoneWingFlightGameTests.cleanup(l);}
}
