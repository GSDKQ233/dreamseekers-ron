// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.unitprogression;

/** Pure GUI-pixel policy, independent of physical display resolution and Minecraft bootstrap. */
final class AdvancedResearchTooltipLayout {
    private AdvancedResearchTooltipLayout(){}
    static int wrapWidth(int guiScaledWidth){
        // Leave normal renderer padding/placement room. Do not impose a minimum that can
        // exceed an unusually narrow scaled window, and avoid subtraction overflow.
        if(guiScaledWidth<=30)return 1;
        return Math.min(310,guiScaledWidth-30);
    }
}
