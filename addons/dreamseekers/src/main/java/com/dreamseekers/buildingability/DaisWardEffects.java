// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.buildingability;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.neutralcontract.NeutralContracts;
import com.dreamseekers.resonance.ResonanceNetwork;
import com.dreamseekers.resonance.ResonanceResearch;
import com.dreamseekers.skill.SkillIdentity;
import com.dreamseekers.summon.BoundedSummons;
import com.dreamseekers.worldtree.DreamAnchorPlacement;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Fixed 10% defense, composed by the existing SkillEffects final consumer (never a second handler). */
@Mod.EventBusSubscriber(modid=DreamseekersMod.MOD_ID)
public final class DaisWardEffects {
    public static final double REDUCTION=.10;
    private static final Map<LivingEntity,Lease> ACTIVE=new IdentityHashMap<>();
    private static final ArrayDeque<Frame> HITS=new ArrayDeque<>();
    private record Frame(LivingEntity target,DamageSource damage,HitPlan plan) { }
    private DaisWardEffects() { }
    public static boolean ready(BuildingAbilitySource source) {
        return source.valid()&&ResonanceNetwork.isDais(source.host())&&ResonanceNetwork.live(source.host())
                &&ResonanceResearch.unlocked(source.owner(),false)
                &&ResonanceNetwork.network(source.level(),source.owner()).rank(source.host())>0;
    }
    private static final class Path {
        final BuildingAbilitySource source;
        final List<BuildingAbilitySource> nodes;
        boolean revoked;
        Path(ServerPlayer actor,BuildingAbilitySource source) {
            this.source=source;List<BuildingAbilitySource> captured=new ArrayList<>();
            var network=ResonanceNetwork.network(source.level(),source.owner());
            BuildingPlacement cursor=source.host();
            while(cursor!=null&&captured.size()<7) {
                BuildingAbilitySource node=BuildingAbilitySource.capture(actor,cursor);
                if(node==null){captured.clear();break;}captured.add(node);cursor=network.parents().get(cursor);
            }
            nodes=List.copyOf(captured);
        }
        boolean valid() {
            if(revoked)return false;
            if(!ready(source)||nodes.isEmpty()||!(nodes.get(nodes.size()-1).host() instanceof DreamAnchorPlacement)
                    ||nodes.stream().anyMatch(node->!node.valid())){revoked=true;return false;}
            for(int i=1;i<nodes.size();i++)if(!ResonanceNetwork.linked(nodes.get(i-1).host(),nodes.get(i).host())){
                revoked=true;return false;
            }
            return true;
        }
    }
    private static boolean eligible(BuildingAbilitySource source,LivingEntity target) {
        return BuildingAbilityEffects.eligible(source,target,12)&&!active(target);
    }
    public static BuildingAbilityEffects.Prepared prepare(ServerPlayer actor,BuildingAbilitySource source) {
        if(!ready(source))return new BuildingAbilityEffects.Prepared("requires_dais_network_tuning",null);
        Path path=new Path(actor,source);if(!path.valid())return new BuildingAbilityEffects.Prepared("disconnected",null);
        List<SkillIdentity.Stamp> chosen=BuildingAbilityEffects.ordered(source).stream().filter(target->eligible(source,target))
                .limit(8).map(SkillIdentity::capture).toList();
        if(chosen.isEmpty())return new BuildingAbilityEffects.Prepared("no_target",null);
        return new BuildingAbilityEffects.Prepared("ready",new BuildingAbilityEffects.Plan() {
            @Override public boolean valid(){return path.valid()&&chosen.stream().anyMatch(t->t.valid()&&eligible(source,t.entity()));}
            @Override public int targets(){return chosen.size();}
            @Override public void apply(){if(!path.valid())return;for(var target:chosen)
                if(target.valid()&&eligible(source,target.entity()))ACTIVE.put(target.entity(),new Lease(path,target));}
        });
    }
    private static final class Lease {
        final Path path;
        final SkillIdentity.Stamp target;
        final long expires;
        boolean revoked;
        Lease(Path path,SkillIdentity.Stamp target){this.path=path;this.target=target;expires=path.source.level().getGameTime()+120;}
        boolean valid(){if(revoked)return false;
            if(ACTIVE.get(target.entity())!=this||path.source.level().getGameTime()>=expires||!path.valid()||!target.valid()
                    ||!BuildingAbilityEffects.eligible(path.source,target.entity(),12))revoked=true;
            return !revoked;
        }
    }
    public static boolean active(LivingEntity target){Lease lease=ACTIVE.get(target);return lease!=null&&lease.valid();}
    private static boolean excluded(DamageSource damage) {
        return damage.is(DamageTypeTags.BYPASSES_INVULNERABILITY)||damage.is(DamageTypeTags.BYPASSES_EFFECTS)
                ||damage.is(DamageTypeTags.BYPASSES_RESISTANCE)||damage.is(DamageTypes.GENERIC_KILL)
                ||damage.is(DamageTypes.FELL_OUT_OF_WORLD);
    }
    private static boolean hostile(LivingEntity actor,LivingEntity target) {
        if(!(actor instanceof Unit attacking)||!(target instanceof Unit defending)||actor==target
                ||actor.level()!=target.level()||!actor.isAlive()||actor.isRemoved()||!actor.isAddedToWorld()
                ||!(target.level() instanceof ServerLevel level)||level.getEntity(actor.getUUID())!=actor
                ||level.getEntity(actor.getId())!=actor||!level.hasChunkAt(actor.blockPosition())
                ||!UnitServerEvents.getAllUnits().contains(actor)||NeutralContracts.pending(actor)||!BoundedSummons.canAct(actor))return false;
        String a=attacking.getOwnerName(),b=defending.getOwnerName();
        return !a.isBlank()&&!b.isBlank()&&!a.equals(b)&&!AlliancesServerEvents.isAllied(a,b);
    }
    /** Captured before the actual damage calculation, not found again after Forge callbacks. */
    public static final class HitPlan {
        private final Lease ward;
        private final SkillIdentity.Stamp actor,target;
        private final DamageSource damage;
        private double nativeReduction;
        private HitPlan(Lease ward,LivingEntity actor,LivingEntity target,DamageSource damage) {
            this.ward=ward;this.actor=SkillIdentity.capture(actor);this.target=SkillIdentity.capture(target);this.damage=damage;
        }
        public boolean valid(DamageSource source){return source==damage&&!excluded(source)&&ward.valid()&&actor.valid()&&target.valid()
                &&source.getEntity()==actor.entity()&&hostile(actor.entity(),target.entity());}
        public void nativeResistanceUsed(double reduction){if(Double.isFinite(reduction)&&reduction>=0&&reduction<=1)
            nativeReduction=Math.max(nativeReduction,reduction);}
        public double reduction(DamageSource source){return valid(source)?REDUCTION:0;}
        public double nativeReduction(DamageSource source){return valid(source)?nativeReduction:0;}
    }
    /** Scope is the real actuallyHurt invocation. A finally owns cleanup even on cancellation/throw;
     * nested hits (including the same DS object) have distinct exact plans and resistance receipts.
     */
    public static void hurt(LivingEntity victim,DamageSource damage,Runnable original) {
        if(!(victim.level() instanceof ServerLevel level)||!level.getServer().isSameThread()){original.run();return;}
        Lease ward=ACTIVE.get(victim);HitPlan plan=null;
        if(ward!=null&&ward.valid()&&!excluded(damage)&&damage.getEntity() instanceof LivingEntity actor&&hostile(actor,victim))
            plan=new HitPlan(ward,actor,victim,damage);
        // Even an unprotected nested hit needs a frame so it cannot borrow an outer matching DS plan.
        Frame frame=new Frame(victim,damage,plan);HITS.push(frame);
        try {original.run();} finally {HITS.removeFirstOccurrence(frame);}
    }
    public static HitPlan matching(LivingEntity victim,DamageSource source) {
        Frame frame=HITS.peek();return frame!=null&&frame.target()==victim&&frame.damage()==source?frame.plan():null;
    }
    public static int remaining(BuildingPlacement host){long until=host.level.getGameTime();
        for(Lease lease:ACTIVE.values())if(lease.path.source.host()==host&&lease.valid())until=Math.max(until,lease.expires);
        return (int)Math.min(120,Math.max(0,until-host.level.getGameTime()));}
    public static void clear(LivingEntity entity){ACTIVE.remove(entity);}
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){if(event.phase==TickEvent.Phase.END&&event.level instanceof ServerLevel level)
        ACTIVE.entrySet().removeIf(entry->entry.getKey().level()==level&&!entry.getValue().valid());}
    public static void stop(){ACTIVE.clear();HITS.clear();}
}
