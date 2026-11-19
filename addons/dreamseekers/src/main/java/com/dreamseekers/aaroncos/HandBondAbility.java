// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.aaroncos;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.unit.AaroncosLeftHandUnit;
import com.dreamseekers.unit.AaroncosRightHandUnit;
import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Standard authenticated command transport; client selection is never payment or ownership authority. */
public final class HandBondAbility extends Ability {
    public static final HandBondAbility INSTANCE=new HandBondAbility();
    private HandBondAbility(){super(null,0,16,0,false,true);showRangeCircle=false;}
    @Override public boolean shouldResetBehaviours(){return false;}
    @Override public float getCooldown(Unit unit){return 0;}
    private static List<net.minecraft.util.FormattedCharSequence> tooltip(){
        var mc=net.minecraft.client.Minecraft.getInstance();var out=new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();
        for(String key:List.of("ability.dreamseekers.hand_bond","ability.dreamseekers.hand_bond.select","ability.dreamseekers.hand_bond.unlink","ability.dreamseekers.hand_bond.rules"))
            out.addAll(mc.font.split(Component.translatable(key),Math.max(1,Math.min(280,mc.getWindow().getGuiScaledWidth()-24))));
        return out;
    }
    private static void pair(){
        var mc=net.minecraft.client.Minecraft.getInstance();if(mc.player==null||mc.getConnection()==null)return;
        var selected=UnitClientEvents.getSelectedUnits();
        var left=selected.stream().filter(e->e instanceof AaroncosLeftHandUnit).toList();
        var right=selected.stream().filter(e->e instanceof AaroncosRightHandUnit).toList();
        if(selected.size()!=2||left.size()!=1||right.size()!=1){mc.player.sendSystemMessage(Component.translatable("ability.dreamseekers.hand_bond.select"));return;}
        mc.getConnection().sendCommand("rts-hand-pair "+left.get(0).getUUID()+" "+right.get(0).getUUID());
    }
    private static void unpair(Unit source){
        var mc=net.minecraft.client.Minecraft.getInstance();if(mc.player!=null&&mc.getConnection()!=null)
            mc.getConnection().sendCommand("rts-hand-unpair "+((net.minecraft.world.entity.Entity)source).getUUID());
    }
    @Override public AbilityButton getButton(Keybinding key,Unit unit){
        return new AbilityButton("hand_bond",DreamseekersMod.AARONCOS_LEFT_HAND_ICON,key,()->false,()->false,
            ()->HandBondService.AVAILABLE&&com.dreamseekers.unitprogression.DreamSpecialistResearch.unlocked(unit.getOwnerName(),DreamseekersMod.id("hand_pairing"),true),
            HandBondAbility::pair,()->unpair(unit),tooltip(),this,unit){
                @Override public void renderTooltip(net.minecraft.client.gui.GuiGraphics graphics,int x,int y){tooltipLines=tooltip();super.renderTooltip(graphics,x,y);}
            };
    }
}
