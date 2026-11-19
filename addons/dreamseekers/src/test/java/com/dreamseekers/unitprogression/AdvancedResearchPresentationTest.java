package com.dreamseekers.unitprogression;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Static artifact/UI contract; paid transactions and effects require server tests. */
class AdvancedResearchPresentationTest {
    private static final Path ROOT=Path.of("addons/dreamseekers/src/main");
    @Test void fiveLocalizedResearchEntriesAndExistingIconsExist() throws Exception {
        for(String locale:new String[]{"zh_cn","en_us"}){
            var json=JsonParser.parseString(Files.readString(ROOT.resolve("resources/assets/dreamseekers/lang/"+locale+".json"))).getAsJsonObject();
            for(String id:new String[]{"stone_range","pulse_range","mother_nest","hand_harmony","light_extension"})
                for(String suffix:new String[]{"",".desc",".requires"}){
                    String key="research.dreamseekers."+id+suffix;
                    assertTrue(json.has(key)&&!json.get(key).getAsString().isBlank(),key);
                }
        }
        for(String icon:new String[]{"stone_spirit_thrower","resonance_dais","beetle_mother_unit","shadow_hand_unit","golden_fox_unit"})
            assertTrue(Files.size(ROOT.resolve("resources/assets/dreamseekers/textures/icons/"+icon+".png"))>0,icon);
    }
    @Test void newResearchIsAppendOnlyAndExplicitlyVisible() throws Exception {
        String registry=Files.readString(ROOT.resolve("java/com/dreamseekers/DreamseekersMod.java"));
        assertTrue(registry.indexOf("AdvancedUnitResearch.register")>registry.indexOf("LightResonanceResearch.register"));
        String buttons=Files.readString(ROOT.resolve("java/com/dreamseekers/progression/DreamRepeatProductionList.java"));
        assertTrue(buttons.contains("AdvancedUnitResearch.items()"));
        assertTrue(buttons.indexOf("AdvancedUnitResearch.items()")>buttons.indexOf("LightResonanceResearch.FINAL"));
    }
}
