// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.spore.SporeEffects;
import com.dreamseekers.unit.FriendlyGhostUnit;
import com.solegendary.reignofnether.api.FinalUnitDamageHooks;
import com.solegendary.reignofnether.api.PrimaryUnitHitHooks;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.goals.UnitRangedAttackGoal;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Independent ordinary-hit receipt, owner-target contract and one non-primary skill settlement.
 * No contributor history, mark, pending settlement or temporary attribute is serialized.
 */
public final class FriendlyDebtContract {
    public static final int RANGE=10,DURATION_TICKS=120,REQUIRED_CONTRIBUTORS=3;
    public static final double PURSUIT_BONUS=.10;
    public static final ResourceLocation EFFECT_ID=ResourceLocation.fromNamespaceAndPath("dreamseekers","friendly_debt_contract");
    public static final ResourceKey<DamageType> DAMAGE_TYPE=ResourceKey.create(Registries.DAMAGE_TYPE,EFFECT_ID);
    private record Key(String owner,UUID target) { }
    private static final Map<ServerLevel,Map<Key,Mark>> WORLDS=new HashMap<>();
    private static final Map<FriendlyGhostUnit,Object> BUSY=new IdentityHashMap<>();
    private static final ArrayDeque<Attempt> ORDINARY=new ArrayDeque<>();
    private static final ArrayDeque<Settlement> SETTLEMENTS=new ArrayDeque<>();
    private FriendlyDebtContract() { }

