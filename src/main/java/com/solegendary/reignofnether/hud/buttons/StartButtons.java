package com.solegendary.reignofnether.hud.buttons;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.player.PlayerServerboundPacket;
import com.solegendary.reignofnether.tutorial.TutorialClientEvents;
import com.solegendary.reignofnether.tutorial.TutorialStage;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.util.LanguageUtil;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.ArrayList;
import com.solegendary.reignofnether.util.MiscUtil;

import static com.solegendary.reignofnether.util.MiscUtil.fcs;

public class StartButtons {

    public static final int ICON_SIZE = 14;
    private static final int STARTING_WORKERS = 5;

    /** Addons are playable without being survival-wave or title-panorama factions. */
    public static List<Button> getAdditionalFactionButtons() {
        List<Button> buttons = new ArrayList<>();
        if (TutorialClientEvents.isEnabled()) return buttons;
        for (ResourceLocation key : com.solegendary.reignofnether.faction.StartFactionSelection
                .additionalFactionKeys(Factions.PLAYABLE_FACTIONS, Factions::getFaction)) {
            var faction = Factions.getFaction(key);
            buttons.add(new Button("StartFaction:" + key, ICON_SIZE, faction.icon, (Keybinding) null,
                    () -> CursorClientEvents.getLeftClickAction() == UnitAction.STARTRTS_REGISTERED_FACTION
                            && key.equals(CursorClientEvents.startFactionSelection.selected()),
                    () -> !PlayerClientEvents.canStartRTS,
                    () -> true,
                    () -> {
                        CursorClientEvents.startFactionSelection.select(key);
                        CursorClientEvents.setLeftClickAction(UnitAction.STARTRTS_REGISTERED_FACTION);
                    }, null,
                    List.of(fcs(MiscUtil.getFactionName(faction), true),
                            fcs(LanguageUtil.getTranslation("hud.startbuttons.reignofnether.registered", STARTING_WORKERS)))));
        }
        return buttons;
    }

    public static Button sandboxStartButton = new Button(
            "Sandbox",
            ICON_SIZE,
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/tick.png"),
            (Keybinding) null,
            () -> false,
            () -> false,
            () -> true,
            () -> PlayerServerboundPacket.startRTS(Factions.NONE, 0d,0d,0d),
            null,
            List.of(
                    fcs(LanguageUtil.getTranslation("hud.gamemode.reignofnether.sandbox_confirm"))
            )
    );
    public static Button villagerStartButton = new Button(
        "VillagersFaction",
            ICON_SIZE,
        ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/villager.png"),
        (Keybinding) null,
        () -> CursorClientEvents.getLeftClickAction() == UnitAction.STARTRTS_VILLAGERS,
        () -> !TutorialClientEvents.isAtOrPastStage(TutorialStage.PLACE_WORKERS_B) || !PlayerClientEvents.canStartRTS,
        () -> true,
        () -> {
            CursorClientEvents.setLeftClickAction(UnitAction.STARTRTS_VILLAGERS);
        },
        null,
        List.of(
            fcs(LanguageUtil.getTranslation("hud.startbuttons.villagers.reignofnether.first"), true),
            fcs(LanguageUtil.getTranslation("hud.startbuttons.villagers.reignofnether.second", STARTING_WORKERS))
        )
    );

    public static Button monsterStartButton = new Button(
        "Monsters",
            ICON_SIZE,
        ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/creeper.png"),
        (Keybinding) null,
        () -> CursorClientEvents.getLeftClickAction() == UnitAction.STARTRTS_MONSTERS,
        () -> TutorialClientEvents.isEnabled() || !PlayerClientEvents.canStartRTS,
        () -> !TutorialClientEvents.isEnabled(),
        () -> CursorClientEvents.setLeftClickAction(UnitAction.STARTRTS_MONSTERS),
        null,
        List.of(
            fcs(LanguageUtil.getTranslation("hud.startbuttons.monsters.reignofnether.first"), true),
            fcs(LanguageUtil.getTranslation("hud.startbuttons.monsters.reignofnether.second", STARTING_WORKERS))
        )
    );

    public static Button piglinStartButton = new Button(
            "Piglins",
            ICON_SIZE,
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/grunt.png"),
            (Keybinding) null,
            () -> CursorClientEvents.getLeftClickAction() == UnitAction.STARTRTS_PIGLINS,
            () -> TutorialClientEvents.isEnabled() || !PlayerClientEvents.canStartRTS,
            () -> !TutorialClientEvents.isEnabled(),
            () -> CursorClientEvents.setLeftClickAction(UnitAction.STARTRTS_PIGLINS),
            null,
            List.of(
                    fcs(LanguageUtil.getTranslation("hud.startbuttons.piglins.reignofnether.first"), true),
                    fcs(LanguageUtil.getTranslation("hud.startbuttons.piglins.reignofnether.second", STARTING_WORKERS))
            )
    );
}
