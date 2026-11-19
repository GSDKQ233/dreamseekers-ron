package com.dreamseekers.worldtree;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WorldTreeActionFeedbackTest {
    private static final UUID STREAM = new UUID(1, 1), SESSION = new UUID(2, 2), EPOCH = new UUID(3, 3), INSTANCE = new UUID(4, 4);
    private static WorldTreeActionFeedback.Context context() { return new WorldTreeActionFeedback.Context(STREAM, SESSION, EPOCH, INSTANCE, 7); }
    private static String key(String value) { return "worldtree.dreamseekers.reason." + value; }

    @Test void exactOwnRejectionIsVisibleAndQueryDoesNotEraseIt() {
        var f = new WorldTreeActionFeedback(); f.submitted(context(), 8);
        assertEquals(key("foreign_block"), f.accept(context(), 8, "foreign_block", 100));
        assertNull(f.accept(context(), 0, "ready", 101));
        assertEquals(key("foreign_block"), f.visible(context(), 101));
    }
    @Test void newerQueryBeforeOwnCompletionDoesNotSwallowFeedback() {
        var f = new WorldTreeActionFeedback(); f.submitted(context(), 8);
        assertNull(f.accept(context(), 0, "ready", 110));
        assertEquals(key("entity_collision"), f.accept(context(), 8, "entity_collision", 111));
        assertEquals(key("entity_collision"), f.visible(context(), 112));
    }
    @Test void duplicateCompletionCannotRepeatChangeOrExtendFeedback() {
        var f = new WorldTreeActionFeedback(); f.submitted(context(), 8);
        assertEquals(key("foreign_block"), f.accept(context(), 8, "foreign_block", 100));
        assertNull(f.accept(context(), 8, "entity_collision", 190));
        assertEquals(key("foreign_block"), f.visible(context(), 199));
        assertNull(f.visible(context(), 200));
    }
    @Test void newSubmissionClearsOldFailureAndRejectsLateOldCompletion() {
        var f = new WorldTreeActionFeedback(); f.submitted(context(), 8);
        f.accept(context(), 8, "foreign_block", 100); f.submitted(context(), 9);
        assertNull(f.visible(context(), 101));
        assertNull(f.accept(context(), 8, "entity_collision", 102));
        assertEquals(key("other_building"), f.accept(context(), 9, "other_building", 103));
        assertNull(f.accept(context(), 8, "foreign_block", 104));
        assertEquals(key("other_building"), f.visible(context(), 104));
    }
    @Test void successfulCompletionsAreQuietAndCannotBeReplacedByAReplay() {
        for (String success : List.of("ready", "started", "cancelled_refunded", "applied")) {
            var f = new WorldTreeActionFeedback(); f.submitted(context(), 8);
            assertNull(f.accept(context(), 8, success, 100));
            assertNull(f.visible(context(), 100));
            assertNull(f.accept(context(), 8, "foreign_block", 101));
        }
    }
    @Test void everyContextIdentityBoundaryRejectsAnotherContext() {
        UUID other = new UUID(99, 99);
        for (var wrong : List.of(new WorldTreeActionFeedback.Context(other, SESSION, EPOCH, INSTANCE, 7),
                new WorldTreeActionFeedback.Context(STREAM, other, EPOCH, INSTANCE, 7),
                new WorldTreeActionFeedback.Context(STREAM, SESSION, other, INSTANCE, 7),
                new WorldTreeActionFeedback.Context(STREAM, SESSION, EPOCH, other, 7),
                new WorldTreeActionFeedback.Context(STREAM, SESSION, EPOCH, INSTANCE, 8))) {
            var f = new WorldTreeActionFeedback(); f.submitted(context(), 8);
            assertNull(f.accept(wrong, 8, "foreign_block", 100));
            assertEquals(key("foreign_block"), f.accept(context(), 8, "foreign_block", 101));
            assertNull(f.visible(wrong, 102));
        }
    }
    @Test void contextRotationDoesNotRevivePriorSessionOrPriorSequence() {
        var f = new WorldTreeActionFeedback(); f.submitted(context(), 8);
        var next = new WorldTreeActionFeedback.Context(STREAM, new UUID(8, 9), EPOCH, INSTANCE, 7);
        f.submitted(next, 1);
        assertNull(f.accept(context(), 8, "foreign_block", 100));
        assertEquals(key("entity_collision"), f.accept(next, 1, "entity_collision", 101));
    }
    @Test void unsolicitedQueryAndUnsubmittedCompletionAreInert() {
        var f = new WorldTreeActionFeedback();
        assertNull(f.accept(context(), 0, "ready", 100));
        assertNull(f.accept(context(), 8, "foreign_block", 100));
        f.submitted(context(), 8);
        assertNull(f.accept(context(), 9, "foreign_block", 100));
        assertNull(f.accept(context(), -1, "foreign_block", 100));
        assertNull(f.visible(context(), 100));
    }
    @Test void unknownStatusesUseLocalizedFallbackRatherThanRawServerStrings() {
        for (String unknown : new String[]{null, "", "server_internal_unexpected", "worldtree.dreamseekers.raw"}) {
            var f = new WorldTreeActionFeedback(); f.submitted(context(), 8);
            assertEquals(key("unknown"), f.accept(context(), 8, unknown, 100));
        }
    }
    @Test void expiryUsesActiveClockAndHidesDuringClockRegression() {
        var f = new WorldTreeActionFeedback(); f.submitted(context(), 8);
        f.accept(context(), 8, "foreign_block", 100);
        assertNull(f.visible(context(), 99));
        assertNotNull(f.visible(context(), 199));
        assertNull(f.visible(context(), 200));
    }
    @Test void idleAndCompleteProgressHaveDistinctPresentationWithoutChangingCounters() {
        for (int remaining : new int[]{-1, 0, 1, 1200, 2400, 2401, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertEquals(0, WorldTreeActionFeedback.progress("IDLE", remaining));
            assertEquals(2400, WorldTreeActionFeedback.progress("COMPLETE", remaining));
        }
        assertEquals(0, WorldTreeActionFeedback.progress(null, 0));
        assertEquals(0, WorldTreeActionFeedback.progress("future_unknown_phase", 0));
    }
    @Test void inProgressAndBlockedWorkUseBoundedActualRemainingWork() {
        for (String phase : List.of("RUNNING", "BLOCKED", "REFUND_PENDING")) {
            assertEquals(0, WorldTreeActionFeedback.progress(phase, 2400));
            assertEquals(1, WorldTreeActionFeedback.progress(phase, 2399));
            assertEquals(1200, WorldTreeActionFeedback.progress(phase, 1200));
            assertEquals(2400, WorldTreeActionFeedback.progress(phase, 0));
            assertEquals(2400, WorldTreeActionFeedback.progress(phase, Integer.MIN_VALUE));
            assertEquals(0, WorldTreeActionFeedback.progress(phase, Integer.MAX_VALUE));
        }
    }
    @Test void allRuntimeReasonAndPhaseKeysHaveBothLanguageValues() throws Exception {
        for (String locale : List.of("zh_cn", "en_us")) {
            JsonObject language = JsonParser.parseString(Files.readString(Path.of(
                    "addons/dreamseekers/src/main/resources/assets/dreamseekers/lang/" + locale + ".json"))).getAsJsonObject();
            for (String reason : WorldTreeActionFeedback.REASONS) translated(language, key(reason));
            translated(language, key("unknown"));
            translated(language, "worldtree.dreamseekers.action_failed");
            for (String phase : List.of("idle", "running", "blocked", "refund_pending", "complete"))
                translated(language, "worldtree.dreamseekers.phase." + phase);
            for (String gate : List.of("invalid_sequence", "reconnect_required", "stale_request", "replay_mismatch",
                    "invalid_account", "wrong_thread", "invalid_value", "output_overflow", "unknown_extension", "receipt_limit", "unknown_receipt"))
                translated(language, "building_ability.status." + gate);
        }
    }
    private static void translated(JsonObject language, String key) {
        assertTrue(language.has(key), key);
        String value = language.get(key).getAsString();
        assertFalse(value.isBlank(), key); assertNotEquals(key, value);
    }
    @Test void realClientWiringKeepsAuthorityAndSnapshotFreshnessSeparateFromPresentation() throws Exception {
        String client = Files.readString(Path.of("addons/dreamseekers/src/main/java/com/dreamseekers/worldtree/WorldTreeClient.java"));
        assertTrue(client.contains("if(newer)states.put(p,new Cache(snapshot,CLOCK.now()))"));
        assertTrue(client.contains("EconomyActionClient.matchesContext(snapshot.session(),snapshot.epoch())"));
        assertTrue(client.contains("currentIdentity=newer||cached==null||feedbackContext(cached.value()).equals(feedbackContext(snapshot))"));
        assertTrue(client.contains(".submitted(\n                new WorldTreeActionFeedback.Context"));
        assertTrue(client.contains("local.accept(feedbackContext(snapshot),snapshot.completed(),snapshot.status(),CLOCK.now())"));
        assertEquals(2, client.split("WorldTreeActionFeedback.progress\\(s.phase\\(\\).name\\(\\),s.remaining\\(\\)\\)", -1).length - 1);
        assertFalse(client.contains("2400-s.remaining()"));
        assertTrue(client.contains("appendFeedback(lines,p,s)"));
        assertTrue(client.contains("appendFeedback(windLines,p,s)"));
        assertTrue(client.contains("states.clear();queries.clear();feedback.clear()"));
        String buildings = Files.readString(Path.of("addons/dreamseekers/src/main/java/com/dreamseekers/buildingability/BuildingAbilityClient.java"));
        assertEquals(2, buildings.split("statusMessage\\(packet.status\\(\\)\\)", -1).length - 1);
        assertTrue(buildings.contains("I18n.exists(key)"));
    }
}
