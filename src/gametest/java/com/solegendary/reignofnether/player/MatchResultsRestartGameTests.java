package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.faction.Factions;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.GameTestHolder;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.*;

/** Actual normal shutdown and a distinct second JVM; no manual reload or static-map reset. */
@GameTestHolder("api60_results_restart")
public final class MatchResultsRestartGameTests {
    private static final String A="Api60RestartA", B="Api60RestartB", D="Api60RestartDefeated", E="Api60RestartEnemy";
    private static final File WITNESS=new File("api60-results-restart-witness.nbt");
    private static long jvm(){return ManagementFactory.getRuntimeMXBean().getStartTime();}
    @GameTestGenerator public static List<TestFunction> selected(){
        String phase=System.getProperty("api60.results.phase","");if(phase.isBlank())return List.of();
        if(phase.equals("prepare"))return List.of(test("prepareRealDefeatAndSavedMatch",MatchResultsRestartGameTests::prepare));
        if(!phase.equals("verify"))throw new IllegalArgumentException("prepare|verify required");
        return List.of(test("activePlayersDoNotResurrectDefeated",MatchResultsRestartGameTests::active),
                test("defeatedScoresSurviveRealRestart",MatchResultsRestartGameTests::history),
                test("elapsedDurationSurvivesRealRestart",MatchResultsRestartGameTests::duration),
                test("restoredRowsKeepAlliancesAndIndividualOutcomes",MatchResultsRestartGameTests::rows));
    }
    private static TestFunction test(String name,java.util.function.Consumer<GameTestHelper> body){
        System.out.println("API60_RESULTS_SELECTED phase="+System.getProperty("api60.results.phase")+" case="+name+" required=true");
        return new TestFunction("api60_results_restart",name.toLowerCase(Locale.ROOT),"api60_results_restart:ore_mine_test_arena",net.minecraft.world.level.block.Rotation.NONE,100,0L,true,1,1,body);
    }
    public static final class StopWitness {
        @SubscribeEvent(priority=EventPriority.LOWEST) public void stopping(ServerStoppingEvent event){
            if(PlayerServerEvents.getRTSPlayer(D)!=null||PlayerServerEvents.rtsPlayers.size()!=3||PlayerServerEvents.postGameRtsPlayers.stream().noneMatch(p->p.name.equals(D))||PlayerServerEvents.rtsGameTicks<4321)
                throw new AssertionError("live active/defeated rosters and duration survive normal stop");
            CompoundTag tag=new CompoundTag();tag.putLong("jvmStart",jvm());tag.putLong("gameTicks",PlayerServerEvents.rtsGameTicks);tag.putString("defeated",D);
            try{NbtIo.writeCompressed(tag,WITNESS);}catch(Exception ex){throw new RuntimeException(ex);}
            System.out.println("API60_RESULTS_NORMAL_STOP jvmStart="+jvm()+" active=3 defeated=1 gameTicks="+PlayerServerEvents.rtsGameTicks);
        }
    }
    private static void prepare(GameTestHelper h){
        h.assertTrue(!WITNESS.exists()&&PlayerServerEvents.rtsPlayers.isEmpty()&&PlayerServerEvents.postGameRtsPlayers.isEmpty(),"fresh isolated prepare world");
        for(String name:List.of(A,B,D,E)){
            var p=RTSPlayer.getNewPlayer(name,Factions.VILLAGERS,Math.abs(name.hashCode()),11,false,-1L,-1L);
            p.scores.scoreList.put(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED,name.equals(D)?19:7);
            PlayerServerEvents.rtsPlayers.add(p);
        }
        AlliancesServerEvents.addAlliance(A,B);AlliancesServerEvents.addAlliance(B,D);
        PlayerServerEvents.defeat(D,"API60 isolated results regression");
        h.assertTrue(PlayerServerEvents.getRTSPlayer(D)==null&&PlayerServerEvents.rtsPlayers.size()==3
                &&PlayerServerEvents.postGameRtsPlayers.size()==1&&PlayerServerEvents.postGameRtsPlayers.get(0).name.equals(D),"actual ordinary defeat transfers player to history without ending match");
        PlayerServerEvents.rtsGameTicks=4321;
        MinecraftForge.EVENT_BUS.register(new StopWitness());
        System.out.println("API60_RESULTS_PREPARE_PASS jvmStart="+jvm()+" realDefeat=true");h.succeed();
    }
    private static CompoundTag witness(GameTestHelper h){
        CompoundTag tag;try{tag=NbtIo.readCompressed(WITNESS);}catch(Exception ex){throw new RuntimeException(ex);}
        h.assertTrue(tag.getLong("jvmStart")!=jvm()&&tag.getString("defeated").equals(D),"actual independent prior JVM stop witness");
        System.out.println("API60_RESULTS_RESTART_OBSERVED priorJvm="+tag.getLong("jvmStart")+" currentJvm="+jvm()+" active="+PlayerServerEvents.rtsPlayers.size()+" historical="+PlayerServerEvents.postGameRtsPlayers.size()+" ticks="+PlayerServerEvents.rtsGameTicks);
        return tag;
    }
    private static void active(GameTestHelper h){witness(h);h.assertTrue(PlayerServerEvents.getRTSPlayer(D)==null&&PlayerServerEvents.rtsPlayers.size()==3
            &&PlayerServerEvents.getRTSPlayer(A)!=null&&PlayerServerEvents.getRTSPlayer(B)!=null&&PlayerServerEvents.getRTSPlayer(E)!=null,"only original active participants restore; defeat is not reversed");h.succeed();}
    private static void history(GameTestHelper h){witness(h);var records=PlayerServerEvents.postGameRtsPlayers;
        h.assertTrue(records.size()==1&&records.get(0).name.equals(D)&&records.get(0).scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED)==19
                &&records.get(0).startPosColorId==11&&!records.get(0).isDogPerson,"defeated player's exact score and prior fields restore separately");h.succeed();}
    private static void duration(GameTestHelper h){var tag=witness(h);h.assertTrue(PlayerServerEvents.rtsGameTicks>=tag.getLong("gameTicks")&&tag.getLong("gameTicks")>=4321,"elapsed game duration continues from saved value, not zero");h.succeed();}
    private static void rows(GameTestHelper h){witness(h);var rows=PlayerServerEvents.buildMatchStatsRows(Set.of(A,B));var byName=new HashMap<String,MatchStatsClientboundPacket.MatchStatRow>();for(var row:rows)byName.put(row.name,row);
        h.assertTrue(rows.size()==4&&byName.get(A).winner&&byName.get(B).winner&&!byName.get(D).winner&&!byName.get(E).winner,"one snapshot row per participant, explicit outcomes preserved");
        h.assertTrue(byName.get(A).teamId==byName.get(B).teamId&&byName.get(D).teamId!=byName.get(A).teamId&&byName.get(E).teamId!=byName.get(D).teamId,"restored real allies group, defeated ally and unrelated same-color player stay separate");h.succeed();}
}
