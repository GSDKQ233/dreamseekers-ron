// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Pure presentation policy plus source wiring; rendered fonts and GUI scales need a client. */
class DreamTooltipLayoutTest {
    private static final String ROOT = "addons/dreamseekers/src/main/java/com/dreamseekers/";
    private static String source(String path) throws Exception { return Files.readString(Path.of(ROOT + path)); }

    @Test void wrapWidthMatchesTheSkillAndResearchBudget() {
        int[] widths = {1364, 480, 341, 273, 80, 30, 0, Integer.MIN_VALUE, Integer.MAX_VALUE};
        int[] expected = {310, 310, 310, 243, 50, 1, 1, 1, 310};
        for (int i = 0; i < widths.length; i++) assertEquals(expected[i], DreamTooltipLayout.wrapWidth(widths[i]));
    }

    @Test void wholeSecondsDropTheFractionAndRealFractionsKeepOneDecimal() {
        assertEquals("8", DreamTooltipLayout.number(8.0));
        assertEquals("18", DreamTooltipLayout.number(360 / 20.0));
        assertEquals("4", DreamTooltipLayout.number(4.0));
        assertEquals("9.6", DreamTooltipLayout.number(160 * 1.2 / 20.0));
        assertEquals("16.2", DreamTooltipLayout.number(360 * .9 / 20.0));
        assertEquals("4.8", DreamTooltipLayout.number(4.8));
        assertEquals("0.6", DreamTooltipLayout.number(12 / 20.0));
    }

    @Test void cooldownBadgeIsNeverABareNumber() {
        assertEquals("1s", DreamTooltipLayout.cooldownBadge(1));
        assertEquals("1s", DreamTooltipLayout.cooldownBadge(20));
        assertEquals("2s", DreamTooltipLayout.cooldownBadge(21));
        assertEquals("0s", DreamTooltipLayout.cooldownBadge(-5));
    }

    @Test void cutTextEndsWithAnEllipsisWithinTheBudget() {
        java.util.function.ToIntFunction<String> width = text -> text.length() * 6;
        java.util.function.BiFunction<String, Integer, String> prefix = (text, max) -> text.substring(0, Math.min(text.length(), max / 6));
        assertEquals("short", DreamTooltipLayout.ellipsize("short", 60, width, prefix));
        String cut = DreamTooltipLayout.ellipsize("a much longer rule line", 60, width, prefix);
        assertTrue(cut.endsWith("…"));
        assertTrue(width.applyAsInt(cut) <= 60);
    }

    @Test void everyAbilitySlotKeepsAnArmedSkillForItsOwnButton() {
        int[] slots = {81, 87, 69, 82, 84, 89};
        for (int slot : slots) assertTrue(DreamTooltipLayout.keepsTargetMode(slot, slots));
        assertFalse(DreamTooltipLayout.keepsTargetMode(65, slots));
        String client = assertDoesNotThrow(() -> source("skill/SkillClient.java"));
        for (int slot = 1; slot <= 6; slot++) assertTrue(client.contains("Keybindings.abilitySlot" + slot + ".getKey()"));
    }

    @Test void sentenceTooltipsAreWrappedInsteadOfSingleVisualLines() throws Exception {
        for (String path : List.of("production/DreamUnitProduction.java", "worldtree/WorldTreeClient.java",
                "resonance/ResonanceClient.java", "ecologyworkposts/EcologyClient.java", "neutralcontract/ContractAbility.java",
                "scout/ScoutMarkAbility.java", "shadow/TerrorbeakUpgradeAbility.java", "encounter/EncounterClient.java",
                "client/DreamEconomyClientHud.java")) {
            String source = source(path);
            assertTrue(source.contains("DreamTooltipText.wrap("), path);
            assertFalse(source.contains("renderComponentTooltip"), path);
        }
        String recipe = source("production/DreamUnitProduction.java");
        assertTrue(recipe.contains("text.addAll(wrap(Component.translatable(entityType.get().getDescriptionId() + \".tooltip\")))"));
        assertTrue(recipe.contains("this.tooltipLines = tooltip(false);"), "sandbox button resolves language and width on hover");
    }

    @Test void badgesAndConditionalLinesUseTheSharedPolicy() throws Exception {
        assertTrue(source("buildingability/BuildingAbilityClient.java").contains("DreamTooltipLayout.cooldownBadge(remaining)"));
        assertTrue(source("buildingability/BuildingAbilityClient.java").contains("if (cooling > 0) addTooltip("));
        assertTrue(source("resonance/ResonanceClient.java").contains("DreamTooltipLayout.cooldownBadge(cooldown(p))"));
        assertTrue(source("ecologyworkposts/EcologyClient.java").contains("count()+\"/\"+EcologyWorkPosts.CAPACITY"));
        assertTrue(source("building/DreamBuildingSupport.java").contains("if (building.cost.population != 0) lines.add(ResourceCosts.getFormattedPop(building.cost));"));
        assertTrue(source("client/DreamEconomyClientHud.java").contains("lines.add(title);"));
    }

    @Test void everyBuildingHasABuildMenuHotkeyAwayFromTheWorkerSkills() throws Exception {
        String mod = source("DreamseekersMod.java");
        assertFalse(mod.contains("Factions.registerBuildings(DREAMSEEKERS, building);"));
        assertTrue(mod.contains("registerStartBuilding(DREAMSEEKERS, DREAM_ANCHOR, Keybindings.hotkey3)"));
        assertTrue(mod.contains("registerBuildings(DREAMSEEKERS, REVERIE_GARDEN, Keybindings.hotkey4)"));
        assertTrue(mod.contains("registerBuildings(DREAMSEEKERS, BEETLE_SANCTUARY, Keybindings.abilitySlot3)"));
        String keys = mod.substring(mod.indexOf("var buildKeys = List.of("), mod.indexOf("FUNCTIONAL_BUILDINGS.get(i), buildKeys.get(i)"));
        assertEquals(8, keys.split("Keybindings\\.", -1).length - 1, "one key per functional building");
        assertFalse(keys.contains("abilitySlot1,") || keys.contains("abilitySlot2,"), "Q and W stay the Firefly's own skills");
    }
}
