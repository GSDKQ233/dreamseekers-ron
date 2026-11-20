package com.solegendary.reignofnether.auth;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.solegendary.reignofnether.alliance.*;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.gamemode.*;
import com.solegendary.reignofnether.hud.custombutton.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.research.economy.ResearchEconomyFixtures.Peer;
import com.solegendary.reignofnether.scenario.*;
import com.solegendary.reignofnether.startpos.*;
import com.solegendary.reignofnether.survival.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.*;
import java.util.*;

/** API64-compatible runtime regressions and positive controls. Each test has its own sequential batch. */
@GameTestHolder("api65_auth_extended") @PrefixGameTestTemplate(false)
public final class AuthExtendedGameTests {
    static PlayerServerboundPacket start(AuthBoundaryGameTests.Fixture f) {
        BlockPos p=f.h.absolutePos(new BlockPos(3,3,3));
        return new PlayerServerboundPacket(PlayerAction.START_RTS,Factions.VILLAGERS,f.actor.getId(),(double)p.getX(),(double)p.getY(),(double)p.getZ());
    }
    static void cleanupUnits(AuthBoundaryGameTests.Fixture f) {
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(f.name));
        for(var e:List.copyOf(UnitServerEvents.getAllUnits())) if(e instanceof Unit u&&u.getOwnerName().equals(f.name)) {u.setOwnerName("");e.discard();}
    }
    static void survival(AuthBoundaryGameTests.Fixture f, boolean legacy)throws Exception {
        f.h.assertTrue(!SurvivalServerEvents.isEnabled(),"isolated fresh survival test");
        if(!legacy)f.send(new GameModeServerboundPacket(GameMode.SURVIVAL));
        f.send(start(f));
        if(legacy)f.send(new GameModeServerboundPacket(GameMode.SURVIVAL));
        f.send(new SurvivalServerboundPacket(WaveDifficulty.EASY,0));
        f.h.assertTrue(SurvivalServerEvents.isEnabled(),"first non-OP survival start succeeds");
    }
    @GameTest(templateNamespace="api65_auth_extended",template="ore_mine_test_arena",batch="auth_modern_survival")
    public static void modernFirstPlayerSurvivalWorks(GameTestHelper h)throws Exception {
        try(var f=new AuthBoundaryGameTests.Fixture(h)){try{survival(f,false);}finally{SurvivalServerEvents.reset();cleanupUnits(f);}}h.succeed();
    }
    @GameTest(templateNamespace="api65_auth_extended",template="ore_mine_test_arena",batch="auth_legacy_survival")
    public static void legacyFirstPlayerWireOrderSurvivalWorks(GameTestHelper h)throws Exception {
        try(var f=new AuthBoundaryGameTests.Fixture(h)){try{survival(f,true);}finally{SurvivalServerEvents.reset();cleanupUnits(f);}}h.succeed();
    }
    @GameTest(templateNamespace="api65_auth_extended",template="ore_mine_test_arena",batch="auth_survival_intruder")
    public static void existingClassicPlayerCannotEnableSurvival(GameTestHelper h)throws Exception {
        try(var f=new AuthBoundaryGameTests.Fixture(h)){try{f.existing(f.actor.getId(),Factions.VILLAGERS);
            f.send(new SurvivalServerboundPacket(WaveDifficulty.EASY,0));
            h.assertTrue(!SurvivalServerEvents.isEnabled(),"existing CLASSIC match cannot be converted by survival packet");
        }finally{SurvivalServerEvents.reset();}}h.succeed();
    }
    @GameTest(templateNamespace="api65_auth_extended",template="ore_mine_test_arena",batch="auth_wave_guard")
    public static void waveNumberRequiresDebugAuthority(GameTestHelper h)throws Exception {
        try(var f=new AuthBoundaryGameTests.Fixture(h)){try{SurvivalServerEvents.enable(WaveDifficulty.EASY);f.existing(f.actor.getId(),Factions.VILLAGERS);
            int before=SurvivalServerEvents.nextWave.number;f.send(new SurvivalServerboundPacket(WaveDifficulty.BEGINNER,9));
            h.assertTrue(SurvivalServerEvents.nextWave.number==before,"ordinary survival player cannot skip waves");
            ResearchServerEvents.addCheat(f.name,"thereisnospoon");f.send(new SurvivalServerboundPacket(WaveDifficulty.BEGINNER,9));
            h.assertTrue(SurvivalServerEvents.nextWave.number==9,"authorized cheat retains wave editing");
        }finally{SurvivalServerEvents.reset();}}h.succeed();
    }
    @GameTest(templateNamespace="api65_auth_extended",template="ore_mine_test_arena",batch="auth_scenario_npc")
    public static void npcScenarioRoleRejectsFirstHuman(GameTestHelper h)throws Exception {
        try(var f=new AuthBoundaryGameTests.Fixture(h)){var previous=List.copyOf(ScenarioServerEvents.scenarioRoles);
            try{ScenarioServerEvents.scenarioRoles.clear();ScenarioRole role=new ScenarioRole(1);role.faction=Factions.VILLAGERS;role.isNpc=true;ScenarioServerEvents.scenarioRoles.add(role);
                h.getLevel().getGameRules().getRule(GameRuleRegistrar.SCENARIO_MODE).set(true,h.getLevel().getServer());
                f.send(new PlayerServerboundPacket(PlayerAction.START_RTS_SCENARIO,f.actor.getId(),1d,0d,0d));
                h.assertTrue(PlayerServerEvents.rtsPlayers.isEmpty(),"NPC error is a rejection, not admission");
            }finally{ScenarioServerEvents.scenarioRoles.clear();ScenarioServerEvents.scenarioRoles.addAll(previous);}}h.succeed();
    }
    @GameTest(templateNamespace="api65_auth_extended",template="ore_mine_test_arena",batch="auth_scenario_positive")
    public static void playableScenarioRoleStillStarts(GameTestHelper h)throws Exception {
        try(var f=new AuthBoundaryGameTests.Fixture(h)){var previous=List.copyOf(ScenarioServerEvents.scenarioRoles);
            try{ScenarioServerEvents.scenarioRoles.clear();ScenarioRole role=new ScenarioRole(1);role.faction=Factions.VILLAGERS;ScenarioServerEvents.scenarioRoles.add(role);
                h.getLevel().getGameRules().getRule(GameRuleRegistrar.SCENARIO_MODE).set(true,h.getLevel().getServer());
                f.send(new PlayerServerboundPacket(PlayerAction.START_RTS_SCENARIO,f.actor.getId(),1d,0d,0d));
                h.assertTrue(PlayerServerEvents.rtsPlayers.size()==1&&PlayerServerEvents.rtsPlayers.get(0).scenarioRoleIndex==1,"legal first human scenario role preserved");
            }finally{ScenarioServerEvents.scenarioRoles.clear();ScenarioServerEvents.scenarioRoles.addAll(previous);}}h.succeed();
    }
    static void slots(AuthBoundaryGameTests.Fixture f,int action)throws Exception {
        var previous=List.copyOf(StartPosServerEvents.startPoses);StartPosServerEvents.cancelStartGameCountdown(true);
        try {StartPosServerEvents.startPoses.clear();
            StartPos own=new StartPos(f.h.absolutePos(new BlockPos(2,2,2)),Factions.VILLAGERS,f.name,1);
            StartPos other=new StartPos(f.h.absolutePos(new BlockPos(4,2,4)),Factions.MONSTERS,f.name+"B",2);
            StartPosServerEvents.startPoses.add(own);StartPosServerEvents.startPoses.add(other);
            if(action==0)f.send(new StartPosServerboundPacket(StartPosAction.RESERVE,other.pos,Factions.VILLAGERS,f.name));
            else if(action==1)f.send(new StartPosServerboundPacket(StartPosAction.UNRESERVE,other.pos,Factions.NONE,""));
            else f.send(new StartPosServerboundPacket(StartPosAction.PLAYER_READY,BlockPos.ZERO,Factions.NONE,other.playerName));
            f.h.assertTrue(other.playerName.equals(f.name+"B")&&other.faction==Factions.MONSTERS&&!other.ready,"other participant's reservation and readiness unchanged");
            if(action==2){f.send(new StartPosServerboundPacket(StartPosAction.PLAYER_READY,BlockPos.ZERO,Factions.NONE,f.name));f.h.assertTrue(own.ready,"own readiness remains available");}
        }finally{StartPosServerEvents.cancelStartGameCountdown(true);StartPosServerEvents.startPoses.clear();StartPosServerEvents.startPoses.addAll(previous);}
    }
    @GameTest(templateNamespace="api65_auth_extended",template="ore_mine_test_arena",batch="auth_slot_reserve")
    public static void cannotStealAnotherStartSlot(GameTestHelper h)throws Exception {try(var f=new AuthBoundaryGameTests.Fixture(h)){slots(f,0);}h.succeed();}
    @GameTest(templateNamespace="api65_auth_extended",template="ore_mine_test_arena",batch="auth_slot_cancel")
    public static void cannotCancelAnotherStartSlot(GameTestHelper h)throws Exception {try(var f=new AuthBoundaryGameTests.Fixture(h)){slots(f,1);}h.succeed();}
    @GameTest(templateNamespace="api65_auth_extended",template="ore_mine_test_arena",batch="auth_slot_ready")
    public static void cannotReadyAnotherParticipant(GameTestHelper h)throws Exception {try(var f=new AuthBoundaryGameTests.Fixture(h)){slots(f,2);}h.succeed();}
    static int command(ServerPlayer p,String command)throws Exception{return p.getServer().getCommands().getDispatcher().execute(command,p.createCommandSourceStack());}
    @GameTest(templateNamespace="api65_auth_extended",template="ore_mine_test_arena",batch="auth_alliance_consume")
    public static void acceptedAllianceCannotBeReplayedAfterDisband(GameTestHelper h)throws Exception {
        try(var f=new AuthBoundaryGameTests.Fixture(h);var peer=new Peer(h.getLevel(),f.name+"B")){
            var b=h.getLevel().getServer().getPlayerList().getPlayerByName(f.name+"B");
            try{
                String to = b.getGameProfile().getName();
                command(f.actor, "ally " + to);
                command(b, "ally " + f.name);
                h.assertTrue(command(f.actor, "allywithdraw " + to) == 1
                        && !AlliancesServerEvents.pendingAlliances.getOrDefault(to, Set.of()).contains(f.name)
                        && AlliancesServerEvents.pendingAlliances.getOrDefault(f.name, Set.of()).contains(to),
                        "outgoing withdrawal leaves the independent incoming invitation intact");
                h.assertTrue(command(f.actor, "allycancelrequest " + to) == 1
                        && !AlliancesServerEvents.pendingAlliances.getOrDefault(f.name, Set.of()).contains(to),
                        "recipient decline consumes only its own incoming invitation");
                command(f.actor,"ally "+b.getGameProfile().getName());command(b,"allyconfirm "+f.name);
                h.assertTrue(AlliancesServerEvents.isAllied(f.name,b.getGameProfile().getName()),"legitimate invitation accepted");
                command(f.actor,"disband "+b.getGameProfile().getName());command(b,"allyconfirm "+f.name);
                h.assertTrue(!AlliancesServerEvents.isAllied(f.name,b.getGameProfile().getName()),"accepted directed invitation is consumed");
            }finally{AlliancesServerEvents.removeAlliance(f.name,b.getGameProfile().getName());AlliancesServerEvents.pendingAlliances.remove(f.name);AlliancesServerEvents.pendingAlliances.remove(b.getGameProfile().getName());}
        }h.succeed();
    }
    @GameTest(templateNamespace="api65_auth_extended",template="ore_mine_test_arena",batch="auth_alliance_lock")
    public static void pendingAllianceRespectsCurrentLock(GameTestHelper h)throws Exception {
        boolean old=h.getLevel().getGameRules().getBoolean(GameRuleRegistrar.LOCK_ALLIANCES);
        try(var f=new AuthBoundaryGameTests.Fixture(h);var peer=new Peer(h.getLevel(),f.name+"B")){
            var b=h.getLevel().getServer().getPlayerList().getPlayerByName(f.name+"B");
            try{h.getLevel().getGameRules().getRule(GameRuleRegistrar.LOCK_ALLIANCES).set(false,h.getLevel().getServer());
                f.existing(f.actor.getId(),Factions.VILLAGERS);command(f.actor,"ally "+b.getGameProfile().getName());
                h.getLevel().getGameRules().getRule(GameRuleRegistrar.LOCK_ALLIANCES).set(true,h.getLevel().getServer());
                command(b,"allyconfirm "+f.name);h.assertTrue(!AlliancesServerEvents.isAllied(f.name,b.getGameProfile().getName()),"pending request cannot bypass a later alliance lock");
            }finally{h.getLevel().getGameRules().getRule(GameRuleRegistrar.LOCK_ALLIANCES).set(old,h.getLevel().getServer());AlliancesServerEvents.removeAlliance(f.name,b.getGameProfile().getName());AlliancesServerEvents.pendingAlliances.remove(f.name);AlliancesServerEvents.pendingAlliances.remove(b.getGameProfile().getName());}
        }h.succeed();
    }
    @GameTest(templateNamespace="api65_auth_extended",template="ore_mine_test_arena",batch="auth_camera_selector")
    public static void legacyCameraSelectorUsesEntityArgument(GameTestHelper h)throws Exception {
        try(var f=new AuthBoundaryGameTests.Fixture(h)){
            int result=h.getLevel().getServer().getCommands().getDispatcher().execute("rtsapi-set-camera @s true",f.actor.createCommandSourceStack().withPermission(2));
            h.assertTrue(result==1,"authorized selector command executes");
        }h.succeed();
    }
    @GameTest(templateNamespace="api65_auth_extended",template="ore_mine_test_arena",batch="auth_button_factory")
    public static void registeredButtonCodecAndRightClickAreServerSafe(GameTestHelper h)throws Exception {
        try(var f=new AuthBoundaryGameTests.Fixture(h)){
            CustomButton button;
            try{button=CustomButton.CODEC.parse(JsonOps.INSTANCE,JsonParser.parseString("{\"name\":\"AuthButton\",\"icon\":\"minecraft:textures/item/stone.png\",\"x\":0,\"y\":0,\"left_click_actions\":[{\"type\":\"reignofnether:experience\",\"points\":2}],\"right_click_actions\":[{\"type\":\"reignofnether:experience\",\"points\":7}]}"))
                        .getOrThrow(false,message->{throw new AssertionError(message);});}
            catch(Throwable failure){throw new AssertionError("registered datapack CustomButton.CODEC factory must construct on server; this isolates linkage failure inside GameTest",failure);}
            ResourceLocation id=ResourceLocation.parse("api65_auth:test_button");var old=CustomButtonServerEvents.customButtons.put(id,button);
            try{int before=f.actor.totalExperience;
                FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());
                try{new CustomButtonActionServerboundPacket(id,false).encode(b);CustomButtonActionServerboundPacket.decode(b).handle(()->f.context);}finally{b.release();}
                h.assertTrue(f.actor.totalExperience-before==7,"right click executes registered right action, not left");
                button.isEnabled=false;new CustomButtonActionServerboundPacket(id,true).handle(()->f.context);
                h.assertTrue(f.actor.totalExperience-before==7,"disabled button requests do not execute");
            }finally{if(old==null)CustomButtonServerEvents.customButtons.remove(id);else CustomButtonServerEvents.customButtons.put(id,old);}
        }h.succeed();
    }
}
