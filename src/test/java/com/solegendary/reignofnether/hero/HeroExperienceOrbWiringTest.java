package com.solegendary.reignofnether.hero;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Source guard only; the save and reload itself is covered by HeroExperienceOrbReloadGameTests. */
class HeroExperienceOrbWiringTest {
    @Test void orbSavesItsHeroByUuidAndWaitsForItAfterALoad() throws Exception {
        String orb = Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/hero/HeroExperienceOrb.java"));
        int save = orb.indexOf("void addAdditionalSaveData(CompoundTag tag)");
        int load = orb.indexOf("void readAdditionalSaveData(CompoundTag tag)");
        int tick = orb.indexOf("public void tick()");
        assertTrue(save >= 0 && load > save && tick > load);
        assertTrue(orb.indexOf("tag.putUUID(TAG_HERO, heroUuid)", save) < load);
        assertTrue(orb.indexOf("tag.putInt(TAG_AGE, age)", save) < load);
        assertTrue(orb.indexOf("tag.getUUID(TAG_HERO)", load) < tick);
        assertTrue(orb.indexOf("resolveHero();", tick) > tick, "a loaded orb rebinds its hero before moving");
        assertTrue(orb.contains("serverLevel.getEntity(heroUuid)"));
        assertTrue(orb.contains("followingHero == null ? heroUuid == null : followingHero.isDeadOrDying()"));
        assertFalse(orb.contains("followingHero == null || followingHero.isDeadOrDying()"),
                "a null reference alone must not discard a saved orb");
    }
}
