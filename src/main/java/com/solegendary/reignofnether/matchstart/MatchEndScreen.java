package com.solegendary.reignofnether.matchstart;

import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.RTSPlayerScoresEnum;
import com.solegendary.reignofnether.time.TimeUtils;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Bounded results viewport: compact cards on narrow windows, columns on wider ones. */
public class MatchEndScreen extends Screen {
    private static final int UNITS = RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED.ordinal();
    private static final int MILITARY = RTSPlayerScoresEnum.MILITARY_UNITS_PRODUCED.ordinal();
    private static final int BUILDINGS = RTSPlayerScoresEnum.TOTAL_BUILDINGS_CONSTRUCTED.ordinal();
    private static final int RESOURCES = RTSPlayerScoresEnum.TOTAL_RESOURCES_HARVESTED.ordinal();
    private static final int NORMAL = 0xFFFFFFFF, DIM = 0xFFB0B8C0, ACCENT = 0xFFE6C76A;
    private final Screen parent;
    private final long duration;
    private final List<MatchResultsModel.Team> teams;
    private MatchResultsLayout layout;
    private double scroll;
    private boolean draggingScroll;

    public MatchEndScreen() { this(null); }
    public MatchEndScreen(Screen parent) {
        super(Component.translatable("matchend.reignofnether.title"));
        this.parent = parent instanceof MatchEndScreen ? null : parent;
        this.duration = MatchEndClientEvents.getGameDurationTicks();
        this.teams = MatchResultsModel.teams(MatchEndClientEvents.getRows());
    }
    public Screen returnScreen() { return parent; }

