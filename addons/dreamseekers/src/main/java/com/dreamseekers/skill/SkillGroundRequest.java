// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;
import net.minecraft.core.BlockPos;
import java.util.UUID;
/** Position is the empty destination cell above supporting ground; sender determines the world. */
public record SkillGroundRequest(UnitSkill skill,int sourceId,UUID sourceUuid,long revision,BlockPos position) {
    public SkillGroundRequest { if(position!=null)position=position.immutable(); }
}
