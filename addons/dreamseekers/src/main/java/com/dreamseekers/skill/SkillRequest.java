// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;
import java.util.UUID;
/** Input only: never accepts an owner, balance, duration, cooldown or effect amount from a client. */
public record SkillRequest(UnitSkill skill, int sourceId, UUID sourceUuid, long revision,
                           int targetId, UUID targetUuid) { }
