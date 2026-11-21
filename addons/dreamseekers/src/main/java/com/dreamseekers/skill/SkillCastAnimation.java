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

    /** Server ticks the animation name is kept on the synced channel; shorter than every mapped animation. */
    public static final int HOLD_TICKS = 6;

    private static final class Pending {
        final Mob caster; final UnitSkill skill; int ticks = HOLD_TICKS;
        Pending(Mob caster, UnitSkill skill) { this.caster = caster; this.skill = skill; }
    }
    private static final List<Pending> PENDING = new ArrayList<>();

    /**
     * Server-side. PasterDream's own LivingTick handler consumes the synced animation name on the server one
     * entity tick after it is written, so on its own the name is a one-tick pulse that a client misses whenever
     * both data packets reach it between two of its ticks. The name is therefore rewritten at the end of each
     * server tick for {@link #HOLD_TICKS} ticks, after the entity ticks and before the next tracker broadcast.
     */
    public static void play(Mob caster, UnitSkill skill) {
        if (nativeAnimation(skill) == null || caster == null || caster.level().isClientSide() || !caster.isAlive()) return;
        PENDING.removeIf(pending -> pending.caster == caster);
        PENDING.add(new Pending(caster, skill));
    }

    @SubscribeEvent public static void flush(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        for (Pending pending : List.copyOf(PENDING)) {
            boolean live = !pending.caster.isRemoved() && pending.caster.isAlive();
            if (live) apply(pending.caster, pending.skill);
            if (!live || --pending.ticks <= 0) PENDING.remove(pending);
        }
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
