package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
/** Diagnostic wrapper uses exactly the original walk fixture, budget, bodies and readiness predicates. */
@GameTestHolder("dreamseekers_contract_ready") @PrefixGameTestTemplate(false)
public final class NeutralContractReadinessDiagnostic {
    @GameTest(templateNamespace="dreamseekers_contract_ready",template="dreamseekers_test_arena",batch="contract_ready_diagnostic",timeoutTicks=800)
    public static void originalWalkReadinessWithoutFixtureMutation(GameTestHelper h){NeutralContractGameTests.actualWorkerWalkAndEightSecondPaidBeetlePreserveInjuryAndFreeOrigin(h);}
    @AfterBatch(batch="contract_ready_diagnostic") public static void cleanup(net.minecraft.server.level.ServerLevel level){NeutralContractGameTests.cleanup(level);}
}
