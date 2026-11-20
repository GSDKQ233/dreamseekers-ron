package com.solegendary.reignofnether.survival;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.buildings.placements.PortalPlacement;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.items.ItemServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.PlayerClientboundPacket;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.sounds.SoundAction;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import com.solegendary.reignofnether.time.TimeUtils;
import com.solegendary.reignofnether.tutorial.TutorialServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;

import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.solegendary.reignofnether.time.TimeUtils.getWaveSurvivalTimeModifier;

public class SurvivalServerEvents {

    private static boolean isEnabled = false;
    @Nullable public static Wave currentWave = null;
    public static Wave nextWave = Wave.getWave(1);
    private static WaveDifficulty difficulty = WaveDifficulty.BEGINNER;
    private static final ArrayList<WaveEnemy> enemies = new ArrayList<>();
    public static final String ENEMY_OWNER_NAME = "Enemy";
    private static boolean wavesFinished;
    // IDs with a native entity save but no confirmed local publication yet. Unloaded is not dead.
    private static final Set<UUID> awaitingEnemies = new HashSet<>();
    private static final Map<BlockPos, SurvivalSaveData.PortalState> restoredPortals = new HashMap<>();
    private static final Set<UUID> retiredEnemies = new HashSet<>();
    private static final Set<BlockPos> retiredPortals = new HashSet<>();
    private static final Set<LivingEntity> retireOnPublication = Collections.newSetFromMap(new IdentityHashMap<>());
    // Native join fires BEFORE addFreshEntity has returned. Reconcile only fresh join candidates
    // after this specific synchronous wave callback, never a global headcount or a chunk reload.
    private static final class Dispatch {
        final Set<LivingEntity> joins = Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<LivingEntity, Entity.RemovalReason> departed = new IdentityHashMap<>();
    }
    @Nullable private static Dispatch dispatchBirths;
    @Nullable private static Vec3 lastKill;
    public static Faction lastFaction = Factions.NONE;

    public static final ArrayList<WavePortal> portals = new ArrayList<>();

    public static final long TICK_INTERVAL = 10;
    private static long lastTime = -1;
    private static long lastEnemyCount = 0;
    private static long ticks = 0;

    private static final ArrayList<BuildingPlacement> lastPortals = new ArrayList<>();

    private static ServerLevel serverLevel = null;

    public static WaveDifficulty getDifficulty() {
        return difficulty;
    }

    static WaveCursor cursor() {
        return new WaveCursor(nextWave.number, currentWave == null ? 0 : currentWave.number, wavesFinished);
    }

    private static void apply(WaveCursor cursor) {
        nextWave = Wave.getWave(cursor.next());
        currentWave = cursor.active() == 0 ? null : Wave.getWave(cursor.active());
        wavesFinished = cursor.finished();
    }

    public static void saveData(ServerLevel level) {
        if (serverLevel != level) return;
        stageSave(level);
        level.getDataStorage().save();
    }

    private static int clientStatus() { return SurvivalResult.current().clientStatus(cursor()); }

    private static void stageSave(ServerLevel level) {
        SurvivalSaveData data = SurvivalSaveData.getInstance(level);
        data.isEnabled = isEnabled;
        data.setCursor(cursor());
        data.result = SurvivalResult.current();
        data.retiredEnemies.clear(); data.retiredEnemies.addAll(retiredEnemies);
        data.retiredPortals.clear(); data.retiredPortals.addAll(retiredPortals);
        data.difficulty = difficulty;
        data.randomSeed = Wave.randomSeed;
        data.activeEnemies.clear();
        data.activePortals.clear();
        data.lastKill = null;
        if (currentWave != null) {
            data.activeEnemies.addAll(awaitingEnemies);
            for (WaveEnemy e : enemies) {
                LivingEntity body = e.getEntity();
                if (body.isAlive() && presentIn(level, body))
                    data.activeEnemies.add(body.getUUID());
            }
            data.activePortals.putAll(restoredPortals);
            for (WavePortal portal : portals)
                data.activePortals.put(portal.portal.originPos,
                        new SurvivalSaveData.PortalState(portal.wave.number, portal.remainingInitialPop()));
            data.portalSpawnTicks = WavePortal.spawnClock();
            data.lastKill = lastKill;
        }
        data.setDirty();
    }

