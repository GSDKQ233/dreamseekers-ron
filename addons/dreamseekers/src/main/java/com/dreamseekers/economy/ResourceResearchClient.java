// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.economy;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.building.DreamBuildingSpec;
import com.dreamseekers.building.DreamResonanceAura;
import com.dreamseekers.building.DreamScaffoldBuilding;
import com.dreamseekers.building.ReverieGarden;
import com.dreamseekers.buildingability.DaisWardEffects;
import com.dreamseekers.client.DreamTooltipText;
import com.dreamseekers.resonance.ResonanceNetwork;
import com.dreamseekers.resonance.ResonanceResearch;
import com.solegendary.reignofnether.api.BuildingUnitOrderHooks;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.client.ActiveClientTickClock;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.research.economy.EconomyActionClient;
import com.solegendary.reignofnether.resources.ExtendedResourcesClient;
import com.solegendary.reignofnether.resources.ResourcesClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A technology entry inside the ordinary selection panel, not a unit order, ability or global research. */
@Mod.EventBusSubscriber(modid = DreamseekersMod.MOD_ID, value = Dist.CLIENT)
public final class ResourceResearchClient {
    private static final String KEY = "research.dreamseekers.production_methods.";
    private static final String DAIS_KEY = "research.dreamseekers.resonance_amplification.";
    private static final ResourceLocation RESEARCH_ICON = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/knowledge_book.png");
    private static final ResourceLocation CANCEL_ICON = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/barrier.png");
    private static final ActiveClientTickClock CLOCK = new ActiveClientTickClock();
    private static final int FRESH_TICKS = 60;
    private static long queried = Long.MIN_VALUE;
    private static Connection connection;
    private static Panel panel;
    private static boolean registered;

    private static final class Panel {
        final BuildingPlacement host;
        final String owner;
        final boolean dais;
        final UUID instance, nonce = UUID.randomUUID();
        ResourceResearchPackets.Snapshot snapshot;
        long received, pending;
        Panel(BuildingPlacement host, UUID instance) {
            this.host = host; this.instance = instance; owner = host.ownerName;
            dais = ResourceBuildingResearch.isDais(host);
        }
    }
    private ResourceResearchClient() {}
    public static void register() {
        if (!registered) {
            registered = true;
            BuildingUnitOrderHooks.registerButtons(ResourceResearchClient::append);
        }
    }

    private static boolean ownedPanel(BuildingPlacement host) {
        var mc = Minecraft.getInstance();
        return host != null && mc.player != null && mc.level == host.level && host.isBuilt && !host.isDestroyedServerside
                && HudClientEvents.isHudInteractive() && HudClientEvents.hudSelectedPlacement == host
                && mc.player.getGameProfile().getName().equals(host.ownerName) && PlayerClientEvents.getRTSPlayer(host.ownerName) != null
                && BuildingClientEvents.getSelectedBuildings().contains(host) && BuildingClientEvents.getBuildings().contains(host)
                && ResourceBuildingResearch.supports(host);
    }

    private static void context() {
        var mc = Minecraft.getInstance();
        connection = mc.getConnection() == null ? null : mc.getConnection().getConnection();
        if (CLOCK.context(mc.level, connection)) { panel = null; queried = Long.MIN_VALUE; }
        if (panel != null && (!ownedPanel(panel.host) || !panel.owner.equals(panel.host.ownerName)
                || !panel.instance.equals(ResourceBuildingResearch.instance(panel.host))
                || panel.snapshot != null && ExtendedResourcesClient.ready()
                    && !panel.snapshot.epoch().equals(ExtendedResourcesClient.epoch()))) panel = null;
    }

    private static ResourceResearchPackets.Snapshot current(Panel expected) {
        if (expected == null || expected != panel || expected.snapshot == null || !CLOCK.fresh(expected.received, FRESH_TICKS))
            return null;
        var s = expected.snapshot;
        return ExtendedResourcesClient.ready() && s.epoch().equals(ExtendedResourcesClient.epoch())
                && EconomyActionClient.matchesContext(s.session(), s.epoch()) ? s : null;
    }

    private static void query(Panel open) {
        if (open != panel || connection == null || !CLOCK.queryDue(queried, ResourceResearchPackets.QUERY_TICKS)) return;
        queried = CLOCK.now(); // global limiter; changing the selection or rerendering does not bypass it
        var known = current(open);
        var target = new ResourceResearchPackets.Target(open.host.level.dimension().location(), open.host.originPos,
                open.instance, open.nonce, 0, 0);
        ResourceResearchPackets.channel().sendToServer(new ResourceResearchPackets.Request(ResourceResearchPackets.Action.QUERY,
                target, known == null ? ResourceResearchPackets.ZERO : known.session(), known == null ? ResourceResearchPackets.ZERO : known.epoch(), 0));
    }

