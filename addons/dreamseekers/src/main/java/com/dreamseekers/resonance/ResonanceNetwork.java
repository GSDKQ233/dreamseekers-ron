package com.dreamseekers.resonance;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.building.*;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.function.*;
import com.dreamseekers.skill.*;
import com.dreamseekers.unitprogression.DreamSpecialistResearch;
import com.dreamseekers.worldtree.DreamAnchorPlacement;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Server-authoritative bounded tree network. No chunk loading, implicit allied links or persistent effects. */
@Mod.EventBusSubscriber(modid=DreamseekersMod.MOD_ID)
public final class ResonanceNetwork {
    public static final int LINK_RANGE=24,PULSE_RANGE=12,MAX_TARGETS=16,COST=15,COOLDOWN=600;
    public static final String EFFECT_KEY="resonance_pulse";
    private static final Comparator<BuildingPlacement> ORDER=Comparator.comparingInt((BuildingPlacement p)->p.originPos.getX())
        .thenComparingInt(p->p.originPos.getY()).thenComparingInt(p->p.originPos.getZ());
    private static final Map<BuildingPlacement,Observation> OBSERVATIONS=new WeakHashMap<>();
    private static final Set<BuildingPlacement> CASTING=Collections.newSetFromMap(new IdentityHashMap<>());
    private ResonanceNetwork(){}
    private static final class Observation {
        String owner; UUID identity; long ownerRevision,capture,generation,observedOwner; boolean supported; Object state;
        Observation(BuildingPlacement p,DreamFunctionState.Snapshot s,ResonanceState r){update(p,s,r);}
        void update(BuildingPlacement p,DreamFunctionState.Snapshot s,ResonanceState r){owner=p.ownerName;identity=s.instanceId();ownerRevision=s.ownerRevision();capture=p.getCaptureRevision();observedOwner=((DreamFunctionalPlacement)p).functionalOwnerRevision();supported=s.supported()&&r.supported();state=r;}
        void observe(BuildingPlacement p,DreamFunctionState.Snapshot s,ResonanceState r){
            if(!Objects.equals(owner,p.ownerName)||!Objects.equals(identity,s.instanceId())||ownerRevision!=s.ownerRevision()
                ||capture!=p.getCaptureRevision()||observedOwner!=((DreamFunctionalPlacement)p).functionalOwnerRevision()||supported!=(s.supported()&&r.supported())||state!=r){
                if(generation<Long.MAX_VALUE)generation++;update(p,s,r);
            }
        }
    }
    public record Network(List<BuildingPlacement> active,int total,int limit,Map<BuildingPlacement,BuildingPlacement> parents){public Network{active=List.copyOf(active);parents=Map.copyOf(parents);}public int rank(BuildingPlacement p){return active.indexOf(p)+1;}}
    public record View(UUID instance,long ownerRevision,long capture,long generation,int rank,int active,int total,int limit,int cooldown,boolean supported,boolean tuned){}
    public static boolean isDais(BuildingPlacement p){return p!=null&&p.getBuilding() instanceof DreamScaffoldBuilding b&&b.spec()==DreamBuildingSpec.RESONANCE_DAIS;}
    private static boolean physical(BuildingPlacement p){return p!=null&&p.level instanceof ServerLevel level&&level.getServer().isSameThread()
        &&p.isBuilt&&!p.isDestroyedServerside&&!p.shouldBeDestroyed()&&p.getHealth()>0&&!p.ownerName.isBlank()
        &&p.getCaptureRevision()>=0&&BuildingServerEvents.getBuildings().contains(p)&&p.level.hasChunksAt(p.minCorner,p.maxCorner);}
    private static Observation observation(BuildingPlacement p){
        if(!(p instanceof DreamFunctionalPlacement view))return null;
        ResonanceState r=p.getDataStorage().getData(ResonanceState.TYPE);var s=view.stateSnapshot();
        var o=OBSERVATIONS.computeIfAbsent(p,key->new Observation(p,s,r));o.observe(p,s,r);return o;
    }
    public static boolean live(BuildingPlacement p){if(!isDais(p)||!physical(p))return false;var o=observation(p);
        return o!=null&&o.supported&&o.identity!=null&&o.generation<Long.MAX_VALUE&&o.ownerRevision<Long.MAX_VALUE&&o.observedOwner>=0;}
    public static boolean owns(ServerPlayer actor,BuildingPlacement p){return actor!=null&&p!=null&&actor.serverLevel()==p.level
        &&actor.getGameProfile().getName().equals(p.ownerName)&&DreamSpecialistResearch.ownerSupported(p.ownerName,false)
        &&isDais(p)&&physical(p);}
    /** Bounds each component before multiplication, so hostile far-apart coordinates cannot overflow. */
    public static boolean linked(BlockPos a,BlockPos b){long dx=(long)a.getX()-b.getX(),dy=(long)a.getY()-b.getY(),dz=(long)a.getZ()-b.getZ();
        return Math.abs(dx)<=LINK_RANGE&&Math.abs(dy)<=LINK_RANGE&&Math.abs(dz)<=LINK_RANGE&&dx*dx+dy*dy+dz*dz<=(long)LINK_RANGE*LINK_RANGE;}
    public static Network network(ServerLevel level,String owner){
        boolean tuned=ResonanceResearch.unlocked(owner,false);int limit=tuned?6:4;
        if(!level.getServer().isSameThread()||!DreamSpecialistResearch.ownerSupported(owner,false))return new Network(List.of(),0,limit,Map.of());
        var all=BuildingServerEvents.getBuildings().stream().filter(p->p.level==level&&owner.equals(p.ownerName)).toList();
        var nodes=all.stream().filter(ResonanceNetwork::live).sorted(ORDER).toList();
        List<BuildingPlacement> frontier=all.stream().filter(p->p instanceof DreamAnchorPlacement tree&&physical(p)&&tree.productionAvailable()&&tree.tree().supported).sorted(ORDER).toList();
        List<BuildingPlacement> active=new ArrayList<>();Map<BuildingPlacement,BuildingPlacement> parents=new IdentityHashMap<>();
        while(!frontier.isEmpty()&&active.size()<limit){
            var from=frontier;List<BuildingPlacement> next=nodes.stream().filter(p->!active.contains(p)
                &&from.stream().anyMatch(root->linked(root.originPos,p.originPos))).sorted(ORDER).toList();
            List<BuildingPlacement> accepted=new ArrayList<>();for(var p:next){if(active.size()>=limit)break;active.add(p);accepted.add(p);parents.put(p,from.stream().filter(root->linked(root.originPos,p.originPos)).findFirst().orElseThrow());}frontier=accepted;
        }
        return new Network(active,nodes.size(),limit,parents);
    }
    public static View view(BuildingPlacement p){if(!physical(p)||!isDais(p))return null;var o=observation(p);if(o==null)return null;
        var r=p.getDataStorage().getData(ResonanceState.TYPE);var net=network((ServerLevel)p.level,p.ownerName);long now=p.level.getGameTime();
        int cooldown=!r.supported()?0:(int)Math.min(Integer.MAX_VALUE,Math.max(0,r.readyAt()-Math.max(0,now)));
        return new View(o.identity==null?new UUID(0,0):o.identity,o.ownerRevision,o.capture,o.generation,net.rank(p),net.active().size(),net.total(),net.limit(),cooldown,
            live(p),ResonanceResearch.unlocked(p.ownerName,false));
    }
    private static final class LinkStamp {
        final BuildingPlacement building;final String owner;final long capture,generation;final Object state;final UUID identity;
        LinkStamp(BuildingPlacement p){building=p;owner=p.ownerName;capture=p.getCaptureRevision();
            if(isDais(p)){var o=observation(p);generation=o.generation;state=p.getDataStorage().getData(ResonanceState.TYPE);identity=o.identity;}
            else{var tree=((DreamAnchorPlacement)p).tree();generation=-1;state=tree;identity=tree.instance;}}
        boolean valid(){if(!physical(building)||!owner.equals(building.ownerName)||capture!=building.getCaptureRevision())return false;
            if(isDais(building)){if(!live(building))return false;var o=observation(building);return generation==o.generation&&identity.equals(o.identity)&&state==building.getDataStorage().getData(ResonanceState.TYPE);}
            return building instanceof DreamAnchorPlacement tree&&tree.productionAvailable()&&tree.tree()==state&&tree.tree().supported&&identity.equals(tree.tree().instance);
        }
    }
    private static final class Source {
        final BuildingPlacement host;final ServerLevel level;final String owner;final Resources account;final UUID accountEpoch,identity;
        final long capture,revision,generation;final ResonanceState state;final List<LinkStamp> path;boolean revoked;
        Source(BuildingPlacement p){host=p;level=(ServerLevel)p.level;owner=p.ownerName;account=ExtendedResourcesSync.account(owner);accountEpoch=account.accountEpoch;
            var o=observation(p);identity=o.identity;capture=o.capture;revision=o.ownerRevision;generation=o.generation;state=p.getDataStorage().getData(ResonanceState.TYPE);
            var graph=network(level,owner);List<LinkStamp> captured=new ArrayList<>();BuildingPlacement cursor=p;
            while(cursor!=null&&captured.size()<=6){captured.add(new LinkStamp(cursor));cursor=graph.parents().get(cursor);}path=List.copyOf(captured);}
        boolean valid(){if(revoked)return false;if(!check()){revoked=true;return false;}return true;}
        private boolean check(){if(!live(host)||!owner.equals(host.ownerName)||ExtendedResourcesSync.account(owner)!=account
                ||!accountEpoch.equals(account.accountEpoch)||!DreamSpecialistResearch.ownerSupported(owner,false))return false;
            var o=observation(host);return o.generation==generation&&o.capture==capture&&o.ownerRevision==revision&&identity.equals(o.identity)
                &&host.getDataStorage().getData(ResonanceState.TYPE)==state&&!path.isEmpty()&&path.get(path.size()-1).building instanceof DreamAnchorPlacement
                &&path.stream().allMatch(LinkStamp::valid)&&network(level,owner).rank(host)>0;}
    }
    public static int pulseRange(String owner,boolean client) {
        return ResonanceResearch.unlocked(owner,client) && com.dreamseekers.unitprogression.AdvancedUnitResearch.unlocked(owner,
                com.dreamseekers.unitprogression.AdvancedUnitResearch.PULSE_RANGE_ID,client)?14:PULSE_RANGE;
    }
    private static boolean inPulseRange(BuildingPlacement p,LivingEntity e,int range) {
        return e.level()==p.level && e.distanceToSqr(p.originPos.getX()+.5,p.originPos.getY()+.5,p.originPos.getZ()+.5)<=(double)range*range;
    }
    private static boolean eligible(ServerPlayer actor,BuildingPlacement p,LivingEntity e,int range){
        if(!(e instanceof Unit u)||!(e instanceof AttackerUnit)||!e.isAlive()||e.isRemoved()||e.level()!=p.level
            ||!p.ownerName.equals(u.getOwnerName())||!UnitServerEvents.getAllUnits().contains(e)
            ||((ServerLevel)p.level).getEntity(e.getUUID())!=e||!com.dreamseekers.summon.BoundedSummons.canAct(e)
            ||e.getAttribute(BuffKind.ATTACK_SPEED.attribute())==null||!CoordinatedMark.visible(actor,e))return false;
        return inPulseRange(p,e,range);
    }
    /** Only the server selects recipients and fixed parameters; wire payloads contain no effect or target list. */
    public static String pulse(ServerPlayer actor,BuildingPlacement p,UUID instance,long ownerRevision,long capture,long generation){
        if(!owns(actor,p)||!live(p))return "forbidden";var o=observation(p);
        if(!Objects.equals(instance,o.identity)||ownerRevision!=o.ownerRevision||capture!=o.capture||generation!=o.generation)return "stale_building";
        if(network(actor.serverLevel(),p.ownerName).rank(p)==0)return "disconnected";
        var state=p.getDataStorage().getData(ResonanceState.TYPE);long now=p.level.getGameTime();
        if(now<0||now>Long.MAX_VALUE-COOLDOWN)return "unavailable";
        if(state.readyAt()>now)return "cooldown";if(!CASTING.add(p))return "busy";
        try{
            Source source=new Source(p);
            final int range=pulseRange(p.ownerName,false); // immutable selection radius for this paid pulse; movement does not revoke an existing buff
            List<SkillIdentity.Stamp> targets=actor.serverLevel().getEntitiesOfClass(LivingEntity.class,new AABB(p.originPos).inflate(range+1),e->eligible(actor,p,e,range))
                .stream().filter(e->!SkillEffects.hasNamedBuff(e,EFFECT_KEY,BuffKind.ATTACK_SPEED))
                .sorted(Comparator.comparingDouble((LivingEntity e)->e.distanceToSqr(p.originPos.getX()+.5,p.originPos.getY()+.5,p.originPos.getZ()+.5)).thenComparing(LivingEntity::getUUID))
                .limit(MAX_TARGETS).map(SkillIdentity::capture).toList();
            if(targets.isEmpty())return "no_target";
            if(!source.valid()||targets.stream().noneMatch(t->t.valid()&&eligible(actor,p,t.entity(),range)))return "stale_building";
            boolean tuned=ResonanceResearch.unlocked(p.ownerName,false);int duration=tuned?160:100;double amount=tuned?.15:.10;
            var result=ResourceTransaction.exchange(actor.serverLevel().getServer(),source.account,source.accountEpoch,source.account.revision,
                new ResourceTransaction.Cost(0,0,0,Map.of(DreamPassiveEconomy.SHADOW,COST)),ResourceTransaction.Cost.ZERO);
            if(!result.applied())return result.status()==ResourceTransaction.Status.INSUFFICIENT_RESOURCES?"insufficient_resources":"unavailable";
            // Exchange has no external callback. Spend cooldown before any modifier publication/reentry.
            if(!state.spend(now))throw new IllegalStateException("Resonance cooldown changed during atomic debit");
            for(var target:targets)if(source.valid()&&target.valid()&&eligible(actor,p,target.entity(),range))
                SkillEffects.addGuardedBuildingBuff(target.entity(),EFFECT_KEY,BuffKind.ATTACK_SPEED,amount,duration,
                    ()->source.valid()&&target.valid()&&source.owner.equals(((Unit)target.entity()).getOwnerName()));
            return "success";
        }finally{CASTING.remove(p);}
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event){OBSERVATIONS.clear();CASTING.clear();}
}
