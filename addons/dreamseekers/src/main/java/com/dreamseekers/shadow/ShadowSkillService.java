// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.shadow;
import com.dreamseekers.unit.ShadowGhostUnit;
import com.dreamseekers.unit.ShadowSquealGhostUnit;
import com.dreamseekers.unit.TeamSafeCombatUnit;
import net.minecraft.server.level.ServerPlayer;
/** Legacy ownership-only requests stay closed; implemented abilities use the revision-bound SkillService path. */
public final class ShadowSkillService {
    private ShadowSkillService() { }
    public enum Result { INVALID_REQUEST,INVALID_SOURCE,WRONG_DIMENSION,STALE_OWNER,NOT_OWNER,UNAVAILABLE }
    public static Result request(ServerPlayer sender,ShadowSkillRequest request){
        if(sender==null||request==null||request.schema()!=1||request.requestId()==null||request.caster()==null||request.target()==null||request.skill()==null)return Result.INVALID_REQUEST;
        var level=sender.serverLevel();if(!level.getServer().isSameThread())return Result.INVALID_REQUEST;
        var source=request.caster();if(source.entityUuid()==null||source.dimension()==null||source.ownershipRevision()<0)return Result.INVALID_REQUEST;
        if(!level.dimension().location().equals(source.dimension()))return Result.WRONG_DIMENSION;
        var entity=level.getEntity(source.entityId());
        boolean exactType = request.skill()==ShadowSkillRequest.Skill.SHADOW_GHOST_SPECIAL && entity instanceof ShadowGhostUnit
                || request.skill()==ShadowSkillRequest.Skill.SHADOW_SQUEAL_GHOST_SPECIAL && entity instanceof ShadowSquealGhostUnit
                || request.skill()==ShadowSkillRequest.Skill.TERRORBEAK_SPECIAL && entity instanceof com.dreamseekers.unit.TerrorbeakUnit
                || request.skill()==ShadowSkillRequest.Skill.FRIENDLY_GHOST_CONTRACT && entity instanceof com.dreamseekers.unit.FriendlyGhostUnit
                || request.skill()==ShadowSkillRequest.Skill.SHADOW_HAND_SPECIAL && entity instanceof com.dreamseekers.unit.ShadowHandUnit
                || request.skill()==ShadowSkillRequest.Skill.ASH_BONE_WING_SPECIAL && entity instanceof com.dreamseekers.unit.AshBoneWingUnit
                || request.skill()==ShadowSkillRequest.Skill.CRAZY_TERRORBEAK_SPECIAL && entity instanceof com.dreamseekers.unit.CrazyTerrorbeakUnit
                || request.skill()==ShadowSkillRequest.Skill.SHADOW_GOLEM_SPECIAL && entity instanceof com.dreamseekers.unit.ShadowGolemUnit
                || request.skill()==ShadowSkillRequest.Skill.WEEPING_SQUEAL_SUMMON && entity instanceof com.dreamseekers.unit.WeepingSquealGhostUnit
                || request.skill()==ShadowSkillRequest.Skill.AARONCOS_LEFT_SPECIAL && entity instanceof com.dreamseekers.unit.AaroncosLeftHandUnit
                || request.skill()==ShadowSkillRequest.Skill.AARONCOS_RIGHT_SPECIAL && entity instanceof com.dreamseekers.unit.AaroncosRightHandUnit;
        if(!exactType||!(entity instanceof TeamSafeCombatUnit ghost)||!entity.isAlive()||entity.isRemoved()||!entity.getUUID().equals(source.entityUuid())||level.getEntity(source.entityUuid())!=entity)return Result.INVALID_SOURCE;
        if(ghost.rts().ownershipRevision!=source.ownershipRevision())return Result.STALE_OWNER;
        if(ghost.getOwnerName().isBlank()||!ghost.getOwnerName().equals(sender.getGameProfile().getName()))return Result.NOT_OWNER;
        return Result.UNAVAILABLE; // Fail closed: zero costs, cooldown, population, buffs, summons or world writes.
    }
}
