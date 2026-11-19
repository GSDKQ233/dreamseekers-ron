package com.dreamseekers.client;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Source contracts only; actual fonts, GUI scales and hover placement need client verification. */
class BuildingTooltipLayoutTest {
    private String source() throws Exception {
        return Files.readString(Path.of("addons/dreamseekers/src/main/java/com/dreamseekers/building/DreamBuildingSupport.java"));
    }
    @Test void titleAndDescriptionsWrapLogicalComponentsOnceAtCurrentScaledWidth() throws Exception {
        String source = source();
        String tooltip = source.substring(source.indexOf("private static final class ClientTooltip"));
        assertTrue(tooltip.contains("mc.getWindow().getGuiScaledWidth()"));
        assertTrue(tooltip.contains("screenWidth <= 30 ? 1 : Math.min(310, screenWidth - 30)"));
        assertTrue(tooltip.contains("mc.font.split(Component.translatable(key).withStyle(Style.EMPTY.withBold(true)), width)"));
        assertTrue(tooltip.contains("mc.font.split(Component.translatable(key + \".tooltip1\"), width)"));
        assertTrue(tooltip.contains("mc.font.split(Component.translatable(key + \".tooltip2\"), width)"));
        assertEquals(3, tooltip.split("mc.font.split", -1).length - 1);
        assertFalse(tooltip.contains("getVisualOrderText"));
        assertFalse(tooltip.contains("getString()"));
        assertFalse(tooltip.contains("Component.literal"));
        assertTrue(tooltip.contains("lines.add(ResourceCosts.getFormattedCost(building.cost))"));
        assertTrue(tooltip.contains("lines.add(ResourceCosts.getFormattedPop(building.cost))"));
        assertTrue(tooltip.contains("lines.add(FormattedCharSequence.forward(\"\", Style.EMPTY))"));
    }
    @Test void cachedButtonRefreshesAtHoverWithoutChangingPlacementOrAvailability() throws Exception {
        String source = source();
        String button = source.substring(source.indexOf("public static BuildingPlaceButton button"),
                source.indexOf("private static final class ClientTooltip"));
        assertTrue(button.contains("new BuildingPlaceButton("));
        assertTrue(button.contains("() -> BuildingClientEvents.getBuildingToPlace() == building"));
        assertTrue(button.contains("() -> false, () -> canBuildClient(building)"));
        assertTrue(button.contains("List.of(), building)"));
        assertTrue(button.contains("@Override public void renderTooltip("));
        assertTrue(button.contains("final Building tooltipBuilding = building;"));
        assertTrue(button.contains("tooltipLines = ClientTooltip.lines(tooltipBuilding, key);"));
        assertTrue(button.contains("super.renderTooltip(graphics, mouseX, mouseY);"));
        assertFalse(button.contains("Minecraft.getInstance"));
        assertFalse(button.contains("onLeftClick"));
    }
}
