// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;
import java.util.UUID;
import net.minecraft.core.BlockPos;
/** Authenticated sender's dimension only. Server resolves an exact registered building object. */
public record SkillBuildingRequest(UnitSkill skill,int sourceId,UUID sourceUuid,long revision,
                                  BlockPos origin,BlockPos contact) { }
