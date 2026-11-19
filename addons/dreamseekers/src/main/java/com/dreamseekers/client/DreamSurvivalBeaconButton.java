// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.client;

import com.dreamseekers.DreamseekersFaction;
import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.building.DreamBuildingMilestones;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.gamemode.ClientGameModeHelper;
import com.solegendary.reignofnether.gamemode.GameMode;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.survival.SurvivalClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.client.Minecraft;

public final class DreamSurvivalBeaconButton {
    private DreamSurvivalBeaconButton() {}

    public static void register() {
        // Directly add one unbound button: registerBuildings would change the native beacon's faction.
        DreamseekersMod.DREAMSEEKERS.getBuildingButtons().add(Buildings.BEACON.getBuildButton(null,
                () -> SurvivalClientEvents.isEnabled && ClientGameModeHelper.gameMode == GameMode.SURVIVAL,
                DreamSurvivalBeaconButton::hasPrerequisites, DreamseekersFaction.BEACON_PREREQUISITE));
    }

    private static boolean hasPrerequisites() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return false;
        String owner = HudClientEvents.hudSelectedEntity instanceof Unit unit
                ? unit.getOwnerName() : mc.player.getGameProfile().getName();
        return DreamseekersMod.DREAMSEEKERS.equals(PlayerClientEvents.getPlayerFaction(owner))
                && DreamBuildingMilestones.hasUpgradedTreeAndLaboratory(mc.level, owner);
    }
}
