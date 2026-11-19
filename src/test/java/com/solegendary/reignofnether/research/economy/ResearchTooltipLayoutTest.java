package com.solegendary.reignofnether.research.economy;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pure policy/field wiring only: rendered font bounds still require actual GUI verification. */
class ResearchTooltipLayoutTest {
    private static String read(String path)throws Exception{return Files.readString(Path.of(path));}
    @Test void widthTracksCurrentScaledWindowWithExistingResearchPolicy(){
        int[] widths={1364,682,455,341,273,228,160,80,30,0,Integer.MIN_VALUE,Integer.MAX_VALUE};
        int[] expected={310,310,310,310,243,198,130,50,1,1,1,310};
        for(int i=0;i<widths.length;i++)assertEquals(expected[i],ResearchTooltipLayout.wrapWidth(widths[i]));
        for(int width=31;width<=4096;width++){int actual=ResearchTooltipLayout.wrapWidth(width);assertTrue(actual>0&&actual<=310&&actual<=width-30);}
    }
    @Test void receiptLogicalFieldsAndLiveRatesRemainAndClientLoadingIsLate()throws Exception{
        String s=read("src/main/java/com/solegendary/reignofnether/research/economy/ReceiptBackedResearchItem.java");
        String body=s.substring(s.indexOf("protected java.util.List<net.minecraft.util.FormattedCharSequence> liveTooltip("),s.indexOf("protected static java.util.List<net.minecraft.util.FormattedCharSequence> wrapTooltip("));
        for(String field:new String[]{"new ArrayList<Component>()","Component.translatable(title)","Component.translatable(description)","getCost(true,p.ownerName)","research.economy.cost","c.food,c.wood,c.ore","ProductionWorkRates.estimatedSeconds(c.ticks,ProductionWorkRates.percent(p,this))","extensions.entrySet()","resourceLabel(e.getKey()),e.getValue()","research.economy.refund_notice","return wrapTooltip(lines)"})assertTrue(body.contains(field),field);
        assertFalse(body.contains("getVisualOrderText"));assertFalse(body.contains(".getString()"));
        assertTrue(s.contains("private static final class ClientTooltip"));assertTrue(s.contains("mc.font.split(component,width)"));assertTrue(s.contains("getWindow().getGuiScaledWidth()"));
        assertFalse(s.substring(0,s.indexOf("private static final class ClientTooltip")).contains("Minecraft.getInstance"));
        assertTrue(s.contains("ReceiptBackedResearchItem.this.liveTooltip(p)"));
    }
    @Test void windAndLuminousAppendOnlyLogicalFieldsUseSharedWrapperWithoutReordering()throws Exception{
        String wind=read("addons/dreamseekers/src/main/java/com/dreamseekers/progression/DreamWindResearch.java");
        String body=wind.substring(wind.indexOf("private java.util.List<net.minecraft.util.FormattedCharSequence> windTooltip"),wind.indexOf("@Override public StartProductionButton getStartButton"));
        for(String field:new String[]{"new ArrayList<Component>()","wind_level\",level,5","if(capped)","wind_max","wind_quote\",c.food,c.wood,c.ore,shadow","ProductionWorkRates.estimatedSeconds(c.ticks,ProductionWorkRates.percent(p,this))","wind_next_effect\",family==0?30+3*tier:family==1?140+10*tier:125+10*tier","wind_requires","research.economy.refund_notice","return wrapTooltip(text)"})assertTrue(body.contains(field),field);
        assertFalse(body.contains("getVisualOrderText"));assertFalse(body.contains(".getString()"));
        String light=read("addons/dreamseekers/src/main/java/com/dreamseekers/resonance/LightResonanceResearch.java");
        String appended=light.substring(light.indexOf("@Override protected java.util.List<net.minecraft.util.FormattedCharSequence> liveTooltip"),light.indexOf("@Override public StartProductionButton getStartButton"));
        assertTrue(appended.contains("var lines=super.liveTooltip(p)"));assertTrue(appended.contains("lines.addAll(wrapTooltip(java.util.List.of("));
        for(String key:new String[]{"+tier+\".requires\"","luminous_resonance.scope","luminous_resonance.cast"})assertTrue(appended.contains(key),key);
        assertFalse(appended.contains("getVisualOrderText"));assertFalse(appended.contains("wrapTooltip(lines)"));
    }
}
