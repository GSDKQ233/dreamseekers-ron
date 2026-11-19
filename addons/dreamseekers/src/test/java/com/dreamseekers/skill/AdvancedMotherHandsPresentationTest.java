package com.dreamseekers.skill;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
/** Static presentation/source guard only; server behavior is in AdvancedMotherHandsGameTests. */
class AdvancedMotherHandsPresentationTest {
    private static final Path ROOT=Path.of("addons/dreamseekers/src/main");
    private String source(String path)throws Exception{return Files.readString(ROOT.resolve("java/com/dreamseekers/"+path+".java"));}
    @Test void motherButtonIsAppendedWithoutChangingBroodOrHandCooldowns()throws Exception{
        assertEquals(0,UnitSkill.MOTHER_NEST.cooldownTicks());assertEquals(0,UnitSkill.MOTHER_NEST.durationTicks());
        assertEquals(900,UnitSkill.MOTHER_BROOD.cooldownTicks());assertEquals(400,UnitSkill.AARONCOS_LEFT_PRESS.cooldownTicks());assertEquals(600,UnitSkill.AARONCOS_RIGHT_SHOCK.cooldownTicks());
        String unit=source("unit/BeetleMotherUnit");assertTrue(unit.contains("abilities.add(com.dreamseekers.skill.UnitSkillAbility.MOTHER_NEST)"));assertTrue(unit.contains("abilities.add(com.dreamseekers.skill.UnitSkillAbility.MOTHER_BROOD)"));
    }
    @Test void bothLocalesExplainActualStanceAndBoundedHarmony()throws Exception{
        for(String locale:List.of("zh_cn","en_us")){
            var lang=JsonParser.parseString(Files.readString(ROOT.resolve("resources/assets/dreamseekers/lang/"+locale+".json"))).getAsJsonObject();
            for(String key:List.of("ability.dreamseekers.mother_nest","ability.dreamseekers.mother_nest.tooltip1","ability.dreamseekers.mother_nest.tooltip2","ability.dreamseekers.mother_nest.tooltip3","ability.dreamseekers.mother_nest.state.unknown","ability.dreamseekers.mother_nest.state.active","ability.dreamseekers.mother_nest.state.mobile","ability.dreamseekers.hand_harmony.active","ability.dreamseekers.hand_harmony.inactive","ability.dreamseekers.unit_skill.extension.active","ability.dreamseekers.unit_skill.extension.baseline"))assertTrue(lang.has(key)&&!lang.get(key).getAsString().isBlank(),key);
        }
    }
    @Test void serverRecomputesAllNewPrivateStateAndNoOldPairGateIsOpened()throws Exception{
        String network=source("skill/SkillNetwork");assertTrue(network.contains("SkillAmplification.extensionActive(source,packet.skill())"));assertTrue(network.contains("MotherNestStance.active(source.mob())"));assertTrue(network.contains("AaroncosHarmony.active(source)"));
        assertTrue(source("skill/AaroncosHarmony").contains("SkillEffects.hasNamedBuff(source.mob(),KEY,BuffKind.ATTACK_SPEED)"));
        assertFalse(source("skill/AaroncosHarmony").contains("AaroncosPairService"));
        assertTrue(source("aaroncos/AaroncosPairService").contains("UNAVAILABLE"));
        assertTrue(source("skill/MotherNestStance").contains("ACTIVE.remove(s,n)"));
    }
}