    @Override protected void init() {
        boolean compact = MatchResultsLayout.create(width, height, 0).compact();
        int content = 0;
        for (var team : teams)
            content += 20 + team.members().size() * (compact ? 42 : 24) + (compact ? 44 : 28) + 12;
        layout = MatchResultsLayout.create(width, height, content);
        scroll = layout.clampScroll(scroll);
        draggingScroll = false;
        int buttonWidth = Math.min(100, Math.max(20, layout.width() - 16));
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(layout.left() + layout.width() - buttonWidth - 8,
                        layout.top() + layout.height() - 26, buttonWidth, 20).build());
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int left = layout.left(), top = layout.top(), right = left + layout.width();
        g.fill(left, top, right, top + layout.height(), 0xE8202530);
        g.fill(left, top, right, top + 2, ACCENT);
        int cl = left + 12, cr = right - 16, usable = Math.max(1, cr - cl);
        String hoveredName = null;
        fitText(g, title.getString(), cl, top + 9, usable - 90, ACCENT);
        drawNumberText(g, TimeUtils.getTimeStrFromTicks(duration), cr - 80, cr, top + 9, DIM);
        if (layout.compact()) {
            fitText(g, Component.translatable("matchend.reignofnether.scroll_hint").getString(), cl, top + 27, usable, DIM);
        } else {
            String[] labels = {"col_units", "col_military", "col_buildings"};
            for (int i = 0; i < 3; i++)
                drawNumberText(g, Component.translatable("matchend.reignofnether." + labels[i]).getString(),
                        numberLeft(cl, usable, i), numberRight(cl, usable, i), top + 28, DIM);
        }
        g.fill(cl, layout.bodyTop() - 3, cr, layout.bodyTop() - 2, 0x557E8999);
        if (layout.viewportHeight() > 0) {
            g.enableScissor(left + 4, layout.bodyTop(), right - 7, layout.bodyBottom());
            try {
                int y = layout.bodyTop() - (int) scroll;
                String localName = Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getName().getString();
                int group = 0;
                for (var team : teams) {
                    group++;
                    String label = Component.translatable(team.winner() ? "matchend.reignofnether.winner" : "matchend.reignofnether.loser").getString()
                            + " · " + Component.translatable("matchend.reignofnether.group", group).getString();
                    fitText(g, label, cl, y + 3, usable, team.winner() ? 0xFF6CE26C : 0xFFE05A5A);
                    y += 20;
                    for (var row : team.members()) {
                        int rowHeight = layout.compact() ? 42 : 24;
                        if (row.name().equals(localName)) g.fill(cl - 3, y - 2, cr, y + rowHeight - 3, 0x305CB7D4);
                        // Avoid skin fetch/render work for rows outside the scissored viewport.
                        if (y + rowHeight > layout.bodyTop() && y < layout.bodyBottom()) {
                            ResourceLocation skin = MyRenderer.getPlayerSkinRl(row.name());
                            if (skin != null) {
                                g.blit(skin, cl, y, 16, 16, 8.0f, 8.0f, 8, 8, 64, 64);
                                g.blit(skin, cl, y, 16, 16, 40.0f, 8.0f, 8, 8, 64, 64);
                            }
                            ResourceLocation key = ResourceLocation.tryParse(row.factionKey());
                            var faction = key == null ? null : Factions.getFaction(key);
                            if (faction != null && faction.icon != null) MyRenderer.renderIcon(g, faction.icon, cl + 20, y, 16);
                            int nameWidth = layout.compact() ? usable - 40 : numberLeft(cl, usable, 0) - cl - 44;
                            fitText(g, row.name(), cl + 40, y + 4, nameWidth, NORMAL);
                            if (font.width(row.name()) > nameWidth && mouseX >= cl + 40 && mouseX <= cl + 40 + nameWidth
                                    && mouseY >= Math.max(y, layout.bodyTop()) && mouseY < Math.min(y + 18, layout.bodyBottom()))
                                hoveredName = row.name();
                            renderNumbers(g, row.score(UNITS), row.score(MILITARY), row.score(BUILDINGS), cl, usable,
                                    layout.compact() ? y + 22 : y + 4, NORMAL, layout.compact());
                        }
                        y += rowHeight;
                    }
                    fitText(g, Component.translatable("matchend.reignofnether.party_total").getString(), cl, y,
                            layout.compact() ? usable : numberLeft(cl, usable, 0) - cl - 4, DIM);
                    renderNumbers(g, team.total(UNITS), team.total(MILITARY), team.total(BUILDINGS), cl, usable,
                            layout.compact() ? y + 14 : y, ACCENT, layout.compact());
                    y += layout.compact() ? 30 : 14;
                    fitText(g, Component.translatable("matchend.reignofnether.resources", Long.toString(team.total(RESOURCES))).getString(),
                            cl, y, usable, DIM);
                    y += 26;
                }
            } finally { g.disableScissor(); }
        }
        if (layout.maxScroll() > 0 && layout.viewportHeight() > 0) {
            int thumb = thumbHeight(), track = layout.viewportHeight() - thumb;
            int y = layout.bodyTop() + (int) (track * scroll / layout.maxScroll());
            g.fill(right - 6, layout.bodyTop(), right - 3, layout.bodyBottom(), 0xFF414B5C);
            g.fill(right - 6, y, right - 3, y + thumb, ACCENT);
        }
        fitText(g, "/rts-results", cl, top + layout.height() - 20, Math.max(0, layout.width() - 134), DIM);
        super.render(g, mouseX, mouseY, partialTick);
        if (hoveredName != null) g.renderTooltip(font, Component.literal(hoveredName), mouseX, mouseY);
    }

    private int numberLeft(int cl, int usable, int column) {
        return layout.compact() ? cl + usable * column / 3 : cl + usable * (46 + column * 18) / 100;
    }
    private int numberRight(int cl, int usable, int column) {
        return layout.compact() ? cl + usable * (column + 1) / 3 - 5 : cl + usable * (64 + column * 18) / 100 - 4;
    }
    private void renderNumbers(GuiGraphics g, long units, long military, long buildings, int cl, int usable, int y, int color, boolean labels) {
        long[] values = {units, military, buildings};
        String[] keys = {"col_units", "col_military", "col_buildings"};
        for (int i = 0; i < 3; i++) {
            String text = (labels ? Component.translatable("matchend.reignofnether." + keys[i]).getString() + " " : "") + values[i];
            drawNumberText(g, text, numberLeft(cl, usable, i), numberRight(cl, usable, i), y, color);
        }
    }
    private void fitText(GuiGraphics g, String text, int x, int y, int maxWidth, int color) {
        if (maxWidth <= 0) return;
        if (font.width(text) > maxWidth) {
            String suffix = font.width("…") <= maxWidth ? "…" : "";
            text = font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(suffix))) + suffix;
        }
        g.drawString(font, text, x, y, color, true);
    }
    private void drawNumberText(GuiGraphics g, String text, int left, int right, int y, int color) {
        int space = Math.max(1, right - left), textWidth = Math.max(1, font.width(text));
        float scale = Math.min(1f, (float) space / textWidth);
        g.pose().pushPose();
        g.pose().translate(right - textWidth * scale, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, 0, 0, color, true);
        g.pose().popPose();
    }
    private int thumbHeight() { return Math.min(layout.viewportHeight(), Math.max(12, layout.viewportHeight() * layout.viewportHeight() / Math.max(1, layout.contentHeight()))); }
    private void scrollToMouse(double mouseY) {
        int track = layout.viewportHeight() - thumbHeight();
        scroll = layout.clampScroll(track <= 0 ? 0 : (mouseY - layout.bodyTop() - thumbHeight() / 2d) * layout.maxScroll() / track);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= layout.left() && mouseX <= layout.left() + layout.width()
                && mouseY >= layout.bodyTop() && mouseY <= layout.bodyBottom()) {
            scroll = layout.clampScroll(scroll - delta * 28);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && layout.maxScroll() > 0 && x >= layout.left() + layout.width() - 9
                && x <= layout.left() + layout.width() && y >= layout.bodyTop() && y <= layout.bodyBottom()) {
            draggingScroll = true; scrollToMouse(y); return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (draggingScroll && button == 0) { scrollToMouse(y); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && draggingScroll) { draggingScroll = false; return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        switch (key) {
            case GLFW.GLFW_KEY_PAGE_DOWN -> scroll = layout.clampScroll(scroll + layout.viewportHeight());
            case GLFW.GLFW_KEY_PAGE_UP -> scroll = layout.clampScroll(scroll - layout.viewportHeight());
            case GLFW.GLFW_KEY_DOWN -> scroll = layout.clampScroll(scroll + 28);
            case GLFW.GLFW_KEY_UP -> scroll = layout.clampScroll(scroll - 28);
            case GLFW.GLFW_KEY_HOME -> scroll = 0;
            case GLFW.GLFW_KEY_END -> scroll = layout.maxScroll();
            default -> { return super.keyPressed(key, scan, modifiers); }
        }
        return true;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { MatchEndClientEvents.dismiss(); }
}
