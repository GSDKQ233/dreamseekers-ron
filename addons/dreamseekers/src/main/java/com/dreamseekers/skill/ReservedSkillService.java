// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;
import com.dreamseekers.unit.PinkSlimeUnit;
import com.dreamseekers.unit.JellyfishUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
/** No handler can enable this reserved interface. A future effect requires a separately reviewed implementation. */
public final class ReservedSkillService {
    private ReservedSkillService() { }
    public enum Result { INVALID_REQUEST, INVALID_SOURCE, NOT_OWNER, INVALID_TARGET, DISABLED }
    public static Result request(ServerPlayer authenticatedSender, ReservedSkillRequest request) {
        if (authenticatedSender == null || request == null || request.skill() == null
                || request.sourceUuid() == null || request.target() == null) return Result.INVALID_REQUEST;
        var level = authenticatedSender.serverLevel();
        // Never accept client world state or call game mutation from a networking/worker thread.
        if (!level.getServer().isSameThread()) return Result.INVALID_REQUEST;
        Entity entity = level.getEntity(request.sourceId());
        if (!(entity instanceof Unit source) || !entity.isAlive() || entity.isRemoved()
                || !entity.getUUID().equals(request.sourceUuid())) return Result.INVALID_SOURCE;
        boolean sourceMatchesSkill = switch (request.skill()) {
            case STONE_SPIRIT_SIEGE_RESEARCH -> entity instanceof com.dreamseekers.unit.StoneSpiritThrowerUnit;
            case WIND_KNIGHT_SPECIAL -> entity instanceof com.dreamseekers.unit.WindKnightUnit;
            case BONE_WING_SPECIAL -> entity instanceof com.dreamseekers.unit.BoneWingUnit;
            case PINK_SLIME_SUPPORT -> entity instanceof PinkSlimeUnit;
            case JELLYFISH_PROTECTION, JELLYFISH_GRADUAL_HEAL -> entity instanceof JellyfishUnit;
        };
        if (!sourceMatchesSkill) return Result.INVALID_SOURCE;
        if (source.getOwnerName().isBlank()
                || !source.getOwnerName().equals(authenticatedSender.getGameProfile().getName())) return Result.NOT_OWNER;
        if (request.target() instanceof ReservedSkillRequest.EntityTarget target) {
            Entity victim = level.getEntity(target.entityId());
            if (target.uuid() == null || victim == null || !victim.isAlive()
                    || victim.isRemoved() || !victim.getUUID().equals(target.uuid())) return Result.INVALID_TARGET;
        }
        return Result.DISABLED; // No resources consumed/granted, target changes, summons, damage or status effects.
    }
}
