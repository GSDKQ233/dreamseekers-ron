package com.solegendary.reignofnether.resources;

import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards shutdown listener order; the two-JVM GameTest proves persisted values. */
class ResourcesShutdownOrderTest {
    private static EventPriority priority(String className) throws ReflectiveOperationException {
        // Inspect the listener contract without initializing Minecraft registries.
        Class<?> listener = Class.forName(className, false, getClassLoader());
        return listener.getDeclaredMethod("onServerStopping", ServerStoppingEvent.class)
                .getAnnotation(SubscribeEvent.class).priority();
    }

    private static ClassLoader getClassLoader() {
        return ResourcesShutdownOrderTest.class.getClassLoader();
    }

    @Test
    void resourceSnapshotRunsBeforeUnitAndBuildingTeardown() throws ReflectiveOperationException {
        EventPriority resources = priority("com.solegendary.reignofnether.resources.ResourcesServerEvents");
        EventPriority units = priority("com.solegendary.reignofnether.unit.UnitServerEvents");
        EventPriority buildings = priority("com.solegendary.reignofnether.building.BuildingServerEvents");
        assertEquals(EventPriority.HIGHEST, resources);
        assertTrue(resources.ordinal() < units.ordinal(), "cargo must be read before the unit list is cleared");
        assertTrue(resources.ordinal() < buildings.ordinal(), "pending queues must be read before cancellation and teardown");
    }
}
