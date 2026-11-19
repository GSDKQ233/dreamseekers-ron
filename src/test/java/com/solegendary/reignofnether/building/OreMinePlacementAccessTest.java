package com.solegendary.reignofnether.building;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OreMinePlacementAccessTest {
    @Test
    void ownerCanPlaceWithoutSpecialPermissions() {
        assertTrue(OreMinePlacementAccess.canPlaceFor("Alice", "Alice", false, false));
    }

    @Test
    void anotherOwnerRequiresActualServerResolvedPermission() {
        assertFalse(OreMinePlacementAccess.canPlaceFor("Alice", "Bob", false, false));
        assertTrue(OreMinePlacementAccess.canPlaceFor("Alice", "Bob", false, true));
        assertTrue(OreMinePlacementAccess.canPlaceFor("Alice", "Bob", true, false));
        assertTrue(OreMinePlacementAccess.canPlaceFor("Alice", "Bob", true, true));
    }

    @Test
    void namesAreComparedExactlyLikeResourceAccountOwnership() {
        assertFalse(OreMinePlacementAccess.canPlaceFor("Alice", "alice", false, false));
        assertFalse(OreMinePlacementAccess.canPlaceFor("Alice", " Alice", false, false));
        assertFalse(OreMinePlacementAccess.canPlaceFor("Alice ", "Alice", false, false));
    }

    @Test
    void missingSenderOrOwnerIsDeniedEvenWithBothPermissionFlags() {
        String[] missing = { null, "", " ", "\t\n" };
        for (String name : missing) {
            for (boolean sandbox : new boolean[] { false, true }) {
                for (boolean allyControl : new boolean[] { false, true }) {
                    assertFalse(OreMinePlacementAccess.canPlaceFor(name, "Alice", sandbox, allyControl));
                    assertFalse(OreMinePlacementAccess.canPlaceFor("Alice", name, sandbox, allyControl));
                    assertFalse(OreMinePlacementAccess.canPlaceFor(name, name, sandbox, allyControl));
                }
            }
        }
    }
}