    public static void accept(ResourceResearchPackets.Snapshot packet, Connection source) {
        context();
        var mc = Minecraft.getInstance();
        if (source == null || connection != source || mc.player == null || !packet.valid()
                || !mc.player.getGameProfile().getName().equals(packet.owner())) return;
        var open = panel;
        var target = packet.target();
        boolean visible = open != null && open.owner.equals(packet.owner()) && target.matchesView(
                open.host.level.dimension().location(), open.host.originPos, open.instance, open.nonce);
        boolean established = EconomyActionClient.matchesContext(packet.session(), packet.epoch());
        if (packet.progress() == null || !visible || ExtendedResourcesClient.ready()
                && !packet.epoch().equals(ExtendedResourcesClient.epoch())) {
            // Replays/late completions can retire their own request after closing the panel. They cannot establish
            // a session, refresh a stale observation, or attach private progress to the newly selected building.
            if (packet.completed() <= 0 || !established) return;
            EconomyActionClient.accept(packet.stream(), packet.order(), packet.session(), packet.epoch(), packet.completed());
            if (visible) feedback(open, packet);
            return;
        }
        boolean newer = EconomyActionClient.accept(packet.stream(), packet.order(), packet.session(), packet.epoch(), packet.completed());
        if (newer || established) feedback(open, packet);
        if (!newer || open != panel) return;
        var old = open.snapshot;
        if (old != null) {
            if (!old.session().equals(packet.session()) || !old.epoch().equals(packet.epoch())
                    || old.target().capture() != target.capture()) {
                panel = null; // Includes A→B→A while away: a new tenure requires a newly opened view.
                return;
            }
            if (target.revision() < old.target().revision()) return;
        }
        open.snapshot = packet;
        open.received = CLOCK.now();
    }

    private static void feedback(Panel open, ResourceResearchPackets.Snapshot packet) {
        var before = open.snapshot;
        if (open.pending == 0 || open.pending != packet.completed() || before == null
                || !before.stream().equals(packet.stream()) || !before.session().equals(packet.session())
                || !before.epoch().equals(packet.epoch()) || before.target().capture() != packet.target().capture()) return;
        open.pending = 0;
        String status = packet.status();
        if ("stale_session".equals(status) || "stale_building".equals(status) || "forbidden".equals(status))
            panel = null;
        String key = switch (status) {
            case "refund_pending", "stale_building" -> KEY + status;
            default -> "research.economy." + status;
        };
        DreamTooltipText.hudMessage(Component.translatable(I18n.exists(key) ? key : "research.economy.failed_closed"));
    }

    private static boolean complete(Panel open, ResourceResearchPackets.Snapshot s) {
        return ResourceBuildingResearch.isUpgraded(open.host) || s != null && s.progress().upgraded();
    }
    private static String pauseReason(Panel open) {
        var owner = PlayerClientEvents.getRTSPlayer(open.owner);
        if (open.dais && !DreamFactionAccess.allows(owner)) return "dreamseekers.error.wrong_faction";
        return DreamPassiveEconomy.factionPauseReason(open.host.getBuilding(), owner);
    }
    private static boolean allowed(Panel open, ResourceResearchPackets.Snapshot seen, ResourceResearchPackets.Action action) {
        var now = current(open);
        if (now == null || seen == null || complete(open, now) || !seen.target().equals(now.target())
                || !now.session().equals(seen.session()) || !now.epoch().equals(seen.epoch())) return false;
        if (action == ResourceResearchPackets.Action.CANCEL) return now.progress().remaining() > 0;
        return action == ResourceResearchPackets.Action.START && now.progress().remaining() == 0 && pauseReason(open) == null;
    }
    private static void act(Panel open, ResourceResearchPackets.Snapshot seen, ResourceResearchPackets.Action action) {
        context();
        if (!allowed(open, seen, action)) return;
        // Bind the click to the exact row the player saw. A stale cancel cannot cancel a restarted job.
        var target = seen.target();
        EconomyActionClient.submit(ResourceResearchPackets.fingerprint(action, target), (session, sequence) -> {
            open.pending = sequence;
            return new ResourceResearchPackets.Request(action, target, session, seen.epoch(), sequence);
        }, ResourceResearchPackets.channel()::sendToServer);
    }

    private static Component text(String key, Object... args) { return Component.translatable(KEY + key, args); }
    private static Component tierText(Panel open, String key, Object... args) {
        return Component.translatable((open.dais ? DAIS_KEY : KEY) + key, args);
    }

