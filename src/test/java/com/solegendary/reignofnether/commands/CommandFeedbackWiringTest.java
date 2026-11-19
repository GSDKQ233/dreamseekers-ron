package com.solegendary.reignofnether.commands;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Structural guard complements dedicated-server command/supplier GameTests. */
class CommandFeedbackWiringTest {
    private static final Path ROOT = Path.of("src/main/java/com/solegendary/reignofnether/commands");

    @Test void commandPackageHasNoClientLocalizationLinkage() throws Exception {
        try (var files = Files.walk(ROOT)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("net.minecraft.client"), file + " must remain server-loadable");
                assertFalse(source.contains("I18n."), file + " must defer localization to Components");
            }
        }
    }

    @Test void optionalFeedbackKeepsNestedTranslationAndEmptyComponents() throws Exception {
        String legacy = Files.readString(ROOT.resolve("CommandsServerEvents.java"));
        assertTrue(legacy.contains("Component ownersText = ownerName.isEmpty() ? Component.empty() : Component.translatable("));
        String modern = Files.readString(ROOT.resolve("rtsapi/PlayerCommands.java"));
        for (String field : new String[]{"forcePanTicks", "lockTicks", "zoomLevel"}) {
            assertTrue(modern.contains("Component " + field + "Text = " + field + " > 0 ? Component.translatable("));
            assertTrue(modern.contains("\", " + field + ") : Component.empty();"));
        }
    }

    @Test void dedicatedSuiteHasRealTemplateAndAcceptsSuccessFeedback() throws Exception {
        assertTrue(Files.isRegularFile(Path.of("src/gametest/resources/data/reignofnether_command_feedback/structures/ore_mine_test_arena.nbt")));
        String test = Files.readString(Path.of("src/gametest/java/com/solegendary/reignofnether/commands/CommandFeedbackGameTests.java"));
        assertTrue(test.contains("boolean acceptsSuccess() { return true; }"));
        assertTrue(test.contains("getDispatcher().execute(command, source)"));
        assertTrue(test.contains(".withSource(feedback)"));
    }
}
