// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;
import com.dreamseekers.DreamseekersMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid=DreamseekersMod.MOD_ID)
public final class SkillLifecycle {
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if(event.phase==TickEvent.Phase.END&&event.level instanceof ServerLevel level)SkillEffects.tick(level);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void joined(EntityJoinLevelEvent event) {
        if(!event.getLevel().isClientSide()) {
            if(event.getEntity() instanceof LivingEntity living) { TerrorbeakDreadRoar.joined(living);SkillEffects.removeModifiers(living); }
            if(event.getEntity() instanceof net.minecraft.world.entity.projectile.EvokerFangs fangs)WindFangDamage.joined(fangs);
            if(event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile)OrdinarySkillHit.projectileJoined(projectile);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent event) {
        if(!event.getEntity().level().isClientSide())SkillEffects.clearForDeath(event.getEntity(),event.getSource());
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if(!event.getLevel().isClientSide()&&event.getEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile)
            OrdinarySkillHit.projectileLeft(projectile);
        if(!event.getLevel().isClientSide()&&event.getEntity() instanceof net.minecraft.world.entity.projectile.EvokerFangs fangs) {
            WindFangDamage.left(fangs);OrdinaryFangOrigin.left(fangs);
        }
        if(!event.getLevel().isClientSide()&&event.getEntity() instanceof LivingEntity living) {
            SkillEffects.clearForLeave(living);SkillIdentity.forget(living);
            if(living instanceof SkillUnit unit)unit.rts().skills().detach();
        }
    }
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)CoordinatedMark.playerLeft(player);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { SkillEffects.stop(); }
}
