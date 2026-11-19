// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.buildingability;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.building.DreamWatchtower;
import com.dreamseekers.function.DreamWatchtowerPlacement;
import com.dreamseekers.skill.CoordinatedMark;
import com.dreamseekers.skill.SkillIdentity;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.addon.GarrisonableBuildingAddon;
import com.solegendary.reignofnether.research.economy.EconomyActionGate;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Existing-visibility observation only. The original target set shrinks and never refills. */
@Mod.EventBusSubscriber(modid=DreamseekersMod.MOD_ID)
public final class TowerObservation {
    private static final Map<BuildingPlacement,Observation> ACTIVE=new IdentityHashMap<>();
    private static long nextOrder;
    private TowerObservation() { }
    public static Vec3 eye(BuildingPlacement host){return Vec3.atCenterOf(new BlockPos(3,13,3).rotate(host.rotation).offset(host.originPos).above());}
    private static boolean garrison(BuildingAbilitySource source,LivingEntity entity){return BuildingAbilityEffects.permanentOwnUnit(source,entity)
            &&entity instanceof Unit unit&&GarrisonableBuildingAddon.getGarrison(unit)==source.host();}
    public static boolean ready(BuildingAbilitySource source){return source.valid()&&source.host() instanceof DreamWatchtowerPlacement tower
            &&tower.modelRevision()==2&&source.host().getBuilding() instanceof DreamWatchtower definition&&definition.isGarrisonEnabled(source.host())
            &&UnitServerEvents.getAllUnits().stream().anyMatch(entity->garrison(source,entity));}
    private static boolean eligible(ServerPlayer viewer,BuildingAbilitySource source,LivingEntity target){
        if(!(target instanceof Unit unit)||unit.getOwnerName().isBlank()||source.owner().equals(unit.getOwnerName())
                ||AlliancesServerEvents.isAllied(source.owner(),unit.getOwnerName())
                ||!BuildingAbilityEffects.permanentUnit(source.level(),unit.getOwnerName(),target)
                ||!BuildingAbilityEffects.nearby(source,target,24)||!CoordinatedMark.visible(viewer,target))return false;
        return LoadedBuildingSight.clear(source.level(),eye(source.host()),target.getEyePosition(),null);
    }
    public static BuildingAbilityEffects.Prepared prepare(ServerPlayer viewer,BuildingAbilitySource source){
        if(nextOrder==Long.MAX_VALUE)return new BuildingAbilityEffects.Prepared("unavailable",null);
        if(!ready(source))return new BuildingAbilityEffects.Prepared("requires_own_garrison",null);
        Connection connection=EconomyActionGate.connection(viewer);
        var token=connection==null?null:EconomyActionGate.query(viewer,connection);
        if(token==null)return new BuildingAbilityEffects.Prepared("stale_session",null);
        List<SkillIdentity.Stamp> occupants=UnitServerEvents.getAllUnits().stream().filter(e->garrison(source,e)).limit(3).map(SkillIdentity::capture).toList();
        List<SkillIdentity.Stamp> targets=BuildingAbilityEffects.ordered(source).stream().filter(e->eligible(viewer,source,e)).limit(6).map(SkillIdentity::capture).toList();
        if(targets.isEmpty())return new BuildingAbilityEffects.Prepared("no_target",null);
        return new BuildingAbilityEffects.Prepared("ready",new BuildingAbilityEffects.Plan(){
            private boolean identity(){var current=EconomyActionGate.query(viewer,connection);return source.valid()&&ready(source)&&current!=null
                &&current.equals(token)&&occupants.stream().anyMatch(o->o.valid()&&garrison(source,o.entity()));}
            @Override public boolean valid(){return identity()&&targets.stream().anyMatch(t->t.valid()&&eligible(viewer,source,t.entity()));}
            @Override public int targets(){return targets.size();}
            @Override public void apply(){if(!identity())return;
                List<SkillIdentity.Stamp> valid=targets.stream().filter(t->t.valid()&&eligible(viewer,source,t.entity())).toList();
                if(valid.isEmpty())return;
                Observation observation=new Observation(viewer,connection,token,source,occupants,valid);
                Observation previous=ACTIVE.put(source.host(),observation);if(previous!=null)previous.send(true);
                observation.send(false);
            }
        });
    }
    private static final class Observation {
        final ServerPlayer viewer;
        final Connection connection;
        final EconomyActionGate.Token token;
        final BuildingAbilitySource source;
        final List<SkillIdentity.Stamp> occupants;
        final List<SkillIdentity.Stamp> targets;
        final long expires,revision;
        long lastSend=Long.MIN_VALUE;
        boolean revoked, dirty;
        Observation(ServerPlayer viewer,Connection connection,EconomyActionGate.Token token,BuildingAbilitySource source,
                List<SkillIdentity.Stamp> occupants,List<SkillIdentity.Stamp> targets){
            this.viewer=viewer;this.connection=connection;this.token=token;this.source=source;this.occupants=new ArrayList<>(occupants);
            this.targets=new ArrayList<>(targets);expires=source.level().getGameTime()+120;revision=source.state().revision();
        }
        boolean valid(){if(revoked)return false;
            var players=source.level().getServer().getPlayerList();
            if(ACTIVE.get(source.host())!=this||source.level().getGameTime()>=expires||!source.valid()||!ready(source)
                    ||!viewer.isAlive()||viewer.isRemoved()||viewer.serverLevel()!=source.level()
                    ||players.getPlayer(viewer.getUUID())!=viewer||players.getPlayerByName(source.owner())!=viewer
                    ||!connection.isConnected()||connection.getPacketListener()!=viewer.connection
                    ||!token.equals(EconomyActionGate.query(viewer,connection))
                    ||occupants.stream().noneMatch(o->o.valid()&&garrison(source,o.entity())))revoked=true;
            return !revoked;
        }
        void tick(){if(!valid()){retire(this);return;}
            boolean changed=targets.removeIf(t->!t.valid()||!eligible(viewer,source,t.entity()));
            if(targets.isEmpty()){retire(this);return;}
            long now=source.level().getGameTime();if(changed||dirty||lastSend==Long.MIN_VALUE||now-lastSend>=5)send(false);
        }
        void send(boolean clear){if(nextOrder==Long.MAX_VALUE)return;
            var rows=clear?List.<TowerMarkPackets.Target>of():targets.stream().map(t->new TowerMarkPackets.Target(t.entity().getId(),t.uuid())).toList();
            int remaining=clear?0:(int)Math.max(0,Math.min(120,expires-source.level().getGameTime()));
            if(remaining==0)rows=List.of();
            TowerMarkPackets.send(viewer,connection,new TowerMarkPackets.Snapshot(++nextOrder,token.session(),token.epoch(),source.owner(),source.level().dimension().location(),
                    source.host().originPos,source.instance(),source.generation(),revision,remaining,rows));
            lastSend=source.level().getGameTime();dirty=false;
        }
    }
    private static void retire(Observation observation){if(ACTIVE.remove(observation.source.host(),observation)){observation.revoked=true;observation.send(true);}}
    public static int remaining(BuildingPlacement host){Observation observation=ACTIVE.get(host);return observation!=null&&observation.valid()
            ?(int)Math.max(0,Math.min(120,observation.expires-host.level.getGameTime())):0;}
    public static boolean marked(LivingEntity target){return ACTIVE.values().stream().anyMatch(o->o.valid()
            &&o.targets.stream().anyMatch(t->t.entity()==target&&t.valid()&&eligible(o.viewer,o.source,target)));}
    /** Remove-only cleanup; never sends or performs spatial reads from EntityLeave. */
    public static void clear(LivingEntity entity){for(var observation:ACTIVE.values()) {
        if(observation.targets.removeIf(t->t.entity()==entity))observation.dirty=true;
        // The cast is supported by any surviving original occupant, not every one.
        // Never inspect remaining entities here: EntityLeave must stay remove-only.
        // valid()/tick() retires the effect if no eligible original remains; newcomers
        // cannot refill this captured cohort or resurrect a retired observation.
        observation.occupants.removeIf(t->t.entity()==entity);
    }}
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){if(event.phase==TickEvent.Phase.END&&event.level instanceof ServerLevel level)
        for(Observation observation:List.copyOf(ACTIVE.values()))if(observation.source.level()==level)observation.tick();}
    public static void stop(){ACTIVE.clear();nextOrder=0;}
}
