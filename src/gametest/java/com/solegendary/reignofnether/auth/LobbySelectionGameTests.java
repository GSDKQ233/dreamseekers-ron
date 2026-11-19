package com.solegendary.reignofnether.auth;

import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.startpos.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
import java.util.List;

/** Normal API64 MatchStartScreen contract: reserve a seat before choosing a faction. */
@GameTestHolder("api65_auth_lobby") @PrefixGameTestTemplate(false)
public final class LobbySelectionGameTests {
    @GameTest(templateNamespace="api65_auth_lobby",template="ore_mine_test_arena",batch="auth_lobby_two_stage")
    public static void reserveChooseAndDeselectFactionPreserveOwnSeat(GameTestHelper h)throws Exception {
        try(var f=new AuthBoundaryGameTests.Fixture(h)) {
            var previous=List.copyOf(StartPosServerEvents.startPoses);StartPosServerEvents.cancelStartGameCountdown(true);
            try{StartPosServerEvents.startPoses.clear();var seat=new StartPos(h.absolutePos(new BlockPos(3,2,3)),1);StartPosServerEvents.startPoses.add(seat);
                f.send(new StartPosServerboundPacket(StartPosAction.RESERVE,seat.pos,Factions.NONE,f.name));
                h.assertTrue(seat.playerName.equals(f.name)&&seat.faction==Factions.NONE&&!seat.ready,"first click reserves an unselected own seat");
                f.send(new StartPosServerboundPacket(StartPosAction.RESERVE,seat.pos,Factions.VILLAGERS,f.name));
                h.assertTrue(seat.playerName.equals(f.name)&&seat.faction==Factions.VILLAGERS,"second click selects faction");
                f.send(new StartPosServerboundPacket(StartPosAction.RESERVE,seat.pos,Factions.NONE,f.name));
                h.assertTrue(seat.playerName.equals(f.name)&&seat.faction==Factions.NONE&&!seat.ready,"clicking selected faction deselects it without losing seat");
                f.send(new StartPosServerboundPacket(StartPosAction.RESERVE,seat.pos,Factions.VILLAGERS,f.name));
                f.send(new StartPosServerboundPacket(StartPosAction.PLAYER_READY,BlockPos.ZERO,Factions.NONE,f.name));
                h.assertTrue(seat.ready&&StartPosServerEvents.isStartingGame(),"valid selected own seat can become ready and begin countdown");
            }finally{StartPosServerEvents.cancelStartGameCountdown(true);StartPosServerEvents.startPoses.clear();StartPosServerEvents.startPoses.addAll(previous);}
        }h.succeed();
    }
}
