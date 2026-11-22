// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.client.form;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.building.DreamBuildingSpec;
import com.dreamseekers.building.DreamScaffoldBuilding;
import com.dreamseekers.building.ReverieGarden;
import com.dreamseekers.economy.ResourceBuildingResearch;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.sandbox.SandboxClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Physical-looking but entirely visual. No extra blocks/entities, selection boxes, particles,
 * client predictions, world writes, remembered buildings or render-time research requests. */
@Mod.EventBusSubscriber(modid = DreamseekersMod.MOD_ID, value = Dist.CLIENT)
public final class ProducerUpgradeForms {
    private ProducerUpgradeForms() {}

    @Mod.EventBusSubscriber(modid = DreamseekersMod.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Models {
        @SubscribeEvent public static void register(ModelEvent.RegisterAdditional event) {
            for (var mount : ProducerFormMount.values()) event.register(mount.model);
        }
    }

    private static ProducerFormMount form(BuildingPlacement p) {
        if (p.getBuilding() instanceof ReverieGarden)
            return ProducerFormMount.GARDEN.isSupported(p) ? ProducerFormMount.GARDEN : null;
        if (!(p.getBuilding() instanceof DreamScaffoldBuilding b)) return null;
        if (b.spec() == DreamBuildingSpec.SHADOW_MINE)
            return ProducerFormMount.MINE.isSupported(p) ? ProducerFormMount.MINE : null;
        if (b.spec() != DreamBuildingSpec.SHADOW_FURNACE) return null;
        if (ProducerFormMount.CAULDRON.isSupported(p)) return ProducerFormMount.CAULDRON;
        return ProducerFormMount.CHIMNEY.isSupported(p) ? ProducerFormMount.CHIMNEY : null;
    }

    private static boolean fullyVisible(ClientLevel level, BuildingPlacement p) {
        boolean fog = FogOfWarClientEvents.isEnabled();
        if (fog && !FogOfWarClientEvents.isBuildingInBrightChunk(p)) return false;
        // A single bright sample at a frontier is not permission to show a new silhouette
        // in its hidden half. No shape extends beyond the footprint (asset integrity test).
        int minX = p.minCorner.getX(), maxX = p.maxCorner.getX();
        int minZ = p.minCorner.getZ(), maxZ = p.maxCorner.getZ();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++)
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) return false;
                if (!fog) continue;
                int x0 = Math.max(minX, cx << 4), z0 = Math.max(minZ, cz << 4);
                if (!FogOfWarClientEvents.isBlockVisible(x0, z0)) return false;
                if (FogOfWarClientEvents.getEdgeMask(cx, cz) == null) continue;
                for (int x = x0; x <= Math.min(maxX, (cx << 4) + 15); x++)
                    for (int z = z0; z <= Math.min(maxZ, (cz << 4) + 15); z++)
                        if (!FogOfWarClientEvents.isBlockVisible(x, z)) return false;
            }
        return true;
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        var mc = Minecraft.getInstance();
        var level = mc.level;
        if (level == null || mc.player == null || mc.getConnection() == null || level.dimension() != Level.OVERWORLD) return;
        var camera = event.getCamera().getPosition();
        var poses = event.getPoseStack();
        var buffers = mc.renderBuffers().bufferSource();
        var sheet = Sheets.solidBlockSheet(); // ordinary opaque, depth-tested block atlas
        var manager = mc.getModelManager(); // active atlas/model pair, including after F3+T
        int range = 16 * mc.options.getEffectiveRenderDistance();
        boolean drew = false;
        for (var p : BuildingClientEvents.getBuildings()) {
            if (p.getLevel() != level || !p.isBuilt || p.isDestroyedServerside || !ResourceBuildingResearch.isUpgraded(p)) continue;
            if ((!SandboxClientEvents.isSandboxPlayer() && p.isOutsideWorldBorder())
                    || camera.x < p.minCorner.getX() - range || camera.x > p.maxCorner.getX() + 1 + range
                    || camera.z < p.minCorner.getZ() - range || camera.z > p.maxCorner.getZ() + 1 + range) continue;
            // Include the visible roof dressing only for render culling, never in the actual hitbox.
            if (!event.getFrustum().isVisible(new AABB(p.minCorner, p.maxCorner.offset(1, 6, 1)))
                    || !fullyVisible(level, p)) continue;
            var mount = form(p);
            if (mount == null) continue;
            var model = manager.getModel(mount.model);
            if (model == manager.getMissingModel()) continue;
            var probe = ProducerFormMount.worldCell(p.originPos, p.rotation, mount.light);
            int light = LevelRenderer.getLightColor(level, probe);
            poses.pushPose();
            try {
                ProducerFormMount.place(poses, p.originPos, p.rotation, camera);
                mc.getBlockRenderer().getModelRenderer().renderModel(poses.last(), buffers.getBuffer(sheet),
                        null, model, 1, 1, 1, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                drew = true;
            } finally {
                poses.popPose();
            }
        }
        if (drew) buffers.endBatch(sheet);
    }
}
