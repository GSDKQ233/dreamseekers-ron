package com.dreamseekers.progression;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pure late-state/callback checks plus actual laboratory hook wiring, not a GUI screenshot test. */
class LaboratoryResearchSlotStateTest {
    @Test void completionBetweenHoverAndClickCannotInvokeStalePurchase(){
        var completed=new AtomicBoolean(false);var eligible=new AtomicBoolean(true);var purchases=new AtomicInteger();
        var state=new LaboratoryResearchSlotState(completed::get,eligible::get,purchases::incrementAndGet);
        assertTrue(state.enabled());assertFalse(state.completed());assertEquals(0,purchases.get());
        completed.set(true); // late research response after the original hover
        assertTrue(state.completed());assertFalse(state.enabled());state.click();assertEquals(0,purchases.get());
        completed.set(false);eligible.set(false);state.click();assertEquals(0,purchases.get());
        eligible.set(true);state.click();assertEquals(1,purchases.get());
    }
    @Test void everyFixedIdRetainsItsSlotForEveryCompletionTransition(){
        List<String> ids=List.of("precision_tools","shell_weaving","contract_care","stone_siege","resonance_tuning","contract_capacity","luminous_resonance_1","luminous_resonance_2","stone_range","pulse_range","mother_nest","hand_harmony","light_extension");
        var completed=new HashSet<String>();var purchases=new ArrayList<String>();var slots=new LinkedHashMap<String,LaboratoryResearchSlotState>();
        for(String id:ids)slots.put(id,new LaboratoryResearchSlotState(()->completed.contains(id),()->true,()->purchases.add(id)));
        for(String id:ids){
            completed.add(id);assertEquals(ids,new ArrayList<>(slots.keySet()));assertEquals(13,slots.size());
            for(String candidate:ids){var state=slots.get(candidate);assertEquals(!completed.contains(candidate),state.enabled());state.completed();}
            slots.get(id).click();assertTrue(purchases.isEmpty(),"completed slot never buys");
        }
        assertEquals(16,3+slots.size());
    }
    @Test void onlyLaboratoryOneTimeButtonsArePinnedAndWindPathIsUntouched()throws Exception{
        String source=Files.readString(Path.of("addons/dreamseekers/src/main/java/com/dreamseekers/progression/DreamRepeatProductionList.java"));
        assertTrue(source.contains("for(int family=0;family<3;family++){int next=Math.min(4,s.level(family));result.add(DreamWindResearch.TIERS[family][next].getStartButton(p,keys[family]));}"));
        assertTrue(source.contains("for(var item:oneTimeItems())result.add(stableOneTimeButton(p,item))"));
        String[] expected={"DreamSpecialistResearch.PRECISION_TOOLS","DreamSpecialistResearch.SHELL_WEAVING","DreamSpecialistResearch.CONTRACT_CARE","DreamSpecialistResearch.STONE_SIEGE","ResonanceResearch.ITEM","ContractResearch.CAPACITY","LightResonanceResearch.FIRST","LightResonanceResearch.FINAL","AdvancedUnitResearch.items()"};
        int at=-1;for(String key:expected){int next=source.indexOf(key);assertTrue(next>at,key);at=next;}
        assertTrue(source.contains("ResearchClient.hasResearch(item.id())"));
        assertTrue(source.contains("var button=item.getStartButton(p,null)"));
        assertTrue(source.contains("button.isHidden=()->false"));assertTrue(source.contains("button.isEnabled=state::enabled"));
        assertTrue(source.contains("button.greyWhenDisabled=true"));assertTrue(source.contains("research.dreamseekers.completed_short"));
        assertTrue(source.contains("button.onLeftClick=state::click"));
        assertFalse(source.contains("ResearchQueueClient.start"));assertFalse(source.contains("nativeClick.run()"));
        assertFalse(source.contains(".removeIf("));assertFalse(source.contains(".filter("));
    }
}
