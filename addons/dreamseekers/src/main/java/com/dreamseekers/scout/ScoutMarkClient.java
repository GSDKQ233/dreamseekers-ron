// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.scout;
import com.solegendary.reignofnether.client.ActiveClientTickClock;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.unit.PinkChickenScout;
import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/** Private cache contains IDs only: no position snapshots, extra vision, global glow or world mutation. */
@Mod.EventBusSubscriber(modid = DreamseekersMod.MOD_ID, value = Dist.CLIENT)
public final class ScoutMarkClient {
    private static final ActiveClientTickClock CLOCK=new ActiveClientTickClock();
    private static final Minecraft MC = Minecraft.getInstance();
    private static final Map<UUID, View> VIEWS = new HashMap<>();
    @Nullable private static UUID armed;
    private static int swallowRelease = -1;
    private ScoutMarkClient() { }

    private static void clearView() { VIEWS.clear(); cancel(); swallowRelease = -1; }
    private static void context() {
        var connection = MC.getConnection() == null ? null : MC.getConnection().getConnection();
        if (CLOCK.context(MC.level, connection)) clearView();
    }
    @SubscribeEvent public static void login(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn e) { CLOCK.clear(); clearView(); }
    @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) { CLOCK.clear(); clearView(); }

    private record View(int scoutId, UUID scoutUuid, long revision, long cooldownTicks,
                        int targetId, @Nullable UUID targetUuid, long remainingTicks, long receivedAt) {
        View withoutTarget() { return new View(scoutId, scoutUuid, revision, cooldownTicks, -1, null, 0, receivedAt); }
    }
    public static void accept(ScoutMarkNetwork.Sync sync) {
        context();
        if (MC.level == null || MC.player == null) return;
        if (sync.revision() < 0) { VIEWS.remove(sync.scoutUuid()); return; }
        Entity entity = MC.level.getEntity(sync.scoutId());
        if (!(entity instanceof PinkChickenScout scout) || !scout.getUUID().equals(sync.scoutUuid()) || !owned(scout)) return;
        View old = VIEWS.get(sync.scoutUuid());
        if (old != null && sync.revision() < old.revision) return;
        long now = CLOCK.now();
        VIEWS.put(sync.scoutUuid(), new View(sync.scoutId(), sync.scoutUuid(), sync.revision(),
                Math.max(0, Math.min(ScoutBalance.MARK_COOLDOWN_TICKS, sync.cooldownTicks())), sync.targetId(), sync.targetUuid(),
                Math.max(0, Math.min(ScoutBalance.MARK_DURATION_TICKS, sync.remainingTicks())), now));
    }
    private static boolean owned(PinkChickenScout scout) {
        return MC.player != null && scout.level() == MC.level && scout.isAlive() && scout.getOwnerName().equals(MC.player.getGameProfile().getName());
    }
    public static int cooldown(PinkChickenScout scout) {
        context();
        View view = VIEWS.get(scout.getUUID());
        return MC.level == null || view == null ? 0 : (int) CLOCK.remaining(view.receivedAt, view.cooldownTicks);
    }
    public static boolean isArmed(PinkChickenScout scout) { return scout.getUUID().equals(armed); }
    public static void arm(PinkChickenScout scout) {
        context();
        if (!owned(scout) || !(MC.screen instanceof TopdownGui) || !OrthoviewClientEvents.isEnabled()) return;
        if (isArmed(scout)) { cancel(); return; }
        CursorClientEvents.setLeftClickAction(null);
        CursorClientEvents.setLeftClickSandboxAction(null);
        armed = scout.getUUID();
        HudClientEvents.showTemporaryMessage(Component.translatable("ability.dreamseekers.scout_mark.select").getString());
    }
    public static void cancel() { armed = null; }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (armed == null || !(event.getScreen() instanceof TopdownGui)) return;
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            cancel(); swallowRelease = event.getButton(); event.setCanceled(true); return;
        }
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        if (HudClientEvents.isMouseOverAnyButtonOrHud()) {
            // Preserve toggle semantics for this same button; other HUD actions cancel the target mode.
            if (!(HudClientEvents.getMousedOverButton() instanceof AbilityButton button)
                    || button.ability != ScoutMarkAbility.INSTANCE) cancel();
            return;
        }
        PinkChickenScout scout = armedScout();
        event.setCanceled(true);
        swallowRelease = event.getButton();
        cancel();
        if (scout == null || MC.level == null) return;
        LivingEntity target = UnitClientEvents.getPreselectedUnits().stream()
                .filter(e -> e instanceof Unit && e != scout && visibleTarget(scout, e)).findFirst().orElse(null);
        if (target == null) {
            HudClientEvents.showTemporaryMessage(Component.translatable("ability.dreamseekers.scout_mark.result.invalid").getString());
            return;
        }
        View view = VIEWS.get(scout.getUUID());
        // Unknown revision deliberately fails closed; periodic owner-only resync supplies it.
        if (view == null) {
            HudClientEvents.showTemporaryMessage(Component.translatable("ability.dreamseekers.scout_mark.result.replay").getString());
            return;
        }
        ScoutMarkNetwork.request(new ScoutMarkNetwork.Cast(scout.getId(), scout.getUUID(), target.getId(), target.getUUID(), view.revision));
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void release(ScreenEvent.MouseButtonReleased.Pre event) {
        if (event.getButton() == swallowRelease) { swallowRelease = -1; event.setCanceled(true); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void key(ScreenEvent.KeyPressed.Pre event) {
        if (armed == null) return;
        if (event.getKeyCode() == GLFW.GLFW_KEY_ESCAPE) { cancel(); event.setCanceled(true); }
        else if (event.getKeyCode() != GLFW.GLFW_KEY_LEFT_SHIFT && event.getKeyCode() != GLFW.GLFW_KEY_RIGHT_SHIFT
                && event.getKeyCode() != Keybindings.abilitySlot1.getKey()) cancel();
    }
    @Nullable private static PinkChickenScout armedScout() {
        if (armed == null || MC.level == null) return null;
        return UnitClientEvents.getSelectedUnits().stream()
                .filter(e -> e instanceof PinkChickenScout && e.getUUID().equals(armed))
                .map(e -> (PinkChickenScout) e).filter(ScoutMarkClient::owned).findFirst().orElse(null);
    }
    private static boolean visibleTarget(PinkChickenScout scout, Entity target) {
        if (!(target instanceof LivingEntity living) || !(target instanceof Unit unit) || !living.isAlive()
                || target.isRemoved() || target.isInvisible() || target.isSpectator() || !owned(scout)) return false;
        String owner = scout.getOwnerName();
        return !unit.getOwnerName().isBlank() && !owner.equals(unit.getOwnerName())
                && !AlliancesClient.isAllied(owner, unit.getOwnerName())
                && scout.distanceToSqr(target) <= (double) ScoutBalance.MARK_RANGE * ScoutBalance.MARK_RANGE
                && scout.distanceToSqr(target) <= (double) scout.getSightRange() * scout.getSightRange()
                && scout.hasLineOfSight(living) && FogOfWarClientEvents.isInBrightChunk(target);
    }
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        context();
        if (CLOCK.endTick(MC.isPaused())) clearView();
        if (MC.level == null || MC.player == null) { clearView(); return; }
        if (!(MC.screen instanceof TopdownGui) || !OrthoviewClientEvents.isEnabled()) {
            cancel(); swallowRelease = -1;
        } else if (armedScout() == null || CursorClientEvents.getLeftClickAction() != null) cancel();
        VIEWS.entrySet().removeIf(entry -> {
            View view = entry.getValue(); Entity e = MC.level.getEntity(view.scoutId);
            return !(e instanceof PinkChickenScout scout) || !e.getUUID().equals(view.scoutUuid) || !owned(scout)
                    || !CLOCK.fresh(view.receivedAt, 40);
        });
        VIEWS.replaceAll((uuid, view) -> {
            if (view.targetUuid == null) return view;
            Entity caster = MC.level.getEntity(view.scoutId), target = MC.level.getEntity(view.targetId);
            if (CLOCK.remaining(view.receivedAt, view.remainingTicks) == 0 || !(caster instanceof PinkChickenScout scout) || target == null
                    || !target.getUUID().equals(view.targetUuid) || !visibleTarget(scout, target)) return view.withoutTarget();
            return view;
        });
    }
    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || MC.level == null
                || MC.player == null || !OrthoviewClientEvents.isEnabled()) return;
        context();
        var camera = event.getCamera().getPosition();
        var pose = event.getPoseStack();
        var buffer = MC.renderBuffers().bufferSource().getBuffer(RenderType.lines()); // normal depth test: no wall outline
        var renderedTargets = new HashSet<UUID>();
        for (View view : VIEWS.values()) {
            if (view.targetUuid == null || CLOCK.remaining(view.receivedAt, view.remainingTicks) == 0) continue;
            Entity caster = MC.level.getEntity(view.scoutId), target = MC.level.getEntity(view.targetId);
            if (!(caster instanceof PinkChickenScout scout) || target == null || !target.getUUID().equals(view.targetUuid)
                    || !visibleTarget(scout, target)) continue;
            if (!renderedTargets.add(target.getUUID())) continue; // concurrent scouts share one visual marker
            AABB box = target.getBoundingBox().inflate(.10);
            pose.pushPose(); pose.translate(-camera.x, -camera.y, -camera.z);
            LevelRenderer.renderLineBox(pose, buffer, box, 1F, .35F, .68F, .95F);
            double x = (box.minX + box.maxX) / 2, z = (box.minZ + box.maxZ) / 2;
            LevelRenderer.renderLineBox(pose, buffer, new AABB(x-.16, box.maxY+.12, z-.16, x+.16, box.maxY+.42, z+.16), 1F, .8F, .25F, 1F);
            pose.popPose();
        }
    }
}