    private static boolean server(Entity entity) {
        return entity.level() instanceof ServerLevel level&&level.getServer().isSameThread();
    }
    private static boolean loaded(SkillIdentity.Stamp stamp) {
        return stamp!=null&&stamp.valid()&&stamp.entity().getUUID().equals(stamp.uuid())&&stamp.entity().isAddedToWorld()
                &&stamp.level().getEntity(stamp.entity().getId())==stamp.entity();
    }
    private static boolean enemy(LivingEntity source,LivingEntity target) {
        return target instanceof Unit&&!(target instanceof com.dreamseekers.spore.JungleSporeCarrier)
                &&!target.isSpectator()&&CoordinatedMark.enemy(source,target);
    }
    private static final class Mark implements SporeEffects.ManagedNegative {
        final SkillIdentity.Stamp caster,target;
        final UUID viewerUuid;
        final Key key;
        final long expiresAt;
        final float damage;
        final Set<UUID> contributors=new HashSet<>();
        boolean consumed;
        Mark(SkillIdentity.Stamp caster,SkillIdentity.Stamp target,UUID viewerUuid,int ticks,float damage) {
            this.caster=caster;this.target=target;this.viewerUuid=viewerUuid;this.damage=damage;
            key=new Key(caster.owner(),target.uuid());expiresAt=target.level().getGameTime()+ticks;
        }
        boolean identitiesValid() {
            return loaded(caster)&&loaded(target)&&target.level().getGameTime()<expiresAt&&enemy(caster.entity(),target.entity());
        }
        @Override public ResourceLocation id() { return EFFECT_ID; }
        @Override public String tieBreakKey() { return viewerUuid.toString(); }
        @Override public boolean current() {
            Map<Key,Mark> world=WORLDS.get(caster.level());
            return !consumed&&world!=null&&world.get(key)==this&&identitiesValid();
        }
        @Override public boolean remove() { return current()&&retire(this); }
    }
    private static Mark find(LivingEntity target,String owner) {
        if(target==null||owner==null||!server(target))return null;
        Map<Key,Mark> world=WORLDS.get((ServerLevel)target.level());if(world==null)return null;
        Mark mark=world.get(new Key(owner,target.getUUID()));
        return mark!=null&&mark.target.entity()==target&&mark.current()?mark:null;
    }
    /** Exact, read-only owner/target lookup for an observed combatant; never scans other contracts. */
    public static SporeEffects.ManagedNegative negative(LivingEntity target,String owner) { return find(target,owner); }
    public static int remainingTicks(LivingEntity target,String owner) {
        Mark mark=find(target,owner);return mark==null?0:(int)(mark.expiresAt-target.level().getGameTime());
    }
    public static int contributors(LivingEntity target,String owner) {
        Mark mark=find(target,owner);return mark==null?0:mark.contributors.size();
    }
    public static boolean isSettlement(DamageSource source) { return source!=null&&source.is(DAMAGE_TYPE); }
    public static List<SporeEffects.ManagedNegative> negatives(LivingEntity target) {
        if(!server(target))return List.of();
        Map<Key,Mark> world=WORLDS.get((ServerLevel)target.level());if(world==null)return List.of();
        return world.values().stream().filter(mark->mark.target.entity()==target&&mark.current())
                .sorted(java.util.Comparator.comparing(mark->mark.viewerUuid.toString()))
                .map(mark->(SporeEffects.ManagedNegative)mark).toList();
    }
    private static ServerPlayer viewer(Mark mark) {
        ServerPlayer viewer=mark.caster.level().getServer().getPlayerList().getPlayer(mark.viewerUuid);
        return viewer!=null&&viewer.getGameProfile().getName().equals(mark.caster.owner())?viewer:null;
    }
    private static boolean visibleInRange(Mark mark) {
        return mark.identitiesValid()&&mark.caster.entity().distanceToSqr(mark.target.entity())<=RANGE*RANGE
                &&mark.caster.entity().hasLineOfSight(mark.target.entity())&&CoordinatedMark.visible(viewer(mark),mark.target.entity());
    }
    private static boolean castValid(ServerPlayer viewer,FriendlyGhostUnit source,SkillIdentity.Stamp caster,
                                     SkillIdentity.Stamp target,long command) {
        return loaded(caster)&&loaded(target)&&viewer.isAlive()&&!viewer.isRemoved()&&viewer.serverLevel()==caster.level()
                &&viewer.getGameProfile().getName().equals(caster.owner())&&source.rts().commandRevision==command
                &&!UnitServerEvents.hasPendingImmediateAction(source.getId())&&enemy(source,target.entity())
                &&source.distanceToSqr(target.entity())<=RANGE*RANGE&&source.hasLineOfSight(target.entity())
                &&CoordinatedMark.visible(viewer,target.entity());
    }
    static SkillResult cast(ServerPlayer viewer,FriendlyGhostUnit source,LivingEntity target,BooleanSupplier commit) {
        if(viewer==null||commit==null||!server(source)||!(source instanceof Unit)||!source.isAlive()||source.isRemoved())return SkillResult.INVALID_SOURCE;
        if(!enemy(source,target)||target.isRemoved())return SkillResult.INVALID_TARGET;
        if(source.distanceToSqr(target)>RANGE*RANGE)return SkillResult.RANGE;
        if(!CoordinatedMark.visible(viewer,target)||!source.hasLineOfSight(target))return SkillResult.HIDDEN;
        if(find(target,source.getOwnerName())!=null)return SkillResult.ALREADY_ACTIVE;
        if(BUSY.containsKey(source))return SkillResult.ALREADY_ACTIVE;
        Object reservation=new Object();BUSY.put(source,reservation);Mark installed=null;boolean committed=false;
        try {
            SkillIdentity.Stamp caster=SkillIdentity.capture(source),victim=SkillIdentity.capture(target);
            long command=source.rts().commandRevision;
            int duration=SporeEffects.incomingManagedDuration(target,DURATION_TICKS);
            float damage=source.getUnitAttackDamage();
            if(!Float.isFinite(damage)||damage<0||duration<=0||!castValid(viewer,source,caster,victim,command))return SkillResult.INVALID_TARGET;
            if(find(target,caster.owner())!=null)return SkillResult.ALREADY_ACTIVE;
            installed=new Mark(caster,victim,viewer.getUUID(),duration,damage);
            WORLDS.computeIfAbsent(caster.level(),level->new HashMap<>()).put(installed.key,installed);
            if(!installed.current()||!castValid(viewer,source,caster,victim,command)||!commit.getAsBoolean())return SkillResult.REPLAY;
            committed=true;SkillEffects.refresh(source);return SkillResult.SUCCESS;
        } finally {
            if(!committed&&installed!=null)retire(installed);
            BUSY.remove(source,reservation);
        }
    }

