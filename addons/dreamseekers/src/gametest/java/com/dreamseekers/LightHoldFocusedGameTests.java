// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
@GameTestHolder("dreamseekers_light_hold_focused") @PrefixGameTestTemplate(false)
public final class LightHoldFocusedGameTests {
    @AfterBatch(batch="dreamseekers_light_hold_focused") public static void cleanup(ServerLevel level){LightExplicitAttackGameTests.cleanup(level);}
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_light_hold_focused",timeoutTicks=600) public static void slimeSameEnemyAttack(GameTestHelper h){LightExplicitAttackGameTests.slimeSameEnemyAttackReleasesHoldAndActuallyHits(h);}
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_light_hold_focused",timeoutTicks=600) public static void boneSameEnemyAttack(GameTestHelper h){LightExplicitAttackGameTests.lightBoneSameEnemyAttackReleasesHoldAndActuallyShoots(h);}
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_light_hold_focused",timeoutTicks=600) public static void windSameEnemyAttack(GameTestHelper h){LightExplicitAttackGameTests.windSameEnemyAttackKeepsExistingVerifiedSemantics(h);}
}
