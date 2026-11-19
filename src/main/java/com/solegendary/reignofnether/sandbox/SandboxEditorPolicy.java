package com.solegendary.reignofnether.sandbox;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Map-editor grants are world-local UUIDs, never display names, alliance flags or the play whitelist. */
final class SandboxEditorPolicy {
    static final int MAX_GRANTS = 256;
    private final Set<UUID> invited = new TreeSet<>();

    boolean grant(UUID id) {
        return id != null && invited.size() < MAX_GRANTS && invited.add(id);
    }

    boolean revoke(UUID id) {
        return id != null && invited.remove(id);
    }

    Set<UUID> snapshot() {
        return Set.copyOf(invited);
    }

    boolean mayEnter(UUID id, boolean integratedOwner, boolean serverAdmin) {
        return id != null && (integratedOwner || serverAdmin || invited.contains(id));
    }

    static boolean boundSeat(UUID actor, int entityId, UUID seatUuid, int seatId, boolean sandboxFaction) {
        return sandboxFaction && entityId >= 0 && actor != null && actor.equals(seatUuid) && entityId == seatId;
    }

    /** These are the three relationship choices offered by the editor. No arbitrary beneficiary names. */
    static boolean mayAssignOwner(String profileName, String owner) {
        return profileName != null && !profileName.isBlank() && owner != null
                && (owner.isEmpty() || "Enemy".equals(owner) || profileName.equals(owner));
    }
}
