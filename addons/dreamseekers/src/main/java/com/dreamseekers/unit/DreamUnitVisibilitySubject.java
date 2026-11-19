package com.dreamseekers.unit;

/** Only paid/controlled addon adapters participate; natural dependency entities do not. */
public final class DreamUnitVisibilitySubject {
    private DreamUnitVisibilitySubject() { }

    public static boolean matches(Object entity) {
        return entity instanceof DreamRtsUnit || entity instanceof DreamNoncombatUnit;
    }
}
