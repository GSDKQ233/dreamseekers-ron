// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.spore.JungleSporeCarrier;
import com.dreamseekers.unit.ShadowHandUnit;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.goals.UnitBowAttackGoal;
import com.solegendary.reignofnether.unit.goals.UnitCrossbowAttackGoal;
import com.solegendary.reignofnether.unit.goals.UnitRangedAttackGoal;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/** Selects a target for the normal server cast. Never issues attack, movement or resource orders. */
final class SkillAutocastPolicy {
    private SkillAutocastPolicy() { }
    private static final int SUPPORT_CANDIDATES = 64, RECENT_HIT_TICKS = 60;
    private static final int MAX_OBSERVED_COMBAT_RANGE = 12;
    private static final EntityTypeTest<Entity, LivingEntity> LIVING = EntityTypeTest.forClass(LivingEntity.class);
    private static final EntityTypeTest<Entity, ShadowHandUnit> SHADOW_HANDS = EntityTypeTest.forClass(ShadowHandUnit.class);

    static boolean supports(UnitSkill skill) {
        return skill != null && switch (skill) {
            case JELLY_FLOW, GEL_GUARD, FOX_BLESSING, BEETLE_ADVANCE, SNAIL_SHELL_FORMATION,
                 COORDINATED_MARK, SHADOW_CORROSION_TOUCH, SQUEAL_SUPPRESSION_WAVE,
                 TERRORBEAK_DREAD_ROAR, FRIENDLY_DEBT_CONTRACT, SHADOW_HAND_GRIP, ASH_EMBER_LANDING,
                 FOX_DREAM_SEED, STONE_HEAVY_SHOT, SHADOW_GOLEM_STRIKE, WIND_DASH, FIREFLY_RUSH -> true;
            default -> false;
        };
    }

    static boolean defaultEnabled(UnitSkill skill) { return supports(skill); }

    static boolean nativeTriggerOnly(UnitSkill skill) {
        return switch(skill) {
            case GEL_GUARD, SNAIL_SHELL_FORMATION, STONE_HEAVY_SHOT, SHADOW_GOLEM_STRIKE, WIND_DASH, FIREFLY_RUSH -> true;
            default -> false;
        };
    }

    @Nullable static LivingEntity select(SkillUnit source, UnitSkill skill, @Nullable ServerPlayer owner) {
        if (!supports(skill) || !(source.mob().level() instanceof ServerLevel level)) return null;
        var body = source.mob();
        if (skill == UnitSkill.JELLY_FLOW) {
            // Preserve the original online/offline behavior and triage, including pure MOVE/FOLLOW.
            return level.getEntitiesOfClass(LivingEntity.class, body.getBoundingBox().inflate(skill.range()),
                    t -> t != body && !t.isSpectator() && SkillEffects.friendly(body, t)
                            && t.getHealth() < t.getMaxHealth() && inRange(body, t, skill.range())
                            && !SkillEffects.hasHeal(t)).stream().min(triage(body)).orElse(null);
        }
        if (owner == null || !observable(owner, body) || source.isCasting()) return null;
        // The common blessing cast asks blessingRecipients once, then rechecks every selected unit.
        if (skill == UnitSkill.FOX_BLESSING) return body;

        // Do not cast from a stale attack target during retreat/follow or a direct building order.
        if (!combatIntent(source) || !(source instanceof AttackerUnit attacker)) return null;
        LivingEntity target = focus(body);
        double radius = switch (skill) {
            case BEETLE_ADVANCE -> 3;
            case SHADOW_CORROSION_TOUCH -> MAX_OBSERVED_COMBAT_RANGE;
            default -> skill.range();
        };
        if (!visibleEnemy(source, owner, target, radius)) return null;
        return switch (skill) {
            case COORDINATED_MARK -> CoordinatedMark.ticksRemaining(target, source.getOwnerName()) == 0 ? target : null;
            case BEETLE_ADVANCE -> !disabledFighter(body) ? body : null;
            case SHADOW_CORROSION_TOUCH -> !disabledFighter(body) && meleeReach(body, attacker, target)
                    && target.getArmorValue() > 0
                    && !ShadowCorrosionTouch.hasDebuff(target) ? body : null;
            case SQUEAL_SUPPRESSION_WAVE -> !disabledFighter(body) && !SquealSuppressionWave.hasDebuff(target) ? target : null;
            case TERRORBEAK_DREAD_ROAR -> SkillService.blessingCombatUnit(target) && !disabledFighter(target)
                    && !TerrorbeakDreadRoar.hasDisarm(target) && TerrorbeakDreadRoar.remainingRecontrolTicks(target) == 0 ? body : null;
            case FRIENDLY_DEBT_CONTRACT -> FriendlyDebtContract.remainingTicks(target, source.getOwnerName()) == 0
                    && debtSquad(source, owner, target) ? target : null;
            case SHADOW_HAND_GRIP -> !ShadowHandGrip.hasRoot(target) && ShadowHandGrip.remainingRecontrolTicks(target) == 0
                    && !ownGripPending(source, owner, target) ? target : null;
            case ASH_EMBER_LANDING -> settledGroundTarget(target) ? target : null;
            default -> null;
        };
    }

