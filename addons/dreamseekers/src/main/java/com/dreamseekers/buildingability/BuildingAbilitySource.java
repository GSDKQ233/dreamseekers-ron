// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.buildingability;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.economy.DreamGardenPlacement;
import com.dreamseekers.function.DreamFunctionalView;
import com.dreamseekers.function.DreamFunctionLifecycle;
import com.dreamseekers.function.DreamFunctionalPlacement;
import com.dreamseekers.function.DreamWatchtowerPlacement;
import com.dreamseekers.production.DreamHallPlacement;
import com.dreamseekers.unitprogression.DreamSpecialistResearch;
import com.dreamseekers.worldtree.DreamAnchorPlacement;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.data.DataStorage;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.ExtendedResourcesSync;
import com.solegendary.reignofnether.resources.Resources;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;

/** Exact server-only building/account lifetime. Revocation is one-way and cannot be refreshed. */
@Mod.EventBusSubscriber(modid=DreamseekersMod.MOD_ID)
public final class BuildingAbilitySource {
    private static final Map<BuildingPlacement,Observation> OBSERVATIONS=new WeakHashMap<>();
    private static final java.util.Set<BuildingPlacement> TERMINAL=java.util.Collections.newSetFromMap(new WeakHashMap<>());
    private static long nextGeneration;
    private static long allocateGeneration(){return nextGeneration==Long.MAX_VALUE?Long.MAX_VALUE:++nextGeneration;}
    private record Backing(Object state,UUID identity,long ownerRevision,long runtimeRevision,int descriptor){}
    private static final class Observation {
        String owner;long capture,generation;DataStorage storage;BuildingAbilityState state;Backing backing;
        Observation(BuildingPlacement host,DataStorage storage,BuildingAbilityState state,Backing backing){generation=allocateGeneration();update(host,storage,state,backing);}
        void update(BuildingPlacement host,DataStorage nextStorage,BuildingAbilityState nextState,Backing nextBacking){
            owner=host.ownerName;capture=host.getCaptureRevision();storage=nextStorage;state=nextState;backing=nextBacking;
        }
        void observe(BuildingPlacement host,DataStorage nextStorage,BuildingAbilityState nextState,Backing nextBacking){
            if(!Objects.equals(owner,host.ownerName)||capture!=host.getCaptureRevision()||storage!=nextStorage||state!=nextState
                    ||!Objects.equals(backing,nextBacking)){
                generation=allocateGeneration();
                update(host,nextStorage,nextState,nextBacking);
            }
        }
    }
    private final BuildingPlacement host;
    private final ServerLevel level;
    private final BlockPos origin;
    private final String owner;
    private final Resources account;
    private final UUID accountEpoch,instance;
    private final RTSPlayer player;
    private final Faction faction;
    private final DataStorage storage;
    private final BuildingAbilityState state;
    private final Backing backing;
    private final long capture,generation;
    private boolean revoked;
    private BuildingAbilitySource(BuildingPlacement host,Observation observed){
        this.host=host;level=(ServerLevel)host.level;origin=host.originPos.immutable();owner=host.ownerName;
        account=ExtendedResourcesSync.account(owner);accountEpoch=account.accountEpoch;
        player=PlayerServerEvents.getRTSPlayer(owner);faction=player.faction;
        storage=observed.storage;state=observed.state;instance=state.instance();backing=observed.backing;
        capture=observed.capture;generation=observed.generation;
    }
    public BuildingPlacement host(){return host;}
    public ServerLevel level(){return level;}
    public String owner(){return owner;}
    public Resources account(){return account;}
    public UUID accountEpoch(){return accountEpoch;}
    public UUID instance(){return instance;}
    public long capture(){return capture;}
    public long generation(){return generation;}
    public BuildingAbilityState state(){return state;}

