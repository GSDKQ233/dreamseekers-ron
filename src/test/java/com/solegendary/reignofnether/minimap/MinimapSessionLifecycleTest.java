package com.solegendary.reignofnether.minimap;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Pure resource-key and state lifetime tests; no Minecraft client or OpenGL is initialized. */
class MinimapSessionLifecycleTest {
    private static final class Texture { int closes; void close(){closes++;} }
    @Test void repeatedResizeReplacesOneRegistryEntryAndRegistryOwnsClosing() {
        var slot=new MinimapSessionLifecycle.TextureSlot<>("reignofnether:minimap");
        Map<String,Texture> registry=new HashMap<>();List<Texture> allocated=new ArrayList<>();
        for(int i=0;i<100;i++){
            Texture replacement=new Texture();allocated.add(replacement);
            slot.replace(replacement,(key,texture)->{Texture old=registry.put(key,texture);if(old!=null)old.close();});
            assertEquals(Set.of(slot.key()),registry.keySet());assertSame(replacement,registry.get(slot.key()));
        }
        for(int i=0;i<99;i++)assertEquals(1,allocated.get(i).closes,"only registry closes each replaced texture once");
        assertEquals(0,allocated.get(99).closes,"live texture remains open");
    }
    @Test void logoutClearsBothVirtualCollectionsAndTemporaryMarkersInPlace() {
        List<String> units=new ArrayList<>(List.of("A-unit")),neutral=new ArrayList<>(List.of("A-neutral")),markers=new ArrayList<>(List.of("A-ping"));
        var markerReference=markers;
        MinimapSessionLifecycle.clearSession(units,neutral,markers);
        assertTrue(units.isEmpty());assertTrue(neutral.isEmpty());assertTrue(markers.isEmpty());assertSame(markerReference,markers);
        markers.add("B-ping");assertEquals(List.of("B-ping"),markerReference);
    }
    @Test void repeatedLogoutIsIdempotent() {
        List<Object> units=new ArrayList<>(),neutral=new ArrayList<>(),markers=new ArrayList<>();
        MinimapSessionLifecycle.clearSession(units,neutral,markers);MinimapSessionLifecycle.clearSession(units,neutral,markers);
        assertEquals(0,units.size()+neutral.size()+markers.size());
    }
}
