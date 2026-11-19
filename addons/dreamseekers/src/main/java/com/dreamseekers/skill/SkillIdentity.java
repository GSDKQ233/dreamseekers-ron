// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Tracks actual objects and ownership epochs, including same-tick A->B->A on core units. */
public final class SkillIdentity {
    private SkillIdentity() { }
    private static final Map<Entity, Epoch> EPOCHS = new WeakHashMap<>();
    private static final class Epoch {
        String owner; long value;
        Epoch(String owner) { this.owner=owner; }
    }
    public record Stamp(LivingEntity entity, ServerLevel level, UUID uuid, String owner,
                        long epoch, long dreamEpoch) {
        public boolean valid() { return entity.isAlive() && identityValid(); }
        /** Post-hit receipts may accept death caused by their own write, never removal/transfer/replacement. */
        public boolean identityValid() {
            if (entity.isRemoved() || entity.level()!=level
                    || level.getEntity(uuid)!=entity || !(entity instanceof Unit unit)
                    || !owner.equals(unit.getOwnerName()) || owner.isBlank()) return false;
            Epoch current=EPOCHS.get(entity);
            return current!=null && current.value==epoch
                    && (!(entity instanceof SkillUnit dream) || dream.rts().ownershipRevision==dreamEpoch);
        }
    }
    public static Stamp capture(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !(entity instanceof Unit unit))
            throw new IllegalArgumentException("Server owned Unit required");
        observeOwnerMutation(entity);
        Epoch current=EPOCHS.computeIfAbsent(entity,e->new Epoch(unit.getOwnerName()));
        return new Stamp(entity,level,entity.getUUID(),unit.getOwnerName(),current.value,
                entity instanceof SkillUnit dream?dream.rts().ownershipRevision:0);
    }
    /** Called only after a synced-data write; untracked/wild entities are strict no-ops. */
    public static void observeOwnerMutation(Entity entity) {
        if (entity.level().isClientSide() || !(entity instanceof Unit unit)) return;
        Epoch current=EPOCHS.get(entity);
        if (current!=null && !current.owner.equals(unit.getOwnerName())) {
            current.owner=unit.getOwnerName();current.value++;
            if (entity instanceof LivingEntity living) SkillEffects.clearForOwnerChange(living);
        }
    }
    public static void forget(Entity entity) { EPOCHS.remove(entity); }
}
