// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.shadow;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Pure completion identity checks; no Minecraft bootstrap, packet delivery, or GUI claim. */
class PendingUpgradeViewTest {
    private static final UUID STREAM = UUID.randomUUID(), SESSION = UUID.randomUUID(), EPOCH = UUID.randomUUID();
    private static final UUID BODY = UUID.randomUUID();
    private static PendingUpgradeView<Object> pending() {
        return new PendingUpgradeView<>(new Object(), STREAM, SESSION, EPOCH, 7, 31, BODY);
    }
    @Test void exactSubmittedIdentityCompletesAndRetainsTheOriginalBody() {
        var body = new Object(); var view = new PendingUpgradeView<>(body, STREAM, SESSION, EPOCH, 7, 31, BODY);
        assertSame(body, view.source());
        assertTrue(view.completedBy(STREAM, SESSION, EPOCH, 7, 31, BODY));
    }
    @Test void foreignStreamSessionAndEpochCannotReleaseTheBody() {
        var view = pending(); var foreign = UUID.randomUUID();
        assertFalse(view.completedBy(foreign, SESSION, EPOCH, 7, 31, BODY));
        assertFalse(view.completedBy(STREAM, foreign, EPOCH, 7, 31, BODY));
        assertFalse(view.completedBy(STREAM, SESSION, foreign, 7, 31, BODY));
    }
    @Test void queryNegativeAndDifferentSequencesNeverCompleteThisRequest() {
        var view = pending();
        for (long completed : new long[] {Long.MIN_VALUE, -1, 0, 1, 6, 8, Long.MAX_VALUE})
            assertFalse(view.completedBy(STREAM, SESSION, EPOCH, completed, 31, BODY));
        var invalid = new PendingUpgradeView<>(new Object(), STREAM, SESSION, EPOCH, 0, 31, BODY);
        assertFalse(invalid.completedBy(STREAM, SESSION, EPOCH, 0, 31, BODY));
    }
    @Test void reusedEntityIdOrUuidDoesNotReleaseAReplacementBody() {
        var view = pending();
        assertFalse(view.completedBy(STREAM, SESSION, EPOCH, 7, 32, BODY));
        assertFalse(view.completedBy(STREAM, SESSION, EPOCH, 7, 31, UUID.randomUUID()));
    }
    @Test void sameViewOldContextCompletionCannotDeleteNewSubmission() {
        var contexts = new UUID[][] {
                {UUID.randomUUID(), SESSION, EPOCH},
                {STREAM, UUID.randomUUID(), EPOCH},
                {STREAM, SESSION, UUID.randomUUID()}
        };
        for (var context : contexts) {
            var retained = new HashMap<UUID, PendingUpgradeView<Object>>(); var nonce = UUID.randomUUID();
            var replacement = new PendingUpgradeView<>(new Object(), context[0], context[1], context[2], 7, 31, BODY);
            retained.put(nonce, pending()); retained.put(nonce, replacement);
            var current = retained.get(nonce);
            if (current.completedBy(STREAM, SESSION, EPOCH, 7, 31, BODY)) retained.remove(nonce, current);
            assertSame(replacement, retained.get(nonce));
            if (current.completedBy(context[0], context[1], context[2], 7, 31, BODY)) retained.remove(nonce, current);
            assertTrue(retained.isEmpty());
        }
    }
    @Test void sameContextCompletionRetiresRetentionEvenWhenUiSnapshotIsStale() {
        var retained = new HashMap<UUID, PendingUpgradeView<Object>>(); var nonce = UUID.randomUUID();
        retained.put(nonce, pending()); boolean acceptedUi = false;
        var current = retained.get(nonce);
        if (current.completedBy(STREAM, SESSION, EPOCH, 7, 31, BODY)) retained.remove(nonce, current);
        assertFalse(acceptedUi); assertTrue(retained.isEmpty());
    }
    @Test void completionLookupCannotClearAnotherViewNonce() {
        var retained = new HashMap<UUID, PendingUpgradeView<Object>>();
        var nonce = UUID.randomUUID(); var other = UUID.randomUUID();
        retained.put(nonce, pending()); retained.put(other, pending());
        var current = retained.get(nonce);
        if (current.completedBy(STREAM, SESSION, EPOCH, 7, 31, BODY)) retained.remove(nonce, current);
        assertFalse(retained.containsKey(nonce)); assertTrue(retained.containsKey(other));
        assertNull(retained.get(nonce)); // a repeated completion cannot release another key
    }
}
