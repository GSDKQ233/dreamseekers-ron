package com.dreamseekers.neutralcontract;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.dreamseekers.unit.FireflyWorker;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class ContractAbility extends Ability {
    public static final ContractAbility INSTANCE=new ContractAbility();
    private ContractAbility(){super(null,0,32,0,true,true);showRangeCircle=false;showRangeLine=false;showRadiusCircle=false;}
    @Override public boolean shouldResetBehaviours(){return false;}
    @Override public AbilityButton getButton(Keybinding key,Unit unit){var worker=(FireflyWorker)unit;
        var tooltip=new ArrayList<net.minecraft.util.FormattedCharSequence>();
        for(String suffix:List.of("title","prices","rules","capacity_rules","cancel_rule"))tooltip.add(Component.translatable("neutral_contract."+suffix).getVisualOrderText());
        var button=new AbilityButton("neutral_contract",ResourceLocation.fromNamespaceAndPath("minecraft","textures/item/lead.png"),key,
            ()->ContractClient.armed(worker)||ContractClient.active(worker),()->false,()->ContractClient.available(worker),
            ()->ContractClient.arm(worker),()->ContractClient.cancel(worker),tooltip,this,unit);
        button.bottomLeftText=()->ContractClient.label(worker);return button;
    }
}
