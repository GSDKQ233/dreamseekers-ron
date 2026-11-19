// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.spore;
import com.dreamseekers.unit.DreamRtsUnit;
import com.dreamseekers.unit.DreamNoncombatUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.server.level.ServerPlayer;
/** Server-only fail-closed boundary; there is intentionally no summon, activation, payment or network handler. */
public final class SporeSummonService {
    private SporeSummonService() { }
    public enum Result { INVALID_REQUEST, INVALID_SOURCE, WRONG_DIMENSION, STALE_OWNER, NOT_OWNER, INVALID_TARGET, UNAVAILABLE }
    public static Result request(ServerPlayer sender,SporeSummonRequest request) {
        if(sender==null||request==null||request.schema()!=1||request.requestId()==null||request.caster()==null
                ||request.target()==null||request.target().position()==null)return Result.INVALID_REQUEST;
        var level=sender.serverLevel();if(!level.getServer().isSameThread())return Result.INVALID_REQUEST;
        var src=request.caster();
        if(src.entityUuid()==null||src.dimension()==null||src.ownershipRevision()<0)return Result.INVALID_REQUEST;
        if(!level.dimension().location().equals(src.dimension()))return Result.WRONG_DIMENSION;
        var entity=level.getEntity(src.entityId());
        if(!(entity instanceof Unit unit)||!entity.isAlive()||entity.isRemoved()||!entity.getUUID().equals(src.entityUuid()))return Result.INVALID_SOURCE;
        long revision;
        if(entity instanceof DreamRtsUnit owned)revision=owned.rts().ownershipRevision;
        else if(entity instanceof DreamNoncombatUnit owned)revision=owned.rts().ownershipRevision;
        else return Result.INVALID_SOURCE;
        if(revision!=src.ownershipRevision())return Result.STALE_OWNER;
        if(unit.getOwnerName().isBlank()||!unit.getOwnerName().equals(sender.getGameProfile().getName()))return Result.NOT_OWNER;
        if(!level.isInWorldBounds(request.target().position())||!level.hasChunkAt(request.target().position()))return Result.INVALID_TARGET;
        return Result.UNAVAILABLE; // Eligibility/economy/range/visibility/effect rules are NOT secretly enabled.
    }
}
