package com.solegendary.reignofnether.resources.kill;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.PrimaryUnitHitHooks;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Runtime first-join launch evidence. Reloaded/unknown projectiles never invent launch authority. */
@Mod.EventBusSubscriber(modid=ReignOfNether.MOD_ID)
public final class RtsProjectileProvenance {
    private RtsProjectileProvenance() {}
    private static final Map<Projectile,Launch> LAUNCHES=new WeakHashMap<>();
    private record Launch(EconomicDamageOrigin origin,EconomicCarrierIdentity.Stamp carrier) {}
    static boolean registered(LivingEntity entity) {
        return entity.isAddedToWorld() && UnitServerEvents.getAllUnits().stream().anyMatch(unit -> unit==entity);
    }
    static Resources account(String owner) {
        Resources found=null;
        for(Resources candidate:ResourcesServerEvents.resourcesList)if(owner.equals(candidate.ownerName)) {
            if(found!=null)return null;found=candidate;
        }
        return found;
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST,receiveCanceled=false)
    public static void firstJoin(EntityJoinLevelEvent event) {
        if(!(event.getLevel() instanceof ServerLevel level)||!(event.getEntity() instanceof Projectile projectile)
                ||!level.getServer().isSameThread()||LAUNCHES.containsKey(projectile))return;
        LAUNCHES.put(projectile,null); // First join is authoritative even when rejected/missing a shooter.
        if(event.loadedFromDisk())return;
        Entity entity=projectile.getOwner();
        if(!(entity instanceof LivingEntity shooter)||shooter.level()!=level)return;
        EconomicDamageOrigin origin=EconomicDamageOrigin.captureRegistered(shooter);
        EconomicCarrierIdentity.Stamp carrier=EconomicCarrierIdentity.capture(projectile,shooter);
        if(origin!=null&&carrier!=null)LAUNCHES.put(projectile,new Launch(origin,carrier));
    }
    /** Immutable authority reference captured during a real primary hit, not its proxy DamageSource. */
    static final class DamageEvidence {
        private final Projectile projectile;
        private final Launch launch;
        private final EconomicDamageOrigin origin;
        private java.util.function.BooleanSupplier extraValidity=()->true;
        private DamageEvidence(Projectile projectile,Launch launch,EconomicDamageOrigin origin) {
            this.projectile=projectile;this.launch=launch;this.origin=origin;
        }
        LivingEntity shooter(){return origin.source;}
        boolean valid() {return extraValidity.getAsBoolean()&&origin.valid()&&(projectile==null||(launch!=null&&launch.carrier.valid(origin)));}
        boolean accepts(DamageSource damage) {
            Entity responsible=damage.getEntity();
            if(responsible==shooter())return true;
            // Native projectiles lose getOwner resolution after normal removal; do not rewrite damage.
            return projectile!=null&&origin.terminal()&&(responsible==null||responsible==projectile);
        }
    }
    static DamageEvidence captureLaunch(Projectile projectile) {
        Launch launch=LAUNCHES.get(projectile);
        return launch==null?null:new DamageEvidence(projectile,launch,launch.origin);
    }
    static DamageEvidence nativeEvidence(EconomicDamageOrigin origin) {return new DamageEvidence(null,null,origin);}
    static DamageEvidence nativeEvidence(EconomicDamageOrigin origin,java.util.function.BooleanSupplier validity) {
        DamageEvidence evidence=nativeEvidence(origin);evidence.extraValidity=validity;return evidence;
    }
    private record ExactHit(Projectile projectile,Entity victim,DamageSource source,DamageEvidence evidence,EconomicHurtInvocation invocation) {}
    private static final ThreadLocal<ExactHit> EXACT_HIT=new ThreadLocal<>();

    /** Wrap only the selected primary impact. Neither provider ordering nor damage semantics change. */
    public static PrimaryUnitHitHooks.Impact wrapPrimary(Projectile projectile,Entity victim,
                                                         PrimaryUnitHitHooks.Impact impact) {
        if(!(projectile.level() instanceof ServerLevel level)||!level.getServer().isSameThread())return impact;
        Launch launch=LAUNCHES.get(projectile);
        DamageEvidence evidence=launch==null?null:new DamageEvidence(projectile,launch,launch.origin);
        return (source,amount) -> {
            ExactHit previous=EXACT_HIT.get();
            EconomicHurtInvocation invocation=EconomicHurtInvocation.begin(victim);
            EXACT_HIT.set(new ExactHit(projectile,victim,source,evidence,invocation));
            try { return impact.hurt(source,amount); }
            finally { invocation.close();if(previous==null)EXACT_HIT.remove();else EXACT_HIT.set(previous); }
        };
    }
    /** Diagnostic only: exact object identity proof for native primary-hit runtime fixtures. */
    public static boolean hasExactPrimaryHit(Projectile projectile,Entity victim,DamageSource source) {
        ExactHit hit=EXACT_HIT.get();
        return hit!=null&&hit.projectile==projectile&&hit.victim==victim&&hit.source==source
                &&hit.invocation.eligible()&&hit.evidence!=null&&hit.evidence.valid()&&hit.evidence.accepts(source);
    }
    static DamageEvidence captureDamage(DamageSource damage,LivingEntity victim) {
        // A shared write is an exact authority boundary; never fall through on a mismatch.
        if(EconomicSharedDamageHooks.active())return EconomicSharedDamageHooks.captureShared(victim,damage);
        if(EconomicFangHooks.matches(victim,damage))return EconomicFangHooks.capture(victim,damage);
        if(EconomicExactDamageHooks.matches(victim,damage))return EconomicExactDamageHooks.capture(victim,damage);
        if(EconomicExplosionHooks.matches(victim,damage))return EconomicExplosionHooks.capture(victim,damage);
        ExactHit hit=EXACT_HIT.get();
        if(hit!=null&&hit.victim==victim&&hit.source==damage) {
            DamageEvidence evidence=hit.evidence;
            return hit.invocation.eligible()&&evidence!=null&&evidence.accepts(damage)&&evidence.valid()?evidence:null;
        }
        Entity direct=damage.getDirectEntity();
        // Ordinary MOB_ATTACK is immediate; other owner/owner proxies still need exact native hooks.
        if(direct instanceof LivingEntity shooter&&damage.getEntity()==shooter&&damage.is(DamageTypes.MOB_ATTACK)) {
            EconomicDamageOrigin origin=EconomicDamageOrigin.captureRegistered(shooter);
            return origin==null?null:new DamageEvidence(null,null,origin);
        }
        if(!(direct instanceof Projectile projectile))return null;
        Launch launch=LAUNCHES.get(projectile);
        DamageEvidence evidence=launch==null?null:new DamageEvidence(projectile,launch,launch.origin);
        return evidence!=null&&evidence.accepts(damage)&&evidence.valid()?evidence:null;
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        LAUNCHES.clear();EXACT_HIT.remove();EconomicSharedDamageHooks.clear();EconomicExplosionHooks.clear();EconomicFangHooks.clear();EconomicExactDamageHooks.clear();EconomicDamageOrigin.clear();EconomicCarrierIdentity.clear();EconomicUnitIdentity.clear();
    }
}
