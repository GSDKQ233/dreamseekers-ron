package com.dreamseekers.unitprogression;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Static artifact/UI contract; paid transactions and effects require server tests. */
class ExpansionResearchPresentationTest {
    private static final Path ROOT=Path.of("addons/dreamseekers/src/main");
    @Test void fourLocalizedResearchEntriesAndExistingIconsExist() throws Exception {
        for(String locale:new String[]{"zh_cn","en_us"}){
            var json=JsonParser.parseString(Files.readString(ROOT.resolve("resources/assets/dreamseekers/lang/"+locale+".json"))).getAsJsonObject();
            for(String id:new String[]{"air_superiority","breaching_structure","ember_inhibition","aaroncos_vortex","hand_pairing"})
                for(String suffix:new String[]{"",".desc",".requires"}){
                    String key="research.dreamseekers."+id+suffix;
                    assertTrue(json.has(key)&&!json.get(key).getAsString().isBlank(),key);
                }
        }
        for(String icon:new String[]{"bone_wing_unit","shadow_golem_unit","ash_bone_wing_unit","skill_shadow_hand_grip","shadow_hand_unit"})
            assertTrue(Files.size(ROOT.resolve("resources/assets/dreamseekers/textures/icons/"+icon+".png"))>0,icon);
    }
    @Test void newResearchIsAppendOnlyAndExplicitlyVisible() throws Exception {
        String registry=Files.readString(ROOT.resolve("java/com/dreamseekers/DreamseekersMod.java"));
        assertTrue(registry.indexOf("ExpansionResearch.register")>registry.indexOf("AdvancedUnitResearch.register"));
        String buttons=Files.readString(ROOT.resolve("java/com/dreamseekers/progression/DreamRepeatProductionList.java"));
        assertTrue(buttons.contains("ExpansionResearch.items()"));
        assertTrue(buttons.indexOf("ExpansionResearch.items()")>buttons.indexOf("AdvancedUnitResearch.items()"));
    }
}
