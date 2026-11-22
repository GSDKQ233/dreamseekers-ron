// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.spore.JungleSporeCarrier;
import com.dreamseekers.unit.BasaltSnailUnit;
import com.dreamseekers.unit.PinkSlimeUnit;
import com.dreamseekers.unit.ShadowGolemUnit;
import com.dreamseekers.unit.WindKnightUnit;
import com.solegendary.reignofnether.api.FinalUnitDamageHooks;
import com.solegendary.reignofnether.api.PrimaryUnitHitHooks;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.entity.EntityTypeTest;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.BooleanSupplier;

/**
 * A receipt for exactly one native ordinary primary, not a LivingAttack/Hurt event or an HP delta
 * across callbacks. No target memory, retry tick, order, or saved effect. The original hurt runs
 * unchanged. Only after its return may a surviving friendly gain a shield / HOLD overlay for the
 * next hit, or the ordinary melee attacker react to its own hit.
 */
public final class DamageTriggeredDefense {
    private static final int MAX_GUARDS = 64;
    private static final EntityTypeTest<Entity, PinkSlimeUnit> GUARDS = EntityTypeTest.forClass(PinkSlimeUnit.class);
    private static final ThreadLocal<Hit> ACTIVE = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> DISPATCHING = new ThreadLocal<>();
    private DamageTriggeredDefense() {}

    private static boolean server(Entity entity) {
        return entity.level() instanceof ServerLevel level && level.getServer().isSameThread();
    }

    /** The ordinary hit may continue through any of these changes; only its optional effect is lost. */
    private record Reactor(SkillUnit source, UnitSkill skill, SkillIdentity.Stamp stamp,
                           ServerPlayer owner, long command, long revision) {
        @Nullable static Reactor capture(SkillUnit source, UnitSkill skill) {
            ServerPlayer owner = SkillService.passiveCommander(source, skill);
            return owner == null ? null : new Reactor(source, skill, SkillIdentity.capture(source.mob()),
                    owner, source.rts().commandRevision, source.rts().skills().revision());
        }
        boolean current() {
            return BeetleAdvance.loaded(stamp) && source.rts().commandRevision == command
                    && source.rts().skills().revision() == revision
                    && SkillService.passiveCommander(source, skill) == owner;
        }
        boolean sees(LivingEntity target) {
            return SkillAutocastPolicy.observable(owner, target)
                    && (target == source.mob() || SkillAutocastPolicy.sees(source.mob(), target));
        }
    }

    private static boolean eligiblePair(LivingEntity attacker, LivingEntity victim) {
        return attacker instanceof Unit && attacker instanceof AttackerUnit && victim instanceof Unit
                && !(attacker instanceof JungleSporeCarrier) && !(victim instanceof JungleSporeCarrier)
                && !attacker.isSpectator() && !victim.isSpectator() && !victim.isInvulnerable()
                && CoordinatedMark.enemy(attacker, victim);
    }

    private static final class Hit {
        final SkillIdentity.Stamp attacker, victim;
        final RTSPlayer attackerSeat, victimSeat;
        final BooleanSupplier origin;
        final boolean ranged;
        final DamageSource damage;
        @Nullable final Reactor shell, offensive;
        @Nullable Reactor gel;
        boolean entered, revoked, captured, positive;
        @Nullable VanillaWrite vanillaWrite;

        Hit(SkillIdentity.Stamp attacker, SkillIdentity.Stamp victim, BooleanSupplier origin,
            boolean ranged, DamageSource damage) {
            this.attacker = attacker;
            this.victim = victim;
            this.origin = origin;
            this.ranged = ranged;
            this.damage = damage;
            attackerSeat = PlayerServerEvents.getRTSPlayer(attacker.owner());
            victimSeat = PlayerServerEvents.getRTSPlayer(victim.owner());
            // Neither a newly queued HOLD nor a toggle/rebind inside a Forge callback authorizes this hit.
            shell = ranged && victim.entity() instanceof BasaltSnailUnit snail && snail.isHoldingExplicitOrder()
                    ? Reactor.capture(snail, UnitSkill.SNAIL_SHELL_FORMATION) : null;
            if (!ranged && attacker.entity() instanceof ShadowGolemUnit golem)
                offensive = Reactor.capture(golem, UnitSkill.SHADOW_GOLEM_STRIKE);
            else if (!ranged && attacker.entity() instanceof WindKnightUnit knight)
                offensive = Reactor.capture(knight, UnitSkill.WIND_DASH);
            else offensive = null;
        }

        boolean legal() {
            return !revoked && BeetleAdvance.loaded(attacker) && BeetleAdvance.loaded(victim)
                    && origin.getAsBoolean() && eligiblePair(attacker.entity(), victim.entity())
                    && attackerSeat != null && victimSeat != null
                    && PlayerServerEvents.getRTSPlayer(attacker.owner()) == attackerSeat
                    && PlayerServerEvents.getRTSPlayer(victim.owner()) == victimSeat
                    && !victim.entity().isInvulnerableTo(damage);
        }

