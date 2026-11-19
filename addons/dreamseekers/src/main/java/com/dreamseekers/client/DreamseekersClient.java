package com.dreamseekers.client;

import com.dreamseekers.DreamseekersMod;
import net.pasterdream.client.renderer.SmallStoneSpiritRenderer;
import net.pasterdream.client.renderer.FireflyRenderer;
import net.pasterdream.client.renderer.PinkChickenRenderer;
import net.pasterdream.client.renderer.GoldenFoxRenderer;
import net.pasterdream.client.renderer.JellyfishRenderer;
import net.pasterdream.client.renderer.BlackBeetleRenderer;
import net.pasterdream.client.renderer.BasaltSnailRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Reuse renderers from the user's installed original mod; nothing is copied. */
@Mod.EventBusSubscriber(modid = DreamseekersMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DreamseekersClient {
    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(DreamseekersMod.FIREFLY_WORKER.get(), FireflyRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.PINK_CHICKEN_SCOUT.get(), PinkChickenRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.GOLDEN_FOX_UNIT.get(), GoldenFoxRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.PINK_SLIME_UNIT.get(), DreamPinkSlimeRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.JELLYFISH_UNIT.get(), JellyfishRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.WIND_KNIGHT_UNIT.get(), net.pasterdream.client.renderer.WindKnightRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.JUNGLE_SPORE_CARRIER.get(), net.pasterdream.client.renderer.SporeEntityRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.BONE_WING_UNIT.get(), net.pasterdream.client.renderer.BoneWingRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.BONE_WING_BOLT.get(), net.pasterdream.client.renderer.BoneWingFireBallProjectileRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.STONE_SPIRIT_THROWER_UNIT.get(), SmallStoneSpiritRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.STONE_SPIRIT_THROWER_BOLT.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.STONE_HEAVY_PROJECTILE.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.STONE_SPIRIT_WORKER.get(), SmallStoneSpiritRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.SHADOW_GHOST_UNIT.get(), net.pasterdream.client.renderer.ShadowGhostRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.SHADOW_SQUEAL_GHOST_UNIT.get(), net.pasterdream.client.renderer.ShadowSquealGhostRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.SQUEAL_WAVE_BOLT.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.SUPPRESSION_WAVE_PROJECTILE.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.EMBER_LANDING_PROJECTILE.get(), net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.ASH_BONE_WING_UNIT.get(),net.pasterdream.client.renderer.AshBoneWingRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.ASH_BONE_WING_BOLT.get(),net.pasterdream.client.renderer.BoneWingFireBallProjectileRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.SHADOW_HAND_UNIT.get(),net.pasterdream.client.renderer.ShadowHandRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.FRIENDLY_GHOST_UNIT.get(),net.pasterdream.client.renderer.FriendlyGhostRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.FRIENDLY_GHOST_BOLT.get(),net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.CRAZY_TERRORBEAK_UNIT.get(),net.pasterdream.client.renderer.CrazyTerrorbeakRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.AARONCOS_LEFT_HAND_UNIT.get(),net.pasterdream.client.renderer.AaroncosLefthand0Renderer::new);
        event.registerEntityRenderer(DreamseekersMod.AARONCOS_RIGHT_HAND_UNIT.get(),net.pasterdream.client.renderer.AaroncosRighthand0Renderer::new);
        event.registerEntityRenderer(DreamseekersMod.SHADOW_GOLEM_UNIT.get(),net.pasterdream.client.renderer.ShadowGolemRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.WEEPING_SQUEAL_GHOST_UNIT.get(),net.pasterdream.client.renderer.ShadowSquealGhost0Renderer::new);
        event.registerEntityRenderer(DreamseekersMod.WEEPING_SQUEAL_WAVE_BOLT.get(),net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.TERRORBEAK_UNIT.get(),net.pasterdream.client.renderer.TerrorbeakRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.BEETLE_MOTHER_UNIT.get(), net.pasterdream.client.renderer.BlackBeetleMotherRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.BEETLE.get(), BlackBeetleRenderer::new);
        event.registerEntityRenderer(DreamseekersMod.BASALT_SNAIL.get(), BasaltSnailRenderer::new);
    }
}
