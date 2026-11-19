// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.spore.SporeEffects;
import com.dreamseekers.unit.TerrorbeakUnit;
import com.dreamseekers.unit.CrazyTerrorbeakUnit;
import com.dreamseekers.unit.TeamSafeCombatUnit;
import com.dreamseekers.unit.TeamSafeMeleeAttackGoal;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Source/target-owned temporary ordinary-attack interdiction, not native DISARM or a skill lock. */
public final class TerrorbeakDreadRoar {
    public static final int RANGE=3,MAX_TARGETS=4,DISARM_TICKS=30,RECONTROL_TICKS=100;
    public static final double LOW_HEALTH_RATIO=.35,PREDATORY_BONUS=.15;
    public static final ResourceLocation EFFECT_ID=ResourceLocation.fromNamespaceAndPath("dreamseekers","terrorbeak_dread_roar");
    private static final Map<LivingEntity,Disarm> DISARMS=new IdentityHashMap<>();
    private static final Map<LivingEntity,Protection> PROTECTIONS=new IdentityHashMap<>();
    private static final Map<TeamSafeCombatUnit,Object> BUSY=new IdentityHashMap<>();
    private TerrorbeakDreadRoar() { }
    private static boolean approved(TeamSafeCombatUnit source) { return (source instanceof TerrorbeakUnit || source instanceof CrazyTerrorbeakUnit)
        && !com.dreamseekers.shadow.TerrorbeakUpgradeService.pending(source.mob())
        && (!(source instanceof TerrorbeakUnit base)||!com.dreamseekers.shadow.TerrorbeakUpgradeService.busy(base)); }
    private static boolean server(LivingEntity body) {
        return body.level() instanceof ServerLevel level&&level.getServer().isSameThread();
    }
    private static boolean loaded(SkillIdentity.Stamp stamp) {
        return stamp!=null&&stamp.valid()&&stamp.entity().getUUID().equals(stamp.uuid())&&stamp.entity().isAddedToWorld()
                &&stamp.level().getEntity(stamp.entity().getId())==stamp.entity();
    }
    private static boolean body(LivingEntity entity) {
        return server(entity)&&entity.isAlive()&&!entity.isRemoved()&&entity.isAddedToWorld()
                &&entity.level().getEntity(entity.getId())==entity
                &&((ServerLevel)entity.level()).getEntity(entity.getUUID())==entity;
    }
    private static boolean disabled(TeamSafeCombatUnit source) {
        return source.mob().isPassenger()||source.mob().hasEffect(MobEffectRegistrar.STUN.get())
                ||source.mob().hasEffect(MobEffectRegistrar.FREEZE.get())||source.mob().hasEffect(MobEffectRegistrar.UNCONTROLLABLE.get());
    }
    private static boolean enemy(TeamSafeCombatUnit source,LivingEntity target) {
        return target!=source&&body(target)&&!target.isSpectator()&&source.mob().level()==target.level()
                &&!(target instanceof com.dreamseekers.spore.JungleSporeCarrier)
                &&SkillService.blessingCombatUnit(target)&&CoordinatedMark.enemy(source.mob(),target);
    }
    /** Deliberately independent of either owner epoch or the removable short disarm's source. */
    private record Protection(LivingEntity target,ServerLevel level,UUID uuid,long until) {
        boolean current() {
            return body(target)&&target.level()==level&&target.getUUID().equals(uuid)
                    &&level.getEntity(uuid)==target&&level.getGameTime()<until;
        }
    }
    private static final class Disarm implements SporeEffects.ManagedNegative {
        final SkillIdentity.Stamp caster,target;final long expiresAt;
        Disarm(SkillIdentity.Stamp caster,SkillIdentity.Stamp target,int ticks) {
            this.caster=caster;this.target=target;expiresAt=target.level().getGameTime()+ticks;
        }
        @Override public ResourceLocation id() { return EFFECT_ID; }
        @Override public boolean current() {
            return DISARMS.get(target.entity())==this&&loaded(caster)&&loaded(target)
                    &&target.level().getGameTime()<expiresAt&&CoordinatedMark.enemy(caster.entity(),target.entity());
        }
        @Override public boolean remove() { return current()&&DISARMS.remove(target.entity(),this); }
    }
    public static boolean hasDisarm(LivingEntity target) { return remainingDisarmTicks(target)>0; }
    public static int remainingDisarmTicks(LivingEntity target) {
        if(!server(target))return 0;
        Disarm effect=DISARMS.get(target);return effect!=null&&effect.current()?(int)(effect.expiresAt-target.level().getGameTime()):0;
    }
    public static int remainingRecontrolTicks(LivingEntity target) {
        if(!server(target))return 0;
        Protection protection=PROTECTIONS.get(target);return protection!=null&&protection.current()?(int)(protection.until-target.level().getGameTime()):0;
    }
    public static SporeEffects.ManagedNegative negative(LivingEntity target) {
        if(!server(target))return null;
        Disarm effect=DISARMS.get(target);return effect!=null&&effect.current()?effect:null;
    }
    private static boolean eligible(ServerPlayer viewer,TeamSafeCombatUnit source,LivingEntity target) {
        return enemy(source,target)&&source.mob().distanceToSqr(target)<=RANGE*RANGE&&source.mob().hasLineOfSight(target)
                &&CoordinatedMark.visible(viewer,target)&&!hasDisarm(target)&&remainingRecontrolTicks(target)==0;
    }
    private static boolean casterValid(ServerPlayer viewer,TeamSafeCombatUnit source,SkillIdentity.Stamp caster,long command) {
        return loaded(caster)&&!disabled(source)&&viewer.isAlive()&&!viewer.isRemoved()&&viewer.serverLevel()==caster.level()
                &&viewer.getGameProfile().getName().equals(caster.owner())&&source.rts().commandRevision==command
                &&!UnitServerEvents.hasPendingImmediateAction(source.mob().getId());
    }
    /** No damage/sound/event dispatch before the transaction commit. At least one exact install is paid. */
    static SkillResult cast(ServerPlayer viewer,TeamSafeCombatUnit source,BooleanSupplier commit) {
        if(viewer==null||source==null||!approved(source)||commit==null||!body(source.mob())||source.getOwnerName().isBlank())return SkillResult.INVALID_SOURCE;
        if(disabled(source))return SkillResult.DISABLED;
        if(BUSY.containsKey(source))return SkillResult.ALREADY_ACTIVE;
        Object reservation=new Object();BUSY.put(source,reservation);
        List<Disarm> installed=new ArrayList<>();Map<LivingEntity,Protection> protections=new IdentityHashMap<>();
        boolean committed=false;
        try {
            SkillIdentity.Stamp caster=SkillIdentity.capture(source.mob());long command=source.rts().commandRevision;
            if(!casterValid(viewer,source,caster,command))return SkillResult.INVALID_SOURCE;
            List<LivingEntity> candidates=caster.level().getEntitiesOfClass(LivingEntity.class,source.mob().getBoundingBox().inflate(RANGE),
                    target->eligible(viewer,source,target));
            candidates.sort(Comparator.comparingDouble((LivingEntity target)->source.mob().distanceToSqr(target)).thenComparing(target->target.getUUID().toString()));
            for(LivingEntity target:candidates) {
                if(!casterValid(viewer,source,caster,command))return SkillResult.INVALID_SOURCE;
                if(!eligible(viewer,source,target))continue;
                SkillIdentity.Stamp stamp=SkillIdentity.capture(target);
                int duration=SporeEffects.incomingManagedDuration(target,DISARM_TICKS);
                if(duration<=0||!loaded(stamp)||!casterValid(viewer,source,caster,command)||!eligible(viewer,source,target))continue;
                Disarm effect=new Disarm(caster,stamp,duration);
                Protection protection=new Protection(target,caster.level(),stamp.uuid(),caster.level().getGameTime()+RECONTROL_TICKS);
                DISARMS.put(target,effect);PROTECTIONS.put(target,protection);installed.add(effect);protections.put(target,protection);
                if(installed.size()>=MAX_TARGETS)break;
            }
            if(installed.isEmpty())return SkillResult.NO_TARGET;
            if(!casterValid(viewer,source,caster,command)||!commit.getAsBoolean())return SkillResult.REPLAY;
            committed=true;return SkillResult.SUCCESS;
        } finally {
            if(!committed) {
                for(Disarm effect:installed)DISARMS.remove(effect.target.entity(),effect);
                protections.forEach((target,protection)->PROTECTIONS.remove(target,protection));
            }
            BUSY.remove(source,reservation);
        }
    }
    private static boolean outsideMeleeReach(TeamSafeCombatUnit source,LivingEntity target) {
        float width=source.mob().getBbWidth()+source.getBonusMeleeRange();
        float targetWidth=target.getBbWidth()+(target instanceof Unit unit?unit.getBonusMeleeRangeForAttackers():0);
        double reach=width*2f*width*2f+targetWidth;
        return Double.isFinite(reach)&&reach>=0&&source.mob().distanceToSqr(target)>reach;
    }
    /** Pure current-intent query. Never creates a path, target, move order or effect lease. */
    public static double predatoryBonus(LivingEntity body) {
        if(!(body instanceof TeamSafeCombatUnit source)||!approved(source)||!body(source.mob())||source.mob().isNoAi()||disabled(source)||source.isCasting()
                ||source.getHoldPosition()||source.getFollowTarget()!=null||source.getMoveGoal().getMoveTarget()!=null
                ||UnitServerEvents.hasPendingImmediateAction(source.mob().getId())||AttackerUnit.isAttackingBuilding(source)
                ||!(source.getAttackGoal() instanceof TeamSafeMeleeAttackGoal)
                ||source.mob().goalSelector.getRunningGoals().noneMatch(goal->goal.getGoal()==source.getAttackGoal())
                ||source.mob().getNavigation().getPath()==null||source.mob().getNavigation().isDone())return 0;
        LivingEntity target=source.mob().getTarget();
        if(target==null||!enemy(source,target)||!Float.isFinite(target.getHealth())||!Float.isFinite(target.getMaxHealth())
                ||target.getMaxHealth()<=0||target.getHealth()>=target.getMaxHealth()*LOW_HEALTH_RATIO
                ||!outsideMeleeReach(source,target)||!source.mob().hasLineOfSight(target))return 0;
        // A newly selected target may coexist briefly with the old target's path. Wait for the
        // original goal's next repath; never manufacture a route or extrapolate a hidden target.
        var path=source.mob().getNavigation().getPath();
        if(path==null||!target.blockPosition().equals(path.getTarget()))return 0;
        ServerPlayer viewer=((ServerLevel)source.mob().level()).getServer().getPlayerList().getPlayerByName(source.getOwnerName());
        return CoordinatedMark.visible(viewer,target)?PREDATORY_BONUS:0;
    }
    public static void tickPredatory(TeamSafeCombatUnit source) { if(server(source.mob()))SkillEffects.refresh(source.mob()); }
    static void tick(ServerLevel level) {
        DISARMS.entrySet().removeIf(entry->entry.getKey().level()==level&&!entry.getValue().current());
        PROTECTIONS.entrySet().removeIf(entry->entry.getValue().level==level&&!entry.getValue().current());
    }
    /** Short source/target-owned effect only. Ownership changes intentionally retain loaded-body protection. */
    static void clearEffects(LivingEntity entity) {
        DISARMS.entrySet().removeIf(entry->entry.getKey()==entity||entry.getValue().caster.entity()==entity);
    }
    static void clear(LivingEntity entity,boolean preserveProtection) {
        clearEffects(entity);if(!preserveProtection)PROTECTIONS.remove(entity);
    }
    static void joined(LivingEntity entity) { clear(entity,false); }
    static void relationshipsChanged() { DISARMS.entrySet().removeIf(entry->!entry.getValue().current()); }
    static void stop() { DISARMS.clear();PROTECTIONS.clear(); /* active Java reservations retain their finally owner */ }
}
