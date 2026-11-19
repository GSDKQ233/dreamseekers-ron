package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.player.MatchStatsClientboundPacket.MatchStatRow;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/** Main-thread result cache, cleared on logout/reset/new match rather than on dismissal. */
public class MatchEndClientEvents {
    private static final MatchResultsState STATE = new MatchResultsState();
    private static MatchResultsModel.Snapshot snapshot = new MatchResultsModel.Snapshot(0, List.of());

    public static long getGameDurationTicks() { return snapshot.ticks(); }
    public static List<MatchResultsModel.Row> getRows() { return snapshot.rows(); }
    public static boolean hasResults() { return STATE.hasResults(); }

    public static void receive(long ticks, List<MatchStatRow> incoming) {
        if (incoming == null || incoming.isEmpty()) { clear(); return; }
        List<MatchResultsModel.Row> rows = new ArrayList<>();
        for (MatchStatRow row : incoming) {
            if (row == null) continue;
            List<Long> scores = new ArrayList<>();
            if (row.scores != null) for (int value : row.scores) scores.add((long) value);
            rows.add(new MatchResultsModel.Row(row.name,
                    row.faction == null || row.faction.key == null ? null : row.faction.key.toString(),
                    row.winner, row.teamId, scores));
        }
        if (rows.isEmpty()) { clear(); return; }
        var next = new MatchResultsModel.Snapshot(ticks, rows);
        if (!STATE.receive(next)) return;
        snapshot = next;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MatchEndScreen screen) {
            // Replace an already visible result, keeping the original return destination.
            STATE.dismiss();
            mc.setScreen(new MatchEndScreen(screen.returnScreen()));
        }
    }

    public static void clear() {
        STATE.clear();
        snapshot = new MatchResultsModel.Snapshot(0, List.of());
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MatchEndScreen) mc.setScreen(null);
    }

    public static void dismiss() {
        STATE.dismiss();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MatchEndScreen screen) mc.setScreen(screen.returnScreen());
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (STATE.consumeOpen(mc.player != null && mc.level != null, mc.screen == null))
            mc.setScreen(new MatchEndScreen(null));
    }

    @SubscribeEvent
    public static void onRegisterCommand(RegisterClientCommandsEvent evt) {
        evt.getDispatcher().register(Commands.literal("rts-results").executes(command -> {
            if (!STATE.requestOpen()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) mc.player.sendSystemMessage(Component.translatable("matchend.reignofnether.empty"));
                return 0;
            }
            // Chat closes after command dispatch; opening on the next free tick avoids losing the screen.
            return 1;
        }));
    }

    @SubscribeEvent
    public static void onPauseMenu(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen pause) || !hasResults()) return;
        event.addListener(Button.builder(Component.translatable("matchend.reignofnether.title"), button -> {
            if (!hasResults()) return;
            STATE.dismiss();
            Minecraft.getInstance().setScreen(new MatchEndScreen(pause));
        }).bounds(8, Math.max(8, pause.height - 28), Math.min(140, Math.max(20, pause.width - 16)), 20).build());
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut evt) { clear(); }
}
