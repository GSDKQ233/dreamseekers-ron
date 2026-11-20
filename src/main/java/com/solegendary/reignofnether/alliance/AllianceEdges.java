package com.solegendary.reignofnether.alliance;

import java.util.*;

/** Mode-owned undirected edges are separate from recipient <- sender invitations. */
final class AllianceEdges {
    private AllianceEdges() {}
    record Edge(String first, String second) {
        Edge {
            if (first.compareTo(second) > 0) { String swap = first; first = second; second = swap; }
        }
    }
    static Set<Edge> fromGraph(Map<String, Set<String>> graph) {
        Set<Edge> result = new HashSet<>();
        graph.forEach((from, tos) -> tos.forEach(to -> { if (!from.equals(to)) result.add(new Edge(from, to)); }));
        return result;
    }
    static Set<Edge> withEqualTeams(Map<String, Integer> teams) {
        Set<Edge> result = new HashSet<>();
        Map<Integer, List<String>> grouped = new HashMap<>();
        teams.forEach((name, team) -> grouped.computeIfAbsent(team, k -> new ArrayList<>()).add(name));
        for (var names : grouped.values()) {
            for (int i = 0; i < names.size(); i++) for (int j = i + 1; j < names.size(); j++)
                result.add(new Edge(names.get(i), names.get(j)));
        }
        return result;
    }
}
