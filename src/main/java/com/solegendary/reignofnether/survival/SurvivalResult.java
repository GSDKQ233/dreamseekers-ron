package com.solegendary.reignofnether.survival;

import java.util.Set;

/** A scored match ends at a beacon or total elimination, not simply when wave 30 is cleared.
 * The single bound server overworld owns this snapshot; only its load/reset/finish hooks replace it.
 * Kept independent of Forge so score writers don't initialize wave registries. */
public record SurvivalResult(Kind kind, Set<String> winners, long elapsed) {
    public enum Kind { IN_PROGRESS, BEACON_VICTORY, ALL_DEFEATED }

    static final SurvivalResult OPEN = new SurvivalResult(Kind.IN_PROGRESS, Set.of(), 0);
    private static SurvivalResult bound = OPEN;
    static final int BEACON_FREEPLAY = -2, DEFEAT_FREEPLAY = -3;

    public SurvivalResult {
        winners = kind == Kind.BEACON_VICTORY ? Set.copyOf(winners) : Set.of();
        elapsed = Math.max(0, elapsed);
    }

    public boolean terminal() { return kind != Kind.IN_PROGRESS; }
    public static boolean isAfterMatch() { return bound.terminal(); }
    public static SurvivalResult current() { return bound; }
    static void bind(SurvivalResult result) { bound = result; }
    static boolean finishOnce(Kind kind, Set<String> winners, long elapsed) {
        if (isAfterMatch() || kind == Kind.IN_PROGRESS || kind == Kind.BEACON_VICTORY && winners.isEmpty()) return false;
        bind(new SurvivalResult(kind, winners, elapsed));
        return true;
    }
    int clientStatus(WaveCursor cursor) {
        return switch (kind) {
            case IN_PROGRESS -> cursor.forClient();
            case BEACON_VICTORY -> BEACON_FREEPLAY;
            case ALL_DEFEATED -> DEFEAT_FREEPLAY;
        };
    }
}
