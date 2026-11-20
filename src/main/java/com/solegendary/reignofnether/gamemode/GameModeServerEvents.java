package com.solegendary.reignofnether.gamemode;

import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.survival.SurvivalServerEvents;
import com.solegendary.reignofnether.sandbox.SandboxEditorAccess;
import com.solegendary.reignofnether.tutorial.TutorialServerEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

/** Mode selection is a per-connection proposal, never a client-controlled global lock.
 * Successful admission commits it. Existing saves derive the mode from authoritative state. */
public class GameModeServerEvents {
    private static final int PROPOSAL_TICKS = 200;
    private static final int LEGACY_HANDSHAKE_TICKS = 100;
    private static final Map<MinecraftServer, Map<UUID, Proposal>> PROPOSALS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Session> SESSIONS = new IdentityHashMap<>();
    private record Proposal(ServerPlayer player, ServerGamePacketListenerImpl connection, GameMode mode,
                            int expiresAt, boolean legacy) { }
    public record StartChoice(GameMode mode, boolean legacy) { }
    private static final class Session {
        GameMode mode; final ServerPlayer starter; final ServerGamePacketListenerImpl connection;
        final int legacyUntil; int survivalUntil; final RTSPlayer slot; boolean awaitingLegacy;
        Session(ServerPlayer starter, StartChoice choice) {
            this.mode=choice.mode();this.starter=starter;this.connection=starter.connection;
            this.slot=PlayerServerEvents.getRTSPlayer(starter.getGameProfile().getName());
            this.legacyUntil=starter.getServer().getTickCount()+LEGACY_HANDSHAKE_TICKS;
            this.survivalUntil=this.legacyUntil;
            this.awaitingLegacy=choice.legacy()&&choice.mode()==GameMode.CLASSIC;
        }
    }
    private static boolean current(ServerPlayer player) {
        return player != null && player.connection != null
                && ServerPlayerRequest.isCurrent(player, player.connection.connection);
    }
    private static boolean allowed(ServerPlayer player, GameMode mode) {
        if (!current(player) || player.serverLevel().dimension()!=Level.OVERWORLD
                || com.solegendary.reignofnether.survival.SurvivalResult.isAfterMatch()
                || PlayerServerEvents.rtsLocked || mode==null || mode==GameMode.NONE) return false;
        if (mode == GameMode.SANDBOX && !SandboxEditorAccess.mayEnter(player, player.connection.connection)) return false;
        boolean scenario=player.getServer().getGameRules().getBoolean(GameRuleRegistrar.SCENARIO_MODE);
        if (scenario != (mode==GameMode.SCENARIO)) return false;
        if ((mode==GameMode.SANDBOX || mode==GameMode.SURVIVAL)
                && player.getServer().getGameRules().getBoolean(GameRuleRegistrar.PVP_MODES_ONLY)) return false;
        return mode!=GameMode.SURVIVAL || !TutorialServerEvents.isEnabled();
    }
    public static GameMode getGameMode(ServerLevel level) {
        if (level.getServer().getGameRules().getBoolean(GameRuleRegistrar.SCENARIO_MODE)) return GameMode.SCENARIO;
        for (RTSPlayer player:PlayerServerEvents.rtsPlayers) if(player.faction==Factions.NONE) return GameMode.SANDBOX;
        if (SurvivalServerEvents.isEnabled()) return GameMode.SURVIVAL;
        Session session=SESSIONS.get(level.getServer());
        if (session!=null && PlayerServerEvents.rtsPlayers.contains(session.slot)) return session.mode;
        return GameMode.CLASSIC;
    }
    private static boolean hasMatch() { return !PlayerServerEvents.rtsPlayers.isEmpty() || SurvivalServerEvents.isEnabled(); }
    private static Map<UUID,Proposal> proposals(MinecraftServer server) {
        Map<UUID,Proposal> proposals=PROPOSALS.computeIfAbsent(server,s->new HashMap<>());
        proposals.values().removeIf(p->!current(p.player()) || p.player().getServer()!=server
                || p.player().connection!=p.connection() || server.getTickCount()-p.expiresAt()>=0);
        return proposals;
    }
    public static boolean requestMode(ServerPlayer player, GameMode mode) {
        if (!allowed(player,mode)) { cancelProposal(player);return false; }
        MinecraftServer server=player.getServer();
        if (!hasMatch()) {
            SESSIONS.remove(server);
            Map<UUID,Proposal> proposals=proposals(server);
            if (proposals.size()>=16_384&&!proposals.containsKey(player.getUUID())) return false;
            proposals.put(player.getUUID(),new Proposal(player,player.connection,mode,server.getTickCount()+PROPOSAL_TICKS,false));
            return true; // no broadcast, no reservation/global lock
        }
        GameMode actual=getGameMode(player.serverLevel());
        Session session=SESSIONS.get(server);
        // API64 clients send START, then MODE, then SURVIVAL. Only the exact successful first
        // starter gets one bounded finalization; unrelated clients cannot turn CLASSIC into SURVIVAL.
        boolean legacy=session!=null && PlayerServerEvents.rtsPlayers.contains(session.slot) && session.awaitingLegacy && session.starter==player
                && session.connection==player.connection && server.getTickCount()-session.legacyUntil<0
                && PlayerServerEvents.rtsPlayers.size()==1
                && PlayerServerEvents.rtsPlayers.get(0).name.equals(player.getGameProfile().getName());
        if (mode!=actual) {
            if (!legacy || actual!=GameMode.CLASSIC || mode!=GameMode.SURVIVAL) return false;
            session.mode=mode;
            session.survivalUntil=server.getTickCount()+LEGACY_HANDSHAKE_TICKS;
        }
        if (session!=null && session.starter==player) session.awaitingLegacy=false;
        GameModeClientboundPacket.setAndLockAllClientGameModes(getGameMode(player.serverLevel()));
        return true;
    }
    /** Preserve the old wire order without giving direct server APIs a post-start override window. */
    public static void preparePacketStart(ServerPlayer player, Faction faction) {
        if (!current(player) || hasMatch()) return;
        Map<UUID,Proposal> proposals=proposals(player.getServer());
        if (!proposals.containsKey(player.getUUID())) {
            GameMode mode=faction==Factions.NONE?GameMode.SANDBOX:GameMode.CLASSIC;
            if (allowed(player,mode)) proposals.put(player.getUUID(),new Proposal(player,player.connection,mode,
                    player.getServer().getTickCount()+PROPOSAL_TICKS,true));
        }
    }
    public static StartChoice takeStartChoice(ServerPlayer player, Faction faction, boolean scenario) {
        if (!current(player)) return null;
        return chooseStart(player, faction, scenario, proposals(player.getServer()).remove(player.getUUID()));
    }
    /** Validate a lobby seat without consuming or expiring any player's pending mode choice. */
    public static StartChoice previewStartChoice(ServerPlayer player, Faction faction, boolean scenario) {
        if (!current(player)) return null;
        MinecraftServer server = player.getServer();
        Proposal proposal = PROPOSALS.getOrDefault(server, Map.of()).get(player.getUUID());
        if (proposal != null && (proposal.player() != player || proposal.connection() != player.connection
                || server.getTickCount() - proposal.expiresAt() >= 0)) proposal = null;
        return chooseStart(player, faction, scenario, proposal);
    }
    private static StartChoice chooseStart(ServerPlayer player, Faction faction, boolean scenario, Proposal proposal) {
        GameMode mode=proposal!=null?proposal.mode():(scenario?GameMode.SCENARIO:
                hasMatch()?getGameMode(player.serverLevel()):faction==Factions.NONE?GameMode.SANDBOX:GameMode.CLASSIC);
        if (!allowed(player,mode) || scenario!=(mode==GameMode.SCENARIO)
                || (!scenario && (faction==Factions.NONE)!=(mode==GameMode.SANDBOX))) return null;
        if (hasMatch() && mode!=getGameMode(player.serverLevel())) return null;
        return new StartChoice(mode,proposal!=null&&proposal.legacy());
    }
    public static void commitStart(ServerPlayer player, StartChoice choice, boolean first) {
        if (!current(player) || choice==null) return;
        if (first) SESSIONS.put(player.getServer(),new Session(player,choice));
        GameModeClientboundPacket.setAndLockAllClientGameModes(getGameMode(player.serverLevel()));
    }
    public static boolean mayEnableSurvival(ServerPlayer player) {
        if (!allowed(player,GameMode.SURVIVAL) || SurvivalServerEvents.isEnabled()) return false;
        Session session=SESSIONS.get(player.getServer());
        return session!=null && session.mode==GameMode.SURVIVAL && session.starter==player
                && session.connection==player.connection && player.getServer().getTickCount()-session.survivalUntil<0
                && PlayerServerEvents.rtsPlayers.contains(session.slot)
                && PlayerServerEvents.isRTSPlayer(player.getGameProfile().getName());
    }
    public static void cancelProposal(ServerPlayer player) {
        if(player==null||player.getServer()==null)return;
        Map<UUID,Proposal> proposals=PROPOSALS.get(player.getServer());
        if(proposals!=null)proposals.remove(player.getUUID());
    }
    public static void reset(MinecraftServer server) { PROPOSALS.remove(server);SESSIONS.remove(server); }
    @SubscribeEvent public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && hasMatch())
            GameModeClientboundPacket.setAndLockAllClientGameModes(getGameMode(player.serverLevel()));
    }
    private static void abandonUninitializedSurvival(MinecraftServer server, Session session) {
        if (session.mode==GameMode.SURVIVAL && !SurvivalServerEvents.isEnabled()
                && PlayerServerEvents.rtsPlayers.contains(session.slot)) {
            session.mode=GameMode.CLASSIC;session.awaitingLegacy=false;
            GameModeClientboundPacket.setAndLockAllClientGameModes(GameMode.CLASSIC);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!event.getServer().isSameThread())return;
        MinecraftServer server=event.getServer();Session session=SESSIONS.get(server);
        if(session==null)return;
        if(!PlayerServerEvents.rtsPlayers.contains(session.slot)){SESSIONS.remove(server);return;}
        if(server.getTickCount()-session.legacyUntil>=0)session.awaitingLegacy=false;
        if(server.getTickCount()-session.survivalUntil>=0 || !current(session.starter)
                || session.starter.connection!=session.connection)abandonUninitializedSurvival(server,session);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof ServerPlayer player){
            cancelProposal(player);
            Session session=SESSIONS.get(player.getServer());
            if(session!=null&&session.starter==player)abandonUninitializedSurvival(player.getServer(),session);
        }
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { reset(event.getServer()); }
}
