// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;
import com.dreamseekers.unit.RtsUnitState;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.world.entity.Mob;
/** Shared skill host for attacking and genuinely non-attacking support adapters. */
public interface SkillUnit extends Unit {
    RtsUnitState rts();
    default Mob mob() { return (Mob)this; }
}
