package com.solegendary.reignofnether.sounds;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Headless actual fade lifecycle plus auxiliary manager wiring; not an audio-engine acceptance. */
class FadeableMusicLifecycleTest {
    @Disabled("Requires real client/Forge transformed bootstrap: SoundEvent registry init fails in plain JUnit; vanilla Bootstrap then fails Forge NetworkEvent listener-list construction without its transformed no-arg constructor. Assertions retained for a supported runtime; not a passing audio test.")
    @Test void realLoopingInstanceStopsWhenRetired() {
        SoundEvent event = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("reignofnether", "test_fade"));
        FadeableMusicInstance instance = new FadeableMusicInstance(event);
        assertTrue(instance.isLooping());
        assertFalse(instance.isStopped());
        instance.startFadeOut();
        for (int i=0; i<102; i++) instance.tick();
        assertTrue(instance.isStopped());
        assertFalse(instance.isLooping());
        assertEquals(0f, instance.getVolume());
    }

    @Test void managerRetiresDifferentPreviousInstanceBeforeReplacingItsOnlyReference() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/sounds/SoundClientEvents.java"));
        int start = source.indexOf("public static void playFadeableMusicInstance(FadeableMusicInstance instance, int tickLength)");
        int end = source.indexOf("public static void stopFadeableMusicInstance()", start);
        assertTrue(start >= 0 && end > start);
        String body = source.substring(start, end);
        int retire = body.indexOf("if (customSong != instance) stopFadeableMusicInstance();");
        int replace = body.indexOf("customSong = instance;");
        assertTrue(retire >= 0 && replace > retire, "source wiring must retire old loop before overwriting the handle");
    }
}