    /** A valid charge can only come from within native grip range. Check loaded local hands
     * and their current owner visibility before reading that exact body's reservation. */
    private static boolean ownGripPending(SkillUnit source, ServerPlayer owner, LivingEntity target) {
        var match = new ArrayList<ShadowHandUnit>(1);
        owner.serverLevel().getEntities(SHADOW_HANDS, target.getBoundingBox().inflate(ShadowHandGrip.RANGE),
                hand -> hand != source.mob() && hand.getOwnerName().equals(source.getOwnerName())
                        && inRange(hand, target, ShadowHandGrip.RANGE) && observable(owner, hand)
                        && ShadowHandGrip.hasLiveWindupOn(hand, target), match, 1);
        return !match.isEmpty();
    }

    /** An automatic area cast must not fill spare seats with idle, already blessed or hidden fighters. */
    static List<LivingEntity> blessingRecipients(SkillUnit source, ServerPlayer owner) {
        return local(source, UnitSkill.FOX_BLESSING.range(), t -> t != source.mob() && focus(t) != null
                && SkillEffects.friendly(source.mob(), t)).stream().filter(t -> blessingRecipient(source, t, owner))
                .sorted(nearest(source.mob())).limit(SkillEffects.FOX_MAX_TARGETS).toList();
    }

    static boolean blessingRecipient(SkillUnit source, LivingEntity target, ServerPlayer owner) {
        return target != source.mob() && inRange(source.mob(), target, UnitSkill.FOX_BLESSING.range())
                && SkillEffects.friendly(source.mob(), target) && observable(owner, target) && sees(source.mob(), target)
                && !SkillEffects.hasNamedBuff(target, SkillEffects.FOX_BLESSING_KEY, BuffKind.ATTACK_SPEED)
                && engaged(source, owner, target);
    }

    static boolean matchesGroundFocus(SkillUnit source, ServerPlayer owner, LivingEntity original, BlockPos point) {
        return owner != null && combatIntent(source) && focus(source.mob()) == original && settledGroundTarget(original)
                && original.blockPosition().equals(point) && visibleEnemy(source, owner, original, UnitSkill.ASH_EMBER_LANDING.range());
    }

    private static boolean settledGroundTarget(LivingEntity target) {
        return target.onGround() && target.getDeltaMovement().horizontalDistanceSqr() <= .05 * .05
                && Math.abs(target.getDeltaMovement().y) <= .1
                && (!(target instanceof net.minecraft.world.entity.Mob mob) || mob.getNavigation().isDone());
    }

    /** Do not veto an ATTACK_MOVE's ordinary in-combat target: the shared casts do not rewrite its destination. */
    private static boolean combatIntent(SkillUnit source) {
        return source.getFollowTarget() == null && source.getMoveGoal().getMoveTarget() == null
                && !(source instanceof AttackerUnit attacker && AttackerUnit.isAttackingBuilding(attacker));
    }

    @Nullable static LivingEntity focus(LivingEntity body) {
        if (!(body instanceof Unit unit) || unit.getTargetGoal() == null) return null;
        return unit.getTargetGoal().getTarget();
    }

    private static boolean engaged(SkillUnit source, ServerPlayer owner, LivingEntity fighter) {
        LivingEntity enemy = focus(fighter);
        return enemy != null && fighter instanceof AttackerUnit attacker && !disabledFighter(fighter)
                && SkillService.blessingCombatUnit(fighter) && CoordinatedMark.enemy(fighter, enemy)
                && attackReach(fighter, attacker, enemy)
                && visibleEnemy(source, owner, enemy, UnitSkill.FRIENDLY_DEBT_CONTRACT.range() + MAX_OBSERVED_COMBAT_RANGE)
                && (fighter == source.mob() || sees(fighter, enemy));
    }

    @Nullable static LivingEntity recentAttacker(LivingEntity body) {
        int age = body.tickCount - body.getLastHurtByMobTimestamp();
        return age >= 0 && age <= RECENT_HIT_TICKS ? body.getLastHurtByMob() : null;
    }

