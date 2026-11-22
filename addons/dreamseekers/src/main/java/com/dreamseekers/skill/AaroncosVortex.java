// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.skill;

import com.dreamseekers.economy.DreamFactionAccess;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.spore.SporeEffects;
import com.dreamseekers.unit.AaroncosRightHandUnit;
import com.dreamseekers.unitprogression.ExpansionResearch;
import com.solegendary.reignofnether.api.FinalUnitDamageHooks;
import com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.registrars.MobEffectRegistrar;
import com.solegendary.reignofnether.resources.*;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Bounded RTS adaptation of the native vortex: no native spell, displacement, blocks or summons.
 * The six server pulses and slow leases are transient; the ordinary SkillState owns the spent cooldown.
 */
public final class AaroncosVortex {
    public static final int RANGE=UnitSkill.AARONCOS_RIGHT_VORTEX.range(),RADIUS=6,COST=40,PULSE_INTERVAL=20,PULSES=6,SLOW_TICKS=40;
    public static final float DAMAGE=5;
    public static final double SLOW=.35;
    private static final int TARGETS_PER_PULSE=6;
    public static final ResourceLocation EFFECT_ID=ResourceLocation.fromNamespaceAndPath("dreamseekers","aaroncos_vortex");
    public static final ResourceKey<DamageType> DAMAGE_TYPE=ResourceKey.create(Registries.DAMAGE_TYPE,EFFECT_ID);
    private static final UUID SLOW_ID=UUID.fromString("7e80b86a-c6ec-4b10-bdce-600af935e461");
    private static final Map<AaroncosRightHandUnit,Zone> ACTIVE=new IdentityHashMap<>();
    private static final Map<LivingEntity,Slow> SLOWS=new IdentityHashMap<>();
    private static final Map<ServerLevel,Long> LAST_TICK=new IdentityHashMap<>();
    private static final ArrayDeque<Impact> HITS=new ArrayDeque<>();
    private AaroncosVortex() { }
    private static boolean loaded(SkillIdentity.Stamp stamp) {
        return stamp!=null&&stamp.valid()&&stamp.entity().isAddedToWorld()
                &&stamp.level().getEntity(stamp.uuid())==stamp.entity()
                &&stamp.level().getEntity(stamp.entity().getId())==stamp.entity();
    }
    private static boolean disabled(AaroncosRightHandUnit source) {
        return source.isPassenger()||source.hasEffect(MobEffectRegistrar.STUN.get())
                ||source.hasEffect(MobEffectRegistrar.FREEZE.get())||source.hasEffect(MobEffectRegistrar.UNCONTROLLABLE.get());
    }
    private static boolean los(ServerLevel level,LivingEntity source,Vec3 from,Vec3 to) {
        return level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,source)).getType()==HitResult.Type.MISS;
    }
    private static SkillResult ground(ServerPlayer viewer,AaroncosRightHandUnit source,BlockPos pos) {
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
    private static final class Zone {
        final AaroncosRightHandUnit source; final SkillIdentity.Stamp caster;
        final ServerPlayer actor;final Resources account;final UUID epoch;final BlockPos point;
        final long command,start;int pulses;boolean paid,committed,revoked;
        Zone(ServerPlayer actor,AaroncosRightHandUnit source,BlockPos point,Resources account) {
            this.actor=actor;this.source=source;this.point=point.immutable();this.account=account;epoch=account.accountEpoch;
            caster=SkillIdentity.capture(source);command=source.rts().commandRevision;start=actor.serverLevel().getGameTime();
        }
        boolean authority() {
            return !revoked&&loaded(caster)&&!disabled(source)&&source.rts().commandRevision==command
                    &&!UnitServerEvents.hasPendingImmediateAction(source.getId())
                    &&actor.isAlive()&&!actor.isRemoved()&&actor.connection!=null&&actor.serverLevel()==caster.level()
                    &&caster.level().getServer().getPlayerList().getPlayer(actor.getUUID())==actor
                    &&caster.owner().equals(actor.getGameProfile().getName())
                    &&ExtendedResourcesSync.account(caster.owner())==account&&account.extendedStateSupported()&&epoch.equals(account.accountEpoch)
                    &&DreamFactionAccess.allows(PlayerServerEvents.getRTSPlayer(caster.owner()))
                    &&ExpansionResearch.unlocked(caster.owner(),ExpansionResearch.AARONCOS_VORTEX_ID,false)
                    &&ground(actor,source,point)==SkillResult.SUCCESS;
        }
        boolean valid() {long now=caster.level().getGameTime();return ACTIVE.get(source)==this&&authority()&&now>=start&&now<=start+PULSES*PULSE_INTERVAL;}
        boolean enemy(LivingEntity target) {
            return target!=source&&target instanceof Unit unit&&!unit.getOwnerName().isBlank()
                    &&!(target instanceof com.dreamseekers.spore.JungleSporeCarrier)&&!unit.isGarrisoned()
                    &&target.isAlive()&&!target.isRemoved()&&!target.isSpectator()&&target.isAddedToWorld()
                    &&target.level()==caster.level()&&caster.level().getEntity(target.getUUID())==target
                    &&caster.level().getEntity(target.getId())==target&&source.canHarm(target)
                    &&!com.dreamseekers.neutralcontract.NeutralContracts.pending(target)
                    &&com.dreamseekers.summon.BoundedSummons.canAct(target);
        }
        boolean target(LivingEntity target) {Vec3 center=Vec3.atBottomCenterOf(point).add(0,.25,0);
            return valid()&&enemy(target)&&target.getBoundingBox().getCenter().distanceToSqr(center)<=RADIUS*RADIUS
                    &&CoordinatedMark.visible(actor,target)&&los(caster.level(),source,center,target.getBoundingBox().getCenter());}
    }
    static SkillResult cast(ServerPlayer actor,AaroncosRightHandUnit source,BlockPos point,BooleanSupplier commit) {
        if(actor==null||source==null||point==null||commit==null||!(source.level() instanceof ServerLevel level)||!level.getServer().isSameThread())return SkillResult.INVALID_REQUEST;
        if(actor.serverLevel()!=level||!actor.getGameProfile().getName().equals(source.getOwnerName())||!source.isAddedToWorld())return SkillResult.NOT_OWNER;
        if(!ExpansionResearch.unlocked(source.getOwnerName(),ExpansionResearch.AARONCOS_VORTEX_ID,false))return SkillResult.DISABLED;
        if(ACTIVE.containsKey(source)||AaroncosCombat.active(source))return SkillResult.ALREADY_ACTIVE;
        Resources account=ExtendedResourcesSync.account(source.getOwnerName());
        if(account==null||!account.extendedStateSupported())return SkillResult.NOT_OWNER;
        SkillResult admission=ground(actor,source,point);if(admission!=SkillResult.SUCCESS)return admission;
        if(account.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0)<COST)return SkillResult.INSUFFICIENT_RESOURCES;
        Zone zone=new Zone(actor,source,point,account);ACTIVE.put(source,zone);
        try {
            if(!zone.valid())return SkillResult.INVALID_SOURCE;
            if(!commit.getAsBoolean())return SkillResult.REPLAY;
            zone.committed=zone.paid;
            return zone.committed?SkillResult.SUCCESS:SkillResult.INSUFFICIENT_RESOURCES;
        } finally {if(!zone.committed)ACTIVE.remove(source,zone);}
    }
    public static boolean pay(AaroncosRightHandUnit source) {
        Zone zone=ACTIVE.get(source);if(zone==null||zone.paid||!zone.valid())return false;
        var result=ResourceTransaction.exchange(zone.caster.level().getServer(),zone.account,zone.epoch,zone.account.revision,
                new ResourceTransaction.Cost(0,0,0,Map.of(DreamPassiveEconomy.SHADOW,COST)),ResourceTransaction.Cost.ZERO);
        zone.paid=result.applied();return zone.paid;
    }
    public static boolean active(AaroncosRightHandUnit source) {Zone zone=ACTIVE.get(source);return zone!=null&&zone.committed&&zone.valid();}
    /** Channel holds position without starting any native spell; new orders invalidate the lease. */
    public static void beforeTick(AaroncosRightHandUnit source) {
        if(!active(source))return;
        source.getNavigation().stop();source.getMoveControl().setWantedPosition(source.getX(),source.getY(),source.getZ(),0);
        source.setSpeed(0);source.setZza(0);source.setXxa(0);
    }
    public static net.minecraft.world.entity.ai.goal.Goal goal(AaroncosRightHandUnit source) {
        return new net.minecraft.world.entity.ai.goal.Goal(){
            {setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
            @Override public boolean canUse(){return active(source);}
            @Override public boolean canContinueToUse(){return active(source);}
            @Override public boolean requiresUpdateEveryTick(){return true;}
            @Override public void tick(){beforeTick(source);}
        };
    }
    static final class Impact {
        final Zone zone;final SkillIdentity.Stamp target;final DamageSource damage;boolean entered,revoked;VanillaWrite vanillaWrite;
        Impact(Zone zone,LivingEntity target){this.zone=zone;this.target=SkillIdentity.capture(target);
            damage=new DamageSource(zone.caster.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DAMAGE_TYPE),zone.source,zone.source);}
        boolean legal(){return !revoked&&HITS.contains(this)&&loaded(target)&&zone.committed&&zone.target(target.entity());}
    }
    public static boolean isPulse(DamageSource source){return source!=null&&source.is(DAMAGE_TYPE);}
    private static Impact matching(LivingEntity target,DamageSource source){for(Impact hit:HITS)if(hit.damage==source&&hit.target.entity()==target)return hit;return null;}
    public static boolean rejectRepeatedHurt(LivingEntity target,DamageSource source){
        if(!isPulse(source))return false;Impact hit=matching(target,source);
        if(hit==null)return true;if(hit.entered){hit.revoked=true;return true;}hit.entered=true;return !hit.legal();
    }
    public static float validateAfterHurt(LivingEntity target,DamageSource source,float amount){
        if(!isPulse(source))return amount;Impact hit=matching(target,source);return hit!=null&&hit.legal()?amount:0;
    }
    static FinalUnitDamageHooks.Finalizer prepareFinal(LivingEntity target,DamageSource damage){
        Impact hit=matching(target,damage);return hit==null||!hit.legal()?amount->0:SkillEffects.prepareVortexFinal(target,damage,hit);
    }
    /** Magic takes vanilla actuallyHurt, which must report this exact ticket's own HP write.
     * The token lives on the private pulse attempt, never a DamageSource-only/global last-hit slot. */
    public static final class VanillaWrite {
        private final Impact hit;private final FinalUnitDamageHooks.Ticket ticket;
        private float amount;private boolean resolved,reported;
        private VanillaWrite(Impact hit,FinalUnitDamageHooks.Ticket ticket){this.hit=hit;this.ticket=ticket;}
    }
    public static VanillaWrite captureVanillaTicket(LivingEntity target,DamageSource damage,FinalUnitDamageHooks.Ticket ticket) {
        Impact hit=matching(target,damage);
        if(hit==null||!hit.legal())return null;
        if(hit.vanillaWrite!=null){hit.revoked=true;return null;}
        return hit.vanillaWrite=new VanillaWrite(hit,ticket);
    }
    public static void resolvedVanillaTicket(VanillaWrite write,float result) {
        if(write==null||write.resolved)return;
        write.resolved=true;write.amount=result;
        // A fully consumed Gel shield has no vanilla setHealth call but does have this receipt.
        if(result==0){write.reported=true;write.ticket.appliedHealthDamage(0);}
    }
    public static void vanillaHealthWrite(LivingEntity target,DamageSource damage,Runnable original) {
        Impact hit=matching(target,damage);VanillaWrite write=hit==null?null:hit.vanillaWrite;
        if(hit==null||!hit.legal()||write==null||write.hit!=hit||!write.resolved||write.reported){original.run();return;}
        write.reported=true; // commit before original write; never reward nested/repeated completion
        float before=target.getHealth();original.run();float after=target.getHealth();
        float actual=Float.isFinite(write.amount)&&write.amount>0&&Float.isFinite(before)&&Float.isFinite(after)
                ?Math.min(write.amount,Math.max(0,Math.max(0,before)-Math.max(0,after))):0;
        write.ticket.appliedHealthDamage(actual);
    }
    private static final class Slow implements SporeEffects.ManagedNegative {
        final Zone zone;final SkillIdentity.Stamp target;final long until;
        Slow(Impact hit,int ticks){zone=hit.zone;target=hit.target;until=target.level().getGameTime()+ticks;}
        @Override public ResourceLocation id(){return EFFECT_ID;}
        @Override public boolean current(){return SLOWS.get(target.entity())==this&&target.level().getGameTime()<until&&loaded(target)&&zone.authority()&&zone.enemy(target.entity());}
        @Override public boolean remove(){if(!current())return false;dropSlow(target.entity());return true;}
    }
    private static void dropSlow(LivingEntity target){SLOWS.remove(target);removeModifier(target);}
    static void removeModifier(LivingEntity target){for(var attr:List.of(Attributes.MOVEMENT_SPEED,Attributes.FLYING_SPEED)){var a=target.getAttribute(attr);if(a!=null)a.removeModifier(SLOW_ID);}}
    static void applySlow(Impact hit) {
        if(!hit.legal())return;LivingEntity target=hit.target.entity();int duration=SporeEffects.incomingManagedDuration(target,SLOW_TICKS);
        if(duration<=0||!hit.legal())return; // refresh duration, never stack the fixed modifier
        dropSlow(target);Slow slow=new Slow(hit,duration);SLOWS.put(target,slow);
        if(!slow.current()){dropSlow(target);return;}
        for(var attr:List.of(Attributes.MOVEMENT_SPEED,Attributes.FLYING_SPEED)){if(!hit.legal()||!slow.current()){dropSlow(target);return;}var a=target.getAttribute(attr);if(a!=null)a.addTransientModifier(new AttributeModifier(SLOW_ID,"dreamseekers_vortex_slow",-SLOW,AttributeModifier.Operation.MULTIPLY_TOTAL));}
    }
    public static SporeEffects.ManagedNegative negative(LivingEntity target){Slow slow=SLOWS.get(target);return slow!=null&&slow.current()?slow:null;}
    private record Recipient(String owner,UUID target) { }
    static void tick(ServerLevel level) {
        if(!level.getServer().isSameThread()||Objects.equals(LAST_TICK.put(level,level.getGameTime()),level.getGameTime()))return;
        for(var entry:List.copyOf(SLOWS.entrySet()))if(entry.getKey().level()==level&&!entry.getValue().current())dropSlow(entry.getKey());
        ACTIVE.entrySet().removeIf(e->e.getKey().level()==level&&!e.getValue().valid());
        long now=level.getGameTime();List<Zone> due=ACTIVE.values().stream().filter(z->z.caster.level()==level&&z.committed&&z.valid()
                &&z.pulses<PULSES&&now==z.start+(long)(z.pulses+1)*PULSE_INTERVAL)
                .sorted(Comparator.comparing(z->z.caster.uuid().toString())).toList();
        Map<Recipient,Impact> hits=new LinkedHashMap<>();
        for(Zone zone:due){zone.pulses++;Vec3 center=Vec3.atBottomCenterOf(zone.point).add(0,.25,0);
            var targets=level.getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(RADIUS),zone::target).stream()
                    .sorted(Comparator.comparingDouble((LivingEntity e)->e.getBoundingBox().getCenter().distanceToSqr(center)).thenComparing(e->e.getUUID().toString())).limit(TARGETS_PER_PULSE).toList();
            for(var target:targets)hits.putIfAbsent(new Recipient(zone.caster.owner(),target.getUUID()),new Impact(zone,target));
        }
        for(Impact hit:hits.values()){if(!hit.zone.target(hit.target.entity()))continue;HITS.push(hit);
            try {com.solegendary.reignofnether.resources.kill.EconomicExactDamageHooks.wrap(hit.zone.source,hit.target.entity(),hit.target.entity()::hurt).hurt(hit.damage,DAMAGE);}
            finally {HITS.removeFirstOccurrence(hit);}}
        for(Zone zone:due)if(zone.valid())particles(level,zone);
    }
    private static void particles(ServerLevel level,Zone zone){Vec3 center=Vec3.atBottomCenterOf(zone.point).add(0,.25,0);
        float spread=RADIUS*.5f;
        var packet=new net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket(ParticleTypes.PORTAL,false,center.x,center.y,center.z,spread,.4f,spread,0,12);
        for(ServerPlayer player:level.players())if(player.connection!=null&&player.blockPosition().closerToCenterThan(center,512)
                &&(!FogOfWarServerEvents.isFogActiveFor(player)||level.dimension()==Level.OVERWORLD&&FogOfWarServerEvents.isBlockVisibleFor(player,zone.point.getX(),zone.point.getZ())))player.connection.send(packet);
    }
    static void clear(LivingEntity entity){Zone zone=ACTIVE.remove(entity);if(zone!=null)zone.revoked=true;
        for(var entry:List.copyOf(SLOWS.entrySet()))if(entry.getKey()==entity||entry.getValue().zone.source==entity)dropSlow(entry.getKey());
        for(Impact hit:HITS)if(hit.zone.source==entity||hit.target.entity()==entity)hit.revoked=true;
    }
    static void stop(){for(var entity:List.copyOf(SLOWS.keySet()))dropSlow(entity);ACTIVE.clear();LAST_TICK.clear();HITS.clear();}
}
