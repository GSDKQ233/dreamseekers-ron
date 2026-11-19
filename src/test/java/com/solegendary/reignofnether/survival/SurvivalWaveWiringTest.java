package com.solegendary.reignofnether.survival;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Source guards complement pure sequence tests; these do not emulate a Forge server. */
class SurvivalWaveWiringTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/survival/" + name + ".java"));
    }

    @Test void constructedWavesConsumeSeededSurvivalOnlySequence() throws Exception {
        String wave = source("Wave");
        assertTrue(wave.contains("this.faction = WaveFactionSequence.atWave(FACTIONS, number, Factions.NONE)"));
        assertTrue(wave.contains("Factions.SURVIVAL_FACTIONS.stream()"));
        assertTrue(wave.contains(".sorted(Comparator.comparing(ResourceLocation::toString))"));
        assertTrue(wave.contains("faction != Factions.NONE && faction.spawnWave != null"));
        assertTrue(wave.contains("WaveFactionSequence.generate(factions, randomSeed, 30)"));
        assertFalse(wave.contains("CLASSIC_FACTIONS"));
        assertFalse(wave.contains("getRandomItem"));
        assertFalse(wave.contains("waves.clear()"));
    }

    @Test void joiningClientsReceiveSeedBeforeWaveNumberAndEnable() throws Exception {
        String server = source("SurvivalServerEvents");
        int join = server.indexOf("void onPlayerJoin(");
        int seed = server.indexOf("SurvivalClientboundPacket.setWaveRandomSeed", join);
        int number = server.indexOf("SurvivalClientboundPacket.setWaveNumber", join);
        int enable = server.indexOf("SurvivalClientboundPacket.enableAndSetDifficulty", join);
        assertTrue(join >= 0 && seed > join && number > seed && enable > number);
        String client = source("SurvivalClientEvents");
        int setter = client.indexOf("void setRandomSeed(");
        assertTrue(client.indexOf("Wave.reseedWaves()", setter) > client.indexOf("Wave.randomSeed = number", setter));
    }
    @Test void repairGameTestsUseEnabledTemplateNamespaceAndRealTemplates() throws Exception {
        String[][] fixtures = {
                {"building/PendingConstructionGameTests.java", "reignofnether_pending_construction"},
                {"fogofwar/NeutralFogLifecycleGameTests.java", "reignofnether_neutral_fog_lifecycle"},
                {"survival/SurvivalSequenceGameTests.java", "reignofnether_survival_sequence"}
        };
        for (String[] fixture : fixtures) {
            String text = Files.readString(Path.of("src/gametest/java/com/solegendary/reignofnether/" + fixture[0]));
            var constant = java.util.regex.Pattern.compile("NAMESPACE\\s*=\\s*\"([^\"]+)\"").matcher(text);
            if (constant.find()) text = text.replace("templateNamespace=NAMESPACE",
                    "templateNamespace=\"" + constant.group(1) + "\"");
            var namespaces = java.util.regex.Pattern.compile("templateNamespace\\s*=\\s*\"([^\"]+)\"").matcher(text);
            int count = 0;
            while (namespaces.find()) {
                assertEquals(fixture[1], namespaces.group(1), "Forge filters by template namespace");
                count++;
            }
            assertTrue(count > 0, "must declare actual GameTests");
            assertTrue(Files.isRegularFile(Path.of("src/gametest/resources/data/" + fixture[1]
                    + "/structures/ore_mine_test_arena.nbt")), "template must exist in enabled namespace");
        }
    }
}
