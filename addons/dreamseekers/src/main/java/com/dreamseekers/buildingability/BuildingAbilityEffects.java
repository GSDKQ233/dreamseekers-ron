// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.buildingability;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.ecologyworkposts.EcologyWorkPosts;
import com.dreamseekers.neutralcontract.ContractLedger;
import com.dreamseekers.neutralcontract.NeutralContracts;
import com.dreamseekers.shadow.TerrorbeakUpgradeService;
import com.dreamseekers.skill.BuffKind;
import com.dreamseekers.skill.SkillEffects;
import com.dreamseekers.skill.SkillIdentity;
import com.dreamseekers.summon.BoundedSummons;
import com.dreamseekers.worldtree.DreamAnchorPlacement;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.kill.RtsKillValueClaims;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Fixed server plans. Effects are transient; only the service's paid cooldown is persisted.
 * No public constructor can mint a buff lease, and no packet supplies amount/category/targets.
 */
@Mod.EventBusSubscriber(modid=DreamseekersMod.MOD_ID)
public final class BuildingAbilityEffects {
    public static final String ROOT_KEY="building_root_crown",HALL_KEY="building_hall_preparation";
    public static final int REST_TICKS=200,HEAL_INTERVAL=20,HEAL_PULSES=6;
    private static final Map<LivingEntity,EnumMap<BuildingAbility,BuffLease>> BUFFS=new IdentityHashMap<>();
    private static final Map<LivingEntity,GardenLease> GARDENS=new IdentityHashMap<>();
    private static final Map<LivingEntity,Long> JOINED=new WeakHashMap<>(),COMBAT=new WeakHashMap<>();
    private static final Set<LivingEntity> REFRESHING=Collections.newSetFromMap(new IdentityHashMap<>());
    private BuildingAbilityEffects() { }
    public interface Plan { boolean valid(); void apply(); int targets(); }
    public record Prepared(String status,Plan plan) { }