        boolean witnessed(Reactor observer) {
            return observer != null && observer.current()
                    && observer.sees(attacker.entity()) && observer.sees(victim.entity());
        }

        boolean mayProtect(Reactor guard) {
            var slime = guard.source().mob();
            var target = victim.entity();
            int range = UnitSkill.GEL_GUARD.range();
            return slime != target && slime.distanceToSqr(target) <= range * range
                    && SkillEffects.friendly(slime, target) && CoordinatedMark.enemy(slime, attacker.entity())
                    && SkillEffects.shieldRemaining(target) <= 0 && witnessed(guard);
        }

        /** One typed spatial query only after real HP/Gel was lost. More than 64 plausible guards
         * means no guard, not whoever happened to be returned first by the section iterator. */
        @Nullable Reactor findGuard() {
            var target = victim.entity();
            if (SkillEffects.shieldRemaining(target) > 0) return null;
            int range = UnitSkill.GEL_GUARD.range();
            var guards = new ArrayList<PinkSlimeUnit>();
            victim.level().getEntities(GUARDS, target.getBoundingBox().inflate(range), slime ->
                    slime != target && !slime.isNoAi() && !slime.isSpectator()
                    && slime.distanceToSqr(target) <= range * range && SkillEffects.friendly(slime, target)
                    && CoordinatedMark.enemy(slime, attacker.entity())
                    && slime.rts().skills().autocastEnabled(UnitSkill.GEL_GUARD)
                    && slime.rts().skills().cooldownTicks(UnitSkill.GEL_GUARD) == 0, guards, MAX_GUARDS + 1);
            if (guards.size() > MAX_GUARDS) return null;
            guards.sort(Comparator.comparingDouble((PinkSlimeUnit slime) -> slime.distanceToSqr(target))
                    .thenComparing(Entity::getUUID));
            for (PinkSlimeUnit slime : guards) {
                Reactor guard = Reactor.capture(slime, UnitSkill.GEL_GUARD);
                if (guard != null && mayProtect(guard)) return guard;
            }
            return null;
        }

        void finish() {
            if (!positive || !legal()) return;
            var target = victim.entity();
            if (shell != null && shell.source() instanceof BasaltSnailUnit snail && snail.isHoldingExplicitOrder()
                    && witnessed(shell))
                SkillService.castReactive(snail, UnitSkill.SNAIL_SHELL_FORMATION, snail, shell.owner());
            if (gel != null && legal() && mayProtect(gel))
                SkillService.castReactive(gel.source(), UnitSkill.GEL_GUARD, target, gel.owner());
            if (offensive == null || !legal() || !witnessed(offensive)
                    || offensive.source().getTargetGoal() == null
                    || offensive.source().getTargetGoal().getTarget() != target) return;
            if (offensive.source() instanceof ShadowGolemUnit golem)
                ShadowGolemStrike.passiveReceipt(golem, target, offensive.owner());
            else if (offensive.source() instanceof WindKnightUnit knight)
                WindDash.passiveReceipt(knight, target, offensive.owner());
        }
    }

    private static boolean physical(DamageSource damage) {
        return damage != null && !damage.is(DamageTypeTags.IS_EXPLOSION)
                && !damage.is(DamageTypeTags.IS_FIRE) && !damage.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && !damage.is(DamageTypeTags.BYPASSES_EFFECTS) && !damage.is(DamageTypeTags.BYPASSES_RESISTANCE);
    }

    /** Only the exact claimed native ordinary melee call, not a manual doHurtTarget / skill swing. */
    static boolean melee(Mob source, Entity target, @Nullable DamageSource damage, BooleanSupplier original) {
        if (!server(source) || ACTIVE.get() != null || DISPATCHING.get() != null || !(target instanceof LivingEntity victim)
                || !eligiblePair(source, victim) || !physical(damage) || damage.getEntity() != source
                || damage.getDirectEntity() != source || damage.is(DamageTypeTags.IS_PROJECTILE)
                || !(damage.is(DamageTypes.MOB_ATTACK) || NativeOrdinaryDebtHit.accepts(source, damage)))
            return original.getAsBoolean();
        return run(new Hit(SkillIdentity.capture(source), SkillIdentity.capture(victim), () -> true, false, damage), original);
    }

