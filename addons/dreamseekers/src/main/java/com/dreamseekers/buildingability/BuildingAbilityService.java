// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.buildingability;

import com.dreamseekers.economy.DreamGardenPlacement;
import com.dreamseekers.worldtree.DreamAnchorPlacement;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.resources.ResourceTransaction;
import net.minecraft.server.level.ServerPlayer;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Fixed server transaction, independent of the deliberately unavailable legacy function request. */
public final class BuildingAbilityService {
    private static final Set<BuildingPlacement> CASTING=Collections.newSetFromMap(new IdentityHashMap<>());
    private BuildingAbilityService(){}
    public record View(UUID instance,long capture,long generation,long revision,BuildingAbility ability,
                       int cooldown,boolean supported,boolean available,String reason){}
    public static String prerequisite(BuildingPlacement p,BuildingAbility ability){
        if(ability==null||BuildingAbility.forBuilding(p)!=ability||!ability.implemented())return "unavailable";
        if(ability==BuildingAbility.ROOT_CROWN){
            if(!(p instanceof DreamAnchorPlacement tree)||!tree.tree().supported)return "unsupported";
            return tree.hasStandingUpgrade()
                &&tree.productionAvailable()?"ready":"requires_tree_upgrade";
        }
        if(ability==BuildingAbility.GARDEN_RECOVERY)
            return p instanceof DreamGardenPlacement garden&&garden.migrationReady()?"ready":"unsupported";
        if(ability==BuildingAbility.HALL_PREPARATION)
            return com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().stream().anyMatch(lab->lab.level==p.level
                &&p.ownerName.equals(lab.ownerName)&&lab.getBuilding()==com.dreamseekers.DreamseekersMod.RESONANCE_LABORATORY
                &&lab.isBuilt&&!lab.isDestroyedServerside&&lab.level.hasChunksAt(lab.minCorner,lab.maxCorner)
                &&lab instanceof com.dreamseekers.building.DreamLaboratoryPlacement laboratory&&laboratory.available())?"ready":"requires_laboratory";
        if(ability==BuildingAbility.DAIS_WARD)
            return p.level instanceof net.minecraft.server.level.ServerLevel level&&com.dreamseekers.resonance.ResonanceNetwork.live(p)
                &&com.dreamseekers.resonance.ResonanceResearch.unlocked(p.ownerName,false)
                &&com.dreamseekers.resonance.ResonanceNetwork.network(level,p.ownerName).rank(p)>0?"ready":"requires_dais_network_tuning";
        if(ability==BuildingAbility.WATCHTOWER_MARK)
            return p.getBuilding() instanceof com.dreamseekers.building.DreamWatchtower definition&&definition.isGarrisonEnabled(p)
                ?"ready":"requires_own_garrison";
        return ability==BuildingAbility.ECOLOGY_CODEX?"ready":"unavailable";
    }
    public static View view(ServerPlayer actor,BuildingPlacement host){
        var view=BuildingAbilitySource.inspect(actor,host);if(view==null)return null;
        if(!view.available()||!view.ability().paid())return view;
        var source=BuildingAbilitySource.capture(actor,host);
        if(source==null)return new View(view.instance(),view.capture(),view.generation(),view.revision(),view.ability(),view.cooldown(),false,false,"unsupported");
        var prepared=BuildingAbilityEffects.prepare(actor,source,view.ability());
        String reason=prepared==null?"unavailable":prepared.status();
        if(prepared!=null&&prepared.plan()!=null&&prepared.plan().targets()>0&&prepared.plan().valid())reason="ready";
        else if("ready".equals(reason))reason="no_target";
        if("ready".equals(reason)&&(source.account().food<view.ability().foodCost()
                ||source.account().extra.getOrDefault(com.dreamseekers.economy.DreamPassiveEconomy.SHADOW,0)<view.ability().shadowCost()))reason="insufficient_resources";
        return new View(view.instance(),view.capture(),view.generation(),view.revision(),view.ability(),view.cooldown(),view.supported(),"ready".equals(reason),reason);
    }
    public static String cast(ServerPlayer actor,BuildingPlacement host,UUID instance,long capture,long generation,long revision){
        var source=BuildingAbilitySource.capture(actor,host);
        if(source==null)return "forbidden";
        var ability=BuildingAbility.forBuilding(host);var state=source.state();
        if(!Objects.equals(instance,source.instance())||capture!=source.capture()||generation!=source.generation()||revision!=state.revision())return "stale_building";
        String prerequisite=prerequisite(host,ability);if(!"ready".equals(prerequisite))return prerequisite;
        if(!ability.paid())return "unavailable";
        long now=source.level().getGameTime();
        if(now<0||now>Long.MAX_VALUE-ability.cooldownTicks()||state.revision()==Long.MAX_VALUE)return "unavailable";
        if(state.readyAt(ability)>now)return "cooldown";
        if(!CASTING.add(host))return "busy";
        try {
            var prepared=BuildingAbilityEffects.prepare(actor,source,ability);
            if(prepared==null)return "unavailable";
            var plan=prepared.plan();
            if(plan==null||plan.targets()<=0)return "ready".equals(prepared.status())?"no_target":prepared.status();
            if(!source.valid()||!plan.valid()||!state.canSpend(ability,now)||state.revision()!=revision)return "stale_building";
            var result=ResourceTransaction.exchange(source.level().getServer(),source.account(),source.accountEpoch(),source.account().revision,
                ability.cost(),ResourceTransaction.Cost.ZERO);
            if(!result.applied())return result.status()==ResourceTransaction.Status.INSUFFICIENT_RESOURCES?"insufficient_resources":"unavailable";
            // The debit has no external callback. Commit nonce/CD before any effect publication.
            if(!state.spend(ability,now))throw new IllegalStateException("Building ability cooldown changed inside atomic debit");
            try { plan.apply(); }
            catch(RuntimeException error){
                ReignOfNether.LOGGER.error("Paid building ability canceled after its cooldown commit; no refund or replay",error);
                return "paid_cancelled";
            }
            return "success";
        } finally {CASTING.remove(host);}
    }
}
