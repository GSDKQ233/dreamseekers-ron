// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AutocastTooltipTest {
    private static final String SKILL = "ability.dreamseekers.";
    private static final String MODE = SKILL + "unit_skill.cast_mode.";
    private static final Pattern TOKENS = Pattern.compile("%(?:[0-9]+\\$)?s|%%|[0-9]+(?:\\.[0-9]+)?");

    private static JsonObject language(String locale) throws Exception {
        var path = Path.of("addons/dreamseekers/src/main/resources/assets/dreamseekers/lang/" + locale + ".json");
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static TranslatableContents translation(Component component) {
        return assertInstanceOf(TranslatableContents.class, component.getContents());
    }

    @Test void tooltipShowsLivePreferenceSeparatelyFromDefaultAndManualOnly() {
        UnitSkill skill = UnitSkill.JELLY_FLOW;
        for (Boolean current : new Boolean[]{null, false, true}) {
            var header = translation(UnitSkillAbility.castMode(skill, current));
            assertEquals(MODE + "status", header.getKey());
            Object[] args = header.getArgs();
            assertEquals(2, args.length);
            assertEquals(MODE + (current == null ? "unknown" : current ? "on" : "off"),
                    translation((Component) args[0]).getKey());
            assertEquals(MODE + (skill.defaultAutocast() ? "on" : "off"), translation((Component) args[1]).getKey());
        }
        for (UnitSkill manual : Arrays.stream(UnitSkill.values()).filter(s -> !s.supportsAutocast()).toList())
            assertEquals(MODE + "manual", translation(UnitSkillAbility.castMode(manual, true)).getKey());
    }

    @Test void everySupportedSkillHasOneTriggerInEachLanguageWithMatchingNumbers() throws Exception {
        var en = language("en_us");
        var zh = language("zh_cn");
        var manual = EnumSet.of(UnitSkill.MOTHER_NEST, UnitSkill.MOTHER_BROOD, UnitSkill.WEEPING_ECHOES,
                UnitSkill.AARONCOS_LEFT_PRESS, UnitSkill.AARONCOS_RIGHT_SHOCK, UnitSkill.AARONCOS_RIGHT_VORTEX);
        assertEquals(23, UnitSkill.values().length, "adding a skill requires an explicit control-mode decision");
        var automatic = EnumSet.complementOf(manual);
        assertEquals(17, automatic.size());
        for (var skill : UnitSkill.values()) {
            assertEquals(automatic.contains(skill), skill.supportsAutocast(), skill.name());
            assertEquals(automatic.contains(skill), skill.defaultAutocast(), skill.name());
        }
        Set<String> triggers = Arrays.stream(UnitSkill.values()).filter(UnitSkill::supportsAutocast)
                .map(s -> SKILL + s.id() + ".autocast_trigger").collect(Collectors.toSet());
        for (var lang : List.of(en, zh))
            assertEquals(triggers, lang.keySet().stream().filter(k -> k.endsWith(".autocast_trigger")).collect(Collectors.toSet()));
        for (var key : triggers) {
            assertFalse(en.get(key).getAsString().isBlank(), key);
            assertFalse(zh.get(key).getAsString().isBlank(), key);
            assertEquals(tokens(en, key), tokens(zh, key), key);
        }
        for (var lang : List.of(en, zh)) {
            assertEquals(List.of("%1$s", "%2$s"), tokens(lang, MODE + "status"));
            for (var suffix : List.of("on", "off", "unknown", "manual", "controls", "cancel"))
                assertFalse(lang.get(MODE + suffix).getAsString().isBlank(), MODE + suffix);
            for (var key : List.of("jelly_flow.tooltip3", "fox_blessing.tooltip3", "snail_shell_formation.tooltip6"))
                assertEquals(tokens(lang, SKILL + key), tokens(lang, SKILL + key + ".autocast"), key);
        }
    }

    private static List<String> tokens(JsonObject lang, String key) {
        return TOKENS.matcher(lang.get(key).getAsString()).results().map(m -> m.group()).toList();
    }
}
