// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.bonewing.BoneWingBolt;
import com.dreamseekers.skill.BuffKind;
import com.dreamseekers.skill.SkillEffects;
import com.dreamseekers.skill.SkillRequest;
import com.dreamseekers.skill.SkillResult;
import com.dreamseekers.skill.SkillService;
import com.dreamseekers.unit.BoneWingUnit;
import com.dreamseekers.unit.PinkSlimeUnit;
import com.solegendary.reignofnether.registrars.AttributeRegistrar;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.function.Consumer;

import static com.dreamseekers.DreamseekersMod.*;
import static com.dreamseekers.skill.UnitSkill.GEL_GUARD;

@GameTestHolder("dreamseekers_passive_edges")
@PrefixGameTestTemplate(false)
public final class DefensePassiveBoundaryGameTests {
    private static final String ARENA = "unit_isolation_arena", BATCH = "passive_defense_edges";

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) { AutocastGameTests.cleanup(level); }

    private static void power(BoneWingUnit wing, double amount) {
        wing.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(amount);
        wing.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(amount);
    }
    private static int cd(PinkSlimeUnit gel) { return gel.rts().skills().cooldownTicks(GEL_GUARD); }
    private static void close(GameTestHelper h, double actual, double expected, String what) {
        h.assertTrue(Math.abs(actual - expected) < .002, what + ": expected=" + expected + " actual=" + actual);
    }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 200)
    public static void exactlyBrokenOldGelWarrantsOneNewShieldEvenWithNoHealthWrite(GameTestHelper h) {
        var f = new AutocastGameTests.F(h, "GelOnly");
        var first = f.spawn(PINK_SLIME_UNIT.get(), f.own, 8, 13);
        var next = f.spawn(PINK_SLIME_UNIT.get(), f.own, 8, 7);
        var victim = f.durable(f.spawn(JELLYFISH_UNIT.get(), f.ally, 10, 10));
        var wing = f.spawn(BONE_WING_UNIT.get(), f.foe, 16, 10);
        ShadowGolemStrikeSkillGameTests.neutral(victim);
        f.stationary(first); f.stationary(next); f.stationary(wing);
        power(wing, SkillEffects.GEL_AMOUNT);
        int[] hits = {0}; long[] rev = {0}; float[] hp = {0}, preExisting = {0}, beforeFinal = {0};
        Consumer<LivingDamageEvent> observe = e -> {
            if (e.getEntity() != victim || e.getSource().getEntity() != wing
                    || !(e.getSource().getDirectEntity() instanceof BoneWingBolt)) return;
            hits[0]++;
            preExisting[0] = SkillEffects.shieldRemaining(victim);
            beforeFinal[0] = e.getAmount();
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, observe);
        f.cleanups.add(() -> MinecraftForge.EVENT_BUS.unregister(observe));
        f.ready().thenExecute(() -> {
            h.assertTrue(SkillService.request(f.peer.actor, new SkillRequest(GEL_GUARD, first.getId(), first.getUUID(),
                    first.rts().skills().revision(), victim.getId(), victim.getUUID())) == SkillResult.SUCCESS,
                    "owner manually deployed the original shield");
            next.setNoAi(false); rev[0] = next.rts().skills().revision(); hp[0] = victim.getHealth();
            close(h, SkillEffects.shieldRemaining(victim), 12, "original Gel before any shot");
            f.focus(wing, victim); wing.setNoAi(false);
        }).thenWaitUntil(() -> h.assertTrue(hits[0] > 0, "actual ordinary bolt reached final damage"))
          .thenExecute(() -> {
              h.assertTrue(hits[0] == 1 && cd(next) > 0 && next.rts().skills().revision() == rev[0] + 1,
                      "one confirmed shield-only primary spent once on the second guard; cd=" + cd(next));
              close(h, preExisting[0], 12, "old Gel still present during the initial damage callback");
              close(h, beforeFinal[0], 12, "actual final impact exactly breaks, not overflows, old Gel");
              close(h, victim.getHealth(), hp[0], "fully paid by old Gel, zero actual HP loss");
              close(h, SkillEffects.bonus(victim, BuffKind.ATTACK_SPEED), .15, "old Gel actually broke on hostile damage");
              close(h, SkillEffects.shieldRemaining(victim), 12, "fresh shield still full after the triggering hit");
              f.done();
          });
    }

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 300)
    public static void yellowHeartsAndRecursivePrimaryCannotManufactureAGuardReceipt(GameTestHelper h) {
        var f = new AutocastGameTests.F(h, "GelNeg");
        var guard = f.spawn(PINK_SLIME_UNIT.get(), f.own, 8, 7);
        var victim = f.durable(f.spawn(JELLYFISH_UNIT.get(), f.ally, 10, 10));
        var wing = f.spawn(BONE_WING_UNIT.get(), f.foe, 16, 10);
        ShadowGolemStrikeSkillGameTests.neutral(victim);
        f.stationary(guard); f.stationary(wing); power(wing, 8);
        int[] phase = {0}, attempts = {0}; float[] hp = {0}; long[] rev = {0};
        Consumer<LivingDamageEvent> reenter = e -> {
            if (phase[0] == 1 && e.getEntity() == victim && e.getSource().getEntity() == wing
                    && e.getSource().getDirectEntity() instanceof BoneWingBolt) {
                phase[0] = 2; // once; do not cancel or scale the outer actual bolt
                attempts[0]++;
                victim.hurt(e.getSource(), 1);
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, reenter);
        f.cleanups.add(() -> MinecraftForge.EVENT_BUS.unregister(reenter));
        f.ready().thenExecute(() -> {
            guard.setNoAi(false); hp[0] = victim.getHealth(); rev[0] = guard.rts().skills().revision();
            victim.setAbsorptionAmount(24); f.focus(wing, victim); wing.setNoAi(false);
        }).thenWaitUntil(() -> h.assertTrue(victim.getAbsorptionAmount() < 24, "real ordinary primary consumed yellow hearts"))
          .thenExecute(() -> {
              close(h, victim.getAbsorptionAmount(), 16, "yellow absorption paid for one ordinary bolt");
              close(h, victim.getHealth(), hp[0], "yellow absorption prevented any HP loss");
              h.assertTrue(cd(guard) == 0 && guard.rts().skills().revision() == rev[0]
                      && SkillEffects.shieldRemaining(victim) == 0, "not a Dreamseekers Gel or health receipt");
              victim.setAbsorptionAmount(0); phase[0] = 1;
          })
          .thenWaitUntil(() -> h.assertTrue(attempts[0] == 1 && victim.getHealth() < hp[0], "recursive callback during a real accepted ordinary bolt"))
          .thenExecute(() -> {
              close(h, victim.getHealth(), hp[0] - 8, "recursive reuse leaves original base damage alone");
              h.assertTrue(cd(guard) == 0 && guard.rts().skills().revision() == rev[0]
                      && SkillEffects.shieldRemaining(victim) == 0, "untrusted reentry cannot spend a cooldown");
          })
          .thenWaitUntil(() -> h.assertTrue(cd(guard) > 0, "later clean physical bolt gets a fresh, independent receipt"))
          .thenExecute(() -> {
              close(h, victim.getHealth(), hp[0] - 16, "clean hit lands before guard");
              close(h, SkillEffects.shieldRemaining(victim), 12, "clean receipt protects the next hit only");
              h.assertTrue(guard.rts().skills().revision() == rev[0] + 1 && attempts[0] == 1, "exactly one actual expenditure");
              f.done();
          });
    }
}
