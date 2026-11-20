package com.solegendary.reignofnether.alliance;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AllianceRosterTest {
    @Test void oppositeInvitationsHaveIndependentReceiptsAndPrivateSnapshots() {
        Map<String, Set<String>> inbox = new HashMap<>();
        assertTrue(DirectedInvitations.add(inbox, "B", "A"));
        assertFalse(DirectedInvitations.add(inbox, "B", "A")); // no duplicate toast or change
        assertTrue(DirectedInvitations.add(inbox, "A", "B"));
        assertTrue(DirectedInvitations.add(inbox, "B", "C"));
        Set<String> deliveries = new HashSet<>();
        DirectedInvitations.forViewer(inbox, "Observer", (to, from) -> deliveries.add(to + "<-" + from));
        assertTrue(deliveries.isEmpty());
        DirectedInvitations.forViewer(inbox, "A", (to, from) -> deliveries.add(to + "<-" + from));
        assertEquals(Set.of("A<-B", "B<-A"), deliveries);
        assertTrue(DirectedInvitations.remove(inbox, "B", "A")); // requester A withdraws
        assertFalse(DirectedInvitations.remove(inbox, "B", "A"));
        assertTrue(DirectedInvitations.remove(inbox, "A", "B")); // recipient A declines
        assertEquals(Map.of("B", Set.of("C")), inbox);
    }

    @Test void oldAndNewScenarioTeamsProduceAnExactUndirectedDelta() {
        var old = AllianceEdges.fromGraph(Map.of("A", Set.of("B", "C"), "B", Set.of("A"), "C", Set.of("A")));
        var next = AllianceEdges.withEqualTeams(Map.of("A", 2, "B", 1, "C", 2, "D", 1));
        var removed = new HashSet<>(old); removed.removeAll(next);
        var added = new HashSet<>(next); added.removeAll(old);
        assertEquals(Set.of(new AllianceEdges.Edge("B", "A")), removed);
        assertEquals(Set.of(new AllianceEdges.Edge("D", "B")), added);
        assertEquals(2, next.size());
    }
}
