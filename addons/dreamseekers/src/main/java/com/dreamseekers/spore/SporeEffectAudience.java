// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.spore;
import com.dreamseekers.unit.mixin.SporeChunkTrackingAccess;
import com.dreamseekers.unit.mixin.SporeTrackedEntityAccess;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import java.util.List;
import java.util.LinkedHashSet;
/** Only clients already tracking this actual object, intersected with current authoritative visibility. */
public final class SporeEffectAudience {
    private SporeEffectAudience() { }
    public static List<ServerPlayer> visibleTracking(LivingEntity target) {
        if(!(target.level() instanceof ServerLevel level)||!level.getServer().isSameThread()||!(target instanceof Unit)
                ||!target.isAlive()||target.isRemoved()||level.getEntity(target.getUUID())!=target)return List.of();
        Object tracked=((SporeChunkTrackingAccess)level.getChunkSource().chunkMap).dreamseekers$trackedEntities().get(target.getId());
        if(!(tracked instanceof SporeTrackedEntityAccess access)||access.dreamseekers$trackedEntity()!=target)return List.of();
        LinkedHashSet<ServerPlayer> recipients=new LinkedHashSet<>();
        for(var connection:List.copyOf(access.dreamseekers$trackingConnections())) {
            ServerPlayer player=connection.getPlayer();
            if(player==null||player.isRemoved()||player.connection!=connection||player.serverLevel()!=level)continue;
            if(FogOfWarServerEvents.isFogActiveFor(player)&&level.dimension()!=Level.OVERWORLD)continue;
            var pos=target.blockPosition();
            if(FogOfWarServerEvents.isBlockVisibleFor(player,pos.getX(),pos.getZ()))recipients.add(player);
        }
        return List.copyOf(recipients);
    }
}
