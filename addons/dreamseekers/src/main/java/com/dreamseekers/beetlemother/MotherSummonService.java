// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.beetlemother;
import com.dreamseekers.unit.BeetleMotherUnit;
import net.minecraft.server.level.ServerPlayer;
/** Legacy ownership-only requests remain unavailable. Summoning uses authenticated SkillRequest
 * with skill revision and cooldown validation. */
public final class MotherSummonService {
    private MotherSummonService() { }
    public enum Result { INVALID_REQUEST,INVALID_SOURCE,WRONG_DIMENSION,STALE_OWNER,NOT_OWNER,UNAVAILABLE }
    public static Result request(ServerPlayer sender,MotherSummonRequest request){
        if(sender==null||request==null||request.schema()!=1||request.requestId()==null||request.caster()==null||request.target()==null)return Result.INVALID_REQUEST;
        var level=sender.serverLevel();if(!level.getServer().isSameThread())return Result.INVALID_REQUEST;
        var source=request.caster();if(source.entityUuid()==null||source.dimension()==null||source.ownershipRevision()<0)return Result.INVALID_REQUEST;
        if(!level.dimension().location().equals(source.dimension()))return Result.WRONG_DIMENSION;
        var entity=level.getEntity(source.entityId());
        if(!(entity instanceof BeetleMotherUnit mother)||!entity.isAlive()||entity.isRemoved()||!entity.getUUID().equals(source.entityUuid())||level.getEntity(source.entityUuid())!=entity)return Result.INVALID_SOURCE;
        if(mother.rts().ownershipRevision!=source.ownershipRevision())return Result.STALE_OWNER;
        if(mother.getOwnerName().isBlank()||!mother.getOwnerName().equals(sender.getGameProfile().getName()))return Result.NOT_OWNER;
        return Result.UNAVAILABLE; // Fail closed: zero costs, cooldown, population, buffs, summons or world writes.
    }
}
