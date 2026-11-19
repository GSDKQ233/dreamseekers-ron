// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;
import java.util.UUID;
/** Typed integration boundary only; no wire packet, enabled toggle, costs or invented effect. */
public record ReservedSkillRequest(Skill skill, int sourceId, UUID sourceUuid, Target target) {
    public enum Skill { PINK_SLIME_SUPPORT, JELLYFISH_PROTECTION, JELLYFISH_GRADUAL_HEAL, BONE_WING_SPECIAL, WIND_KNIGHT_SPECIAL, STONE_SPIRIT_SIEGE_RESEARCH }
    public sealed interface Target permits Self, EntityTarget { }
    public record Self() implements Target { }
    public record EntityTarget(int entityId, UUID uuid) implements Target { }
}
