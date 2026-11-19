package com.solegendary.reignofnether.matchstart;

import java.util.Objects;

/** Pure client presentation lifecycle; an identical result never steals focus twice. */
public final class MatchResultsState {
    private Object identity;
    private boolean pending;
    public boolean receive(Object value) {
        if (value == null || Objects.equals(identity, value)) return false;
        identity = value;
        pending = true;
        return true;
    }
    public boolean hasResults() { return identity != null; }
    public boolean requestOpen() {
        if (!hasResults()) return false;
        pending = true;
        return true;
    }
    public boolean consumeOpen(boolean worldReady, boolean screenFree) {
        if (!pending || !worldReady || !screenFree) return false;
        pending = false;
        return true;
    }
    public void dismiss() { pending = false; }
    public void clear() { identity = null; pending = false; }
}
