package com.solegendary.reignofnether.resources.kill;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.api.PrimaryUnitHitHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
/** Fangs are not Projectile. Only first-join identity and this exact native hurt carry authority. */
@Mod.EventBusSubscriber(modid=ReignOfNether.MOD_ID)
public final class EconomicFangHooks {
 private EconomicFangHooks(){}
 private static final Map<EvokerFangs,Launch> LAUNCHES=new WeakHashMap<>();
 private static final class Launch {
  final EconomicDamageOrigin origin;final UUID id,ownerId;final ServerLevel level;
  boolean changed;
  Launch(EvokerFangs f,EconomicDamageOrigin o){origin=o;id=f.getUUID();ownerId=o.source.getUUID();level=(ServerLevel)f.level();}
  boolean valid(EvokerFangs f){return !changed&&origin.valid()&&f.level()==level&&f.getUUID().equals(id)
    &&ownerId.equals(raw(f))&&f.getOwner()==origin.source&&(level.getEntity(id)==null||level.getEntity(id)==f);}
 }
 private record Hit(EvokerFangs fang,LivingEntity victim,DamageSource damage,RtsProjectileProvenance.DamageEvidence evidence,EconomicHurtInvocation invocation){}
 private static final ThreadLocal<Hit> HIT=new ThreadLocal<>();
 private static UUID raw(EvokerFangs f){return f instanceof EconomicFangOwnerView v?v.reignofnether$economicFangOwnerUUID():null;}
 @SubscribeEvent(priority=EventPriority.HIGHEST)
 public static void joined(EntityJoinLevelEvent event){
  if(!(event.getEntity() instanceof EvokerFangs f)||!(event.getLevel() instanceof ServerLevel level)
    ||!level.getServer().isSameThread()||LAUNCHES.containsKey(f))return;
  LAUNCHES.put(f,null);
  if(event.loadedFromDisk()||f.getOwner()==null)return;
  EconomicDamageOrigin o=EconomicDamageOrigin.captureRegistered(f.getOwner());
  if(o!=null&&o.source.level()==level&&o.source.getUUID().equals(raw(f)))LAUNCHES.put(f,new Launch(f,o));
 }
 public static void ownerSet(EvokerFangs f,LivingEntity requested){
  if(f.level().isClientSide())return;Launch launch=LAUNCHES.get(f);if(launch==null)return;
  // Unlike Projectile.setOwner(null), EvokerFangs.setOwner(null) actually clears ownerUUID.
  if(requested!=launch.origin.source||!launch.ownerId.equals(raw(f)))launch.changed=true;
 }
 public static PrimaryUnitHitHooks.Impact wrap(EvokerFangs f,LivingEntity victim,PrimaryUnitHitHooks.Impact original){
  if(!(f.level() instanceof ServerLevel level)||!level.getServer().isSameThread())return original;
  Launch launch=LAUNCHES.get(f);
  RtsProjectileProvenance.DamageEvidence evidence=launch==null?null:RtsProjectileProvenance.nativeEvidence(launch.origin,()->launch.valid(f));
  return (damage,amount)->{Hit previous=HIT.get();EconomicHurtInvocation invocation=EconomicHurtInvocation.begin(victim);HIT.set(new Hit(f,victim,damage,evidence,invocation));
   try{return original.hurt(damage,amount);}finally{invocation.close();if(previous==null)HIT.remove();else HIT.set(previous);}};
 }
 public static boolean hasExactFangHit(EvokerFangs f,LivingEntity victim,DamageSource damage){Hit h=HIT.get();return h!=null&&h.fang==f&&matches(victim,damage)&&capture(victim,damage)!=null;}
 static boolean matches(LivingEntity victim,DamageSource damage){Hit h=HIT.get();return h!=null&&h.victim==victim&&h.damage==damage;}
 static RtsProjectileProvenance.DamageEvidence capture(LivingEntity victim,DamageSource damage){Hit h=HIT.get();return matches(victim,damage)&&h.invocation.eligible()&&h.evidence!=null&&h.evidence.valid()&&h.evidence.accepts(damage)?h.evidence:null;}
 static void clear(){LAUNCHES.clear();HIT.remove();}
}
