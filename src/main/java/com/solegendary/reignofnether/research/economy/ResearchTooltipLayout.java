package com.solegendary.reignofnether.research.economy;

/** Pure scaled-GUI-pixel budget; no client bootstrap or resource/production mutation. */
final class ResearchTooltipLayout {
    private ResearchTooltipLayout(){}
    static int wrapWidth(int guiScaledWidth){
        if(guiScaledWidth<=30)return 1;
        return Math.min(310,guiScaledWidth-30);
    }
}
