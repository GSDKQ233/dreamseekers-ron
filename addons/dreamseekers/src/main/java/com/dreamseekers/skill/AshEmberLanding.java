// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.shadow.EmberLandingProjectile;
import com.dreamseekers.unit.AshBoneWingUnit;
import com.solegendary.reignofnether.api.FinalUnitDamageHooks;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** A real blocked flight followed by five fixed magic pulses. No ordinary provenance, fire,
 * persistent zone, research grant, catch-up pulse, target tracking or native projectile procedure.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=DreamseekersMod.MOD_ID)
public final class AshEmberLanding {
    public static final int RANGE=8,DURATION_TICKS=100,PULSE_INTERVAL=20,PULSE_COUNT=5,TARGET_LIMIT=6;
    public static final double RADIUS=2.5;
    public static final float PULSE_DAMAGE=2;
    public static final ResourceLocation EFFECT_ID=ResourceLocation.fromNamespaceAndPath("dreamseekers","ash_ember_landing");
    public static final ResourceKey<DamageType> DAMAGE_TYPE=ResourceKey.create(Registries.DAMAGE_TYPE,EFFECT_ID);
    private static final Map<AshBoneWingUnit,Construction> BUSY=new IdentityHashMap<>();
    private static final Map<EmberLandingProjectile,Launch> FLIGHTS=new IdentityHashMap<>();
    private static final Map<ServerLevel,World> WORLDS=new IdentityHashMap<>();
    private static final ArrayDeque<Impact> HITS=new ArrayDeque<>();
    private AshEmberLanding() { }
    private static boolean server(Entity entity) { return entity.level() instanceof ServerLevel level&&level.getServer().isSameThread(); }
    private static boolean loaded(SkillIdentity.Stamp stamp) {
        return stamp!=null&&stamp.valid()&&stamp.entity().getUUID().equals(stamp.uuid())&&stamp.entity().isAddedToWorld()
                &&stamp.level().getEntity(stamp.entity().getId())==stamp.entity();
    }
    private static boolean disabled(AshBoneWingUnit source) {
        return source.isPassenger()||source.hasEffect(MobEffectRegistrar.STUN.get())
                ||source.hasEffect(MobEffectRegistrar.FREEZE.get())||source.hasEffect(MobEffectRegistrar.UNCONTROLLABLE.get());
    }
    private static boolean enemy(AshBoneWingUnit source,LivingEntity target) {
        return target!=source&&target instanceof Unit&&!(target instanceof com.dreamseekers.spore.JungleSporeCarrier)
                &&target.isAlive()&&!target.isRemoved()&&!target.isSpectator()&&target.isAddedToWorld()
                &&target.level()==source.level()&&source.level() instanceof ServerLevel level
                &&level.getEntity(target.getUUID())==target&&level.getEntity(target.getId())==target
                &&level.getWorldBorder().isWithinBounds(target.blockPosition())&&CoordinatedMark.enemy(source,target);
    }
    private static boolean finite(Vec3 point) { return Double.isFinite(point.x)&&Double.isFinite(point.y)&&Double.isFinite(point.z); }
    private static boolean los(ServerLevel level,Entity source,Vec3 from,Vec3 to) {
        return level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,source)).getType()==HitResult.Type.MISS;
    }
    private static SkillResult ground(ServerPlayer viewer,AshBoneWingUnit source,BlockPos pos) {
        ServerLevel level=viewer.serverLevel();
        if(!level.isInWorldBounds(pos)||!level.hasChunkAt(pos)||!level.hasChunkAt(pos.below())
                ||!level.getWorldBorder().isWithinBounds(pos))return SkillResult.GROUND_INVALID;
        Vec3 point=Vec3.atBottomCenterOf(pos);
        if(source.position().distanceToSqr(point)>RANGE*RANGE)return SkillResult.RANGE;
        if(FogOfWarServerEvents.isFogActiveFor(viewer)&&level.dimension()!=Level.OVERWORLD
                ||!FogOfWarServerEvents.isBlockVisibleFor(viewer,pos.getX(),pos.getZ()))return SkillResult.HIDDEN;
        if(!level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),Direction.UP)
                ||!level.getFluidState(pos).isEmpty()||!level.getFluidState(pos.below()).isEmpty()
                ||!level.getBlockState(pos).getCollisionShape(level,pos).isEmpty())return SkillResult.GROUND_INVALID;
        return los(level,source,source.getEyePosition(),point.add(0,.01,0))?SkillResult.SUCCESS:SkillResult.HIDDEN;
    }
    private static final class Construction {
        final AshBoneWingUnit source;
        final ServerPlayer viewer;
        final String owner;
        final BlockPos point;
        final long command;
        SkillIdentity.Stamp caster;
        boolean revoked;
        Construction(ServerPlayer viewer,AshBoneWingUnit source,BlockPos point) {
            this.viewer=viewer;this.source=source;this.point=point.immutable();owner=source.getOwnerName();command=source.rts().commandRevision;
        }
        boolean valid() {
            return !revoked&&BUSY.get(source)==this&&loaded(caster)&&owner.equals(caster.owner())&&!disabled(source)
                    &&source.rts().commandRevision==command&&!UnitServerEvents.hasPendingImmediateAction(source.getId())
                    &&viewer.isAlive()&&!viewer.isRemoved()&&viewer.serverLevel()==caster.level()
                    &&viewer.getGameProfile().getName().equals(owner)&&ground(viewer,source,point)==SkillResult.SUCCESS;
        }
    }
    private static final class Launch {
        final EmberLandingProjectile projectile;
        final AshBoneWingUnit source;
        final SkillIdentity.Stamp caster;
        final UUID id,viewer;
        boolean published,committed,spent,revoked;
        Launch(EmberLandingProjectile projectile,Construction construction) {
            this.projectile=projectile;source=construction.source;caster=construction.caster;
            id=projectile.getUUID();viewer=construction.viewer.getUUID();
        }
        boolean publishedValid() {
            return !revoked&&published&&FLIGHTS.get(projectile)==this&&loaded(caster)
                    &&projectile.level()==caster.level()&&!projectile.isRemoved()&&projectile.isAddedToWorld()
                    &&projectile.getUUID().equals(id)&&projectile.getOwner()==source
                    &&caster.level().getEntity(id)==projectile&&caster.level().getEntity(projectile.getId())==projectile;
        }
        boolean valid() { return committed&&publishedValid(); }
    }
    static SkillResult cast(ServerPlayer viewer,AshBoneWingUnit source,BlockPos point,BooleanSupplier commit) {
        if(viewer==null||point==null||commit==null||!server(source)||!source.isAlive()||source.isRemoved()
                ||!source.isAddedToWorld()||source.getOwnerName().isBlank())return SkillResult.INVALID_SOURCE;
        if(viewer.serverLevel()!=source.level()||!viewer.getGameProfile().getName().equals(source.getOwnerName()))return SkillResult.NOT_OWNER;
        if(BUSY.containsKey(source))return SkillResult.ALREADY_ACTIVE;
        if(disabled(source))return SkillResult.DISABLED;
        SkillResult admission=ground(viewer,source,point);if(admission!=SkillResult.SUCCESS)return admission;
        Construction construction=new Construction(viewer,source,point);BUSY.put(source,construction);
        try {
            construction.caster=SkillIdentity.capture(source);
            if(!construction.valid())return SkillResult.INVALID_SOURCE;
            EmberLandingProjectile projectile=new EmberLandingProjectile(DreamseekersMod.EMBER_LANDING_PROJECTILE.get(),source.level());
            if(!construction.valid()) { projectile.discard();return SkillResult.INVALID_SOURCE; }
            Launch launch=new Launch(projectile,construction);FLIGHTS.put(projectile,launch);
            try {
                projectile.setOwner(source);projectile.setPos(source.getX(),source.getEyeY(),source.getZ());
                // Aim slightly into the supporting block so a successful empty-floor shot has a real collision.
                Vec3 delta=Vec3.atBottomCenterOf(point).add(0,-.05,0).subtract(projectile.position());
                projectile.shoot(delta.x,delta.y,delta.z,EmberLandingProjectile.SPEED,0);
                if(launch.revoked||projectile.isRemoved()||projectile.getOwner()!=source||!construction.valid())return SkillResult.SPAWN_FAILED;
                launch.published=construction.caster.level().addFreshEntity(projectile);
                if(!launch.publishedValid()||!construction.valid())return SkillResult.SPAWN_FAILED;
                if(!commit.getAsBoolean())return SkillResult.REPLAY;
                launch.committed=true;
                source.swing(InteractionHand.MAIN_HAND);
                return SkillResult.SUCCESS;
            } finally { if(!launch.committed) { launch.revoked=true;FLIGHTS.remove(projectile,launch);projectile.discard(); } }
        } finally { BUSY.remove(source,construction); }
    }
    public static boolean flightValid(EmberLandingProjectile projectile) {
        if(!server(projectile))return false;
        Launch launch=FLIGHTS.get(projectile);return launch!=null&&!launch.spent&&launch.valid();
    }
    public static boolean canHit(EmberLandingProjectile projectile,Entity victim) {
        Launch launch=FLIGHTS.get(projectile);
        return flightValid(projectile)&&victim instanceof LivingEntity target&&enemy(launch.source,target);
    }
    public static void ownerChanged(EmberLandingProjectile projectile) {
        if(!server(projectile))return;
        Launch launch=FLIGHTS.get(projectile);if(launch!=null&&projectile.getOwner()!=launch.source)launch.revoked=true;
    }
    public static void projectileLeft(EmberLandingProjectile projectile) {
        Launch launch=FLIGHTS.remove(projectile);if(launch!=null)launch.revoked=true;
    }
    /** First real collision creates only a zone. No damage or synthetic impact is dispatched here. */
    public static void land(EmberLandingProjectile projectile,HitResult hit) {
        if(!server(projectile))return;
        Launch launch=FLIGHTS.get(projectile);
        if(launch==null||launch.spent||!launch.valid()||hit==null||hit.getType()==HitResult.Type.MISS)return;
        if(hit instanceof net.minecraft.world.phys.EntityHitResult entityHit
                &&(!(entityHit.getEntity() instanceof LivingEntity target)||!enemy(launch.source,target)))return;
        launch.spent=true;
        try {
            Vec3 origin=hit.getLocation();
            if(!finite(origin)||!launch.valid()||!launch.caster.level().hasChunkAt(BlockPos.containing(origin))
                    ||!launch.caster.level().getWorldBorder().isWithinBounds(BlockPos.containing(origin)))return;
            World world=WORLDS.computeIfAbsent(launch.caster.level(),ignored->new World());
            world.zones.put(launch.id,new Zone(launch,origin));
        } finally { launch.revoked=true;FLIGHTS.remove(projectile,launch);projectile.discard(); }
    }
    /** Current legal enemy in any live researched zone receives one reduction, regardless of overlap. */
    public static boolean inhibitsHealing(LivingEntity target) {
        if(!(target.level() instanceof ServerLevel level)||!level.getServer().isSameThread())return false;
        World world=WORLDS.get(level);if(world==null)return false;
        return world.zones.values().stream().anyMatch(zone->zone.targetLegal(target)
            &&com.dreamseekers.unitprogression.ExpansionResearch.unlocked(zone.caster.owner(),
                com.dreamseekers.unitprogression.ExpansionResearch.EMBER_INHIBITION_ID,false));
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onHeal(net.minecraftforge.event.entity.living.LivingHealEvent event) {
        float amount=event.getAmount();
        if(Float.isFinite(amount)&&amount>0&&inhibitsHealing(event.getEntity()))
            event.setAmount(com.dreamseekers.unitprogression.ExpansionResearchMath.healing(amount,true));
    }
    private static final class World {
        final Map<UUID,Zone> zones=new HashMap<>();
        long lastProcessed=Long.MIN_VALUE;
    }
    private static final class Zone {
        final UUID id,viewer;
        final AshBoneWingUnit source;
        final SkillIdentity.Stamp caster;
        final Vec3 center;
        final long landedAt,expiresAt;
        int pulses;
        boolean revoked;
        Zone(Launch launch,Vec3 center) {
            id=launch.id;viewer=launch.viewer;source=launch.source;caster=launch.caster;this.center=center;
            landedAt=caster.level().getGameTime();expiresAt=landedAt+DURATION_TICKS;
        }
        boolean valid() {
            World world=WORLDS.get(caster.level());long now=caster.level().getGameTime();
            return !revoked&&world!=null&&world.zones.get(id)==this&&loaded(caster)&&now>=landedAt&&now<=expiresAt
                    &&caster.level().hasChunkAt(BlockPos.containing(center));
        }
        ServerPlayer viewer() {
            ServerPlayer player=caster.level().getServer().getPlayerList().getPlayer(viewer);
            return player!=null&&player.isAlive()&&!player.isRemoved()&&player.serverLevel()==caster.level()
                    &&player.getGameProfile().getName().equals(caster.owner())?player:null;
        }
        boolean targetLegal(LivingEntity target) {
            return valid()&&enemy(source,target)&&target.getBoundingBox().getCenter().distanceToSqr(center)<=RADIUS*RADIUS
                    &&CoordinatedMark.visible(viewer(),target)&&los(caster.level(),source,center,target.getBoundingBox().getCenter());
        }
    }
    public record ZoneView(UUID id,UUID sourceUuid,String owner,Vec3 center,long landedAt,long expiresAt,int pulses) { }
    public static List<ZoneView> zones(ServerLevel level) {
        if(!level.getServer().isSameThread())return List.of();
        World world=WORLDS.get(level);if(world==null)return List.of();
        return world.zones.values().stream().filter(Zone::valid).sorted(Comparator.comparing(zone->zone.id.toString()))
                .map(zone->new ZoneView(zone.id,zone.caster.uuid(),zone.caster.owner(),zone.center,zone.landedAt,zone.expiresAt,zone.pulses)).toList();
    }
    public static int activeFlightCount(AshBoneWingUnit source) {
        if(!server(source))return 0;
        return (int)FLIGHTS.values().stream().filter(launch->launch.source==source&&!launch.spent&&launch.valid()).count();
    }
    static final class Impact {
        final Zone zone;
        final SkillIdentity.Stamp target;
        final DamageSource damage;
        final long pulseAt;
        boolean entered,revoked;
        Impact(Zone zone,SkillIdentity.Stamp target) {
            this.zone=zone;this.target=target;pulseAt=zone.caster.level().getGameTime();
            damage=new DamageSource(zone.caster.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE_TYPE),zone.source,zone.source);
        }
        boolean legal() { return !revoked&&entered&&zone.caster.level().getGameTime()==pulseAt&&loaded(target)&&zone.targetLegal(target.entity()); }
    }
    public static boolean isPulse(DamageSource damage) { return damage!=null&&damage.is(DAMAGE_TYPE); }
    private static Impact matching(LivingEntity target,DamageSource damage) {
        Impact hit=HITS.peek();return hit!=null&&hit.target.entity()==target&&hit.damage==damage?hit:null;
    }
    public static boolean rejectRepeatedHurt(LivingEntity target,DamageSource damage) {
        if(!server(target)||!isPulse(damage))return false;
        Impact hit=matching(target,damage);if(hit==null)return true;
        if(hit.entered) { hit.revoked=true;return true; }
        hit.entered=true;return !hit.legal();
    }
    public static float validateAfterHurt(LivingEntity target,DamageSource damage,float amount) {
        if(!isPulse(damage))return amount;
        Impact hit=matching(target,damage);return hit!=null&&hit.legal()?amount:0;
    }
    static FinalUnitDamageHooks.Finalizer prepareFinal(LivingEntity target,DamageSource damage) {
        Impact hit=matching(target,damage);
        return hit!=null&&hit.legal()?SkillEffects.prepareEmberFinal(target,damage,hit):amount->0;
    }
    private record Recipient(String owner,UUID target) { }
    /** Collect each due zone's capped candidates, choose once, and consume all schedule entries BEFORE hurt.
     * A callback cannot replace a rejected winner or recursively run this world's same tick again.
     */
    static void tick(ServerLevel level) {
        if(!level.getServer().isSameThread())return;
        World world=WORLDS.get(level);if(world==null)return;
        long now=level.getGameTime();if(world.lastProcessed==now)return;world.lastProcessed=now;
        world.zones.values().removeIf(zone->!zone.valid());
        List<Zone> due=world.zones.values().stream().filter(zone->zone.pulses<PULSE_COUNT
                        &&now==zone.landedAt+(long)(zone.pulses+1)*PULSE_INTERVAL)
                .sorted(Comparator.comparing(zone->zone.id.toString())).toList();
        Map<Recipient,Impact> winners=new LinkedHashMap<>();
        for(Zone zone:due) {
            zone.pulses++; // consumed even when no target or a later callback rejects it
            List<SkillIdentity.Stamp> candidates=level.getEntitiesOfClass(LivingEntity.class,new AABB(zone.center,zone.center).inflate(RADIUS),zone::targetLegal)
                    .stream().sorted(Comparator.comparingDouble((LivingEntity entity)->entity.getBoundingBox().getCenter().distanceToSqr(zone.center))
                            .thenComparing(entity->entity.getUUID().toString())).limit(TARGET_LIMIT).map(SkillIdentity::capture).toList();
            for(SkillIdentity.Stamp target:candidates)winners.putIfAbsent(new Recipient(zone.caster.owner(),target.uuid()),new Impact(zone,target));
        }
        for(Impact hit:List.copyOf(winners.values())) {
            if(!loaded(hit.target)||!hit.zone.targetLegal(hit.target.entity()))continue;
            HITS.push(hit);
            try { com.solegendary.reignofnether.resources.kill.EconomicExactDamageHooks.wrap(hit.zone.caster.entity(),hit.target.entity(),hit.target.entity()::hurt).hurt(hit.damage,PULSE_DAMAGE); }
            finally { HITS.removeFirstOccurrence(hit); }
        }
        for(Zone zone:due)if(zone.valid())emitParticles(level,zone);
        world.zones.values().removeIf(zone->!zone.valid()||now>=zone.expiresAt);
        // Keep lastProcessed for this loaded level even after its final zone; reentrant landing cannot reset it.
    }
    /** Zone visuals reveal no hidden column. Preserve core's existing 512-block particle reach,
     * but do not pass through its truncating, dimensionless visibility gate a second time. */
    private static void emitParticles(ServerLevel level,Zone zone) {
        BlockPos column=BlockPos.containing(zone.center);
        Vec3 position=zone.center.add(0,.1,0);
        var packet=new net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket(
                ParticleTypes.ASH,false,position.x,position.y,position.z,
                (float)(RADIUS*.6),.1f,(float)(RADIUS*.6),0,8);
        for(ServerPlayer player:level.players()) {
            if(player.serverLevel()!=level||player.isRemoved()||player.connection==null
                    ||!player.blockPosition().closerToCenterThan(position,512))continue;
            if(FogOfWarServerEvents.isFogActiveFor(player)
                    &&(level.dimension()!=Level.OVERWORLD
                    ||!FogOfWarServerEvents.isBlockVisibleFor(player,column.getX(),column.getZ())))continue;
            player.connection.send(packet);
        }
    }
    static void clear(LivingEntity entity) {
        if(!server(entity))return;
        Construction construction=BUSY.get(entity);if(construction!=null)construction.revoked=true;
        for(Launch launch:FLIGHTS.values())if(launch.source==entity)launch.revoked=true;
        for(World world:WORLDS.values())for(Zone zone:world.zones.values())if(zone.source==entity)zone.revoked=true;
        for(Impact hit:HITS)if(hit.zone.source==entity||hit.target.entity()==entity)hit.revoked=true;
    }
    static void stop() { BUSY.clear();FLIGHTS.clear();WORLDS.clear();HITS.clear(); }
}
