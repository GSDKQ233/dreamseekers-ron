// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.aaroncos.ControlledAaroncosHand;
import com.dreamseekers.economy.DreamFactionAccess;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.unit.AaroncosLeftHandUnit;
import com.dreamseekers.unit.AaroncosRightHandUnit;
import com.solegendary.reignofnether.api.FinalUnitDamageHooks;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Paid controlled-hand skills, not native boss spells. Transient charges never survive reload. */
public final class AaroncosCombat {
    private static final Map<ControlledAaroncosHand,Charge> CHARGES=new IdentityHashMap<>();
    private static final ArrayDeque<Impact> HITS=new ArrayDeque<>();
    private AaroncosCombat() {}
    public static boolean isHandSkill(UnitSkill skill) {return skill==UnitSkill.AARONCOS_LEFT_PRESS||skill==UnitSkill.AARONCOS_RIGHT_SHOCK;}
    public static int shadowCost(UnitSkill skill) {return skill==UnitSkill.AARONCOS_LEFT_PRESS?20:skill==UnitSkill.AARONCOS_RIGHT_SHOCK?30:-1;}
    private static int windup(UnitSkill skill) {return skill==UnitSkill.AARONCOS_LEFT_PRESS?12:20;}
    private static int range(UnitSkill skill) {return skill==UnitSkill.AARONCOS_LEFT_PRESS?6:4;}
    private static boolean matches(ControlledAaroncosHand source,UnitSkill skill) {
        return source instanceof AaroncosLeftHandUnit&&skill==UnitSkill.AARONCOS_LEFT_PRESS
            ||source instanceof AaroncosRightHandUnit&&skill==UnitSkill.AARONCOS_RIGHT_SHOCK;
    }
    private static boolean server(Entity e) {return e.level() instanceof ServerLevel l&&l.getServer().isSameThread();}
    private static boolean loaded(SkillIdentity.Stamp s) {return s!=null&&s.valid()&&s.entity().isAddedToWorld()&&s.level().getEntity(s.entity().getId())==s.entity();}
    private static boolean disabled(ControlledAaroncosHand source) {
        Mob body=source.mob();return body.isPassenger()||body.hasEffect(MobEffectRegistrar.STUN.get())
            ||body.hasEffect(MobEffectRegistrar.FREEZE.get())||body.hasEffect(MobEffectRegistrar.UNCONTROLLABLE.get());
    }
    private static boolean viewer(ServerPlayer actor,SkillIdentity.Stamp source) {
        return actor!=null&&actor.isAlive()&&!actor.isRemoved()&&actor.serverLevel()==source.level()&&actor.connection!=null
            &&source.owner().equals(actor.getGameProfile().getName())
            &&source.level().getServer().getPlayerList().getPlayer(actor.getUUID())==actor
            &&source.level().getServer().getPlayerList().getPlayerByName(source.owner())==actor;
    }
    private static boolean legal(ControlledAaroncosHand source,ServerPlayer viewer,UnitSkill skill,LivingEntity target) {
        Mob body=source.mob();int r=range(skill);
        return target!=body&&target instanceof Unit&&!(target instanceof com.dreamseekers.spore.JungleSporeCarrier)
            &&target.isAlive()&&!target.isRemoved()&&target.isAddedToWorld()&&!target.isSpectator()
            &&target.level()==body.level()&&body.level().getEntity(target.getId())==target
            &&((ServerLevel)body.level()).getEntity(target.getUUID())==target&&source.canHarm(target)
            &&body.level().getWorldBorder().isWithinBounds(target.blockPosition())
            &&ShadowGolemStrike.distanceSquared(body.position(),target.getBoundingBox())<=r*r
            &&CoordinatedMark.visible(viewer,target)&&body.hasLineOfSight(target);
    }
    private static List<SkillIdentity.Stamp> select(ControlledAaroncosHand source,ServerPlayer viewer,UnitSkill skill,LivingEntity target) {
        if(skill==UnitSkill.AARONCOS_LEFT_PRESS)return legal(source,viewer,skill,target)?List.of(SkillIdentity.capture(target)):List.of();
        if(target!=source.mob())return List.of();
        return source.mob().level().getEntitiesOfClass(LivingEntity.class,source.mob().getBoundingBox().inflate(range(skill)),
                e->legal(source,viewer,skill,e)).stream()
            .sorted(Comparator.comparingDouble((LivingEntity e)->ShadowGolemStrike.distanceSquared(source.mob().position(),e.getBoundingBox()))
                .thenComparing(e->e.getUUID().toString())).limit(4).map(SkillIdentity::capture).toList();
    }
    private static final class Charge {
        final ControlledAaroncosHand source;final ServerPlayer actor;final UnitSkill skill;
        final SkillIdentity.Stamp caster;final List<SkillIdentity.Stamp> targets;
        final Resources account;final UUID epoch;final long command,releaseAt;
        boolean paid,releasing;
        Charge(ControlledAaroncosHand source,ServerPlayer actor,UnitSkill skill,List<SkillIdentity.Stamp> targets,Resources account) {
            this.source=source;this.actor=actor;this.skill=skill;this.targets=List.copyOf(targets);this.account=account;
            caster=SkillIdentity.capture(source.mob());epoch=account.accountEpoch;command=source.rts().commandRevision;
            releaseAt=caster.level().getGameTime()+windup(skill);
        }
        boolean authority() {
            return CHARGES.get(source)==this&&loaded(caster)&&viewer(actor,caster)&&!disabled(source)
                &&source.rts().commandRevision==command&&!UnitServerEvents.hasPendingImmediateAction(source.mob().getId())
                &&ExtendedResourcesSync.account(caster.owner())==account&&account.extendedStateSupported()&&epoch.equals(account.accountEpoch)
                &&DreamFactionAccess.allows(PlayerServerEvents.getRTSPlayer(caster.owner()));
        }
        boolean validTarget(SkillIdentity.Stamp target) {return loaded(target)&&legal(source,actor,skill,target.entity());}
        boolean valid() {return authority()&&(releasing||targets.stream().anyMatch(this::validTarget))&&authority();}
    }
    public static SkillResult start(ServerPlayer actor,ControlledAaroncosHand source,UnitSkill skill,LivingEntity target,BooleanSupplier commit) {
        if(source==null||target==null||!server(source.mob())||!matches(source,skill)||commit==null||!source.mob().isAlive()||source.mob().isRemoved())return SkillResult.INVALID_SOURCE;
        if(active(source))return SkillResult.ALREADY_ACTIVE;
        SkillIdentity.Stamp original=SkillIdentity.capture(source.mob());
        if(!loaded(original)||!viewer(actor,original)||com.dreamseekers.summon.BoundedSummons.isTemporary(source.mob()))return SkillResult.INVALID_SOURCE;
        if(disabled(source)||UnitServerEvents.hasPendingImmediateAction(source.mob().getId()))return SkillResult.DISABLED;
        Resources account=ExtendedResourcesSync.account(source.getOwnerName());
        if(account==null||!account.extendedStateSupported()||!DreamFactionAccess.allows(PlayerServerEvents.getRTSPlayer(source.getOwnerName())))return SkillResult.NOT_OWNER;
        if(account.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0)<shadowCost(skill))return SkillResult.INSUFFICIENT_RESOURCES;
        List<SkillIdentity.Stamp> targets=select(source,actor,skill,target);
        if(targets.isEmpty())return SkillResult.INVALID_TARGET;
        long command=source.rts().commandRevision,revision=source.rts().skills().revision();
        CHARGES.remove(source);Unit.fullResetBehaviours(source);
        if(!loaded(original)||!viewer(actor,original)||source.rts().commandRevision!=command+1
            ||source.rts().skills().revision()!=revision||UnitServerEvents.hasPendingImmediateAction(source.mob().getId()))return SkillResult.REPLAY;
        Charge charge=new Charge(source,actor,skill,targets,account);CHARGES.put(source,charge);stopMovement(source);
        if(!charge.valid()||!commit.getAsBoolean()||!charge.paid){CHARGES.remove(source,charge);return SkillResult.REPLAY;}
        source.mob().swing(InteractionHand.MAIN_HAND,true);ExtendedResourcesSync.send(actor);return SkillResult.SUCCESS;
    }
    /** Called only by authenticated SkillService immediately before its no-callback SkillState.commit. */
    public static boolean pay(ControlledAaroncosHand source,UnitSkill skill) {
        Charge charge=CHARGES.get(source);
        if(charge==null||charge.skill!=skill||charge.paid||charge.releasing||!charge.valid())return false;
        var result=ResourceTransaction.exchange(charge.caster.level().getServer(),charge.account,charge.epoch,charge.account.revision,
            new ResourceTransaction.Cost(0,0,0,Map.of(DreamPassiveEconomy.SHADOW,shadowCost(skill))),ResourceTransaction.Cost.ZERO);
        charge.paid=result.applied();return charge.paid;
    }
    public static boolean active(ControlledAaroncosHand source) {if(!server(source.mob()))return false;Charge c=CHARGES.get(source);return c!=null&&c.valid();}
    public static int remainingTicks(ControlledAaroncosHand source) {Charge c=CHARGES.get(source);return active(source)?(int)Math.max(0,c.releaseAt-source.mob().level().getGameTime()):0;}
    private static void stopMovement(ControlledAaroncosHand source) {
        Mob body=source.mob();body.getNavigation().stop();body.getMoveControl().setWantedPosition(body.getX(),body.getY(),body.getZ(),0);
        body.setSpeed(0);body.setZza(0);body.setXxa(0);
    }
    public static Goal goal(ControlledAaroncosHand source) {return new Goal(){
        {setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){return active(source);}
        @Override public boolean canContinueToUse(){return active(source);}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void tick(){if(active(source))stopMovement(source);}
    };}
    public static void beforeTick(ControlledAaroncosHand source) {if(!server(source.mob()))return;Charge c=CHARGES.get(source);if(c!=null&&!c.valid())CHARGES.remove(source,c);else if(c!=null)stopMovement(source);}
    public static void afterTick(ControlledAaroncosHand source) {
        if(!server(source.mob()))return;Charge c=CHARGES.get(source);
        if(c==null||c.releasing||!c.paid)return;
        if(!c.valid()){CHARGES.remove(source,c);return;}
        if(source.mob().level().getGameTime()<c.releaseAt)return;
        c.releasing=true;
        try {
            float ad=(float)(source.getUnitAttackDamage()+com.solegendary.reignofnether.unit.interfaces.AttackerUnit.getWeaponDamageModifier(source)),mult=c.skill==UnitSkill.AARONCOS_LEFT_PRESS?1.75f:1f;
            if(!Float.isFinite(ad)||ad<=0||ad>100000||!c.authority())return;
            for(SkillIdentity.Stamp target:c.targets) {
                if(!c.authority())break;
                if(!c.validTarget(target))continue;
                Impact hit=new Impact(c,target,ad*mult);HITS.push(hit);
                try {com.solegendary.reignofnether.resources.kill.EconomicExactDamageHooks.wrap(source.mob(),target.entity(),target.entity()::hurt).hurt(hit.damage,hit.amount);}
                finally {HITS.removeFirstOccurrence(hit);}
                AaroncosShockSuppression.install(hit);
            }
        } finally {CHARGES.remove(source,c);}
    }
    private static final class HandDamage extends DamageSource {
        HandDamage(ControlledAaroncosHand source) {super(source.mob().level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
            .getHolderOrThrow(DamageTypes.MOB_ATTACK),source.mob(),source.mob());}
    }
    static final class Impact {
        final Charge charge;final SkillIdentity.Stamp target;final DamageSource damage;final float amount;
        boolean entered,claimed,revoked,reported,receipt,receiptConsumed;
        Impact(Charge charge,SkillIdentity.Stamp target,float amount){this.charge=charge;this.target=target;this.amount=amount;damage=new HandDamage(charge.source);}
        boolean legal(){return entered&&!revoked&&!reported&&charge.authority()&&charge.validTarget(target);}
        void applied(float healthLoss,float shieldLoss) {
            if(reported)return;
            boolean authorized=claimed&&HITS.peek()==this&&legal();reported=true;
            receipt=authorized&&charge.skill==UnitSkill.AARONCOS_RIGHT_SHOCK
                    &&((Float.isFinite(healthLoss)&&healthLoss>0)||(Float.isFinite(shieldLoss)&&shieldLoss>0))
                    &&suppressionAuthority();
        }
        boolean suppressionAuthority() {
            return reported&&!revoked&&charge.skill==UnitSkill.AARONCOS_RIGHT_SHOCK
                    &&charge.authority()&&charge.validTarget(target);
        }
        boolean consumeSuppressionReceipt() {
            if(receiptConsumed)return false;receiptConsumed=true;
            return receipt&&suppressionAuthority();
        }
        SkillIdentity.Stamp caster() { return charge.caster; }
        Resources account() { return charge.account; }
        UUID accountEpoch() { return charge.epoch; }
    }
    public static boolean isStrike(DamageSource source){return source instanceof HandDamage;}
    private static Impact matching(LivingEntity target,DamageSource source){Impact h=HITS.peek();return h!=null&&h.target.entity()==target&&h.damage==source?h:null;}
    public static boolean rejectRepeatedHurt(LivingEntity target,DamageSource source){
        if(!isStrike(source))return false;if(!server(target))return true;Impact h=matching(target,source);
        if(h==null)return true;if(h.entered){h.revoked=true;return true;}h.entered=true;return !h.legal();
    }
    public static float validateAfterHurt(LivingEntity target,DamageSource source,float amount){
        if(!isStrike(source))return amount;Impact h=matching(target,source);return h!=null&&h.claimed&&h.legal()?amount:0;
    }
    private static final FinalUnitDamageHooks.OrdinaryAttack DENIED=new FinalUnitDamageHooks.OrdinaryAttack(){
        @Override public float beforeArmor(float amount){return 0;}
        @Override public FinalUnitDamageHooks.Finalizer prepareFinal(){return amount->0;}
    };
    static FinalUnitDamageHooks.OrdinaryAttack prepareOrdinaryAttack(LivingEntity target,DamageSource source){
        if(!isStrike(source))return null;Impact h=matching(target,source);
        if(h==null||!h.entered||h.claimed)return DENIED;h.claimed=true;
        return new FinalUnitDamageHooks.OrdinaryAttack(){
            @Override public float beforeArmor(float amount){return h.legal()?h.amount:0;}
            @Override public float validateAfterHurt(float amount){return h.legal()?amount:0;}
            @Override public FinalUnitDamageHooks.Finalizer prepareFinal(){return SkillEffects.prepareAaroncosFinal(target,source,h);}
        };
    }
    public static void commandChanged(ControlledAaroncosHand source){if(server(source.mob()))CHARGES.remove(source);}
    static void tick(ServerLevel level){CHARGES.entrySet().removeIf(e->e.getKey().mob().level()==level&&!e.getValue().valid());}
    static void clear(LivingEntity entity){CHARGES.entrySet().removeIf(e->e.getKey().mob()==entity);}
    static void stop(){CHARGES.clear();HITS.clear();}
}
