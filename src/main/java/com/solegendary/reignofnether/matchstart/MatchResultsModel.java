package com.solegendary.reignofnether.matchstart;

import java.util.*;

/** Immutable presentation snapshot. No Minecraft client classes, mutable packets or screen objects. */
public final class MatchResultsModel {
    private MatchResultsModel() {}
    public record Row(String name, String factionKey, boolean winner, int teamId, List<Long> scores) {
        public Row {
            name = Objects.requireNonNullElse(name, "");
            factionKey = Objects.requireNonNullElse(factionKey, "reignofnether:neutral");
            scores = scores == null ? List.of() : scores.stream().map(n -> n == null ? 0L : Math.max(0L, n)).toList();
        }
        public long score(int index) { return index >= 0 && index < scores.size() ? scores.get(index) : 0; }
    }
    public record Snapshot(long ticks, List<Row> rows) {
        public Snapshot { ticks = Math.max(0, ticks); rows = List.copyOf(rows); }
    }
    public record Team(boolean winner, List<Row> members) {
        public Team { members = List.copyOf(members); }
        public long total(int score) {
            long sum = 0;
            for (Row row : members) {
                long value = row.score(score);
                sum = Long.MAX_VALUE - sum < value ? Long.MAX_VALUE : sum + value;
            }
            return sum;
        }
    }
    private record Group(int id, boolean winner, String solo) {}
    public static List<Team> teams(List<Row> rows) {
        Map<Group, List<Row>> grouped = new LinkedHashMap<>();
        for (Row row : rows) {
            // Nonpositive IDs are unassigned, never an implicit all-player team.
            Group key = new Group(row.teamId(), row.winner(), row.teamId() > 0 ? "" : row.name());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        List<Team> result = new ArrayList<>();
        grouped.forEach((key, members) -> result.add(new Team(key.winner(), members)));
        result.sort(Comparator.comparing(Team::winner).reversed());
        return List.copyOf(result);
    }
}
