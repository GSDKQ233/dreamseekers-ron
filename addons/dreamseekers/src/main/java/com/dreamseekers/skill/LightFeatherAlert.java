// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.unit.PinkChickenScout;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.api.HealthWriteObservers;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import java.util.ArrayDeque;

/** Passive defensive response; no command, target, visibility or damage mutation. */
public final class LightFeatherAlert {
    private LightFeatherAlert() { }
    private static final org.slf4j.Logger LOGGER=com.mojang.logging.LogUtils.getLogger();
    public static final double SPEED_BONUS=.20;
    public static final int DURATION_TICKS=60,COOLDOWN_TICKS=200;
    public static final String BUFF_KEY="light_feather_alert";
    private static final String SAVE_KEY="DreamseekersLightFeatherAlert";

    public static final class State {
        private final PinkChickenScout chicken;
        private final ArrayDeque<Hit> hits=new ArrayDeque<>();
        private long cooldownUntil;
        // Invalidates in-flight receipts if a save is loaded into the same live object.
        private long loadEpoch;
        public State(PinkChickenScout chicken) { this.chicken=chicken; }
        private long now() { return chicken.level().getGameTime(); }
        public int cooldownTicks() {
            long now=now();
            return cooldownUntil<=now?0:(int)Math.min(COOLDOWN_TICKS,cooldownUntil-now);
        }
        public long cooldownDeadline() { return cooldownUntil; }
        public boolean active() {
            return SkillEffects.hasNamedBuff(chicken,BUFF_KEY,BuffKind.MOVEMENT_SPEED);
        }
        /** Every nested invocation gets its own frame, including invalid/environment sources. */
        public Hit enter(DamageSource damage) {
            Hit hit=new Hit(this,damage);hits.push(hit);
            // Install the frame before identity observation; even nested capture failure cannot borrow an outer frame.
            try { hit.captureIdentities(); }
            catch(RuntimeException failure) {
                hit.revoked=true;LOGGER.error("Light Feather Alert identity capture failed",failure);
            }
            return hit;
        }
        public HealthWriteObservers.Observer captureWrite(DamageSource damage) {
            Hit hit=hits.peek();
            if(hit==null||hit.damage!=damage||hit.writeClaimed||!hit.eligible())return null;
            hit.writeClaimed=true;
            return actual->hit.applied(actual);
        }
        public void save(CompoundTag root) {
            CompoundTag tag=new CompoundTag();tag.putLong("CooldownUntil",cooldownUntil);root.put(SAVE_KEY,tag);
        }
        public void load(CompoundTag root) {
            // Shared readRtsData -> SkillState.load already clears short buffs and modifiers.
            // Never persist a short effect, attack source, captured frame or queued reward.
            if(loadEpoch<Long.MAX_VALUE)loadEpoch++;
            for(Hit hit:hits)hit.revoked=true;
            long now=now(),saved=root.getCompound(SAVE_KEY).getLong("CooldownUntil");
            cooldownUntil=Math.max(now,Math.min(safeDeadline(now,COOLDOWN_TICKS),saved));
        }
        private void trigger(Hit hit,float actual) {
            if(!Float.isFinite(actual)||actual<=0||!hit.eligible()||cooldownTicks()>0)return;
            long now=now();
            // Reserve first: installation/attribute notifications must not recursively trigger twice.
            cooldownUntil=safeDeadline(now,COOLDOWN_TICKS);
            // The key has one source (self). If already present, never refresh or refund a spent cooldown.
            SkillEffects.tryAddNamedBuff(chicken,chicken,BUFF_KEY,BuffKind.MOVEMENT_SPEED,SPEED_BONUS,DURATION_TICKS);
        }
    }
    public static final class Hit implements AutoCloseable {
        private final State state;
        private final DamageSource damage;
        private SkillIdentity.Stamp victim,attacker;
        private final long loadEpoch;
        private boolean writeClaimed,reported,closed,revoked;
        private Hit(State state,DamageSource damage) {
            this.state=state;this.damage=damage;this.loadEpoch=state.loadEpoch;
        }
        private void captureIdentities() {
            PinkChickenScout chicken=state.chicken;
            if(chicken.level() instanceof ServerLevel level&&level.getServer().isSameThread()
                    &&chicken.isAlive()&&!chicken.isRemoved()&&!chicken.getOwnerName().isBlank()
                    &&damage.getEntity() instanceof LivingEntity enemy&&enemy instanceof Unit unit
                    &&enemy!=chicken&&enemy.isAlive()&&!enemy.isRemoved()&&enemy.level()==level
                    &&!unit.getOwnerName().isBlank()) {
                victim=SkillIdentity.capture(chicken);attacker=SkillIdentity.capture(enemy);
            }
        }
        private boolean eligible() {
            if(closed||revoked||state.hits.peek()!=this||loadEpoch!=state.loadEpoch
                    ||victim==null||attacker==null||!victim.valid()||!attacker.valid()
                    ||damage.getEntity()!=attacker.entity()||victim.level()!=attacker.level())return false;
            return !victim.owner().equals(attacker.owner())
                    &&!AlliancesServerEvents.isAllied(victim.owner(),attacker.owner());
        }
        private void applied(float actual) {
            if(reported)return;
            reported=true; // the exact write is consumed before any effect installation
            state.trigger(this,actual);
        }
        @Override public void close() {
            closed=true;state.hits.removeFirstOccurrence(this);
        }
    }
    private static long safeDeadline(long now,int ticks) {
        return now>Long.MAX_VALUE-ticks?Long.MAX_VALUE:now+ticks;
    }
}
