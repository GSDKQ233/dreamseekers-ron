package com.solegendary.reignofnether.player;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MatchResultTeamsTest {
    @Test void unrelatedUncoloredParticipantsRemainSeparate() {
        var ids = MatchResultTeams.assign(List.of("C", "A", "B"), Set.of("A"), Set::of);
        assertEquals(Map.of("A",1,"B",2,"C",3), ids);
    }
    @Test void realAlliesGroupWithoutDependingOnDisplayColor() {
        var ids = MatchResultTeams.assign(List.of("A","B","C"), Set.of("A","B"),
                name -> name.equals("C") ? Set.of("C") : Set.of("A","B"));
        assertEquals(ids.get("A"), ids.get("B"));
        assertNotEquals(ids.get("A"), ids.get("C"));
    }
    @Test void defeatedAllyCannotChangeSurvivorOutcome() {
        var ids = MatchResultTeams.assign(List.of("A","B","C"), Set.of("A","C"),
                name -> Set.of("A","B","C"));
        assertEquals(ids.get("A"),ids.get("C"));
        assertNotEquals(ids.get("A"),ids.get("B"));
    }
    @Test void inputOrderAndDuplicatesDoNotChangeGroups() {
        var expected = MatchResultTeams.assign(List.of("A","B","C"), Set.of("B"), Set::of);
        assertEquals(expected, MatchResultTeams.assign(List.of("C","B","A","B"), Set.of("B"), Set::of));
    }
    @Test void unknownWinnerOrNonParticipantDoesNotCreateRows() {
        var ids = MatchResultTeams.assign(List.of("A","B"), Set.of("Absent"),
                name -> Set.of(name,"Absent"));
        assertEquals(Map.of("A",1,"B",2),ids);
    }
    @Test void emptyRosterAndReturnedMappingAreStable() {
        assertTrue(MatchResultTeams.assign(List.of(),Set.of(),Set::of).isEmpty());
        var ids = MatchResultTeams.assign(List.of("A"),Set.of(),Set::of);
        assertThrows(UnsupportedOperationException.class, () -> ids.put("B",2));
    }
    @Test void terminalDefeatBroadcastPrecedesTheModeSpecificVictoryBranch() throws Exception {
        String source=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/com/solegendary/reignofnether/player/PlayerServerEvents.java"));
        String defeat=source.substring(source.indexOf("public static void defeat(String playerName"),source.indexOf("public static void beaconVictory"));
        assertTrue(defeat.contains("if (rtsPlayers.isEmpty()) {\n                broadcastMatchStats(Set.of());"));
        assertTrue(defeat.indexOf("broadcastMatchStats(Set.of());")<defeat.indexOf("else if (!serverLevel.getServer().getGameRules().getRule(GameRuleRegistrar.COOP_MODE).get())"));
    }

}