    // Detach without killing or changing the world. reset() is for an intentional match reset only.
    private static void detach() {
        enemies.clear(); portals.clear(); lastPortals.clear();
        awaitingEnemies.clear(); restoredPortals.clear(); lastKill = null;
        retiredEnemies.clear(); retiredPortals.clear(); retireOnPublication.clear(); dispatchBirths = null;
        SurvivalResult.bind(SurvivalResult.OPEN);
        currentWave = null; wavesFinished = false; isEnabled = false; serverLevel = null;
        ticks = 0; lastEnemyCount = 0; lastTime = -1; lastFaction = Factions.NONE;
        WavePortal.restoreSpawnClock(0);
    }

    @SubscribeEvent
    public static void loadWaveData(ServerStartedEvent evt) {
        detach();
        ServerLevel level = evt.getServer().getLevel(Level.OVERWORLD);
        if (level == null) return;
        serverLevel = level;
        SurvivalSaveData survivalData = SurvivalSaveData.getInstance(level);
        isEnabled = survivalData.isEnabled;
        Wave.randomSeed = survivalData.randomSeed;
        Wave.reseedWaves();
        apply(survivalData.cursor());
        difficulty = survivalData.difficulty;
        SurvivalResult.bind(survivalData.result);
        retiredEnemies.addAll(survivalData.retiredEnemies); retiredPortals.addAll(survivalData.retiredPortals);
        if (isEnabled()) {
            if (currentWave != null) {
                awaitingEnemies.addAll(survivalData.activeEnemies);
                restoredPortals.putAll(survivalData.activePortals);
                lastKill = survivalData.lastKill;
                WavePortal.restoreSpawnClock(survivalData.portalSpawnTicks);
            }
            SurvivalClientboundPacket.setWaveRandomSeed(Wave.randomSeed);
            SurvivalClientboundPacket.enableAndSetDifficulty(difficulty);
            SurvivalClientboundPacket.setWaveNumber(clientStatus());
        }
        // Spawn chunks can publish before ServerStartedEvent. Exact retirement also applies
        // to old chunk saves that appear after an explicit match reset.
        if (isEnabled || !retiredEnemies.isEmpty()) level.getAllEntities().forEach(SurvivalServerEvents::trackEnemy);
        ReignOfNether.LOGGER.info("Loaded survival: enabled={}, cursor={}, difficulty={}", isEnabled, cursor(), difficulty);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerStopping(ServerStoppingEvent evt) {
        ServerLevel level = evt.getServer().overworld();
        if (serverLevel != level || !isEnabled) return;
        // Before the building/unit shutdown handlers detach their lists. A last kill between
        // scheduler ticks still settles before vanilla saves entities, buildings and player rolls.
        if (SurvivalResult.isAfterMatch()) {
            retirePublishedEnemies(level); saveData(level); return;
        }
        refreshThreats(level);
        if (!settleIfEmpty(level)) saveData(level);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent evt) { detach(); }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END || evt.level.isClientSide() || evt.level.dimension() != Level.OVERWORLD)
            return;

        serverLevel = (ServerLevel) evt.level;

        retirePublishedEnemies(serverLevel);
        if (!isEnabled() || SurvivalResult.isAfterMatch())
            return; // explicit unscored free play: no dusk, night, enemy AI, clear, or new rewards

        ticks += 1;
        if (ticks % TICK_INTERVAL != 0)
            return;

        if (ticks % 100 == 0) redeemPendingRewards(serverLevel);

        long normTime = TimeUtils.normaliseTime(evt.level.getDayTime());

        if (!isStarted()) {
            setToGameStartTime();
            return;
        }

