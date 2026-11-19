package com.solegendary.reignofnether.resources.kill;
import com.solegendary.reignofnether.api.PrimaryUnitHitHooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
/** Narrow per-call native/addon damage witness. No blanket damage-type or last-attacker fallback. */
public final class EconomicExactDamageHooks {
 private EconomicExactDamageHooks(){}
 private record Hit(net.minecraft.world.entity.Entity victim,DamageSource damage,RtsProjectileProvenance.DamageEvidence evidence,EconomicHurtInvocation invocation){}
 private static final ThreadLocal<Hit> HIT=new ThreadLocal<>();
 public static PrimaryUnitHitHooks.Impact wrap(LivingEntity source,net.minecraft.world.entity.Entity victim,PrimaryUnitHitHooks.Impact original){
  EconomicDamageOrigin origin=EconomicDamageOrigin.captureRegistered(source);
  RtsProjectileProvenance.DamageEvidence evidence=origin==null?null:RtsProjectileProvenance.nativeEvidence(origin);
  return (damage,amount)->{Hit previous=HIT.get();EconomicHurtInvocation invocation=EconomicHurtInvocation.begin(victim);HIT.set(new Hit(victim,damage,evidence,invocation));
   try{return original.hurt(damage,amount);}finally{invocation.close();if(previous==null)HIT.remove();else HIT.set(previous);}};
 }
 public static PrimaryUnitHitHooks.Impact wrapProjectile(net.minecraft.world.entity.projectile.Projectile projectile,net.minecraft.world.entity.Entity victim,PrimaryUnitHitHooks.Impact original){
  RtsProjectileProvenance.DamageEvidence evidence=RtsProjectileProvenance.captureLaunch(projectile);
  return (damage,amount)->{Hit previous=HIT.get();EconomicHurtInvocation invocation=EconomicHurtInvocation.begin(victim);HIT.set(new Hit(victim,damage,evidence,invocation));
   try{return original.hurt(damage,amount);}finally{invocation.close();if(previous==null)HIT.remove();else HIT.set(previous);}};
 }
 static boolean matches(LivingEntity victim,DamageSource damage){Hit h=HIT.get();return h!=null&&h.victim==victim&&h.damage==damage;}
 static RtsProjectileProvenance.DamageEvidence capture(LivingEntity victim,DamageSource damage){Hit h=HIT.get();return matches(victim,damage)&&h.invocation.eligible()&&h.evidence!=null&&h.evidence.valid()?h.evidence:null;}
 static void clear(){HIT.remove();}
}
