// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
/** Focused alias of the exact main-suite contract case, not a separate behavior claim. */
@GameTestHolder("dreamseekers_building_care_diagnostic") @PrefixGameTestTemplate(false)
public final class BuildingContractCareDiagnosticGameTests {
    @GameTest(templateNamespace="dreamseekers_building_care_diagnostic",template="dreamseekers_test_arena",batch="building_contract_care_diagnostic",timeoutTicks=1500)
    public static void sameMainSnailProofCaseWithRuntimeDiagnostics(GameTestHelper h) {
        BuildingContractCareGameTests.realSnailProofRejectsPendingForgedFreeOtherOriginAndReplacedAccount(h);
    }
    @AfterBatch(batch="building_contract_care_diagnostic") public static void cleanup(ServerLevel level) {
        BuildingContractCareGameTests.cleanup(level);
    }
}
