package com.dreamseekers.encounter;

import com.dreamseekers.neutralcontract.NeutralContracts;
import com.solegendary.reignofnether.sandbox.SandboxServer;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.resources.*;
import net.pasterdream.entity.BlackBeetleEntity;
import net.pasterdream.entity.BasaltSnailEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Explicit editor placement only. Never changes natural spawning, terrain, ownership or economics. */
@Mod.EventBusSubscriber(modid="dreamseekers")
public final class NeutralEncounters {
    public static final String MARKER="dreamseekers_map_encounter";
    public enum Kind { BEETLE("black_beetle"), SNAIL("basalt_snail");public final ResourceLocation type;Kind(String path){type=new ResourceLocation("pasterdream",path);}}
    private static final Set<Entity> PENDING=Collections.newSetFromMap(new IdentityHashMap<>());
    private record Stamp(UUID id,Kind kind){}
    private static final Map<Entity,Stamp> TRACKED=new WeakHashMap<>();
    private static final Map<UUID,Long> LAST=new HashMap<>();
    private static boolean busy;
    private NeutralEncounters(){}
    public static boolean authorized(ServerPlayer actor,Connection connection){return actor!=null&&actor.server!=null&&actor.server.isSameThread()&&actor.isAlive()&&!actor.isRemoved()
        &&connection!=null&&connection.isConnected()&&actor.connection!=null&&connection.getPacketListener()==actor.connection
        &&actor.server.getPlayerList().getPlayer(actor.getUUID())==actor&&actor.server.getPlayerList().getPlayerByName(actor.getGameProfile().getName())==actor
        &&SandboxServer.isSandboxPlayer(actor.getGameProfile().getName())
        &&PlayerServerEvents.rtsPlayers.stream().filter(p->p.name.equals(actor.getGameProfile().getName())).count()==1
        &&ResourcesServerEvents.resourcesList.stream().filter(r->r.ownerName.equals(actor.getGameProfile().getName())).count()==1
        &&ExtendedResourcesSync.account(actor.getGameProfile().getName()).extendedStateSupported();}
    static Kind marker(Entity e){CompoundTag data=e.getPersistentData();if(!data.contains(MARKER,10))return null;var t=data.getCompound(MARKER);
        if(!t.contains("schema",3)||t.getInt("schema")!=1||!t.contains("kind",8))return null;try{Kind k=Kind.valueOf(t.getString("kind"));return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).equals(k.type)&&e.getClass()==(k==Kind.BEETLE?BlackBeetleEntity.class:BasaltSnailEntity.class)?k:null;}catch(RuntimeException ex){return null;}}
    public static String place(ServerPlayer actor,Connection connection,Kind kind,ResourceLocation dimension,BlockPos floor){
        if(!authorized(actor,connection))return "denied";var level=actor.serverLevel();if(kind==null||!level.dimension().location().equals(dimension))return "invalid_position";
        var account=ExtendedResourcesSync.account(actor.getGameProfile().getName());var epoch=account.accountEpoch;var role=PlayerServerEvents.getRTSPlayer(actor.getGameProfile().getName());
        if(busy)return "busy";var ledger=EncounterLedger.get(actor.server);if(!ledger.supported())return "unsupported";if(ledger.count()>=EncounterLedger.LIMIT)return "capacity";
        long now=actor.server.overworld().getGameTime();Long last=LAST.get(actor.getUUID());if(last!=null&&now-last<8)return "rate_limited";
        // A deliberately bounded editor reach; no request may force chunks or address another dimension.
        if(floor.distSqr(actor.blockPosition())>512d*512d||floor.getY()<level.getMinBuildHeight()||floor.getY()+8>=level.getMaxBuildHeight()
            ||!level.hasChunkAt(floor)||!level.getWorldBorder().isWithinBounds(floor)||!level.getBlockState(floor).isFaceSturdy(level,floor,net.minecraft.core.Direction.UP)
            ||!level.getFluidState(floor).isEmpty())return "invalid_position";
        EntityType<?> type=BuiltInRegistries.ENTITY_TYPE.getOptional(kind.type).orElse(null);if(type==null)return "unavailable";
        Entity made=type.create(level);if(!(made instanceof Mob mob)||NeutralContracts.nativeKind(made)==null){if(made!=null)made.discard();return "unavailable";}
        mob.moveTo(floor.getX()+.5,floor.getY()+1,floor.getZ()+.5,0,0);mob.setPersistenceRequired();CompoundTag tag=new CompoundTag();tag.putInt("schema",1);tag.putString("kind",kind.name());mob.getPersistentData().put(MARKER,tag);
        AABB box=mob.getBoundingBox();if(!space(level,mob,box)){mob.discard();return "blocked";}
        UUID id=mob.getUUID();int numericId=mob.getId();float health=mob.getHealth(),maximum=mob.getMaxHealth();busy=true;PENDING.add(mob);boolean committed=false;
        try{if(!level.addFreshEntity(mob))return "blocked";
            if(!authorized(actor,connection)||actor.serverLevel()!=level||EncounterLedger.get(actor.server)!=ledger||!ledger.supported()
                ||ExtendedResourcesSync.account(actor.getGameProfile().getName())!=account||!account.accountEpoch.equals(epoch)||PlayerServerEvents.getRTSPlayer(actor.getGameProfile().getName())!=role
                ||mob.getId()!=numericId||!mob.getUUID().equals(id)||level.getEntity(id)!=mob||level.getEntity(mob.getId())!=mob||!mob.isAlive()||mob.isRemoved()||mob.isNoAi()
                ||!mob.isPersistenceRequired()||marker(mob)!=kind||NeutralContracts.nativeKind(mob)==null||mob.getHealth()!=health||mob.getMaxHealth()!=maximum||!mob.getActiveEffects().isEmpty()||mob.isOnFire()||!mob.getBoundingBox().equals(box)||!space(level,mob,box))return "failed_closed";
            if(!ledger.register(id,kind))return "capacity";TRACKED.put(mob,new Stamp(id,kind));LAST.put(actor.getUUID(),now);committed=true;return "placed";
        }finally{if(!committed&&!mob.isRemoved())mob.discard();PENDING.remove(mob);busy=false;}
    }
    private static boolean space(ServerLevel level,Mob mob,AABB box){
        if(!level.getWorldBorder().isWithinBounds(box))return false;
        for(BlockPos p:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY-1,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ)))
            if(!level.hasChunkAt(p)||!level.getFluidState(p).isEmpty())return false;
        for(BlockPos p:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY-1,box.minZ),BlockPos.containing(Math.nextDown(box.maxX),box.minY-1,Math.nextDown(box.maxZ))))
            if(!level.getBlockState(p).isFaceSturdy(level,p,net.minecraft.core.Direction.UP))return false;
        return level.noCollision(mob,box)&&level.getEntities(mob,box).isEmpty();}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void join(EntityJoinLevelEvent e){if(e.getLevel() instanceof ServerLevel l&&!e.isCanceled()&&!PENDING.contains(e.getEntity())){Kind k=marker(e.getEntity());if(k!=null&&EncounterLedger.get(l.getServer()).matches(e.getEntity().getUUID(),k))TRACKED.put(e.getEntity(),new Stamp(e.getEntity().getUUID(),k));}}
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent e){if(!(e.getLevel() instanceof ServerLevel l))return;Entity entity=e.getEntity();Stamp stamp=TRACKED.remove(entity);var reason=entity.getRemovalReason();
        if(stamp!=null&&reason!=null&&reason.shouldDestroy()&&stamp.id.equals(entity.getUUID())&&marker(entity)==stamp.kind)EncounterLedger.get(l.getServer()).release(stamp.id,stamp.kind);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void attack(LivingAttackEvent e){if(PENDING.contains(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void drops(LivingDropsEvent e){if(PENDING.contains(e.getEntity())){e.getDrops().clear();e.setCanceled(true);}}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void xp(LivingExperienceDropEvent e){if(PENDING.contains(e.getEntity())){e.setDroppedExperience(0);e.setCanceled(true);}}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){PENDING.clear();TRACKED.clear();LAST.clear();busy=false;}
}