    /** Called only for the primary hit of an ordinary, actually published projectile; never a
     * secondary/AoE damage tag or a newly fabricated proxy. Keep normal damage on every denial. */
    @Nullable static PrimaryUnitHitHooks.Impact projectile(SkillIdentity.Stamp shooter, LivingEntity target,
                    BooleanSupplier origin, @Nullable PrimaryUnitHitHooks.Impact original) {
        if (!server(target) || ACTIVE.get() != null || DISPATCHING.get() != null
                || !BeetleAdvance.loaded(shooter) || !eligiblePair(shooter.entity(), target)) return original;
        SkillIdentity.Stamp victim = SkillIdentity.capture(target);
        PrimaryUnitHitHooks.Impact delegate = original == null ? target::hurt : original;
        return new PrimaryUnitHitHooks.Impact() {
            private boolean used;
            @Override public boolean hurt(DamageSource damage, float amount) {
                if (!server(target) || used) return delegate.hurt(damage, amount);
                used = true;
                if (ACTIVE.get() != null || DISPATCHING.get() != null || !physical(damage)
                        || !damage.is(DamageTypeTags.IS_PROJECTILE) || damage.getEntity() != shooter.entity()
                        || !Float.isFinite(amount) || amount <= 0 || !BeetleAdvance.loaded(victim)
                        || !BeetleAdvance.loaded(shooter) || !origin.getAsBoolean())
                    return delegate.hurt(damage, amount);
                return run(new Hit(shooter, victim, origin, true, damage), () -> delegate.hurt(damage, amount));
            }
        };
    }

    private static boolean run(Hit hit, BooleanSupplier original) {
        if (!hit.legal()) return original.getAsBoolean();
        ACTIVE.set(hit);
        boolean result;
        try { result = original.getAsBoolean(); }
        finally { ACTIVE.remove(); }
        // Never expose an active primary to a new effect, a derived splash, or a nested sync callback.
        if (hit.positive) {
            DISPATCHING.set(true);
            try { hit.finish(); }
            finally { DISPATCHING.remove(); }
        }
        return result;
    }

    /** Reentry with the very same victim/source revokes the optional evidence, not either base hurt. */
    public static void observeHurt(LivingEntity target, DamageSource damage) {
        if (!server(target)) return;
        Hit hit = ACTIVE.get();
        if (hit == null || hit.damage != damage || hit.victim.entity() != target) return;
        if (hit.entered) hit.revoked = true;
        hit.entered = true;
    }

    @Nullable private static Hit matching(LivingEntity target, DamageSource damage) {
        if (!server(target)) return null;
        Hit hit = ACTIVE.get();
        return hit != null && hit.entered && !hit.revoked && hit.damage == damage && hit.victim.entity() == target
                ? hit : null;
    }
    @Nullable static Receipt capture(LivingEntity target, DamageSource damage) {
        Hit hit = matching(target, damage);
        if (hit == null || !hit.legal()) return null;
        if (hit.captured) { hit.revoked = true; return null; }
        hit.captured = true;
        return new Receipt(hit);
    }
    static final class Receipt {
        private final Hit hit;
        private boolean used;
        private Receipt(Hit hit) { this.hit = hit; }

        void applied(float healthLoss, float gelLoss) {
            if (used) return;
            used = true;
            if (ACTIVE.get() != hit || !hit.legal() || !positive(healthLoss) && !positive(gelLoss)) return;
            hit.positive = true;
            hit.gel = hit.findGuard();
        }
        private static boolean positive(float value) { return Float.isFinite(value) && value > 0; }
    }

    /** The exact Bee sting takes the vanilla damage branch, whose health write is outside
     * FinalUnitDamageHooks.begin. Pair its actual write (or actual full Gel absorption) to the
     * common ticket. If another role observes the very same write, the ticket deduplicates. */
    public static final class VanillaWrite {
        private final Hit hit;
        private final FinalUnitDamageHooks.Ticket ticket;
        private float amount;
        private boolean resolved, reported;
        private VanillaWrite(Hit hit, FinalUnitDamageHooks.Ticket ticket) { this.hit = hit; this.ticket = ticket; }
    }
    @Nullable public static VanillaWrite captureVanillaTicket(LivingEntity target, DamageSource damage,
                                                               FinalUnitDamageHooks.Ticket ticket) {
        Hit hit = matching(target, damage);
        if (hit == null || !hit.captured) return null;
        if (hit.vanillaWrite != null) { hit.revoked = true; return null; }
        return hit.vanillaWrite = new VanillaWrite(hit, ticket);
    }
    public static void resolvedVanillaTicket(@Nullable VanillaWrite write, float result) {
        if (write == null || write.resolved) return;
        write.resolved = true;
        write.amount = result;
        if (result == 0 && ACTIVE.get() == write.hit && !write.hit.revoked) {
            write.reported = true;
            write.ticket.appliedHealthDamage(0); // no HP write; only spent Gel can make this positive
        }
    }
    public static void vanillaHealthWrite(LivingEntity target, DamageSource damage, Runnable original) {
        Hit hit = matching(target, damage);
        VanillaWrite write = hit == null ? null : hit.vanillaWrite;
        if (write == null || !write.resolved || write.reported) { original.run(); return; }
        write.reported = true;
        float before = target.getHealth();
        original.run();
        float after = target.getHealth();
        float actual = Float.isFinite(before) && Float.isFinite(after) && Float.isFinite(write.amount)
                ? Math.min(write.amount, Math.max(0, Math.max(0, before) - Math.max(0, after))) : 0;
        write.ticket.appliedHealthDamage(actual);
    }
}
