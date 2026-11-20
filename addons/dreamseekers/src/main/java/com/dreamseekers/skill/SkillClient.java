// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;
import com.solegendary.reignofnether.client.ActiveClientTickClock;

import com.dreamseekers.DreamseekersMod;
import com.solegendary.reignofnether.alliance.AlliancesClient;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.guiscreen.TopdownGui;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Local input/private cooldowns. Visible cleanse packets update presentation, never server effects. */
@Mod.EventBusSubscriber(modid = DreamseekersMod.MOD_ID, value = Dist.CLIENT)
public final class SkillClient {
    private static final ActiveClientTickClock CLOCK=new ActiveClientTickClock();
    private static final Minecraft MC = Minecraft.getInstance();
    private record Key(UUID source,UnitSkill skill) { }
    private static final Map<Key,View> VIEWS = new HashMap<>();
    private static final Map<UUID,Long> REVISIONS = new HashMap<>();
    @Nullable private static ClientLevel cachedLevel;
    @Nullable private static UUID armedSource;
    @Nullable private static UnitSkill armedSkill;
    private static int swallowRelease = -1;
    private static int swallowKeyRelease = -1;

    private SkillClient() { }

    private static void clearView() {
        VIEWS.clear(); REVISIONS.clear(); cachedLevel = MC.level;
        cancel(); swallowRelease = -1; swallowKeyRelease = -1;
    }
    private static void context() {
        var connection = MC.getConnection() == null ? null : MC.getConnection().getConnection();
        if (CLOCK.context(MC.level, connection)) clearView();
    }
    @SubscribeEvent public static void login(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn e) { CLOCK.clear(); clearView(); }
    @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) { CLOCK.clear(); clearView(); }

    private record View(UnitSkill skill, int sourceId, UUID sourceUuid, long revision,
                        long cooldownTicks, long receivedAt, boolean autocastEnabled, int amplificationTier, boolean lightExtension, boolean motherNestActive, boolean handHarmonyActive) { }

    public static void accept(SkillNetwork.Sync sync) {
        context();
        if (MC.level == null || MC.player == null) return;
        if (sync.revision() < 0) {
            VIEWS.keySet().removeIf(key->key.source().equals(sync.sourceUuid())); REVISIONS.remove(sync.sourceUuid());
            if (sync.sourceUuid().equals(armedSource)) cancel();
            return;
        }
        Entity entity = MC.level.getEntity(sync.sourceId());
        if (!(entity instanceof SkillUnit source) || !SkillService.supports(source,sync.skill())
                || !source.mob().getUUID().equals(sync.sourceUuid()) || !owned(source)) return;
        if(sync.revision()<REVISIONS.getOrDefault(sync.sourceUuid(),-1L))return;
        REVISIONS.put(sync.sourceUuid(),sync.revision());
        long now = CLOCK.now();
        VIEWS.put(new Key(sync.sourceUuid(),sync.skill()), new View(sync.skill(), sync.sourceId(), sync.sourceUuid(), sync.revision(),
                Math.max(0, Math.min(sync.skill().cooldownTicks(), sync.cooldownTicks())), now,
                sync.skill().supportsAutocast()&&sync.autocastEnabled(),sync.amplificationTier(),sync.lightExtension(),sync.motherNestActive(),sync.handHarmonyActive()));
    }

    public static void acceptRemoval(SkillNetwork.EffectRemoved packet) {
        if(MC.level==null)return;
        Entity entity=MC.level.getEntity(packet.targetId());
        if(!(entity instanceof LivingEntity target)||!(entity instanceof Unit)||!entity.getUUID().equals(packet.targetUuid()))return;
        var effect=net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(packet.effect());
        if(effect!=null)target.removeEffect(effect);
    }

    private static boolean owned(SkillUnit source) {
        return MC.player != null && source.mob().level() == MC.level && source.mob().isAlive() && !source.mob().isRemoved()
                && source.getOwnerName().equals(MC.player.getGameProfile().getName());
    }

    public static SkillReadiness readiness(SkillUnit source, UnitSkill skill) {
        View view = currentView(source, skill);
        return view == null ? SkillReadiness.WAITING : SkillReadiness.at(CLOCK,view.receivedAt(),view.cooldownTicks());
    }
    public static int cooldown(SkillUnit source, UnitSkill skill) { return readiness(source,skill).nativeCooldown(skill.cooldownTicks()); }

    /** No local building/research guess: only a fresh exact-owner server snapshot can display a boost. */
    public static int amplificationTier(SkillUnit source,UnitSkill skill) {
        View view=currentView(source,skill);return view==null||!SkillPotencyView.supports(skill)?0:view.amplificationTier();
    }
    public static boolean lightExtension(SkillUnit source,UnitSkill skill){View view=currentView(source,skill);return view!=null&&view.lightExtension();}
    public static boolean motherNestActive(SkillUnit source){View view=currentView(source,UnitSkill.MOTHER_NEST);return view!=null&&view.motherNestActive();}
    public static boolean handHarmonyActive(SkillUnit source,UnitSkill skill){View view=currentView(source,skill);return view!=null&&view.handHarmonyActive();}
    public static boolean hasCurrentState(SkillUnit source,UnitSkill skill){return currentView(source,skill)!=null;}

    public static boolean isArmed(SkillUnit source, UnitSkill skill) {
        return source.mob().getUUID().equals(armedSource) && skill == armedSkill;
    }

    public static void arm(SkillUnit source, UnitSkill skill) {
        if (!SkillService.supports(source,skill) || !owned(source)
                || !(MC.screen instanceof TopdownGui) || !OrthoviewClientEvents.isEnabled()) return;
        if (isArmed(source, skill)) { cancel(); return; }
        if (currentView(source, skill) == null) {
            message("ability.dreamseekers.unit_skill.awaiting_state");
            return;
        }
        if (cooldown(source, skill) > 0) {
            message("ability.dreamseekers.unit_skill.result.cooldown");
            return;
        }
        CursorClientEvents.setLeftClickAction(null);
        CursorClientEvents.setLeftClickSandboxAction(null);
        if(skill==UnitSkill.MOTHER_NEST||skill==UnitSkill.AARONCOS_RIGHT_SHOCK||skill==UnitSkill.MOTHER_BROOD||skill==UnitSkill.WEEPING_ECHOES||skill==UnitSkill.FOX_BLESSING||skill==UnitSkill.FIREFLY_RUSH||skill==UnitSkill.BEETLE_ADVANCE||skill==UnitSkill.SNAIL_SHELL_FORMATION||skill==UnitSkill.SHADOW_CORROSION_TOUCH||skill==UnitSkill.TERRORBEAK_DREAD_ROAR) {
            View view=currentView(source,skill);
            if(view==null)return;
            cancel();
            SkillNetwork.request(new SkillNetwork.Cast(skill,source.mob().getId(),source.mob().getUUID(),view.revision(),
                    source.mob().getId(),source.mob().getUUID()));
            return;
        }
        armedSource = source.mob().getUUID();
        armedSkill = skill;
        message("ability.dreamseekers."+skill.id()+".select");
    }

    public static boolean autocastEnabled(SkillUnit source,UnitSkill skill) {
        View view=currentView(source,skill);return view!=null&&view.autocastEnabled();
    }
    public static void rightClick(SkillUnit source,UnitSkill skill) {
        if(isArmed(source,skill)) { cancel();return; }
        if(!skill.supportsAutocast()) { cancel();return; }
        if(!owned(source)||!(MC.screen instanceof TopdownGui)||!OrthoviewClientEvents.isEnabled())return;
        View view=currentView(source,skill);
        if(view==null) { message("ability.dreamseekers.unit_skill.awaiting_state");return; }
        cancel();
        SkillNetwork.request(new SkillNetwork.Toggle(skill,source.mob().getId(),source.mob().getUUID(),view.revision(),!view.autocastEnabled()));
    }
    public static void cancel() { armedSource = null; armedSkill = null; }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void mouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (armedSource == null || !(event.getScreen() instanceof TopdownGui)) return;
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            cancel(); swallowRelease = event.getButton(); event.setCanceled(true); return;
        }
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return;
        CursorClientEvents.preparePress(event);
        if (HudClientEvents.isMouseOverAnyButtonOrHud()) {
            if (!(HudClientEvents.getMousedOverButton() instanceof AbilityButton button)
                    || !(button.ability instanceof UnitSkillAbility ability) || ability.skill() != armedSkill) cancel();
            return;
        }
        SkillUnit source = armedCaster();
        UnitSkill skill = armedSkill;
        View view=source==null||skill==null?null:currentView(source,skill);
        SkillReadiness state=view==null?SkillReadiness.WAITING:SkillReadiness.at(CLOCK,view.receivedAt(),view.cooldownTicks());
        if(!state.ready()) {
            cancel(); // Give the press AND its release back to ordinary world interaction.
            if(state.waiting())SnapshotSkillButton.waiting();else SnapshotSkillButton.cooling(state);
            return;
        }
        event.setCanceled(true);
        swallowRelease = event.getButton();
        cancel();
        if(skill==UnitSkill.FOX_DREAM_SEED||skill==UnitSkill.ASH_EMBER_LANDING||skill==UnitSkill.AARONCOS_RIGHT_VORTEX) {
            net.minecraft.core.BlockPos ground=CursorClientEvents.getPreselectedBlockPos();
            if(ground==null) { message("ability.dreamseekers.unit_skill.result.ground_invalid");return; }
            // Cursor rays identify a supporting solid block; the authoritative request names the empty feet cell.
            if(!MC.level.getBlockState(ground).getCollisionShape(MC.level,ground).isEmpty())ground=ground.above();
            SkillNetwork.request(new SkillNetwork.GroundCast(skill,source.mob().getId(),source.mob().getUUID(),view.revision(),ground));
            return;
        }
        LivingEntity target = UnitClientEvents.getPreselectedUnits().stream()
                .filter(entity -> eligibleTarget(source, skill, entity)).findFirst().orElse(null);
        if(skill==UnitSkill.SHADOW_GOLEM_STRIKE) {
            var selection=GolemStrikeTargetSelection.<LivingEntity,com.solegendary.reignofnether.building.BuildingPlacement>choose(target,()->{
                if(!(source instanceof com.dreamseekers.unit.ShadowGolemUnit golem))return null;
                var clicked=CursorClientEvents.getPreselectedBlockPos();
                var building=clicked==null?null:com.solegendary.reignofnether.building.BuildingClientEvents.getPreselectedBuilding();
                return GolemStrikeTargetSelection.building(golem,building,clicked,
                        b->com.solegendary.reignofnether.building.BuildingClientEvents.getBuildings().stream().anyMatch(current->current==b),
                        SkillClient::visibleTargetColumn);
            });
            if(selection==null) { message("ability.dreamseekers.unit_skill.result.invalid_target");return; }
            if(selection.unit()!=null) {
                SkillNetwork.request(new SkillNetwork.Cast(skill,source.mob().getId(),source.mob().getUUID(),view.revision(),
                        selection.unit().getId(),selection.unit().getUUID()));
            } else {
                SkillNetwork.request(new SkillNetwork.BuildingCast(skill,source.mob().getId(),source.mob().getUUID(),view.revision(),
                        selection.building().originPos,selection.contact()));
            }
            return;
        }
        if (target == null) {
            message("ability.dreamseekers.unit_skill.result.invalid_target");
            return;
        }
        SkillNetwork.request(new SkillNetwork.Cast(skill, source.mob().getId(), source.mob().getUUID(), view.revision(),
                target.getId(), target.getUUID()));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void release(ScreenEvent.MouseButtonReleased.Pre event) {
        if (event.getButton() == swallowRelease) { swallowRelease = -1; event.setCanceled(true); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void key(ScreenEvent.KeyPressed.Pre event) {
        if (armedSource == null) return;
        if (event.getKeyCode() == GLFW.GLFW_KEY_ESCAPE) {
            cancel(); swallowKeyRelease = event.getKeyCode(); event.setCanceled(true);
        }
        // Every ability slot belongs to a skill button, whose own release toggles or replaces the armed skill.
        else if (event.getKeyCode() != GLFW.GLFW_KEY_LEFT_SHIFT && event.getKeyCode() != GLFW.GLFW_KEY_RIGHT_SHIFT
                && !com.dreamseekers.client.DreamTooltipLayout.keepsTargetMode(event.getKeyCode(),
                    Keybindings.abilitySlot1.getKey(), Keybindings.abilitySlot2.getKey(), Keybindings.abilitySlot3.getKey(),
                    Keybindings.abilitySlot4.getKey(), Keybindings.abilitySlot5.getKey(), Keybindings.abilitySlot6.getKey())) cancel();
    }

    /** RoN dispatches several commands on key release; target-mode Escape must not also dispatch one. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void keyRelease(ScreenEvent.KeyReleased.Pre event) {
        if (event.getKeyCode() == swallowKeyRelease) { swallowKeyRelease = -1; event.setCanceled(true); }
    }

    @Nullable private static SkillUnit armedCaster() {
        if (armedSource == null || MC.level == null) return null;
        return UnitClientEvents.getSelectedUnits().stream()
                .filter(entity -> entity instanceof SkillUnit && entity.getUUID().equals(armedSource))
                .map(entity -> (SkillUnit) entity).filter(SkillClient::owned).findFirst().orElse(null);
    }

    @Nullable private static View currentView(SkillUnit source, UnitSkill skill) {
        context();
        if (MC.level == null || cachedLevel != MC.level || !owned(source)) return null;
        View view = VIEWS.get(new Key(source.mob().getUUID(),skill));
        if (view == null || view.revision()!=REVISIONS.getOrDefault(source.mob().getUUID(),-1L) || view.skill() != skill || view.sourceId() != source.mob().getId()
                || MC.level.getEntity(source.mob().getId()) != source.mob() || !CLOCK.fresh(view.receivedAt(), 40)) return null;
        return view;
    }

    /** Dash and Golem mirror server exact-column fog, not the native entity ranged-reveal exception.
     * Both native fog implementations are enabled only for the viewer's RTS role; their
     * current column caches are Overworld-only, so active fog fails closed elsewhere. */
    private static boolean visibleTargetColumn(net.minecraft.core.BlockPos pos) {
        return MC.level!=null
                &&(!com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents.isEnabled()
                    ||MC.level.dimension()==net.minecraft.world.level.Level.OVERWORLD)
                &&com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents.isInBrightChunk(pos);
    }

    private static boolean eligibleTarget(SkillUnit source, UnitSkill skill, Entity target) {
        if (!(target instanceof LivingEntity living) || !(target instanceof Unit unit) || target == source.mob()
                || !living.isAlive() || target.isRemoved() || target.isSpectator()
                || target.level() != source.mob().level() || !owned(source) || unit.getOwnerName().isBlank()) return false;
        // Golem mirrors its existing server-side structural/identity/LOS gates only.
        if((skill==UnitSkill.SHADOW_GOLEM_STRIKE||skill==UnitSkill.AARONCOS_LEFT_PRESS)&&(target instanceof com.dreamseekers.spore.JungleSporeCarrier
                ||!target.isAddedToWorld()||target.level().getEntity(target.getId())!=target
                ||!target.level().getWorldBorder().isWithinBounds(target.blockPosition())
                ||!source.mob().hasLineOfSight(target)))return false;
        String owner = source.getOwnerName();
        boolean friendly=owner.equals(unit.getOwnerName())||AlliancesClient.isAllied(owner,unit.getOwnerName());
        return ((skill==UnitSkill.WIND_DASH||skill==UnitSkill.COORDINATED_MARK||skill==UnitSkill.STONE_HEAVY_SHOT||skill==UnitSkill.SQUEAL_SUPPRESSION_WAVE||skill==UnitSkill.FRIENDLY_DEBT_CONTRACT||skill==UnitSkill.SHADOW_HAND_GRIP||skill==UnitSkill.SHADOW_GOLEM_STRIKE||skill==UnitSkill.AARONCOS_LEFT_PRESS)?!friendly:friendly)
                && SkillTargetRange.withinRange(skill,
                    (skill==UnitSkill.SHADOW_GOLEM_STRIKE||skill==UnitSkill.AARONCOS_LEFT_PRESS)
                        ?ShadowGolemStrike.distanceSquared(source.mob().position(),living.getBoundingBox()):source.mob().distanceToSqr(target),
                    ()->source instanceof com.dreamseekers.unit.StoneSpiritThrowerUnit stone?StoneHeavyShot.range(stone):skill.range())
                && ((skill!=UnitSkill.WIND_DASH&&skill!=UnitSkill.COORDINATED_MARK&&skill!=UnitSkill.STONE_HEAVY_SHOT&&skill!=UnitSkill.SQUEAL_SUPPRESSION_WAVE&&skill!=UnitSkill.FRIENDLY_DEBT_CONTRACT&&skill!=UnitSkill.SHADOW_HAND_GRIP&&skill!=UnitSkill.SHADOW_GOLEM_STRIKE&&skill!=UnitSkill.AARONCOS_LEFT_PRESS)||!target.isInvisible()
                    &&((skill==UnitSkill.WIND_DASH||skill==UnitSkill.SHADOW_GOLEM_STRIKE||skill==UnitSkill.AARONCOS_LEFT_PRESS)
                        ?visibleTargetColumn(target.blockPosition())
                        :com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents.isInBrightChunk(target)))
                && (skill!=UnitSkill.JELLY_FLOW||living.getHealth()<living.getMaxHealth());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        context();
        if (CLOCK.endTick(MC.isPaused())) clearView();
        if (MC.level == null || MC.player == null) { clearView(); return; }
        if (!(MC.screen instanceof TopdownGui) || !OrthoviewClientEvents.isEnabled()) {
            cancel(); swallowRelease = -1;
        } else if (armedCaster() == null || CursorClientEvents.getLeftClickAction() != null) cancel();
        VIEWS.entrySet().removeIf(entry -> {
            View view = entry.getValue();
            Entity entity = MC.level.getEntity(view.sourceId());
            return !(entity instanceof SkillUnit source) || !source.mob().getUUID().equals(view.sourceUuid())
                    || !owned(source) || !CLOCK.fresh(view.receivedAt(), 40);
        });
        java.util.Set<UUID> liveSources=new java.util.HashSet<>();
        VIEWS.keySet().forEach(key->liveSources.add(key.source()));REVISIONS.keySet().retainAll(liveSources);
    }

    private static void message(String key) { HudClientEvents.showTemporaryMessage(Component.translatable(key).getString()); }
}
