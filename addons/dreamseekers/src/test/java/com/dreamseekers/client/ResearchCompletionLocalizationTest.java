package com.dreamseekers.client;

import com.dreamseekers.economy.DreamEconomyResearch;
import com.dreamseekers.progression.DreamWindResearch;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.solegendary.reignofnether.research.economy.CoreEconomyResearch;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Exact registry-identity completion keys, distinct from button title keys. No GUI claim. */
class ResearchCompletionLocalizationTest {
    private JsonObject language(String namespace, String locale) throws Exception {
        String root = namespace.equals("dreamseekers") ? "addons/dreamseekers/" : "";
        return JsonParser.parseString(Files.readString(Path.of(root + "src/main/resources/assets/" + namespace
                + "/lang/" + locale + ".json"))).getAsJsonObject();
    }
    private String completionKey(ResourceLocation id) {
        return "research." + id.getNamespace() + "." + id.getPath();
    }
    private String translated(JsonObject language, ResourceLocation id) {
        String key = completionKey(id);
        assertTrue(language.has(key), "Missing runtime completion key: " + key);
        String value = language.get(key).getAsString();
        assertFalse(value.isBlank());
        assertFalse(value.startsWith("research."), "raw localization key must not be the translation");
        return value;
    }
    @Test void actualCoreEconomyItemIdsResolveToTheirExistingButtonTitles() throws Exception {
        var ids = List.of(CoreEconomyResearch.workforce().id(), CoreEconomyResearch.salvage().id(),
                CoreEconomyResearch.smelting().id());
        var titles = List.of("research.economy.workforce", "research.economy.salvage", "research.economy.smelting");
        for (String locale : new String[] {"zh_cn", "en_us"}) {
            var lang = language("reignofnether", locale);
            for (int i = 0; i < ids.size(); i++)
                assertEquals(lang.get(titles.get(i)).getAsString(), translated(lang, ids.get(i)));
        }
    }
    @Test void actualDreamEconomyRegisteredConstantsResolveToButtonTitles() throws Exception {
        var ids = List.of(DreamEconomyResearch.CONVERSION_ID, DreamEconomyResearch.ASCENDANCY_ID);
        var titles = List.of("research.dreamseekers.shadow_conversion", "research.dreamseekers.world_tree_ascendancy");
        for (String locale : new String[] {"zh_cn", "en_us"}) {
            var lang = language("dreamseekers", locale);
            for (int i = 0; i < ids.size(); i++)
                assertEquals(lang.get(titles.get(i)).getAsString(), translated(lang, ids.get(i)));
        }
        // Pin this test to the real client's completion-key derivation, not the button's display key.
        String client = Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/research/ResearchClient.java"));
        assertTrue(client.contains("\"research.\" + researchItemName.getNamespace() + \".\" + researchItemName.getPath()"));
    }
    @Test void allFifteenRegisteredWindTierIdentityKeysHaveBothLanguages() throws Exception {
        // Wind progression uses private synchronization, not the generic completion toast today.
        // Still cover every registered tier identity so any displayed identity has a localized name.
        assertEquals(3, DreamWindResearch.NAMES.length);
        for (String locale : new String[] {"zh_cn", "en_us"}) {
            var lang = language("dreamseekers", locale);
            int count = 0;
            for (String family : DreamWindResearch.NAMES) for (int tier = 1; tier <= 5; tier++) {
                var id = ResourceLocation.fromNamespaceAndPath("dreamseekers", family + "_" + tier);
                String value = translated(lang, id);
                assertTrue(value.startsWith(lang.get("research.dreamseekers." + family).getAsString()));
                assertTrue(value.contains(Integer.toString(tier)));
                count++;
            }
            assertEquals(15, count);
        }
        String source = Files.readString(Path.of("addons/dreamseekers/src/main/java/com/dreamseekers/progression/DreamWindResearch.java"));
        assertTrue(source.contains("n<=5"));
        assertTrue(source.contains("NAMES[family]+\"_\"+tier"));
    }
}
