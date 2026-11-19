// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
@GameTestHolder("dreamseekers_spore_focused") @PrefixGameTestTemplate(false)
public final class SporeFocusedGameTests {
    @AfterBatch(batch="dreamseekers_spore_focused") public static void cleanup(ServerLevel level){SporeCarrierGameTests.cleanup(level);}
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_spore_focused",timeoutTicks=140) public static void nonUnitHasNoTrainingPopulationOrLivingAuthority(GameTestHelper h){SporeCarrierGameTests.nonUnitHasNoTrainingPopulationOrLivingAuthority(h);}
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_spore_focused",timeoutTicks=140) public static void deterministicOriginalRewardBranchOnlyRunsForWildSpore(GameTestHelper h){SporeCarrierGameTests.deterministicOriginalRewardBranchOnlyRunsForWildSpore(h);}
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_spore_focused",timeoutTicks=140) public static void realDeathKeepsWildLootButNoCarrierLootOrExperience(GameTestHelper h){SporeCarrierGameTests.realDeathKeepsWildLootButNoCarrierLootOrExperience(h);}
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_spore_focused",timeoutTicks=140) public static void interactionsAndAttackCannotTurnCarrierIntoFreeUnit(GameTestHelper h){SporeCarrierGameTests.interactionsAndAttackCannotTurnCarrierIntoFreeUnit(h);}
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_spore_focused",timeoutTicks=140) public static void originalHealAndFoxAreaKeepWildEffectsButCannotActivateCarrier(GameTestHelper h){SporeCarrierGameTests.originalHealAndFoxAreaKeepWildEffectsButCannotActivateCarrier(h);}
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_spore_focused",timeoutTicks=140) public static void forgedNbtCannotEnableEffectsEquipmentShieldLootOrLifetime(GameTestHelper h){SporeCarrierGameTests.forgedNbtCannotEnableEffectsEquipmentShieldLootOrLifetime(h);}
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_spore_focused",timeoutTicks=140) public static void nonfiniteHealthAndUnknownLiveSchemaNeverPersistAuthority(GameTestHelper h){SporeCarrierGameTests.nonfiniteHealthAndUnknownLiveSchemaNeverPersistAuthority(h);}
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_spore_focused",timeoutTicks=140) public static void typedRequestChecksRealSenderEpochDimensionAndNeverSpendsOrSummons(GameTestHelper h){SporeCarrierGameTests.typedRequestChecksRealSenderEpochDimensionAndNeverSpendsOrSummons(h);}
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_spore_focused",timeoutTicks=140) public static void authenticatedOrdinaryCommandCannotEnqueueCarrier(GameTestHelper h){SporeCarrierGameTests.authenticatedOrdinaryCommandCannotEnqueueCarrier(h);}
    @GameTest(template="unit_isolation_arena",batch="dreamseekers_spore_focused",timeoutTicks=140) public static void combatCasterDeadRawUnloadedAndOutOfBoundsSourcesRemainClosed(GameTestHelper h){SporeCarrierGameTests.combatCasterDeadRawUnloadedAndOutOfBoundsSourcesRemainClosed(h);}
}