    /** Publication is exact, not merely a matching entity type or owner string. Workers are allowed.
     * Economic-free provenance is never changed. A committed neutral contract is permanent, unlike
     * pending contract bodies or temporary summons. Unknown free metadata is deliberately inert.
     */
    public static boolean permanentOwnUnit(BuildingAbilitySource source,LivingEntity entity) {
        return source!=null&&permanentUnit(source.level(),source.owner(),entity);
    }
    static boolean permanentUnit(ServerLevel level,String owner,LivingEntity entity) {
        if(level==null||owner==null||owner.isBlank()||entity==null||!(entity instanceof Mob mob)||!(entity instanceof Unit unit)
                ||entity instanceof HeroUnit||entity.level()!=level||!entity.isAlive()||entity.isRemoved()
                ||!entity.isAddedToWorld()||entity.isSpectator()||!owner.equals(unit.getOwnerName())
                ||level.getEntity(entity.getUUID())!=entity||level.getEntity(entity.getId())!=entity
                ||!level.hasChunkAt(entity.blockPosition())||UnitServerEvents.getAllUnits().stream().filter(e->e==entity).count()!=1
                ||BoundedSummons.isTemporary(entity)||!BoundedSummons.canAct(entity)||NeutralContracts.pending(entity)
                ||TerrorbeakUpgradeService.pending(entity)||entity.hasEffect(MobEffectRegistrar.LIMITED_LIFESPAN.get())
                ||entity instanceof com.solegendary.reignofnether.unit.units.monsters.PhantomSummon
                ||entity instanceof com.solegendary.reignofnether.unit.units.monsters.SilverfishUnit
                ||(entity instanceof com.solegendary.reignofnether.unit.units.monsters.ZombieUnit zombie&&zombie.isSummoned())
                ||(entity instanceof com.solegendary.reignofnether.unit.units.neutral.BeeUnit bee&&bee.isSummoned())
                ||RtsKillValueClaims.isClaimed(entity))return false;
        @SuppressWarnings("unchecked") EntityType<? extends Mob> type=(EntityType<? extends Mob>)mob.getType();
        boolean committedAdvanced=TerrorbeakUpgradeService.isCommittedAdvanced(entity);
        if(ProductionItems.getProductionItem(type)==null&&!committedAdvanced)return false;
        if(!RtsKillValueClaims.isFree(entity))return !entity.getPersistentData().contains(NeutralContracts.ORIGIN);
        if(!entity.getPersistentData().contains(RtsKillValueClaims.FREE_ORIGIN_KEY,Tag.TAG_COMPOUND))return false;
        CompoundTag free=entity.getPersistentData().getCompound(RtsKillValueClaims.FREE_ORIGIN_KEY);
        if(!free.contains("schema",Tag.TAG_INT)||free.getInt("schema")!=1||!free.contains("origin",Tag.TAG_STRING))return false;
        // Native permanent infection remains its original economic-free origin, never a summoned body.
        if("NATIVE_INFECTION".equals(free.getString("origin")))return !entity.getPersistentData().contains(NeutralContracts.ORIGIN);
        return com.dreamseekers.neutralcontract.CommittedContractUnits.isCommittedPermanent(entity,owner);
    }
    private static boolean rootReady(BuildingAbilitySource source) {
        return source.host() instanceof DreamAnchorPlacement anchor&&anchor.productionAvailable()
                &&anchor.hasStandingUpgrade();
    }
    static boolean hallReady(BuildingAbilitySource source) {
        if(source.host().getBuilding()!=DreamseekersMod.DREAM_HALL)return false;
        return BuildingServerEvents.getBuildings().stream().anyMatch(p->p.level==source.level()&&source.owner().equals(p.ownerName)
                &&p.getBuilding()==DreamseekersMod.RESONANCE_LABORATORY&&p.isBuilt&&!p.isDestroyedServerside
                &&p.level.hasChunksAt(p.minCorner,p.maxCorner)&&p.getHealth()>0&&!p.shouldBeDestroyed()
                &&p instanceof com.dreamseekers.building.DreamLaboratoryPlacement lab&&lab.available());
    }
    private static boolean hallTarget(BuildingAbilitySource source,LivingEntity target) {
        if(!(target instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit attacker)
                ||target instanceof com.solegendary.reignofnether.unit.interfaces.WorkerUnit||!eligible(source,target,12)
                ||!namedFree(target,BuildingAbility.HALL_PREPARATION)||SkillEffects.hasNamedBuff(target,HALL_KEY,BuffKind.ATTACK_DAMAGE))return false;
        var attribute=target.getAttribute(BuffKind.ATTACK_DAMAGE.attribute());
        if(attribute==null||!Double.isFinite(attribute.getBaseValue())||attribute.getBaseValue()<=0
                ||!Float.isFinite(attacker.getBaseUnitAttackDamage())||attacker.getBaseUnitAttackDamage()<=0||attacker.getAttackGoal()==null)return false;
        @SuppressWarnings("unchecked") var type=(EntityType<? extends Mob>)target.getType();
        return DreamseekersMod.DREAM_HALL.productions.get().contains(ProductionItems.getProductionItem(type))
                ||target.getClass()==com.dreamseekers.unit.CrazyTerrorbeakUnit.class&&target.getType()==DreamseekersMod.CRAZY_TERRORBEAK_UNIT.get()
                    &&!TerrorbeakUpgradeService.pending(target)&&DreamseekersMod.DREAM_HALL.productions.get().contains(DreamseekersMod.TERRORBEAK_PRODUCTION);
    }
    private static boolean buffTarget(BuildingAbilitySource source,LivingEntity target,BuildingAbility ability) {
        return ability==BuildingAbility.ROOT_CROWN?rootTarget(source,target):ability==BuildingAbility.HALL_PREPARATION&&hallTarget(source,target);
    }
    private static boolean buffReady(BuildingAbilitySource source,BuildingAbility ability) {
        return ability==BuildingAbility.ROOT_CROWN?rootReady(source):ability==BuildingAbility.HALL_PREPARATION&&hallReady(source);
    }
    /** Distances are to the registered building footprint (origin layer vertically), not a client-chosen point. */
    static boolean nearby(BuildingAbilitySource source,LivingEntity target,int radius) {
        return com.dreamseekers.building.DreamFootprintRange.pointWithin(source.host(),target.getX(),target.getY(),target.getZ(),radius);
    }
    /** Sphere around the origin block; the watchtower mark keeps it because its client mirror checks the same sphere. */
    static boolean nearbyOrigin(BuildingAbilitySource source,LivingEntity target,int radius) {
        double x=source.host().originPos.getX()+.5,y=source.host().originPos.getY()+.5,z=source.host().originPos.getZ()+.5;
        double dx=target.getX()-x,dy=target.getY()-y,dz=target.getZ()-z;
        return Math.abs(dx)<=radius&&Math.abs(dy)<=radius&&Math.abs(dz)<=radius&&dx*dx+dy*dy+dz*dz<=(double)radius*radius;
    }
    /** A point just outside the footprint on the target-facing side avoids tracing through the
     * source building's own solid decorative walls. Its offset is fixed by server geometry.
     */
    static Vec3 observationPoint(BuildingPlacement host,LivingEntity target) {
        double cx=(host.minCorner.getX()+host.maxCorner.getX()+1)/2d;
        double cz=(host.minCorner.getZ()+host.maxCorner.getZ()+1)/2d;
        double dx=target.getX()-cx,dz=target.getZ()-cz;
        double hx=(host.maxCorner.getX()-host.minCorner.getX()+1)/2d+.05;
        double hz=(host.maxCorner.getZ()-host.minCorner.getZ()+1)/2d+.05;
        double scale=Math.max(Math.abs(dx)/hx,Math.abs(dz)/hz);
        if(scale>1){dx/=scale;dz/=scale;}
        return new Vec3(cx+dx,Math.max(host.minCorner.getY()+.5,Math.min(target.getEyeY(),host.maxCorner.getY()+1.05)),cz+dz);
    }
    private static boolean visibleFrom(BuildingAbilitySource source,LivingEntity target) {
        return LoadedBuildingSight.clear(source.level(),observationPoint(source.host(),target),target.getEyePosition(),target);
    }
    static boolean eligible(BuildingAbilitySource source,LivingEntity target,int radius) {
        return permanentOwnUnit(source,target)&&nearby(source,target,radius)&&visibleFrom(source,target);
    }
    private static boolean namedFree(LivingEntity target,BuildingAbility ability) {
        var entries=BUFFS.get(target);BuffLease old=entries==null?null:entries.get(ability);
        return old==null||!old.valid();
    }
    private static boolean rootTarget(BuildingAbilitySource source,LivingEntity target) {
        var move=target.getAttribute(Attributes.MOVEMENT_SPEED);
        return eligible(source,target,12)&&move!=null&&Double.isFinite(move.getBaseValue())&&move.getBaseValue()>0
                &&namedFree(target,BuildingAbility.ROOT_CROWN)&&!SkillEffects.hasNamedBuff(target,ROOT_KEY,BuffKind.MOVEMENT_SPEED);
    }
    private static boolean combatOrder(LivingEntity entity) {
        return entity instanceof Unit unit&&(unit.hasLivingTarget()
                ||entity instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit attacker
                    &&com.solegendary.reignofnether.unit.interfaces.AttackerUnit.isAttackingBuilding(attacker));
    }
    private static boolean resting(LivingEntity entity,long now) {
        Long joined=JOINED.get(entity);
        if(joined==null){JOINED.put(entity,now);return false;}
        long last=Math.max(joined,COMBAT.getOrDefault(entity,joined));
        return now>=last&&now-last>=REST_TICKS&&entity instanceof Unit&&!combatOrder(entity)&&entity.hurtTime==0;
    }
    private static boolean caredFor(LivingEntity target) {
        for(BuildingPlacement court:BuildingServerEvents.getBuildings())
            if(court.level==target.level()&&EcologyWorkPosts.isCourt(court))
                for(var row:EcologyWorkPosts.snapshot(court))if(row.uuid().equals(target.getUUID()))return true;
        return false;
    }
    private static boolean gardenTarget(BuildingAbilitySource source,LivingEntity target) {
        GardenLease current=GARDENS.get(target);
        return eligible(source,target,8)&&Float.isFinite(target.getHealth())&&Float.isFinite(target.getMaxHealth())
                &&target.getMaxHealth()-target.getHealth()>0&&resting(target,source.level().getGameTime())
                &&(current==null||!current.valid())&&!SkillEffects.hasHeal(target)&&!caredFor(target);
    }
    static List<LivingEntity> ordered(BuildingAbilitySource source) {
        return UnitServerEvents.getAllUnits().stream().filter(e->e.level()==source.level())
                .sorted(Comparator.comparingDouble((LivingEntity e)->e.distanceToSqr(source.host().originPos.getX()+.5,
                        source.host().originPos.getY()+.5,source.host().originPos.getZ()+.5)).thenComparing(e->e.getUUID().toString())).toList();
    }
    public static Prepared prepare(ServerPlayer actor,BuildingAbilitySource source,BuildingAbility ability) {
        if(actor==null||source==null||!source.valid()||actor.serverLevel()!=source.level()
                ||!actor.getGameProfile().getName().equals(source.owner()))return new Prepared("forbidden",null);
        long now=source.level().getGameTime();
        if(now<0||now>Long.MAX_VALUE-1200)return new Prepared("unavailable",null);
        if(ability==BuildingAbility.ROOT_CROWN) {
            if(!rootReady(source))return new Prepared("prerequisite",null);
            var selected=ordered(source).stream().filter(e->rootTarget(source,e)).limit(10).map(SkillIdentity::capture).toList();
            return selected.isEmpty()?new Prepared("no_target",null):new Prepared("ready",new BuffPlan(source,BuildingAbility.ROOT_CROWN,selected));
        }
        if(ability==BuildingAbility.HALL_PREPARATION) {
            if(!hallReady(source))return new Prepared("requires_laboratory",null);
            var selected=ordered(source).stream().filter(e->hallTarget(source,e)).limit(8).map(SkillIdentity::capture).toList();
            return selected.isEmpty()?new Prepared("no_target",null):new Prepared("ready",new BuffPlan(source,BuildingAbility.HALL_PREPARATION,selected));
        }
        if(ability==BuildingAbility.DAIS_WARD)return DaisWardEffects.prepare(actor,source);
        if(ability==BuildingAbility.WATCHTOWER_MARK)return TowerObservation.prepare(actor,source);
        if(ability==BuildingAbility.GARDEN_RECOVERY) {
            var selected=ordered(source).stream().filter(e->gardenTarget(source,e)).limit(6).map(SkillIdentity::capture).toList();
            return selected.isEmpty()?new Prepared("no_target",null):new Prepared("ready",new GardenPlan(source,selected));
        }
        return new Prepared("unavailable",null); // Informational codex is not a paid effect cast.
    }
    private record BuffPlan(BuildingAbilitySource source,BuildingAbility ability,List<SkillIdentity.Stamp> chosen) implements Plan {
        @Override public boolean valid(){return source.valid()&&buffReady(source,ability)&&chosen.stream().anyMatch(t->t.valid()&&buffTarget(source,t.entity(),ability));}
        @Override public int targets(){return chosen.size();}
        @Override public void apply() {
            if(!source.valid()||!buffReady(source,ability))return;
            for(var target:chosen)if(target.valid()&&buffTarget(source,target.entity(),ability)) {
                BuffLease lease=new BuffLease(source,target,ability);
                BUFFS.computeIfAbsent(target.entity(),key->new EnumMap<>(BuildingAbility.class)).put(ability,lease);
                if(!SkillEffects.addBuildingAbilityBuff(lease)) {
                    var active=BUFFS.get(target.entity());if(active!=null)active.remove(ability,lease);
                }
            }
        }
    }
    /** Constructor is private: callers can only receive a lease selected and paid by the service. */
    public static final class BuffLease {
        private final BuildingAbilitySource source;
        private final SkillIdentity.Stamp target;
        private final BuildingAbility ability;
        private final long expires;
        private boolean revoked;
        private BuffLease(BuildingAbilitySource source,SkillIdentity.Stamp target,BuildingAbility ability) {
            this.source=source;this.target=target;this.ability=ability;expires=source.level().getGameTime()+(ability==BuildingAbility.ROOT_CROWN?160:200);
        }
        public LivingEntity target(){return target.entity();}
        public BuffKind kind(){return ability==BuildingAbility.ROOT_CROWN?BuffKind.MOVEMENT_SPEED:BuffKind.ATTACK_DAMAGE;}
        public String key(){return ability==BuildingAbility.ROOT_CROWN?ROOT_KEY:HALL_KEY;}
        public double amount(){return ability==BuildingAbility.ROOT_CROWN?.15:.10;}
        public long expiresAt(){return expires;}
        public boolean valid() {
            if(revoked)return false;
            var active=BUFFS.get(target.entity());
            if(active==null||active.get(ability)!=this||source.level().getGameTime()>=expires||!source.valid()
                    ||!buffReady(source,ability)||!target.valid()||!eligible(source,target.entity(),12))revoked=true;
            return !revoked;
        }
    }
    private record GardenPlan(BuildingAbilitySource source,List<SkillIdentity.Stamp> chosen) implements Plan {
        @Override public boolean valid(){return source.valid()&&chosen.stream().anyMatch(t->t.valid()&&gardenTarget(source,t.entity()));}
        @Override public int targets(){return chosen.size();}
        @Override public void apply(){if(!source.valid())return;for(var target:chosen)
            if(target.valid()&&gardenTarget(source,target.entity()))GARDENS.put(target.entity(),new GardenLease(source,target));}
    }
    private static final class GardenLease {
        final BuildingAbilitySource source;
        final SkillIdentity.Stamp target;
        final long expires;
        long next;
        int pulses;
        boolean revoked,processing;
        GardenLease(BuildingAbilitySource source,SkillIdentity.Stamp target) {
            this.source=source;this.target=target;long now=source.level().getGameTime();expires=now+120;next=now+20;
        }
        boolean valid() {
            if(revoked)return false;
            if(GARDENS.get(target.entity())!=this||source.level().getGameTime()>expires||!source.valid()||!target.valid()
                    ||!eligible(source,target.entity(),8)||!resting(target.entity(),source.level().getGameTime())
                    ||SkillEffects.hasHeal(target.entity())||caredFor(target.entity()))revoked=true;
            return !revoked;
        }
        void tick(long now) {
            if(!valid()){GARDENS.remove(target.entity(),this);return;}
            if(processing||now<next||pulses>=HEAL_PULSES)return;
            processing=true;
            try {
                // Consume before posting hooks. Lag cannot create catch-up bursts or a seventh heal.
                pulses++;next=now+HEAL_INTERVAL;
                LivingEntity entity=target.entity();
                float requested=Math.min(1f,Math.max(0,entity.getMaxHealth()-entity.getHealth()));
                if(requested>0) {
                    float accepted=ForgeEventFactory.onLivingHeal(entity,requested);
                    if(valid()&&Float.isFinite(accepted)&&accepted>0)
                        entity.setHealth(Math.min(entity.getMaxHealth(),entity.getHealth()+Math.min(requested,accepted)));
                }
                if(pulses>=HEAL_PULSES||now>=expires)GARDENS.remove(entity,this);
            } finally {processing=false;}
        }
    }
    /** Advancement must not wash away a paid incoming building effect. */
    public static boolean hasEffects(LivingEntity target) {
        var buffs=BUFFS.get(target);
        if(buffs!=null&&buffs.values().stream().anyMatch(BuffLease::valid))return true;
        GardenLease garden=GARDENS.get(target);return (garden!=null&&garden.valid())||DaisWardEffects.active(target)||TowerObservation.marked(target);
    }
    public static int remaining(BuildingPlacement host) {
        long now=host.level.getGameTime(),until=now;
        for(var map:BUFFS.values())for(var lease:map.values())if(lease.source.host()==host&&lease.valid())until=Math.max(until,lease.expires);
        for(var lease:GARDENS.values())if(lease.source.host()==host&&lease.valid())until=Math.max(until,lease.expires);
        return Math.max(TowerObservation.remaining(host),Math.max(DaisWardEffects.remaining(host),(int)Math.min(1200,Math.max(0,until-now))));
    }
    /** No spatial reads or callbacks: safe inside ownership/death/ChunkHolder leave cleanup. */
    public static void clear(LivingEntity target){BUFFS.remove(target);GARDENS.remove(target);DaisWardEffects.clear(target);TowerObservation.clear(target);}
    public static void refreshAttribute(LivingEntity target,Attribute attribute) {
        if(target.level().isClientSide()||(!BUFFS.containsKey(target)&&!SkillEffects.hasBuildingAbilityBuffRecord(target))||REFRESHING.contains(target)
                ||(attribute!=Attributes.MOVEMENT_SPEED&&attribute!=Attributes.FLYING_SPEED
                    &&attribute!=BuffKind.ATTACK_DAMAGE.attribute()&&attribute!=Attributes.ATTACK_DAMAGE))return;
        REFRESHING.add(target);
        try {SkillEffects.refreshBuildingAbilityStats(target);}finally {REFRESHING.remove(target);}
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void joined(EntityJoinLevelEvent event) {
        if(!event.getLevel().isClientSide()&&event.getEntity() instanceof LivingEntity entity) {
            JOINED.put(entity,event.getLevel().getGameTime());COMBAT.remove(entity);clear(entity);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damage(LivingDamageEvent event) {
        if(event.getEntity().level().isClientSide()||event.isCanceled()||!Float.isFinite(event.getAmount())||event.getAmount()<=0)return;
        long now=event.getEntity().level().getGameTime();COMBAT.put(event.getEntity(),now);
        if(event.getSource().getEntity() instanceof LivingEntity actor&&actor.level()==event.getEntity().level())COMBAT.put(actor,now);
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level))return;
        // Sustained combat orders (including building attacks) restart the real out-of-combat window.
        for(var entity:List.copyOf(UnitServerEvents.getAllUnits()))if(entity.level()==level&&entity.isAlive()&&combatOrder(entity))
            COMBAT.put(entity,level.getGameTime());
        for(var target:List.copyOf(BUFFS.keySet()))if(target.level()==level) {
            var buffs=BUFFS.get(target);if(buffs==null)continue;
            buffs.values().removeIf(lease->!lease.valid());
            // Refresh even after the final lease retires, removing the previous single modifier.
            SkillEffects.refreshBuildingAbilityStats(target);
            if(buffs.isEmpty())BUFFS.remove(target,buffs);
        }
        for(GardenLease lease:List.copyOf(GARDENS.values()))if(lease.source.level()==level)lease.tick(level.getGameTime());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){stop();}
    public static void stop(){BUFFS.clear();GARDENS.clear();JOINED.clear();COMBAT.clear();REFRESHING.clear();DaisWardEffects.stop();TowerObservation.stop();}
}
