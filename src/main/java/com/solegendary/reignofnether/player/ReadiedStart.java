package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.blocks.RTSStartBlock;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.gamemode.GameMode;
import com.solegendary.reignofnether.gamemode.GameModeServerEvents;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.startpos.StartPos;
import com.solegendary.reignofnether.startpos.StartPosSnapshotPacket;
import com.solegendary.reignofnether.startpos.StartPosServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/** A ready countdown names exact seats. A successful commit does not re-run a per-seat admission path. */
public final class ReadiedStart {
    private ReadiedStart() {}
    public record Seat(BlockPos pos, String owner, Faction faction, int color, boolean physical, boolean ready) {
        public Seat(StartPos p) { this(p.pos.immutable(), p.playerName, p.faction, p.colorId, p.isFromStartBlock, p.ready); }
    }
    public record Failure(String player, String reasonKey) {}
    record Assets(Building capitol, BlockPos origin, boolean dogPerson, List<LivingEntity> workers,
                  BuildingPlacement placement) {
        Assets prepared(BuildingPlacement building) { return new Assets(capitol, origin, dogPerson, workers, building); }
    }
    private record Bounds(BlockPos min, BlockPos max) {
        boolean overlaps(Bounds b) {
            return min.getX() <= b.max.getX() && b.min.getX() <= max.getX()
                    && min.getY() <= b.max.getY() && b.min.getY() <= max.getY()
                    && min.getZ() <= b.max.getZ() && b.min.getZ() <= max.getZ();
        }
    }
    private record Plan(Seat seat, ServerPlayer player, Faction faction, GameModeServerEvents.StartChoice choice,
                        Assets assets, Bounds bounds, List<LivingEntity> units) {}
    private static Failure failure(Seat seat, String suffix) {
        return new Failure(seat == null || seat.owner().isBlank() ? "?" : seat.owner(), "startpos.reignofnether.preflight_" + suffix);
    }

    private static boolean spaceAvailable(ServerLevel level, BlockPos origin, Bounds proposed) {
        // Native placement currently reserves exact origins globally, while physical intersections
        // are meaningful only within a dimension. Match both rules before any seat is granted.
        return BuildingServerEvents.getBuildings().stream().noneMatch(b -> b.originPos.equals(origin)
                || b.getLevel() == level && proposed.overlaps(new Bounds(b.minCorner, b.maxCorner)));
    }

    private static Failure checkAdmission(ServerLevel level, Seat seat, ServerPlayer player, Faction faction) {
        if (player == null || player.connection == null || player.serverLevel() != level
                || !ServerPlayerRequest.isCurrent(player, player.connection.connection)) return failure(seat, "player");
        if (PlayerServerEvents.rtsLocked) return new Failure(seat.owner(), "server.reignofnether.locked");
        if (PlayerServerEvents.alreadyHasSlot(player)) return new Failure(seat.owner(), "server.reignofnether.already_started");
        if (seat.physical() && !(level.getBlockState(seat.pos()).getBlock() instanceof RTSStartBlock)) return failure(seat, "position");
        if (level.getWorldBorder().getDistanceToBorder(seat.pos().getX(), seat.pos().getZ()) < 1)
            return new Failure(seat.owner(), "server.reignofnether.outside_border");
        var choice = GameModeServerEvents.previewStartChoice(player, faction, false);
        return choice == null || choice.mode() != GameMode.CLASSIC ? failure(seat, "mode") : null;
    }

    private static LivingEntity unit(EntityType<?> type, ServerLevel level, String owner, BlockPos pos, boolean worker) {
        var candidate = type == null ? null : type.create(level);
        if (!(candidate instanceof LivingEntity entity) || !(candidate instanceof Unit u)
                || worker && !(candidate instanceof WorkerUnit)) return null;
        u.setOwnerName(owner);
        entity.moveTo(pos, 0, 0);
        return entity;
    }

