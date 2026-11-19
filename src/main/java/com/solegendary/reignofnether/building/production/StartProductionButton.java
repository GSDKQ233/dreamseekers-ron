package com.solegendary.reignofnether.building.production;

import com.solegendary.reignofnether.building.BuildingProductionServerboundPacket;
import com.solegendary.reignofnether.building.BuildingServerboundPacket;
import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.keybinds.Keybinding;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.Supplier;

public class StartProductionButton extends Button {
    private com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement liveResearchPlacement;
    private ProductionItem liveResearchItem;

    /** Only explicit native research opts in; receipt research has its own live tooltip renderer. */
    public void bindLiveResearchTime(com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement placement, ProductionItem item) {
        if (item.workKind == ProductionItem.WorkKind.RESEARCH
                && !(item instanceof com.solegendary.reignofnether.research.economy.ReceiptBackedResearchItem)) {
            liveResearchPlacement = placement;
            liveResearchItem = item;
        }
    }

    @Override public void renderTooltip(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY) {
        if (liveResearchPlacement == null || liveResearchItem == null || tooltipLines == null) {
            super.renderTooltip(graphics, mouseX, mouseY);
            return;
        }
        List<FormattedCharSequence> original = tooltipLines;
        var current = new java.util.ArrayList<FormattedCharSequence>(original.size());
        for (var line : original) {
            if (line instanceof com.solegendary.reignofnether.resources.ResourceCosts.TimeLine) {
                int ticks = liveResearchItem.getCost(true, liveResearchPlacement.ownerName).ticks;
                long seconds = ProductionWorkRates.estimatedSeconds(ticks, ProductionWorkRates.percent(liveResearchPlacement, liveResearchItem));
                current.add(FormattedCharSequence.forward("\uE004  " + seconds + "s", com.solegendary.reignofnether.util.MyRenderer.iconStyle));
            } else current.add(line); // Never replace cost/title/description/population lines.
        }
        try {
            tooltipLines = current;
            super.renderTooltip(graphics, mouseX, mouseY);
        } finally {
            tooltipLines = original; // Retain marker identity and cached buttons/pages across future hovers.
        }
    }

   public StartProductionButton(String name, ResourceLocation rl, Keybinding hotkey,
                                Supplier<Boolean> isHidden, Supplier<Boolean> isEnabled,
                                List<FormattedCharSequence> tooltipLines, ProductionItem production) {

        // generate x/y based on given position (starting at 0 which is bottom left 1 row above generic action buttons)
        super(name, Button.itemIconSize, rl, hotkey, () -> false, isHidden, isEnabled, () -> BuildingProductionServerboundPacket.startProduction(production), () -> {}, tooltipLines);
    }

    public StartProductionButton(String name, ResourceLocation iconRl, ResourceLocation frameRl, Keybinding hotkey,
                                 Supplier<Boolean> isHidden, Supplier<Boolean> isEnabled,
                                 List<FormattedCharSequence> tooltipLines, ProductionItem production) {

        // generate x/y based on given position (starting at 0 which is bottom left 1 row above generic action buttons)
        super(name, Button.itemIconSize, iconRl, frameRl, hotkey, () -> false, isHidden, isEnabled, () -> BuildingProductionServerboundPacket.startProduction(production), () -> {}, tooltipLines);
    }
}
