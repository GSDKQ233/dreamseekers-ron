package com.solegendary.reignofnether.client;

import com.google.gson.JsonParser;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import net.minecraft.client.resources.language.ClientLanguage;
import net.minecraft.locale.Language;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** Registration/resource behavior plus source wiring guards; this is not a GUI test. */
class Api65ClientRegressionTest {
    private static final Path LANG = Path.of("src/main/resources/assets/reignofnether/lang");

    @Test void everyDeclaredBindingIncludingMarketOccursOnceInRegistrationList() throws Exception {
        var all = Keybindings.all();
        for (var field : Keybindings.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            if (field.getType() == Keybinding.class)
                assertEquals(1, Collections.frequency(all, field.get(null)), field.getName());
            else if (field.getType() == Keybinding[].class)
                for (Keybinding key : (Keybinding[]) field.get(null))
                    assertEquals(1, Collections.frequency(all, key), field.getName());
        }
        assertEquals(1, Collections.frequency(all, Keybindings.hotkey10));
    }

    @Test void persianAndEveryMainJsonResourceParse() throws Exception {
        for (String root : new String[]{"src/main/resources", "addons/dreamseekers/src/main/resources"}) {
            if (!Files.isDirectory(Path.of(root))) continue;
            try (var paths = Files.walk(Path.of(root))) {
                for (Path path : paths.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".json") || p.toString().endsWith(".mcmeta")).toList())
                    assertDoesNotThrow(() -> JsonParser.parseString(Files.readString(path)), path.toString());
            }
        }
        var persian = JsonParser.parseString(Files.readString(LANG.resolve("fa_ir.json"))).getAsJsonObject();
        assertFalse(persian.entrySet().isEmpty(), "Keep the existing Persian translations");
        persian.entrySet().forEach(entry -> assertTrue(entry.getValue().isJsonPrimitive()
                && entry.getValue().getAsJsonPrimitive().isString(), entry.getKey()));
    }

    @Test void supportedMessagesAndEnglishFallbackUseTheClientLanguageLoader(@TempDir Path pack) throws Exception {
        String ready = "matchstart.reignofnether.ready_count";
        String reset = "server.reignofnether.match_reset_hard_map";
        var lang = Files.createDirectories(pack.resolve("assets/reignofnether/lang"));
        for (String file : List.of("en_us.json", "zh_cn.json"))
            Files.copy(LANG.resolve(file), lang.resolve(file));
        // Only the synthetic locale lacks the ready-count key. Real translations may grow freely.
        Files.writeString(lang.resolve("xx_test.json"), "{\"" + reset + "\":\"fixture override\"}");

        try (var resources = new MultiPackResourceManager(PackType.CLIENT_RESOURCES,
                List.of(new PathPackResources("locale-test", pack, false)))) {
            var english = ClientLanguage.loadFrom(resources, List.of(Language.DEFAULT), false);
            var chinese = ClientLanguage.loadFrom(resources, List.of("zh_cn"), false);
            for (var supported : List.of(english, chinese)) {
                for (String key : List.of(ready, reset, "commands.reignofnether.reset", "commands.reignofnether.hard_reset",
                        "survival.reignofnether.beacon_freeplay", "survival.reignofnether.defeat_freeplay",
                        "building.reignofnether.indoor_spawn_blocked", "research.economy.prerequisite_pause",
                        "startpos.reignofnether.preflight_failed", "startpos.reignofnether.preflight_player",
                        "startpos.reignofnether.preflight_position", "startpos.reignofnether.preflight_mode",
                        "startpos.reignofnether.preflight_assets", "startpos.reignofnether.preflight_interrupted")) {
                    assertFalse(supported.getOrDefault(key, "").isBlank(), key);
                    assertNotEquals(key, supported.getOrDefault(key), key);
                }
                String count = String.format(Locale.ROOT, supported.getOrDefault(ready), 3, 8);
                assertTrue(count.contains("3") && count.contains("8"), count);
            }
            var partial = ClientLanguage.loadFrom(resources, List.of(Language.DEFAULT, "xx_test"), false);
            assertEquals("fixture override", partial.getOrDefault(reset));
            assertEquals(english.getOrDefault(ready), partial.getOrDefault(ready));
        }
    }

    @Test void hiddenHudRetiresItsFrameAndGatesMousePortraitAndButtonKeys() throws Exception {
        String hud = Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/hud/HudClientEvents.java"));
        assertTrue(hud.contains("enabled = !enabled;\n                // Retire"));
        assertTrue(hud.contains("public static void onDrawScreen(ScreenEvent.Render.Post evt) {\n        clearInteractiveHud();"));
        for (String declaration : new String[]{"public static boolean isMouseOverAnyButton()",
                "public static boolean isMouseOverAnyButtonOrHud()", "public static Button getMousedOverButton()",
                "public static Button getMousedOverStartPosButton()"}) {
            int start = hud.indexOf(declaration);
            assertTrue(start >= 0);
            assertTrue(hud.substring(start, Math.min(hud.length(), start + 180)).contains("!isHudInteractive()"), declaration);
        }
        assertTrue(hud.contains("if (isHudInteractive() && MiscUtil.isLeftClickDown(MC))"));
        assertTrue(hud.contains("if (!isHudInteractive() || evt.getScreen() != MC.screen) return;"));
        String button = Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/hud/buttons/Button.java"));
        assertEquals(3, button.split("MC.screen instanceof TopdownGui && !HudClientEvents.isHudInteractive\\(\\)", -1).length - 1);
    }
}