    /** Null means every frozen seat was committed on this server tick. No roster, account, proposal,
     * alliance, building or published unit is changed by the validation loop. */
    public static Failure tryStart(ServerLevel level, List<Seat> seats) {
        if (level == null || !level.getServer().isSameThread() || level != level.getServer().overworld()
                || PlayerServerEvents.serverLevel != level || BuildingServerEvents.getServerLevel() != level
                || seats.isEmpty() || seats.size() > StartPosSnapshotPacket.MAX_SEATS
                || !StartPosServerEvents.isPendingStart(seats)) return failure(null, "position");
        List<Plan> plans = new ArrayList<>();
        Set<UUID> players = new HashSet<>();
        Map<Building, ArrayList<BuildingBlock>> templates = new IdentityHashMap<>();
        for (Seat seat : seats) {
            if (!seat.ready() || seat.owner().isBlank()) return failure(seat, "player");
            ServerPlayer player = level.getServer().getPlayerList().getPlayerByName(seat.owner());
            Faction faction = PlayerServerEvents.resolveStartFaction(seat.faction());
            if (faction == null || faction == Factions.NONE || !Factions.PLAYABLE_FACTIONS.contains(faction.key)
                    || faction.workerEntityType == null || faction.scoutEntityType == null || faction.capitolBuilding == null)
                return failure(seat, "assets");
            Failure rejected = checkAdmission(level, seat, player, faction);
            if (rejected != null) return rejected;
            if (!players.add(player.getUUID())) return failure(seat, "player");
            var choice = GameModeServerEvents.previewStartChoice(player, faction, false);

            try {
                var building = ReignOfNetherRegistries.BUILDING.get(faction.capitolBuilding);
                if (building == null) return failure(seat, "assets");
                var blocks = templates.computeIfAbsent(building, b -> b.getRelativeBlockData(level));
                if (blocks == null || blocks.isEmpty()) return failure(seat, "assets");
                var origin = PlayerServerEvents.getBuildingOriginPos(seat.pos(), blocks);
                var bounds = new Bounds(BuildingUtils.getMinCorner(blocks).offset(origin).above(),
                        BuildingUtils.getMaxCorner(blocks).offset(origin).above());
                if (!level.getWorldBorder().isWithinBounds(bounds.min()) || !level.getWorldBorder().isWithinBounds(bounds.max())
                        || level.isOutsideBuildHeight(bounds.min()) || level.isOutsideBuildHeight(bounds.max()))
                    return failure(seat, "position");
                if (!spaceAvailable(level, origin, bounds) || plans.stream().anyMatch(p ->
                        origin.equals(p.assets().origin()) || bounds.overlaps(p.bounds()))) return failure(seat, "position");

                boolean dog = BuildingServerEvents.random.nextBoolean();
                EntityType<?> worker = ForgeRegistries.ENTITY_TYPES.getValue(faction.workerEntityType);
                var workers = new ArrayList<LivingEntity>();
                for (int i = 0; i < 5; i++) {
                    var candidate = unit(worker, level, seat.owner(), origin.offset(-1, 1, i), true);
                    if (candidate == null) return failure(seat, "assets");
                    workers.add(candidate);
                }
                var units = new ArrayList<>(workers);
                if (FogOfWarServerEvents.isEnabled()) {
                    EntityType<?> type = faction == Factions.VILLAGERS && !dog ? EntityRegistrar.SCOUT_CAT_UNIT.get()
                            : ForgeRegistries.ENTITY_TYPES.getValue(faction.scoutEntityType);
                    var candidate = unit(type, level, seat.owner(), origin.offset(0, type == EntityRegistrar.BAT_UNIT.get() ? 10 : 1, 2), false);
                    if (candidate == null) return failure(seat, "assets");
                    units.add(candidate);
                }
                plans.add(new Plan(seat, player, faction, choice, new Assets(building, origin, dog, List.copyOf(workers), null),
                        bounds, List.copyOf(units)));
            } catch (RuntimeException invalidDefinition) {
                ReignOfNether.LOGGER.warn("Cannot prepare lobby start assets for {}", faction.key, invalidDefinition);
                return failure(seat, "assets");
            }
        }

        // An ordinary Forge join may be declined. Until the entire batch has published, these exact
        // new bodies are tentative: never pay for/assign any seat, and roll back only our own spawns.
        List<LivingEntity> attempted = new ArrayList<>();
        boolean published = false;
        try {
            for (Plan p : plans) {
                for (LivingEntity entity : p.units()) {
                    attempted.add(entity);
                    if (!level.addFreshEntity(entity)) return failure(p.seat(), "assets");
                }
            }
            // Creating a native placement can emit fog notifications, so do not do it in the
            // read-only loop. Resolve every concrete capitol before creating even the first account.
            for (int i = 0; i < plans.size(); i++) {
                Plan p = plans.get(i);
                var b = BuildingUtils.getNewBuildingPlacement(p.assets().capitol(), level, p.assets().origin(),
                        net.minecraft.world.level.block.Rotation.NONE, p.seat().owner(), false);
                if (b == null || b.getLevel() != level || !p.assets().origin().equals(b.originPos)
                        || !b.ownerName.equals(p.seat().owner())) return failure(p.seat(), "assets");
                plans.set(i, new Plan(p.seat(), p.player(), p.faction(), p.choice(), p.assets().prepared(b), p.bounds(), p.units()));
            }
            if (!StartPosServerEvents.isPendingStart(seats)) return failure(null, "position");
            // Publication may run callbacks synchronously. Check the whole batch again before grants.
            for (Plan p : plans) {
                Failure denied = checkAdmission(level, p.seat(), p.player(), p.faction());
                if (denied != null) return denied;
                if (!spaceAvailable(level, p.assets().origin(), p.bounds())) return failure(p.seat(), "position");
                for (var unit : p.units()) if (!UnitServerEvents.isPublishedBody(level, unit)
                        || !p.seat().owner().equals(((Unit)unit).getOwnerName())) return failure(p.seat(), "assets");
                for (var worker : p.assets().workers()) if (((WorkerUnit)worker).getBuildRepairGoal() == null
                        || ((WorkerUnit)worker).getGatherResourceGoal() == null) return failure(p.seat(), "assets");
            }
            published = true;
        } catch (RuntimeException refused) {
            ReignOfNether.LOGGER.warn("Cannot publish lobby start units", refused);
            return failure(null, "assets");
        } finally {
            if (!published) {
                for (var entity : attempted) {
                    if (entity.isAddedToWorld()) UnitServerEvents.rollbackUncommittedProductionSpawn(entity);
                    else UnitServerEvents.rollbackRejectedUnitSpawn(entity);
                }
            }
        }

        for (Plan p : plans) GameModeServerEvents.cancelProposal(p.player());
        for (Plan p : plans) {
            try {
                if (PlayerServerEvents.grantAdmittedStart(p.player(), Vec3.atLowerCornerOf(p.seat().pos()),
                        p.faction(), p.seat().color(), p.choice(), p.assets())) continue;
                ReignOfNether.LOGGER.error("Prepared capitol was refused during commit for {}", p.seat().owner());
            } catch (RuntimeException interrupted) {
                ReignOfNether.LOGGER.error("Lobby commit interrupted for {}", p.seat().owner(), interrupted);
            }
            // Unknown third-party callbacks can change terrain or ownership during a grant. Do not
            // rewind the world, claim success, or leave tentative units behind for unadmitted seats.
            for (Plan unseated : plans) if (!PlayerServerEvents.alreadyHasSlot(unseated.player()))
                for (var unit : unseated.units()) if (unseated.seat().owner().equals(((Unit)unit).getOwnerName()))
                    UnitServerEvents.rollbackUncommittedProductionSpawn(unit);
            PlayerServerEvents.saveRTSPlayers();
            return failure(p.seat(), "interrupted");
        }
        return null;
    }
}
