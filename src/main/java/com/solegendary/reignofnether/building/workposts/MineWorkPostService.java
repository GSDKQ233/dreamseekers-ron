package com.solegendary.reignofnether.building.workposts;

import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.shared.MineWorkPosts;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.research.economy.ResearchPlacementIdentity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import java.util.UUID;

/** Control is deliberately limited to the actual actor's own mine, including registered sandbox actors.
 * A sandbox elsewhere on the server and allied ownership confer no new mine authority. */
public final class MineWorkPostService {
    private MineWorkPostService() {}
    public static boolean owns(ServerPlayer actor, BuildingPlacement mine) {
        return actor != null && mine != null && actor.level() == mine.level
                && actor.level().dimension().equals(Level.OVERWORLD)
                && actor.getName().getString().equals(mine.ownerName)
                && PlayerServerEvents.getRTSPlayer(mine.ownerName) != null
                && BuildingServerEvents.getBuildings().contains(mine) && MineWorkPosts.isMine(mine)
                && mine.isBuilt && !mine.isDestroyedServerside && mine.getHealth() > 0
                && !mine.shouldBeDestroyed() && mine.getCaptureRevision() >= 0;
    }
    public static String release(ServerPlayer actor, BuildingPlacement mine, UUID instance, long capture) {
        if (!owns(actor, mine) || !ResearchPlacementIdentity.serverIdentity(mine).equals(instance)
                || mine.getCaptureRevision() != capture) return "forbidden";
        // Never accept a client-supplied worker list. The live server leases are the entire authority.
        MineWorkPosts.releaseAll(mine);
        return "mine_workers_released";
    }
}
