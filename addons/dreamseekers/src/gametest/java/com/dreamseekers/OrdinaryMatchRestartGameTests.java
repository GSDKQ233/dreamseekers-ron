package com.dreamseekers;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.GameRuleRegistrar;
import com.solegendary.reignofnether.scenario.ScenarioServerEvents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.GameTestHolder;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.*;

/** Separate actual JVMs and the same ordinary saved world. Never clears runtime maps. */
@GameTestHolder("api59_alliance_restart")
public final class OrdinaryMatchRestartGameTests {
    private static final String A="Api59RestartAllyA",B="Api59RestartAllyB";
    private static final File WITNESS=new File("api59-alliance-restart-witness.nbt");
    private static final int COLOR=11;
    private static long jvm(){return ManagementFactory.getRuntimeMXBean().getStartTime();}
    private static boolean ordinary(ServerLevel level){return !level.getGameRules().getBoolean(GameRuleRegistrar.SCENARIO_MODE)&&!level.getGameRules().getBoolean(GameRuleRegistrar.COOP_MODE);}
    private static void context(GameTestHelper h){h.assertTrue(ordinary(h.getLevel()),"ordinary match: scenario and coop disabled; untouched default role slots");}
    @GameTestGenerator public static List<TestFunction> selected(){
        String phase=System.getProperty("api59.alliance.phase","");if(phase.isBlank())return List.of();
        if(phase.equals("prepare"))return List.of(test("prepareOrdinaryAlliedPlayers",OrdinaryMatchRestartGameTests::prepare));
        if(!phase.equals("verify"))throw new IllegalArgumentException("prepare|verify required");
        return List.of(test("restoredPlayerIdentityPositiveControl",OrdinaryMatchRestartGameTests::identity),
            test("ordinaryAllianceSurvivesActualRestart",OrdinaryMatchRestartGameTests::alliance),
            test("ordinaryPlayerColorSurvivesActualRestart",OrdinaryMatchRestartGameTests::color),
            test("ordinaryScoutPreferenceSurvivesActualRestart",OrdinaryMatchRestartGameTests::scout));
    }
    private static TestFunction test(String name,java.util.function.Consumer<GameTestHelper> body){
        System.out.println("API59_ALLIANCE_SELECTED phase="+System.getProperty("api59.alliance.phase")+" case="+name+" required=true");
        return new TestFunction("api59_alliance_restart",name.toLowerCase(Locale.ROOT),"api59_alliance_restart:dreamseekers_test_arena",net.minecraft.world.level.block.Rotation.NONE,100,0L,true,1,1,body);
    }
    public static final class StopWitness {
        @SubscribeEvent(priority=EventPriority.LOWEST) public void stopping(ServerStoppingEvent event){
            var a=PlayerServerEvents.getRTSPlayer(A);var b=PlayerServerEvents.getRTSPlayer(B);
            if(a==null||b==null||a.startPosColorId!=COLOR||b.startPosColorId!=COLOR||a.isDogPerson||b.isDogPerson||!AlliancesServerEvents.isAllied(A,B)||!AlliancesServerEvents.isAllied(B,A)||!ordinary(event.getServer().overworld()))throw new AssertionError("ordinary players and bidirectional alliance must remain live through normal save/stop");
            CompoundTag tag=new CompoundTag();tag.putLong("jvmStart",jvm());tag.putLong("stopTime",event.getServer().overworld().getGameTime());tag.putString("ownerA",A);tag.putString("ownerB",B);tag.putInt("color",COLOR);tag.putBoolean("dog",false);tag.putBoolean("alliedAtStop",true);
            try{NbtIo.writeCompressed(tag,WITNESS);}catch(Exception ex){throw new RuntimeException(ex);}
            System.out.println("API59_ALLIANCE_NORMAL_STOP_WITNESS jvmStart="+jvm()+" allied=true color="+COLOR+" dog=false");
        }
    }
    private static void prepare(GameTestHelper h){
        context(h);h.assertTrue(!WITNESS.exists()&&PlayerServerEvents.getRTSPlayer(A)==null&&PlayerServerEvents.getRTSPlayer(B)==null,"fresh isolated prepare world");
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(A,DreamseekersMod.DREAMSEEKERS,98101,COLOR,false,-1L,-1L));
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(B,DreamseekersMod.DREAMSEEKERS,98102,COLOR,false,-1L,-1L));
        AlliancesServerEvents.addAlliance(A,B);
        h.assertTrue(AlliancesServerEvents.isAllied(A,B)&&AlliancesServerEvents.isAllied(B,A),"actual public ordinary alliance API establishes both directions");
        MinecraftForge.EVENT_BUS.register(new StopWitness());
        System.out.println("API59_ALLIANCE_PREPARE_PASS jvmStart="+jvm()+" expectedPlayers=2 assignedScenarioRoles=0 expectedColor="+COLOR);h.succeed();
    }
    private static CompoundTag witness(GameTestHelper h){
        context(h);CompoundTag t;try{t=NbtIo.readCompressed(WITNESS);}catch(Exception ex){throw new RuntimeException(ex);}
        h.assertTrue(t.getLong("jvmStart")!=jvm(),"genuinely separate JVM process; never simulated by clearing maps");
        h.assertTrue(t.getBoolean("alliedAtStop")&&t.getInt("color")==COLOR&&!t.getBoolean("dog"),"actual normal-stop witness records expected prior state");
        var a=PlayerServerEvents.getRTSPlayer(A);var b=PlayerServerEvents.getRTSPlayer(B);
        h.assertTrue(a!=null&&b!=null&&a.id==98101&&b.id==98102&&a.faction==DreamseekersMod.DREAMSEEKERS&&b.faction==DreamseekersMod.DREAMSEEKERS&&a.scenarioRoleIndex==-1&&b.scenarioRoleIndex==-1,"normal server startup restores both original player identities and addon factions from disk");
        System.out.println("API59_ALLIANCE_RESTART_OBSERVED priorJvm="+t.getLong("jvmStart")+" currentJvm="+jvm()+" alliedAB="+AlliancesServerEvents.isAllied(A,B)+" alliedBA="+AlliancesServerEvents.isAllied(B,A)+" colorA="+a.startPosColorId+" colorB="+b.startPosColorId+" dogA="+a.isDogPerson+" dogB="+b.isDogPerson);
        return t;
    }
    private static void identity(GameTestHelper h){witness(h);h.succeed();}
    private static void alliance(GameTestHelper h){witness(h);h.assertTrue(AlliancesServerEvents.isAllied(A,B)&&AlliancesServerEvents.isAllied(B,A),"ordinary bidirectional alliance must survive actual normal server restart");h.succeed();}
    private static void color(GameTestHelper h){witness(h);h.assertTrue(PlayerServerEvents.getRTSPlayer(A).startPosColorId==COLOR&&PlayerServerEvents.getRTSPlayer(B).startPosColorId==COLOR,"ordinary start-position colors must survive actual normal server restart");h.succeed();}
    private static void scout(GameTestHelper h){witness(h);h.assertTrue(!PlayerServerEvents.getRTSPlayer(A).isDogPerson&&!PlayerServerEvents.getRTSPlayer(B).isDogPerson,"ordinary cat/scout preferences must survive actual normal server restart");h.succeed();}
}
