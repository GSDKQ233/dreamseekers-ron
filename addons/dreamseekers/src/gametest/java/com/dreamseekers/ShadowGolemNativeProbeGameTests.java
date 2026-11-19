// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
@GameTestHolder("dreamseekers_golem_native_probe") @PrefixGameTestTemplate(false)
public final class ShadowGolemNativeProbeGameTests {
    @GameTest(template="golem_unit_arena",batch="golem_native_probe",timeoutTicks=180)
    public static void actualWildAreaAndControlledTargets(GameTestHelper h){ShadowGolemNativeGameTests.realWildFortyFourTickAreaHitsCowButNeverControlledCombatWorkerOrNoncombat(h);}
    @AfterBatch(batch="golem_native_probe") public static void cleanup(ServerLevel l){ShadowGolemNativeGameTests.cleanup(l);}
}
