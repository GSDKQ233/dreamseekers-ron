// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.scout;

import com.dreamseekers.unit.PinkChickenScout;
import com.dreamseekers.skill.SnapshotSkillButton;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Custom target mode deliberately does not reuse an unrelated combat UnitAction. */
public final class ScoutMarkAbility extends Ability {
    public static final ScoutMarkAbility INSTANCE = new ScoutMarkAbility();
    private ScoutMarkAbility() {
        super(null, ScoutBalance.MARK_COOLDOWN_TICKS, ScoutBalance.MARK_RANGE, 0, true, true);
        showRangeCircle = false; showRangeLine = false; showRadiusCircle = false;
    }
    @Override public boolean shouldResetBehaviours() { return false; }
    @Override public float getCooldown(Unit unit) {
        if (!(unit instanceof PinkChickenScout scout)) return cooldownMax;
        return scout.level().isClientSide() ? ScoutMarkClient.cooldown(scout) : scout.scoutMark().cooldownTicks();
    }
    @Override public AbilityButton getButton(Keybinding key, Unit unit) {
        PinkChickenScout scout = (PinkChickenScout) unit;
        return SnapshotSkillButton.bind(new AbilityButton("Reconnaissance Mark", ResourceLocation.fromNamespaceAndPath("dreamseekers", "textures/icons/skill_scout_mark.png"),
                key, () -> ScoutMarkClient.isArmed(scout), () -> false, () -> true,
                () -> ScoutMarkClient.arm(scout), ScoutMarkClient::cancel,
                List.of(Component.translatable("ability.dreamseekers.scout_mark").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        Component.translatable("ability.dreamseekers.scout_mark.tooltip1", ScoutBalance.MARK_RANGE,
                                ScoutBalance.MARK_DURATION_TICKS / 20, ScoutBalance.MARK_COOLDOWN_TICKS / 20).getVisualOrderText(),
                        Component.translatable("ability.dreamseekers.scout_mark.tooltip2").getVisualOrderText(),
                        Component.translatable("ability.dreamseekers.scout_mark.tooltip3").getVisualOrderText()), this, unit),()->ScoutMarkClient.readiness(scout));
    }
}
