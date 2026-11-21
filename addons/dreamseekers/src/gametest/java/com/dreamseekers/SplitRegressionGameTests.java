// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.StructureUtils;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraftforge.gametest.GameTestHolder;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Test-only selection: original methods, annotation budgets, templates and original batch hooks.
 * The historical 115-method selection is extended by the three light explicit-attack
 * methods and five shadow-ghost native/peaceful methods, then two additional building checks (125 total).
 * No production mixin, no skipped assertions, no manual ticking, no automatic retries.
 * Forge registers original @AfterBatch hooks independently of namespace filtering.
 */
@GameTestHolder("dreamseekers_split")
public final class SplitRegressionGameTests {
    private static final List<String> ORIGINAL_CLASSES = List.of(
            "com.dreamseekers.LightExplicitAttackGameTests",
            "com.dreamseekers.ShadowGhostNativeGameTests",
            "com.dreamseekers.BuildingAssetGameTests",
            "com.dreamseekers.DreamRallyLifecycleGameTests",
            "com.dreamseekers.DreamseekersGameTests",
            "com.dreamseekers.FireflyEconomyGameTests",
            "com.dreamseekers.FireflyRepairGameTests",
            "com.dreamseekers.FireflyWorkerGameTests",
            "com.dreamseekers.GardenWorkerGameTests",
            "com.dreamseekers.GoldenFoxCommandAuthorityGameTests",
            "com.dreamseekers.GoldenFoxInteractionGameTests",
            "com.dreamseekers.GoldenFoxMovementGameTests",
            "com.dreamseekers.GoldenFoxProductionGameTests",
            "com.dreamseekers.JellyfishCommandAuthorityGameTests",
            "com.dreamseekers.JellyfishFlightGameTests",
            "com.dreamseekers.JellyfishIsolationGameTests",
            "com.dreamseekers.JellyfishProductionGameTests",
            "com.dreamseekers.JellyfishSafetyGameTests",
            "com.dreamseekers.PinkChickenFlightGameTests",
            "com.dreamseekers.PinkChickenIsolationGameTests",
            "com.dreamseekers.PinkChickenProductionGameTests",
            "com.dreamseekers.PinkSlimeCommandAuthorityGameTests",
            "com.dreamseekers.PinkSlimeMovementGameTests",
            "com.dreamseekers.PinkSlimeProductionGameTests",
            "com.dreamseekers.PinkSlimeSafetyGameTests",
            "com.dreamseekers.ScaffoldBuildingGameTests",
            "com.dreamseekers.ScoutMarkGameTests",
            "com.dreamseekers.ScoutMarkVisionGameTests",
            "com.dreamseekers.UnitIsolationGameTests",
            "com.solegendary.reignofnether.building.OreMineGameTests"
    );
    @GameTestGenerator public static List<TestFunction> selectedOriginalBatch() throws ClassNotFoundException {
        String selected = System.getProperty("dreamseekers.test.batch", "");
        if (selected.isBlank()) return List.of(); // ordinary aggregate unchanged
        String selectedCase = System.getProperty("dreamseekers.test.case", "");
        List<Method> methods = new ArrayList<>();
        int originalCount = 0;
        for (String name : ORIGINAL_CLASSES) for (Method method : Class.forName(name).getDeclaredMethods()) {
            GameTest test = method.getAnnotation(GameTest.class);
            if (test == null) continue;
            originalCount++;
            if (test.batch().equals(selected) && (selectedCase.isBlank() || method.getName().equals(selectedCase))) methods.add(method);
        }
        if (originalCount != 125 || methods.isEmpty() || (!selectedCase.isBlank() && methods.size() != 1))
            throw new IllegalArgumentException("Expected 125 explicitly listed original cases and nonempty unambiguous selection; total=" + originalCount + " selected=" + methods.size());
        methods.sort(Comparator.comparing(m -> m.getDeclaringClass().getName()+"."+m.getName()));
        List<TestFunction> tests = new ArrayList<>();
        for (Method method : methods) {
            GameTest a = method.getAnnotation(GameTest.class);
            if (a.template().isBlank()) throw new IllegalArgumentException("Explicit original template required: " + method);
            String id = method.getDeclaringClass().getSimpleName()+"."+method.getName();
            System.out.println("DREAMSEEKERS_SPLIT_SELECTED batch="+a.batch()+" original="+id+" timeout="+a.timeoutTicks()+" attempts="+a.attempts()+" required="+a.required());
            tests.add(new TestFunction(a.batch(), id.toLowerCase(java.util.Locale.ROOT), "dreamseekers_split:"+a.template(),
                    StructureUtils.getRotationForRotationSteps(a.rotationSteps()), a.timeoutTicks(), a.setupTicks(),
                    a.required(), a.attempts(), a.requiredSuccesses(), helper -> invoke(method,helper)));
        }
        return tests;
    }
    private static void invoke(Method method, GameTestHelper helper) {
        try { method.invoke(null,helper); }
        catch (InvocationTargetException error) {
            Throwable cause=error.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error fatal) throw fatal;
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }
}
