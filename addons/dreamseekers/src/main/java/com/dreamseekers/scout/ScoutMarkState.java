// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.scout;

import com.dreamseekers.unit.PinkChickenScout;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.UUID;

/** One non-stacking, owner-private observation. It never changes fog, combat, or target goals. */
public final class ScoutMarkState {
    public enum Result { SUCCESS, INVALID, OWNER, COOLDOWN, HIDDEN, RANGE, ALLY, REPLAY, RATE, DISABLED }
    private static final String NBT = "DreamseekersScoutMark";
    private final PinkChickenScout scout;
    private long cooldownUntil;
    private long expiresAt;
    private long revision;
    private long lastAttempt = Long.MIN_VALUE;
    private long lastFeedback = Long.MIN_VALUE;
    @Nullable private UUID targetUuid;
    @Nullable private UUID casterUuid;
    private String castingOwner = "";
    private String targetOwner = "";
    @Nullable private UUID lastRecipient;
    private long lastSync = Long.MIN_VALUE;

    public ScoutMarkState(PinkChickenScout scout) { this.scout = scout; }
    public long revision() { return revision; }
    public boolean allowFeedback() {
        long now = now();
        if (lastFeedback != Long.MIN_VALUE && now - lastFeedback < 5) return false;
        lastFeedback = now;
        return true;
    }
    public int cooldownTicks() { return (int) Math.max(0, Math.min(ScoutBalance.MARK_COOLDOWN_TICKS, cooldownUntil - now())); }
    public int remainingTicks() { return targetUuid == null ? 0 : (int) Math.max(0, Math.min(ScoutBalance.MARK_DURATION_TICKS, expiresAt - now())); }
    @Nullable public UUID targetUuid() { return targetUuid; }
    public boolean hasActiveMark() { return targetUuid != null && remainingTicks() > 0; }
    private long now() { return scout.level().getGameTime(); }

    /** The only casting entry: authoritative sender, exact entity identity, revision and cooldown. */
    public Result tryCast(ServerPlayer sender, Entity target, long expectedRevision) {
        if (scout.level().isClientSide() || !scout.isAlive() || sender == null || !sender.isAlive()
                || sender.serverLevel() != scout.level()) return Result.INVALID;
        if (scout.getOwnerName().isBlank() || !scout.getOwnerName().equals(sender.getGameProfile().getName())) return Result.OWNER;
        if (scout.hasEffect(MobEffectRegistrar.STUN.get()) || scout.hasEffect(MobEffectRegistrar.FREEZE.get())
                || scout.hasEffect(MobEffectRegistrar.UNCONTROLLABLE.get())) return Result.DISABLED;
        if (expectedRevision != revision) return Result.REPLAY;
        if (cooldownTicks() > 0) return Result.COOLDOWN;
        long now = now();
        if (lastAttempt != Long.MIN_VALUE && now - lastAttempt < 5) return Result.RATE;
        lastAttempt = now;
        Result eligibility = eligibility(sender, target);
        if (eligibility != Result.SUCCESS) return eligibility;
        if (revision == Long.MAX_VALUE) return Result.REPLAY; // fail closed instead of wrap/replay
        targetUuid = target.getUUID();
        casterUuid = sender.getUUID();
        castingOwner = scout.getOwnerName();
        targetOwner = ((Unit) target).getOwnerName();
        expiresAt = now + ScoutBalance.MARK_DURATION_TICKS;
        cooldownUntil = now + ScoutBalance.MARK_COOLDOWN_TICKS;
        revision++;
        sync(sender);
        return Result.SUCCESS;
    }

    /** Both cast and continued observation use the same stricter-than-fog visibility gate. */
    public Result eligibility(ServerPlayer viewer, @Nullable Entity target) {
        if (viewer == null || !scout.isAlive() || viewer.serverLevel() != scout.level()
                || !scout.getOwnerName().equals(viewer.getGameProfile().getName())) return Result.OWNER;
        if (!(target instanceof LivingEntity living) || !(target instanceof Unit unit)
                || target == scout || !living.isAlive() || target.isRemoved() || target.level() != scout.level()
                || target.isSpectator()) return Result.INVALID;
        if (unit.getOwnerName().isBlank() || AlliancesServerEvents.isAlliedOrOwned(scout.getOwnerName(), unit.getOwnerName())) return Result.ALLY;
        if (scout.distanceToSqr(target) > (double) ScoutBalance.MARK_RANGE * ScoutBalance.MARK_RANGE) return Result.RANGE;
        if (scout.distanceToSqr(target) > (double) scout.getSightRange() * scout.getSightRange()
                || living.isInvisible() || !scout.hasLineOfSight(living)
                || !FogOfWarServerEvents.isBlockVisibleFor(viewer, target.getBlockX(), target.getBlockZ())) return Result.HIDDEN;
        return Result.SUCCESS;
    }

