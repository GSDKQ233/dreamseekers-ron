// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.client;

import com.dreamseekers.skill.SkillCastAnimation;
import com.dreamseekers.skill.UnitSkill;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Art resource contracts only: file presence, size and naming. These checks do not claim in-game rendering coverage. */
class ArtResourceIntegrityTest {
    private static final Path ROOT = Path.of("addons/dreamseekers");
    private static final Path ICONS = ROOT.resolve("src/main/resources/assets/dreamseekers/textures/icons");
    private static final Path DATA = ROOT.resolve("src/main/resources/data/dreamseekers/structures");
    private static final Path ASSETS = ROOT.resolve("src/main/resources/assets/dreamseekers/structures");
    private static final Pattern ICON = Pattern.compile("\"textures/icons/([a-z0-9_]+)\\.png\"");
    private static final Pattern NAMESPACED = Pattern.compile("\"(minecraft|pasterdream)\"\\s*,\\s*\"textures/[a-z0-9_/]+\\.png\"");

    private static TreeSet<String> names(Path directory, String suffix) throws Exception {
        try (var files = Files.list(directory)) {
            return files.map(file -> file.getFileName().toString()).filter(name -> name.endsWith(suffix))
                    .map(name -> name.substring(0, name.length() - suffix.length())).collect(Collectors.toCollection(TreeSet::new));
        }
    }

    @Test void everyIconNamedInSourceExists() throws Exception {
        try (var sources = Files.walk(ROOT.resolve("src/main/java"))) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                var matcher = ICON.matcher(Files.readString(source));
                while (matcher.find())
                    assertTrue(Files.isRegularFile(ICONS.resolve(matcher.group(1) + ".png")), source.getFileName() + " -> " + matcher.group(1));
            }
        }
    }

    @Test void everySkillHasItsOwnIcon() {
        for (UnitSkill skill : UnitSkill.values())
            assertTrue(Files.isRegularFile(ICONS.resolve("skill_" + skill.id() + ".png")), skill.id());
    }

    @Test void unitPortraitsAreAddonOwnedRatherThanPlaceholders() throws Exception {
        String mod = Files.readString(ROOT.resolve("src/main/java/com/dreamseekers/DreamseekersMod.java"));
        for (String constant : new String[]{"CRAZY_TERRORBEAK_ICON", "AARONCOS_LEFT_HAND_ICON", "AARONCOS_RIGHT_HAND_ICON"}) {
            String line = mod.lines().filter(text -> text.contains(" " + constant + "=") || text.contains(" " + constant + " =")).findFirst().orElseThrow();
            assertFalse(NAMESPACED.matcher(line).find(), constant);
            assertTrue(line.contains("textures/icons/" + constant.replace("_ICON", "").toLowerCase(java.util.Locale.ROOT) + "_unit.png"), constant);
        }
    }

    @Test void iconsShareOneCanvasSize() throws Exception {
        for (String name : names(ICONS, ".png")) {
            var image = ImageIO.read(ICONS.resolve(name + ".png").toFile());
            assertNotNull(image, name);
            int expected = name.equals("shadow_energy") ? 16 : 64; // the resource glyph is drawn at HUD glyph size
            assertEquals(expected, image.getWidth(), name);
            assertEquals(expected, image.getHeight(), name);
            assertTrue(image.getColorModel().hasAlpha(), name + " needs a transparent background");
        }
    }

    @Test void castAnimationsExistInTheInstalledNativeModels() throws Exception {
        Map<UnitSkill, String> files = Map.of(
                UnitSkill.WIND_DASH, "wind_knight", UnitSkill.TERRORBEAK_DREAD_ROAR, "terrorbeak",
                UnitSkill.SHADOW_GOLEM_STRIKE, "shadow_golem", UnitSkill.MOTHER_BROOD, "black_beetle_mother",
                UnitSkill.BEETLE_ADVANCE, "black_beetle", UnitSkill.WEEPING_ECHOES, "shadow_squeal_wave",
                UnitSkill.AARONCOS_LEFT_PRESS, "aaroncos_lefthand_0", UnitSkill.AARONCOS_RIGHT_SHOCK, "aaroncos_righthand_0",
                UnitSkill.AARONCOS_RIGHT_VORTEX, "aaroncos_righthand_0");
        for (UnitSkill skill : UnitSkill.values()) {
            String animation = SkillCastAnimation.nativeAnimation(skill);
            assertEquals(files.containsKey(skill), animation != null, skill.id());
            if (animation == null) continue;
            String resource = "/assets/pasterdream/animations/" + files.get(skill) + ".animation.json";
            try (var stream = ArtResourceIntegrityTest.class.getResourceAsStream(resource)) {
                assertNotNull(stream, resource);
                JsonObject animations = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("animations");
                assertTrue(animations.has(animation), resource + " lacks " + animation);
            }
        }
    }

    @Test void everyRuntimeStructureIsMirroredAndListedInTheManifest() throws Exception {
        TreeSet<String> data = names(DATA, ".nbt");
        assertEquals(data, names(ASSETS, ".nbt"));
        var manifest = JsonParser.parseString(Files.readString(ROOT.resolve("structure_manifest.json"))).getAsJsonObject();
        TreeSet<String> listed = new TreeSet<>();
        for (var raw : manifest.getAsJsonArray("structures")) listed.add(raw.getAsJsonObject().get("id").getAsString());
        for (var raw : manifest.getAsJsonArray("runtime_template_assets")) {
            var item = raw.getAsJsonObject();
            String id = item.get("id").getAsString();
            assertTrue(listed.contains(item.get("base_building").getAsString()), id);
            assertTrue(listed.add(id), id);
            assertEquals(item.get("sha256").getAsString(), HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(DATA.resolve(id + ".nbt")))), id);
        }
        assertEquals(data, listed);
        for (String name : data)
            assertArrayEquals(Files.readAllBytes(DATA.resolve(name + ".nbt")), Files.readAllBytes(ASSETS.resolve(name + ".nbt")), name);
    }
}
