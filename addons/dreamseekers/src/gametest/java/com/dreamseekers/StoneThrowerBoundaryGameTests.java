// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
@GameTestHolder("dreamseekers_stone_boundary")
@PrefixGameTestTemplate(false)
public final class StoneThrowerBoundaryGameTests {
    @GameTest(template="stone_native_arena",batch="stone_boundary",timeoutTicks=320)
    public static void groundPhysicalAttack(GameTestHelper h){StoneThrowerNativeGameTests.sameGroundEnemyExplicitAttackReleasesHoldThenTravelsAndDamagesWithoutMelee(h);}
    @GameTest(template="stone_native_arena",batch="stone_boundary",timeoutTicks=180)
    public static void gatewayIsolation(GameTestHelper h){StoneThrowerNativeGameTests.actualGatewayTeleportsVanillaButNotPaidStoneOrItsProjectile(h);}
    @AfterBatch(batch="stone_boundary") public static void cleanup(ServerLevel l){StoneThrowerNativeGameTests.cleanup(l);}
}
