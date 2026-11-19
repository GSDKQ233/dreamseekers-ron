package com.dreamseekers.unitprogression;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Resource/UI contract only. Gameplay is tested by the actual Forge specialist profile. */
class SpecialistPresentationTest {
    private static final Path ROOT=Path.of("addons/dreamseekers/src/main");
    @Test void allFourCompletionTitlesDescriptionsAndIconsExist() throws Exception {
        for(String locale:new String[]{"zh_cn","en_us"}) {
            var json=JsonParser.parseString(Files.readString(ROOT.resolve("resources/assets/dreamseekers/lang/"+locale+".json"))).getAsJsonObject();
            for(String id:new String[]{"precision_tools","shell_weaving","contract_care","stone_siege"}) {
                String key="research.dreamseekers."+id;
                assertTrue(json.has(key)&&!json.get(key).getAsString().isBlank(),key);
                assertTrue(json.has(key+".desc")&&!json.get(key+".desc").getAsString().isBlank(),key+".desc");
            }
        }
        for(String icon:new String[]{"firefly_worker","skill_snail_shell_formation","contract_ecology","stone_spirit_thrower"})
            assertTrue(Files.size(ROOT.resolve("resources/assets/dreamseekers/textures/icons/"+icon+".png"))>0);
    }
    @Test void existingWindFamiliesAndNewSpecialistsShareActualVisibleProductionList() throws Exception {
        String source=Files.readString(ROOT.resolve("java/com/dreamseekers/progression/DreamRepeatProductionList.java"));
        assertTrue(source.contains("family<3"));assertTrue(source.contains("DreamWindResearch.TIERS"));
        for(String item:new String[]{"PRECISION_TOOLS","SHELL_WEAVING","CONTRACT_CARE","STONE_SIEGE"})
            assertTrue(source.contains("DreamSpecialistResearch."+item),item);
    }
}
