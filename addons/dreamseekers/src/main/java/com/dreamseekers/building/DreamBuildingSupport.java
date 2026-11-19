// SPDX-License-Identifier: GPL-3.0-only
// Original addon implementation using Reign of Nether's public building API.
package com.dreamseekers.building;

import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.sandbox.SandboxClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Shared original structures and server-authoritative faction / tech checks. */
public final class DreamBuildingSupport {
    private DreamBuildingSupport() {}

    public static final ResourceLocation FACTION = ResourceLocation.fromNamespaceAndPath("dreamseekers", "dreamseekers");

    /**
     * Load only this addon's original structure, rather than RoN's hardcoded namespace.
     * Direct classpath loading intentionally gives client and server identical geometry;
     * replacing these gameplay templates with client-only resource packs is unsupported.
     */
    public static ArrayList<BuildingBlock> load(String name) {
        String path = "/data/dreamseekers/structures/" + name + ".nbt";
        try (InputStream stream = DreamBuildingSupport.class.getResourceAsStream(path)) {
            if (stream == null)
                throw new IllegalStateException("Missing original Dreamseekers structure: " + path);
            CompoundTag nbt = NbtIo.readCompressed(stream);
            ListTag palette = nbt.getList("palette", 10);
            if (palette.isEmpty() || nbt.getList("blocks", 10).isEmpty())
                throw new IllegalStateException("Empty Dreamseekers structure: " + path);
            for (int i = 0; i < palette.size(); i++) {
                String identifier = palette.getCompound(i).getString("Name");
                ResourceLocation key = ResourceLocation.tryParse(identifier);
                if (key == null || !BuiltInRegistries.BLOCK.containsKey(key))
                    throw new IllegalStateException("Missing required structure block " + identifier + " in " + path);
            }
            return BuildingBlockData.getBuildingBlocksFromNbt(nbt);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot decode original Dreamseekers structure: " + path, error);
        }
    }

    public static String availability(Building building, String ownerName) {
        RTSPlayer owner = ownerName == null ? null : PlayerServerEvents.getRTSPlayer(ownerName);
        return availability(building, ownerName, owner == null ? null : owner.faction,
                BuildingServerEvents.getBuildings(),
                owner != null && ResearchServerEvents.playerHasCheat(ownerName, "modifythephasevariance"));
    }

    /** Pure common policy, also used by the client for an accurately disabled button. */
    public static String availability(Building building, String ownerName, Faction faction,
                                      Iterable<BuildingPlacement> placements, boolean ignorePrerequisite) {
        if (ownerName == null || ownerName.isBlank() || !com.dreamseekers.economy.DreamFactionAccess.allows(faction))
            return "dreamseekers.error.wrong_faction";
        if (building instanceof DreamScaffoldBuilding scaffold &&
                (scaffold.spec() == DreamBuildingSpec.SHADOW_MINE || scaffold.spec() == DreamBuildingSpec.SHADOW_FURNACE)) {
            int count=0;
            for (BuildingPlacement placement : placements)
                if (ownerName.equals(placement.ownerName) && placement.getBuilding() == building && !placement.isDestroyedServerside) count++;
            if(count >= 2) return "dreamseekers.error.passive_limit";
        }
        if (building.isCapitol || ignorePrerequisite)
            return null;
        for (BuildingPlacement placement : placements)
            if (ownerName.equals(placement.ownerName) && placement.isBuilt
                    && placement.getBuilding() instanceof DreamAnchor)
                return null;
        return "dreamseekers.error.requires_anchor";
    }

    private static boolean canBuildClient(Building building) {
        if (SandboxClientEvents.isSandboxPlayer())
            return true;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null)
            return false;
        String owner = HudClientEvents.hudSelectedEntity instanceof Unit unit
                ? unit.getOwnerName() : minecraft.player.getName().getString();
        return availability(building, owner, PlayerClientEvents.getPlayerFaction(owner),
                BuildingClientEvents.getBuildings(), ResearchClient.hasCheat("modifythephasevariance")) == null;
    }

    public static BuildingPlaceButton button(Building building, Keybinding hotkey) {
        String key = "buildings.dreamseekers." + building.structureName;
        final Building tooltipBuilding = building;
        return new BuildingPlaceButton(Component.translatable(key).getString(), building.icon, hotkey,
                () -> BuildingClientEvents.getBuildingToPlace() == building,
                () -> false, () -> canBuildClient(building),
                List.of(), building) {
            @Override public void renderTooltip(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY) {
                tooltipLines = ClientTooltip.lines(tooltipBuilding, key);
                super.renderTooltip(graphics, mouseX, mouseY);
            }
        };
    }

    /** Resolve language and scaled width at hover time, including for cached building buttons.
     * Split logical components once; cost/population lines keep their original formatting. */
    private static final class ClientTooltip {
        private static List<FormattedCharSequence> lines(Building building, String key) {
            Minecraft mc = Minecraft.getInstance();
            int screenWidth = mc.getWindow().getGuiScaledWidth();
            int width = screenWidth <= 30 ? 1 : Math.min(310, screenWidth - 30);
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.addAll(mc.font.split(Component.translatable(key).withStyle(Style.EMPTY.withBold(true)), width));
            lines.add(ResourceCosts.getFormattedCost(building.cost));
            lines.add(ResourceCosts.getFormattedPop(building.cost));
            lines.add(FormattedCharSequence.forward("", Style.EMPTY));
            lines.addAll(mc.font.split(Component.translatable(key + ".tooltip1"), width));
            lines.addAll(mc.font.split(Component.translatable(key + ".tooltip2"), width));
            return lines;
        }
    }
}
