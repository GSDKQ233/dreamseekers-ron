package com.solegendary.reignofnether.startpos;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Side/snapshot source contracts complement the in-server lifecycle, border and codec fixtures. */
class LobbySessionWiringTest {
    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/com/solegendary/reignofnether/" + path + ".java"));
    }
    @Test void commonResetPrecedesAuthoritativeSnapshotAndSuccessFollowsCommit() throws Exception {
        String player = source("player/PlayerServerEvents");
        String reset = player.substring(player.indexOf("public static int resetRTS"), player.indexOf("public static void publishScenarioMap"));
        assertTrue(reset.indexOf("PlayerClientboundPacket.resetRTS") >= 0);
        assertTrue(reset.indexOf("StartPosServerEvents.reset(serverLevel)") > reset.indexOf("PlayerClientboundPacket.resetRTS"));
        String lobby = source("startpos/StartPosServerEvents");
        String tick = lobby.substring(lobby.indexOf("public static void onServerTick"), lobby.indexOf("private static boolean mapModeExceedsLimit"));
        assertTrue(tick.indexOf("ReadiedStart.tryStart") >= 0);
        assertTrue(tick.indexOf("PlayerServerEvents.setRTSLock(true") > tick.indexOf("if (failure != null)"));
        assertTrue(tick.indexOf("started_game") > tick.indexOf("PlayerServerEvents.setRTSLock(true"));
    }
    @Test void bothScenarioRosterMirrorsUseStableRoleIdentityAndActualReadyDelta() throws Exception {
        String server = source("player/PlayerServerEvents");
        assertTrue(server.contains("PlayerClientboundPacket.addRTSPlayer(rtsPlayer, true)"));
        assertTrue(server.contains("PlayerClientboundPacket.addRTSPlayer(rtsPlayer, false)"));
        assertTrue(server.contains("PlayerClientboundPacket.addRTSPlayer(npcRtsPlayer, false)"));
        String menu = source("scenario/ScenarioClientEvents");
        assertTrue(menu.contains("p.scenarioRoleIndex == role.index"));
        assertTrue(menu.contains("PlayerServerboundPacket.startRTSScenario(role.index)"));
        String ready = source("startpos/StartPosServerEvents");
        assertTrue(ready.contains("if (actualReady)\n            StartPosClientboundPacket.readyPlayer"));
    }
}
