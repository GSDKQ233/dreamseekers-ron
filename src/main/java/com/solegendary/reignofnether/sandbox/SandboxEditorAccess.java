package com.solegendary.reignofnether.sandbox;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.player.ServerPlayerRequest;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** One queued request, resolved on the server thread. Do not cache this capability between actions. */
public final class SandboxEditorAccess {
    private final ServerPlayer actor;
    private final ServerLevel level;
    final Connection connection;

    private SandboxEditorAccess(ServerPlayer actor, Connection connection) {
        this.actor = actor;
        this.level = actor.serverLevel();
        this.connection = connection;
    }

    /** Also checked before creating a NONE seat, so self-selecting the mode does not grant authority. */
    public static boolean mayEnter(ServerPlayer actor, Connection connection) {
        if (!ServerPlayerRequest.isCurrent(actor, connection)) return false;
        ServerLevel level = actor.serverLevel();
        return level == level.getServer().overworld() && level == PlayerServerEvents.serverLevel
                && !level.getGameRules().getBoolean(GameRuleRegistrar.SCENARIO_MODE)
                && !level.getGameRules().getBoolean(GameRuleRegistrar.PVP_MODES_ONLY)
                && SandboxEditorSaveData.get(level).policy.mayEnter(actor.getUUID(),
                level.getServer().isSingleplayerOwner(actor.getGameProfile()), actor.hasPermissions(4));
    }

    @Nullable public static SandboxEditorAccess resolve(ServerPlayer actor, Connection connection) {
        if (!mayEnter(actor, connection)) return null;
        RTSPlayer found = null;
        synchronized (PlayerServerEvents.rtsPlayers) {
            for (RTSPlayer seat : PlayerServerEvents.rtsPlayers) {
                if (!actor.getUUID().equals(seat.playerUuid) && actor.getId() != seat.id) continue;
                if (found != null || !SandboxEditorPolicy.boundSeat(actor.getUUID(), actor.getId(),
                        seat.playerUuid, seat.id, seat.faction == Factions.NONE)) return null;
                found = seat;
            }
        }
        return found == null ? null : new SandboxEditorAccess(actor, connection);
    }

    public ServerPlayer actor() { return actor; }
    public ServerLevel level() { return level; }

    public boolean mayAssignOwner(String owner) {
        return SandboxEditorPolicy.mayAssignOwner(actor.getGameProfile().getName(), owner);
    }

    /** In particular, never load a remote chunk just to validate a requested edit. */
    public boolean editablePosition(BlockPos pos) {
        return pos != null && level.isInWorldBounds(pos) && level.hasChunkAt(pos);
    }

    @Nullable public LivingEntity unit(int id) {
        var entity = level.getEntity(id);
        return entity instanceof LivingEntity living && living instanceof Unit && living.level() == level
                && living.isAlive() && !living.isRemoved() ? living : null;
    }

    /** Exact registered origin, not arbitrary block contents or an identically placed building in another dimension. */
    @Nullable public BuildingPlacement building(BlockPos pos) {
        if (!editablePosition(pos) || BuildingServerEvents.getServerLevel() != level) return null;
        BuildingPlacement found = null;
        for (BuildingPlacement placement : BuildingServerEvents.getBuildings()) {
            if (placement.level != level || !placement.originPos.equals(pos)) continue;
            if (found != null) return null;
            found = placement;
        }
        return found;
    }
}
