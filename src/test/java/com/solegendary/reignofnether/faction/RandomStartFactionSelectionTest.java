package com.solegendary.reignofnether.faction;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RandomStartFactionSelectionTest {
    private static Faction faction(String id) {
        Faction faction = new Faction().setKey(ResourceLocation.parse(id));
        faction.workerEntityType = ResourceLocation.parse(id + "_worker");
        faction.scoutEntityType = ResourceLocation.parse(id + "_scout");
        faction.capitolBuilding = ResourceLocation.parse(id + "_hall");
        return faction;
    }

    private static Map<ResourceLocation, Faction> roster(boolean addon) {
        Map<ResourceLocation, Faction> map = new LinkedHashMap<>();
        for (String id : List.of("villagers", "monsters", "piglins")) {
            Faction faction = faction("reignofnether:" + id);
            map.put(faction.key, faction);
        }
        if (addon) {
            Faction dream = faction("dreamseekers:dreamseekers").noCubeMap();
            // No survival wave function is needed for an ordinary playable faction.
            assertNull(dream.spawnWave);
            map.put(dream.key, dream);
        }
        return map;
    }

    @Test void everyInstalledStartFactionIsReachableByExactlyOneBoundedIndex() {
        var map = roster(true);
        var playable = new ArrayList<>(map.keySet());
        playable.add(ResourceLocation.parse("dreamseekers:dreamseekers"));
        var candidates = RandomStartFactionSelection.candidates(playable, map::get, ignored -> true);
        assertEquals(new ArrayList<>(map.values()), candidates);
        assertEquals(4, candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            final int index = i;
            assertSame(candidates.get(i), RandomStartFactionSelection.select(candidates, bound -> {
                assertEquals(4, bound);
                return index;
            }));
        }
        assertEquals("dreamseekers", candidates.get(3).key.getNamespace());
    }

    @Test void coreOnlyInstallationKeepsThreeOriginalFactions() {
        var map = roster(false);
        assertEquals(new ArrayList<>(map.values()), RandomStartFactionSelection.candidates(
                new ArrayList<>(map.keySet()), map::get, ignored -> true));
    }

    @Test void reservedUnplayableIncompleteMismatchedAndMissingRegistrationsCannotBeDrawn() {
        var map = roster(true);
        var expected = new ArrayList<>(map.values());
        for (String id : List.of("reignofnether:neutral", "reignofnether:none", "reignofnether:random",
                "test:disabled", "test:no_worker", "test:no_scout", "test:no_capitol", "test:missing_asset")) {
            Faction value = faction(id);
            map.put(value.key, value);
        }
        map.get(ResourceLocation.parse("test:disabled")).playable = false;
        map.get(ResourceLocation.parse("test:no_worker")).workerEntityType = null;
        map.get(ResourceLocation.parse("test:no_scout")).scoutEntityType = null;
        map.get(ResourceLocation.parse("test:no_capitol")).capitolBuilding = null;
        map.put(ResourceLocation.parse("test:mismatched"), faction("test:other"));
        var keys = new ArrayList<>(map.keySet());
        keys.add(ResourceLocation.parse("test:absent"));
        keys.add(null);
        assertEquals(expected, RandomStartFactionSelection.candidates(keys, map::get,
                candidate -> !candidate.key.equals(ResourceLocation.parse("test:missing_asset"))));
        assertEquals(4, expected.size());
        assertEquals(13, map.size(), "selection must not mutate the registry");
    }

    @Test void emptyRosterDoesNotDrawAndSelectionDoesNotMutateCandidates() {
        assertNull(RandomStartFactionSelection.select(List.of(), bound -> {
            fail("empty roster must not request a random draw");
            return 0;
        }));
        var faction = faction("dreamseekers:dreamseekers");
        var candidates = List.of(faction);
        assertSame(faction, RandomStartFactionSelection.select(candidates, bound -> {
            assertEquals(1, bound);
            return 0;
        }));
        assertEquals(List.of(faction), candidates);
    }

    @Test void actualStartUsesIndependentValidatedRosterWithoutChangingClassicWaveSemantics() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/player/PlayerServerEvents.java"));
        assertTrue(source.contains("RandomStartFactionSelection.candidates(Factions.PLAYABLE_FACTIONS"));
        assertTrue(source.contains("RandomStartFactionSelection.select(candidates, random::nextInt)"));
        assertTrue(source.contains("ENTITY_TYPES.containsKey(candidate.workerEntityType)"));
        assertTrue(source.contains("ENTITY_TYPES.containsKey(candidate.scoutEntityType)"));
        assertTrue(source.contains("BUILDING.containsKey(candidate.capitolBuilding)"));
        String start = source.substring(source.indexOf("public static void startRTS(int playerId, Vec3 pos, Faction faction, int startPosColorId)"),
                source.indexOf("ReignOfNether.LOGGER.info(\"[Player] startRTS:"));
        assertFalse(start.contains("CLASSIC_FACTIONS"));
        assertFalse(start.contains("List.of(Factions.VILLAGERS, Factions.MONSTERS, Factions.PIGLINS)"));
    }
}
