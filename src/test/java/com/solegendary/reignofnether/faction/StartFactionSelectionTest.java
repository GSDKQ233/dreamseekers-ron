package com.solegendary.reignofnether.faction;

import com.google.gson.JsonParser;
import com.solegendary.reignofnether.unit.UnitAction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class StartFactionSelectionTest {
    private final ResourceLocation dream = ResourceLocation.parse("dreamseekers:dreamseekers");

    @Test void registeredFactionRetainsNamespaceThroughPlacementIntent() {
        var selection = new StartFactionSelection();
        assertNull(selection.resolve(UnitAction.STARTRTS_REGISTERED_FACTION));
        selection.select(dream);
        selection.onActionChanged(UnitAction.STARTRTS_REGISTERED_FACTION);
        assertEquals(dream, selection.resolve(UnitAction.STARTRTS_REGISTERED_FACTION));
        assertEquals(dream, selection.selected());
        assertTrue(UnitAction.STARTRTS_REGISTERED_FACTION.isStartRTS());
    }
    @Test void cancelAndEveryOtherActionRetireOldFactionIntent() {
        var selection = new StartFactionSelection();
        selection.select(dream);
        selection.onActionChanged(null);
        assertNull(selection.resolve(UnitAction.STARTRTS_REGISTERED_FACTION));
        for (var action : UnitAction.values()) {
            if (action == UnitAction.STARTRTS_REGISTERED_FACTION) continue;
            selection.select(dream);
            selection.onActionChanged(action);
            assertNull(selection.selected(), action.name());
        }
        selection.select(dream);
        selection.onActionChanged(UnitAction.MOVE);
        selection.onActionChanged(UnitAction.STARTRTS_REGISTERED_FACTION);
        assertNull(selection.resolve(UnitAction.STARTRTS_REGISTERED_FACTION), "cannot resurrect an abandoned selection");
    }
    @Test void classicActionsKeepOriginalIdentityAndNewActionIsAppended() {
        var selection = new StartFactionSelection();
        assertEquals(ResourceLocation.parse("reignofnether:villagers"), selection.resolve(UnitAction.STARTRTS_VILLAGERS));
        assertEquals(ResourceLocation.parse("reignofnether:monsters"), selection.resolve(UnitAction.STARTRTS_MONSTERS));
        assertEquals(ResourceLocation.parse("reignofnether:piglins"), selection.resolve(UnitAction.STARTRTS_PIGLINS));
        assertNull(selection.resolve(UnitAction.MOVE));
        assertNull(selection.resolve(null));
        assertEquals(UnitAction.DEBUG2.ordinal() + 1, UnitAction.STARTRTS_REGISTERED_FACTION.ordinal());
        assertEquals(4, java.util.Arrays.stream(UnitAction.values()).filter(UnitAction::isStartRTS).count());
    }
    @Test void freePlacementDiscoversRegisteredAddonWithoutRequiringPanoramaOrPresetRoster() {
        var classic = new java.util.ArrayList<ResourceLocation>();
        var registry = new java.util.HashMap<ResourceLocation, Faction>();
        for (String name : new String[] {"villagers", "monsters", "piglins", "neutral", "none", "random"}) {
            var key = ResourceLocation.parse("reignofnether:" + name);
            var faction = new Faction().setKey(key);
            faction.workerEntityType = ResourceLocation.parse("reignofnether:villager_unit");
            registry.put(key, faction);
            classic.add(key);
        }
        assertTrue(StartFactionSelection.additionalFactionKeys(classic, registry::get).isEmpty(),
                "core-only roster keeps the three existing buttons without extras");
        var addon = new Faction().setKey(dream).noCubeMap();
        addon.workerEntityType = ResourceLocation.parse("dreamseekers:firefly_worker");
        registry.put(dream, addon);
        classic.add(dream);
        classic.add(dream); // registry list duplicates must not duplicate buttons
        classic.add(ResourceLocation.parse("absent:missing"));
        assertEquals(java.util.List.of(dream), StartFactionSelection.additionalFactionKeys(classic, registry::get));
        addon.playable = false;
        assertTrue(StartFactionSelection.additionalFactionKeys(classic, registry::get).isEmpty());
        addon.playable = true;
        addon.workerEntityType = null;
        assertTrue(StartFactionSelection.additionalFactionKeys(classic, registry::get).isEmpty());
    }
    @Test void repeatedSelectionAndSwitchingDoNotReuseCancelledIntent() {
        var selection = new StartFactionSelection();
        var other = ResourceLocation.parse("addon:other");
        selection.select(dream);
        selection.select(dream);
        assertEquals(dream, selection.resolve(UnitAction.STARTRTS_REGISTERED_FACTION));
        selection.select(other);
        assertEquals(other, selection.resolve(UnitAction.STARTRTS_REGISTERED_FACTION));
        selection.onActionChanged(null);
        assertNull(selection.resolve(UnitAction.STARTRTS_REGISTERED_FACTION));
        selection.select(dream);
        assertEquals(dream, selection.resolve(UnitAction.STARTRTS_REGISTERED_FACTION));
    }
    @Test void correctFactionNamespaceExistsInBothDeliveredLanguages() throws Exception {
        var faction = new Faction().setKey(dream);
        assertEquals("hud.faction.dreamseekers.dreamseekers", faction.getTranslationKey());
        for (String locale : new String[] {"en_us", "zh_cn"}) {
            var path = Path.of("addons/dreamseekers/src/main/resources/assets/dreamseekers/lang/" + locale + ".json");
            var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            assertEquals(locale.equals("zh_cn") ? "寻梦者" : "Dreamseekers", json.get(faction.getTranslationKey()).getAsString());
        }
    }
    @Test void scenarioAnnouncementPreservesClientLocalizedFactionArgument() throws Exception {
        var faction = new Faction().setKey(dream);
        var name = net.minecraft.network.chat.Component.translatable(faction.getTranslationKey());
        var message = net.minecraft.network.chat.Component.translatable("server.reignofnether.started_scenario", "player", "role", name);
        var content = (net.minecraft.network.chat.contents.TranslatableContents) message.getContents();
        assertSame(name, content.getArgs()[2]);
        assertEquals("hud.faction.dreamseekers.dreamseekers",
                ((net.minecraft.network.chat.contents.TranslatableContents) name.getContents()).getKey());
        var source = Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/player/PlayerServerEvents.java"));
        assertTrue(source.contains("playerName, role.name, Component.translatable(role.faction.getTranslationKey())"));
        assertFalse(source.contains("playerName, role.name, role.faction.getName()"));
    }
}
