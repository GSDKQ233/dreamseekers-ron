package com.solegendary.reignofnether.scenario;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;

public class ScenarioServerEvents {

    // Backwards-compatible current-world view. Authoritative roles belong to that
    // world's SavedData; no role or list is copied out of a previous world's view.
    public static ArrayList<ScenarioRole> scenarioRoles = new ArrayList<>();
    private static ServerLevel activeWorld;

    static boolean isActiveWorld(ServerLevel level) {
        return level != null && level == activeWorld;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent evt) {
        saveScenarioRoles(evt.getServer().overworld());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent evt) {
        if (activeWorld != null && activeWorld.getServer() == evt.getServer()) {
            activeWorld = null;
            scenarioRoles = new ArrayList<>();
        }
    }

    public static void saveScenarioRoles() {
        saveScenarioRoles(activeWorld);
    }

    public static void saveScenarioRoles(ServerLevel level) {
        if (!isActiveWorld(level) || !level.getServer().isSameThread()) return;
        ScenarioRoleSaveData.getInstance(level).setDirty();
        level.getDataStorage().save();
    }

    @SubscribeEvent(priority = EventPriority.LOW) // roster and persisted alliances load first
    public static void loadScenarioRoles(ServerStartedEvent evt) {
        ServerLevel level = evt.getServer().overworld();
        ScenarioRoleSaveData data = ScenarioRoleSaveData.getInstance(level);
        scenarioRoles = data.scenarioRoleSaves;
        activeWorld = level;
        // Fresh/absent role data is not a request to discard this world's saved alliances.
        if (data.hadSavedRoles()) {
            if (level.getGameRules().getBoolean(GameRuleRegistrar.SCENARIO_MODE))
                AlliancesServerEvents.applyScenarioAlliances();
            if (level.getGameRules().getBoolean(GameRuleRegistrar.COOP_MODE))
                AlliancesServerEvents.applyCoopAlliances();
        }
        ReignOfNether.LOGGER.info("Loaded {} world-scoped scenario roles", scenarioRoles.size());
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        // The login event already runs on the server thread after the PLAY session exists.
        // Never schedule work against a process-wide list on a wall-clock executor.
        if (evt.getEntity() instanceof ServerPlayer player && activeWorld != null
                && player.getServer() == activeWorld.getServer()) {
            sendRoles(PacketDistributor.PLAYER.with(() -> player));
        }
    }

    public static void syncScenarioRoles() {
        if (activeWorld != null && activeWorld.getServer().isSameThread()) {
            sendRoles(PacketDistributor.ALL.noArg());
        }
    }

    private static void sendRoles(PacketDistributor.PacketTarget target) {
        for (ScenarioRole role : scenarioRoles) {
            role.packNbt();
            PacketHandler.INSTANCE.send(target, new ScenarioClientboundPacket(
                    ScenarioAction.LOAD_SCENARIO_ROLE, role.nbt.copy()
            ));
        }
    }

    public static int getNumScenarioUnits(ScenarioRole role) {
        int count = 0;
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (role != null && le instanceof Unit unit && unit.getScenarioRoleIndex() == role.index) {
                count++;
            }
        }
        return count;
    }

    public static int getNumScenarioBuildings(ScenarioRole role) {
        int count = 0;
        for (BuildingPlacement bpl : BuildingServerEvents.getBuildings()) {
            if (role != null && bpl.scenarioRoleIndex == role.index) {
                count++;
            }
        }
        return count;
    }

    public static int getNumScenarioUnits() {
        int count = 0;
        for (LivingEntity le : UnitServerEvents.getAllUnits()) {
            if (le instanceof Unit unit && unit.getScenarioRoleIndex() >= 0) {
                count++;
            }
        }
        return count;
    }

    public static int getNumScenarioBuildings() {
        int count = 0;
        for (BuildingPlacement bpl : BuildingServerEvents.getBuildings()) {
            if (bpl.scenarioRoleIndex >= 0) {
                count++;
            }
        }
        return count;
    }

    public static boolean isScenarioStarted(ServerLevel level) {
        return !PlayerServerEvents.rtsPlayers.isEmpty() && level.getGameRules().getRule(GameRuleRegistrar.SCENARIO_MODE).get();
    }
}

