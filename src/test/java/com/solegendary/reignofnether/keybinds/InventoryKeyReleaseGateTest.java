// SPDX-License-Identifier: GPL-3.0-only
package com.solegendary.reignofnether.keybinds;

import net.minecraftforge.client.event.ScreenEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

/** Real Forge key-release payload, no window/render loop, and no claim of GUI validation. */
class InventoryKeyReleaseGateTest {
    private ScreenEvent.KeyReleased.Post release(int key) {
        return new ScreenEvent.KeyReleased.Post(new Screen(Component.literal("Unrendered key event probe")) { }, key, 0, 0);
    }
    @Test void buildingSelectionWinsEvenWhileOldHeroHudAndInventoryAreVisible() {
        var event=release(84); // T, existing inventory slot 1 and fifth production slot.
        var itemCalls=new ArrayList<Integer>();
        assertFalse(InventoryKeyReleaseGate.dispatch(true,true,true,true,true,false,event.getKeyCode(),itemCalls::add));
        assertTrue(itemCalls.isEmpty(),"stale hero cannot consume an item on building production T");
    }
    @Test void sixthProductionYCannotUseStaleHeroInventorySlotTwo() {
        var event=release(89); // Y, existing item slot 2 and newly staged sixth production slot.
        var calls=new ArrayList<Integer>();
        assertFalse(InventoryKeyReleaseGate.dispatch(true,true,true,true,true,false,event.getKeyCode(),calls::add));
        assertTrue(calls.isEmpty(),"current building context excludes stale hero item Y");
        assertTrue(InventoryKeyReleaseGate.dispatch(true,true,false,true,true,false,event.getKeyCode(),calls::add));
        assertEquals(java.util.List.of(89),calls,"actual selected hero still receives the original Y key exactly once");
    }
    @Test void focusedTopdownTextInputNeverUsesSelectedHeroInventoryKeys() {
        var calls=new ArrayList<Integer>();
        for(int key:new int[]{84,89,70,71,67,66}) {
            var event=release(key);
            assertFalse(InventoryKeyReleaseGate.dispatch(true,true,false,true,true,true,event.getKeyCode(),calls::add));
        }
        assertTrue(calls.isEmpty(),"typing with a selected hero never dispatches inventory hotkeys");
    }
    @Test void deselectedOrReplacedHudEntityCannotReceiveTheRelease() {
        var event=release(84);var calls=new ArrayList<Integer>();
        assertFalse(InventoryKeyReleaseGate.dispatch(true,true,false,false,true,false,event.getKeyCode(),calls::add));
        assertTrue(calls.isEmpty(),"empty selection or another unit selected rejects stale HUD entity");
    }
    @Test void nonTopdownMissingPlayerOrUnavailableInventoryFailClosed() {
        var event=release(84);var calls=new ArrayList<Integer>();
        assertFalse(InventoryKeyReleaseGate.dispatch(false,true,false,true,true,false,event.getKeyCode(),calls::add));
        assertFalse(InventoryKeyReleaseGate.dispatch(true,false,false,true,true,false,event.getKeyCode(),calls::add));
        assertFalse(InventoryKeyReleaseGate.dispatch(true,true,false,true,false,false,event.getKeyCode(),calls::add));
        assertTrue(calls.isEmpty());
    }
    @Test void actualSelectedHeroKeepsTheOriginalUnmodifiedKeyPayload() {
        var calls=new ArrayList<Integer>();
        for(int key:new int[]{84,89,70,71,67,66}) {
            var event=release(key);
            assertTrue(InventoryKeyReleaseGate.dispatch(true,true,false,true,true,false,event.getKeyCode(),calls::add));
            assertEquals(key,calls.get(calls.size()-1));
        }
        assertEquals(6,calls.size(),"one dispatch per release; no changed hero bindings");
    }
}
