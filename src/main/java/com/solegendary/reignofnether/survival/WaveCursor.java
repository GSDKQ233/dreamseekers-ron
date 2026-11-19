package com.solegendary.reignofnether.survival;

/** The planned next encounter, the newest issued (not yet cleared) wave, and the terminal state.
 * As before, nights can add another wave before the preceding enemies have been defeated.
 * The whole outstanding group earns one clear. Wave 30 is issued at most once without a cheat.
 */
record WaveCursor(int next, int active, boolean finished) {
    static final int LAST = 30;
    // Server-to-client SET_WAVE_NUMBER also distinguishes the two states with no next encounter.
    static final int FINAL_ACTIVE = -1;
    static final int FINISHED = 0;

    WaveCursor {
        next = finished ? LAST : Math.max(1, Math.min(LAST, next));
        active = finished || active <= 0 ? 0 : Math.min(LAST, active);
    }

    boolean canStart() { return !finished && !(active == LAST && next == LAST); }

    WaveCursor start() {
        return canStart() ? new WaveCursor(Math.min(LAST, next + 1), next, false) : this;
    }

    WaveCursor clear() {
        return active == 0 ? this : new WaveCursor(next, 0, active == LAST && next == LAST);
    }

    int forClient() { return finished ? FINISHED : canStart() ? next : FINAL_ACTIVE; }
}