        if (lastTime >= 0) {
            if (lastTime <= TimeUtils.DUSK - 600 && normTime > TimeUtils.DUSK - 600) {
                PlayerServerEvents.sendMessageToAllPlayers("survival.reignofnether.dusksoon", true);
                SoundClientboundPacket.playSoundForAllPlayers(SoundAction.RANDOM_CAVE_AMBIENCE);
            }
            if (lastTime <= TimeUtils.DUSK && normTime > TimeUtils.DUSK) {
                PlayerServerEvents.sendMessageToAllPlayers("survival.reignofnether.dusk", true);
                SoundClientboundPacket.playSoundForAllPlayers(SoundAction.RANDOM_CAVE_AMBIENCE);
                setToStartingNightTime();
            }
            if (lastTime <= TimeUtils.DUSK + getWaveSurvivalTimeModifier(difficulty) + 50 &&
                    normTime > TimeUtils.DUSK + getWaveSurvivalTimeModifier(difficulty) + 50) {
                startNextWave((ServerLevel) evt.level);
            }
            if (lastTime <= TimeUtils.DAWN && normTime > TimeUtils.DAWN) {
                PlayerServerEvents.sendMessageToAllPlayers("survival.reignofnether.dawn", true);
                setToStartingDayTime();
            }
        }

        ServerLevel level = (ServerLevel) evt.level;
        refreshThreats(level);
        int enemyCount = threatCount();
        if (enemyCount == 0) {
            settleIfEmpty(level);
        } else if (enemyCount < lastEnemyCount && enemyCount <= 3) {
            if (enemyCount == 1)
                PlayerServerEvents.sendMessageToAllPlayers("survival.reignofnether.remaining_enemies_one");
            else
                PlayerServerEvents.sendMessageToAllPlayers("survival.reignofnether.remaining_enemies", false, enemyCount);
        }
        for (WaveEnemy enemy : List.copyOf(enemies)) {
            LivingEntity body = enemy.getEntity();
            if (level.getEntity(body.getUUID()) == body) enemy.tick(TICK_INTERVAL);
        }
        for (WavePortal portal : portals)
            portal.tick(TICK_INTERVAL);

