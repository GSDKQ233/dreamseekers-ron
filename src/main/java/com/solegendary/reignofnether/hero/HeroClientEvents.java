package com.solegendary.reignofnether.hero;

import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.unit.HeroUnitSave;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;

public class HeroClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    public static ArrayList<HeroUnitSave> fallenHeroes = new ArrayList<>();

    // A server with an empty roster sends no add packets. Clear the previous session
    // even if its player was already detached by the time the logout event arrived.
    @SubscribeEvent
    public static void onDisconnect(ClientPlayerNetworkEvent.LoggingOut evt) {
        fallenHeroes.clear();
    }

    public static void addFallenHero(HeroUnitSave heroUnitSave) {
        if (MC.player != null && heroUnitSave.ownerName.equals(MC.player.getName().getString())) {
            fallenHeroes.removeIf(fHero -> fHero.ownerName.equals(heroUnitSave.ownerName) && fHero.name.equals(heroUnitSave.name));
            fallenHeroes.add(heroUnitSave);
            for (BuildingPlacement buildingPlacement : BuildingClientEvents.getBuildings())
                if (buildingPlacement instanceof ProductionPlacement)
                    buildingPlacement.updateButtons();
        }
    }
}
