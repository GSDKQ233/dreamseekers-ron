package com.dreamseekers.skill;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pure width and source contracts only. Does not claim actual font or GUI screenshots passed. */
class SkillTooltipLayoutTest {
    private static String ability()throws Exception{return Files.readString(Path.of("addons/dreamseekers/src/main/java/com/dreamseekers/skill/UnitSkillAbility.java"));}
    @Test void scaledWidthUsesSameResearchBudgetAndNeverUnsafeMinimum(){
        int[] widths={1364,682,455,341,273,228,160,80,30,0,Integer.MIN_VALUE,Integer.MAX_VALUE};
        int[] expected={310,310,310,310,243,198,130,50,1,1,1,310};
        for(int i=0;i<widths.length;i++)assertEquals(expected[i],SkillTooltipLayout.wrapWidth(widths[i]));
        for(int width=31;width<=4096;width++){int actual=SkillTooltipLayout.wrapWidth(width);assertTrue(actual>0&&actual<=310&&actual<=width-30);}
    }
    @Test void allLogicalLinesAndLiveParametersRemainBeforeSingleSplit()throws Exception{
        String source=ability();String logical=source.substring(source.indexOf("private java.util.List<net.minecraft.util.FormattedCharSequence> tooltip(SkillUnit source)"),source.indexOf("private static final class ClientTooltip"));
        for(String required:new String[]{"List<Component> tooltip","Component.translatable(prefix).withStyle(Style.EMPTY.withBold(true))","prefix+\".tooltip1\"","prefix+\".tooltip2\"","prefix+\".tooltip3\"","duration,cooldown,primary,potency.healingPulse()","derived,extended?4.8:4.0","for(int line=4;line<=5;line++)","for(int line=4;line<=8;line++)","for(int line=4;line<=6;line++)","unit_skill.amplification.","unit_skill.extension.","mother_nest.state.","hand_harmony.","SkillClient.hasCurrentState","StoneHeavyShot.range(stone)","return ClientTooltip.wrap(tooltip);"})assertTrue(logical.contains(required),required);
        assertFalse(logical.contains("getVisualOrderText"));assertFalse(logical.contains(".getString()"));
        assertTrue(source.contains("mc.font.split(component,width)"));assertTrue(source.contains("getWindow().getGuiScaledWidth()"));
        assertTrue(source.contains("tooltipLines=UnitSkillAbility.this.tooltip(source)"));
    }
    @Test void staticAbilitiesDoNotBootstrapMinecraftOrChangeInputWiring()throws Exception{
        String source=ability();String setup=source.substring(0,source.indexOf("private java.util.List<net.minecraft.util.FormattedCharSequence> tooltip(SkillUnit source)"));
        assertFalse(setup.contains("Minecraft.getInstance"));
        assertEquals(1,source.split("Minecraft.getInstance",-1).length-1);
        assertTrue(source.contains("private static final class ClientTooltip"));
        assertTrue(source.contains("()->SkillClient.arm(source,skill),()->SkillClient.rightClick(source,skill)"));
        assertTrue(source.contains("skill==UnitSkill.MOTHER_NEST&&SkillClient.motherNestActive(source)"));
        assertTrue(source.contains("AdvancedUnitResearch.MOTHER_NEST_ID,true"));
        assertTrue(source.contains("super(null, skill.cooldownTicks(), skill.range(), 0, true, true)"));
    }
}