    /** Called once per server entity tick, including when its goal AI is disabled. */
    public void tick() {
        if (!(scout.level() instanceof ServerLevel level)) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayerByName(scout.getOwnerName());
        // A transferred unit must not retain its previous owner's observation or recipient.
        if (targetUuid != null && (owner == null || !owner.getUUID().equals(casterUuid)
                || !scout.getOwnerName().equals(castingOwner) || !validateActiveFor(owner))) clear();
        if (owner == null || owner.serverLevel() != level) {
            clearPreviousRecipient(level);
            return;
        }
        if (lastRecipient != null && !lastRecipient.equals(owner.getUUID())) clearPreviousRecipient(level);
        if (lastRecipient == null || now() - lastSync >= 10) sync(owner);
    }

    /** Exposed for runtime tests and packet resync, without extending the lifetime. */
    public boolean validateActiveFor(ServerPlayer owner) {
        if (!(scout.level() instanceof ServerLevel level) || targetUuid == null || expiresAt <= now()
                || !scout.isAlive() || !scout.getOwnerName().equals(castingOwner)
                || owner == null || !owner.getUUID().equals(casterUuid)) return false;
        Entity target = level.getEntity(targetUuid); // never force-load a target chunk
        return target instanceof Unit unit && unit.getOwnerName().equals(targetOwner)
                && eligibility(owner, target) == Result.SUCCESS;
    }

    public void clear() {
        if (targetUuid == null) return;
        targetUuid = null;
        casterUuid = null;
        castingOwner = "";
        targetOwner = "";
        expiresAt = 0;
        if (scout.level() instanceof ServerLevel level && lastRecipient != null) {
            ServerPlayer previous = level.getServer().getPlayerList().getPlayer(lastRecipient);
            if (previous != null) sync(previous);
        }
    }

    /** Remove the private client view on death/unload; saved timestamps are not extended. */
    public void detach() {
        if (scout.level() instanceof ServerLevel level) clearPreviousRecipient(level);
    }

    private void clearPreviousRecipient(ServerLevel level) {
        if (lastRecipient != null) {
            ServerPlayer previous = level.getServer().getPlayerList().getPlayer(lastRecipient);
            if (previous != null) ScoutMarkNetwork.send(previous, ScoutMarkNetwork.Sync.clear(scout));
        }
        lastRecipient = null;
    }

    public void sync(ServerPlayer viewer) {
        if (viewer == null || !scout.getOwnerName().equals(viewer.getGameProfile().getName())
                || viewer.serverLevel() != scout.level()) return;
        Entity target = scout.level() instanceof ServerLevel level && validateActiveFor(viewer) ? level.getEntity(targetUuid) : null;
        ScoutMarkNetwork.send(viewer, new ScoutMarkNetwork.Sync(scout.getId(), scout.getUUID(), revision,
                cooldownTicks(), target == null ? -1 : target.getId(), target == null ? null : target.getUUID(),
                target == null ? 0 : remainingTicks()));
        lastRecipient = viewer.getUUID();
        lastSync = now();
    }

    public void save(CompoundTag root) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("CooldownUntil", cooldownUntil);
        tag.putLong("Revision", revision);
        if (targetUuid != null && casterUuid != null) {
            tag.putUUID("Target", targetUuid);
            tag.putUUID("Caster", casterUuid);
            tag.putString("CastingOwner", castingOwner);
            tag.putString("TargetOwner", targetOwner);
            tag.putLong("ExpiresAt", expiresAt);
        }
        root.put(NBT, tag);
    }

    public void load(CompoundTag root) {
        CompoundTag tag = root.getCompound(NBT);
        cooldownUntil = Math.max(now(), Math.min(now() + ScoutBalance.MARK_COOLDOWN_TICKS, tag.getLong("CooldownUntil")));
        revision = Math.max(0, tag.getLong("Revision"));
        targetUuid = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
        casterUuid = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
        castingOwner = tag.getString("CastingOwner");
        targetOwner = tag.getString("TargetOwner");
        expiresAt = Math.min(now() + ScoutBalance.MARK_DURATION_TICKS, tag.getLong("ExpiresAt"));
        if (expiresAt <= now() || casterUuid == null || castingOwner.isBlank() || targetOwner.isBlank()) {
            targetUuid = null;
            casterUuid = null;
            expiresAt = 0;
        }
        lastAttempt = Long.MIN_VALUE;
        lastFeedback = Long.MIN_VALUE;
        lastRecipient = null;
        lastSync = Long.MIN_VALUE;
    }
}
