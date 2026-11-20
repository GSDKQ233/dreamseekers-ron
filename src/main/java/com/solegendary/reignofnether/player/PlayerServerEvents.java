package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.ability.HeroAbility;
import com.solegendary.reignofnether.ability.TradeAction;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.alliance.AllyCommand;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.neutral.Beacon;
import com.solegendary.reignofnether.building.buildings.placements.CustomBuildingPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.faction.RandomStartFactionSelection;
import com.solegendary.reignofnether.fogofwar.FogChunkSnapshot;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.gamemode.GameMode;
import com.solegendary.reignofnether.gamemode.GameModeServerEvents;
import com.solegendary.reignofnether.gamemode.GameModeClientboundPacket;
import com.solegendary.reignofnether.gamerules.GameruleClientboundPacket;
import com.solegendary.reignofnether.guiscreen.TopdownGuiContainer;
import com.solegendary.reignofnether.hero.HeroClientboundPacket;
import com.solegendary.reignofnether.hero.HeroServerEvents;
import com.solegendary.reignofnether.items.ItemServerEvents;
import com.solegendary.reignofnether.items.RandomItemDropRule;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.research.ResearchClientboundPacket;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.sandbox.SandboxServer;
import com.solegendary.reignofnether.scenario.ScenarioRole;
import com.solegendary.reignofnether.scenario.ScenarioServerEvents;
import com.solegendary.reignofnether.scenario.ScenarioUtils;
import com.solegendary.reignofnether.startpos.StartPosServerEvents;
import com.solegendary.reignofnether.survival.SurvivalServerEvents;
import com.solegendary.reignofnether.survival.SurvivalResult;
import com.solegendary.reignofnether.time.TimeServerEvents;
import com.solegendary.reignofnether.time.TimeUtils;
import com.solegendary.reignofnether.tutorial.TutorialServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.AttackerUnit;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.unit.packets.UnitSyncClientboundPacket;
import com.solegendary.reignofnether.util.MiscUtil;
import com.solegendary.reignofnether.worldborder.WorldBorderServerEvents;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.MenuConstructor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.*;

import static com.solegendary.reignofnether.building.BuildingServerEvents.random;
import static com.solegendary.reignofnether.building.BuildingServerEvents.saveBuildings;
import static com.solegendary.reignofnether.time.TimeUtils.getWaveSurvivalTimeModifier;
import static net.minecraft.world.level.GameRules.RULE_DISABLE_ELYTRA_MOVEMENT_CHECK;

// this class tracks all available players so that any serverside functions that need to affect the player can be
// performed here by sending a client->server packet containing MC.player.getId()

public class PlayerServerEvents {

    // list of what gamemode these players should be in when outside of RTS cam
    private static final Map<String, GameType> playerDefaultGameModes = new HashMap<>();
    private static final Map<String, Boolean> playerGuiOpenStatus = new HashMap<>();

    public static final ArrayList<ServerPlayer> players = new ArrayList<>();
    public static final ArrayList<ServerPlayer> orthoviewPlayers = new ArrayList<>();
    // players that are currently playing a match
    public static final List<RTSPlayer> rtsPlayers = Collections.synchronizedList(new ArrayList<>());
    // list of players after a match ends
    public static final List<RTSPlayer> postGameRtsPlayers = Collections.synchronizedList(new ArrayList<>());
    public static boolean rtsLocked = false; // can players join as RTS players or not?
    public static boolean rtsSyncingEnabled = true; // will logging in players sync units and buildings?

    private static final int MONSTER_START_TIME_OF_DAY = 500; // 500 = dawn, 6500 = noon, 12500 = dusk

    public static final int TICKS_TO_REVEAL = 60 * ResourceCost.TICKS_PER_SECOND;

    public static long rtsGameTicks = 0; // ticks up as long as there is at least 1 rtsPlayer

    public static ServerLevel serverLevel = null;

    // warpten - faster building/unit production
    // operationcwal - faster resource gathering
    // modifythephasevariance - ignore building requirements
    // medievalman - get all research (cannot reverse)
    // greedisgood X - gain X of each resource
    // foodforthought - ignore soft population caps
    // thereisnospoon - allow changing survival wave by clicking the wave indicator and using debug commands
    // slipslopslap - monster units are unaffected by sunlight
    // wouldyoukindly - allow control of non-unit mobs in RTS mode
    // thebeastofcaerbannog - spawns the Killer Rabbit
    // elitetaurenchieftain - levels all owned heroes to 10
    public static final List<String> singleWordCheats = List.of(
        "warpten",
        "operationcwal",
        "modifythephasevariance",
        "medievalman",
        "foodforthought",
        "thereisnospoon",
        "slipslopslap",
        "wouldyoukindly"
    );

    public static void saveRTSPlayers() {
        if (serverLevel == null) {
            return;
        }
        RTSPlayerSaveData data = RTSPlayerSaveData.getInstance(serverLevel);
        data.rtsPlayers.clear();
        data.rtsPlayers.addAll(rtsPlayers);
        data.postGameRtsPlayers.clear();
        data.postGameRtsPlayers.addAll(postGameRtsPlayers);
        data.gameTicks = rtsGameTicks;
        data.admissionLocked = rtsLocked;
        data.save();
        serverLevel.getDataStorage().save();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent evt) {
        ServerLevel level = evt.getServer().getLevel(Level.OVERWORLD);

        if (level != null) {
            RTSPlayerSaveData data = RTSPlayerSaveData.getInstance(level);

            rtsPlayers.clear();
            rtsPlayers.addAll(data.rtsPlayers);
            postGameRtsPlayers.clear();
            postGameRtsPlayers.addAll(data.postGameRtsPlayers);
            serverLevel = level;
            rtsGameTicks = data.gameTicks;
            rtsLocked = data.admissionLocked;
            FogOfWarServerEvents.invalidateRtsCache();

            for (RTSPlayer rtsPlayer : rtsPlayers) {
                if (rtsPlayer.faction == Factions.NONE) {
                    GameModeClientboundPacket.setAndLockAllClientGameModes(GameMode.SANDBOX);
                    enableAllCheats(rtsPlayer.name);
                    break;
                }
            }
            UnitServerEvents.maxPopulation = level.getGameRules().getInt(GameRuleRegistrar.MAX_POPULATION);

            level.getGameRules().getRule(RULE_DISABLE_ELYTRA_MOVEMENT_CHECK).set(true, evt.getServer());
        }
    }

