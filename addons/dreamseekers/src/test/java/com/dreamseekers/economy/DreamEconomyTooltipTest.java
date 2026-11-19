package com.dreamseekers.economy;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Source/UI contract regression; actual translated font layout requires client GUI QA. */
class DreamEconomyTooltipTest {
    private static String source() throws Exception {
        return Files.readString(Path.of("addons/dreamseekers/src/main/java/com/dreamseekers/economy/DreamEconomyResearch.java"));
    }
    @Test void bothPrerequisitesSurviveEveryHoverRefreshRatherThanOnlyButtonCreation() throws Exception {
        String source=source();
        int start=source.indexOf("@Override protected java.util.List<net.minecraft.util.FormattedCharSequence> liveTooltip");
        assertTrue(start>=0,"must override the live refresh hook");
        String tooltip=source.substring(start,source.indexOf("@Override public StartProductionButton getStartButton",start));
        for(String field:new String[]{"Component.translatable(title)","Component.translatable(description)",
            "research.economy.cost","research.economy.extra_cost","resourceLabel(DreamPassiveEconomy.SHADOW),shadow",
            "ascended?\"research.dreamseekers.requires_ascended_tree\":\"research.dreamseekers.requires_dream_owner\"",
            "research.economy.refund_notice","return wrapTooltip(lines)"})assertTrue(tooltip.contains(field),field);
        assertTrue(tooltip.indexOf("requires_ascended_tree")<tooltip.indexOf("research.economy.refund_notice"));
        assertFalse(source.contains("button.tooltipLines="),"creation-only tooltip would be discarded");
        String base=Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/research/economy/ReceiptBackedResearchItem.java"));
        assertTrue(base.contains("this.tooltipLines=ReceiptBackedResearchItem.this.liveTooltip(p);"));
        assertTrue(base.contains("mc.font.split(component,width)"));
        assertTrue(base.contains("getWindow().getGuiScaledWidth()"));
    }
    @Test void liveQuoteAndRateRemainLogicalComponentsUntilSingleWrappingPass() throws Exception {
        String source=source();
        for(String live:new String[]{"var cost=getCost(true,p.ownerName)","cost.food,cost.wood,cost.ore",
            "ProductionWorkRates.estimatedSeconds(cost.ticks,ProductionWorkRates.percent(p,this))",
            "new java.util.ArrayList<Component>()"})assertTrue(source.contains(live),live);
        assertFalse(source.contains("getVisualOrderText()"));
        assertFalse(source.contains(".getString()"));
        assertFalse(source.contains("cost.ticks/20"));
    }
    @Test void clientAndServerPrerequisiteAndReceiptGatesRemainIntact() throws Exception {
        String source=source();
        for(String gate:new String[]{"p->eligibleHost(p,ascended)","DreamPassiveEconomy.isDreamOwner(PlayerServerEvents.getRTSPlayer(p.ownerName))",
            "(!ascended||tree.getUpgradeLevel(p)==1)","var base=button.isEnabled", "button.isEnabled=()->base.get()",
            "DreamPassiveEconomy.isDreamOwner(PlayerClientEvents.getRTSPlayer(p.ownerName))", "(!ascended||p.getBuilding().getUpgradeLevel(p)==1)"})
            assertTrue(source.contains(gate),gate);
    }
}
