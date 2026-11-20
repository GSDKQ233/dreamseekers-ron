// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;
import com.dreamseekers.unit.PinkSlimeUnit;
import com.dreamseekers.unit.JellyfishUnit;
import com.dreamseekers.unit.GoldenFoxUnit;
import com.dreamseekers.unit.WindKnightUnit;
import com.dreamseekers.unit.BoneWingUnit;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import com.solegendary.reignofnether.unit.goals.MeleeAttackUnitGoal;
import com.solegendary.reignofnether.unit.goals.MeleeWindupAttackUnitGoal;
import com.solegendary.reignofnether.unit.goals.MeleeAttackSlimeUnitGoal;
import com.solegendary.reignofnether.unit.goals.UnitRangedAttackGoal;
import com.solegendary.reignofnether.unit.goals.UnitBowAttackGoal;
import com.solegendary.reignofnether.unit.goals.UnitCrossbowAttackGoal;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.CreeperUnit;
import com.solegendary.reignofnether.unit.units.monsters.SlimeUnit;
import com.solegendary.reignofnether.unit.units.villagers.EvokerUnit;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.BowItem;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.Comparator;
import java.util.UUID;
import javax.annotation.Nullable;

/** Real authenticated sender only. Effect + cooldown commit share one server-thread transaction. */
public final class SkillService {
    private SkillService() { }
    @Nullable public static UnitSkill skillFor(SkillUnit source) {
        if(source instanceof com.dreamseekers.unit.AaroncosLeftHandUnit)return UnitSkill.AARONCOS_LEFT_PRESS;
        if(source instanceof com.dreamseekers.unit.AaroncosRightHandUnit)return UnitSkill.AARONCOS_RIGHT_SHOCK;
        if(source instanceof com.dreamseekers.unit.BeetleMotherUnit)return UnitSkill.MOTHER_BROOD;
        if(source instanceof com.dreamseekers.unit.WeepingSquealGhostUnit)return UnitSkill.WEEPING_ECHOES;
        if(source instanceof com.dreamseekers.unit.ShadowGolemUnit)return UnitSkill.SHADOW_GOLEM_STRIKE;
        if(source instanceof com.dreamseekers.unit.AshBoneWingUnit)return UnitSkill.ASH_EMBER_LANDING;
        if(source instanceof com.dreamseekers.unit.ShadowHandUnit)return UnitSkill.SHADOW_HAND_GRIP;
        if(source instanceof com.dreamseekers.unit.FriendlyGhostUnit)return UnitSkill.FRIENDLY_DEBT_CONTRACT;
        if(source instanceof com.dreamseekers.unit.TerrorbeakUnit || source instanceof com.dreamseekers.unit.CrazyTerrorbeakUnit)return UnitSkill.TERRORBEAK_DREAD_ROAR;
        if(source instanceof com.dreamseekers.unit.ShadowSquealGhostUnit)return UnitSkill.SQUEAL_SUPPRESSION_WAVE;
        if(source instanceof com.dreamseekers.unit.ShadowGhostUnit)return UnitSkill.SHADOW_CORROSION_TOUCH;
        if(source instanceof com.dreamseekers.unit.StoneSpiritThrowerUnit)return UnitSkill.STONE_HEAVY_SHOT;
        if(source instanceof com.dreamseekers.unit.BasaltSnailUnit)return UnitSkill.SNAIL_SHELL_FORMATION;
        if(source instanceof com.dreamseekers.unit.BeetleUnit)return UnitSkill.BEETLE_ADVANCE;
        if(source instanceof com.dreamseekers.unit.FireflyWorker)return UnitSkill.FIREFLY_RUSH;
        if(source instanceof PinkSlimeUnit)return UnitSkill.GEL_GUARD;
        if(source instanceof JellyfishUnit)return UnitSkill.JELLY_FLOW;
        if(source instanceof GoldenFoxUnit)return UnitSkill.FOX_BLESSING;
        if(source instanceof WindKnightUnit)return UnitSkill.WIND_DASH;
        if(source instanceof BoneWingUnit)return UnitSkill.COORDINATED_MARK;
        return null;
    }
    public static boolean supports(SkillUnit source) { return skillFor(source)!=null; }
    public static java.util.List<UnitSkill> skillsFor(SkillUnit source) {
        if(source instanceof com.dreamseekers.unit.BeetleMotherUnit)return java.util.List.of(UnitSkill.MOTHER_BROOD,UnitSkill.MOTHER_NEST);
        if(source instanceof com.dreamseekers.unit.AaroncosRightHandUnit)return java.util.List.of(UnitSkill.AARONCOS_RIGHT_SHOCK,UnitSkill.AARONCOS_RIGHT_VORTEX);
        if(source instanceof GoldenFoxUnit)return java.util.List.of(UnitSkill.FOX_BLESSING,UnitSkill.FOX_DREAM_SEED);
        UnitSkill skill=skillFor(source);return skill==null?java.util.List.of():java.util.List.of(skill);
    }
    public static boolean supports(SkillUnit source,UnitSkill skill) { return skill!=null&&skillsFor(source).contains(skill); }
    private static boolean loaded(SkillUnit source) {
        var body=source.mob();
        return body.level() instanceof ServerLevel level&&level.getServer().isSameThread()
                &&body.isAlive()&&!body.isRemoved()&&level.getEntity(body.getId())==body
                &&level.getEntity(body.getUUID())==body;
    }
    private static boolean disabled(SkillUnit source) {
        var body=source.mob();
        return body.hasEffect(MobEffectRegistrar.STUN.get())||body.hasEffect(MobEffectRegistrar.FREEZE.get())
                ||body.hasEffect(MobEffectRegistrar.UNCONTROLLABLE.get());
    }
    private static SkillResult authorization(ServerPlayer sender,UnitSkill skill,int sourceId,UUID sourceUuid,long revision) {
        if(sender==null||skill==null||sourceUuid==null||!sender.isAlive()
                ||!sender.serverLevel().getServer().isSameThread())return SkillResult.INVALID_REQUEST;
        Entity body=sender.serverLevel().getEntity(sourceId);
        if(!(body instanceof SkillUnit source)||!supports(source,skill)||!body.getUUID().equals(sourceUuid)
                ||!loaded(source))return SkillResult.INVALID_SOURCE;
        if(source.getOwnerName().isBlank()||!source.getOwnerName().equals(sender.getGameProfile().getName()))return SkillResult.NOT_OWNER;
        SkillState state=source.rts().skills();
        if(revision!=state.revision()||!state.canAdvance())return SkillResult.REPLAY;
        return SkillResult.SUCCESS;
    }
    public static SkillResult request(ServerPlayer sender,SkillRequest request) {
        if(request==null||request.targetUuid()==null)return SkillResult.INVALID_REQUEST;
        SkillResult authorized=authorization(sender,request.skill(),request.sourceId(),request.sourceUuid(),request.revision());
        if(authorized!=SkillResult.SUCCESS)return authorized;
        SkillUnit source=(SkillUnit)sender.serverLevel().getEntity(request.sourceId());
        if(source.rts().skills().cooldownTicks(request.skill())>0)return SkillResult.COOLDOWN;
        if(disabled(source))return SkillResult.DISABLED;
        Entity entity=sender.serverLevel().getEntity(request.targetId());
        if(!(entity instanceof LivingEntity target)||!entity.getUUID().equals(request.targetUuid()))return SkillResult.INVALID_TARGET;
        SkillResult result;
        if(AaroncosCombat.isHandSkill(request.skill())) {
            if(source instanceof com.dreamseekers.unit.AaroncosRightHandUnit right&&AaroncosVortex.active(right))return SkillResult.ALREADY_ACTIVE;
            if(request.skill()==UnitSkill.AARONCOS_RIGHT_SHOCK&&target!=source.mob())return SkillResult.INVALID_TARGET;
            SkillState state=source.rts().skills();
            result=AaroncosCombat.start(sender,(com.dreamseekers.aaroncos.ControlledAaroncosHand)source,request.skill(),target,()->{
                if(authorization(sender,request.skill(),request.sourceId(),request.sourceUuid(),request.revision())!=SkillResult.SUCCESS
                        ||state.cooldownTicks(request.skill())>0||disabled(source))return false;
                if(!AaroncosCombat.pay((com.dreamseekers.aaroncos.ControlledAaroncosHand)source,request.skill()))return false;
                state.commit(request.skill());return true;
            });
        } else if(request.skill()==UnitSkill.MOTHER_BROOD||request.skill()==UnitSkill.WEEPING_ECHOES) {
            if(target!=source.mob())return SkillResult.INVALID_TARGET;
            SkillState state=source.rts().skills();
            result=com.dreamseekers.summon.BoundedSummons.cast(sender,source,request.skill(),()->{
                if(authorization(sender,request.skill(),request.sourceId(),request.sourceUuid(),request.revision())!=SkillResult.SUCCESS
                        ||state.cooldownTicks(request.skill())>0||disabled(source))return false;
                if(!com.dreamseekers.summon.BoundedSummons.pay(source,request.skill()))return false;
                state.commit(request.skill());return true;
            });
        } else if(request.skill()==UnitSkill.SHADOW_GOLEM_STRIKE) {
            SkillState state=source.rts().skills();
            result=ShadowGolemStrike.start(sender,(com.dreamseekers.unit.ShadowGolemUnit)source,target,()->{
                if(authorization(sender,request.skill(),request.sourceId(),request.sourceUuid(),request.revision())!=SkillResult.SUCCESS
                        ||state.cooldownTicks(request.skill())>0||disabled(source))return false;
                state.commit(request.skill());return true;
            });
        } else if(request.skill()==UnitSkill.SHADOW_HAND_GRIP) {
            SkillState state=source.rts().skills();
            result=ShadowHandGrip.start(sender,(com.dreamseekers.unit.ShadowHandUnit)source,target,()->{
                if(authorization(sender,request.skill(),request.sourceId(),request.sourceUuid(),request.revision())!=SkillResult.SUCCESS
                        ||state.cooldownTicks(request.skill())>0||disabled(source))return false;
                state.commit(request.skill());return true;
            });
        } else if(request.skill()==UnitSkill.FRIENDLY_DEBT_CONTRACT) {
            SkillState state=source.rts().skills();
            result=FriendlyDebtContract.cast(sender,(com.dreamseekers.unit.FriendlyGhostUnit)source,target,()->{
                if(authorization(sender,request.skill(),request.sourceId(),request.sourceUuid(),request.revision())!=SkillResult.SUCCESS
                        ||state.cooldownTicks(request.skill())>0||disabled(source))return false;
                state.commit(request.skill());return true;
            });
        } else if(request.skill()==UnitSkill.TERRORBEAK_DREAD_ROAR) {
            if(target!=source.mob())return SkillResult.INVALID_TARGET;
            SkillState state=source.rts().skills();
            result=TerrorbeakDreadRoar.cast(sender,(com.dreamseekers.unit.TeamSafeCombatUnit)source,()->{
                if(authorization(sender,request.skill(),request.sourceId(),request.sourceUuid(),request.revision())!=SkillResult.SUCCESS
                        ||state.cooldownTicks(request.skill())>0||disabled(source))return false;
                state.commit(request.skill());return true;
            });
        } else if(request.skill()==UnitSkill.SQUEAL_SUPPRESSION_WAVE) {
            SkillState state=source.rts().skills();
            result=SquealSuppressionWave.cast(sender,(com.dreamseekers.unit.ShadowSquealGhostUnit)source,target,()->{
                if(authorization(sender,request.skill(),request.sourceId(),request.sourceUuid(),request.revision())!=SkillResult.SUCCESS
                        ||state.cooldownTicks(request.skill())>0||disabled(source))return false;
                state.commit(request.skill());return true;
            });
        } else if(request.skill()==UnitSkill.WIND_DASH) {
            result=WindDash.start(sender,(WindKnightUnit)source,target);
            if(result==SkillResult.SUCCESS)source.rts().skills().commit(UnitSkill.WIND_DASH);
        } else if(request.skill()==UnitSkill.STONE_HEAVY_SHOT) {
            result=StoneHeavyShot.start(sender,(com.dreamseekers.unit.StoneSpiritThrowerUnit)source,target);
            if(result==SkillResult.SUCCESS)source.rts().skills().commit(UnitSkill.STONE_HEAVY_SHOT);
        } else if(request.skill()==UnitSkill.COORDINATED_MARK) {
            result=CoordinatedMark.cast(sender,(BoneWingUnit)source,target);
            if(result==SkillResult.SUCCESS)source.rts().skills().commit(UnitSkill.COORDINATED_MARK);
        } else result=cast(source,request.skill(),target);
        if(result==SkillResult.SUCCESS)source.rts().skills().sync(sender);
        return result;
    }
    /** Separate building target input; never reinterprets a ground/feet coordinate as a placement. */
    public static SkillResult requestBuilding(ServerPlayer sender,SkillBuildingRequest request) {
        if(request==null||request.origin()==null||request.contact()==null||request.skill()!=UnitSkill.SHADOW_GOLEM_STRIKE)
            return SkillResult.INVALID_REQUEST;
        SkillResult authorized=authorization(sender,request.skill(),request.sourceId(),request.sourceUuid(),request.revision());
        if(authorized!=SkillResult.SUCCESS)return authorized;
        var source=(com.dreamseekers.unit.ShadowGolemUnit)sender.serverLevel().getEntity(request.sourceId());
        SkillState state=source.rts().skills();
        if(state.cooldownTicks(request.skill())>0)return SkillResult.COOLDOWN;
        if(disabled(source))return SkillResult.DISABLED;
        SkillResult result=ShadowGolemStrike.startBuilding(sender,source,request.origin(),request.contact(),()->{
            if(authorization(sender,request.skill(),request.sourceId(),request.sourceUuid(),request.revision())!=SkillResult.SUCCESS
                    ||state.cooldownTicks(request.skill())>0||disabled(source))return false;
            state.commit(request.skill());return true;
        });
        if(result==SkillResult.SUCCESS)state.sync(sender);
        return result;
    }
    /** Ground input has no entity token, owner, amount, dimension or client balance. */
    public static SkillResult requestGround(ServerPlayer sender,SkillGroundRequest request) {
        if(request==null||request.position()==null||(request.skill()!=UnitSkill.FOX_DREAM_SEED&&request.skill()!=UnitSkill.ASH_EMBER_LANDING&&request.skill()!=UnitSkill.AARONCOS_RIGHT_VORTEX))return SkillResult.INVALID_REQUEST;
        SkillResult authorized=authorization(sender,request.skill(),request.sourceId(),request.sourceUuid(),request.revision());
        if(authorized!=SkillResult.SUCCESS)return authorized;
        SkillUnit source=(SkillUnit)sender.serverLevel().getEntity(request.sourceId());
        SkillState state=source.rts().skills();
        if(state.cooldownTicks(request.skill())>0)return SkillResult.COOLDOWN;
        if(disabled(source))return SkillResult.DISABLED;
        java.util.function.BooleanSupplier commit=()->{
            // Join callbacks may have successfully used the other skill with this global revision.
            if(authorization(sender,request.skill(),request.sourceId(),request.sourceUuid(),request.revision())!=SkillResult.SUCCESS
                    ||state.cooldownTicks(request.skill())>0||disabled(source))return false;
            if(request.skill()==UnitSkill.AARONCOS_RIGHT_VORTEX&&!AaroncosVortex.pay((com.dreamseekers.unit.AaroncosRightHandUnit)source))return false;
            state.commit(request.skill());return true;
        };
        SkillResult result=request.skill()==UnitSkill.AARONCOS_RIGHT_VORTEX
                ?AaroncosVortex.cast(sender,(com.dreamseekers.unit.AaroncosRightHandUnit)source,request.position(),commit)
                :request.skill()==UnitSkill.ASH_EMBER_LANDING
                ?AshEmberLanding.cast(sender,(com.dreamseekers.unit.AshBoneWingUnit)source,request.position(),commit)
                :com.dreamseekers.spore.SporeLease.spawn(sender,(GoldenFoxUnit)source,request.position(),commit);
        if(result==SkillResult.SUCCESS)state.sync(sender);
        return result;
    }
    /** Owner-only preference change. Disabling never cancels an already committed heal. */
    public static SkillResult setAutocast(ServerPlayer sender,UnitSkill skill,int sourceId,UUID sourceUuid,long revision,boolean enabled) {
        SkillResult authorized=authorization(sender,skill,sourceId,sourceUuid,revision);
        if(authorized!=SkillResult.SUCCESS)return authorized;
        if(!skill.supportsAutocast())return SkillResult.DISABLED;
        SkillUnit source=(SkillUnit)sender.serverLevel().getEntity(sourceId);
        SkillState state=source.rts().skills();
        if(state.autocastEnabled(skill)==enabled)return SkillResult.SUCCESS; // idempotent; no amplified state response
        state.changeAutocast(skill,enabled);state.sync(sender);return SkillResult.SUCCESS;
    }
    private static SkillResult cast(SkillUnit source,UnitSkill skill,LivingEntity target) {
        SkillState state=source.rts().skills();
        if(!loaded(source)||!supports(source,skill)||source.getOwnerName().isBlank())return SkillResult.INVALID_SOURCE;
        if(!state.canAdvance())return SkillResult.REPLAY;
        if(state.cooldownTicks(skill)>0)return SkillResult.COOLDOWN;
        if(disabled(source))return SkillResult.DISABLED;
        var body=source.mob();var level=(ServerLevel)body.level();
        if(skill==UnitSkill.MOTHER_NEST) {
            if(target!=body)return SkillResult.INVALID_TARGET;
            SkillResult result=MotherNestStance.toggle((com.dreamseekers.unit.BeetleMotherUnit)source);
            if(result==SkillResult.SUCCESS)state.commit(skill);
            return result;
        }
        if(skill==UnitSkill.SHADOW_CORROSION_TOUCH) {
            if(target!=body)return SkillResult.INVALID_TARGET;
            SkillResult result=ShadowCorrosionTouch.start((com.dreamseekers.unit.ShadowGhostUnit)source);
            if(result==SkillResult.SUCCESS)state.commit(skill);
            return result;
        }
        if(skill==UnitSkill.SNAIL_SHELL_FORMATION) {
            if(target!=body)return SkillResult.INVALID_TARGET;
            SkillResult result=SnailShellFormation.start((com.dreamseekers.unit.BasaltSnailUnit)source);
            if(result==SkillResult.SUCCESS)state.commit(skill);
            return result;
        }
        if(skill==UnitSkill.BEETLE_ADVANCE) {
            if(target!=body)return SkillResult.INVALID_TARGET;
            SkillResult result=BeetleAdvance.start((com.dreamseekers.unit.BeetleUnit)source);
            if(result==SkillResult.SUCCESS)state.commit(skill);
            return result;
        }
        if(skill==UnitSkill.FIREFLY_RUSH) {
            if(target!=body)return SkillResult.INVALID_TARGET;
            SkillResult result=FireflyRush.start((com.dreamseekers.unit.FireflyWorker)source);
            if(result==SkillResult.SUCCESS)state.commit(skill);
            return result;
        }
        if(skill==UnitSkill.FOX_BLESSING)return castBlessing(source,target);
        if(target==body||level.getEntity(target.getUUID())!=target||target.isSpectator()
                ||!SkillEffects.friendly(body,target))return SkillResult.INVALID_TARGET;
        if(body.distanceToSqr(target)>(double)skill.range()*skill.range())return SkillResult.RANGE;
        // Ordinary skills never touch the global resource ledger or rewrite movement/attack orders.
        boolean applied;
        SkillAmplification.Profile amplification=SkillAmplification.capture(source,skill);
        switch(skill) {
            case GEL_GUARD -> applied=SkillEffects.addShield(source,target,amplification);
            case JELLY_FLOW -> {
                if(target.getHealth()>=target.getMaxHealth())return SkillResult.FULL_HEALTH;
                applied=SkillEffects.addHeal(source,target,amplification);
            }
            default -> { return SkillResult.DISABLED; }
        }
        if(!applied)return SkillResult.ALREADY_ACTIVE;
        state.commit(skill,amplification);return SkillResult.SUCCESS;
    }
    /** Only proven ordinary-attack cadence goals qualify, not every AttackerUnit (notably Creeper).
     * Do not call canUse(): being idle without an enemy is not a reason to deny a support buff.
     */
    static boolean blessingCombatUnit(LivingEntity target) {
        if(!(target instanceof Mob mob)||!(target instanceof Unit)||!(target instanceof AttackerUnit attacker)
                ||target instanceof WorkerUnit||target instanceof CreeperUnit)return false;
        var aps=target.getAttribute(AttributeRegistrar.ATTACKS_PER_SECOND.get());
        var damage=target.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get());
        if(aps==null||damage==null||!Double.isFinite(aps.getBaseValue())||aps.getBaseValue()<=0)return false;
        Goal goal=attacker.getAttackGoal();
        // Native Evoker fangs are ordinary attacks but their base damage is a fixed constant, not
        // the intentionally zero damage attribute. Admit only this concrete, equipped fang role.
        if(target instanceof EvokerUnit evoker)return goal instanceof UnitBowAttackGoal<?>
                &&mob.isHolding(stack->stack.getItem() instanceof BowItem)&&evoker.getCastFangsGoal()!=null
                &&Float.isFinite(EvokerUnit.FANGS_DAMAGE)&&EvokerUnit.FANGS_DAMAGE>0;
        if(!Double.isFinite(attacker.getBaseUnitAttackDamage())||attacker.getBaseUnitAttackDamage()<=0)return false;
        if(goal instanceof UnitBowAttackGoal<?> bow)return bow.isHoldingRangedWeapon();
        if(goal instanceof UnitCrossbowAttackGoal<?>)return mob.isHolding(stack->stack.getItem() instanceof CrossbowItem);
        return goal instanceof MeleeAttackUnitGoal||goal instanceof MeleeWindupAttackUnitGoal
                ||goal instanceof UnitRangedAttackGoal<?>
                ||(target instanceof SlimeUnit&&goal instanceof MeleeAttackSlimeUnitGoal);
    }
    private static boolean blessingTarget(SkillUnit source,LivingEntity target) {
        var body=source.mob();var level=(ServerLevel)body.level();
        return target!=body&&!target.isSpectator()&&SkillEffects.friendly(body,target)
                &&level.getEntity(target.getId())==target&&level.getEntity(target.getUUID())==target
                &&body.distanceToSqr(target)<=(double)UnitSkill.FOX_BLESSING.range()*UnitSkill.FOX_BLESSING.range()
                &&blessingCombatUnit(target);
    }
    /** The client sends only its own caster token, never a position or selected recipient list. */
    private static SkillResult castBlessing(SkillUnit source,LivingEntity token) {
        if(token!=source.mob())return SkillResult.INVALID_TARGET;
        var body=source.mob();var level=(ServerLevel)body.level();
        var candidates=level.getEntitiesOfClass(LivingEntity.class,
                body.getBoundingBox().inflate(UnitSkill.FOX_BLESSING.range()),target->blessingTarget(source,target));
        candidates.sort(Comparator.comparingDouble((LivingEntity target)->body.distanceToSqr(target))
                .thenComparing(target->target.getUUID().toString()));
        if(candidates.isEmpty())return SkillResult.NO_TARGET;
        SkillAmplification.Profile amplification=SkillAmplification.capture(source,UnitSkill.FOX_BLESSING);
        int applied=0;
        for(LivingEntity target:candidates) {
            // Each addition is a same-thread named-effect reservation, including across allied casters.
            if(blessingTarget(source,target)&&SkillEffects.tryAddFoxBlessing(source,target,amplification)) {
                if(++applied>=SkillEffects.FOX_MAX_TARGETS)break;
            }
        }
        if(applied==0)return SkillResult.ALREADY_ACTIVE;
        source.rts().skills().commit(UnitSkill.FOX_BLESSING,amplification);return SkillResult.SUCCESS;
    }
    /** Trusted AI entry, never a packet. Existing orders are observed, not replaced or followed. */
    static boolean autocast(SkillUnit source) {
        if(!(source instanceof JellyfishUnit)||!loaded(source)||source.mob().isNoAi()
                ||source.getOwnerName().isBlank()||disabled(source))return false;
        UnitSkill skill=UnitSkill.JELLY_FLOW;SkillState state=source.rts().skills();
        if(!state.autocastEnabled(skill)||state.cooldownTicks(skill)>0||!state.canAdvance())return false;
        var body=source.mob();var level=(ServerLevel)body.level();
        var candidates=level.getEntitiesOfClass(LivingEntity.class,body.getBoundingBox().inflate(skill.range()),
                target->target!=body&&!target.isSpectator()&&SkillEffects.friendly(body,target)
                        &&target.getHealth()<target.getMaxHealth()
                        &&body.distanceToSqr(target)<=(double)skill.range()*skill.range()
                        &&!SkillEffects.hasHeal(target));
        candidates.sort(Comparator.comparingDouble((LivingEntity target)->target.getHealth()/target.getMaxHealth())
                .thenComparingDouble(body::distanceToSqr).thenComparing(target->target.getUUID().toString()));
        for(LivingEntity target:candidates)if(cast(source,skill,target)==SkillResult.SUCCESS)return true;
        return false;
    }
}
