package com.solegendary.reignofnether.sandbox;

import com.solegendary.reignofnether.building.BuildingClientboundPacket;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.addon.NetherConvertingAddon;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.packets.UnitSyncClientboundPacket;
import java.util.LinkedHashSet;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import static com.solegendary.reignofnether.player.PlayerServerEvents.serverLevel;

public class SandboxServer {

    public static boolean isSandboxPlayer(String playerName) {
        for (RTSPlayer rtsPlayer : PlayerServerEvents.rtsPlayers)
            if (rtsPlayer.faction == Factions.NONE && playerName.equals(rtsPlayer.name))
                return true;
        return false;
    }

    /** Match mode only; never establishes editor authority for an incoming request. */
    public static boolean isAnyoneASandboxPlayer() {
        for (RTSPlayer rtsPlayer : PlayerServerEvents.rtsPlayers)
            if (rtsPlayer.faction == Factions.NONE)
                return true;
        return false;
    }

    static boolean edit(ServerPlayer actor, Connection connection, SandboxAction action,
                        String owner, String unitName, BlockPos pos, int[] ids) {
        SandboxEditorAccess editor = SandboxEditorAccess.resolve(actor, connection);
        if (editor == null || action == null) return false;
        return switch (action) {
            case SPAWN_UNIT -> spawn(editor, owner, unitName, pos);
            case SET_UNIT_OWNER, SET_ANCHOR, REMOVE_ANCHOR, RESET_TO_ANCHOR -> {
                if (ids == null || ids.length == 0 || ids.length > SandboxServerboundPacket.MAX_SELECTION
                        || (action == SandboxAction.SET_UNIT_OWNER && !editor.mayAssignOwner(owner))
                        || (action == SandboxAction.SET_ANCHOR && !editor.editablePosition(pos))) yield false;
                var selected = new LinkedHashSet<LivingEntity>();
                for (int id : ids) {
                    LivingEntity entity = editor.unit(id);
                    if (entity == null) yield false; // mixed stale or out-of-world group: no partial edit
                    Unit unit = (Unit) entity;
                    if (action == SandboxAction.RESET_TO_ANCHOR && Unit.hasAnchor(unit)
                            && !editor.editablePosition(unit.getAnchor())) yield false;
                    selected.add(entity);
                }
                for (LivingEntity entity : selected) {
                    switch (action) {
                        case SET_UNIT_OWNER -> changeUnitOwner(entity, owner);
                        case SET_ANCHOR -> changeAnchor(entity, pos);
                        case REMOVE_ANCHOR -> clearAnchor(entity);
                        case RESET_TO_ANCHOR -> reset(entity);
                    }
                }
                yield true;
            }
            case SET_BUILDING_OWNER, REMOVE_BUILDING -> {
                BuildingPlacement placement = editor.building(pos);
                if (placement == null) yield false;
                if (action == SandboxAction.REMOVE_BUILDING)
                    yield BuildingServerEvents.getBuildings().removeIf(b -> b == placement && remove(b));
                if (!editor.mayAssignOwner(owner)) yield false;
                changeBuildingOwner(placement, owner);
                yield true;
            }
            default -> false;
        };
    }

    /** Compatibility entry point with a connection: always reauthorize, even before the legacy spawn fallback. */
    public static void spawnUnit(ServerPlayer actor, Connection connection, String owner, String unitName, BlockPos pos) {
        edit(actor, connection, SandboxAction.SPAWN_UNIT, owner, unitName, pos, new int[0]);
    }

    private static boolean spawn(SandboxEditorAccess editor, String owner, String unitName, BlockPos pos) {
        if (!editor.mayAssignOwner(owner) || !editor.editablePosition(pos) || !editor.editablePosition(pos.above()))
            return false;
        EntityType<? extends Mob> type = EntityRegistrar.getEntityType(unitName);
        if (type == null) return false;
        if (!SandboxEconomyFixture.spawn(editor.actor(), editor.connection, editor.level(), type, owner, pos))
            spawnUnmarked(editor.level(), type, owner, pos);
        return true;
    }

