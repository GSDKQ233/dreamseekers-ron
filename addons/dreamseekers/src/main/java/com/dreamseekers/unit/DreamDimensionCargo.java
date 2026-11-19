package com.dreamseekers.unit;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.unit.packets.UnitSyncClientboundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Live transfer only. Ordinary NBT remains ledger-settled and never restores valued cargo. */
final class DreamDimensionCargo {
    private static final Set<Mob> TRANSFERRING = Collections.newSetFromMap(new IdentityHashMap<>());

    private DreamDimensionCargo() { }

    static Entity changeDimension(DreamRtsUnit source, ServerLevel destination, Supplier<Entity> original) {
        Mob body = source.mob();
        if (!(body.level() instanceof ServerLevel previous) || !previous.getServer().isSameThread()
                || destination == null || destination.getServer() != previous.getServer()
                || body.isRemoved() || !body.isAddedToWorld()
                || previous.getEntity(body.getUUID()) != body || !TRANSFERRING.add(body)) return null;
        UUID uuid = body.getUUID();
        try {
            Entity result = original.get();
            // Forge's normal reposition callback copies NBT into a newly admitted same-UUID body,
            // then retires the original with CHANGED_DIMENSION. Null/cancel/same-body never pays.
            if (result == body || !(result instanceof DreamRtsUnit target)
                    || result.getType() != body.getType() || result.level() != destination
                    || !uuid.equals(result.getUUID()) || !uuid.equals(body.getUUID())
                    || body.level() != previous || body.getRemovalReason() != Entity.RemovalReason.CHANGED_DIMENSION
                    || !result.isAlive() || result.isRemoved() || !result.isAddedToWorld())
                return result;
            // Forge marks added only after successful UUID/section admission. A newly loaded
            // destination may still be FULL/HIDDEN: its visible lookup is published later.
            // Never accept a conflicting published identity, but do not discard valid cold-chunk cargo.
            Entity byUuid = destination.getEntity(uuid);
            Entity byId = destination.getEntity(result.getId());
            if ((byUuid != null && byUuid != result) || (byId != null && byId != result)) return result;

            var moved = new ArrayList<ItemStack>();
            for (ItemStack stack : source.getItems())
                if (!stack.isEmpty() && DreamRtsUnit.isSettledRtsResource(stack)) moved.add(stack.copy());
            if (!moved.isEmpty()) {
                // No callbacks between remove and add: the retired source cannot deliver twice.
                source.getItems().removeIf(stack -> !stack.isEmpty() && DreamRtsUnit.isSettledRtsResource(stack));
                target.getItems().addAll(moved);
                // Join may have published the pre-handoff, empty cargo. Publish the committed state.
                try {
                    UnitSyncClientboundPacket.sendSyncResourcesPacket(target);
                } catch (RuntimeException publicationFailure) {
                    ReignOfNether.LOGGER.warn("Dreamseekers dimension cargo committed; resource sync failed for {}", uuid, publicationFailure);
                }
            }
            return result;
        } finally {
            TRANSFERRING.remove(body);
        }
    }
}
