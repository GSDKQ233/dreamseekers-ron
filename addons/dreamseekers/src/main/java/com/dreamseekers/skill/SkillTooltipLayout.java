// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

/** Same pure scaled-pixel policy as AdvancedResearchTooltipLayout; no client bootstrap. */
final class SkillTooltipLayout {
    private SkillTooltipLayout(){}
    static int wrapWidth(int guiScaledWidth){
        if(guiScaledWidth<=30)return 1;
        return Math.min(310,guiScaledWidth-30);
    }
}
