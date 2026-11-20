// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.client.DreamTooltipLayout;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** These first tooltip lines spell their numbers out in the text; keep them equal to the code constants. */
class HardCodedSkillNumbersTest {
    private static List<String> numbers(String lang, String key) throws Exception {
        String json = Files.readString(Path.of("addons/dreamseekers/src/main/resources/assets/dreamseekers/lang/" + lang + ".json"));
        var line = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        assertTrue(line.find(), lang + " " + key);
        var found = new ArrayList<String>();
        var number = Pattern.compile("\\d+(?:\\.\\d+)?").matcher(line.group(1));
        while (number.find()) found.add(number.group());
        return found;
    }
    private static String seconds(int ticks) { return DreamTooltipLayout.number(ticks / 20.0); }
    private static void expect(UnitSkill skill, String... values) throws Exception {
        for (String lang : new String[]{"en_us", "zh_cn"}) {
            var shown = numbers(lang, "ability.dreamseekers." + skill.id() + ".tooltip1");
            for (String value : values) assertTrue(shown.contains(value), lang + " " + skill.id() + " should show " + value + " but shows " + shown);
        }
    }
    @Test void summonsShowTheirDurationAndCooldown() throws Exception {
        for (UnitSkill skill : new UnitSkill[]{UnitSkill.MOTHER_BROOD, UnitSkill.WEEPING_ECHOES})
            expect(skill, seconds(skill.durationTicks()), seconds(skill.cooldownTicks()));
    }
    @Test void aaroncosHandsShowTheirWindupRangeAndCooldown() throws Exception {
        expect(UnitSkill.AARONCOS_LEFT_PRESS, seconds(UnitSkill.AARONCOS_LEFT_PRESS.durationTicks()), seconds(UnitSkill.AARONCOS_LEFT_PRESS.cooldownTicks()));
        expect(UnitSkill.AARONCOS_RIGHT_SHOCK, seconds(UnitSkill.AARONCOS_RIGHT_SHOCK.durationTicks()), seconds(UnitSkill.AARONCOS_RIGHT_SHOCK.cooldownTicks()));
        expect(UnitSkill.AARONCOS_RIGHT_VORTEX, Integer.toString(UnitSkill.AARONCOS_RIGHT_VORTEX.range()), seconds(UnitSkill.AARONCOS_RIGHT_VORTEX.cooldownTicks()));
    }
}
