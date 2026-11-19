package com.solegendary.reignofnether.building.production;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.resources.ResourceSources;
import com.solegendary.reignofnether.resources.kill.EconomicUnitIdentity;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.unit.UnitActionItem;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** One-shot, server-thread handoff for native production. No wall-clock tasks or saved orders. */
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID)
public final class NativeProductionRally {
    private static final int MAX_PENDING = 16_384;
    private static final Map<Entity, Pending> PENDING = new IdentityHashMap<>();
    private record Pending(ProductionPlacement host, ServerLevel level, LivingEntity entity, int id,
                           UUID uuid, String owner, EconomicUnitIdentity.Stamp ownerStamp, long capture,
                           long readyAt, long expiresAt, List<BlockPos> points, LivingEntity target,
                           UUID targetUuid, int targetId, boolean attackMove, ResourceName resource) { }
    private NativeProductionRally() { }

    public static void schedule(ProductionPlacement host, Unit unit, List<BlockPos> points,
                                LivingEntity target, boolean attackMove, ResourceName resource) {
        if (!(host.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !(unit instanceof LivingEntity entity) || entity.level() != level
                || !entity.isAddedToWorld() || !entity.isAlive() || entity.isRemoved()
                || !host.ownerName.equals(unit.getOwnerName()) || unit.getOwnerName().isBlank()
                || host.getCaptureRevision() < 0 || PENDING.size() >= MAX_PENDING) return;
        PENDING.put(entity, new Pending(host, level, entity, entity.getId(), entity.getUUID(), unit.getOwnerName(),
                EconomicUnitIdentity.capture(entity), host.getCaptureRevision(), level.getGameTime() + 1,
                level.getGameTime() + 40, points.stream().map(BlockPos::immutable).toList(), target,
                target == null ? null : target.getUUID(), target == null ? -1 : target.getId(), attackMove, resource));
    }

    /** Called only for the exact source at a verified command/reset/removal boundary. */
    public static void cancel(Entity entity) {
        if (entity != null && entity.level() instanceof ServerLevel level && level.getServer().isSameThread())
            PENDING.remove(entity);
    }

    @SubscribeEvent public static void left(EntityLeaveLevelEvent event) { cancel(event.getEntity()); }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void stopping(ServerStoppingEvent event) {
        PENDING.values().removeIf(pending -> pending.level().getServer() == event.getServer());
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || !level.getServer().isSameThread()) return;
        // Dispatch can invoke native resets and mutate the map; iterate a stable snapshot.
        for (Pending pending : List.copyOf(PENDING.values())) {
            if (pending.level() != level || PENDING.get(pending.entity()) != pending) continue;
            LivingEntity entity = pending.entity(); Unit unit = (Unit) entity;
            ProductionPlacement host = pending.host();
            if ((!host.isBuilt && !host.allowProdWhileBuilding) || host.isDestroyedServerside
                    || host.shouldBeDestroyed() || host.getLevel() != level
                    || !BuildingServerEvents.getBuildings().contains(host)
                    || host.getCaptureRevision() != pending.capture() || !host.ownerName.equals(pending.owner())
                    || entity.level() != level || !entity.isAlive() || entity.isRemoved()
                    || entity.getId() != pending.id() || !entity.getUUID().equals(pending.uuid())
                    || !pending.ownerStamp().sameOwner() || !unit.getOwnerName().equals(pending.owner())
                    || level.getGameTime() > pending.expiresAt()) { PENDING.remove(entity); continue; }
            if (level.getGameTime() < pending.readyAt() || level.getEntity(pending.uuid()) != entity
                    || level.getEntity(pending.id()) != entity) continue;
            // An immediate command may not have reached its authorized dispatch/reset boundary yet.
            // Invalid foreign requests must not consume the pending rally.
            if (UnitServerEvents.hasPendingImmediateAction(pending.id())) continue;
            PENDING.remove(entity); // Consume before our own command invokes native cancellation hooks.
            LivingEntity target = pending.target();
            if (target != null) {
                if (!target.isAlive() || target.isRemoved() || target.level() != level
                        || target.getId() != pending.targetId() || !target.getUUID().equals(pending.targetUuid())
                        || level.getEntity(pending.targetId()) != target || level.getEntity(pending.targetUuid()) != target) continue;
                boolean hostile = ResourceSources.isHuntableAnimal(target) || target instanceof Unit other
                        && !AlliancesServerEvents.isAlliedOrOwned(other.getOwnerName(), pending.owner());
                new UnitActionItem(pending.owner(), hostile ? UnitAction.ATTACK : UnitAction.FOLLOW,
                        pending.targetId(), new int[]{pending.id()}, BlockPos.ZERO, BlockPos.ZERO).action(level);
            } else if (!pending.points().isEmpty()) {
                UnitAction action = pending.attackMove() ? UnitAction.ATTACK_MOVE : UnitAction.MOVE;
                new UnitActionItem(pending.owner(), action, -1, new int[]{pending.id()},
                        pending.points().get(0), BlockPos.ZERO).action(level);
                for (int i = 1; i < pending.points().size(); i++)
                    UnitServerEvents.addActionItem(pending.owner(), action, -1, new int[]{pending.id()},
                            pending.points().get(i), BlockPos.ZERO, true);
                if (!pending.attackMove() && pending.resource() != ResourceName.NONE
                        && unit instanceof WorkerUnit worker && worker.getGatherResourceGoal() != null)
                    worker.getGatherResourceGoal().setTargetResourceName(pending.resource());
            }
        }
    }
}
