// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.buildingability;

import com.dreamseekers.ecologyworkposts.EcologyClient;
import com.dreamseekers.ecologyworkposts.EcologyPackets;
import com.dreamseekers.ecologyworkposts.EcologyWorkPosts;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.research.economy.EconomyActionClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** A bounded live roster. Rows have no authority and never contain a server-supplied position. */
public final class EcologyCodexScreen extends Screen {
    private static final int ROW_HEIGHT = 34, MAX_VISIBLE_ROWS = 9;
    private static final int PANEL = 0xF0142024, EDGE = 0xFF66B8A5, TEXT = 0xFFE5F5F0, MUTED = 0xFF9CBAB3;
    private final BuildingPlacement court;
    private final Screen returnScreen;
    private final Level openedLevel;
    private final Connection openedConnection;
    private UUID nonce;
    private boolean closing;
    private int page, scroll, left, top, panelWidth, panelHeight, visibleRows;
    private Button previous, next, scrollUp, scrollDown;
    private final List<RowButtons> rowButtons = new ArrayList<>();

    private record RowButtons(Button select, Button focus, Button care) { }

    EcologyCodexScreen(BuildingPlacement court, UUID nonce, Screen returnScreen) {
        super(Component.translatable("building_ability.ecology_codex"));
        this.court = court;
        this.nonce = nonce;
        this.returnScreen = returnScreen;
        Minecraft mc = Minecraft.getInstance();
        openedLevel = mc.level;
        openedConnection = mc.getConnection() == null ? null : mc.getConnection().getConnection();
        var snapshot = BuildingAbilityClient.current(court, nonce);
        page = snapshot == null ? 0 : snapshot.codex().page();
    }

    BuildingPlacement host() { return court; }
    boolean ownsView(BuildingPlacement placement) { return !closing && court == placement; }

