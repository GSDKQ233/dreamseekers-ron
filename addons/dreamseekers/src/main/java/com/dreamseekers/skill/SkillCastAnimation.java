// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import net.minecraft.world.entity.Mob;
import net.pasterdream.entity.AaroncosLefthand0Entity;
import net.pasterdream.entity.AaroncosRighthand0Entity;
import net.pasterdream.entity.BlackBeetleEntity;
import net.pasterdream.entity.BlackBeetleMotherEntity;
import net.pasterdream.entity.ShadowGolemEntity;
import net.pasterdream.entity.ShadowSquealGhost0Entity;
import net.pasterdream.entity.TerrorbeakEntity;
import net.pasterdream.entity.WindKnightEntity;

/**
 * Presentation only: plays the cast animation that already ships in the installed PasterDream model
 * through its own synced one-shot animation channel. Skills whose model has no such animation stay silent.
 */
public final class SkillCastAnimation {
    private SkillCastAnimation() {}

    /** Animation key inside the native animation file of the unit that owns the skill, or null. */
    public static String nativeAnimation(UnitSkill skill) {
        if (skill == null) return null;
        return switch (skill) {
            case WIND_DASH -> "skill_0";
            case TERRORBEAK_DREAD_ROAR -> "roar";
            case SHADOW_GOLEM_STRIKE, MOTHER_BROOD, BEETLE_ADVANCE, WEEPING_ECHOES -> "skill";
            case AARONCOS_LEFT_PRESS -> "skill_hit";
            case AARONCOS_RIGHT_SHOCK -> "skill_magicball";
            case AARONCOS_RIGHT_VORTEX -> "skill_vortex";
            default -> null;
        };
    }

    /** Server-side; the native entity synchronizes the one-shot animation to tracking clients. */
    public static void play(Mob caster, UnitSkill skill) {
        String animation = nativeAnimation(skill);
        if (animation == null || caster == null || caster.level().isClientSide() || !caster.isAlive()) return;
        switch (skill) {
            case WIND_DASH -> { if (caster instanceof WindKnightEntity e) e.setAnimation(animation); }
            // The crazy variant's model has no roar animation, so only the ordinary terrorbeak plays it.
            case TERRORBEAK_DREAD_ROAR -> { if (caster instanceof TerrorbeakEntity e) e.setAnimation(animation); }
            case SHADOW_GOLEM_STRIKE -> { if (caster instanceof ShadowGolemEntity e) e.setAnimation(animation); }
            case MOTHER_BROOD -> { if (caster instanceof BlackBeetleMotherEntity e) e.setAnimation(animation); }
            case BEETLE_ADVANCE -> { if (caster instanceof BlackBeetleEntity e) e.setAnimation(animation); }
            case WEEPING_ECHOES -> { if (caster instanceof ShadowSquealGhost0Entity e) e.setAnimation(animation); }
            case AARONCOS_LEFT_PRESS -> { if (caster instanceof AaroncosLefthand0Entity e) e.setAnimation(animation); }
            case AARONCOS_RIGHT_SHOCK, AARONCOS_RIGHT_VORTEX -> { if (caster instanceof AaroncosRighthand0Entity e) e.setAnimation(animation); }
            default -> { }
        }
    }
}
