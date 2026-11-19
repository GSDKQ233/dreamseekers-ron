package com.solegendary.reignofnether.resources.kill;

import com.solegendary.reignofnether.hero.HeroServerEvents;
import com.solegendary.reignofnether.unit.HeroUnitSave;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.UUID;

/** One economic value per unit lineage, including the native new-entity hero revival path. */
public final class RtsKillValueClaims {
    public static final String KEY = "reignofnether:economic_value";
    private RtsKillValueClaims() {}
    public static final String FREE_ORIGIN_KEY = "reignofnether:free_economic_origin";
    public enum FreeOrigin { NATIVE_SUMMON, NATIVE_INFECTION, NEUTRAL_CONTRACT }
    /** Positive evidence of a free body, not a redeemed death claim. Presence is monotonic/fail-closed:
     * a future or malformed marker must never turn a known free body into fresh economic value. */
    public static boolean isFree(Entity entity) {
        return entity.getPersistentData().contains(FREE_ORIGIN_KEY);
    }
    /** Trusted native creation/setter entry only; no client packet can mint or clear this provenance. */
    public static void markFree(Entity entity, FreeOrigin origin) {
        if (!(entity.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !(entity instanceof com.solegendary.reignofnether.unit.interfaces.Unit)
                || origin == null || isFree(entity)) return;
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 1); tag.putString("origin", origin.name());
        entity.getPersistentData().put(FREE_ORIGIN_KEY, tag);
    }
    private static boolean valid(CompoundTag value) {
        return value.contains("version", Tag.TAG_INT) && value.getInt("version") == 1 && value.hasUUID("origin");
    }
    /** Invalid saved economic metadata is fail-closed, never a fresh unpaid body. */
    public static boolean isClaimed(Entity entity) {
        if (!entity.getPersistentData().contains(KEY)) return false;
        if (!entity.getPersistentData().contains(KEY,Tag.TAG_COMPOUND)) return true;
        CompoundTag value = entity.getPersistentData().getCompound(KEY);
        return !valid(value) || value.contains("claim");
    }
    public static UUID valueId(Entity entity) {
        CompoundTag value = ensure(entity);
        return valid(value) ? value.getUUID("origin") : entity.getUUID();
    }
    private static CompoundTag ensure(Entity entity) {
        if (!entity.getPersistentData().contains(KEY)) {
            CompoundTag value = new CompoundTag();
            value.putInt("version",1); value.putUUID("origin",entity.getUUID());
            entity.getPersistentData().put(KEY,value);
        }
        return entity.getPersistentData().getCompound(KEY);
    }
    /** Called when a native hero falls, before the delayed economic claim may be decided. */
    public static Tag copyValue(Entity entity) {
        ensure(entity);
        return entity.getPersistentData().get(KEY).copy();
    }
    /** Do not clear this on alive ticks, ownership changes, reconnect or hero revival. */
    public static void restoreValue(Entity entity, Tag saved) {
        if (saved != null) entity.getPersistentData().put(KEY,saved.copy());
    }
    static UUID reserve(Entity victim, String owner, UUID accountEpoch) {
        if (isClaimed(victim)) return null;
        UUID nonce = UUID.randomUUID();
        CompoundTag claim = new CompoundTag();
        claim.putUUID("id",nonce); claim.putString("owner",owner);
        claim.putUUID("accountEpoch",accountEpoch); claim.putString("outcome","reserved");
        ensure(victim).put("claim",claim);
        return nonce;
    }
    static void rollback(Entity victim, UUID nonce) {
        CompoundTag value = ensure(victim);
        CompoundTag claim = value.getCompound("claim");
        if (claim.hasUUID("id") && nonce.equals(claim.getUUID("id"))) value.remove("claim");
    }
    static void commit(LivingEntity victim, UUID nonce, String outcome) {
        CompoundTag value = ensure(victim);
        CompoundTag claim = value.getCompound("claim");
        if (!claim.hasUUID("id") || !nonce.equals(claim.getUUID("id"))) return;
        claim.putString("outcome",outcome);
        UUID origin = value.getUUID("origin");
        // A cheat/instant native revive may already have replaced the old entity this tick.
        for (LivingEntity entity : UnitServerEvents.getAllUnits()) {
            if (entity instanceof HeroUnit && entity != victim && entity.level().getServer() == victim.level().getServer()
                    && sameOrigin(entity.getPersistentData().get(KEY),origin))
                restoreValue(entity,value);
        }
        boolean updated = false;
        for (HeroUnitSave hero : HeroServerEvents.fallenHeroes) {
            if (sameOrigin(hero.economicValue,origin) || hero.uuid.equals(victim.getStringUUID())) {
                hero.economicValue = value.copy(); updated = true;
            }
        }
        if (updated && victim.level() instanceof ServerLevel level) UnitServerEvents.saveFallenHeroUnits(level);
    }
    private static boolean sameOrigin(Tag tag, UUID origin) {
        return tag instanceof CompoundTag value && valid(value) && origin.equals(value.getUUID("origin"));
    }
}
