// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.spore;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.skill.SkillIdentity;
import com.dreamseekers.skill.SkillResult;
import com.dreamseekers.unit.GoldenFoxUnit;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Server-only birth authority. Pending reservations count toward both caps before Join callbacks.
 * No NBT, client synced data, owner string or existing world entity can recreate a lease.
 * Activation cleansing and incoming-effect protection share this lease; point-kill remains a separate staged adapter.
 */
public final class SporeLease {
    private SporeLease() { }
    public static final float HEALTH=10;
    public static final int CAST_RANGE=10,LIFETIME_TICKS=200,OWNER_CAP=4,CLEANSE_PROTECTION_TICKS=200;
    public static final double RADIUS=3,NEW_NEGATIVE_DURATION_FACTOR=.8;
    private static final Map<UUID,Lease> LEASES=new HashMap<>();
    private static final class Construction {
        final GoldenFoxUnit source;
        final String owner;
        SkillIdentity.Stamp caster;
        Lease lease;
        Construction(GoldenFoxUnit source) { this.source=source;owner=source.getOwnerName(); }
    }
    private static final Map<GoldenFoxUnit,Construction> CONSTRUCTING=new java.util.IdentityHashMap<>();
    static final class Lease {
        final JungleSporeCarrier carrier;
        final UUID carrierId;
        final SkillIdentity.Stamp caster;
        final long expiresAt;
        boolean committed,retired,activated;
        Lease(JungleSporeCarrier carrier,SkillIdentity.Stamp caster) {
            this.carrier=carrier;carrierId=carrier.getUUID();this.caster=caster;
            expiresAt=this.caster.level().getGameTime()+LIFETIME_TICKS;
        }
        boolean current() { return LEASES.get(carrierId)==this&&!retired; }
        boolean validReservation() {
            // A pending entity need not be published yet, but must continue to occupy its slots.
            return current()&&caster.entity().isAddedToWorld()&&caster.valid()&&carrier.getUUID().equals(carrierId)
                    &&carrier.level()==caster.level()&&!carrier.isRemoved()
                    &&carrier.isAlive()&&caster.level().getGameTime()<expiresAt
                    &&(!committed||carrier.isAddedToWorld()&&published());
        }
        boolean published() { return caster.level().getEntity(carrierId)==carrier
                &&caster.level().getEntity(carrier.getId())==carrier; }
        boolean active() { return committed&&validReservation(); }
    }
    private static boolean server(Level level) { return level instanceof ServerLevel world&&world.getServer().isSameThread(); }
    private static void retire(Lease lease) {
        // Forge Leave is dispatched before lookup removal. Earlier listeners may query/prune while
        // vanilla still iterates this section; isAddedToWorld already records that untracking began.
        retire(lease,lease.caster.entity().isAddedToWorld()&&(!lease.committed||lease.carrier.isAddedToWorld()));
    }
    private static void retire(Lease lease,boolean discardBody) {
        if(!lease.current())return;
        lease.retired=true;LEASES.remove(lease.carrierId,lease);
        lease.carrier.setLeaseDisplay(false,"");
        // Remove authority before callbacks; a discarded prior object never releases a replacement lease.
        if(discardBody&&!lease.carrier.isRemoved())lease.carrier.discard();
    }
    private static void prune() {
        for(Lease lease:List.copyOf(LEASES.values()))if(!lease.validReservation())retire(lease);
    }
    private static SkillResult ground(ServerPlayer viewer,GoldenFoxUnit source,BlockPos pos,JungleSporeCarrier carrier) {
        ServerLevel level=viewer.serverLevel();
        if(!level.isInWorldBounds(pos)||!level.hasChunkAt(pos)||!level.hasChunkAt(pos.below())
                ||!level.getWorldBorder().isWithinBounds(pos)||!level.getWorldBorder().isWithinBounds(carrier.getBoundingBox()))return SkillResult.GROUND_INVALID;
        if(source.position().distanceToSqr(Vec3.atBottomCenterOf(pos))>(double)CAST_RANGE*CAST_RANGE)return SkillResult.RANGE;
        if(FogOfWarServerEvents.isFogActiveFor(viewer)&&level.dimension()!=Level.OVERWORLD
                ||!FogOfWarServerEvents.isBlockVisibleFor(viewer,pos.getX(),pos.getZ()))return SkillResult.HIDDEN;
        if(!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP)
                ||!level.getFluidState(pos).isEmpty()||!level.getFluidState(pos.below()).isEmpty()
                ||!level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()
                ||!level.noCollision(carrier,carrier.getBoundingBox())
                ||BuildingServerEvents.getBuildings().stream().anyMatch(building->building.level==level&&building.isPosInsideBuilding(pos)))return SkillResult.GROUND_INVALID;
        return SkillResult.SUCCESS;
    }
    /** Only SkillService supplies the authenticated final revision/CD commit callback. */
    public static SkillResult spawn(ServerPlayer viewer,GoldenFoxUnit source,BlockPos position,BooleanSupplier commit) {
        if(viewer==null||source==null||position==null||commit==null||!server(source.level())
                ||viewer.serverLevel()!=source.level()||!viewer.isAlive()||viewer.isRemoved()
                ||source.getOwnerName().isBlank()||!source.getOwnerName().equals(viewer.getGameProfile().getName()))return SkillResult.INVALID_REQUEST;
        ServerLevel level=viewer.serverLevel();
        if(!source.isAddedToWorld()||level.getEntity(source.getId())!=source||level.getEntity(source.getUUID())!=source||!source.isAlive()||source.isRemoved())return SkillResult.INVALID_SOURCE;
        if(CONSTRUCTING.containsKey(source))return SkillResult.ALREADY_ACTIVE;
        // This physical in-flight lock precedes capture/prune/create, all of which can trigger callbacks.
        // Source invalidation must not remove it: only this outer finally may release it.
        Construction construction=new Construction(source);CONSTRUCTING.put(source,construction);
        try {
            construction.caster=SkillIdentity.capture(source);
            if(!construction.caster.valid()||!construction.owner.equals(construction.caster.owner()))return SkillResult.INVALID_SOURCE;
            prune();
            if(LEASES.values().stream().anyMatch(lease->lease.caster.entity()==source))return SkillResult.ALREADY_ACTIVE;
            long occupied=CONSTRUCTING.values().stream().filter(reservation->reservation.owner.equals(construction.owner)).count()
                    +LEASES.values().stream().filter(lease->lease.caster.owner().equals(construction.owner)
                            &&CONSTRUCTING.values().stream().noneMatch(reservation->reservation.lease==lease)).count();
            if(occupied>OWNER_CAP)return SkillResult.CAP_REACHED;
            JungleSporeCarrier carrier=DreamseekersMod.JUNGLE_SPORE_CARRIER.get().create(level);
            if(carrier==null)return SkillResult.SPAWN_FAILED;
            if(!construction.caster.valid()) { carrier.discard();return SkillResult.INVALID_SOURCE; }
            carrier.setPos(Vec3.atBottomCenterOf(position));
            SkillResult permitted=ground(viewer,source,position,carrier);
            if(permitted!=SkillResult.SUCCESS) { carrier.discard();return permitted; }
            Lease lease=new Lease(carrier,construction.caster);construction.lease=lease;
            // Same reservation acquires its actual carrier; cap still counts it exactly once.
            LEASES.put(lease.carrierId,lease);
            try {
                if(!carrier.initializeBirthHealth())return SkillResult.SPAWN_FAILED;
                if(!level.addFreshEntity(carrier)||!lease.validReservation()||!lease.published())return SkillResult.SPAWN_FAILED;
                if(carrier.getHealth()!=HEALTH||carrier.getMaxHealth()!=HEALTH||!carrier.position().equals(Vec3.atBottomCenterOf(position)))return SkillResult.SPAWN_FAILED;
                permitted=ground(viewer,source,position,carrier);
                if(permitted!=SkillResult.SUCCESS)return permitted;
                if(!commit.getAsBoolean())return SkillResult.REPLAY;
                lease.committed=true;carrier.setLeaseDisplay(true,lease.caster.owner());
                SporeEffects.activate(lease);
                return SkillResult.SUCCESS;
            } finally { if(!lease.committed)retire(lease); }
        } finally { CONSTRUCTING.remove(source,construction); }
    }

    @Nullable static Lease current(JungleSporeCarrier carrier) {
        if(!server(carrier.level()))return null;
        Lease lease=LEASES.get(carrier.getUUID());
        if(lease==null||lease.carrier!=carrier)return null;
        if(!lease.validReservation()) { retire(lease);return null; }
        return lease;
    }
    /** Constructor-independent, one-shot initialization only while this exact reservation is unpublished. */
    static boolean pendingBirth(JungleSporeCarrier carrier) {
        Lease lease=current(carrier);
        return lease!=null&&!lease.committed&&!lease.retired
                &&lease.caster.level().getEntity(lease.carrierId)==null
                &&lease.caster.level().getEntity(carrier.getId())==null;
    }
    public static boolean active(JungleSporeCarrier carrier) { Lease lease=current(carrier);return lease!=null&&lease.active(); }
    /** Pending birth may survive a synchronous tick during Join; it grants no aura or damage target. */
    static boolean authorizedToExist(JungleSporeCarrier carrier) { return current(carrier)!=null; }
    public static int remainingTicks(JungleSporeCarrier carrier) {
        Lease lease=current(carrier);return lease!=null&&lease.active()?(int)Math.max(0,lease.expiresAt-lease.caster.level().getGameTime()):0;
    }
    @Nullable public static JungleSporeCarrier activeFor(GoldenFoxUnit source) {
        if(!server(source.level()))return null;
        prune();return LEASES.values().stream().filter(lease->lease.caster.entity()==source&&lease.active()).map(lease->lease.carrier).findFirst().orElse(null);
    }
    public static int activeCount(String trueOwner) {
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if(server==null||!server.isSameThread()||trueOwner==null)return 0;
        prune();return (int)LEASES.values().stream().filter(lease->lease.active()&&lease.caster.owner().equals(trueOwner)).count();
    }
    static List<Lease> activeLeases(ServerLevel level) {
        if(!level.getServer().isSameThread())return List.of();
        prune();return LEASES.values().stream().filter(lease->lease.caster.level()==level&&lease.active()).toList();
    }
    public static void tick(ServerLevel level) {
        if(level.getServer().isSameThread()) { prune();SporeEffects.tick(level);SporeCombat.tick(level); }
    }
    public static void clear(LivingEntity entity) { clear(entity,false,false); }
    public static void ownerChanged(LivingEntity entity) { clear(entity,true,false); }
    /** EntityLeave may run while vanilla iterates its live section list. Revoke now, never mutate that list. */
    public static void leftLevel(LivingEntity entity) { clear(entity,false,true); }
    private static void clear(LivingEntity entity,boolean ownerChanged,boolean leavingLevel) {
        if(!server(entity.level()))return;
        SporeCombat.clear(entity);
        if(!ownerChanged)SporeEffects.clear(entity);
        for(Lease lease:List.copyOf(LEASES.values()))if(lease.carrier==entity||lease.caster.entity()==entity) {
            if(leavingLevel)retire(lease,false);else retire(lease);
        }
        // A surviving body has no authority and self-discards at its next actual tick. A saved/reloaded
        // body likewise has no lease; no physical removal queue or authority is serialized.
    }
    public static void stop() {
        // Shutdown is memory cleanup, not a second opportunity to mutate unloaded entity sections.
        for(Lease lease:List.copyOf(LEASES.values()))retire(lease,false);
        LEASES.clear();CONSTRUCTING.clear();SporeEffects.stop();SporeCombat.stop();
    }
}
