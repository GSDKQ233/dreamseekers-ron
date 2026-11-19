// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.shadow;

import java.util.UUID;

/** Strong body retention for one submitted upgrade, not permission to perform an action. */
record PendingUpgradeView<T>(T source, UUID stream, UUID session, UUID epoch, long sequence,
                             int sourceId, UUID sourceUuid) {
    boolean completedBy(UUID incomingStream, UUID incomingSession, UUID incomingEpoch, long completed,
                        int incomingSourceId, UUID incomingSourceUuid) {
        return sequence > 0 && sequence == completed && sourceId == incomingSourceId
                && sourceUuid.equals(incomingSourceUuid) && stream.equals(incomingStream)
                && session.equals(incomingSession) && epoch.equals(incomingEpoch);
    }
}
