// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.unit.BoneWingUnit;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owner-scoped, short-lived target vulnerability. It never writes fog, glow, reveal or target packets. */
public final class CoordinatedMark {
    private CoordinatedMark() { }
    public static final int RANGE=10,DURATION_TICKS=120,COOLDOWN_TICKS=400;
    public static final double VULNERABILITY=.12;
    private record Key(String owner,UUID target) { }
    private static final Map<ServerLevel,Map<Key,Mark>> WORLDS=new HashMap<>();
    static final class Mark {
        final SkillIdentity.Stamp caster,target;
        final ServerPlayer viewer;
        final UUID viewerUuid;
        final Key key;
        final long expiresAt;
        Mark(BoneWingUnit source,LivingEntity target,ServerPlayer viewer) {
            caster=SkillIdentity.capture(source);this.target=SkillIdentity.capture(target);
            this.viewer=viewer;viewerUuid=viewer.getUUID();key=new Key(caster.owner(),this.target.uuid());
            expiresAt=caster.level().getGameTime()+DURATION_TICKS;
        }
        boolean valid() {
            return expiresAt>caster.level().getGameTime()&&caster.valid()&&target.valid()
                    &&viewer.getUUID().equals(viewerUuid)&&!viewer.isRemoved()&&viewer.isAlive()
                    &&viewer.getGameProfile().getName().equals(caster.owner())
                    &&enemy(caster.entity(),target.entity())&&visible(viewer,target.entity());
        }
        boolean current() {
            Map<Key,Mark> world=WORLDS.get(caster.level());
            if(world==null||world.get(key)!=this)return false;
            if(valid())return true;
            world.remove(key,this);return false;
        }
    }
    public static boolean visible(ServerPlayer viewer,LivingEntity target) {
        if(viewer==null||target==null||viewer.serverLevel()!=target.level()||target.isInvisible()
                ||!target.isAlive()||target.isRemoved()||target.isSpectator())return false;
        // The current core fog masks are overworld-only and not keyed by dimension.
        if(FogOfWarServerEvents.isFogActiveFor(viewer)&&viewer.serverLevel().dimension()!=Level.OVERWORLD)return false;
        var pos=target.blockPosition(); // floor, including negative world coordinates
        return FogOfWarServerEvents.isBlockVisibleFor(viewer,pos.getX(),pos.getZ());
    }
    static boolean enemy(LivingEntity source,LivingEntity target) {
        if(!(source instanceof Unit a)||!(target instanceof Unit b)||source==target
                ||source.level()!=target.level()||!source.isAlive()||!target.isAlive()
                ||a.getOwnerName().isBlank()||b.getOwnerName().isBlank())return false;
        return !a.getOwnerName().equals(b.getOwnerName())
                &&!AlliancesServerEvents.isAllied(a.getOwnerName(),b.getOwnerName());
    }
    static SkillResult cast(ServerPlayer viewer,BoneWingUnit source,LivingEntity target) {
        if(!(source.level() instanceof ServerLevel level)||!level.getServer().isSameThread()
                ||viewer.serverLevel()!=level||!source.getOwnerName().equals(viewer.getGameProfile().getName()))return SkillResult.NOT_OWNER;
        if(!enemy(source,target)||level.getEntity(target.getUUID())!=target||target.isSpectator())return SkillResult.INVALID_TARGET;
        if(source.distanceToSqr(target)>(double)RANGE*RANGE)return SkillResult.RANGE;
        if(!visible(viewer,target))return SkillResult.HIDDEN;
        if(find(target,source.getOwnerName())!=null)return SkillResult.ALREADY_ACTIVE;
        Mark mark=new Mark(source,target,viewer);
        if(!mark.valid())return SkillResult.INVALID_TARGET;
        WORLDS.computeIfAbsent(level,l->new HashMap<>()).put(mark.key,mark);
        return SkillResult.SUCCESS;
    }
    static Mark find(LivingEntity target,String owner) {
        if(!(target.level() instanceof ServerLevel level)||!level.getServer().isSameThread())return null;
        Map<Key,Mark> world=WORLDS.get(level);if(world==null)return null;
        Mark mark=world.get(new Key(owner,target.getUUID()));
        return mark!=null&&mark.target.entity()==target&&mark.current()?mark:null;
    }
    /** Read-only form-transition guard across every benefiting owner. Does not clear, refresh or
     * consume a mark; uses the same exact-body/lifecycle validity as damage amplification. */
    public static boolean hasActiveTargetMark(LivingEntity target) {
        if(target==null||!(target.level() instanceof ServerLevel level)||!level.getServer().isSameThread())return false;
        Map<Key,Mark> world=WORLDS.get(level);if(world==null)return false;
        for(Mark mark:world.values())if(mark.target.entity()==target&&mark.target.uuid().equals(target.getUUID())
                &&mark.valid())return true;
        return false;
    }
    public static int ticksRemaining(LivingEntity target,String benefitingOwner) {
        Mark mark=find(target,benefitingOwner);
        return mark==null?0:(int)Math.max(0,mark.expiresAt-mark.caster.level().getGameTime());
    }
    static void tick(ServerLevel level) {
        if(!level.getServer().isSameThread())return;
        Map<Key,Mark> world=WORLDS.get(level);if(world==null)return;
        world.values().removeIf(mark->!mark.valid());
        if(world.isEmpty())WORLDS.remove(level);
    }
    static void clear(LivingEntity entity) {
        if(!(entity.level() instanceof ServerLevel level)||!level.getServer().isSameThread())return;
        Map<Key,Mark> world=WORLDS.get(level);if(world!=null)
            world.values().removeIf(mark->mark.caster.entity()==entity||mark.target.entity()==entity);
    }
    static void playerLeft(ServerPlayer player) {
        if(!player.serverLevel().getServer().isSameThread())return;
        for(Map<Key,Mark> world:WORLDS.values())world.values().removeIf(mark->mark.viewer==player);
    }
    static void relationshipsChanged() {
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if(server==null||!server.isSameThread())return;
        for(ServerLevel level:List.copyOf(WORLDS.keySet()))tick(level);
    }
    static void stop() { WORLDS.clear(); }
}
