package com.solegendary.reignofnether.sandbox;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class SandboxEditorPolicyTest {
    private static final UUID HOST = new UUID(1, 1);
    private static final UUID EDITOR = new UUID(1, 2);
    private static final UUID READER = new UUID(1, 3);

    @Test void eachWorldOwnsOnlyItsExplicitInvites() {
        var worldA = new SandboxEditorPolicy();
        var worldB = new SandboxEditorPolicy();
        assertFalse(worldA.mayEnter(EDITOR, false, false));
        assertTrue(worldA.grant(EDITOR));
        assertFalse(worldA.grant(EDITOR));
        assertTrue(worldA.mayEnter(EDITOR, false, false));
        assertFalse(worldA.mayEnter(READER, false, false));
        assertFalse(worldB.mayEnter(EDITOR, false, false));
        assertTrue(worldA.revoke(EDITOR));
        assertFalse(worldA.mayEnter(EDITOR, false, false));
        assertFalse(worldA.revoke(EDITOR));
    }

    @Test void hostAndAdministratorAreDistinctFromSeatOrPlayPermission() {
        var world = new SandboxEditorPolicy();
        assertTrue(world.mayEnter(HOST, true, false));
        assertTrue(world.mayEnter(EDITOR, false, true));
        assertTrue(world.snapshot().isEmpty());
        assertFalse(world.mayEnter(READER, false, false));
        assertFalse(world.mayEnter(null, true, true));
    }

    @Test void roleMustBeBoundToExactlyTheLiveUuidEntityAndFaction() {
        assertTrue(SandboxEditorPolicy.boundSeat(EDITOR, 14, EDITOR, 14, true));
        assertFalse(SandboxEditorPolicy.boundSeat(READER, 14, EDITOR, 14, true));
        assertFalse(SandboxEditorPolicy.boundSeat(EDITOR, 15, EDITOR, 14, true));
        assertFalse(SandboxEditorPolicy.boundSeat(EDITOR, 14, null, 14, true));
        assertFalse(SandboxEditorPolicy.boundSeat(EDITOR, -1, EDITOR, -1, true));
        assertFalse(SandboxEditorPolicy.boundSeat(EDITOR, 14, EDITOR, 14, false));
    }

    @Test void ownershipChangesMatchTheThreeMenuChoices() {
        assertTrue(SandboxEditorPolicy.mayAssignOwner("Maker", "Maker"));
        assertTrue(SandboxEditorPolicy.mayAssignOwner("Maker", ""));
        assertTrue(SandboxEditorPolicy.mayAssignOwner("Maker", "Enemy"));
        assertFalse(SandboxEditorPolicy.mayAssignOwner("Maker", "OtherPlayer"));
        assertFalse(SandboxEditorPolicy.mayAssignOwner("Maker", null));
        assertFalse(SandboxEditorPolicy.mayAssignOwner("", ""));
    }

    @Test void grantsAreBoundedRevocableAndNotMutableThroughTheView() {
        var world = new SandboxEditorPolicy();
        for (int i = 0; i < SandboxEditorPolicy.MAX_GRANTS; i++) assertTrue(world.grant(new UUID(2, i)));
        assertFalse(world.grant(EDITOR));
        assertThrows(UnsupportedOperationException.class, () -> world.snapshot().clear());
        assertTrue(world.revoke(new UUID(2, 0)));
        assertTrue(world.grant(EDITOR));
        assertEquals(SandboxEditorPolicy.MAX_GRANTS, world.snapshot().size());
    }
}