    /** This independent role never depends on a Bone vulnerability mark being present. */
    private static final class Attempt {
        final Mark mark;
        final SkillIdentity.Stamp participant,target;
        final BooleanSupplier origin;
        final boolean melee,vanilla;
        DamageSource damage;
        boolean active,entered,revoked,receipt,committed;
        VanillaWrite vanillaWrite;
        Attempt(Mark mark,SkillIdentity.Stamp participant,LivingEntity target,BooleanSupplier origin,boolean melee,boolean vanilla) {
            this.mark=mark;this.participant=participant;this.target=SkillIdentity.capture(target);this.origin=origin;
            this.melee=melee;this.vanilla=vanilla;
        }
        boolean eligible() {
            return !revoked&&mark.current()&&loaded(participant)&&loaded(target)&&origin.getAsBoolean()
                    &&participant.owner().equals(mark.caster.owner())&&enemy(participant.entity(),target.entity());
        }
    }
    private static Attempt attempt(SkillIdentity.Stamp participant,LivingEntity target,BooleanSupplier origin,boolean melee,boolean vanilla) {
        if(!ORDINARY.isEmpty()||!loaded(participant)||!(participant.entity() instanceof AttackerUnit)||!enemy(participant.entity(),target))return null;
        Mark mark=find(target,participant.owner());
        if(mark==null||mark.contributors.contains(participant.uuid()))return null;
        Attempt frame=new Attempt(mark,participant,target,origin,melee,vanilla);
        return frame.eligible()?frame:null;
    }
    static boolean melee(Mob source,Entity victim,BooleanSupplier original) {
        if(!server(source)||!(source instanceof Unit)||!(source instanceof AttackerUnit)||!(victim instanceof LivingEntity target)
                ||BeetleAdvance.inAttempt(source)||source instanceof com.dreamseekers.unit.WindKnightUnit knight&&WindDash.isActive(knight))return original.getAsBoolean();
        NativeOrdinaryDebtHit.Proof nativeProof=NativeOrdinaryDebtHit.capture(source,target);
        if(NativeOrdinaryDebtHit.supported(source)&&nativeProof==null)return original.getAsBoolean();
        // Exact Bee STING uses vanilla actuallyHurt, unlike the core MOB_ATTACK override. Reuse
        // its exact vanilla ticket/setHealth witness; no raw sting or unrelated source gains a receipt.
        Attempt frame=attempt(SkillIdentity.capture(source),target,nativeProof==null?()->true:nativeProof::valid,true,
                nativeProof!=null&&source.getClass()==com.solegendary.reignofnether.unit.units.neutral.BeeUnit.class);
        if(frame==null)return original.getAsBoolean();
        boolean result;ORDINARY.push(frame);
        try { result=original.getAsBoolean(); }
        finally { ORDINARY.removeFirstOccurrence(frame); }
        complete(frame); // Native knockback/on-hit callbacks and all inner ordinary scopes have completed.
        return result;
    }
    /** Only OrdinaryAttackGate's exact unsuspended, unclaimed native-hurt commit branch calls this. */
    static boolean commitMelee(Mob source,Entity target,DamageSource exact,BooleanSupplier original) {
        Attempt frame=ORDINARY.peek();
        if(frame==null||!frame.melee||frame.committed||frame.participant.entity()!=source||frame.target.entity()!=target)return original.getAsBoolean();
        if(exact!=null) {
            if(!NativeOrdinaryDebtHit.accepts(source,exact)||!frame.eligible())return original.getAsBoolean();
            frame.damage=exact; // One exact native source object, not a global STING permission.
        } else if(NativeOrdinaryDebtHit.supported(source))return original.getAsBoolean();
        frame.committed=true;frame.active=true;
        try { return original.getAsBoolean(); }
        finally { frame.active=false; }
    }
    /** Actual published ordinary Shot/Birth proof is supplied only by OrdinarySkillHit's exact factory record. */
    static PrimaryUnitHitHooks.Impact wrapOrdinary(SkillIdentity.Stamp participant,LivingEntity target,BooleanSupplier origin,
                                                  boolean vanilla,PrimaryUnitHitHooks.Impact original) {
        Attempt frame=attempt(participant,target,origin,false,vanilla);if(frame==null)return original;
        PrimaryUnitHitHooks.Impact delegate=original==null?target::hurt:original;
        return new PrimaryUnitHitHooks.Impact() {
            boolean used;
            @Override public boolean hurt(DamageSource damage,float amount) {
                if(used||damage.getEntity()!=participant.entity()||isSettlement(damage)
                        ||!vanilla&&!damage.is(DamageTypeTags.IS_PROJECTILE))return delegate.hurt(damage,amount);
                used=true;frame.damage=damage;frame.active=true;frame.committed=true;
                boolean result;ORDINARY.push(frame);
                try { result=delegate.hurt(damage,amount); }
                finally { frame.active=false;ORDINARY.removeFirstOccurrence(frame); }
                complete(frame);return result;
            }
        };
    }
    public static void observeHurt(LivingEntity target,DamageSource damage) {
        Attempt frame=ORDINARY.peek();
        if(frame==null||!frame.active||frame.target.entity()!=target||damage.getEntity()!=frame.participant.entity()||isSettlement(damage))return;
        if(frame.damage==null) {
            if(!frame.melee||damage.getDirectEntity()!=frame.participant.entity()||!damage.is(DamageTypes.MOB_ATTACK)
                    ||damage.is(DamageTypeTags.IS_PROJECTILE))return;
            frame.damage=damage;
        }
        if(frame.damage!=damage)return;
        if(frame.entered)frame.revoked=true;
        frame.entered=true; // Recursive same-source damage forfeits only the extra contract receipt, never ordinary base.
    }
    private static Attempt matching(LivingEntity target,DamageSource damage) {
        Attempt frame=ORDINARY.peek();
        return frame!=null&&frame.active&&frame.entered&&frame.damage==damage&&frame.target.entity()==target?frame:null;
    }
    static Receipt captureReceipt(LivingEntity target,DamageSource damage) {
        Attempt frame=matching(target,damage);return frame==null?null:new Receipt(frame);
    }
    static final class Receipt {
        final Attempt frame;boolean reported;
        Receipt(Attempt frame) { this.frame=frame; }
        void applied(float healthLoss,float gelLoss) {
            if(reported)return;reported=true;
            if((healthLoss>0||gelLoss>0)&&frame.eligible())frame.receipt=true;
        }
    }
    /** Captured before the final Forge callback; nested calls never replace the outer receipt token. */
    public static final class VanillaWrite {
        private final Attempt frame;
        private final FinalUnitDamageHooks.Ticket ticket;
        private float amount;
        private boolean resolved,reported;
        private VanillaWrite(Attempt frame,FinalUnitDamageHooks.Ticket ticket) { this.frame=frame;this.ticket=ticket; }
    }
    public static VanillaWrite captureVanillaTicket(LivingEntity target,DamageSource damage,FinalUnitDamageHooks.Ticket ticket) {
        Attempt frame=matching(target,damage);
        if(frame==null||!frame.vanilla||frame.revoked)return null;
        if(frame.vanillaWrite!=null) { frame.revoked=true;return null; }
        return frame.vanillaWrite=new VanillaWrite(frame,ticket);
    }
    public static void resolvedVanillaTicket(VanillaWrite write,float result) {
        if(write==null||write.resolved)return;
        write.resolved=true;write.amount=result;
        // Full Gel absorption has no native health write; its own captured ticket still reports zero HP.
        if(result==0) { write.reported=true;write.ticket.appliedHealthDamage(0); }
    }
    /** Observes precisely the original vanilla actuallyHurt setHealth call, not outer before/after health. */
    public static void vanillaHealthWrite(LivingEntity target,DamageSource damage,Runnable original) {
        Attempt frame=matching(target,damage);
        VanillaWrite write=frame==null?null:frame.vanillaWrite;
        if(frame==null||!frame.vanilla||frame.revoked||write==null||write.frame!=frame||!write.resolved||write.reported) {
            original.run();return;
        }
        write.reported=true;
        float before=target.getHealth();original.run();float after=target.getHealth();
        float actual=Float.isFinite(write.amount)&&write.amount>0&&Float.isFinite(before)&&Float.isFinite(after)
                ?Math.min(write.amount,Math.max(0,Math.max(0,before)-Math.max(0,after))):0;
        write.ticket.appliedHealthDamage(actual);
    }
    private static void complete(Attempt frame) {
        if(!frame.receipt||!frame.eligible()||!frame.mark.contributors.add(frame.participant.uuid()))return;
        // These are historical UUIDs: once a complete legal hit is recorded, later death/ABA does not erase it.
        if(frame.mark.contributors.size()<REQUIRED_CONTRIBUTORS)return;
        Mark mark=frame.mark;if(!retire(mark))return;
        // Consume first. Out-of-range/hidden/dead targets or a canceled settlement never retry.
        if(!visibleInRange(mark))return;
        LivingEntity caster=mark.caster.entity(),target=mark.target.entity();
        DamageSource source=new DamageSource(mark.caster.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE_TYPE),caster,caster);
        Settlement hit=new Settlement(mark,source);SETTLEMENTS.push(hit);
        try { com.solegendary.reignofnether.resources.kill.EconomicExactDamageHooks.wrap(caster,target,target::hurt).hurt(source,mark.damage); }
        finally { SETTLEMENTS.removeFirstOccurrence(hit); }
    }
    static final class Settlement {
        final Mark mark;final DamageSource damage;
        boolean entered,claimed,revoked;
        Settlement(Mark mark,DamageSource damage) { this.mark=mark;this.damage=damage; }
        boolean legal() {
            return !revoked&&SETTLEMENTS.peek()==this&&entered&&damage.getEntity()==mark.caster.entity()
                    &&damage.getDirectEntity()==mark.caster.entity()&&visibleInRange(mark);
        }
    }
    private static Settlement matchingSettlement(LivingEntity target,DamageSource damage) {
        Settlement hit=SETTLEMENTS.peek();
        return hit!=null&&hit.damage==damage&&hit.mark.target.entity()==target?hit:null;
    }
    public static boolean rejectSettlement(LivingEntity target,DamageSource damage) {
        if(!isSettlement(damage))return false;
        Settlement hit=matchingSettlement(target,damage);
        if(hit==null)return true;
        if(hit.entered) { hit.revoked=true;return true; }
        hit.entered=true;return !hit.legal();
    }
    private static final FinalUnitDamageHooks.OrdinaryAttack DENIED=new FinalUnitDamageHooks.OrdinaryAttack() {
        @Override public float beforeArmor(float amount) { return 0; }
        @Override public float validateAfterHurt(float amount) { return 0; }
        @Override public FinalUnitDamageHooks.Finalizer prepareFinal() { return amount->0; }
    };
    static FinalUnitDamageHooks.OrdinaryAttack prepareOrdinaryAttack(LivingEntity target,DamageSource damage) {
        if(!isSettlement(damage))return null;
        Settlement hit=matchingSettlement(target,damage);
        if(hit==null||!hit.entered||hit.claimed)return DENIED;
        hit.claimed=true;
        return new FinalUnitDamageHooks.OrdinaryAttack() {
            @Override public float beforeArmor(float ignoredDynamicBase) { return hit.legal()?hit.mark.damage:0; }
            @Override public float validateAfterHurt(float amount) { return hit.legal()?amount:0; }
            @Override public FinalUnitDamageHooks.Finalizer prepareFinal() { return SkillEffects.prepareDebtFinal(target,damage,hit); }
        };
    }

    /** The paid caster's own current contract only; original ranged goal/navigation still own behavior. */
    public static double pursuitBonus(LivingEntity body) {
        if(!(body instanceof FriendlyGhostUnit source)||!server(source)||!source.isAlive()||source.isRemoved()||source.isNoAi()
                ||source.isCasting()||source.getHoldPosition()||source.getFollowTarget()!=null||source.getMoveGoal().getMoveTarget()!=null
                ||UnitServerEvents.hasPendingImmediateAction(source.getId())||!(source.getAttackGoal() instanceof UnitRangedAttackGoal)
                ||source.goalSelector.getRunningGoals().noneMatch(goal->goal.getGoal()==source.getAttackGoal()))return 0;
        LivingEntity target=source.getTarget();if(target==null)return 0;
        Mark mark=find(target,source.getOwnerName());
        if(mark==null||mark.caster.entity()!=source||!source.hasLineOfSight(target)||!CoordinatedMark.visible(viewer(mark),target))return 0;
        if(source.hasEffect(com.solegendary.reignofnether.registrars.MobEffectRegistrar.STUN.get())
                ||source.hasEffect(com.solegendary.reignofnether.registrars.MobEffectRegistrar.FREEZE.get())
                ||source.hasEffect(com.solegendary.reignofnether.registrars.MobEffectRegistrar.UNCONTROLLABLE.get()))return 0;
        if(source.distanceTo(target)<=source.getAttackRange())return PURSUIT_BONUS;
        var path=source.getNavigation().getPath();
        return path!=null&&!source.getNavigation().isDone()&&target.blockPosition().equals(path.getTarget())?PURSUIT_BONUS:0;
    }
    public static void tickPursuit(FriendlyGhostUnit source) { if(server(source))SkillEffects.refresh(source); }
    private static boolean retire(Mark mark) {
        Map<Key,Mark> world=WORLDS.get(mark.caster.level());
        if(world==null||!world.remove(mark.key,mark))return false;
        mark.consumed=true;SkillEffects.refresh(mark.caster.entity());return true;
    }
    static void tick(ServerLevel level) {
        Map<Key,Mark> world=WORLDS.get(level);if(world==null)return;
        for(Mark mark:List.copyOf(world.values()))if(!mark.current())retire(mark);
        if(world.isEmpty())WORLDS.remove(level);
    }
    static void clear(LivingEntity entity) {
        if(!server(entity))return;
        for(Map<Key,Mark> world:List.copyOf(WORLDS.values()))for(Mark mark:List.copyOf(world.values()))
            if(mark.caster.entity()==entity||mark.target.entity()==entity)retire(mark);
        // Original hit/transaction frames remain until their owning finally. Never turn an invalid skill into base damage.
    }
    static void relationshipsChanged() {
        for(ServerLevel level:List.copyOf(WORLDS.keySet()))tick(level);
    }
    static void stop() {
        for(Map<Key,Mark> world:List.copyOf(WORLDS.values()))for(Mark mark:List.copyOf(world.values()))retire(mark);
        WORLDS.clear(); // Live Java reservations and hit frames retain their finally owners.
    }
}
