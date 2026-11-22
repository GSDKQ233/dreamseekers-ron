// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/** Read-only spatial selection; no aggro/target/command changes and no chunk tickets. */
final class PassiveSplashTargets {
    private PassiveSplashTargets() { }
    private static final int LOCAL_LIMIT=64;
    private static final EntityTypeTest<Entity,LivingEntity> LIVING=EntityTypeTest.forClass(LivingEntity.class);
    static List<SkillIdentity.Stamp> nearest(ServerLevel level,AABB box,Predicate<LivingEntity> eligible,
                                             ToDoubleFunction<LivingEntity> distance,int limit) {
        var candidates=new ArrayList<LivingEntity>();
        level.getEntities(LIVING,box,eligible,candidates,LOCAL_LIMIT+1);
        // Never select an arbitrary iteration-order prefix in a pathological mob pile.
        if(candidates.size()>LOCAL_LIMIT)return List.of();
        return candidates.stream().sorted(Comparator.comparingDouble(distance).thenComparing(t->t.getUUID().toString()))
                .limit(limit).map(SkillIdentity::capture).toList();
    }
    /** Used only after a proven hit: its own lethal HP write doesn't invalidate the corpse's identity,
     * but never permits hidden, garrisoned, out-of-world, replaced or unregistered victims. */
    static boolean observableAfterHit(ServerPlayer owner,SkillIdentity.Stamp stamp) {
        LivingEntity t=stamp.entity();
        if(t.isAlive())return stamp.valid()&&SkillAutocastPolicy.observable(owner,t);
        if(!stamp.identityValid()||!t.isAddedToWorld()||t.isSpectator()||t.isInvisible()||((Unit)t).isGarrisoned()
                ||owner.serverLevel()!=stamp.level()||stamp.level().getEntity(t.getId())!=t
                ||!stamp.level().hasChunkAt(t.blockPosition())||!stamp.level().getWorldBorder().isWithinBounds(t.blockPosition())
                ||PlayerServerEvents.getRTSPlayer(stamp.owner())==null)return false;
        if(stamp.level().dimension()!=Level.OVERWORLD&&FogOfWarServerEvents.isVisionTableRestrictedFor(owner))return false;
        return FogOfWarServerEvents.isBlockVisibleFor(owner,t.blockPosition().getX(),t.blockPosition().getZ());
    }
}
