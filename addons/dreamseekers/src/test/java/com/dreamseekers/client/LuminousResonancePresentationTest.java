package com.dreamseekers.client;

import com.dreamseekers.skill.SkillPotencyView;
import com.dreamseekers.skill.UnitSkill;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Headless presentation/contract checks, distinct from a rendered Minecraft UI test. */
class LuminousResonancePresentationTest {
    private static final String MAIN="addons/dreamseekers/src/main/";
    private String java(String path)throws Exception{return Files.readString(Path.of(MAIN+"java/com/dreamseekers/"+path+".java"));}
    @Test void displayedPotencyExactlyMatchesAllThreeApprovedTiers(){
        String[][] expected={{"1","12","2","20","15","10"},{"1.2","14.4","2.4","24","18","12"},{"1.5","18","3","30","22.5","15"}};
        for(int tier=0;tier<3;tier++){var view=new SkillPotencyView(tier);assertArrayEquals(expected[tier],new String[]{view.multiplier(),view.shieldOrHealingTotal(),view.healingPulse(),view.foxPercent(),view.reboundPercent(),view.tidePercent()});}
        assertThrows(IllegalArgumentException.class,()->new SkillPotencyView(-1));assertThrows(IllegalArgumentException.class,()->new SkillPotencyView(3));
        assertEquals(List.of(UnitSkill.GEL_GUARD,UnitSkill.JELLY_FLOW,UnitSkill.FOX_BLESSING),Arrays.stream(UnitSkill.values()).filter(SkillPotencyView::supports).toList());
    }
    @Test void originalTwentyOneSkillOrdinalsAndBaselineDurationsNeverChange(){
        assertEquals(List.of("GEL_GUARD","JELLY_FLOW","FOX_BLESSING","WIND_DASH","COORDINATED_MARK","FOX_DREAM_SEED","FIREFLY_RUSH","BEETLE_ADVANCE","SNAIL_SHELL_FORMATION","STONE_HEAVY_SHOT","SHADOW_CORROSION_TOUCH","SQUEAL_SUPPRESSION_WAVE","TERRORBEAK_DREAD_ROAR","FRIENDLY_DEBT_CONTRACT","SHADOW_HAND_GRIP","ASH_EMBER_LANDING","SHADOW_GOLEM_STRIKE","MOTHER_BROOD","WEEPING_ECHOES","AARONCOS_LEFT_PRESS","AARONCOS_RIGHT_SHOCK"),Arrays.stream(UnitSkill.values()).limit(21).map(Enum::name).toList());
        assertEquals(UnitSkill.MOTHER_NEST,UnitSkill.values()[21]);
        assertArrayEquals(new int[]{360,240,480},new int[]{UnitSkill.GEL_GUARD.cooldownTicks(),UnitSkill.JELLY_FLOW.cooldownTicks(),UnitSkill.FOX_BLESSING.cooldownTicks()});
        assertArrayEquals(new int[]{160,120,120},new int[]{UnitSkill.GEL_GUARD.durationTicks(),UnitSkill.JELLY_FLOW.durationTicks(),UnitSkill.FOX_BLESSING.durationTicks()});
    }
    @Test void bothLocalesProvideActualValuesPlaceholdersAndNoObsoleteFoxCap()throws Exception{
        for(String locale:List.of("zh_cn","en_us")){
            var lang=JsonParser.parseString(Files.readString(Path.of(MAIN+"resources/assets/dreamseekers/lang/"+locale+".json"))).getAsJsonObject();
            for(String key:List.of("research.dreamseekers.luminous_resonance_1","research.dreamseekers.luminous_resonance_2","research.dreamseekers.luminous_resonance_1.desc","research.dreamseekers.luminous_resonance_2.desc","research.dreamseekers.luminous_resonance_1.requires","research.dreamseekers.luminous_resonance_2.requires","research.dreamseekers.luminous_resonance.scope","research.dreamseekers.luminous_resonance.cast","ability.dreamseekers.unit_skill.amplification.active","ability.dreamseekers.unit_skill.amplification.baseline","ability.dreamseekers.unit_skill.amplification.unavailable")){assertTrue(lang.has(key));assertFalse(lang.get(key).getAsString().isBlank());}
            for(String skill:List.of("gel_guard","jelly_flow","fox_blessing")){String line=lang.get("ability.dreamseekers."+skill+".tooltip1").getAsString();for(String index:List.of("%1$s","%2$s","%3$s","%4$s"))assertTrue(line.contains(index),skill+" missing "+index);}
            assertTrue(lang.get("ability.dreamseekers.jelly_flow.tooltip1").getAsString().contains("%5$s"));
            assertFalse(lang.get("ability.dreamseekers.fox_blessing.tooltip3").getAsString().contains("25"));
            assertTrue(lang.get("research.dreamseekers.luminous_resonance_1.desc").getAsString().contains("1.2"));assertTrue(lang.get("research.dreamseekers.luminous_resonance_2.desc").getAsString().contains("1.5"));
        }
    }
    @Test void privateUiUsesFreshServerStateAndRefreshesCachedTooltip()throws Exception{
        var client=java("skill/SkillClient");assertTrue(client.contains("sync.amplificationTier()"));assertTrue(client.contains("view==null||!SkillPotencyView.supports(skill)?0:view.amplificationTier()"));
        assertTrue(client.contains("CLOCK.fresh(view.receivedAt(), 40)"));assertTrue(client.contains("view.revision()!=REVISIONS.getOrDefault"));assertTrue(client.contains("!owned(source)"));
        var ability=java("skill/UnitSkillAbility");assertTrue(ability.contains("tooltipLines=UnitSkillAbility.this.tooltip(source)"));assertTrue(ability.contains("new SkillPotencyView(SkillClient.amplificationTier(source,skill))"));
        assertTrue(ability.contains("range,duration,cooldown,primary,potency.healingPulse()"));
        assertFalse(ability.contains("ResonanceNetwork"));assertFalse(ability.contains("LightResonanceResearch"));
    }
    @Test void tierIsOnlyServerToClientAndResearchReusesOriginalLiveQuotes()throws Exception{
        var network=java("skill/SkillNetwork");assertTrue(network.contains("VERSION = \"19\""));
        assertTrue(network.contains("SkillNetwork::acceptsProtocol, SkillNetwork::acceptsProtocol"));assertTrue(network.contains("SkillAmplification.currentTier(source,packet.skill())"));
        int sync=network.indexOf("public record Sync(");assertTrue(sync>0);assertFalse(network.substring(network.indexOf("public record Cast("),sync).contains("amplificationTier"));
        assertTrue(network.contains("Sync.class, 1, NetworkDirection.PLAY_TO_CLIENT"));
        var research=java("resonance/LightResonanceResearch");assertTrue(research.contains("var lines=super.liveTooltip(p)"));assertTrue(research.contains("WorldTreeModel.Branch.BASE"));assertFalse(research.contains("tree.ascended()"));
        String receipt=Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/research/economy/ReceiptBackedResearchItem.java"));
        assertTrue(receipt.contains("var c=getCost(true,p.ownerName)"));assertTrue(receipt.contains("ProductionWorkRates.estimatedSeconds(c.ticks,ProductionWorkRates.percent(p,this))"));
    }
}
