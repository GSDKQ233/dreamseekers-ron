// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

/** Presentation-only values from a bounded owner-private server tier; never accepted by a cast request. */
public record SkillPotencyView(int tier) {
    public SkillPotencyView { if(tier<0||tier>2)throw new IllegalArgumentException("Invalid amplification tier"); }
    public static boolean supports(UnitSkill skill){return skill==UnitSkill.GEL_GUARD||skill==UnitSkill.JELLY_FLOW||skill==UnitSkill.FOX_BLESSING;}
    public String multiplier(){return switch(tier){case 1->"1.2";case 2->"1.5";default->"1";};}
    public String shieldOrHealingTotal(){return switch(tier){case 1->"14.4";case 2->"18";default->"12";};}
    public String healingPulse(){return switch(tier){case 1->"2.4";case 2->"3";default->"2";};}
    public String foxPercent(){return switch(tier){case 1->"24";case 2->"30";default->"20";};}
    public String reboundPercent(){return switch(tier){case 1->"18";case 2->"22.5";default->"15";};}
    public String tidePercent(){return switch(tier){case 1->"12";case 2->"15";default->"10";};}
}
