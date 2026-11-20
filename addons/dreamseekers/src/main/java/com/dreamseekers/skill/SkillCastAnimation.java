// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
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
@Mod.EventBusSubscriber(modid = "dreamseekers")
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

    private record Pending(Mob caster, UnitSkill skill) {}
    private static final List<Pending> PENDING = new ArrayList<>();

    /**
     * Server-side. PasterDream's own LivingTick handler consumes the synced animation name on the server too,
     * so a name written before the caster's tick is cleared before the entity tracker sends it. The name is
     * therefore written at the end of the server tick, after every entity tick and before the next broadcast.
     */
    public static void play(Mob caster, UnitSkill skill) {
        if (nativeAnimation(skill) == null || caster == null || caster.level().isClientSide() || !caster.isAlive()) return;
        PENDING.add(new Pending(caster, skill));
    }

    @SubscribeEvent public static void flush(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        List<Pending> due = new ArrayList<>(PENDING);
        PENDING.clear();
        for (Pending pending : due) apply(pending.caster(), pending.skill());
    }

    private static void apply(Mob caster, UnitSkill skill) {
        String animation = nativeAnimation(skill);
        if (animation == null || caster.isRemoved() || !caster.isAlive()) return;
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
