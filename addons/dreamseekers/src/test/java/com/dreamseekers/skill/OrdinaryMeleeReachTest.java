// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrdinaryMeleeReachTest {
    @Test void usesBothBonusesAndWidensOnlyAfterFloatArithmetic() {
        assertEquals(3.75, OrdinaryMeleeReach.rawSquared(0.5F, 0.25F, 1.0F, 0.5F));
        assertEquals((double) 4.3F, OrdinaryMeleeReach.rawSquared(0.6F, 0.4F, 0.1F, 0.2F));
    }

    @Test void matchesThePinnedNativeFormulaWithoutAddingValidityChecks() {
        float[] widths = {0.0F, -0.0F, 0.3F, 0.61F, 1.4F, 2.035F,
                Float.intBitsToFloat(0x1a000001), Float.MAX_VALUE, Float.NaN};
        float[] bonuses = {-3.0F, -0.0F, 0.0F, 0.1F, 0.23F, 1.0F, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (float sourceWidth : widths) for (float sourceBonus : bonuses)
            for (float targetWidth : widths) for (float targetBonus : bonuses) {
                // RoN's AbstractMeleeAttackUnitGoal#getAttackReachSqr for attacker/target bonuses.
                float width = sourceWidth;
                width += sourceBonus;
                float effectiveTargetWidth = targetWidth;
                effectiveTargetWidth += targetBonus;
                double expected = width * 2.0F * width * 2.0F + effectiveTargetWidth;
                double actual = OrdinaryMeleeReach.rawSquared(sourceWidth, sourceBonus, targetWidth, targetBonus);
                assertEquals(Double.doubleToLongBits(expected), Double.doubleToLongBits(actual),
                        () -> sourceWidth + " + " + sourceBonus + " vs " + targetWidth + " + " + targetBonus);
            }
    }
}
