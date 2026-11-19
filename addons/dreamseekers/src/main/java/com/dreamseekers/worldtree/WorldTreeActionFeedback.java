package com.dreamseekers.worldtree;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Pure presentation state; never authorizes, resends, or changes an economy action. */
public final class WorldTreeActionFeedback {
    public static final long VISIBLE_TICKS = 100;
    public record Context(UUID stream, UUID session, UUID epoch, UUID instance, long capture) { }
    private static final Set<String> QUIET = Set.of("ready", "started", "cancelled_refunded", "applied");
    public static final Set<String> REASONS = Set.of(
        "foreign_block", "entity_collision", "other_building", "block_entity", "entry_obstructed",
        "unloaded_or_border", "invalid_model", "invalid_target", "invalid_host", "invalid_state",
        "invalid_account", "account_changed", "missing_receipt", "invalid_progression",
        "world_write_rejected", "receipt_commit_failed", "insufficient_resources", "stale_account",
        "wrong_thread", "invalid_value", "output_overflow", "unknown_extension", "receipt_limit",
        "unknown_receipt", "forbidden", "stale_session", "invalid_sequence", "reconnect_required",
        "stale_request", "replay_mismatch", "failed_closed", "invalid_action", "refund_pending");
    private Context expected;
    private long sequence;
    private boolean completed;
    private String failure;
    private long received;

    /** Called only while EconomyActionClient creates a genuinely new outgoing request. */
    public void submitted(Context context, long request) {
        if (context == null || request <= 0) return;
        expected = context;
        sequence = request;
        completed = false;
        failure = null;
    }

    /** A global newer query may precede an action completion: compare our exact request,
     * not the snapshot order. Older requests, replays and other contexts remain inert.
     */
    public String accept(Context context, long request, String status, long now) {
        if (completed || request <= 0 || request != sequence || !Objects.equals(expected, context)) return null;
        completed = true;
        if (status != null && QUIET.contains(status)) { failure = null; return null; }
        failure = reasonKey(status);
        received = now;
        return failure;
    }

    public String visible(Context context, long now) {
        if (!Objects.equals(expected, context) || failure == null || now < received || now - received >= VISIBLE_TICKS) return null;
        return failure;
    }

    /** Presentation only: IDLE has not started and COMPLETE is finished. The server
     * remaining-work counter and its scheduling are never changed by this projection.
     */
    public static int progress(String phase, int remaining) {
        if ("COMPLETE".equals(phase)) return 2400;
        if (!"RUNNING".equals(phase) && !"BLOCKED".equals(phase) && !"REFUND_PENDING".equals(phase)) return 0;
        return 2400 - Math.max(0, Math.min(2400, remaining));
    }

    /** Unknown server strings never become raw UI keys or unlocalized identifiers. */
    public static String reasonKey(String reason) {
        return "worldtree.dreamseekers.reason." + (reason != null && REASONS.contains(reason) ? reason : "unknown");
    }
}
