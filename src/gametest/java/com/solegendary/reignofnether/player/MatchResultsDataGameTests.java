package com.solegendary.reignofnether.player;

import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.faction.Factions;
import io.netty.buffer.Unpooled;
import io.netty.buffer.ByteBufUtil;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("api60_results_data")
@PrefixGameTestTemplate(false)
public final class MatchResultsDataGameTests {
    private static final String BATCH="api60_results_data";
    private static RTSPlayer player(String name, int score) {
        var p=RTSPlayer.getNewPlayer(name,Factions.VILLAGERS,Math.abs(name.hashCode()),0,false,-1L,-1L);
        p.scores.scoreList.put(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED,score);
        return p;
    }
    @GameTest(templateNamespace="api60_results_data",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void savedActiveAndDefeatedRostersNeverMerge(GameTestHelper h) {
        var data=new RTSPlayerSaveData();data.rtsPlayers.add(player("CodecActive",7));
        data.postGameRtsPlayers.add(player("CodecDefeated",19));data.gameTicks=12345;
        var restored=RTSPlayerSaveData.load(data.save(new CompoundTag()));
        h.assertTrue(restored.rtsPlayers.size()==1&&restored.rtsPlayers.get(0).name.equals("CodecActive"),"defeated participant never restored as active");
        h.assertTrue(restored.postGameRtsPlayers.size()==1&&restored.postGameRtsPlayers.get(0).name.equals("CodecDefeated")
                &&restored.postGameRtsPlayers.get(0).scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED)==19,"defeated name and scores survive codec");
        h.assertTrue(restored.gameTicks==12345&&!restored.rtsPlayers.get(0).isDogPerson,"duration and API59 player preference preserved");
        h.succeed();
    }
    @GameTest(templateNamespace="api60_results_data",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void oldSaveDefaultsDoNotInventDefeatedPlayersOrDuration(GameTestHelper h) {
        var data=new RTSPlayerSaveData();data.rtsPlayers.add(player("OldActive",7));
        var tag=data.save(new CompoundTag());tag.remove("postGameRtsPlayers");tag.remove("gameTicks");
        var restored=RTSPlayerSaveData.load(tag);
        h.assertTrue(restored.rtsPlayers.size()==1&&restored.postGameRtsPlayers.isEmpty()&&restored.gameTicks==0,"old missing fields are empty/zero");
        tag.putLong("gameTicks",-100);
        h.assertTrue(RTSPlayerSaveData.load(tag).gameTicks==0,"negative saved duration does not restore");
        h.succeed();
    }
    @GameTest(templateNamespace="api60_results_data",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void actualRosterAllianceGroupingSnapshotPacketAndReset(GameTestHelper h) {
        h.assertTrue(PlayerServerEvents.rtsPlayers.isEmpty()&&PlayerServerEvents.postGameRtsPlayers.isEmpty(),"fresh isolated test world has no match participants");
        String a="Api60DataA",b="Api60DataB",dead="Api60DataDead",enemy="Api60DataEnemy";
        try {
            var pa=player(a,7);var pb=player(b,11);var pd=player(dead,19);var pe=player(enemy,23);
            pa.startPosColorId=5;pb.startPosColorId=9;pe.startPosColorId=5;
            PlayerServerEvents.rtsPlayers.addAll(List.of(pa,pb,pe));
            PlayerServerEvents.postGameRtsPlayers.addAll(List.of(pd,player(a,999)));
            AlliancesServerEvents.addAlliance(a,b);AlliancesServerEvents.addAlliance(b,dead);
            var rows=PlayerServerEvents.buildMatchStatsRows(Set.of(a,b));
            var byName=new HashMap<String,MatchStatsClientboundPacket.MatchStatRow>();for(var row:rows)byName.put(row.name,row);
            h.assertTrue(rows.size()==4&&byName.get(a).scores[RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED.ordinal()]==7,"active row wins duplicate historical name; exactly one row each");
            h.assertTrue(byName.get(a).teamId==byName.get(b).teamId&&byName.get(a).teamId!=byName.get(dead).teamId
                    &&byName.get(a).teamId!=byName.get(enemy).teamId&&byName.get(dead).teamId!=byName.get(enemy).teamId,"real alliances and outcomes, not shared/different display colors, define result groups");
            h.assertTrue(byName.get(a).winner&&byName.get(b).winner&&!byName.get(dead).winner&&!byName.get(enemy).winner,"winner set remains authoritative despite allied defeated participant");
            pa.scores.scoreList.put(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED,123);
            h.assertTrue(byName.get(a).scores[RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED.ordinal()]==7,"result score snapshot does not change with live player score");
            var buf=new FriendlyByteBuf(Unpooled.buffer());var roundtrip=new FriendlyByteBuf(Unpooled.buffer());
            try {
                new MatchStatsClientboundPacket(4321,rows).encode(buf);
                var input=new FriendlyByteBuf(buf.copy());
                try {new MatchStatsClientboundPacket(input).encode(roundtrip);} finally {input.release();}
                h.assertTrue(ByteBufUtil.equals(buf,roundtrip),"ordinary packet encode/decode preserves all roster, faction, group, outcome, score and duration fields");
            } finally {buf.release();roundtrip.release();}
            PlayerServerEvents.rtsGameTicks=4321;PlayerServerEvents.saveRTSPlayers();
            h.assertTrue(RTSPlayerSaveData.getInstance(h.getLevel()).postGameRtsPlayers.size()==2,"ordinary save retains historical participants");
            PlayerServerEvents.resetRTS(false);
            var saved=RTSPlayerSaveData.getInstance(h.getLevel());
            h.assertTrue(PlayerServerEvents.rtsPlayers.isEmpty()&&PlayerServerEvents.postGameRtsPlayers.isEmpty()
                    &&saved.rtsPlayers.isEmpty()&&saved.postGameRtsPlayers.isEmpty()&&saved.gameTicks==0,"real reset clears active, history and duration before persistent save");
            var disk=h.getLevel().getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/saved-rtsplayer-data.dat").toFile();
            var diskData=RTSPlayerSaveData.load(NbtIo.readCompressed(disk).getCompound("data"));
            h.assertTrue(diskData.rtsPlayers.isEmpty()&&diskData.postGameRtsPlayers.isEmpty()&&diskData.gameTicks==0,"normal reset flush contains no old participants or duration on actual disk");
            System.out.println("API60_RESULTS_DATA_PASS actual_rosters actual_alliances copied_scores wire_roundtrip real_reset_disk");
            h.succeed();
        } catch(java.io.IOException ex) {throw new RuntimeException(ex);}
        finally {
            PlayerServerEvents.rtsPlayers.clear();PlayerServerEvents.clearMatchHistory();
            AlliancesServerEvents.resetAllAlliances();PlayerServerEvents.saveRTSPlayers();
        }
    }
    @GameTest(templateNamespace="api60_results_data",template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void ordinaryAndCoopLastDefeatEmitOnlyLosingRowsBeforeTimeResets(GameTestHelper h) {
        h.assertTrue(PlayerServerEvents.rtsPlayers.isEmpty()&&PlayerServerEvents.postGameRtsPlayers.isEmpty(),"fresh isolated match before terminal-defeat test");
        var rule=h.getLevel().getGameRules().getRule(com.solegendary.reignofnether.registrars.GameRuleRegistrar.COOP_MODE);
        boolean originalCoop=rule.get();
        try {
            for(boolean coop:new boolean[]{false,true}) {
                rule.set(coop,h.getLevel().getServer());
                String name=coop?"Api60LastCoop":"Api60LastOrdinary";
                PlayerServerEvents.rtsPlayers.add(player(name,29));
                PlayerServerEvents.rtsGameTicks=9876;
                PlayerServerEvents.defeat(name,"API60 isolated final defeat regression");
                var rows=PlayerServerEvents.buildMatchStatsRows(Set.of());
                h.assertTrue(PlayerServerEvents.rtsPlayers.isEmpty()&&PlayerServerEvents.postGameRtsPlayers.size()==1
                        &&rows.size()==1&&rows.get(0).name.equals(name)&&!rows.get(0).winner,"actual last defeat leaves a single losing result in mode coop="+coop);
                h.assertTrue(rows.get(0).scores[RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED.ordinal()]==29
                        &&PlayerServerEvents.rtsGameTicks==9876&&RTSPlayerSaveData.getInstance(h.getLevel()).gameTicks==9876,"score and end duration remain available at terminal broadcast before next tick");
                System.out.println("API60_RESULTS_LAST_DEFEAT_PASS coop="+coop+" ticks=9876 participants=1 winners=0 expectedNormalBroadcast=true");
                PlayerServerEvents.resetRTS(false);
            }
            h.succeed();
        } finally {
            rule.set(originalCoop,h.getLevel().getServer());
            PlayerServerEvents.rtsPlayers.clear();PlayerServerEvents.clearMatchHistory();PlayerServerEvents.saveRTSPlayers();
        }
    }

}
