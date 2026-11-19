// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.buildingability;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.resources.ResourceTransaction;
import java.util.Map;

/** Fixed server contract. Appended independently of the saved UnitSkill and research registries. */
public enum BuildingAbility {
    ROOT_CROWN("root_crown_shelter",0,20,900,160,12,10),
    GARDEN_RECOVERY("garden_recovery",30,0,600,120,8,6),
    HALL_PREPARATION("hall_spell_preparation",0,25,800,200,12,8),
    ECOLOGY_CODEX("ecology_codex",0,0,0,0,0,256),
    DAIS_WARD("dais_ward",0,20,900,120,12,8),
    WATCHTOWER_MARK("watchtower_observation",0,10,500,120,24,6);

    private final String path;
    private final int food,shadow,cooldown,duration,range,maximum;
    BuildingAbility(String path,int food,int shadow,int cooldown,int duration,int range,int maximum) {
        this.path=path;this.food=food;this.shadow=shadow;this.cooldown=cooldown;
        this.duration=duration;this.range=range;this.maximum=maximum;
    }
    public String path(){return path;}
    public int foodCost(){return food;}
    public int shadowCost(){return shadow;}
    public int cooldownTicks(){return cooldown;}
    public int durationTicks(){return duration;}
    public int range(){return range;}
    public int maxTargets(){return maximum;}
    public boolean implemented(){return this==ROOT_CROWN||this==GARDEN_RECOVERY||this==ECOLOGY_CODEX||this==HALL_PREPARATION||this==DAIS_WARD||this==WATCHTOWER_MARK;}
    public boolean paid(){return food>0||shadow>0;}
    public ResourceTransaction.Cost cost(){return new ResourceTransaction.Cost(food,0,0,
        shadow==0?Map.of():Map.of(DreamPassiveEconomy.SHADOW,shadow));}
    public static BuildingAbility forBuilding(BuildingPlacement p) {
        if(p==null)return null;
        var b=p.getBuilding();
        if(b==DreamseekersMod.DREAM_ANCHOR)return ROOT_CROWN;
        if(b==DreamseekersMod.REVERIE_GARDEN)return GARDEN_RECOVERY;
        if(b==DreamseekersMod.DREAM_HALL)return HALL_PREPARATION;
        if(b==DreamseekersMod.CONTRACT_ECOLOGY)return ECOLOGY_CODEX;
        if(b==DreamseekersMod.RESONANCE_DAIS)return DAIS_WARD;
        if(b==DreamseekersMod.SHADOW_WATCHTOWER)return WATCHTOWER_MARK;
        return null;
    }
}
