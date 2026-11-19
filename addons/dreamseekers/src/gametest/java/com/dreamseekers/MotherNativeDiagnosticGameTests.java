// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Diagnostic selection only: six original methods, unchanged budgets and byte-identical original template.
 * One generator preserves the observed full-run layout despite Forge's unordered method-registration set.
 * This is not a replacement full-roster result or a change to native despawn behavior.
 */
@GameTestHolder(MotherNativeDiagnosticGameTests.NAMESPACE)
public final class MotherNativeDiagnosticGameTests {
    public static final String NAMESPACE="dreamseekers_mother_native_diagnostic";
    private static final List<String> METHODS=List.of(
            "realWildSummonAndBuffExcludePaidBeetlesWhilePaidMotherNeverSchedules",
            "nativeHelpGoalRetainsWildPositiveButCannotRecruitPaidMother",
            "nativeBossbarCallbacksAreInstanceIsolated",
            "portalCallbacksAndForcedVehiclesCannotBypassInstanceRestriction",
            "ordinaryNbtBodyAndPeacefulKeepPaidNonHeroIdentity",
            "actualWildDeathKeepsKeysShellsAndVocalcordsButPaidDeathGivesNothing");
    private static int executionIndex;

    @GameTestGenerator public static List<TestFunction> originalSixInObservedOrder() throws NoSuchMethodException {
        if(!Boolean.getBoolean("dreamseekers.mother.native.diagnostic"))return List.of();
        List<TestFunction> result=new ArrayList<>();
        for(int index=0;index<METHODS.size();index++){
            Method method=BeetleMotherNativeGameTests.class.getDeclaredMethod(METHODS.get(index),GameTestHelper.class);
            GameTest a=method.getAnnotation(GameTest.class);
            if(a==null||!a.template().equals("mother_native_arena"))throw new IllegalStateException("Original native test/template changed: "+method);
            final int ordinal=index;
            result.add(new TestFunction(NAMESPACE,id(method.getName()),NAMESPACE+":"+a.template(),
                    StructureUtils.getRotationForRotationSteps(a.rotationSteps()),a.timeoutTicks(),a.setupTicks(),
                    a.required(),a.attempts(),a.requiredSuccesses(),helper->invoke(ordinal,method,helper)));
        }
        return result;
    }

    @BeforeBatch(batch=NAMESPACE) public static void verifyActualRegistryAndBatchOrder(ServerLevel level){
        executionIndex=0;
        List<String> expected=METHODS.stream().map(MotherNativeDiagnosticGameTests::id).toList();
        var registered=GameTestRegistry.getAllTestFunctions().stream().filter(t->t.getBatchName().equals(NAMESPACE)).toList();
        var batches=GameTestRunner.groupTestsIntoBatches(registered);
        List<String> grouped=batches.stream().flatMap(b->b.getTestFunctions().stream()).map(TestFunction::getTestName).toList();
        if(!registered.stream().map(TestFunction::getTestName).toList().equals(expected)||batches.size()!=1||!grouped.equals(expected))
            throw new IllegalStateException("Diagnostic six-case registry/batch order differs: expected="+expected+" actual="+grouped);
        System.out.println("DREAMSEEKERS_MOTHER_NATIVE_DIAGNOSTIC_REGISTRY_AND_BATCH_ORDER "+grouped);
    }

    @AfterBatch(batch=NAMESPACE) public static void cleanup(ServerLevel level){
        try{BeetleMotherNativeGameTests.cleanup(level);}finally{executionIndex=0;}
    }

    private static String id(String method){return "mothernativediagnosticgametests."+method.toLowerCase(Locale.ROOT);}
    private static void invoke(int expectedIndex,Method method,GameTestHelper helper){
        int actualIndex=executionIndex++;
        System.out.println("DREAMSEEKERS_MOTHER_NATIVE_DIAGNOSTIC_EXECUTION expectedIndex="+expectedIndex+" actualIndex="+actualIndex
                +" original="+method.getName()+" arena="+helper.absolutePos(BlockPos.ZERO)+" testTick="+helper.getTick());
        helper.assertTrue(actualIndex==expectedIndex,"diagnostic setup execution must retain original six-case layout order");
        try{method.invoke(null,helper);}
        catch(InvocationTargetException error){
            Throwable cause=error.getCause();
            if(cause instanceof RuntimeException runtime)throw runtime;
            if(cause instanceof Error fatal)throw fatal;
            throw new RuntimeException(cause);
        }catch(ReflectiveOperationException error){throw new RuntimeException(error);}
    }
}