    private static final int SAVE_TICKS_MAX = 1200;
    private static int saveTicks = 0;
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        // Listener order is immaterial: the unit stop hook also checkpoints before emptying its list.
        UnitServerEvents.checkpointUnitPresence(evt.getServer().overworld());
        saveRTSPlayers();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent evt) {
        players.clear(); orthoviewPlayers.clear();
        playerDefaultGameModes.clear(); playerGuiOpenStatus.clear();
        rtsLocked = false; rtsSyncingEnabled = true; serverLevel = null;
    }

    /** Migrate a legacy human slot only from an authenticated matching name. Never manufacture history. */
    private static void bindPlayerSession(ServerPlayer player) {
        synchronized (rtsPlayers) {
            for (RTSPlayer slot : rtsPlayers) {
                if (slot.isBot()) continue;
                boolean match = slot.playerUuid != null ? slot.playerUuid.equals(player.getUUID())
                        : slot.name.equals(player.getGameProfile().getName());
                if (match) { slot.playerUuid = player.getUUID(); slot.id = player.getId(); }
            }
        }
    }
    static boolean alreadyHasSlot(ServerPlayer player) {
        return rtsPlayers.stream().anyMatch(slot -> slot.name.equals(player.getGameProfile().getName())
                || (slot.playerUuid != null && slot.playerUuid.equals(player.getUUID())));
    }
    @SubscribeEvent public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent evt) {
        if (!(evt.getEntity() instanceof ServerPlayer player)) return;
        boolean ortho = orthoviewPlayers.stream().anyMatch(p -> p != null && p.getUUID().equals(player.getUUID()));
        players.removeIf(p -> p == null || p.getUUID().equals(player.getUUID())); players.add(player);
        orthoviewPlayers.removeIf(p -> p == null || p.getUUID().equals(player.getUUID()));
        if (ortho) orthoviewPlayers.add(player);
        bindPlayerSession(player);
        for (RTSPlayer slot : rtsPlayers) if (slot.playerUuid != null && slot.playerUuid.equals(player.getUUID()))
            PlayerClientboundPacket.addRTSPlayer(slot, true);
    }

    public static boolean isRTSPlayer(String playerName) {
        synchronized (rtsPlayers) {
            for (RTSPlayer p : rtsPlayers) {
                if (p.name.equals(playerName)) return true;
            }
            return false;
        }
    }

    @Nullable
    public static RTSPlayer getRTSPlayer(String playerName) {
        synchronized (rtsPlayers) {
            for (RTSPlayer rtsPlayer : rtsPlayers)
                if (rtsPlayer.name.equals(playerName))
                    return rtsPlayer;
        }
        return null;
    }

    public static boolean isRTSPlayer(int id) {
        synchronized (rtsPlayers) {
            for (RTSPlayer p : rtsPlayers) {
                if (p.id == id) return true;
            }
            return false;
        }
    }

    public static boolean isBot(String playerName) {
        synchronized (rtsPlayers) {
            for (RTSPlayer rtsPlayer : rtsPlayers)
                if (rtsPlayer.name.equalsIgnoreCase(playerName)) {
                    return rtsPlayer.isBot();
                }
        }
        return false;
    }

    public static boolean isBot(int id) {
        synchronized (rtsPlayers) {
            for (RTSPlayer rtsPlayer : rtsPlayers)
                if (rtsPlayer.id == id) {
                    return rtsPlayer.isBot();
                }
        }
        return false;
    }

    public static boolean isGameActive() {
        return !rtsPlayers.isEmpty();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent evt) {
        serverLevel = evt.getServer().getLevel(Level.OVERWORLD);

        if (evt.phase == TickEvent.Phase.END) {
            if (SurvivalResult.isAfterMatch()) rtsGameTicks = SurvivalResult.current().elapsed();
            FogChunkSnapshot.recaptureIfRequested(serverLevel);
        }

        synchronized (rtsPlayers) {
            if (evt.phase == TickEvent.Phase.END && !SurvivalResult.isAfterMatch()) {
                for (RTSPlayer rtsPlayer : rtsPlayers)
                    rtsPlayer.serverTick();

                for (RTSPlayer rtsPlayer : rtsPlayers) {
                    if (rtsPlayer.beaconOwnerTicks == Beacon.getTicksToWin(serverLevel)) {
                        PlayerServerEvents.beaconVictory(rtsPlayer.name);
                        break;
                    }
                }
                if (SurvivalResult.isAfterMatch()) {
                    PlayerClientboundPacket.syncRtsGameTime(SurvivalResult.current().elapsed());
                } else if (rtsPlayers.isEmpty()) {
                    rtsGameTicks = 0;
                } else {
                    rtsGameTicks += 1;
                    if (rtsGameTicks % 200 == 0) {
                        PlayerClientboundPacket.syncRtsGameTime(rtsGameTicks);
                    }
                    if (rtsGameTicks % 20 == 0) {
                        for (RTSPlayer rtsPlayer : rtsPlayers) {
                            PlayerClientboundPacket.syncBeaconOwnerTicks(rtsPlayer.name, rtsPlayer.beaconOwnerTicks);
                        }
                    }
                }
            }
        }
        if (evt.phase == TickEvent.Phase.END) {
            saveTicks += 1;
            if (saveTicks >= SAVE_TICKS_MAX) {
                ServerLevel level = evt.getServer().getLevel(Level.OVERWORLD);
                if (level != null) {
                    saveRTSPlayers();
                    saveTicks = 0;
                }
            }
        }
    }

    static void syncUnits() {
        for (LivingEntity entity : UnitServerEvents.getAllUnits()) {
            if (entity instanceof Unit unit) {
                UnitSyncClientboundPacket.sendSyncResourcesPacket(unit);
                UnitSyncClientboundPacket.sendSyncOwnerNamePacket(unit);
                UnitSyncClientboundPacket.sendSyncScenarioRoleIndexPacket(unit);
                if (unit.getAnchor() != null)
                    UnitSyncClientboundPacket.sendSyncAnchorPosPacket(entity, unit.getAnchor());
            }
            if (entity instanceof HeroUnit hero) {
                HeroClientboundPacket.setExperience(entity.getId(), hero.getExperience());
                HeroClientboundPacket.setSkillPoints(entity.getId(), hero.getSkillPoints());
                HeroClientboundPacket.setCharges(entity.getId(), hero.getChargesForSaveData());
                List<HeroAbility> abls = hero.getHeroAbilities();
                if (abls.size() > 0)
                    HeroClientboundPacket.setAbilityRank(entity.getId(), abls.get(0).getRank(hero), 0);
                if (abls.size() > 1)
                    HeroClientboundPacket.setAbilityRank(entity.getId(), abls.get(1).getRank(hero), 1);
                if (abls.size() > 2)
                    HeroClientboundPacket.setAbilityRank(entity.getId(), abls.get(2).getRank(hero), 2);
                if (abls.size() > 3)
                    HeroClientboundPacket.setAbilityRank(entity.getId(), abls.get(3).getRank(hero), 3);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        ServerPlayer serverPlayer = (ServerPlayer) evt.getEntity();

        players.removeIf(p -> p == null || p.getUUID().equals(serverPlayer.getUUID()));
        players.add(serverPlayer);
        bindPlayerSession(serverPlayer);
        String playerName = serverPlayer.getName().getString();
        ReignOfNether.LOGGER.info("Player logged in: " + playerName + ", id: " + serverPlayer.getId());

        // if a player is looking directly at a frozenchunk on login, they may load in the real blocks before
        // they are frozen so move them away then BuildingClientEvents.placeBuilding moves them to their base later
        // don't do this if they don't own any buildings
        /*
        if (isRTSPlayer(playerName) && rtsSyncingEnabled) {
            for (BuildingPlacement building : BuildingServerEvents.getBuildings()) {
                if (building.ownerName.equals(playerName)) {
                    movePlayer(serverPlayer.getId(), 0, ORTHOVIEW_PLAYER_BASE_Y, 0);
                    break;
                }
            }
        }
         */
        if (rtsSyncingEnabled) {
            UnitLoginSync.request(serverPlayer);
            ResearchServerEvents.syncResearch(playerName);
            ResearchServerEvents.syncCheats(playerName);
        }

        boolean inOrthoviewList = false;
        for (ServerPlayer orthoviewPlayer : orthoviewPlayers) {
            if (orthoviewPlayer != null && orthoviewPlayer.getUUID().equals(serverPlayer.getUUID()))  {
                inOrthoviewList = true;
                break;
            }
        }
        orthoviewPlayers.removeIf(p -> p == null || (p.getUUID().equals(serverPlayer.getUUID()) && p != serverPlayer));
        if (!orthoviewPlayers.contains(serverPlayer))
            orthoviewPlayers.add(serverPlayer);

        if (!TutorialServerEvents.isEnabled()) {
            if (!isRTSPlayer(serverPlayer.getId())) {
                serverPlayer.sendSystemMessage(Component.translatable("tutorial.reignofnether.welcome")
                    .withStyle(Style.EMPTY.withBold(true)));
                serverPlayer.sendSystemMessage(Component.translatable("tutorial.reignofnether.join"));
                serverPlayer.sendSystemMessage(Component.translatable("tutorial.reignofnether.help"));
                serverPlayer.sendSystemMessage(Component.translatable("tutorial.reignofnether.controls"));
                if (rtsLocked) {
                    serverPlayer.sendSystemMessage(Component.literal(""));
                    serverPlayer.sendSystemMessage(Component.translatable("tutorial.reignofnether.locked"));
                }
            } else {
                serverPlayer.sendSystemMessage(Component.translatable("tutorial.reignofnether.welcome_back")
                    .withStyle(Style.EMPTY.withBold(true)));
            }
            if (serverPlayer.hasPermissions(4)) {
                serverPlayer.sendSystemMessage(Component.literal(""));
                serverPlayer.sendSystemMessage(Component.translatable("tutorial.reignofnether.op_commands"));
                serverPlayer.sendSystemMessage(Component.translatable("tutorial.reignofnether.fog"));
                serverPlayer.sendSystemMessage(Component.translatable("tutorial.reignofnether.lock"));;
                serverPlayer.sendSystemMessage(Component.translatable("tutorial.reignofnether.reset"));
                serverPlayer.sendSystemMessage(Component.literal(""));
            }
            if (!rtsSyncingEnabled) {
                serverPlayer.sendSystemMessage(Component.literal(""));
                serverPlayer.sendSystemMessage(Component.translatable("tutorial.reignofnether.sync_disabled1"));
                serverPlayer.sendSystemMessage(Component.translatable("tutorial.reignofnether.sync_disabled2"));
                serverPlayer.sendSystemMessage(Component.literal(""));
            }
        }
        if (getRTSPlayer(playerName) == null) {
            PlayerClientboundPacket.removeRTSPlayer(playerName);
        }
        for (RTSPlayer rtsPlayer : rtsPlayers) {
            PlayerClientboundPacket.addRTSPlayer(rtsPlayer, true);
        }

        if (rtsLocked) {
            PlayerClientboundPacket.lockRTS(playerName);
        } else {
            PlayerClientboundPacket.unlockRTS(playerName);
        }

        if (rtsSyncingEnabled) {
            PlayerClientboundPacket.enableStartRTS(playerName);
        } else {
            PlayerClientboundPacket.disableStartRTS(playerName);
        }

        updateMarketRates(playerName);
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent evt) {
        int id = evt.getEntity().getId();
        ReignOfNether.LOGGER.info("Player logged out: " + evt.getEntity().getName().getString() + ", id: " + id);
        players.removeIf(player -> player == null || player.getId() == id);
        orthoviewPlayers.removeIf(player -> player == null || player.getId() == id);
        String name = evt.getEntity().getGameProfile().getName();
        playerGuiOpenStatus.remove(name);
        playerDefaultGameModes.remove(name);
        if (evt.getEntity() instanceof ServerPlayer player) UnitLoginSync.logout(evt);
    }

    public static void startRTS(int playerId, Vec3 pos, Faction faction) {
        startRTS(playerId, pos, faction, 0);
    }

    // readied start is a simultaneous start from players using RTS start pos blocks, difference being:
    // - places the capitol foundations automatically
    // - spawns workers outside the foundations
    // - no start messages are sent other than the one from the countdown
    public static void startRTS(int playerId, Vec3 pos, Faction faction, int startPosColorId) {
        if (faction == null || pos == null || !Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)) return;
        if (faction != Factions.NONE && faction != Factions.RANDOM && !Factions.PLAYABLE_FACTIONS.contains(faction.key)) return;
        faction = resolveStartFaction(faction);
        if (faction == null) return;
        ReignOfNether.LOGGER.info("[Player] startRTS: playerId={}, pos=[{},{},{}], faction={}, startPosColorId={}", playerId, pos.x, pos.y, pos.z, faction, startPosColorId);
        synchronized (rtsPlayers) {
            ServerPlayer serverPlayer = null;
            for (ServerPlayer player : players)
                if (player.getId() == playerId)
                    serverPlayer = player;

            if (serverPlayer == null) {
                return;
            }
            if (rtsLocked) {
                serverPlayer.sendSystemMessage(Component.literal(""));
                serverPlayer.sendSystemMessage(Component.translatable("server.reignofnether.locked"));
                serverPlayer.sendSystemMessage(Component.literal(""));
                return;
            }
            if (alreadyHasSlot(serverPlayer)) {
                serverPlayer.sendSystemMessage(Component.literal(""));
                serverPlayer.sendSystemMessage(Component.translatable("server.reignofnether.already_started"));
                serverPlayer.sendSystemMessage(Component.literal(""));
                return;
            }
            if (serverPlayer.level().getWorldBorder().getDistanceToBorder(pos.x, pos.z) < 1 && faction != Factions.NONE) {
                serverPlayer.sendSystemMessage(Component.literal(""));
                serverPlayer.sendSystemMessage(Component.translatable("server.reignofnether.outside_border"));
                serverPlayer.sendSystemMessage(Component.literal(""));
                return;
            }
            GameModeServerEvents.StartChoice modeChoice = GameModeServerEvents.takeStartChoice(serverPlayer, faction, false);
            if (modeChoice == null) return;
            grantAdmittedStart(serverPlayer, pos, faction, startPosColorId, modeChoice, null);
        }
    }

    static Faction resolveStartFaction(Faction faction) {
        if (faction == Factions.RANDOM) {
            var candidates = RandomStartFactionSelection.candidates(Factions.PLAYABLE_FACTIONS,
                    Factions::getFaction, candidate ->
                            ForgeRegistries.ENTITY_TYPES.containsKey(candidate.workerEntityType)
                                    && ForgeRegistries.ENTITY_TYPES.containsKey(candidate.scoutEntityType)
                                    && ReignOfNetherRegistries.BUILDING.containsKey(candidate.capitolBuilding));
            faction = RandomStartFactionSelection.select(candidates, random::nextInt);
            if (faction == null) {
                ReignOfNether.LOGGER.warn("[Player] Cannot start random faction: no registered start-capable faction");
                return null;
            }
        }
        return faction;
    }

    // Batch callers already checked every frozen seat, and published all of their units together.
    // No admission decision is repeated per participant during the grant phase.
    static boolean grantAdmittedStart(ServerPlayer serverPlayer, Vec3 pos, Faction faction, int startPosColorId,
                                  GameModeServerEvents.StartChoice modeChoice, @Nullable ReadiedStart.Assets batch) {
        synchronized (rtsPlayers) {
            boolean readiedStart = batch != null || startPosColorId != 0;
            boolean firstStart = rtsPlayers.isEmpty();
            boolean isDogPerson = batch == null ? random.nextBoolean() : batch.dogPerson();

            EntityType<?> workerEntityType = ForgeRegistries.ENTITY_TYPES.getValue(faction.workerEntityType);
	        EntityType<?> scoutEntityType = ForgeRegistries.ENTITY_TYPES.getValue(faction.scoutEntityType);
			if (faction.equals(Factions.VILLAGERS) && !isDogPerson) scoutEntityType = EntityRegistrar.SCOUT_CAT_UNIT.get();
            // first RTS join into a fresh game: snapshot the playable area for late joiners
            if (rtsPlayers.isEmpty() && !FogChunkSnapshot.hasAny(serverLevel) && WorldBorderServerEvents.isRtsOptimisedMap(serverLevel)) {
                FogChunkSnapshot.captureFogChunks((ServerLevel) serverPlayer.level());
            }
            if (rtsPlayers.isEmpty()) {
                clearMatchHistory();
                FogOfWarServerEvents.captureNeutralFogUnits();
            }
            RandomItemDropRule randomItemDropRule = RandomItemDropRule.fromValue(
                    serverPlayer.level().getGameRules().getRule(GameRuleRegistrar.RANDOM_ITEM_DROPS).get()
            );
            Long itemSeed = switch (randomItemDropRule) {
                case DISABLED -> -1L;
                case ENABLED_RANDOM, ENABLED_SAME_RARITY -> random.nextLong(1, Long.MAX_VALUE);
                case ENABLED_SAME_ITEMS -> ItemServerEvents.ITEM_DROPS_SEED;
            };
            rtsPlayers.add(RTSPlayer.getNewPlayer(
                    serverPlayer.getName().getString(),
                    faction,
                    serverPlayer.getId(),
                    startPosColorId,
                    isDogPerson,
                    itemSeed,
                    randomItemDropRule == RandomItemDropRule.ENABLED_SAME_RARITY ||
                    randomItemDropRule == RandomItemDropRule.ENABLED_SAME_ITEMS ?
                        ItemServerEvents.ITEM_RARITY_SEED : -1L
            ));
            rtsPlayers.get(rtsPlayers.size() - 1).playerUuid = serverPlayer.getUUID();
            FogOfWarServerEvents.invalidateRtsCache();
            String playerName = serverPlayer.getName().getString();
            ResourcesServerEvents.assignResources(playerName);
            PlayerClientboundPacket.addRTSPlayer(playerName, faction, (long) serverPlayer.getId(), startPosColorId, isDogPerson, false);
            HeroServerEvents.fallenHeroes.removeIf(hero -> hero.ownerName.equals(playerName));

            ServerLevel level = (ServerLevel) serverPlayer.level();
            ArrayList<Entity> startingWorkers = new ArrayList<>();
            Entity startingScout = null;

            boolean isBat = scoutEntityType == EntityRegistrar.BAT_UNIT.get();
            if (batch == null) {
                List<BlockPos> nonReadiedWorkerBps = List.of(
                        new BlockPos((int) pos.x,0,(int) pos.z),
                        new BlockPos((int) pos.x+1,0,(int) pos.z),
                        new BlockPos((int) pos.x,0,(int) pos.z+1),
                        new BlockPos((int) pos.x-1,0,(int) pos.z),
                        new BlockPos((int) pos.x,0,(int) pos.z-1)
                );
                for (BlockPos bp0 : nonReadiedWorkerBps) {
                    Entity entity = workerEntityType != null ? workerEntityType.create(level) : null;
                    if (entity instanceof Unit unit) {
                        BlockPos bp = MiscUtil.getHighestNonAirBlock(level, bp0)
                                .above()
                                .above();
                        unit.setOwnerName(playerName);
                        entity.moveTo(bp, 0, 0);
                        if (!readiedStart)
                            level.addFreshEntity(entity);
                        startingWorkers.add(entity);
                    }
                }
                if (FogOfWarServerEvents.isEnabled()) {
                    BlockPos nonReadiedScoutBp = new BlockPos((int) pos.x - 1, 0,(int) pos.z - 1);
                    Entity scoutEntity = scoutEntityType != null ? scoutEntityType.create(level) : null;
                    if (scoutEntity instanceof Unit) {
                        BlockPos bp = MiscUtil.getHighestNonAirBlock(level, nonReadiedScoutBp)
                                .above()
                                .above();
                        ((Unit) scoutEntity).setOwnerName(playerName);
                        scoutEntity.moveTo(isBat ? bp.offset(0,10,0) : bp, 0, 0);
                        if (!readiedStart)
                            level.addFreshEntity(scoutEntity);
                        startingScout = scoutEntity;
                    }
                }

            } else {
                startingWorkers.addAll(batch.workers());
            }
            if (faction != Factions.NONE) {
                if (SurvivalServerEvents.isEnabled()) {
                    level.setDayTime(TimeUtils.DAWN + getWaveSurvivalTimeModifier(SurvivalServerEvents.getDifficulty()));
                    for (RTSPlayer rtsPlayer : rtsPlayers)
                        if (!rtsPlayer.name.equals(playerName))
                            AlliancesServerEvents.addAlliance(rtsPlayer.name, playerName);
                } else {
                    level.setDayTime(MONSTER_START_TIME_OF_DAY);
                }
                ResearchServerEvents.removeAllCheatsFor(playerName);
            } else {
                enableAllCheats(playerName);
            }
            ResourcesServerEvents.resetResources(playerName, readiedStart);

            if (readiedStart) {
                Building building = batch != null ? batch.capitol() : ReignOfNetherRegistries.BUILDING.get(faction.capitolBuilding);
                if (building != null) {
                    BlockPos bp = batch != null ? batch.origin()
                            : getBuildingOriginPos(BlockPos.containing(pos), building.getRelativeBlockData(level));
                    if (batch == null) {
                        for (int i = 0; i < startingWorkers.size(); i++) {
                            startingWorkers.get(i).moveTo(bp.offset(-1, 1, i), 0, 0);
                            level.addFreshEntity(startingWorkers.get(i));
                        }
                        if (startingScout != null) {
                            startingScout.moveTo(bp.offset(0, isBat ? 10 : 1, 2), 0, 0);
                            level.addFreshEntity(startingScout);
                        }
                    }
                    var workerIds = startingWorkers.stream().mapToInt(Entity::getId).toArray();
                    if (batch == null) {
                        BuildingServerEvents.placeBuilding(building, bp, Rotation.NONE, playerName, workerIds, false, false, true, true);
                    } else {
                        // The exact concrete object, origin, world and footprint were checked for every seat.
                        if (BuildingServerEvents.placeBuilding(batch.placement(), bp, Rotation.NONE, playerName, workerIds,
                                false, false, true, true) != batch.placement()) return false;
                        // Native assignment uses level.getEntity(id). An accepted worker in a cold
                        // section can still be hidden from lookup: give that exact staged object the
                        // same order, without using an ID retry or touching any replacement entity.
                        for (LivingEntity body : batch.workers()) {
                            if (!UnitServerEvents.isPublishedBody(level, body)
                                    || !playerName.equals(((Unit)body).getOwnerName())) return false;
                            var worker = (WorkerUnit)body;
                            if (level.getEntity(body.getId()) == null && worker.getBuildRepairGoal().getBuildingTarget() != batch.placement()) {
                                ((Unit)body).resetBehaviours();
                                WorkerUnit.resetBehavioursExceptExploreBuild(worker);
                                worker.getBuildRepairGoal().setBuildingTarget(batch.placement());
                            }
                        }
                    }
                    PlayerClientboundPacket.teleport(playerName, BlockPos.containing(pos));
                }
                for (RTSPlayer rtsPlayer : rtsPlayers) {
                    String playerName1 = rtsPlayer.name;
                    String playerName2 = serverPlayer.getName().getString();
                    if (!playerName1.equals(playerName2) && rtsPlayer.startPosColorId == startPosColorId) {
                        AlliancesServerEvents.addAlliance(playerName1, playerName2);
                    }
                }
            }

            boolean coopMode = serverLevel.getGameRules().getRule(GameRuleRegistrar.COOP_MODE).get();

            if (!TutorialServerEvents.isEnabled() && !readiedStart) {
                serverPlayer.sendSystemMessage(Component.literal(""));
                if (faction.equals(Factions.NONE))
                    sendMessageToAllPlayers("server.reignofnether.started_sandbox", true, playerName);
                else if (coopMode)
                    sendMessageToAllPlayers("server.reignofnether.started_ally", true, playerName);
                else
                    sendMessageToAllPlayers("server.reignofnether.started", true, playerName);
                sendMessageToAllPlayers("server.reignofnether.total_players", false, rtsPlayers.size());
            }
            if (coopMode)
                AlliancesServerEvents.applyCoopAlliances();


            PlayerClientboundPacket.syncRtsGameTime(rtsGameTicks);
            GameModeServerEvents.commitStart(serverPlayer, modeChoice, firstStart);
            if (batch == null) saveRTSPlayers(); // batch saves once, after lock and all seats are committed
            return true;
        }
    }

    public static BlockPos getBuildingOriginPos(BlockPos bp, ArrayList<BuildingBlock> blocks) {
        Vec3i buildingDimensions = BuildingUtils.getBuildingSize(blocks);
        int xRadius = buildingDimensions.getX() / 2;
        int zRadius = buildingDimensions.getZ() / 2;
        return bp.offset(-xRadius, 0 , -zRadius);
    }

    public static void startRTSBot(String name, Vec3 pos, Faction faction) {
        if (players.isEmpty()) return;
        startRTSBot(name, pos, faction, players.get(0).serverLevel());
    }

    public static void startRTSBot(String name, Vec3 pos, Faction faction, ServerLevel level) {
        if (level == null || !level.getServer().isSameThread() || SurvivalResult.isAfterMatch()) return;
        synchronized (rtsPlayers) {

            EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(faction.workerEntityType);
            RTSPlayer bot = RTSPlayer.getNewBot(name, faction);
            if (rtsPlayers.isEmpty()) clearMatchHistory();
            rtsPlayers.add(bot);
            if (SurvivalServerEvents.isEnabled())
                for (RTSPlayer seat : rtsPlayers)
                    if (seat != bot && !seat.name.equals(bot.name) && !AlliancesServerEvents.isAllied(seat.name, bot.name))
                        AlliancesServerEvents.addAlliance(seat.name, bot.name);
            FogOfWarServerEvents.invalidateRtsCache();
            ResourcesServerEvents.assignResources(bot.name);

            for (int i = -1; i <= 1; i++) {
                Entity entity = entityType != null ? entityType.create(level) : null;
                if (entity instanceof Unit unit) {
                    BlockPos bp = MiscUtil.getHighestNonAirBlock(level, new BlockPos((int) (pos.x + i), 0, (int) pos.z))
                        .above()
                        .above();
                    unit.setOwnerName(bot.name);
                    entity.moveTo(bp, 0, 0);
                    level.addFreshEntity(entity);
                }
            }
            if (faction.equals(Factions.MONSTERS)) {
                level.setDayTime(MONSTER_START_TIME_OF_DAY);
            }
            ResourcesServerEvents.resetResources(bot.name, false);

            if (!TutorialServerEvents.isEnabled()) {
                sendMessageToAllPlayers("server.reignofnether.bot_added", true, bot.name);
                sendMessageToAllPlayers("server.reignofnether.total_players", false, rtsPlayers.size());
            }
            saveRTSPlayers();
        }
    }

    public static void startRTSScenario(int playerId, int roleIndex) {
        synchronized (rtsPlayers) {
            ServerPlayer serverPlayer = null;
            for (ServerPlayer player : players)
                if (player.getId() == playerId)
                    serverPlayer = player;

            ScenarioRole role = ScenarioUtils.getScenarioRole(false, roleIndex);

            if (serverPlayer == null || role == null) {
                return;
            }
            if (role.isNpc) {
                serverPlayer.sendSystemMessage(Component.literal(""));
                serverPlayer.sendSystemMessage(Component.translatable("sandbox.reignofnether.scenario.npc_role_error"));
                serverPlayer.sendSystemMessage(Component.literal(""));
                return;
            }
            if (rtsLocked) {
                serverPlayer.sendSystemMessage(Component.literal(""));
                serverPlayer.sendSystemMessage(Component.translatable("server.reignofnether.locked"));
                serverPlayer.sendSystemMessage(Component.literal(""));
                return;
            }
            if (alreadyHasSlot(serverPlayer)) {
                serverPlayer.sendSystemMessage(Component.literal(""));
                serverPlayer.sendSystemMessage(Component.translatable("server.reignofnether.already_started_scenario"));
                serverPlayer.sendSystemMessage(Component.literal(""));
                return;
            }
            for (RTSPlayer rtsPlayer : rtsPlayers) {
                if (rtsPlayer.scenarioRoleIndex == roleIndex) {
                    serverPlayer.sendSystemMessage(Component.literal(""));
                    serverPlayer.sendSystemMessage(Component.translatable("server.reignofnether.scenario_role_taken"));
                    serverPlayer.sendSystemMessage(Component.literal(""));
                    return;
                }
            }
            GameModeServerEvents.StartChoice modeChoice = GameModeServerEvents.takeStartChoice(serverPlayer, role.faction, true);
            if (modeChoice == null) return;
            boolean firstStart = rtsPlayers.isEmpty();
            RTSPlayer rtsPlayer = RTSPlayer.getNewScenarioPlayer(
                    serverPlayer.getName().getString(),
                    role.faction,
                    serverPlayer.getId(),
                    roleIndex
            );
            rtsPlayer.playerUuid = serverPlayer.getUUID();
            if (rtsPlayers.isEmpty()) clearMatchHistory();
            rtsPlayers.add(rtsPlayer);
            GameModeServerEvents.commitStart(serverPlayer, modeChoice, firstStart);
            FogOfWarServerEvents.invalidateRtsCache();
            String playerName = serverPlayer.getName().getString();
            ResourcesServerEvents.assignScenarioResources(rtsPlayer);
            PlayerClientboundPacket.addRTSPlayer(rtsPlayer, false);

            for (BuildingPlacement building : BuildingServerEvents.getBuildings()) {
                if (building.scenarioRoleIndex == roleIndex) {
                    building.ownerName = playerName;
                    BuildingClientboundPacket.syncBuilding(building.originPos, building.getBlocksPlaced(),
                            building.partialBlocksDestroyed, playerName, building.scenarioRoleIndex);
                }
            }
            for (LivingEntity le : UnitServerEvents.getAllUnits()) {
                if (le instanceof Unit unit && unit.getScenarioRoleIndex() == roleIndex) {
                    unit.setOwnerName(playerName);
                    UnitSyncClientboundPacket.sendSyncOwnerNamePacket(unit);
                }
            }

            sendMessageToAllPlayers("server.reignofnether.started_scenario", true, playerName, role.name, Component.translatable(role.faction.getTranslationKey()));
            PlayerClientboundPacket.syncRtsGameTime(rtsGameTicks);

            // add NPC rtsPlayers
            int id = -1;
            for (ScenarioRole scenarioRole : ScenarioServerEvents.scenarioRoles) {
                int numUnits = ScenarioServerEvents.getNumScenarioUnits(scenarioRole);
                int numBuilds = ScenarioServerEvents.getNumScenarioBuildings(scenarioRole);
                if (!isRTSPlayer(scenarioRole.name) && (numUnits > 0 || numBuilds > 0) && scenarioRole.isNpc) {
                    RTSPlayer npcRtsPlayer = RTSPlayer.getNewScenarioPlayer(
                            scenarioRole.name,
                            scenarioRole.faction,
                            id,
                            scenarioRole.index
                    );
                    rtsPlayers.add(npcRtsPlayer);
                    FogOfWarServerEvents.invalidateRtsCache();
                    ResourcesServerEvents.assignScenarioResources(npcRtsPlayer);
                    PlayerClientboundPacket.addRTSPlayer(npcRtsPlayer, false);
                    id -= 1;
                }
            }
            if (serverLevel.getGameRules().getRule(GameRuleRegistrar.SCENARIO_MODE).get())
                AlliancesServerEvents.applyScenarioAlliances();
            if (serverLevel.getGameRules().getRule(GameRuleRegistrar.COOP_MODE).get())
                AlliancesServerEvents.applyCoopAlliances();
            saveRTSPlayers();
        }
    }

    @SubscribeEvent
    public static void onPlayerChat(ServerChatEvent evt) {
        if (evt.getPlayer().hasPermissions(4)) {
            String msg = evt.getMessage().getString();
            String[] words = msg.split(" ");
            String playerName = evt.getPlayer().getName().getString();

            if (words.length == 1 && words[0].equalsIgnoreCase("thebeastofcaerbannog")) {
                UnitServerEvents.spawnMob(EntityRegistrar.getEntityType("Killer Rabbit"), serverLevel, evt.getPlayer().getOnPos(), playerName);
                sendMessageToAllPlayers("server.reignofnether.used_cheat",false, playerName, words[0]);
            }

            if (words.length == 1 && words[0].equalsIgnoreCase("elitetaurenchieftain")) {
                for (LivingEntity entity : UnitServerEvents.getAllUnits()) {
                    if (entity instanceof HeroUnit heroUnit && ((Unit) heroUnit).getOwnerName().equals(playerName)) {
                        heroUnit.addExperience(10000);
                        heroUnit.setSkillPoints(10);
                        heroUnit.setMana(heroUnit.getMaxMana());
                        HeroClientboundPacket.setExperience(entity.getId(), heroUnit.getExperience());
                        HeroClientboundPacket.setSkillPoints(entity.getId(), 10);
                    }
                }
                sendMessageToAllPlayers("server.reignofnether.used_cheat",false, playerName, words[0]);
            }

            if (words.length == 2) {
                try {
                    if (words[0].equalsIgnoreCase("greedisgood")) {
                        int amount = Integer.parseInt(words[1]);
                        if (amount > 0) {
                            ResourcesServerEvents.addSubtractResources(new Resources(playerName,
                                    amount,
                                    amount,
                                    amount,
                                    amount
                            ));
                            evt.setCanceled(true);
                            sendMessageToAllPlayers("server.reignofnether.used_cheat_amount",
                                    false,
                                    playerName,
                                    words[0],
                                    Integer.toString(amount)
                            );
                        }
                    }
                } catch (NumberFormatException err) {
                    ReignOfNether.LOGGER.error(err);
                }
            }

            if (words.length == 3) {
                try {
                    if (words[0].equalsIgnoreCase("greedisgood")) {
                        int amount = Integer.parseInt(words[2]);
                        if (amount > 0 && List.of("food", "wood", "ore", "emerald").contains(words[1].toLowerCase())) {
                            switch (words[1].toLowerCase()) {
                                case "food" -> ResourcesServerEvents.addSubtractResources(new Resources(playerName, amount, 0, 0,0));
                                case "wood" -> ResourcesServerEvents.addSubtractResources(new Resources(playerName, 0, amount, 0,0));
                                case "ore" -> ResourcesServerEvents.addSubtractResources(new Resources(playerName, 0, 0, amount,0));
                                case "emerald" -> ResourcesServerEvents.addSubtractResources(new Resources(playerName, 0, 0,0, amount));
                            }
                            evt.setCanceled(true);
                            sendMessageToAllPlayers("server.reignofnether.used_cheat_amount",
                                    false,
                                    playerName,
                                    words[0] + " " + words[1],
                                    Integer.toString(amount)
                            );
                        }
                    }
                } catch (NumberFormatException err) {
                    ReignOfNether.LOGGER.error(err);
                }
            }

            for (String cheatName : singleWordCheats) {
                if (words.length == 1 && words[0].equalsIgnoreCase(cheatName)) {
                    if (ResearchServerEvents.playerHasCheat(playerName, cheatName)) {
                        ResearchServerEvents.removeCheat(playerName, cheatName);
                        ResearchClientboundPacket.removeCheat(playerName, cheatName);
                        evt.setCanceled(true);
                        sendMessageToAllPlayers("server.reignofnether.disabled_cheat", false, playerName, cheatName);
                    } else {
                        ResearchServerEvents.addCheat(playerName, cheatName);
                        ResearchClientboundPacket.addCheat(playerName, cheatName);
                        evt.setCanceled(true);
                        sendMessageToAllPlayers("server.reignofnether.enabled_cheat", false, playerName, cheatName);
                    }
                }
            }

            // apply all cheats - NOTE can cause concurrentModificationException clientside
            if (words.length == 1 && words[0].equalsIgnoreCase("allcheats") && (
                playerName.equalsIgnoreCase("solegendary") ||
                playerName.equalsIgnoreCase("altsolegendary"))
            ) {
                ResourcesServerEvents.addSubtractResources(new Resources(playerName, 99999, 99999, 99999));
                UnitServerEvents.maxPopulation = 99999;
                enableAllCheats(playerName);
                evt.setCanceled(true);
                sendMessageToAllPlayers("server.reignofnether.all_cheats", false, playerName);
            }
        }
    }

    public static void enableAllCheats(String playerName) {
        for (String cheatName : singleWordCheats) {
            ResearchServerEvents.addCheat(playerName, cheatName);
            ResearchClientboundPacket.addCheat(playerName, cheatName);
        }
    }

    public static void enableOrthoview(int id) {
        ServerPlayer player = getPlayerById(id);
        if (player == null) return;
        player.removeAllEffects();
        orthoviewPlayers.removeIf(p -> p == null || p.getUUID().equals(player.getUUID()));
        orthoviewPlayers.add(player);
    }

    public static void disableOrthoview(int id) {
        ServerPlayer leaving = getPlayerById(id);
        orthoviewPlayers.removeIf(p -> p == null || p.getId() == id);
        if (leaving != null) {
            // drop fog bookkeeping so normal view-distance behavior resumes
            com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.onPlayerExitOrthoview(leaving);
        }
    }

    private static ServerPlayer getPlayerById(int playerId) {
        for (ServerPlayer player : players) {
            if (playerId == player.getId()) {
                return player;
            }
        }
        return null;
    }

    public static void openTopdownGui(int playerId) {
        ServerPlayer serverPlayer = getPlayerById(playerId);

        if (serverPlayer != null) {
            // Open GUI server-side
            MenuConstructor provider = TopdownGuiContainer.getServerContainerProvider();
            MenuProvider namedProvider = new SimpleMenuProvider(provider, TopdownGuiContainer.TITLE);
            NetworkHooks.openScreen(serverPlayer, namedProvider);

            // Save original game mode only if it's not already saved for this session
            String playerName = serverPlayer.getName().getString();
            playerDefaultGameModes.putIfAbsent(playerName, serverPlayer.gameMode.getGameModeForPlayer());

            // Mark that this player has the GUI open
            playerGuiOpenStatus.put(playerName, true);

            // Set game mode to SPECTATOR for GUI interaction
            serverPlayer.setGameMode(GameType.SPECTATOR);
        } else {
            ReignOfNether.LOGGER.warn("serverPlayer is null, cannot open topdown GUI");
        }
    }

    public static void closeTopdownGui(int playerId) {
        ServerPlayer serverPlayer = getPlayerById(playerId);

        if (serverPlayer != null) {
            String playerName = serverPlayer.getName().getString();

            // Ensure player had GUI open before attempting to close
            if (Boolean.TRUE.equals(playerGuiOpenStatus.get(playerName))) {
                // Restore the player’s original game mode if saved
                GameType originalGameType = playerDefaultGameModes.remove(playerName);

                if (originalGameType != null) {
                    serverPlayer.setGameMode(originalGameType);
                } else {
                    ReignOfNether.LOGGER.warn("No original game mode found for player {}", playerName);
                }

                if (SandboxServer.isSandboxPlayer(playerName)) {
                    serverPlayer.setGameMode(GameType.CREATIVE);
                }

                // Mark that the GUI is now closed
                playerGuiOpenStatus.remove(playerName);
            } else {
                ReignOfNether.LOGGER.warn("Attempted to close GUI for player {} who didn't have it open", playerName);
            }
        } else {
            ReignOfNether.LOGGER.warn("serverPlayer is null, cannot close topdown GUI");
        }
    }

    public static void movePlayer(int playerId, double x, double y, double z) {
        ServerPlayer serverPlayer = getPlayerById(playerId);
        if (serverPlayer != null && (serverPlayer.isCreative() || serverPlayer.isSpectator())) {
            boolean isOrtho = false;
            for (ServerPlayer op : orthoviewPlayers)
                if (op.getId() == playerId) { isOrtho = true; break; }
            if (isOrtho) {
                net.minecraft.world.level.border.WorldBorder border = serverPlayer.level().getWorldBorder();
                double margin = 1.0D;
                double minX = border.getMinX() + margin;
                double maxX = border.getMaxX() - margin;
                double minZ = border.getMinZ() + margin;
                double maxZ = border.getMaxZ() - margin;
                if (maxX > minX && maxZ > minZ) {
                    x = net.minecraft.util.Mth.clamp(x, minX, maxX);
                    z = net.minecraft.util.Mth.clamp(z, minZ, maxZ);
                }
            }
            serverPlayer.teleportTo(x, y, z);
        }
    }

    public static void sendMessageToAllPlayers(String msg) {
        sendMessageToAllPlayers(msg, false);
    }

    public static void sendMessageToAllPlayers(String msg, int color, boolean bold, Object... formatArgs) {
        for (ServerPlayer player : players) {
            player.sendSystemMessage(Component.literal(""));
            if (bold) {
                player.sendSystemMessage(Component.translatable(msg, formatArgs)
                        .withStyle(Style.EMPTY.withBold(true).withColor(color)));
            } else {
                player.sendSystemMessage(Component.translatable(msg, formatArgs)
                        .withStyle(Style.EMPTY.withBold(false).withColor(color)));
            }
            player.sendSystemMessage(Component.literal(""));
        }
    }

    public static void sendMessageToAllPlayers(String msg, boolean bold,  Object... formatArgs) {
        sendMessageToAllPlayers(msg, 0xFFFFFF, bold, formatArgs);
    }

    public static void sendMessageToAllPlayersNoNewlines(String msg) {
        sendMessageToAllPlayersNoNewlines(msg, false);
    }

    public static void sendMessageToAllPlayersNoNewlines(String msg, boolean bold, Object... formatArgs) {
        for (ServerPlayer player : players) {
            if (bold) {
                player.sendSystemMessage(Component.translatable(msg, formatArgs).withStyle(Style.EMPTY.withBold(true)));
            } else {
                player.sendSystemMessage(Component.translatable(msg, formatArgs));
            }
        }
    }

    public static void sendMessageToPlayerNoNewLines(String playerName, String msg) {
        sendMessageToPlayerNoNewLines(playerName, msg, false);
    }

    public static void sendMessageToPlayer(String playerName, String msg) {
        sendMessageToPlayer(playerName, msg, false);
    }

    public static void sendMessageToPlayerNoNewLines(String playerName, String msg, boolean bold, Object... formatArgs) {
        for (ServerPlayer player : players) {
            if (player.getName().getString().equals(playerName)) {
                if (bold) {
                    player.sendSystemMessage(Component.translatable(msg, formatArgs).withStyle(Style.EMPTY.withBold(true)));
                } else {
                    player.sendSystemMessage(Component.translatable(msg, formatArgs));
                }
                return;
            }
        }
    }

    public static void sendMessageToPlayer(String playerName, String msg, boolean bold, Object... formatArgs) {
        for (ServerPlayer player : players) {
            if (player.getName().getString().equals(playerName)) {
                player.sendSystemMessage(Component.literal(""));
                if (bold) {
                    player.sendSystemMessage(Component.translatable(msg, formatArgs).withStyle(Style.EMPTY.withBold(true)));
                } else {
                    player.sendSystemMessage(Component.translatable(msg, formatArgs));
                }
                player.sendSystemMessage(Component.literal(""));
                return;
            }
        }
    }

    // defeat a player, giving them a defeat screen, removing all their unit/building control and removing them from
    // rtsPlayers
    public static void defeat(int playerId, String reason) {
        for (ServerPlayer player : players) {
            if (player.getId() == playerId) {
                defeat(player.getName().getString(), reason);
                return;
            }
        }
    }

    public static void defeat(String playerName, String reason) {
        if (serverLevel == null || SurvivalResult.isAfterMatch() || SandboxServer.isSandboxPlayer(playerName))
            return;

        boolean playerExists = false;
        for (RTSPlayer rtsPlayer : rtsPlayers) {
            if (rtsPlayer.name.equals(playerName)) {
                playerExists = true;
                break;
            }
        }
        if (!playerExists)
            return;

        ReignOfNether.LOGGER.info("[Player] defeat: playerName={}, reason={}", playerName, reason);

        synchronized (rtsPlayers) {
            // Read the formal mode before removing the session's seat, including its last seat.
            final GameMode matchMode = GameModeServerEvents.getGameMode(serverLevel);
            // Remove the defeated player from the list
            FogOfWarServerEvents.invalidateRtsCache();

            for (ServerPlayer sp : players) {
                if (sp != null && sp.getGameProfile().getName().equals(playerName)) {
                    UnitLoginSync.requestDefeatFog(sp);
                    break;
                }
            }

            rtsPlayers.removeIf(rtsPlayer -> {
                if (rtsPlayer.name.equals(playerName)) {
                    sendMessageToAllPlayers("server.reignofnether.is_defeated", true, playerName, reason);
                    sendMessageToAllPlayers("server.reignofnether.players_remaining", false, (rtsPlayers.size() - 1));

                    postGameRtsPlayers.add(rtsPlayer);

                    PlayerClientboundPacket.defeat(playerName);

                    // Remove ownership from all units and buildings of the defeated player
                    for (LivingEntity entity : UnitServerEvents.getAllUnits()) {
                        if (entity instanceof Unit unit && unit.getOwnerName().equals(playerName)) {
                            unit.resetBehaviours();
                            Unit.resetBehaviours(unit);
                            if (unit instanceof AttackerUnit aUnit)
                                AttackerUnit.resetBehaviours(aUnit);
                            if (unit instanceof WorkerUnit wUnit)
                                WorkerUnit.resetBehaviours(wUnit);
                            unit.setOwnerName("");
                        }
                    }
                    for (BuildingPlacement building : BuildingServerEvents.getBuildings()) {
                        if (building.ownerName.equals(playerName)) {
                            if (building instanceof ProductionPlacement productionBuilding)
                                productionBuilding.productionQueue.clear();
                            building.ownerName = "";
                        }
                    }
                    return true;
                }
                return false;
            });

            // Remove research data and resources associated with the defeated player
            saveRTSPlayers();
            ResearchServerEvents.removeAllResearchFor(playerName);
            ResearchServerEvents.syncResearch(playerName);
            ResearchServerEvents.saveResearch();
            ResearchServerEvents.removeAllCheatsFor(playerName);
            ResourcesServerEvents.removeAccount(playerName);

            // Cooperative wave survival never awards victory for being the last seat/alliance.
            // Losing its last formal seat is a terminal defeat, regardless of the coop gamerule.
            if (matchMode == GameMode.SURVIVAL) {
                if (rtsPlayers.isEmpty() && SurvivalServerEvents.finish(serverLevel,
                        SurvivalResult.Kind.ALL_DEFEATED, Set.of())) broadcastMatchStats(Set.of());
                return;
            }
            // Preserve the existing Classic/Scenario elimination rules.
            if (rtsPlayers.isEmpty()) {
                broadcastMatchStats(Set.of());
            // Check if only allied players are left or if a single player remains.
            } else if (!serverLevel.getServer().getGameRules().getRule(GameRuleRegistrar.COOP_MODE).get()) {
                if (rtsPlayers.size() > 1) {
                    // Get the set of remaining player names
                    Set<String> remainingPlayers = new HashSet<>();
                    for (RTSPlayer player : rtsPlayers) {
                        String name = player.name;
                        remainingPlayers.add(name);
                    }

                    // Use the first remaining player as a reference to find all connected allies
                    String referencePlayer = remainingPlayers.iterator().next();
                    Set<String> factionGroup = AlliancesServerEvents.getAllConnectedAllies(referencePlayer);

                    // Check if all remaining players are part of the same alliance group
                    if (remainingPlayers.equals(factionGroup)) {
                        // Declare victory for all players in the faction group
                        for (String winner : remainingPlayers) {
                            postGameRtsPlayers.add(getRTSPlayer(winner));
                            sendMessageToAllPlayers("server.reignofnether.victory_alliance", true, winner);
                            PlayerClientboundPacket.victory(winner);
                        }
                        broadcastMatchStats(remainingPlayers);
                    }
                } else if (rtsPlayers.size() == 1) {
                    // Single remaining player - declare victory
                    RTSPlayer winner = rtsPlayers.get(0);
                    postGameRtsPlayers.add(winner);
                    sendMessageToAllPlayers("server.reignofnether.victorious", true, winner.name);
                    PlayerClientboundPacket.victory(winner.name);
                    broadcastMatchStats(Set.of(winner.name));
                }
            }
        }
    }

    public static void beaconVictory(String playerName) {
        ReignOfNether.LOGGER.info("[Player] beaconVictory: playerName={}", playerName);
        if (GameModeServerEvents.getGameMode(serverLevel) == GameMode.SURVIVAL) {
            if (!isRTSPlayer(playerName) || SurvivalResult.isAfterMatch()) return;
            // Only a still-active formal seat can receive a victory title. A spectator's stale
            // alliance edge, or a seat already eliminated in this round, is not a victory claim.
            Set<String> winners = new TreeSet<>();
            for (RTSPlayer p : List.copyOf(rtsPlayers))
                if (p.name.equals(playerName) || AlliancesServerEvents.isAllied(playerName, p.name))
                    winners.add(p.name);
            if (!SurvivalServerEvents.finish(serverLevel, SurvivalResult.Kind.BEACON_VICTORY, winners)) return;
            sendMessageToAllPlayers(winners.size() == 1 ? "server.reignofnether.victorious"
                    : "server.reignofnether.victory_alliance", true, playerName);
            for (String name : winners) PlayerClientboundPacket.victory(name);
            broadcastMatchStats(winners);
        } else {
            for (RTSPlayer p : rtsPlayers) {
                String n = p.name;
                if (AlliancesServerEvents.isAllied(playerName, n) || n.equals(playerName)) continue;
                defeat(n, Component.translatable("server.reignofnether.beacon_defeat").getString());
            }
        }
    }

    // Sends the final per-player scoreboard to all clients so they can show the
    // end-of-match stats popup (MatchEndScreen). Winners are those in winnerNames;
    // everyone else (already moved to postGameRtsPlayers on defeat) is a loser.
    public static void broadcastMatchStats(Set<String> winnerNames) {
        var rows = buildMatchStatsRows(winnerNames);
        MatchStatsClientboundPacket.broadcast(rtsGameTicks, rows);
        ReignOfNether.LOGGER.info("[Player] match results sent: ticks={}, participants={}, winners={}",
                rtsGameTicks, rows.size(), rows.stream().filter(row -> row.winner).count());
    }

    /** Point-in-time score copies; active records win name collisions with historical entries. */
    public static List<MatchStatsClientboundPacket.MatchStatRow> buildMatchStatsRows(Set<String> winnerNames) {
        Map<String, RTSPlayer> byName = new TreeMap<>();
        synchronized (rtsPlayers) {
            for (RTSPlayer p : rtsPlayers)
                if (p != null) byName.putIfAbsent(p.name, p);
        }
        synchronized (postGameRtsPlayers) {
            for (RTSPlayer p : postGameRtsPlayers)
                if (p != null) byName.putIfAbsent(p.name, p);
        }
        Map<String, Integer> groups = MatchResultTeams.assign(byName.keySet(), winnerNames,
                AlliancesServerEvents::getAllConnectedAllies);
        List<MatchStatsClientboundPacket.MatchStatRow> rows = new ArrayList<>();
        for (RTSPlayer p : byName.values())
            rows.add(new MatchStatsClientboundPacket.MatchStatRow(
                    p.name, p.faction, winnerNames.contains(p.name), groups.get(p.name),
                    p.scores.getScoreListAsArray()));
        return List.copyOf(rows);
    }

    public static String getBeaconWinTime(String playerName) {
        for (RTSPlayer rtsPlayer : rtsPlayers) {
            if (rtsPlayer.name.equals(playerName)) {
                return TimeUtils.getTimeStrFromTicks(Beacon.getTicksToWin(serverLevel) - rtsPlayer.beaconOwnerTicks);
            }
        }
        return TimeUtils.getTimeStrFromTicks(Beacon.getTicksToWin(serverLevel));
    }

    @SubscribeEvent
    public static void onRegisterCommand(RegisterCommandsEvent evt) {
        AllyCommand.register(evt.getDispatcher());
        RTSPlayerScoresCommand.register(evt.getDispatcher());

        evt.getDispatcher().register(Commands.literal("rts-lock").then(Commands.literal("enable").executes((command) -> {
            if ((command.getSource() != null &&
                command.getSource().getPlayer() != null &&
                command.getSource().getPlayer().hasPermissions(4)) ||
                (command.getSource() != null &&
                !command.getSource().isPlayer())) {
                setRTSLock(true);
                return 1;
            }
            return 0;
        })));

        evt.getDispatcher().register(Commands.literal("rts-lock").then(Commands.literal("disable").executes((command) -> {
            if ((command.getSource() != null &&
                command.getSource().getPlayer() != null &&
                command.getSource().getPlayer().hasPermissions(4)) ||
                (command.getSource() != null &&
                !command.getSource().isPlayer())) {
                setRTSLock(false);
                return 1;
            }
            return 0;
        })));
    }

    public static int resetRTS(boolean hardReset) {
        ReignOfNether.LOGGER.info("[Player] resetRTS: hardReset={}", hardReset);
        GameModeServerEvents.reset(serverLevel.getServer());
        FogChunkSnapshot.clear(serverLevel);
        FogOfWarServerEvents.resetNeutralFogUnits();

        MatchResetPolicy reset = new MatchResetPolicy(hardReset, SandboxServer.isAnyoneASandboxPlayer()
                || serverLevel.getGameRules().getBoolean(GameRuleRegistrar.SCENARIO_MODE));

        synchronized (rtsPlayers) {
            rtsPlayers.clear();
            FogOfWarServerEvents.invalidateRtsCache();

            for (LivingEntity entity : List.copyOf(UnitServerEvents.getAllUnits()))
                if (reset.removeUnit(entity instanceof Unit unit && !Unit.hasAnchor(unit)))
                    entity.kill();

            if (!reset.preserveMap())
                UnitServerEvents.getAllUnits().removeIf(u -> reset.removeUnit(u instanceof Unit unit && !Unit.hasAnchor(unit)));

            if (!reset.preserveMap())
                for (LivingEntity entity : UnitServerEvents.getAllUnits())
                    if (entity instanceof Unit unit)
                        unit.setOwnerName("");

            for (BuildingPlacement building : List.copyOf(BuildingServerEvents.getBuildings())) {
                if (building instanceof ProductionPlacement productionBuilding)
                    productionBuilding.productionQueue.clear();
                if (reset.removeBuilding(building.getBuilding().shouldDestroyOnReset))
                    building.destroy((ServerLevel) building.getLevel());
            }
            if (!reset.preserveMap())
                BuildingServerEvents.getBuildings().removeIf(b -> reset.removeBuilding(b.getBuilding().shouldDestroyOnReset));

            if (!reset.preserveMap())
                for (BuildingPlacement building : BuildingServerEvents.getBuildings())
                    building.ownerName = "";

            ResearchServerEvents.removeAllResearch();
            ResearchServerEvents.removeAllCheats();
            PlayerClientboundPacket.resetRTS(reset);
            StartPosServerEvents.reset(serverLevel);
            if (!TutorialServerEvents.isEnabled()) {
                if (hardReset)
                    sendMessageToAllPlayers(reset.preserveMap() ? "server.reignofnether.match_reset_hard_map"
                            : "server.reignofnether.match_reset_hard", true);
                else
                    sendMessageToAllPlayers("server.reignofnether.match_reset", true);
            }
            ResourcesServerEvents.clearAccounts();
            saveAll();

            if (rtsLocked)
                setRTSLock(false);
            AlliancesServerEvents.resetAllAlliances();
            SurvivalServerEvents.reset();
        }
        HeroServerEvents.fallenHeroes.clear();
        UnitServerEvents.saveFallenHeroUnits(serverLevel);

        for (ServerPlayer player : serverLevel.players())
            player.setGameMode(GameType.SPECTATOR);

        playerDefaultGameModes.replaceAll((key, oldValue) -> GameType.SPECTATOR);
        AlliancesServerEvents.playersWithAlliedControl.clear();
        TimeServerEvents.resetBloodMoon();

        for (BuildingPlacement bpl : BuildingServerEvents.getBuildings())
            if (bpl instanceof CustomBuildingPlacement cbpl)
                cbpl.resetAllCommands();

        return 1;
    }

    public static void publishScenarioMap() {
        if (ScenarioServerEvents.getNumScenarioUnits() == 0 && ScenarioServerEvents.getNumScenarioBuildings() == 0) {
            sendMessageToAllPlayers("server.reignofnether.scenario_published_error1");
            return;
        }

        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (le instanceof Unit unit) {
                ScenarioRole role = ScenarioUtils.getScenarioRole(false, unit.getScenarioRoleIndex());
                unit.setOwnerName(role != null ? role.name : "");
            }
        }
        for (BuildingPlacement bpl : BuildingServerEvents.getBuildings()) {
            ScenarioRole role = ScenarioUtils.getScenarioRole(false, bpl.scenarioRoleIndex);
            bpl.ownerName = role != null ? role.name : "";
        }
        serverLevel.getGameRules().getRule(GameRuleRegistrar.SCENARIO_MODE).set(true, serverLevel.getServer());
        FogOfWarServerEvents.resetNeutralFogUnits();

        synchronized (rtsPlayers) {
            rtsPlayers.clear();
            FogOfWarServerEvents.invalidateRtsCache();

            for (BuildingPlacement building : BuildingServerEvents.getBuildings()) {
                if (building instanceof ProductionPlacement productionBuilding)
                    productionBuilding.productionQueue.clear();
            }
            ResearchServerEvents.removeAllResearch();
            ResearchServerEvents.removeAllCheats();
            ResourcesServerEvents.clearAccounts();
            saveAll();

            if (rtsLocked)
                setRTSLock(false);
            if (serverLevel.getGameRules().getRule(GameRuleRegistrar.SCENARIO_MODE).get())
                AlliancesServerEvents.applyScenarioAlliances();
            if (serverLevel.getGameRules().getRule(GameRuleRegistrar.COOP_MODE).get())
                AlliancesServerEvents.applyCoopAlliances();
            SurvivalServerEvents.reset();
        }
        HeroServerEvents.fallenHeroes.clear();
        UnitServerEvents.saveFallenHeroUnits(serverLevel);

        for (ServerPlayer player : serverLevel.players())
            player.setGameMode(GameType.SPECTATOR);

        playerDefaultGameModes.replaceAll((key, oldValue) -> GameType.SPECTATOR);
        AlliancesServerEvents.playersWithAlliedControl.clear();
        TimeServerEvents.resetBloodMoon();

        // Publish only after the server has saved the new owners, finished clearing the old match,
        // and made the viewers spectators. The regular packets apply level and visibility checks.
        for (BuildingPlacement building : BuildingServerEvents.getBuildings())
            BuildingClientboundPacket.syncBuilding(building);
        for (LivingEntity entity : UnitServerEvents.getAllUnits())
            if (entity instanceof Unit unit) UnitSyncClientboundPacket.sendScenarioPublication(unit);
        PlayerClientboundPacket.publishScenarioMap();
        sendMessageToAllPlayers("server.reignofnether.scenario_published", true);
        sendMessageToAllPlayers("server.reignofnether.scenario_published_tooltip1");
        sendMessageToAllPlayers("server.reignofnether.scenario_published_tooltip2");
        GameruleClientboundPacket.setScenarioMode(true);
        GameModeClientboundPacket.setAndLockAllClientGameModes(GameMode.SCENARIO);
    }

    public static void clearMatchHistory() {
        postGameRtsPlayers.clear();
        rtsGameTicks = 0;
    }

    private static void saveAll() {
        // Only called by reset/publish, never by the normal periodic save.
        clearMatchHistory();
        saveRTSPlayers();
        saveBuildings(serverLevel);
        BuildingServerEvents.saveNetherZones(serverLevel);
        UnitServerEvents.saveGatherTargets(serverLevel);
        ResourcesServerEvents.saveResources(serverLevel);
        ResearchServerEvents.saveResearch();
    }

    public static void setRTSLock(boolean lock) {
        setRTSLock(lock, false);
    }

    public static void setRTSLock(boolean lock, boolean noMsg) {
        rtsLocked = lock;
        if (serverLevel == null) return;
        // A manual toggle is an explicit policy change, including between matches. Snapshot it
        // with the current roster (a readied batch only calls this after every grant).
        saveRTSPlayers();
        serverLevel.players().forEach(p -> {
            if (rtsLocked) {
                PlayerClientboundPacket.lockRTS(p.getName().getString());
            } else {
                PlayerClientboundPacket.unlockRTS(p.getName().getString());
            }
        });
        if (!noMsg) {
            if (rtsLocked) {
                sendMessageToAllPlayers("server.reignofnether.match_locked");
            } else {
                sendMessageToAllPlayers("server.reignofnether.match_unlocked");
            }
        }
    }

    public static void setRTSSyncingEnabled(boolean enable) {
        rtsSyncingEnabled = enable;
        if (rtsSyncingEnabled) {
            sendMessageToAllPlayers("server.reignofnether.sync_enabled");
        } else {
            sendMessageToAllPlayers("server.reignofnether.sync_disabled");
        }
    }

    public static void updateMarketRates(String playerName) {
        for (RTSPlayer rtsPlayer : rtsPlayers) {
            if (rtsPlayer.name.equals(playerName)) {
                for (TradeAction tradeAction : rtsPlayer.tradeRates.keySet()) {
                    PlayerClientboundPacket.setMarketRate(tradeAction, rtsPlayer.name, rtsPlayer.tradeRates.get(tradeAction));
                }
                return;
            }
        }
    }

    public static void updateMarketRates(int playerId) {
        for (RTSPlayer rtsPlayer : rtsPlayers) {
            if (rtsPlayer.id == playerId) {
                for (TradeAction tradeAction : rtsPlayer.tradeRates.keySet()) {
                    PlayerClientboundPacket.setMarketRate(tradeAction, rtsPlayer.name, rtsPlayer.tradeRates.get(tradeAction));
                }
                return;
            }
        }
    }
}