    private static void addDaisEffects(List<Component> lines, Panel open, boolean finished) {
        var tuned = ResonanceResearch.unlocked(open.owner, true);
        var enhanced = ResonanceNetwork.pulseProfile(tuned, true);
        if (finished) {
            lines.add(tierText(open, "current", ResourceBuildingResearch.DAIS_UPGRADED_SUPPLY, DreamResonanceAura.UPGRADED_PERCENT));
            lines.add(tierText(open, "pulse_current", enhanced.speedPercent(), enhanced.durationTicks() / 20));
            lines.add(tierText(open, "ward_current", DaisWardEffects.reductionPercent(true)));
        } else {
            var base = ResonanceNetwork.pulseProfile(tuned, false);
            lines.add(tierText(open, "effect", ResourceBuildingResearch.DAIS_BASE_SUPPLY,
                    ResourceBuildingResearch.DAIS_UPGRADED_SUPPLY, DreamResonanceAura.BASE_PERCENT, DreamResonanceAura.UPGRADED_PERCENT));
            lines.add(tierText(open, "pulse", base.speedPercent(), base.durationTicks() / 20,
                    enhanced.speedPercent(), enhanced.durationTicks() / 20));
            lines.add(tierText(open, "ward", DaisWardEffects.reductionPercent(false), DaisWardEffects.reductionPercent(true)));
        }
    }
    private static Component resource(BuildingPlacement host) {
        var b = host.getBuilding();
        if (b instanceof ReverieGarden) return Component.translatable("resources.reignofnether.food");
        if (b instanceof DreamScaffoldBuilding scaffold && scaffold.spec() == DreamBuildingSpec.SHADOW_FURNACE)
            return Component.translatable("resource.dreamseekers.shadow_energy");
        return Component.translatable("resources.reignofnether.ore");
    }
    private static int percent(int remaining) {
        return (ResourceBuildingResearch.DURATION - remaining) * 100 / ResourceBuildingResearch.DURATION;
    }
    private static List<FormattedCharSequence> tooltip(Panel open, boolean cancel) {
        if (open != panel) return List.of();
        var s = current(open);
        var lines = new ArrayList<Component>();
        boolean finished = complete(open, s);
        lines.add(tierText(open, cancel ? "cancel" : "title"));
        if (!cancel) {
            if (open.dais) addDaisEffects(lines, open, finished);
            else lines.add(text("effect", resource(open.host)));
        }
        if (finished) {
            lines.add(tierText(open, "completed"));
        } else {
            lines.add(text("cost", ResourceBuildingResearch.WOOD, ResourceBuildingResearch.ORE));
            if (!cancel) lines.add(tierText(open, "duration", ResourceBuildingResearch.DURATION / 20, ResourceBuildingResearch.DURATION));
            var reason = pauseReason(open);
            if (reason != null) lines.add(Component.translatable(reason));
            if (s != null && s.progress().remaining() > 0) {
                int remaining = s.progress().remaining();
                // Server-counted active ticks only. Never count down locally during a pause or predict completion.
                lines.add(text("progress", percent(remaining), (remaining + 19) / 20));
                if (cancel) lines.add(Component.translatable("research.economy.refund_notice"));
            } else if (reason == null) {
                if (s == null) lines.add(text("waiting"));
                else {
                    var r = ResourcesClientEvents.getOwnResources();
                    if (r != null && (r.wood < ResourceBuildingResearch.WOOD || r.ore < ResourceBuildingResearch.ORE))
                        lines.add(Component.translatable("research.economy.insufficient_resources"));
                    else lines.add(text("start"));
                }
            }
        }
        return DreamTooltipText.wrap(lines);
    }

    private static AbilityButton button(Panel open, boolean cancel, ResourceResearchPackets.Snapshot seen) {
        var action = cancel ? ResourceResearchPackets.Action.CANCEL : ResourceResearchPackets.Action.START;
        var b = new AbilityButton(tierText(open, cancel ? "cancel" : "title").getString(), cancel ? CANCEL_ICON : RESEARCH_ICON,
                null, () -> false, () -> false, () -> allowed(open, seen, action), () -> act(open, seen, action), null,
                List.of(), null, open.host) {
            @Override public void renderTooltip(GuiGraphics gui, int x, int y) {
                tooltipLines = tooltip(open, cancel);
                super.renderTooltip(gui, x, y);
            }
        };
        if (!cancel) {
            b.bottomLeftText = () -> {
                if (open != panel) return "";
                var s = current(open);
                if (complete(open, s)) return "✓";
                if (s == null) return "?";
                return s.progress().remaining() > 0 ? percent(s.progress().remaining()) + "%" : "";
            };
        }
        return b;
    }

    public static List<AbilityButton> append(BuildingPlacement host, List<AbilityButton> original) {
        context();
        if (connection == null || !ownedPanel(host)) return original;
        UUID id = ResourceBuildingResearch.instance(host);
        if (id == null || ResourceResearchPackets.ZERO.equals(id)) return original; // wait for the native PLACE binding
        if (panel == null) panel = new Panel(host, id);
        var open = panel;
        if (!ResourceBuildingResearch.isUpgraded(host)) query(open);
        var s = current(open);
        var result = new ArrayList<>(original);
        result.add(button(open, false, s));
        if (!complete(open, s) && s != null && s.progress().remaining() > 0) result.add(button(open, true, s));
        return result;
    }

    private static void clear() { panel = null; queried = Long.MIN_VALUE; connection = null; CLOCK.clear(); }
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event) { clear(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            context();
            if (CLOCK.endTick(Minecraft.getInstance().isPaused())) { panel = null; queried = Long.MIN_VALUE; }
        }
    }
}
