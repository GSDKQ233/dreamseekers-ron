// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.summon;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.economy.DreamFactionAccess;
import com.dreamseekers.skill.*;
import com.dreamseekers.unit.*;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.resources.ExtendedResourcesSync;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.kill.RtsKillValueClaims;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Temporary, population-limited births. Only exact in-memory reservations may publish marked entities.
 * Saved markers are tombstones, never permission to recreate a lease or extend its lifetime. */
@Mod.EventBusSubscriber(modid=DreamseekersMod.MOD_ID)
public final class BoundedSummons {
    public static final String MARKER="dreamseekers:temporary_summon";
    public static final int COUNT=2,OWNER_CAP=4,LIFETIME=600;
    private static final Map<SkillUnit,Group> PENDING=new IdentityHashMap<>();
    private static final Set<Group> GROUPS=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Set<Mob> RETIRED=Collections.newSetFromMap(new IdentityHashMap<>());
    public static final com.solegendary.reignofnether.api.FinalUnitDamageHooks.Handler DAMAGE_HANDLER =
        new com.solegendary.reignofnether.api.FinalUnitDamageHooks.Handler() {
            @Override public com.solegendary.reignofnether.api.FinalUnitDamageHooks.Finalizer prepare(
                    net.minecraft.world.entity.LivingEntity victim,net.minecraft.world.damagesource.DamageSource source){return null;}
            @Override public boolean permitsFinalDamage(net.minecraft.world.entity.LivingEntity victim,
                    net.minecraft.world.damagesource.DamageSource source) {
                Entity attacker=source.getEntity();
                return attacker==null||!isTemporary(attacker)||canAct(attacker);
            }
        };
    private BoundedSummons() {}
    private static final class Group {
        final SkillUnit source;
        final SkillIdentity.Stamp caster;
        final ServerPlayer actor;
        final Resources account;
        final UUID accountEpoch;
        final UnitSkill skill;
        final long expires;
        final List<Mob> bodies=new ArrayList<>();
        final Map<Mob,SkillIdentity.Stamp> children=new IdentityHashMap<>();
        boolean confirmed,committed,retired;
        Group(ServerPlayer actor,SkillUnit source,UnitSkill skill,Resources account) {
            this.actor=actor;this.source=source;this.skill=skill;this.account=account;
            accountEpoch=account.accountEpoch;caster=SkillIdentity.capture(source.mob());
            expires=caster.level().getGameTime()+LIFETIME;
        }
        boolean ownerValid() {
            return !retired&&caster.valid()&&source.mob().isAddedToWorld()
                &&ExtendedResourcesSync.account(caster.owner())==account&&account.extendedStateSupported()
                &&accountEpoch.equals(account.accountEpoch)&&DreamFactionAccess.allows(PlayerServerEvents.getRTSPlayer(caster.owner()));
        }
        boolean childValid(Mob body) {
            var stamp=children.get(body);
            return stamp!=null&&stamp.valid()&&body.isAddedToWorld()&&isTemporary(body)
                &&RtsKillValueClaims.isFree(body)&&body.level()==caster.level();
        }
    }
    public static boolean isTemporary(Entity entity){return entity!=null&&entity.getPersistentData().contains(MARKER);}
    private static boolean matches(SkillUnit source,UnitSkill skill) {
        return skill==UnitSkill.MOTHER_BROOD&&source instanceof BeetleMotherUnit
            ||skill==UnitSkill.WEEPING_ECHOES&&source instanceof WeepingSquealGhostUnit;
    }
    private static boolean actorValid(ServerPlayer actor,SkillUnit source) {
        if(actor==null||source==null||!(source.mob().level() instanceof ServerLevel level)
                ||!level.getServer().isSameThread()||actor.serverLevel()!=level||!actor.isAlive()||actor.isRemoved()
                ||actor.connection==null||!source.getOwnerName().equals(actor.getGameProfile().getName()))return false;
        var players=level.getServer().getPlayerList();
        return players.getPlayer(actor.getUUID())==actor&&players.getPlayerByName(source.getOwnerName())==actor;
    }
    private static boolean populationFits(Group group) {
        long total=UnitServerEvents.getCurrentPopulation(group.caster.owner());
        // Join listeners may register a body before publication. Count each exact object once.
        for(Mob body:group.bodies)if(!UnitServerEvents.getAllUnits().contains(body))total+=((Unit)body).getCost().population;
        return total<=UnitServerEvents.maxPopulation&&total<=BuildingServerEvents.getTotalPopulationSupply(group.caster.owner());
    }
    private static boolean safe(ServerLevel level,Mob body) {
        var box=body.getBoundingBox();BlockPos pos=body.blockPosition();
        if(!level.hasChunksAt(BlockPos.containing(box.minX,box.minY-1,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ))
                ||!level.getWorldBorder().isWithinBounds(box)||box.minY<level.getMinBuildHeight()||box.maxY>level.getMaxBuildHeight()
                ||level.containsAnyLiquid(box)||!level.noCollision(body,box)
                ||!level.getEntities(body,box,e->e instanceof net.minecraft.world.entity.LivingEntity&&e.isAlive()).isEmpty()
                ||BuildingServerEvents.getBuildings().stream().anyMatch(b->b.level==level&&b.isPosInsideBuilding(pos)))return false;
        return level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP);
    }
    private static boolean place(Group group,Mob body) {
        var level=group.caster.level();var origin=group.source.mob().blockPosition();
        for(int r=2;r<=4;r++)for(int dz=-r;dz<=r;dz++)for(int dx=-r;dx<=r;dx++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=r)continue;
            for(int dy:new int[]{0,1,-1}) {
                var pos=origin.offset(dx,dy,dz);body.setPos(Vec3.atBottomCenterOf(pos));
                if(safe(level,body)&&group.bodies.stream().noneMatch(other->other!=body&&other.getBoundingBox().intersects(body.getBoundingBox())))return true;
            }
        }
        return false;
    }
    /** After both join events, SkillService rechecks the request, confirms the reservation, then starts
     * the cooldown. Never publish a group for a stale request or a partial pair. */
    public static SkillResult cast(ServerPlayer actor,SkillUnit source,UnitSkill skill,BooleanSupplier commit) {
        if(!actorValid(actor,source)||!matches(source,skill)||commit==null||isTemporary(source.mob()))return SkillResult.INVALID_SOURCE;
        prune();
        String owner=source.getOwnerName();
        if(PENDING.values().stream().anyMatch(g->g.caster.owner().equals(owner)))return SkillResult.ALREADY_ACTIVE;
        if(GROUPS.stream().anyMatch(g->!g.retired&&g.source==source))return SkillResult.ALREADY_ACTIVE;
        long occupied=GROUPS.stream().filter(g->!g.retired&&g.caster.owner().equals(owner)).mapToLong(g->g.bodies.stream().filter(g::childValid).count()).sum();
        if(occupied+COUNT>OWNER_CAP)return SkillResult.CAP_REACHED;
        Resources account=ExtendedResourcesSync.account(owner);
        if(account==null||account.accountEpoch==null||!account.extendedStateSupported()||!DreamFactionAccess.allows(PlayerServerEvents.getRTSPlayer(owner)))return SkillResult.NOT_OWNER;
        Group group=new Group(actor,source,skill,account);PENDING.put(source,group);GROUPS.add(group);
        try {
            for(int i=0;i<COUNT;i++) {
                Mob body=skill==UnitSkill.MOTHER_BROOD?DreamseekersMod.BEETLE.get().create(group.caster.level()):DreamseekersMod.SHADOW_GHOST_UNIT.get().create(group.caster.level());
                if(body==null)return SkillResult.SPAWN_FAILED;
                group.bodies.add(body);((Unit)body).setOwnerName(owner);body.setNoAi(true);
                CompoundTag marker=new CompoundTag();marker.putInt("schema",1);marker.putUUID("caster",source.mob().getUUID());
                marker.putUUID("body",body.getUUID());body.getPersistentData().put(MARKER,marker);
                RtsKillValueClaims.markFree(body,RtsKillValueClaims.FreeOrigin.NATIVE_SUMMON);
                group.children.put(body,SkillIdentity.capture(body));
                if(!group.ownerValid()||!place(group,body))return SkillResult.GROUND_INVALID;
            }
            if(!populationFits(group))return SkillResult.CAP_REACHED;
            for(Mob body:group.bodies) {
                if(!group.ownerValid()||!group.caster.level().addFreshEntity(body)||!group.childValid(body))return SkillResult.SPAWN_FAILED;
            }
            if(!group.ownerValid()||!populationFits(group)||group.bodies.stream().anyMatch(b->!group.childValid(b)||!safe(group.caster.level(),b)))return SkillResult.SPAWN_FAILED;
            if(!commit.getAsBoolean()||!group.confirmed)return SkillResult.REPLAY;
            group.committed=true;
            for(Mob body:group.bodies)body.setNoAi(false);
            return SkillResult.SUCCESS;
        } finally {
            PENDING.remove(source,group);
            if(!group.committed) {
                group.retired=true;GROUPS.remove(group);
                for(Mob body:group.bodies)UnitServerEvents.rollbackUncommittedProductionSpawn(body);
            }
        }
    }
    /** Keep the original actor, match/account epoch, full pair and supply checks at commit.
     * These spells use population, not a resource transaction (even a zero debit advances the ledger). */
    public static boolean confirmReservation(SkillUnit source,UnitSkill skill) {
        Group group=PENDING.get(source);
        if(group==null||group.skill!=skill||group.confirmed||group.committed||group.bodies.size()!=COUNT
                ||!group.ownerValid()||!actorValid(group.actor,source)||!populationFits(group)
                ||group.bodies.stream().anyMatch(b->!group.childValid(b)))return false;
        group.confirmed=true;
        return true;
    }
    private static void retire(Group group) {
        if(group.retired)return;group.retired=true;GROUPS.remove(group);
        // Never discard other entities inside vanilla section/leave iteration. Invalidate now, remove at END.
        for(Mob body:group.bodies)if(!body.isRemoved()){body.setNoAi(true);body.getNavigation().stop();RETIRED.add(body);}
    }
    private static void prune() {
        for(Group group:List.copyOf(GROUPS))if(!group.ownerValid()||group.caster.level().getGameTime()>=group.expires
                ||group.committed&&group.bodies.stream().noneMatch(group::childValid))retire(group);
        for(Group group:List.copyOf(GROUPS))if(group.committed)for(Mob body:group.bodies)
            if(!group.childValid(body)&&!body.isRemoved())RETIRED.add(body);
    }
    /** Final attack permission also covers non-living building targets. Ordinary units are unchanged. */
    public static boolean canAct(Entity body) {
        if(!isTemporary(body))return true;
        if(body.level().isClientSide())return true;
        return GROUPS.stream().anyMatch(g->g.committed&&!g.retired&&g.ownerValid()
            &&g.caster.level().getGameTime()<g.expires&&g.bodies.stream().anyMatch(b->b==body&&g.childValid(b)));
    }
    /** Per-block guarded budget for temporary units only; legacy ordinary attacks do not call here. */
    public static boolean attackBuilding(Mob body,com.solegendary.reignofnether.building.BuildingPlacement building) {
        if(!isTemporary(body)||!canAct(body)||building==null||!(body instanceof com.solegendary.reignofnether.unit.interfaces.AttackerUnit attacker))return false;
        String targetOwner=building.ownerName;long capture=building.getCaptureRevision();
        java.util.function.BooleanSupplier identity=()->building.level==body.level()&&capture>=0&&building.getCaptureRevision()==capture
            &&targetOwner.equals(building.ownerName)&&BuildingServerEvents.getBuildings().contains(building);
        java.util.function.BooleanSupplier allowed=()->identity.getAsBoolean()&&!building.isDestroyedServerside&&!building.shouldBeDestroyed()&&canAct(body)&&body.isAlive()
            &&!targetOwner.isBlank()&&!targetOwner.equals(((Unit)body).getOwnerName())
            &&!com.solegendary.reignofnether.alliance.AlliancesServerEvents.isAllied(targetOwner,((Unit)body).getOwnerName())&&building.isAttackable();
        if(!allowed.getAsBoolean())return false;
        double budget=attacker.getUnitAttackDamage()*building.getMeleeDamageMult()*attacker.getBuildingDamageMultiplier();
        if(!Double.isFinite(budget)||budget<=0||budget>1000000)return false;
        body.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        return building.destroyRandomBlocksGuarded(budget,allowed,identity,body).accepted();
    }
    public static int activeCount(String owner) {
        return (int)GROUPS.stream().filter(g->g.committed&&!g.retired&&g.caster.owner().equals(owner))
                .flatMap(g->g.bodies.stream().filter(g::childValid)).count();
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void join(EntityJoinLevelEvent event) {
        if(event.getLevel().isClientSide()||!isTemporary(event.getEntity()))return;
        boolean permitted=PENDING.values().stream().anyMatch(g->!g.retired&&g.bodies.stream().anyMatch(b->b==event.getEntity()));
        if(!permitted)event.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void leave(EntityLeaveLevelEvent event) {
        if(event.getLevel().isClientSide())return;
        if(event.getEntity() instanceof Mob body&&isTemporary(body))UnitServerEvents.releaseTrackedUnitChunkTicket(body);
        for(Group group:List.copyOf(GROUPS))if(group.source.mob()==event.getEntity())retire(group);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void attack(LivingAttackEvent event) {
        Entity attacker=event.getSource().getEntity();
        if(attacker==null||attacker.level().isClientSide()||!isTemporary(attacker))return;
        boolean allowed=GROUPS.stream().anyMatch(g->g.committed&&!g.retired&&g.ownerValid()
                &&g.caster.level().getGameTime()<g.expires&&g.bodies.stream().anyMatch(b->b==attacker&&g.childValid(b)));
        if(!allowed)event.setCanceled(true);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        prune();
        for(Mob body:List.copyOf(RETIRED)) {
            RETIRED.remove(body);
            if(body.level().getServer()==event.getServer()&&!body.isRemoved()&&body.isAddedToWorld()){
                UnitServerEvents.releaseTrackedUnitChunkTicket(body);body.discard();
            }
        }
    }
    /** Marked bodies intentionally never restore. Release only their exact tracked centers while
     * core ownership is still present; NORMAL core shutdown will clear transient neighborhoods.
     * Do not discard here: shutdown must not trigger defeat or reentrant entity-removal callbacks. */
    @SubscribeEvent(priority=EventPriority.HIGH) public static void stopping(ServerStoppingEvent event) {
        Set<Mob> bodies=Collections.newSetFromMap(new IdentityHashMap<>());
        for(Group group:GROUPS)bodies.addAll(group.bodies);
        for(Group group:PENDING.values())bodies.addAll(group.bodies);
        bodies.addAll(RETIRED);
        for(Mob body:bodies)if(body.level().getServer()==event.getServer()&&isTemporary(body))
            UnitServerEvents.releaseTrackedUnitChunkTicket(body);
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event){GROUPS.clear();PENDING.clear();RETIRED.clear();}
}
