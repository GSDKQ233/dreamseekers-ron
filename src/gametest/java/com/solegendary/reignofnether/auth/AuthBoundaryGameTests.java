package com.solegendary.reignofnether.auth;

import com.solegendary.reignofnether.sandbox.SandboxEditorTestGrant;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.gamemode.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.research.economy.ResearchEconomyFixtures.Peer;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.tutorial.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.*;
import net.minecraftforge.gametest.*;
import net.minecraftforge.network.NetworkEvent;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;

/** Baseline-compatible API64 negative controls. Uses real codec, authenticated Forge Context,
 * and registered server-side handlers; not a real socket, client UI, or dual-client acceptance. */
@GameTestHolder("api65_auth") @PrefixGameTestTemplate(false)
public final class AuthBoundaryGameTests {
    static final class Fixture implements AutoCloseable {
        final GameTestHelper h; final Peer peer; final ServerPlayer actor; final NetworkEvent.Context context;
        final String name; final ServerLevel previousLevel;
        final List<ServerPlayer> previousPlayers=new ArrayList<>(PlayerServerEvents.players);
        final List<ServerPlayer> previousOrtho=new ArrayList<>(PlayerServerEvents.orthoviewPlayers);
        final List<RTSPlayer> previousRts=new ArrayList<>(PlayerServerEvents.rtsPlayers);
        final List<Resources> previousAccounts=new ArrayList<>(ResourcesServerEvents.resourcesList);
        final boolean oldLock=PlayerServerEvents.rtsLocked,oldSync=PlayerServerEvents.rtsSyncingEnabled;
        final boolean oldPvp,oldScenario; final long day;
        Fixture(GameTestHelper h)throws Exception {
            this.h=h;name="Auth"+Long.toUnsignedString(h.absolutePos(BlockPos.ZERO).asLong(),36);
            previousLevel=PlayerServerEvents.serverLevel;PlayerServerEvents.serverLevel=h.getLevel();
            oldPvp=h.getLevel().getGameRules().getBoolean(GameRuleRegistrar.PVP_MODES_ONLY);
            oldScenario=h.getLevel().getGameRules().getBoolean(GameRuleRegistrar.SCENARIO_MODE);day=h.getLevel().getDayTime();
            PlayerServerEvents.rtsLocked=false;PlayerServerEvents.rtsSyncingEnabled=true;
            h.getLevel().getGameRules().getRule(GameRuleRegistrar.PVP_MODES_ONLY).set(false,h.getLevel().getServer());
            h.getLevel().getGameRules().getRule(GameRuleRegistrar.SCENARIO_MODE).set(false,h.getLevel().getServer());
            PlayerServerEvents.players.clear();PlayerServerEvents.orthoviewPlayers.clear();PlayerServerEvents.rtsPlayers.clear();
            peer=new Peer(h.getLevel(),name);actor=h.getLevel().getServer().getPlayerList().getPlayerByName(name);
            Field f=Peer.class.getDeclaredField("context");f.setAccessible(true);context=(NetworkEvent.Context)f.get(peer);
            PlayerServerEvents.players.add(actor);
            h.assertTrue(!actor.hasPermissions(4),"fixture is an authenticated non-OP player");peer.drain();
        }
        void send(Object packet)throws Exception {
            FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());
            try {packet.getClass().getMethod("encode",FriendlyByteBuf.class).invoke(packet,b);
                Object decoded=packet.getClass().getConstructor(FriendlyByteBuf.class).newInstance(b);
                h.assertTrue(b.readableBytes()==0,"complete real packet codec");
                try{decoded.getClass().getMethod("handle",Supplier.class).invoke(decoded,(Supplier<NetworkEvent.Context>)()->context);}
                catch(InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw e;}
            }finally{b.release();}peer.drain();
        }
        PlayerServerboundPacket action(PlayerAction a,int id){return new PlayerServerboundPacket(a,id,0d,80d,0d);}
        RTSPlayer existing(int oldId,com.solegendary.reignofnether.faction.Faction faction) {
            RTSPlayer p=RTSPlayer.getNewPlayer(name,faction,oldId,0,true,-1L,-1L);PlayerServerEvents.rtsPlayers.add(p);return p;
        }
        public void close(){
            PlayerServerEvents.orthoviewPlayers.clear();PlayerServerEvents.orthoviewPlayers.addAll(previousOrtho);
            PlayerServerEvents.players.clear();PlayerServerEvents.players.addAll(previousPlayers);
            PlayerServerEvents.rtsPlayers.clear();PlayerServerEvents.rtsPlayers.addAll(previousRts);
            ResourcesServerEvents.resourcesList.clear();ResourcesServerEvents.resourcesList.addAll(previousAccounts);
            ResearchServerEvents.removeAllCheatsFor(name);
            PlayerServerEvents.rtsLocked=oldLock;PlayerServerEvents.rtsSyncingEnabled=oldSync;
            h.getLevel().getGameRules().getRule(GameRuleRegistrar.PVP_MODES_ONLY).set(oldPvp,h.getLevel().getServer());
            h.getLevel().getGameRules().getRule(GameRuleRegistrar.SCENARIO_MODE).set(oldScenario,h.getLevel().getServer());
            h.getLevel().setDayTime(day);PlayerServerEvents.saveRTSPlayers();PlayerServerEvents.serverLevel=previousLevel;peer.close();
        }
    }
    @GameTest(templateNamespace="api65_auth",template="ore_mine_test_arena",batch="auth_tutorial")
    public static void ordinaryWorldRejectsTutorialTimePacket(GameTestHelper h)throws Exception {
        try(var f=new Fixture(h)){h.assertTrue(!TutorialServerEvents.isEnabled(),"ordinary test world, no tutorial");h.getLevel().setDayTime(7800);
            f.send(new TutorialServerboundPacket(TutorialAction.SET_DAY_TIME,TutorialStage.INTRO));
            h.assertTrue(h.getLevel().getDayTime()==7800,"non-tutorial packet must not change world time");}h.succeed();
    }
    @GameTest(templateNamespace="api65_auth",template="ore_mine_test_arena",batch="auth_admin")
    public static void nonOpCannotLockTheMatch(GameTestHelper h)throws Exception {
        try(var f=new Fixture(h)){f.send(f.action(PlayerAction.LOCK_RTS,-1));h.assertTrue(!PlayerServerEvents.rtsLocked,"non-OP management packet rejected");}h.succeed();
    }
    @GameTest(templateNamespace="api65_auth",template="ore_mine_test_arena",batch="auth_sentinel")
    public static void invalidPlayerSentinelCannotPolluteOrthoview(GameTestHelper h)throws Exception {
        try(var f=new Fixture(h)){f.send(f.action(PlayerAction.ENABLE_ORTHOVIEW,-1));
            h.assertTrue(!PlayerServerEvents.orthoviewPlayers.contains(null),"invalid player id cannot persist null");
            f.send(f.action(PlayerAction.ENABLE_ORTHOVIEW,f.actor.getId()));
            h.assertTrue(PlayerServerEvents.orthoviewPlayers.contains(f.actor),"own valid camera action remains available");}h.succeed();
    }
    @GameTest(templateNamespace="api65_auth",template="ore_mine_test_arena",batch="auth_pvp")
    public static void pvpOnlyRejectsSandboxAdmission(GameTestHelper h)throws Exception {
        try(var f=new Fixture(h); var grant=new SandboxEditorTestGrant(f.actor, null)){h.getLevel().getGameRules().getRule(GameRuleRegistrar.PVP_MODES_ONLY).set(true,h.getLevel().getServer());
            f.send(new GameModeServerboundPacket(GameMode.SANDBOX));f.send(f.action(PlayerAction.START_RTS_SANDBOX,f.actor.getId()));
            h.assertTrue(PlayerServerEvents.rtsPlayers.isEmpty(),"pvpOnly world cannot admit sandbox through packet");
            h.assertTrue(!ResearchServerEvents.playerHasCheat(f.name,"thereisnospoon"),"denied admission grants no cheats");}h.succeed();
    }
    @GameTest(templateNamespace="api65_auth",template="ore_mine_test_arena",batch="auth_classic")
    public static void activeClassicMatchRejectsSandboxIntruder(GameTestHelper h)throws Exception {
        try(var f=new Fixture(h); var grant=new SandboxEditorTestGrant(f.actor, null)){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(f.name+"Other",Factions.VILLAGERS,f.actor.getId()+10000,0,true,-1L,-1L));
            f.send(new GameModeServerboundPacket(GameMode.SANDBOX));f.send(f.action(PlayerAction.START_RTS_SANDBOX,f.actor.getId()));
            h.assertTrue(PlayerServerEvents.rtsPlayers.size()==1,"active classic mode cannot become shared sandbox");}h.succeed();
    }
    @GameTest(templateNamespace="api65_auth",template="ore_mine_test_arena",batch="auth_rejoin")
    public static void sameNameNewEntityIdCannotStartTwice(GameTestHelper h)throws Exception {
        try(var f=new Fixture(h)){RTSPlayer original=f.existing(f.actor.getId()+10000,Factions.NONE);Resources account=new Resources(f.name,71,72,73);ResourcesServerEvents.resourcesList.add(account);
            f.send(f.action(PlayerAction.START_RTS_SANDBOX,f.actor.getId()));
            h.assertTrue(PlayerServerEvents.rtsPlayers.size()==1&&PlayerServerEvents.rtsPlayers.get(0)==original,"stable authenticated name prevents duplicate roster despite new entity id");
            h.assertTrue(ResourcesServerEvents.resourcesList.contains(account)&&account.food==71&&account.wood==72&&account.ore==73,"rejoin preserves account without reset");}h.succeed();
    }
    @GameTest(templateNamespace="api65_auth",template="ore_mine_test_arena",batch="auth_sandbox_positive")
    public static void legalFirstPlayerCanStillStartSandbox(GameTestHelper h)throws Exception {
        try(var f=new Fixture(h); var grant=new SandboxEditorTestGrant(f.actor, null)){f.send(new GameModeServerboundPacket(GameMode.SANDBOX));f.send(f.action(PlayerAction.START_RTS_SANDBOX,f.actor.getId()));
            h.assertTrue(PlayerServerEvents.rtsPlayers.size()==1&&PlayerServerEvents.rtsPlayers.get(0).faction==Factions.NONE,"invited first non-OP sandbox editor remains allowed");
            h.assertTrue(ResearchServerEvents.playerHasCheat(f.name,"thereisnospoon"),"legal sandbox keeps intended cheats");}h.succeed();
    }
}
