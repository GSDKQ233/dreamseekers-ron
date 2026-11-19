// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.aaroncos;
import com.dreamseekers.unit.AaroncosLeftHandUnit;
import com.dreamseekers.unit.AaroncosRightHandUnit;
import net.minecraft.server.level.ServerPlayer;
/** Permanently unavailable reserve: no provider, network activation, payer or NBT enable flag. */
public final class AaroncosPairService {
    private AaroncosPairService() { }
    public enum Result { INVALID_REQUEST, INVALID_SOURCE, WRONG_DIMENSION, STALE_OWNER, NOT_OWNER, UNAVAILABLE }
    public static Result request(ServerPlayer sender,AaroncosPairRequest request){
        if(sender==null||request==null||request.schema()!=1||request.requestId()==null||request.intent()==null||request.left()==null||request.right()==null)return Result.INVALID_REQUEST;
        var level=sender.serverLevel();if(!level.getServer().isSameThread())return Result.INVALID_REQUEST;
        for(var source:java.util.List.of(request.left(),request.right())){
            if(source.entityUuid()==null||source.dimension()==null||source.ownershipRevision()<0)return Result.INVALID_REQUEST;
            if(!level.dimension().location().equals(source.dimension()))return Result.WRONG_DIMENSION;
            var e=level.getEntity(source.entityId());
            if(!(e instanceof ControlledAaroncosHand hand)||!e.isAlive()||e.isRemoved()||!e.getUUID().equals(source.entityUuid())||level.getEntity(source.entityUuid())!=e)return Result.INVALID_SOURCE;
            if(hand.rts().ownershipRevision!=source.ownershipRevision())return Result.STALE_OWNER;
            if(hand.getOwnerName().isBlank()||!hand.getOwnerName().equals(sender.getGameProfile().getName()))return Result.NOT_OWNER;
        }
        if(!(level.getEntity(request.left().entityId()) instanceof AaroncosLeftHandUnit)||!(level.getEntity(request.right().entityId()) instanceof AaroncosRightHandUnit))return Result.INVALID_SOURCE;
        return Result.UNAVAILABLE; // No pair ID minted, no cost/cooldown/HP/ownership/population/world writes.
    }
}
