package com.solegendary.reignofnether.api;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;



@GameTestHolder("reignofnether_registry_concurrency")
@PrefixGameTestTemplate(false)
public final class ConstructionSafeMappedRegistryGameTests {
    private static ConstructionSafeMappedRegistry<Object> registry(String suffix) {
        return new ConstructionSafeMappedRegistry<>(ResourceKey.createRegistryKey(
                ResourceLocation.fromNamespaceAndPath("registry_concurrency_test", suffix)), Lifecycle.experimental());
    }

    @GameTest(templateNamespace="reignofnether_registry_concurrency", template="ore_mine_test_arena", batch="registry_concurrency", timeoutTicks=200)
    public static void allFourPublicRegistriesUseTheProtectedImplementation(GameTestHelper h) {
        assertTrue(ConstructionSafeMappedRegistry.class.isInstance(ReignOfNetherRegistries.BUILDING));
        assertTrue(ConstructionSafeMappedRegistry.class.isInstance(ReignOfNetherRegistries.PRODUCTION_ITEM));
        assertTrue(ConstructionSafeMappedRegistry.class.isInstance(ReignOfNetherRegistries.FACTIONS));
        assertTrue(ConstructionSafeMappedRegistry.class.isInstance(ReignOfNetherRegistries.DATA_TYPE));
        h.succeed();
    }

    @GameTest(templateNamespace="reignofnether_registry_concurrency", template="ore_mine_test_arena", batch="registry_concurrency", timeoutTicks=200)
    public static void concurrentRegistryRegisterPreservesEveryValueHolderAndNumericId(GameTestHelper h) throws Exception {
        // Calls the public vanilla Registry.register path used by core and addon, not a mock lock.
        for (int round = 0; round < 4; round++) {
            var registry = registry("automatic_" + round);
            int threads = 8, each = 256;
            Object[] values = new Object[threads * each];
            var executor = Executors.newFixedThreadPool(threads);
            var ready = new CountDownLatch(threads);
            var start = new CountDownLatch(1);
            var jobs = new ArrayList<Future<?>>();
            try {
                for (int thread = 0; thread < threads; thread++) {
                    final int worker = thread;
                    jobs.add(executor.submit(() -> {
                        ready.countDown();
                        assertTrue(start.await(10, TimeUnit.SECONDS));
                        for (int n = 0; n < each; n++) {
                            int index = worker * each + n;
                            Object value = new Object();
                            values[index] = value;
                            assertSame(value, Registry.register(registry, id(index), value));
                        }
                        return null;
                    }));
                }
                assertTrue(ready.await(10, TimeUnit.SECONDS));
                start.countDown();
                for (var job : jobs) job.get(30, TimeUnit.SECONDS);
                assertEquals(values.length, registry.size());
                var numericIds = new HashSet<Integer>();
                for (int n = 0; n < values.length; n++) {
                    Object value = values[n];
                    assertSame(value, registry.get(id(n)));
                    int numericId = registry.getId(value);
                    assertTrue(numericId >= 0 && numericId < values.length);
                    assertTrue(numericIds.add(numericId), "automatic IDs must not collide");
                    assertSame(value, registry.byId(numericId));
                    assertSame(value, registry.getHolder(ResourceKey.create(registry.key(), id(n))).orElseThrow().value());
                }
            } finally {
                start.countDown();
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            }
        }
        h.succeed();
    }

    @GameTest(templateNamespace="reignofnether_registry_concurrency", template="ore_mine_test_arena", batch="registry_concurrency", timeoutTicks=200)
    public static void directExplicitMappingAndAutomaticRegistrationShareIdState(GameTestHelper h) throws Exception {
        var registry = registry("explicit");
        int threads = 8, each = 128;
        Object[] values = new Object[threads * each];
        var executor = Executors.newFixedThreadPool(threads);
        var start = new CountDownLatch(1);
        var jobs = new ArrayList<Future<?>>();
        try {
            for (int thread = 0; thread < threads; thread++) {
                final int worker = thread;
                jobs.add(executor.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    for (int n = 0; n < each; n++) {
                        int index = worker * each + n;
                        Object value = values[index] = new Object();
                        registry.registerMapping(index, ResourceKey.create(registry.key(), id(index)), value, Lifecycle.stable());
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var job : jobs) job.get(30, TimeUnit.SECONDS);
            assertEquals(values.length, registry.size());
            for (int n = 0; n < values.length; n++) {
                assertSame(values[n], registry.get(id(n)));
                assertSame(values[n], registry.byId(n));
                assertEquals(n, registry.getId(values[n]));
            }
            Object next = Registry.register(registry, id(values.length), new Object());
            assertEquals(values.length, registry.getId(next));
            assertSame(next, registry.byId(values.length));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
        h.succeed();
    }

    private static void assertTrue(boolean value) { assertTrue(value, "registry concurrency assertion"); }
    private static void assertTrue(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void assertSame(Object expected, Object actual) { assertTrue(expected == actual, "registry value/holder identity mismatch"); }
    private static void assertEquals(int expected, int actual) { assertTrue(expected == actual, "expected " + expected + " but got " + actual); }

    private static ResourceLocation id(int index) {
        return ResourceLocation.fromNamespaceAndPath("registry_concurrency_test", "entry_" + index);
    }
}
