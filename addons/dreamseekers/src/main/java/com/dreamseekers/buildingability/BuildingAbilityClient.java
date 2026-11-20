// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.buildingability;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.ecologyworkposts.EcologyClient;
import com.solegendary.reignofnether.api.BuildingUnitOrderHooks;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.client.ActiveClientTickClock;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.research.economy.EconomyActionClient;
import com.solegendary.reignofnether.research.economy.EconomyActionGate;
import com.solegendary.reignofnether.resources.ExtendedResourcesClient;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Owner-private, relative-time views. A UI view is never server effect or care authority. */
@Mod.EventBusSubscriber(modid = DreamseekersMod.MOD_ID, value = Dist.CLIENT)
public final class BuildingAbilityClient {
    private static final ActiveClientTickClock CLOCK = new ActiveClientTickClock();
    private static final Map<BuildingPlacement, View> VIEWS = new WeakHashMap<>();
    private static final int QUERY_PERIOD = 20, SNAPSHOT_TTL = 60, LAST_CODEX_PAGE = 7;
    private static Level level;
    private static Connection connection;
    // One limiter for all building views. Rendering or changing selection cannot create a query flood.
    private static long lastQuery = Long.MIN_VALUE;

    private static final class View {
        final String owner;
        UUID nonce = UUID.randomUUID();
        int page;
        long received;
        BuildingAbilityPackets.Snapshot snapshot;
        View(String owner) { this.owner = owner; }
    }

    private BuildingAbilityClient() { }

