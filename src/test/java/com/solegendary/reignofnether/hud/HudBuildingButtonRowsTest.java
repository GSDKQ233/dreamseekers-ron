package com.solegendary.reignofnether.hud;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class HudBuildingButtonRowsTest {
    @Test void dynamicAbilityRowKeepsSandboxControlsAboveProduction() {
        int height = 810, frame = 22;
        int abilityY = height - frame;
        int productionY = abilityY - frame;
        int sandboxY = HudBuildingButtonRows.sandboxRowY(height, frame, 1, true);
        assertEquals(productionY - frame, sandboxY);
        assertTrue(sandboxY + frame <= productionY);
        assertTrue(productionY + frame <= abilityY);
        // The old static-ability predicate missed this row and overwrote production.
        assertEquals(productionY, HudBuildingButtonRows.sandboxRowY(height, frame, 1, false));
    }

    @Test void multipleProductionRowsAndNativeAbilityRowsKeepDistinctSlots() {
        for (int rows = 1; rows <= 5; rows++) {
            int topProductionY = 600 - 20 * (rows + 1);
            assertEquals(topProductionY - 20, HudBuildingButtonRows.sandboxRowY(600, 20, rows, true));
        }
    }

    @Test void noAbilityAndUnbuiltOrUnitRowsPreserveNativePositions() {
        assertEquals(600 - 20 * 2, HudBuildingButtonRows.sandboxRowY(600, 20, 1, false));
        assertEquals(600 - 20 * 2, HudBuildingButtonRows.sandboxRowY(600, 20, 0, true));
        assertEquals(600 - 20, HudBuildingButtonRows.sandboxRowY(600, 20, 0, false));
    }

    @Test void actualHudUsesRenderedDynamicRowWithoutRequeryingAbilities() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/hud/HudClientEvents.java"));
        String reservation = "if (buildingAbilities.size() > 0 || !hudSelectedPlacement.isBuilt) {\n"
                + "                        buildingAbilityRowReserved = true;";
        assertTrue(source.contains(reservation));
        String sandbox = source.substring(source.indexOf("// Unit sandbox buttons"),
                source.indexOf("// Unit action buttons (attack, stop, move, abilities etc.)"));
        assertTrue(sandbox.contains("HudBuildingButtonRows.sandboxRowY("));
        assertTrue(sandbox.contains("hudSelectedEntity != null || buildingAbilityRowReserved"));
        assertTrue(sandbox.contains("hudSelectedPlacement != null && !hudSelectedPlacement.isBuilt"));
        assertFalse(sandbox.contains("getAbilities()"));
        assertFalse(sandbox.contains("getAbilityButtons()"), "reuse this frame's resolved row, without a second query");
    }
}
