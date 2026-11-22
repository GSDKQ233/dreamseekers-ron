// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;

/** Reuse the original complete manual-cast/owner-wire tests, without testing just a copied mock. */
@GameTestHolder("dreamseekers_passive_edges")
@PrefixGameTestTemplate(false)
public final class PassiveCompatibilityGameTests {
    @AfterBatch(batch="passive_one_wind_wire")
    public static void windCleanup(ServerLevel level){WindDashSkillGameTests.cleanup(level);}
    @AfterBatch(batch="passive_one_snail_wire")
    public static void snailCleanup(ServerLevel level){SnailShellFormationSkillGameTests.cleanup(level);}
    @GameTest(template="unit_isolation_arena",batch="passive_one_wind_wire",timeoutTicks=900)
    public static void manualWindSequenceAndPassivePreference(GameTestHelper h){
        WindDashSkillGameTests.packetCodecCarriesOnlyEnemyIdentityAndUsesSameAuthoritativeExecution(h);
    }
    @GameTest(template="unit_isolation_arena",batch="passive_one_snail_wire",timeoutTicks=1000)
    public static void snailBeetleAndFireflyDeliverTheirActualOwnerOnlySync(GameTestHelper h){
        SnailShellFormationSkillGameTests.naturalEntityTicksPublishInitialAndTenTickOwnerOnlySyncWithoutDuplicateStateTicks(h);
    }
}
