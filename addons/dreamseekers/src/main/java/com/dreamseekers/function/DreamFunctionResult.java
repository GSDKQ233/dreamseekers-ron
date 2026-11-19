// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.function;

/** Explicit fail-closed response. There is deliberately no success/effect result in this scaffold. */
public record DreamFunctionResult(Status status, String reasonKey) {
    public enum Status {
        UNAVAILABLE, NOT_SERVER, UNAUTHORIZED, WRONG_FACTION, NOT_LIVE, NOT_BUILT,
        DESTROYED, UNSUPPORTED_SCHEMA, WRONG_ROLE, INVALID_REQUEST
    }
    public static DreamFunctionResult of(Status status) {
        return new DreamFunctionResult(status, "dreamseekers.function." + status.name().toLowerCase(java.util.Locale.ROOT));
    }
    public boolean available() { return false; }
}
