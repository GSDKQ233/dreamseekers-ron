package com.solegendary.reignofnether.client;

import com.google.gson.JsonParser;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/** Registration/resource behavior plus source wiring guards; this is not a GUI test. */
class Api65ClientRegressionTest {
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
        var persian = JsonParser.parseString(Files.readString(Path.of(
                "src/main/resources/assets/reignofnether/lang/fa_ir.json"))).getAsJsonObject();
        assertEquals(1364, persian.size());
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