    @Override protected void init() {
        panelWidth = Math.max(260, Math.min(700, width - 20));
        panelHeight = Math.max(168, Math.min(430, height - 20));
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        visibleRows = Math.max(1, Math.min(MAX_VISIBLE_ROWS, (panelHeight - 124) / ROW_HEIGHT));
        rowButtons.clear();
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(left + panelWidth - 62, top + 8, 52, 20).build());
        int rowY = top + 66;
        for (int slot = 0; slot < visibleRows; slot++) {
            final int rowSlot = slot;
            int x = left + panelWidth - 182;
            Button select = addRenderableWidget(Button.builder(Component.translatable("building_ability.codex.select"),
                    button -> select(rowSlot)).bounds(x, rowY + 4, 42, 20).build());
            Button focus = addRenderableWidget(Button.builder(Component.translatable("building_ability.codex.focus"),
                    button -> focus(rowSlot)).bounds(x + 45, rowY + 4, 42, 20).build());
            Button care = addRenderableWidget(Button.builder(Component.translatable("building_ability.codex.assign"),
                    button -> care(rowSlot)).bounds(x + 90, rowY + 4, 80, 20).build());
            rowButtons.add(new RowButtons(select, focus, care));
            rowY += ROW_HEIGHT;
        }
        int footer = top + panelHeight - 48;
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(page - 1))
                .bounds(left + 10, footer, 26, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(page + 1))
                .bounds(left + 40, footer, 26, 20).build());
        scrollUp = addRenderableWidget(Button.builder(Component.literal("↑"), button -> moveScroll(-visibleRows))
                .bounds(left + panelWidth - 66, footer, 26, 20).build());
        scrollDown = addRenderableWidget(Button.builder(Component.literal("↓"), button -> moveScroll(visibleRows))
                .bounds(left + panelWidth - 36, footer, 26, 20).build());
        updateControls();
        BuildingAbilityClient.query(court);
    }

    private BuildingAbilityPackets.Snapshot snapshot() {
        return BuildingAbilityClient.current(court, nonce);
    }

    private BuildingCodex.Row rowAt(int slot) {
        var state = snapshot();
        int index = scroll + slot;
        return state == null || index < 0 || index >= state.codex().rows().size() ? null : state.codex().rows().get(index);
    }

    private int rowCount() {
        var state = snapshot();
        return state == null ? 0 : state.codex().rows().size();
    }

    private EcologyPackets.Snapshot careView() { return EcologyClient.codexView(court); }

    private void updateControls() {
        if (previous == null) return;
        var state = snapshot();
        var careState = careView();
        int count = rowCount();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, count - visibleRows)));
        previous.active = page > 0;
        next.active = state != null && page < 7 && (page + 1) * 32 < state.codex().totalRows();
        scrollUp.active = scroll > 0;
        scrollDown.active = scroll + visibleRows < count;
        for (int slot = 0; slot < rowButtons.size(); slot++) {
            RowButtons buttons = rowButtons.get(slot);
            BuildingCodex.Row row = rowAt(slot);
            LivingEntity entity = BuildingAbilityClient.resolveRow(court, nonce, row);
            boolean usable = entity != null;
            buttons.select().visible = buttons.focus().visible = buttons.care().visible = usable;
            buttons.select().active = usable;
            buttons.focus().active = usable && !OrthoviewClientEvents.isCameraLocked();
            boolean releasing = row != null && (row.careState() == 1 || row.careState() == 2);
            String careKey = row != null && row.careState() == 3 ? "elsewhere" : releasing ? "release" : "assign";
            buttons.care().setMessage(Component.translatable("building_ability.codex." + careKey));
            buttons.care().active = usable && careState != null && EconomyActionClient.ready(careState.epoch())
                    && (releasing || !Keybindings.shiftMod.isDown() && row.careState() == 0 && row.careEligible()
                    && careState.occupants().size() < EcologyWorkPosts.CAPACITY);
            buttons.care().setTooltip(Tooltip.create(Component.translatable("building_ability.codex.care_rules")));
        }
    }

    private void select(int slot) {
        if (BuildingAbilityClient.select(court, nonce, rowAt(slot))) onClose();
    }

    private void focus(int slot) {
        if (BuildingAbilityClient.focus(court, nonce, rowAt(slot))) onClose();
    }

    private void care(int slot) {
        BuildingAbilityClient.care(court, nonce, rowAt(slot));
        // Do not optimistically change occupancy or move a unit; only the original server chain does so.
        updateControls();
    }

    private void changePage(int nextPage) {
        if (nextPage < 0 || nextPage > 7 || nextPage == page) return;
        UUID nextNonce = BuildingAbilityClient.changeCodexPage(court, nonce, nextPage);
        if (nextNonce == null) return;
        nonce = nextNonce;
        page = nextPage;
        scroll = 0;
        updateControls();
    }

    private void moveScroll(int amount) {
        scroll = Math.max(0, Math.min(scroll + amount, Math.max(0, rowCount() - visibleRows)));
        updateControls();
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX >= left && mouseX <= left + panelWidth && mouseY >= top + 62
                && mouseY < top + panelHeight - 52) {
            if (amount != 0) moveScroll(amount > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override public void tick() {
        var current = snapshot();
        if (!BuildingAbilityClient.validHost(court, nonce) || !OrthoviewClientEvents.isEnabled()
                || !BuildingClientEvents.getSelectedBuildings().contains(court)
                || current != null && (!current.supported() || !current.available())) {
            onClose();
            return;
        }
        BuildingAbilityClient.query(court);
        updateControls();
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateControls();
        graphics.fill(left - 1, top - 1, left + panelWidth + 1, top + panelHeight + 1, EDGE);
        graphics.fill(left, top, left + panelWidth, top + panelHeight, PANEL);
        graphics.drawString(font, title, left + 10, top + 12, TEXT, false);
        var state = snapshot();
        Component summary = state == null ? Component.translatable("building_ability.waiting")
                : Component.translatable("building_ability.codex.summary", state.codex().totalRows(),
                state.codex().rows().stream().filter(row -> row.hp() < row.maxHp()).count());
        drawClipped(graphics, summary, left + 10, top + 34, panelWidth - 20, MUTED);
        var care = careView();
        Component careSummary = care == null ? Component.translatable("building_ability.codex.care_waiting")
                : Component.translatable(care.researched() ? "building_ability.codex.care_ready" : "building_ability.codex.care_locked",
                care.occupants().size(), EcologyWorkPosts.CAPACITY);
        drawClipped(graphics, careSummary, left + 10, top + 47, panelWidth - 20, MUTED);
        graphics.fill(left + 8, top + 61, left + panelWidth - 8, top + 62, 0x6066B8A5);
        if (state != null && state.codex().rows().isEmpty())
            drawClipped(graphics, Component.translatable("building_ability.codex.empty"), left + 12, top + 74, panelWidth - 24, MUTED);
        int y = top + 66;
        for (int slot = 0; slot < visibleRows; slot++) {
            BuildingCodex.Row row = rowAt(slot);
            if (row == null) break;
            if ((slot & 1) == 0) graphics.fill(left + 8, y, left + panelWidth - 8, y + ROW_HEIGHT - 1, 0x40344F49);
            LivingEntity entity = BuildingAbilityClient.resolveRow(court, nonce, row);
            if (entity == null) {
                drawClipped(graphics, Component.translatable("building_ability.codex.unavailable_row"),
                        left + 12, y + 10, panelWidth - 202, MUTED);
            } else {
                // Never render stale row names, health or position for a transferred/hidden client body.
                drawClipped(graphics, entity.getName(), left + 12, y + 4, panelWidth - 202, TEXT);
                String hp = String.format(Locale.ROOT, "%.1f / %.1f", row.hp(), row.maxHp());
                drawClipped(graphics, Component.translatable("building_ability.codex.row_status", hp,
                        Component.translatable("building_ability.codex.care_state_" + row.careState())),
                        left + 12, y + 17, panelWidth - 202, row.hp() < row.maxHp() ? 0xFFFFCD9A : MUTED);
            }
            y += ROW_HEIGHT;
        }
        int footer = top + panelHeight - 48;
        int totalPages = state == null ? 0 : Math.max(1, (state.codex().totalRows() + 31) / 32);
        drawClipped(graphics, Component.translatable("building_ability.codex.page", page + 1,
                totalPages == 0 ? "?" : Integer.toString(totalPages), rowCount() == 0 ? 0 : scroll + 1, Math.min(scroll + visibleRows, rowCount())),
                left + 74, footer + 6, panelWidth - 150, MUTED);
        Component footerLine = state != null && state.codex().truncated()
                ? Component.translatable("building_ability.codex.truncated") : Component.translatable("building_ability.codex.private_rules");
        drawClipped(graphics, footerLine, left + 10, top + panelHeight - 18, panelWidth - 20, MUTED);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawClipped(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(text.getString(), Math.max(1, maxWidth)), x, y, color, false);
    }

    @Override public void onClose() {
        if (closing) return;
        closing = true;
        BuildingAbilityClient.releaseView(court, nonce);
        Minecraft mc = Minecraft.getInstance();
        Connection current = mc.getConnection() == null ? null : mc.getConnection().getConnection();
        mc.setScreen(mc.level == openedLevel && current == openedConnection && mc.player != null ? returnScreen : null);
    }

    @Override public void removed() {
        closing = true;
        BuildingAbilityClient.releaseView(court, nonce);
        super.removed();
    }

    @Override public boolean isPauseScreen() { return false; }
}
