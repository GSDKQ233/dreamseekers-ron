package com.solegendary.reignofnether.sounds;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Coordinate algebra and source wiring only: does not claim an actual audio-engine or GUI test. */
class SoundPositionTransformTest {
    @Test void remappingPreservesDistanceRelativeToVirtualListenerWithoutDoubleTranslation() {
        Vec3 physical = new Vec3(100, 164, 50), virtual = new Vec3(10, 64, 20), sound = new Vec3(16, 64, 23);
        Vec3 remapped = physical.add(sound.subtract(virtual));
        assertEquals(sound.distanceTo(virtual), remapped.distanceTo(physical), 1.0E-10);
        assertEquals(new Vec3(106, 164, 53), remapped);
    }

    @Test void elevatedPhysicalCameraMustNotMuteNearbyGroundSound() {
        Vec3 physical = new Vec3(0, 164, 0), virtual = new Vec3(0, 64, 0), near = new Vec3(1, 64, 0);
        assertTrue(near.distanceTo(physical) > 96);
        assertTrue(near.distanceTo(virtual) < 96);
        assertTrue(new Vec3(100, 64, 0).distanceTo(virtual) > 96);
    }

    @Test void actualRangeCallerUsesListenerCoordinatesAndRemapCallerUsesSoundOffset() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/sounds/SoundClientEvents.java"));
        int noarg = source.indexOf("public static Vec3 getOrthoviewSoundPos()");
        int positional = source.indexOf("public static Vec3 getOrthoviewSoundPos(@Nullable Vec3 originalPos)");
        int helper = source.indexOf("private static Vec3 getVirtualListenerPos()");
        assertTrue(noarg >= 0 && positional > noarg && helper > positional);
        String listener = source.substring(noarg, positional);
        assertTrue(listener.contains("if (player == null) return null;"));
        assertTrue(listener.contains("OrthoviewClientEvents.isEnabled() ? getVirtualListenerPos() : player.position()"));
        String sound = source.substring(positional, helper);
        assertTrue(sound.contains("originalPos.subtract(virtualListener)"));
        assertTrue(sound.contains("return player.position().add(offset);"));
        assertTrue(source.contains("Vec3 listenerPos = getOrthoviewSoundPos();\n        if (listenerPos == null) return;"));
    }
}
