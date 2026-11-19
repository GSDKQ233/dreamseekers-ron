package com.solegendary.reignofnether.sandbox;

import java.util.UUID;
import com.solegendary.reignofnether.player.RTSPlayer;
import net.minecraft.server.level.ServerPlayer;

/** Disposable positive-test setup only. Exercises the same world data read by live authorization. */
public final class SandboxEditorTestGrant implements AutoCloseable {
    private final ServerPlayer player;
    private final RTSPlayer seat;
    private final boolean granted;
    private final int previousId;
    private final UUID previousUuid;
    private boolean closed;

    public SandboxEditorTestGrant(ServerPlayer actor, RTSPlayer fixtureSeat) {
        if (actor == null || (fixtureSeat != null && !actor.getGameProfile().getName().equals(fixtureSeat.name)))
            throw new IllegalArgumentException("fixture does not match the authenticated player");
        player = actor;
        seat = fixtureSeat;
        previousId = seat == null ? -1 : seat.id;
        previousUuid = seat == null ? null : seat.playerUuid;
        SandboxEditorSaveData world = SandboxEditorSaveData.get(player.serverLevel());
        granted = world.change(player.getUUID(), true);
        if (!granted && !world.policy.snapshot().contains(player.getUUID())) throw new AssertionError("grant limit");
        if (seat != null) { seat.id = player.getId(); seat.playerUuid = player.getUUID(); }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (seat != null) { seat.id = previousId; seat.playerUuid = previousUuid; }
        if (granted) SandboxEditorSaveData.get(player.serverLevel()).change(player.getUUID(), false);
    }
}