    private static boolean debtSquad(SkillUnit source, ServerPlayer owner, LivingEntity target) {
        var body = source.mob();
        if (!engaged(source, owner, body)) return false;
        int peers = 0;
        // Settlement counts same-owner ordinary hitters, not simply any three allies standing in range.
        for (LivingEntity t : local(source, UnitSkill.FRIENDLY_DEBT_CONTRACT.range(), t -> t != body
                && t instanceof Unit u && u.getOwnerName().equals(source.getOwnerName()) && focus(t) == target)) {
            if (observable(owner, t) && sees(body, t) && engaged(source, owner, t)
                    && ++peers >= FriendlyDebtContract.REQUIRED_CONTRIBUTORS - 1) return true;
        }
        return false;
    }

    static boolean attackReach(LivingEntity fighter, AttackerUnit attacker, LivingEntity target) {
        if (!ranged(attacker)) return meleeReach(fighter, attacker, target);
        double r = attacker.getAttackRange();
        return Double.isFinite(r) && r > 0 && inRange(fighter, target, Math.min(r, MAX_OBSERVED_COMBAT_RANGE));
    }

    private static boolean meleeReach(LivingEntity fighter, AttackerUnit attacker, LivingEntity target) {
        float w = fighter.getBbWidth() + attacker.getBonusMeleeRange();
        float tw = target.getBbWidth() + (target instanceof Unit u ? u.getBonusMeleeRangeForAttackers() : 0);
        return fighter.distanceToSqr(target) <= w * 2 * w * 2 + tw;
    }

    private static boolean ranged(AttackerUnit attacker) {
        var goal = attacker.getAttackGoal();
        return goal instanceof UnitRangedAttackGoal<?> || goal instanceof UnitBowAttackGoal<?> || goal instanceof UnitCrossbowAttackGoal<?>;
    }

    static boolean disabledFighter(LivingEntity t) {
        return t.hasEffect(MobEffectRegistrar.STUN.get()) || t.hasEffect(MobEffectRegistrar.FREEZE.get())
                || t.hasEffect(MobEffectRegistrar.UNCONTROLLABLE.get()) || t.hasEffect(MobEffectRegistrar.DISARM.get())
                || TerrorbeakDreadRoar.hasDisarm(t);
    }

    private static boolean visibleEnemy(SkillUnit source, ServerPlayer owner, @Nullable LivingEntity t, double range) {
        return t != null && inRange(source.mob(), t, range) && CoordinatedMark.enemy(source.mob(), t)
                && observable(owner, t) && sees(source.mob(), t);
    }

    static boolean observable(@Nullable ServerPlayer owner, LivingEntity t) {
        if (owner == null || !(t instanceof Unit u) || t instanceof JungleSporeCarrier || t.isSpectator()
                || !t.isAlive() || t.isRemoved() || !t.isAddedToWorld() || t.level() != owner.serverLevel()) return false;
        ServerLevel level = owner.serverLevel();
        return level.getEntity(t.getId()) == t && level.getEntity(t.getUUID()) == t && level.hasChunkAt(t.blockPosition())
                && level.getWorldBorder().isWithinBounds(t.blockPosition()) && !u.getOwnerName().isBlank()
                && PlayerServerEvents.getRTSPlayer(u.getOwnerName()) != null && CoordinatedMark.visible(owner, t) && !u.isGarrisoned();
    }

    static boolean sees(LivingEntity from, LivingEntity to) {
        return BeetleMotherCommandAura.hasLoadedLineOfSight((ServerLevel)from.level(), from, to);
    }

    private static boolean inRange(LivingEntity source, LivingEntity target, double range) {
        return source.level() == target.level() && source.distanceToSqr(target) <= range * range;
    }

    private static Comparator<LivingEntity> nearest(LivingEntity origin) {
        return Comparator.comparingDouble((LivingEntity e) -> origin.distanceToSqr(e)).thenComparing(e -> e.getUUID().toString());
    }

    private static Comparator<LivingEntity> triage(LivingEntity origin) {
        return Comparator.comparingDouble((LivingEntity e) -> e.getHealth() / e.getMaxHealth()).thenComparing(nearest(origin));
    }

    /** A spatial index query, not a world scan. Check cheap role/range filters before expensive rays.
     * At 65 local matches, skip this decision rather than biasing it toward entity iteration order. */
    static List<LivingEntity> local(SkillUnit source, int radius, Predicate<LivingEntity> likely) {
        var body = source.mob(); var level = (ServerLevel)body.level();
        var list = new ArrayList<LivingEntity>();
        level.getEntities(LIVING, body.getBoundingBox().inflate(radius), t -> t instanceof Unit && !(t instanceof JungleSporeCarrier)
                && t.isAlive() && !t.isRemoved() && !t.isSpectator() && inRange(body, t, radius) && likely.test(t), list, SUPPORT_CANDIDATES + 1);
        return list.size() > SUPPORT_CANDIDATES ? List.of() : list;
    }
}
