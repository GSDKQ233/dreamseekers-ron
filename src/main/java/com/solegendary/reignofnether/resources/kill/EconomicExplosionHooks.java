package com.solegendary.reignofnether.resources.kill;

import com.solegendary.reignofnether.unit.units.monsters.CreeperUnit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Exact native invocation -> exact Explosion -> exact victim hurt; never a blanket owner scope. */
public final class EconomicExplosionHooks {
    private EconomicExplosionHooks() {}
    private record Intent(Level level,Entity source,RtsProjectileProvenance.DamageEvidence evidence) {}
    private static final class Frame {
        final Frame previous;final Intent intent;boolean bound;
        Frame(Frame previous,Intent intent){this.previous=previous;this.intent=intent;}
    }
    private static final class Invocation {
        Intent pending;Frame frame;
        Invocation(Intent intent){pending=intent;}
    }
    private record Hit(Explosion explosion,Entity victim,DamageSource damage,RtsProjectileProvenance.DamageEvidence evidence,EconomicHurtInvocation invocation) {}
    private static final ThreadLocal<Invocation> INVOCATION=new ThreadLocal<>();
    private static final ThreadLocal<Hit> HIT=new ThreadLocal<>();
    private static final Map<Explosion,RtsProjectileProvenance.DamageEvidence> EXPLOSIONS=new WeakHashMap<>();

    public static Explosion projectile(Projectile carrier,Entity nativeSource,Supplier<Explosion> original) {
        return invoke(new Intent(carrier.level(),nativeSource,RtsProjectileProvenance.captureLaunch(carrier)),original);
    }
    public static Explosion nativeAction(LivingEntity source,Entity nativeSource,Supplier<Explosion> original) {
        EconomicDamageOrigin origin=EconomicDamageOrigin.captureRegistered(source);
        return invoke(new Intent(source.level(),nativeSource,origin==null?null:RtsProjectileProvenance.nativeEvidence(origin)),original);
    }
    static Explosion transferredTnt(com.solegendary.reignofnether.entities.AdjustablePrimedTnt tnt,RtsProjectileProvenance.DamageEvidence evidence,Supplier<Explosion> original) {
        return invoke(new Intent(tnt.level(),tnt,evidence),original);
    }
    /** One-shot action capability captured before Creeper.dead=true, never a general discard exemption. */
    public static final class SelfExplosion {
        private final Intent intent;private boolean used;
        private SelfExplosion(Intent intent){this.intent=intent;}
        public Explosion explode(Supplier<Explosion> original) {
            Intent permission=used?new Intent(intent.level,intent.source,null):intent;
            used=true;return invoke(permission,original);
        }
    }
    public static SelfExplosion prepareCreeper(CreeperUnit source) {
        EconomicDamageOrigin origin=EconomicDamageOrigin.captureSelfExplosion(source);
        return new SelfExplosion(new Intent(source.level(),source,origin==null?null:RtsProjectileProvenance.nativeEvidence(origin)));
    }
    private static <T> T invoke(Intent intent,Supplier<T> original) {
        if(intent.level.isClientSide()||intent.level.getServer()==null||!intent.level.getServer().isSameThread())return original.get();
        Invocation previous=INVOCATION.get();
        INVOCATION.set(new Invocation(intent));
        try{return original.get();}
        finally {if(previous==null)INVOCATION.remove();else INVOCATION.set(previous);}
    }
    /** Deepest Level.explode HEAD, before MOB griefing callbacks can recursively explode. */
    public static void enter() {
        Invocation invocation=INVOCATION.get();if(invocation==null)return;
        Intent intent=invocation.pending;invocation.pending=null;
        invocation.frame=new Frame(invocation.frame,intent);
    }
    /** Normal-path unwind only; the trusted invoke finally is the exception boundary. */
    public static void leave() {
        Invocation invocation=INVOCATION.get();
        if(invocation!=null&&invocation.frame!=null)invocation.frame=invocation.frame.previous;
    }
    public static void constructed(Explosion explosion,Level level,Entity source) {
        Invocation invocation=INVOCATION.get();
        if(invocation==null||invocation.frame==null)return;
        Frame frame=invocation.frame;
        if(frame.bound)return;frame.bound=true;
        Intent intent=frame.intent;
        if(intent!=null&&intent.level==level&&intent.source==source&&intent.evidence!=null&&intent.evidence.valid())
            EXPLOSIONS.put(explosion,intent.evidence);
    }
    /** This replaces only the existing Entity.hurt invocation, with identical arguments and return. */
    public static boolean hurt(Explosion explosion,Entity victim,DamageSource damage,float amount) {
        RtsProjectileProvenance.DamageEvidence evidence=EXPLOSIONS.get(explosion);
        Hit previous=HIT.get();EconomicHurtInvocation invocation=EconomicHurtInvocation.begin(victim);HIT.set(new Hit(explosion,victim,damage,evidence,invocation));
        try{return victim.hurt(damage,amount);}
        finally{invocation.close();if(previous==null)HIT.remove();else HIT.set(previous);}
    }
    static boolean matches(LivingEntity victim,DamageSource damage) {
        Hit hit=HIT.get();return hit!=null&&hit.victim==victim&&hit.damage==damage;
    }
    static RtsProjectileProvenance.DamageEvidence capture(LivingEntity victim,DamageSource damage) {
        Hit hit=HIT.get();
        return matches(victim,damage)&&hit.invocation.eligible()&&hit.evidence!=null&&hit.evidence.valid()?hit.evidence:null;
    }
    public static boolean hasExactExplosionHit(Explosion explosion,Entity victim,DamageSource damage) {
        Hit hit=HIT.get();return hit!=null&&hit.explosion==explosion&&hit.victim==victim&&hit.damage==damage&&hit.evidence!=null
                &&hit.invocation.eligible()&&EXPLOSIONS.get(explosion)==hit.evidence&&hit.evidence.valid();
    }
    static void clear(){EconomicTntHooks.clear();EXPLOSIONS.clear();INVOCATION.remove();HIT.remove();}
}
