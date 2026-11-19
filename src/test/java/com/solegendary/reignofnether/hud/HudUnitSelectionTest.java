package com.solegendary.reignofnether.hud;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

class HudUnitSelectionTest {
    private record UnitState(String name, int world, boolean removed) {}
    private static final Predicate<UnitState> LIVE = u -> !u.removed() && u.world() == 1;

    @Test void selectsSingleAddonUnitWithoutAnyRenderCallback() {
        var firefly = new UnitState("firefly", 1, false);
        assertSame(firefly, HudUnitSelection.resolve(List.of(firefly), null, LIVE));
    }

    @Test void selectsGroupAndPreservesExplicitPortraitChoiceWithoutRendering() {
        var firefly = new UnitState("firefly", 1, false);
        var stone = new UnitState("stone", 1, false);
        assertSame(firefly, HudUnitSelection.resolve(List.of(firefly, stone), null, LIVE));
        assertSame(stone, HudUnitSelection.resolve(List.of(firefly, stone), stone, LIVE));
        assertSame(stone, HudUnitSelection.resolve(List.of(stone, firefly), stone, LIVE));
    }

    @Test void clearAndReselectRetiresPreviousChoice() {
        var old = new UnitState("old", 1, false);
        var next = new UnitState("next", 1, false);
        assertNull(HudUnitSelection.resolve(List.of(), old, LIVE));
        assertSame(next, HudUnitSelection.resolve(List.of(next), old, LIVE));
        assertSame(next, HudUnitSelection.resolve(List.of(next), null, LIVE));
    }

    @Test void removedSelectedEntityFallsBackToLiveSelection() {
        var removed = new UnitState("removed", 1, true);
        var live = new UnitState("live", 1, false);
        assertSame(live, HudUnitSelection.resolve(List.of(removed, live), removed, LIVE));
        assertNull(HudUnitSelection.resolve(List.of(removed), removed, LIVE));
    }

    @Test void reconnectRejectsPriorWorldEntityEvenWithIdenticalName() {
        var old = new UnitState("firefly", 0, false);
        var fresh = new UnitState("firefly", 1, false);
        assertNull(HudUnitSelection.resolve(List.of(old), old, LIVE));
        assertSame(fresh, HudUnitSelection.resolve(List.of(old, fresh), old, LIVE));
    }

    @Test void sameValueDifferentObjectCannotRetainStaleIdentity() {
        var old = new UnitState("firefly", 1, false);
        var fresh = new UnitState("firefly", 1, false);
        assertEquals(old, fresh);
        assertNotSame(old, fresh);
        assertSame(fresh, HudUnitSelection.resolve(List.of(fresh), old, LIVE));
    }

    @Test void actualHudUsesSelectionForFramesAndTicksNotVanillaRenderEvent() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/hud/HudClientEvents.java"));
        String draw = source.substring(source.indexOf("public static void onDrawScreen("), source.indexOf("public static void onClientTick("));
        String tick = source.substring(source.indexOf("public static void onClientTick("), source.indexOf("private static void refreshHudSelectedEntity("));
        String render = source.substring(source.indexOf("public static void onRenderLivingEntity("), source.indexOf("public static void onRenderNamePlate("));
        assertTrue(draw.contains("refreshHudSelectedEntity();"));
        assertTrue(tick.contains("refreshHudSelectedEntity();"));
        assertTrue(source.contains("HudUnitSelection.resolve("));
        assertTrue(source.contains("entity -> !entity.isRemoved() && entity.level() == MC.level"));
        assertFalse(render.contains("setHudSelectedEntity("));
        assertFalse(render.contains("getSortedSelectedUnits("));
        assertFalse(draw.contains("portraitRendererUnit.model != null"));
        assertTrue(tick.contains("&& hudSelectedEntity != null)"));
    }

    @Test void identityChangeClearsCachedPortraitAndClickZone() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/hud/HudClientEvents.java"));
        String setter = source.substring(source.indexOf("public static void setHudSelectedEntity("), source.indexOf("hudSelectedEntity = entity;"));
        assertTrue(setter.contains("portraitRendererUnit.model = null;"));
        assertTrue(setter.contains("portraitRendererUnit.renderer = null;"));
        assertTrue(setter.contains("unitPortraitZone = null;"));
    }

    @Test void sortedCacheDetectsClearDirectRemovalReplacementAndDuplicateChanges() {
        var a = new UnitState("same", 1, false);
        var b = new UnitState("same", 1, false);
        assertTrue(HudUnitSelection.sameIdentityOrder(List.of(a, b), List.of(a, b)));
        assertFalse(HudUnitSelection.sameIdentityOrder(List.of(a, b), List.of()));
        assertFalse(HudUnitSelection.sameIdentityOrder(List.of(a, b), List.of(a)));
        assertFalse(HudUnitSelection.sameIdentityOrder(List.of(a), List.of(b)));
        assertFalse(HudUnitSelection.sameIdentityOrder(List.of(a, b), List.of(b, a)));
        assertFalse(HudUnitSelection.sameIdentityOrder(List.of(a, a, b), List.of(a, b, b)));
    }

    @Test void nativeSelectionCacheValidatesActualListAndClearInvalidatesFastPath() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/unit/UnitClientEvents.java"));
        String getter = source.substring(source.indexOf("public static ArrayList<LivingEntity> getSortedSelectedUnits("),
                source.indexOf("public static ArrayList<LivingEntity> getAllUnits("));
        String clear = source.substring(source.indexOf("public static void clearSelectedUnits("), source.indexOf("private static long lastLeftClickTime"));
        assertTrue(getter.contains("HudUnitSelection.sameIdentityOrder("));
        assertTrue(getter.contains("sortedSelectionInput, selectedUnits"));
        assertTrue(getter.contains("sortedSelectionInput = new ArrayList<>(units);"));
        assertTrue(clear.contains("markSelectedUnitsChanged();"));
    }
}
