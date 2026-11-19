package com.dreamseekers.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.solegendary.reignofnether.research.economy.EconomyRequestWindow;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Resource/manifest contracts only: these checks do not claim client rendering or gameplay coverage. */
class BuildingIntegrationMetadataTest {
    private static final Path ROOT = Path.of("addons/dreamseekers");
    private JsonObject read(String relative) throws Exception {
        return JsonParser.parseString(Files.readString(ROOT.resolve(relative))).getAsJsonObject();
    }
    private JsonObject language(String locale) throws Exception {
        return read("src/main/resources/assets/dreamseekers/lang/" + locale + ".json");
    }
    @Test void bothClientsTranslateEverySharedEconomyFailureStatus() throws Exception {
        // Execute the pure replay-window class, never any connection or packet handler.
        var window = new EconomyRequestWindow<String>();
        Set<String> statuses = new LinkedHashSet<>();
        statuses.add(window.admit(0, "a").status());
        statuses.add(window.admit(Long.MAX_VALUE, "a").status());
        assertTrue(window.admit(1, "a").execute());
        statuses.add(window.admit(1, "a").status()); // unfinished action fails closed
        statuses.add(window.admit(1, "b").status());
        for (int i = 2; i <= EconomyRequestWindow.CAPACITY + 1; i++) window.admit(i, "a");
        statuses.add(window.admit(1, "a").status());
        statuses.add("stale_session"); // outer account/session gate, before the pure window
        assertEquals(Set.of("invalid_sequence", "reconnect_required", "failed_closed",
                "replay_mismatch", "stale_request", "stale_session"), statuses);
        String gate = Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/research/economy/EconomyActionGate.java"));
        assertTrue(gate.contains("return \"stale_session\""));
        for (String locale : new String[]{"en_us", "zh_cn"}) {
            var lang = language(locale);
            for (String prefix : new String[]{"neutral_contract.", "ecology_workposts."})
                for (String status : statuses) {
                    String key = prefix + status;
                    assertTrue(lang.has(key), locale + " missing " + key);
                    String value = lang.get(key).getAsString();
                    assertFalse(value.isBlank(), key);
                    assertFalse(value.startsWith(prefix), "raw key is not a translation: " + key);
                }
        }
    }
    @Test void maintainedLanguagesKeepIdenticalKeySets() throws Exception {
        assertEquals(language("en_us").keySet(), language("zh_cn").keySet());
    }
    @Test void allElevenManifestRolesDescribeEnabledCurrentServices() throws Exception {
        var manifest = read("structure_manifest.json");
        assertEquals(2, manifest.get("runtime_integration_revision").getAsInt());
        var structures = manifest.getAsJsonArray("structures");
        assertEquals(11, structures.size());
        Set<String> ids = new LinkedHashSet<>();
        for (var raw : structures) {
            var item = raw.getAsJsonObject();
            assertTrue(ids.add(item.get("id").getAsString()), "unique registered building identity");
            assertTrue(item.get("registered").getAsBoolean());
            assertEquals("enabled", item.get("function_status").getAsString());
            assertFalse(item.get("runtime_role").getAsString().isBlank());
            assertFalse(item.get("role_status").getAsString().contains("reserved and disabled"));
        }
        assertEquals(Set.of("dream_anchor", "reverie_garden", "dream_depot", "shadow_mine", "shadow_furnace",
                "beetle_sanctuary", "dream_hall", "resonance_laboratory", "contract_ecology", "resonance_dais", "shadow_watchtower"), ids);
    }
    @Test void originalGeometryHashesRemainExactAndSeparateFromRuntimeTransforms() throws Exception {
        var manifest = read("structure_manifest.json");
        assertTrue(manifest.get("asset_scope").getAsString().contains("original named base NBT asset"));
        for (var raw : manifest.getAsJsonArray("structures")) {
            var item = raw.getAsJsonObject();
            String id = item.get("id").getAsString();
            assertEquals(id, item.get("base_asset_id").getAsString());
            byte[] bytes = Files.readAllBytes(ROOT.resolve("src/main/resources/data/dreamseekers/structures/" + id + ".nbt"));
            assertEquals(item.get("sha256").getAsString(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), id);
            if (id.equals("reverie_garden")) {
                assertEquals("passive_food_population_garden_rest", item.get("runtime_role").getAsString());
                assertTrue(item.get("runtime_transform").getAsString().contains("dreamseekers:decorative_garden_wheat"));
            }
            if (id.equals("shadow_watchtower")) {
                var templates = item.getAsJsonObject("runtime_templates");
                assertEquals("shadow_watchtower", templates.get("legacy_saved_model").getAsString());
                String current = templates.get("new_construction").getAsString();
                assertEquals("shadow_watchtower_garrison_v2", current);
                assertTrue(Files.isRegularFile(ROOT.resolve("src/main/resources/data/dreamseekers/structures/" + current + ".nbt")));
            }
        }
    }
}
