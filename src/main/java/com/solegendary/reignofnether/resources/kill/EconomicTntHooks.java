package com.solegendary.reignofnether.resources.kill;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.entities.AdjustablePrimedTnt;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import java.util.function.Supplier;
/** Only the native throwable-to-primed handoff transfers a launch. Saved or arbitrary TNT has no authority. */
@Mod.EventBusSubscriber(modid=ReignOfNether.MOD_ID)
public final class EconomicTntHooks {
 private EconomicTntHooks(){}
 private static final class Transfer {
  final RtsProjectileProvenance.DamageEvidence evidence;final ServerLevel level;final UUID id;
  boolean joined,invalid;
  Transfer(AdjustablePrimedTnt t,RtsProjectileProvenance.DamageEvidence e){evidence=e;level=(ServerLevel)t.level();id=t.getUUID();}
  boolean valid(AdjustablePrimedTnt t){return joined&&!invalid&&evidence.valid()&&t.getOwner()==evidence.shooter()
    &&t.level()==level&&t.getUUID().equals(id)&&(level.getEntity(id)==null||level.getEntity(id)==t);}
 }
 private static final Map<AdjustablePrimedTnt,Transfer> TRANSFERS=new WeakHashMap<>();
 public static void arm(AdjustablePrimedTnt t,Projectile launch){
  if(!(t.level() instanceof ServerLevel level)||!level.getServer().isSameThread()||t.isAddedToWorld()||t.isRemoved()||TRANSFERS.containsKey(t)||launch.level()!=level)return;
  var evidence=RtsProjectileProvenance.captureLaunch(launch);
  if(evidence!=null&&evidence.valid()&&t.getOwner()==evidence.shooter())TRANSFERS.put(t,new Transfer(t,evidence));
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST)
 public static void join(EntityJoinLevelEvent event){if(event.getEntity() instanceof AdjustablePrimedTnt t){Transfer x=TRANSFERS.get(t);if(x!=null){if(x.joined||event.loadedFromDisk())x.invalid=true;x.joined=true;}}}
 public static Explosion explode(AdjustablePrimedTnt t,Supplier<Explosion> original){
  Transfer x=TRANSFERS.get(t);
  return EconomicExplosionHooks.transferredTnt(t,x!=null&&x.valid(t)?x.evidence:null,original);
 }
 static void clear(){TRANSFERS.clear();}
}
