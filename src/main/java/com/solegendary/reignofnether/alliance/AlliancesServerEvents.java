package com.solegendary.reignofnether.alliance;

import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.scenario.ScenarioRole;
import com.solegendary.reignofnether.scenario.ScenarioServerEvents;
import com.solegendary.reignofnether.scenario.ScenarioUtils;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

public class AlliancesServerEvents {
    private static final Map<String, Set<String>> alliances = new HashMap<>();
    public static final Map<String, Set<String>> pendingAlliances = new HashMap<>();
    public static final HashSet<String> playersWithAlliedControl = new HashSet<>();
    private static AllianceSaveData savedData;

    // Load before scenario/coop's NORMAL-priority rebuild, which remains authoritative.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onServerStarted(ServerStartedEvent event) {
        alliances.clear();
        pendingAlliances.clear();
        playersWithAlliedControl.clear();
        savedData = AllianceSaveData.getInstance(event.getServer().overworld());
        savedData.snapshot().forEach((owner, allies) -> alliances.put(owner, new HashSet<>(allies)));
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        // World storage has already been saved. Never erase persistent data on shutdown.
        savedData = null;
        alliances.clear();
        pendingAlliances.clear();
        playersWithAlliedControl.clear();
    }

    private static void persistAlliances() {
        if (savedData != null)
            savedData.replace(alliances);
    }

    public static boolean canControlAlly(String player, LivingEntity entity) {
        return entity instanceof Unit unit && canControlAlly(player, unit.getOwnerName());
    }
    public static boolean canControlAlly(String player, Unit unit) {
        return canControlAlly(player, unit.getOwnerName());
    }
    public static boolean canControlAlly(String player, String ownerName) {
        return (AlliancesServerEvents.isAllied(player, ownerName) &&
                AlliancesServerEvents.playersWithAlliedControl.contains(ownerName));
    }

    public static void addAlliance(String owner1, String owner2) {
        if (!owner1.equals(owner2)) {
            clearPending(owner1, owner2);
            clearPending(owner2, owner1);
            if (!alliances.computeIfAbsent(owner1, k -> new HashSet<>()).add(owner2)) return;
            alliances.computeIfAbsent(owner2, k -> new HashSet<>()).add(owner1);
            persistAlliances();
            AllianceClientboundPacket.addAlliance(owner1, owner2);
        }
    }

    public static void removeAlliance(String owner1, String owner2) {
        clearPending(owner1, owner2);
        clearPending(owner2, owner1);
        Set<String> alliesOfOwner1 = alliances.get(owner1);
        if (alliesOfOwner1 != null) {
            alliesOfOwner1.remove(owner2);
            if (alliesOfOwner1.isEmpty()) {
                alliances.remove(owner1);
            }
        }
        Set<String> alliesOfOwner2 = alliances.get(owner2);
        if (alliesOfOwner2 != null) {
            alliesOfOwner2.remove(owner1);
            if (alliesOfOwner2.isEmpty()) {
                alliances.remove(owner2);
            }
        }
        persistAlliances();
        AllianceClientboundPacket.removeAlliance(owner1, owner2);
    }

    /** Consume the directed recipient <- requester invitation, including its client mirror. */
    public static boolean clearPending(String recipient, String requester) {
        if (!DirectedInvitations.remove(pendingAlliances, recipient, requester)) return false;
        AllianceClientboundPacket.cancelPendingAlliance(recipient, requester);
        return true;
    }

    public static boolean isAlliedOrOwned(String owner1, String owner2) {
        return isAllied(owner1, owner2) || owner1.equals(owner2);
    }

    public static boolean isAllied(String owner1, String owner2) {
        return alliances.getOrDefault(owner1, Collections.emptySet()).contains(owner2);
    }

    // New method to retrieve direct allies
    public static Set<String> getAllAllies(String owner) {
        return Collections.unmodifiableSet(alliances.getOrDefault(owner, Collections.emptySet()));
    }

    // New method to retrieve all connected allies in an alliance
    public static Set<String> getAllConnectedAllies(String owner) {
        Set<String> allAllies = new HashSet<>();
        findAllConnectedAllies(owner, allAllies);
        return allAllies;
    }

    private static void findAllConnectedAllies(String owner, Set<String> visited) {
        if (!visited.contains(owner)) {
            visited.add(owner);
            for (String ally : getAllAllies(owner)) {
                findAllConnectedAllies(ally, visited);
            }
        }
    }

    public static void applyScenarioAlliances() {
        Map<String, Integer> nameAndTeam = new HashMap<>();
        for (ScenarioRole role : ScenarioServerEvents.scenarioRoles) {
            nameAndTeam.put(role.name, role.teamNumber);
        }
        for (RTSPlayer rtsPlayer : PlayerServerEvents.rtsPlayers) {
            ScenarioRole role = ScenarioUtils.getScenarioRole(false, rtsPlayer.scenarioRoleIndex);
            if (role != null)
                nameAndTeam.put(rtsPlayer.name, role.teamNumber);
        }

        replaceModeGraph(AllianceEdges.withEqualTeams(nameAndTeam));
    }

    private static void replaceModeGraph(Set<AllianceEdges.Edge> wanted) {
        Set<AllianceEdges.Edge> previous = AllianceEdges.fromGraph(alliances);
        for (var edge : previous)
            if (!wanted.contains(edge)) removeAlliance(edge.first(), edge.second());
        for (var edge : wanted)
            if (!previous.contains(edge)) addAlliance(edge.first(), edge.second());
    }

    // ally all RTS players, unless they have an NPC scenario role
    public static void applyCoopAlliances() {
        Map<String, Integer> teams = new HashMap<>();

        for (RTSPlayer rtsPlayer : PlayerServerEvents.rtsPlayers) {
            ScenarioRole role = ScenarioUtils.getScenarioRole(false, rtsPlayer.scenarioRoleIndex);
            if (role == null || !role.isNpc)
                teams.put(rtsPlayer.name, 0);
        }
        replaceModeGraph(AllianceEdges.withEqualTeams(teams));
    }

    public static void resetAllAlliances() {
        alliances.clear();
        pendingAlliances.clear();
        playersWithAlliedControl.clear();
        persistAlliances();
        AllianceClientboundPacket.resetAlliances();
    }

    /** Replace just this connection's public graph, session grants and its own directed invitations. */
    public static void syncAlliances(net.minecraft.server.level.ServerPlayer player) {
        String name = player.getGameProfile().getName();
        AllianceClientboundPacket.sendTo(player, AllianceAction.DISBAND, "", "", true);
        for (var edge : AllianceEdges.fromGraph(alliances))
            AllianceClientboundPacket.sendTo(player, AllianceAction.ACCEPT_REQUEST, edge.first(), edge.second(), true);
        for (String owner : playersWithAlliedControl)
            AllianceClientboundPacket.sendTo(player, AllianceAction.SET_ALLY_CONTROL, owner, "", true);
        DirectedInvitations.forViewer(pendingAlliances, name, (to, from) ->
                AllianceClientboundPacket.sendTo(player, AllianceAction.REQUEST, to, from, true));
    }

    /** Compatibility entry point for an explicit whole-server resync; never used as the login path. */
    public static void syncAlliances() {
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.getPlayerList().getPlayers().forEach(AlliancesServerEvents::syncAlliances);
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent evt) {
        if (evt.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) syncAlliances(player);
    }
}
