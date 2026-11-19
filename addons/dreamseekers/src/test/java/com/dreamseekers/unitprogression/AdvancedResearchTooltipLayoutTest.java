package com.dreamseekers.unitprogression;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Width arithmetic and source/UI contract only; actual fonts and screenshot bounds need GUI QA. */
class AdvancedResearchTooltipLayoutTest {
    @Test void wrapsAgainstScaledWidthRatherThanPhysicalResolution(){
        assertEquals(310,AdvancedResearchTooltipLayout.wrapWidth(1364));
        assertEquals(310,AdvancedResearchTooltipLayout.wrapWidth(682));
        assertEquals(310,AdvancedResearchTooltipLayout.wrapWidth(455));
        assertEquals(310,AdvancedResearchTooltipLayout.wrapWidth(341));
        assertEquals(243,AdvancedResearchTooltipLayout.wrapWidth(273));
        assertEquals(198,AdvancedResearchTooltipLayout.wrapWidth(228));
        assertEquals(130,AdvancedResearchTooltipLayout.wrapWidth(160));
        assertEquals(50,AdvancedResearchTooltipLayout.wrapWidth(80));
    }
    @Test void everyWidthKeepsPositiveBoundedBudgetWithoutUnsafeMinimum(){
        for(int width=31;width<=4096;width++){
            int actual=AdvancedResearchTooltipLayout.wrapWidth(width);
            assertTrue(actual>0&&actual<=310&&actual<=width-30,"scaled width "+width);
        }
        for(int width:new int[]{Integer.MIN_VALUE,-1,0,1,30})assertEquals(1,AdvancedResearchTooltipLayout.wrapWidth(width));
        assertEquals(310,AdvancedResearchTooltipLayout.wrapWidth(Integer.MAX_VALUE));
    }
    @Test void preservesAllLogicalFieldsLiveCostAndSingleVisualOrdering()throws Exception{
        String source=Files.readString(Path.of("addons/dreamseekers/src/main/java/com/dreamseekers/unitprogression/AdvancedUnitResearch.java"));
        String tooltip=source.substring(source.indexOf("@Override protected java.util.List<net.minecraft.util.FormattedCharSequence> liveTooltip"),source.indexOf("@Override public StartProductionButton getStartButton"));
        for(String field:new String[]{"Component.translatable(key)","key+\".desc\"","research.economy.cost","research.economy.extra_cost","research.economy.refund_notice","key+\".requires\"","research.dreamseekers.advanced.once"})assertTrue(tooltip.contains(field),field);
        for(String live:new String[]{"getCost(true,p.ownerName)","cost.food,cost.wood,cost.ore","ProductionWorkRates.estimatedSeconds(cost.ticks","ProductionWorkRates.percent(p,this)","resourceLabel(DreamPassiveEconomy.SHADOW),shadowCost"})assertTrue(tooltip.contains(live),live);
        assertTrue(source.contains("this.shadowCost=shadow;"));
        assertTrue(source.contains("getWindow().getGuiScaledWidth()"));
        assertTrue(source.contains("mc.font.split(component,width)"));
        assertFalse(tooltip.contains("super.liveTooltip"));
        assertFalse(source.contains("getVisualOrderText()"));
        assertFalse(tooltip.contains(".getString()"));
    }
}
