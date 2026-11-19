package com.solegendary.reignofnether.api;

import net.minecraft.world.damagesource.DamageSource;
import com.solegendary.reignofnether.resources.kill.RtsProjectileProvenance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import java.util.ArrayList;
import java.util.List;

/** Optional provenance at a real projectile's primary hurt call, never around its post-hit effects.
 * The default path is exactly Entity.hurt. No addon class or damage policy is linked here.
 */
public final class PrimaryUnitHitHooks {
    private PrimaryUnitHitHooks() { }
    @FunctionalInterface public interface Impact {
        boolean hurt(DamageSource source,float amount);
    }
    @FunctionalInterface public interface Provider {
        /** Capture before a single-use projectile consumes itself; null means unchanged legacy damage. */
        Impact capture(Projectile projectile,Entity victim);
    }
    private static final List<Provider> PROVIDERS=new ArrayList<>();
    public static synchronized void register(Provider provider) {
        if(provider==null||PROVIDERS.contains(provider))throw new IllegalArgumentException("Duplicate/null primary hit provider");
        PROVIDERS.add(provider);
    }
    public static Impact capture(Projectile projectile,Entity victim) {
        if(!projectile.level().isClientSide())for(Provider provider:PROVIDERS) {
            Impact impact=provider.capture(projectile,victim);
            if(impact!=null)return RtsProjectileProvenance.wrapPrimary(projectile,victim,impact);
        }
        return RtsProjectileProvenance.wrapPrimary(projectile,victim,victim::hurt);
    }
    public static boolean hurt(Projectile projectile,Entity victim,DamageSource source,float amount) {
        return capture(projectile,victim).hurt(source,amount);
    }
}
