package com.solegendary.reignofnether.startpos;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Source wiring only: the physical client/GUI reset transition still needs client acceptance. */
class StartPosResetWiringTest {
    @Test void resetAndGamemodeCallbackAssignNoneRatherThanDiscardAnEqualsResult() throws Exception {
        String source=Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/startpos/StartPosClientEvents.java"));
        String reset=source.substring(source.indexOf("public static void resetAll()"),source.indexOf("public static void onClientLogout"));
        String mode=source.substring(source.indexOf("public static void onChangeGamemode"),source.indexOf("public static void onRenderLevel"));
        assertTrue(reset.contains("selectedFaction = Factions.NONE;"));
        assertTrue(mode.contains("selectedFaction = Factions.NONE;"));
        assertFalse(reset.contains("selectedFaction.equals(Factions.NONE);"));
        assertFalse(mode.contains("selectedFaction.equals(Factions.NONE);"));
    }
}