    private static void spawnUnmarked(ServerLevel level, EntityType<? extends Mob> type, String owner, BlockPos pos) {
        Entity entity = UnitServerEvents.spawnMob(type, level, pos, owner);
        if (entity instanceof Unit unit && (owner.isEmpty() || "Enemy".equals(owner))) unit.setAnchor(pos);
    }

    // Trusted server/command APIs. Never dispatch incoming client IDs or positions through these global helpers.
    public static void spawnUnit(String playerName, String unitName, BlockPos pos) {
        EntityType<? extends Mob> type = EntityRegistrar.getEntityType(unitName);
        if (serverLevel != null && type != null) spawnUnmarked(serverLevel, type, playerName, pos);
    }

    private static void forSelected(int[] ids, Consumer<LivingEntity> callback) {
        for (LivingEntity entity : UnitServerEvents.getAllUnits()) {
            if (!(entity instanceof Unit)) continue;
            for (int id : ids) if (entity.getId() == id) { callback.accept(entity); break; }
        }
    }

    public static void setAnchor(int[] ids, BlockPos pos) { forSelected(ids, e -> changeAnchor(e, pos)); }
    public static void resetToAnchor(int[] ids) { forSelected(ids, SandboxServer::reset); }
    public static void removeAnchor(int[] ids) { forSelected(ids, SandboxServer::clearAnchor); }
    public static void setUnitOwner(int[] ids, String owner) { forSelected(ids, e -> changeUnitOwner(e, owner)); }

    private static void changeAnchor(LivingEntity entity, BlockPos pos) {
        Unit unit = (Unit) entity;
        unit.setAnchor(pos);
        UnitSyncClientboundPacket.sendSyncAnchorPosPacket(entity, unit.getAnchor());
    }

    private static void reset(LivingEntity entity) {
        Unit unit = (Unit) entity;
        if (!Unit.hasAnchor(unit)) return;
        entity.moveTo(Vec3.atCenterOf(unit.getAnchor()).add(0, 0.5d, 0));
        entity.setHealth(entity.getMaxHealth());
        entity.removeAllEffects();
        Unit.fullResetBehaviours(unit);
    }

    private static void clearAnchor(LivingEntity entity) {
        ((Unit) entity).setAnchor(BlockPos.ZERO);
        UnitSyncClientboundPacket.sendRemoveAnchorPosPacket(entity);
    }

    private static void changeUnitOwner(LivingEntity entity, String owner) {
        Unit unit = (Unit) entity;
        unit.setOwnerName(owner);
        UnitSyncClientboundPacket.sendSyncOwnerNamePacket(unit);
    }

    public static void setBuildingOwner(BlockPos pos, String owner) {
        for (BuildingPlacement placement : BuildingServerEvents.getBuildings())
            if (placement.originPos.equals(pos)) changeBuildingOwner(placement, owner);
    }

    private static void changeBuildingOwner(BuildingPlacement placement, String owner) {
        placement.setOwnerNameFromServerEditor(owner);
        BuildingClientboundPacket.syncBuilding(placement.originPos, placement.getBlocksPlaced(),
                placement.partialBlocksDestroyed, owner, placement.scenarioRoleIndex);
    }

    public static void removeBuilding(BlockPos pos) {
        BuildingServerEvents.getBuildings().removeIf(b -> b.originPos.equals(pos) && remove(b));
    }

    private static boolean remove(BuildingPlacement b) {
        if (!b.beforePermanentAdministrativeRemoval()) return false;
        BuildingClientboundPacket.removeBuilding(b.originPos);
        NetherConvertingAddon addon = b.getBuilding().getActiveAddon(NetherConvertingAddon.class);
        if (addon != null && addon.getMaxNetherRange(b) > 0 && addon.getNetherZone(b) != null)
            addon.getNetherZone(b).startRestoring();
        b.onRemoved(BuildingPlacement.RemovalReason.SANDBOX);
        return true;
    }
}