    /** Does not load chunks or inspect geometry from a missing footprint. */
    public static boolean physical(BuildingPlacement p){
        return p!=null&&!TERMINAL.contains(p)&&p.level instanceof ServerLevel level&&level.getServer().isSameThread()
            &&BuildingAbility.forBuilding(p)!=null&&BuildingServerEvents.getBuildings().contains(p)
            &&p.isBuilt&&!p.isDestroyedServerside&&p.ownerName!=null&&!p.ownerName.isBlank()
            &&p.getCaptureRevision()>=0&&p.level.hasChunksAt(p.minCorner,p.maxCorner)
            &&p.getHealth()>0&&!p.shouldBeDestroyed();
    }
    private static Backing backing(BuildingPlacement p,DataStorage storage){
        if(p instanceof DreamAnchorPlacement anchor){
            var tree=anchor.tree();
            return tree.supported&&tree.instance!=null&&anchor.unitBindingRevision()>=0&&anchor.unitBindingRevision()<Long.MAX_VALUE&&anchor.productionAvailable()
                ?new Backing(tree,tree.instance,0,anchor.unitBindingRevision(),tree.model.ordinal()):null;
        }
        if(p instanceof DreamGardenPlacement garden){
            int schema=storage.getData(DreamGardenPlacement.DECOR_SCHEMA).knownVersion();
            return garden.migrationReady()&&schema==1?new Backing(garden,null,0,0,schema):null;
        }
        if(p instanceof DreamFunctionalView functional){
            var snapshot=functional.stateSnapshot();
            if(!snapshot.supported()||snapshot.instanceId()==null||snapshot.ownerRevision()<0
                    ||snapshot.ownerRevision()==Long.MAX_VALUE||!snapshot.built()||!p.ownerName.equals(snapshot.owner()))return null;
            long runtime=p instanceof DreamFunctionalPlacement placed?placed.functionalOwnerRevision()
                :p instanceof DreamHallPlacement hall?hall.unitBindingRevision():0;
            if(runtime<0||runtime==Long.MAX_VALUE)return null;
            if(p instanceof DreamHallPlacement hall&&!hall.productionAvailable())return null;
            int descriptor=p instanceof DreamWatchtowerPlacement tower?tower.modelRevision():0;
            if(descriptor<0)return null;
            return new Backing(storage.getData(DreamFunctionLifecycle.STATE),snapshot.instanceId(),snapshot.ownerRevision(),runtime,descriptor);
        }
        return null;
    }
    private static Observation observe(BuildingPlacement p){
        var storage=p.getDataStorage();var state=storage.getData(BuildingAbilityState.TYPE);var backing=backing(p,storage);
        var observed=OBSERVATIONS.computeIfAbsent(p,key->new Observation(p,storage,state,backing));
        observed.observe(p,storage,state,backing);return observed;
    }
    public static boolean owns(ServerPlayer actor,BuildingPlacement p){
        return actor!=null&&physical(p)&&actor.serverLevel()==p.level&&actor.isAlive()&&!actor.isRemoved()&&actor.connection!=null
            &&actor.getGameProfile().getName().equals(p.ownerName)
            &&actor.serverLevel().getServer().getPlayerList().getPlayer(actor.getUUID())==actor
            &&actor.serverLevel().getServer().getPlayerList().getPlayerByName(p.ownerName)==actor
            &&DreamSpecialistResearch.ownerSupported(p.ownerName,false);
    }
    public static BuildingAbilitySource capture(ServerPlayer actor,BuildingPlacement p){
        if(!owns(actor,p))return null;
        var observed=observe(p);
        if(observed.backing==null||!observed.state.supported()||observed.state.instance()==null
                ||observed.state.revision()==Long.MAX_VALUE||observed.generation==Long.MAX_VALUE)return null;
        var source=new BuildingAbilitySource(p,observed);return source.valid()?source:null;
    }
    /** Unsupported states may still produce a disabled owner view; never a command/effect source. */
    static BuildingAbilityService.View inspect(ServerPlayer actor,BuildingPlacement p){
        if(!owns(actor,p))return null;
        var observed=observe(p);var ability=BuildingAbility.forBuilding(p);long now=p.level.getGameTime();
        boolean supported=now>=0&&observed.backing!=null&&observed.state.supported()&&observed.state.instance()!=null
            &&observed.state.revision()<Long.MAX_VALUE&&observed.generation<Long.MAX_VALUE;
        String reason=supported?BuildingAbilityService.prerequisite(p,ability):"unsupported";
        int cooldown=observed.state.cooldown(ability,now);
        if("ready".equals(reason)&&cooldown>0)reason="cooldown";
        return new BuildingAbilityService.View(observed.state.instance()==null?BuildingAbilityPackets.ZERO:observed.state.instance(),
            observed.capture,observed.generation,observed.state.revision(),ability,cooldown,supported,"ready".equals(reason),reason);
    }
    public boolean valid(){
        if(revoked)return false;
        if(!check()){revoked=true;return false;}return true;
    }
    private boolean check(){
        if(!physical(host)||host.level!=level||!host.originPos.equals(origin)||!owner.equals(host.ownerName)
                ||capture!=host.getCaptureRevision()||ExtendedResourcesSync.account(owner)!=account
                ||!accountEpoch.equals(account.accountEpoch)||!DreamSpecialistResearch.ownerSupported(owner,false)
                ||PlayerServerEvents.getRTSPlayer(owner)!=player||player.faction!=faction)return false;
        var observed=observe(host);
        return generation==observed.generation&&storage==observed.storage&&state==observed.state&&state.supported()
            &&instance.equals(state.instance())&&Objects.equals(backing,observed.backing);
    }
    /** Terminal object lifetime; never reads geometry, loads chunks, refunds, or rewrites saved state. */
    public static void detach(BuildingPlacement host){
        if(host!=null&&host.level instanceof ServerLevel level&&level.getServer().isSameThread()){
            TERMINAL.add(host);OBSERVATIONS.remove(host);
        }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event){OBSERVATIONS.clear();TERMINAL.clear();nextGeneration=0;}
}
