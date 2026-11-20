package com.solegendary.reignofnether.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Static guards for resources the client only reports at runtime; this is not a GUI test. */
class CoreResourceIntegrityTest {
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
    private static final Pattern LITERAL_KEY = Pattern.compile(
            "(?:Component\\.translatable|I18n\\.get|fcs|fcsIcons)\\(\\s*\"([a-z0-9_.]+\\.reignofnether\\.[a-z0-9_.]+)\"\\s*[,)]");

    @Test void everyPngResourceIsAPngFile() throws Exception {
        List<String> notPng = new ArrayList<>();
        for (String root : new String[]{"src/main/resources", "addons/dreamseekers/src/main/resources"}) {
            if (!Files.isDirectory(Path.of(root))) continue;
            try (var paths = Files.walk(Path.of(root))) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".png")).toList()) {
                    byte[] bytes = Files.readAllBytes(path);
                    if (bytes.length < 8 || !Arrays.equals(Arrays.copyOf(bytes, 8), PNG_SIGNATURE)) notPng.add(path.toString());
                }
            }
        }
        assertEquals(List.of(), notPng);
    }

    @Test void everyLiteralTranslationKeyInCoreSourcesHasEnglishText() throws Exception {
        var english = JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/assets/reignofnether/lang/en_us.json"))).getAsJsonObject();
        List<String> missing = new ArrayList<>();
        try (var paths = Files.walk(Path.of("src/main/java"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(path)) {
                    String code = line.stripLeading();
                    if (code.startsWith("*") || code.startsWith("//")) continue;
                    Matcher matcher = LITERAL_KEY.matcher(line);
                    while (matcher.find())
                        if (!english.has(matcher.group(1))) missing.add(path.getFileName() + ": " + matcher.group(1));
                }
            }
        }
        assertEquals(List.of(), missing);
    }
}