    @Mod.EventBusSubscriber(modid = DreamseekersMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void setup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
            event.enqueueWork(() -> BuildingUnitOrderHooks.registerButtons(BuildingAbilityClient::append));
        }
    }

    private static void clearViews() { VIEWS.clear(); lastQuery = Long.MIN_VALUE; }

    public static void clear() {
        level = null;
        connection = null;
        CLOCK.clear();
        clearViews();
    }

    private static boolean owns(BuildingPlacement placement) {
        Minecraft mc = Minecraft.getInstance();
        if (placement == null || mc.player == null || mc.level == null || placement.getLevel() != mc.level
                || !placement.isBuilt || placement.isDestroyedServerside || placement.shouldBeDestroyed()
                || placement.getHealth() <= 0 || !placement.ownerName.equals(mc.player.getGameProfile().getName())
                || PlayerClientEvents.getRTSPlayer(placement.ownerName) == null
                || !BuildingClientEvents.getBuildings().contains(placement)) return false;
        BuildingAbility ability = BuildingAbility.forBuilding(placement);
        return ability != null && ability.implemented();
    }

    private static boolean retained(BuildingPlacement placement) {
        var screen = Minecraft.getInstance().screen;
        return BuildingClientEvents.getSelectedBuildings().contains(placement)
                || screen instanceof EcologyCodexScreen codex && codex.ownsView(placement);
    }

    private static void context() {
        Minecraft mc = Minecraft.getInstance();
        Connection next = mc.getConnection() == null ? null : mc.getConnection().getConnection();
        if (CLOCK.context(mc.level, next)) clearViews();
        level = mc.level;
        connection = next;
        VIEWS.entrySet().removeIf(entry -> !owns(entry.getKey()) || !retained(entry.getKey())
                || !entry.getValue().owner.equals(entry.getKey().ownerName)
                || entry.getValue().snapshot != null && ExtendedResourcesClient.ready()
                && !ExtendedResourcesClient.epoch().equals(entry.getValue().snapshot.epoch()));
    }

    private static View view(BuildingPlacement placement) {
        return VIEWS.computeIfAbsent(placement, key -> new View(key.ownerName));
    }

    static boolean validHost(BuildingPlacement placement, UUID nonce) {
        context();
        View view = VIEWS.get(placement);
        return owns(placement) && view != null && view.nonce.equals(nonce);
    }

    static UUID viewNonce(BuildingPlacement placement) {
        context();
        return owns(placement) ? view(placement).nonce : null;
    }

    static void releaseView(BuildingPlacement placement, UUID nonce) {
        View view = VIEWS.get(placement);
        if (view != null && view.nonce.equals(nonce)) VIEWS.remove(placement);
    }

    static void query(BuildingPlacement placement) {
        context();
        if (!owns(placement) || connection == null || !CLOCK.queryDue(lastQuery, QUERY_PERIOD)) return;
        View view = view(placement);
        lastQuery = CLOCK.now();
        BuildingAbility ability = BuildingAbility.forBuilding(placement);
        var action = ability == BuildingAbility.ECOLOGY_CODEX
                ? BuildingAbilityPackets.Action.CODEX_QUERY : BuildingAbilityPackets.Action.QUERY;
        var payload = new BuildingAbilityPackets.Payload(action, level.dimension().location(), placement.originPos,
                view.nonce, BuildingAbilityPackets.ZERO, 0, 0, 0, view.page);
        var known = view.snapshot;
        boolean currentAccount = known != null && ExtendedResourcesClient.ready()
                && ExtendedResourcesClient.epoch().equals(known.epoch());
        BuildingAbilityPackets.CHANNEL.sendToServer(new BuildingAbilityPackets.Request(payload,
                currentAccount ? known.epoch() : BuildingAbilityPackets.ZERO,
                currentAccount ? known.session() : BuildingAbilityPackets.ZERO, 0));
    }

    static BuildingAbilityPackets.Snapshot current(BuildingPlacement placement, UUID requiredNonce) {
        context();
        View view = VIEWS.get(placement);
        if (!owns(placement) || view == null || requiredNonce != null && !view.nonce.equals(requiredNonce)
                || view.snapshot == null || !CLOCK.fresh(view.received, SNAPSHOT_TTL)
                || !ExtendedResourcesClient.ready() || !ExtendedResourcesClient.epoch().equals(view.snapshot.epoch())) return null;
        return view.snapshot;
    }

    private static long cooldown(BuildingPlacement placement) {
        var state = current(placement, null);
        View view = VIEWS.get(placement);
        return state == null || view == null ? 0 : CLOCK.remaining(view.received, state.cooldown());
    }

    public static void accept(BuildingAbilityPackets.Snapshot packet, Connection source) {
        context();
        Minecraft mc = Minecraft.getInstance();
        if (connection == null || source != connection || mc.player == null || !packet.valid()
                || packet.request() < 0 || !mc.player.getGameProfile().getName().equals(packet.owner())) return;
        // Retire shared action completions even if the local view has since closed or changed page.
        if (!EconomyActionClient.accept(packet.stream(), packet.order(), packet.session(), packet.epoch(), packet.request())) return;
        if (level == null || !level.dimension().location().equals(packet.dimension())) return;
        BuildingPlacement host = VIEWS.keySet().stream().filter(placement -> owns(placement)
                && placement.originPos.equals(packet.origin()) && VIEWS.get(placement).nonce.equals(packet.view()))
                .findFirst().orElse(null);
        if (host == null || packet.ability() != BuildingAbility.forBuilding(host)) return;
        View view = VIEWS.get(host);
        if (packet.ability() == BuildingAbility.ECOLOGY_CODEX && packet.codex().page() != view.page) return;
        var previous = view.snapshot;
        if (previous != null && (!previous.epoch().equals(packet.epoch())
                || !previous.instance().equals(packet.instance()) || previous.capture() != packet.capture()
                || previous.generation() != packet.generation())) {
            // A same-position capture/replacement is a new view, not a refresh of the old roster.
            // Revision alone is deliberately omitted: a valid cast advances it and must show its spent cooldown.
            view.nonce = UUID.randomUUID();
            view.snapshot = null;
            view.page = 0;
            return;
        }
        view.snapshot = packet;
        view.received = CLOCK.now();
        if (!"ready".equals(packet.status()))
            mc.player.displayClientMessage(statusMessage(packet.status()), true);
    }

    /** A completion may retire its own pending request, but cannot touch any building/page snapshot.
     * The shared gate already rejects old stream/order/context before changing sequence authority.
     */
    public static void acceptAck(BuildingAbilityPackets.Ack packet,Connection source) {
        context();
        Minecraft mc=Minecraft.getInstance();
        if(connection==null||source!=connection||mc.player==null||!packet.valid()
                ||!mc.player.getGameProfile().getName().equals(packet.owner()))return;
        if(EconomyActionClient.accept(packet.stream(),packet.order(),packet.session(),packet.epoch(),packet.request()))
            mc.player.displayClientMessage(statusMessage(packet.status()),true);
    }

    private static Component statusMessage(String status) {
        String key="building_ability.status."+status;
        return Component.translatable(net.minecraft.client.resources.language.I18n.exists(key)
                ?key:"building_ability.status.failed_closed");
    }

    private static boolean ready(BuildingPlacement placement) {
        var state = current(placement, null);
        return state != null && state.supported() && state.available() && cooldown(placement) == 0
                && EconomyActionClient.ready(state.epoch());
    }

    private static void cast(BuildingPlacement placement) {
        var state = current(placement, null);
        if (!ready(placement) || state == null || state.ability() == BuildingAbility.ECOLOGY_CODEX) {
            query(placement);
            return;
        }
        var payload = new BuildingAbilityPackets.Payload(BuildingAbilityPackets.Action.CAST,
                state.dimension(), state.origin(), state.view(), state.instance(), state.capture(),
                state.generation(), state.revision(), 0);
        if (state.ability() == BuildingAbility.WATCHTOWER_MARK) TowerMarkClient.expect(placement, state);
        EconomyActionClient.submit(new EconomyActionGate.Fingerprint("building_ability", payload),
                (session, sequence) -> new BuildingAbilityPackets.Request(payload, state.epoch(), session, sequence),
                BuildingAbilityPackets.CHANNEL::sendToServer);
    }

    private static void openCodex(BuildingPlacement court) {
        Minecraft mc = Minecraft.getInstance();
        var state = current(court, null);
        if (state == null || !state.supported() || !state.available() || !(mc.screen instanceof TopdownGui)
                || !OrthoviewClientEvents.isEnabled()) { query(court); return; }
        UUID nonce = viewNonce(court);
        if (nonce != null) {
            CursorClientEvents.setLeftClickAction(null);
            mc.setScreen(new EcologyCodexScreen(court, nonce, mc.screen));
        }
    }

    static UUID changeCodexPage(BuildingPlacement court, UUID expected, int nextPage) {
        if (!validHost(court, expected) || nextPage < 0 || nextPage > LAST_CODEX_PAGE) return null;
        View view = VIEWS.get(court);
        view.page = nextPage;
        // A late response to the previous page cannot update the next page or authorize one of its rows.
        view.nonce = UUID.randomUUID();
        view.snapshot = null;
        query(court);
        return view.nonce;
    }

    /** Resolve only the current server-listed row AND the exact already-present own client entity. */
    static LivingEntity resolveRow(BuildingPlacement court, UUID nonce, BuildingCodex.Row row) {
        var snapshot = current(court, nonce);
        if (snapshot == null || !snapshot.supported() || !snapshot.available() || row == null
                || !BuildingClientEvents.getSelectedBuildings().contains(court)
                || snapshot.codex().rows().stream().noneMatch(current -> current.equals(row))
                || level == null || !OrthoviewClientEvents.isEnabled()) return null;
        var found = level.getEntity(row.id());
        if (!(found instanceof LivingEntity entity) || !(entity instanceof Unit unit)
                || entity instanceof HeroUnit || !entity.getUUID().equals(row.uuid())
                || !row.typeId().equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))
                || !entity.isAlive() || entity.isRemoved() || entity.isSpectator() || entity.isPassenger()
                || entity.level() != level || !unit.getOwnerName().equals(court.ownerName)
                || !UnitClientEvents.getAllUnits().contains(entity) || entity.isInvisible()
                || !FogOfWarClientEvents.isInBrightChunk(entity)) return null;
        return entity;
    }

    static boolean select(BuildingPlacement court, UUID nonce, BuildingCodex.Row row) {
        LivingEntity entity = resolveRow(court, nonce, row);
        if (entity == null) return false;
        UnitClientEvents.clearSelectedUnits();
        UnitClientEvents.addSelectedUnit(entity);
        return UnitClientEvents.getSelectedUnits().contains(entity);
    }

    static boolean focus(BuildingPlacement court, UUID nonce, BuildingCodex.Row row) {
        LivingEntity entity = resolveRow(court, nonce, row);
        if (entity == null || OrthoviewClientEvents.isCameraLocked()) return false;
        // The existing RTS-camera action only; no entity position or new teleport request is accepted.
        OrthoviewClientEvents.centreCameraOnPos(entity.position());
        return true;
    }

    static boolean care(BuildingPlacement court, UUID nonce, BuildingCodex.Row row) {
        LivingEntity entity = resolveRow(court, nonce, row);
        if (entity == null) return false;
        var care = EcologyClient.codexView(court);
        if (care == null || !EconomyActionClient.ready(care.epoch())) return false;
        if (row.careState() == 1 || row.careState() == 2)
            return EcologyClient.releaseOwn(court, entity.getUUID());
        if (row.careState() != 0 || !row.careEligible()) return false;
        return EcologyClient.tryAssign(court, List.of(entity));
    }

    private static ResourceLocation icon(BuildingAbility ability) {
        String path = switch (ability) {
            case ROOT_CROWN -> "shi_jie_shu_chong_jia_";
            case GARDEN_RECOVERY -> "ran_meng_hua_cha_";
            case ECOLOGY_CODEX -> "xun_meng_zhe_bi_ji_";
            case HALL_PREPARATION -> "dream_meter";
            case DAIS_WARD -> "rong_meng_shui_jing_sui_pian_";
            case WATCHTOWER_MARK -> "shadow_hand_lantern";
        };
        return ResourceLocation.fromNamespaceAndPath("pasterdream", "textures/item/" + path + ".png");
    }

    static String titleKey(BuildingAbility ability) { return "building_ability." + ability.path(); }

    private static void addTooltip(List<FormattedCharSequence> lines, Component text) {
        var mc = Minecraft.getInstance();
        int width = Math.max(120, Math.min(310, mc.getWindow().getGuiScaledWidth() - 30));
        lines.addAll(mc.font.split(text, width));
    }

    private static List<FormattedCharSequence> tooltip(BuildingPlacement placement) {
        var ability = BuildingAbility.forBuilding(placement);
        var result = new ArrayList<FormattedCharSequence>();
        addTooltip(result, Component.translatable(titleKey(ability)));
        addTooltip(result, Component.translatable(titleKey(ability) + ".rules"));
        addTooltip(result, Component.translatable(titleKey(ability) + ".prerequisite"));
        if (ability != BuildingAbility.ECOLOGY_CODEX) {
            addTooltip(result, Component.translatable("building_ability.quote", ability.foodCost(), ability.shadowCost(),
                    ability.cooldownTicks() / 20, ability.durationTicks() / 20, ability.range(), ability.maxTargets()));
            long cooling = cooldown(placement);
            if (cooling > 0) addTooltip(result, Component.translatable("building_ability.cooldown", (cooling + 19) / 20));
            addTooltip(result, Component.translatable("building_ability.paid_rules"));
        }
        var state = current(placement, null);
        if (state == null) addTooltip(result, Component.translatable("building_ability.waiting"));
        else if (!state.supported()) addTooltip(result, Component.translatable("building_ability.status.unsupported"));
        else if (!state.available()) addTooltip(result, Component.translatable("building_ability.status." + state.reason()));
        if (state != null && state.activeTicks() > 0) {
            long active = CLOCK.remaining(VIEWS.get(placement).received, state.activeTicks());
            if (active > 0) addTooltip(result, Component.translatable("building_ability.active_remaining", (active + 19) / 20));
        }
        return result;
    }

    public static List<AbilityButton> append(BuildingPlacement placement, List<AbilityButton> original) {
        context();
        if (!owns(placement)) return original;
        var ability = BuildingAbility.forBuilding(placement);
        query(placement);
        var out = new ArrayList<>(original);
        var button = new AbilityButton(Component.translatable(titleKey(ability)).getString(), icon(ability), null,
                () -> false, () -> false, () -> ready(placement),
                () -> { if (ability == BuildingAbility.ECOLOGY_CODEX) openCodex(placement); else cast(placement); },
                null, tooltip(placement), null, placement) {
            @Override public void renderTooltip(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY) {
                tooltipLines = tooltip(placement);
                super.renderTooltip(graphics, mouseX, mouseY);
            }
        };
        button.bottomLeftText = () -> {
            var state = current(placement, null);
            if (state == null) return "?";
            if (ability == BuildingAbility.ECOLOGY_CODEX) return Integer.toString(state.codex().totalRows());
            long remaining = cooldown(placement);
            return remaining > 0 ? com.dreamseekers.client.DreamTooltipLayout.cooldownBadge(remaining)
                    : Integer.toString(ability.foodCost() > 0 ? ability.foodCost() : ability.shadowCost());
        };
        out.add(button);
        return out;
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        context();
        if (CLOCK.endTick(Minecraft.getInstance().isPaused())) clearViews();
        if (level == null) return;
        if (Minecraft.getInstance().screen instanceof EcologyCodexScreen screen) query(screen.host());
        else for (var placement : BuildingClientEvents.getSelectedBuildings()) {
            if (owns(placement)) { query(placement); break; }
        }
    }

    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event) { clear(); }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }
}
