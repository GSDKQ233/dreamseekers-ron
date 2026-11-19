// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unitprogression;
/** Explicit, nonstacking expansion coefficients; these never grant research or perform world writes. */
public final class ExpansionResearchMath {
    private ExpansionResearchMath() {}
    public static float airDamage(float base,boolean researched,boolean flyingVictim) {
        return researched&&flyingVictim ? base*1.2f : base;
    }
    public static double buildingBudget(double base,boolean researched) { return researched ? base*1.25d : base; }
    public static float healing(float amount,boolean inhibited) { return inhibited ? amount*.75f : amount; }
}
