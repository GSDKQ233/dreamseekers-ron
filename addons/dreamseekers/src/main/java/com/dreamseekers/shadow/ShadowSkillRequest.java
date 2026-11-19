// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.shadow;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
/** Reserved self-target contract only: undefined skill targeting/cost/effect is not invented. */
public record ShadowSkillRequest(int schema,UUID requestId,Skill skill,Source caster,SelfTarget target) {
    public enum Skill { SHADOW_GHOST_SPECIAL, SHADOW_SQUEAL_GHOST_SPECIAL, TERRORBEAK_SPECIAL, FRIENDLY_GHOST_CONTRACT, SHADOW_HAND_SPECIAL, ASH_BONE_WING_SPECIAL, CRAZY_TERRORBEAK_SPECIAL, SHADOW_GOLEM_SPECIAL, WEEPING_SQUEAL_SUMMON, AARONCOS_LEFT_SPECIAL, AARONCOS_RIGHT_SPECIAL }
    public record Source(int entityId,UUID entityUuid,long ownershipRevision,ResourceLocation dimension) { }
    public record SelfTarget() { }
}
