package com.solegendary.reignofnether.player;

import java.util.*;
import java.util.function.Function;

/** A results-only grouping. It never determines who won or changes live alliances. */
public final class MatchResultTeams {
    private MatchResultTeams() {}

    /**
     * Stable positive IDs for the same participant/alliance/outcome snapshot.
     * A color is not a team identity: uncolored players may be unrelated, and
     * eliminated allies must not overwrite surviving allies' victory status.
     */
    public static Map<String, Integer> assign(Collection<String> participants, Set<String> winners,
                                              Function<String, Set<String>> connectedAllies) {
        var names = new TreeSet<>(participants);
        var groups = new LinkedHashMap<String, Integer>();
        int nextId = 1;
        for (String name : names) {
            if (groups.containsKey(name)) continue;
            int id = nextId++;
            groups.put(name, id);
            Set<String> connected = connectedAllies.apply(name);
            for (String other : names) {
                if (!groups.containsKey(other) && winners.contains(name) == winners.contains(other)
                        && connected.contains(other))
                    groups.put(other, id);
            }
        }
        return Collections.unmodifiableMap(groups);
    }
}
