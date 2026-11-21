// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.shadow;
import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.unit.TerrorbeakUnit;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.network.chat.Component;
import java.util.List;
/** Click is an authenticated economy request, never an unpriced native ability execution. */
public final class TerrorbeakUpgradeAbility extends Ability {
    public static final TerrorbeakUpgradeAbility INSTANCE=new TerrorbeakUpgradeAbility();
    private TerrorbeakUpgradeAbility(){super(null,0,0,0,true,true);showRangeCircle=false;showRangeLine=false;showRadiusCircle=false;}
    @Override public boolean shouldResetBehaviours(){return false;}
    @Override public AbilityButton getButton(Keybinding key,Unit unit){var source=(TerrorbeakUnit)unit;
        var tooltip=com.dreamseekers.client.DreamTooltipText.wrap(Component.translatable("dreamseekers.terrorbeak_upgrade.title"),Component.translatable("dreamseekers.terrorbeak_upgrade.cost"),Component.translatable("dreamseekers.terrorbeak_upgrade.rules"));
        return new AbilityButton(Component.translatable("dreamseekers.terrorbeak_upgrade.title").getString(),DreamseekersMod.CRAZY_TERRORBEAK_ICON,key,()->false,()->false,
            ()->TerrorbeakUpgradeClient.available(source),()->TerrorbeakUpgradeClient.upgrade(source),null,tooltip,this,source);
    }
}