        lastTime = normTime;
        lastEnemyCount = enemyCount;
    }

    private static int threatCount() { return enemies.size() + awaitingEnemies.size() + portals.size(); }

    private static boolean presentIn(ServerLevel level, LivingEntity body) {
        if (body.level() != level || body.isRemoved()) return false;
        Entity visible = level.getEntity(body.getUUID());
        // addFreshEntity can succeed before the chunk enters the public UUID lookup. Do not lose
        // that body, repeat its wave, or run its AI ahead of chunk visibility. A vetoed fresh join
        // never received onAddedToWorld; a different visible lifetime is never this body.
        return visible == body || (visible == null && body.isAddedToWorld());
    }

    private static void refreshThreats(ServerLevel level) { refreshThreats(level, null); }

    private static void refreshThreats(ServerLevel level, @Nullable Wave dispatched) {
        enemies.removeIf(e -> {
            LivingEntity body = e.getEntity();
            if (!presentIn(level, body)) return true;
            awaitingEnemies.remove(body.getUUID());
            if (!body.isAlive() || !ENEMY_OWNER_NAME.equals(e.unit.getOwnerName())) {
                if (body.isDeadOrDying() && currentWave != null) lastKill = body.position();
                return true;
            }
            return false;
        });
        List<BuildingPlacement> currentPortals = new ArrayList<>();
        for (BuildingPlacement b : BuildingServerEvents.getBuildings()) {
            if (b.getLevel() == level && ENEMY_OWNER_NAME.equals(b.ownerName) && b instanceof PortalPlacement portal
                    && !isRetiredWavePortal(portal)) {
                currentPortals.add(b);
                if (!lastPortals.contains(b)) {
                    var state = restoredPortals.remove(b.originPos);
                    Wave wave = state != null ? Wave.getWave(state.wave()) : dispatched != null ? dispatched
                            : currentWave != null ? currentWave : nextWave;
                    var resumed = new WavePortal(portal, wave);
                    if (state != null) resumed.restoreInitialPop(state.remainingInitialPop());
                    portals.add(resumed);
                }
            }
        }
        // BuildingSaveData restores the entire authoritative placement list before any level tick.
        restoredPortals.clear();
        portals.removeIf(p -> !currentPortals.contains(p.getPortal()));
        lastPortals.clear();
        lastPortals.addAll(currentPortals);
    }

    @SubscribeEvent
    public static void onRegisterCommand(RegisterCommandsEvent evt) {
        evt.getDispatcher().register(Commands.literal("debug-end-wave")
                .executes((command) -> {
                    if (command.getSource().getPlayer() != null) {
                        String name = command.getSource().getPlayer().getName().getString();
                        if (!ResearchServerEvents.playerHasCheat(name, "thereisnospoon"))
                            return 0;
                    }
                    return endCurrentWave();
                }));
        evt.getDispatcher().register(Commands.literal("debug-next-night")
                .executes((command) -> {
                    if (command.getSource().getPlayer() != null) {
                        String name = command.getSource().getPlayer().getName().getString();
                        if (!ResearchServerEvents.playerHasCheat(name, "thereisnospoon"))
                            return 0;
                    }
                    if (!isEnabled() || SurvivalResult.isAfterMatch())
                        return 0;
                    PlayerServerEvents.sendMessageToAllPlayers("Advancing to next night and wave");
                    serverLevel.setDayTime(12450);
                    return 1;
                }));
    }

    public static int endCurrentWave() {
        if (!isEnabled() || SurvivalResult.isAfterMatch())
            return 0;
        PlayerServerEvents.sendMessageToAllPlayers("Ending current wave");
        ArrayList<WaveEnemy> enemiesCopy = new ArrayList<>(enemies);
        for (WaveEnemy enemy : enemiesCopy)
            enemy.getEntity().kill();
        ArrayList<WavePortal> portalsCopy = new ArrayList<>(portals);
        for (WavePortal portal : portalsCopy)
            portal.portal.destroy(serverLevel);
        return 1;
    }

    public static void enable(WaveDifficulty diff) {
        if (TutorialServerEvents.isEnabled())
            return;
        if (!isEnabled()) {
            reset();
            lastEnemyCount = 0;
            difficulty = diff;
            isEnabled = true;
            // A second formal seat may have been admitted before the initiating client finished
            // the difficulty handshake. Login/spectating by itself is never an alliance mutation.
            var seats = List.copyOf(PlayerServerEvents.rtsPlayers);
            for (int i = 0; i < seats.size(); i++)
                for (int j = i + 1; j < seats.size(); j++)
                    if (!AlliancesServerEvents.isAllied(seats.get(i).name, seats.get(j).name))
                        AlliancesServerEvents.addAlliance(seats.get(i).name, seats.get(j).name);
            SurvivalClientboundPacket.enableAndSetDifficulty(difficulty);
            if (serverLevel != null)
                saveData(serverLevel);
        }
    }

    public static void reset() {
        for (WaveEnemy enemy : List.copyOf(enemies))
            enemy.getEntity().kill();
        ArrayList<WavePortal> portalsCopy = new ArrayList<>(portals);
        for (WavePortal portal : portalsCopy)
            portal.portal.destroy(serverLevel);
        difficulty = WaveDifficulty.EASY;
        isEnabled = false;
        portals.clear(); lastPortals.clear(); enemies.clear();
        awaitingEnemies.clear(); restoredPortals.clear(); currentWave = null; lastKill = null;
        // Keep unresolved terminal cleanup IDs across a new round; never count an unloaded
        // body from the scored match as a fresh birth in the next one.
        dispatchBirths = null;
        SurvivalResult.bind(SurvivalResult.OPEN);
        wavesFinished = false; lastEnemyCount = 0; ticks = 0;
        WavePortal.restoreSpawnClock(0);
        Wave.randomSeed = System.currentTimeMillis();
        Wave.reseedWaves();
        nextWave = Wave.getWave(1);
        SurvivalClientboundPacket.setWaveRandomSeed(Wave.randomSeed);
        lastTime = -1;
        if (serverLevel != null)
            saveData(serverLevel);
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (isEnabled()) {
            SurvivalClientboundPacket.setWaveRandomSeed(Wave.randomSeed);
            SurvivalClientboundPacket.setWaveNumber(clientStatus());
            SurvivalClientboundPacket.enableAndSetDifficulty(difficulty);
            if (SurvivalResult.isAfterMatch()) PlayerClientboundPacket.syncRtsGameTime(SurvivalResult.current().elapsed());
            // Alliance membership is established by formal admission, never by joining as a viewer.
        }
    }

    private static void trackEnemy(Entity entity) {
        if (entity.level() != serverLevel || !(entity instanceof Unit unit) || !(entity instanceof LivingEntity body)
                || !ENEMY_OWNER_NAME.equals(unit.getOwnerName())) return;
        if (retiredEnemies.contains(body.getUUID())) {
            retireOnPublication.add(body);
        } else if (isEnabled && !SurvivalResult.isAfterMatch() && enemies.stream().noneMatch(e -> e.getEntity() == body)) {
            enemies.add(new WaveEnemy(unit));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityJoin(EntityJoinLevelEvent evt) {
        if (evt.getLevel() != serverLevel || !isEnabled && retiredEnemies.isEmpty()) return;
        if (dispatchBirths != null && !evt.loadedFromDisk() && evt.getEntity() instanceof LivingEntity body
                && body instanceof Unit) dispatchBirths.joins.add(body);
        trackEnemy(evt.getEntity());
    }

    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent evt) {
        if (!isEnabled || evt.getLevel() != serverLevel || !(evt.getEntity() instanceof LivingEntity body)) return;
        Entity.RemovalReason reason = body.getRemovalReason();
        // Unlike a pre-publication join notification, a native leave witnesses a real lifetime.
        // Keep its receipt even when it was removed before the faction callback returned.
        if (dispatchBirths != null && dispatchBirths.joins.contains(body) && body instanceof Unit unit
                && ENEMY_OWNER_NAME.equals(unit.getOwnerName()) && reason != null)
            dispatchBirths.departed.put(body, reason);
        if (!enemies.removeIf(e -> e.getEntity() == body) && !awaitingEnemies.contains(body.getUUID())) return;
        if (currentWave != null && body instanceof Unit unit && ENEMY_OWNER_NAME.equals(unit.getOwnerName())
                && (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK
                || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER) && !body.isDeadOrDying()) {
            awaitingEnemies.add(body.getUUID());
        } else {
            awaitingEnemies.remove(body.getUUID());
            if (currentWave != null && reason == Entity.RemovalReason.KILLED) lastKill = body.position();
        }
    }

    public static long getDayLength() {
        return 12000 - getWaveSurvivalTimeModifier(difficulty);
    }

    public static void setToStartingDayTime() {
        serverLevel.setDayTime(TimeUtils.DAWN + getWaveSurvivalTimeModifier(difficulty));
    }

    public static void setToStartingNightTime() {
        serverLevel.setDayTime(TimeUtils.DUSK + getWaveSurvivalTimeModifier(difficulty));
    }

    public static void setToGameStartTime() {
        serverLevel.setDayTime(TimeUtils.DUSK + getWaveSurvivalTimeModifier(difficulty) + 60);
    }

    public static boolean isEnabled() { return isEnabled; }

    public static boolean isStarted() {
        for (RTSPlayer player : PlayerServerEvents.rtsPlayers)
            if (BuildingUtils.getTotalCompletedBuildingsOwned(false, player.name) > 0)
                return true;
        return false;
    }

    public static List<WaveEnemy> getCurrentEnemies() {
        return enemies;
    }

    public static int getTotalEnemyPopulation() {
        int pop = 0;
        for (WaveEnemy waveEnemy : getCurrentEnemies())
            pop += waveEnemy.unit.getCost().population;
        return pop;
    }

    public static boolean isWaveInProgress() { return currentWave != null; }

    // Triggered once at nightfall. An issued active wave is never spawned again on load;
    // earlier nights may still overlap normally. There is no thirty-first encounter.
    public static void startNextWave(ServerLevel level) {
        if (!isEnabled || level != serverLevel || SurvivalResult.isAfterMatch() || dispatchBirths != null) return;
        WaveCursor before = cursor();
        if (!before.canStart()) return;
        refreshThreats(level); // restore older portals with their original wave, never the candidate
        Set<PortalPlacement> oldPortals = Collections.newSetFromMap(new IdentityHashMap<>());
        for (WavePortal p : portals) oldPortals.add(p.portal);
        Set<UUID> oldBodies = new HashSet<>(awaitingEnemies);
        for (WaveEnemy e : enemies) oldBodies.add(e.getEntity().getUUID());
        Dispatch births = new Dispatch();
        Wave candidate = nextWave;
        Faction previousFaction = lastFaction;
        dispatchBirths = births;
        boolean sameDispatch;
        try {
            candidate.start(level); // keep the committed cursor visible to nested saves/callbacks
        } catch (RuntimeException ex) {
            ReignOfNether.LOGGER.error("Survival wave {} dispatch interrupted; reconciling actual publications", candidate.number, ex);
        } finally {
            sameDispatch = dispatchBirths == births;
            if (sameDispatch) dispatchBirths = null;
        }
        if (!sameDispatch || !isEnabled || level != serverLevel) return;
        if (SurvivalResult.isAfterMatch()) {
            // A synchronous publication listener may finish the match midway through a dispatch.
            // Retire later products too, without ever committing its competitive wave cursor.
            for (LivingEntity body : births.joins) {
                if (body instanceof Unit unit && ENEMY_OWNER_NAME.equals(unit.getOwnerName())
                        && !oldBodies.contains(body.getUUID())) {
                    retiredEnemies.add(body.getUUID()); retireOnPublication.add(body);
                }
            }
            for (BuildingPlacement b : BuildingServerEvents.getBuildings())
                if (b instanceof PortalPlacement p && p.getLevel() == level && ENEMY_OWNER_NAME.equals(p.ownerName)
                        && !oldPortals.contains(p)) retiredPortals.add(p.originPos);
            retirePublishedEnemies(level); saveData(level);
            return;
        }
        boolean published = false;
        Vec3 dispatchKill = null;
        for (LivingEntity body : births.joins) {
            if (!retiredEnemies.contains(body.getUUID()) && !oldBodies.contains(body.getUUID()) && body.isAlive() && presentIn(level, body)
                    && body instanceof Unit unit && ENEMY_OWNER_NAME.equals(unit.getOwnerName())) {
                trackEnemy(body); // tolerate a producer assigning owner after addFreshEntity
                published = true;
            } else if (!oldBodies.contains(body.getUUID()) && !retiredEnemies.contains(body.getUUID())) {
                Entity.RemovalReason reason = births.departed.get(body);
                if (reason != null) {
                    published = true;
                    if (reason == Entity.RemovalReason.KILLED) dispatchKill = body.position();
                    if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER)
                        awaitingEnemies.add(body.getUUID());
                }
            }
        }
        refreshThreats(level, candidate);
        published |= portals.stream().anyMatch(p -> !oldPortals.contains(p.portal));
        if (published && (before.active() == 0 || dispatchKill != null)) lastKill = dispatchKill;
        if (!published) lastFaction = previousFaction;
        apply(before.afterDispatch(published));
        saveData(level);
        SurvivalClientboundPacket.setWaveNumber(clientStatus());
    }

    public static void waveCleared(ServerLevel level) {
        if (!isEnabled || level != serverLevel || SurvivalResult.isAfterMatch() || currentWave == null) return;
        refreshThreats(level);
        settleIfEmpty(level);
    }

    private static boolean settleIfEmpty(ServerLevel level) {
        if (SurvivalResult.isAfterMatch() || dispatchBirths != null || currentWave == null || threatCount() != 0) return false;
        apply(cursor().clear()); // consume the one live claim before drops or notifications
        Vec3 dropAt = lastKill;
        lastKill = null;
        if (dropAt != null)
            for (RTSPlayer seat : List.copyOf(PlayerServerEvents.rtsPlayers))
                if (seat.pendingSurvivalRewards.size() < seat.itemDropQueue.size())
                    seat.pendingSurvivalRewards.addLast(dropAt);
        // Record the consumed wave and the original seats' entitlements BEFORE producing items.
        stageSave(level);
        PlayerServerEvents.saveRTSPlayers();
        SurvivalClientboundPacket.setWaveNumber(clientStatus());
        PlayerServerEvents.sendMessageToAllPlayers(wavesFinished
                ? "survival.reignofnether.waves_complete" : "survival.reignofnether.wave_cleared", true);
        SoundClientboundPacket.playSoundForAllPlayers(SoundAction.ALLY);
        redeemPendingRewards(level);
        return true;
    }

    // At most one owed draw per active seat per pass. A veto does not block another seat,
    // poll the deterministic item queue, reopen the wave or invent a replacement position.
    private static void redeemPendingRewards(ServerLevel level) {
        if (SurvivalResult.isAfterMatch()) return;
        boolean changed = false;
        for (RTSPlayer seat : List.copyOf(PlayerServerEvents.rtsPlayers)) {
            Vec3 pos = seat.pendingSurvivalRewards.peekFirst();
            if (pos != null && level.hasChunkAt(BlockPos.containing(pos))
                    && ItemServerEvents.redeemNextSurvivalReward(seat, level, pos)) changed = true;
        }
        if (changed) PlayerServerEvents.saveRTSPlayers();
    }

    /** Commit the scored result before retiring a single threat or sending any UI. */
    public static boolean finish(ServerLevel level, SurvivalResult.Kind kind, Set<String> winners) {
        if (level == null || level != serverLevel || kind == SurvivalResult.Kind.IN_PROGRESS
                || SurvivalResult.isAfterMatch()) return false;
        // A formally committed Survival session can lose its last seat before difficulty arrived.
        // Bind its result too; it must not revert to Classic on the next tick or reload.
        refreshThreats(level);
        if (!SurvivalResult.finishOnce(kind, winners, PlayerServerEvents.rtsGameTicks)) return false;
        isEnabled = true;
        retiredEnemies.addAll(awaitingEnemies);
        for (WaveEnemy e : enemies) {
            LivingEntity body = e.getEntity();
            retiredEnemies.add(body.getUUID());
            retireOnPublication.add(body);
        }
        for (WavePortal p : portals) retiredPortals.add(p.portal.originPos);
        awaitingEnemies.clear(); restoredPortals.clear(); enemies.clear(); portals.clear(); lastPortals.clear();
        currentWave = null; lastKill = null; lastEnemyCount = 0;
        // No new or deferred competitive item drops in postgame free play.
        for (RTSPlayer p : PlayerServerEvents.rtsPlayers) p.pendingSurvivalRewards.clear();
        for (RTSPlayer p : PlayerServerEvents.postGameRtsPlayers) p.pendingSurvivalRewards.clear();
        stageSave(level);
        PlayerServerEvents.saveRTSPlayers();
        retirePublishedEnemies(level);
        SurvivalClientboundPacket.setWaveNumber(clientStatus());
        return true;
    }

    private static void retirePublishedEnemies(ServerLevel level) {
        if (retireOnPublication.isEmpty()) return;
        var ready = List.copyOf(retireOnPublication);
        retireOnPublication.clear();
        boolean changed = false;
        for (LivingEntity body : ready) {
            if (!presentIn(level, body) || !(body instanceof Unit unit) || !ENEMY_OWNER_NAME.equals(unit.getOwnerName())) continue;
            body.discard(); // cleanup is not a combat death, a last kill, or a loot trigger
            changed |= retiredEnemies.remove(body.getUUID());
        }
        if (changed) stageSave(level);
    }

    public static boolean isRetiredWavePortal(PortalPlacement p) {
        return p.getLevel() == serverLevel && ENEMY_OWNER_NAME.equals(p.ownerName) && retiredPortals.contains(p.originPos);
    }
    public static void retiredWavePortalRemoved(PortalPlacement p) {
        if (isRetiredWavePortal(p)) {
            retiredPortals.remove(p.originPos);
            stageSave(serverLevel);
        }
    }

    public static void setWaveNumber(int waveNumber) {
        if (SurvivalResult.isAfterMatch() || dispatchBirths != null) return;
        nextWave = Wave.getWave(waveNumber);
        wavesFinished = false; // an explicit authorized cheat may replay a finished encounter
        SurvivalClientboundPacket.setWaveNumber(clientStatus());
        if (serverLevel != null && isEnabled) saveData(serverLevel);
    }
}
