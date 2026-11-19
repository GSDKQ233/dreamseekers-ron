package com.solegendary.reignofnether.resources.kill;
import com.solegendary.reignofnether.entities.GhastUnitFireball;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.util.*;
/** Real physical projectile collision; staged final flight is explicit, not claimed as natural aiming AI. */
@GameTestHolder("reignofnether_native_explosion") @PrefixGameTestTemplate(false)
public final class NativeProjectileExplosionGameTests {
 static final String PREFIX="NativeProjectileExplosion_";
 enum Mode { GHAST, GHAST_DEAD, GHAST_ABA, GHAST_NESTED_GRIEFING, MOUNTED_ENTITY, MOUNTED_BLOCK }
 static final class Case {Mode mode;Mob source,alternate,primary,secondary,nested;Projectile carrier;Resources account;Explosion explosion;boolean nestedRan,inNativeOnHit,blockImpact;final Map<UUID,Integer> credits=new HashMap<>();final Set<UUID> witnessed=new HashSet<>();}
 static final class NativeProjectileExplosionSubscriber {
  final GameTestHelper h;final List<Entity> entities=new ArrayList<>();final List<Case> cases=new ArrayList<>();final Set<ChunkPos> chunks=new HashSet<>();
  NativeProjectileExplosionSubscriber(GameTestHelper h){this.h=h;}
  Resources account(String n){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(n,Factions.VILLAGERS));Resources r=new Resources(n,100,100,100);ResourcesServerEvents.resourcesList.add(r);return r;}
  BlockPos position(int x,int z){BlockPos pos=h.absolutePos(new BlockPos(x,2,z));ChunkPos chunk=new ChunkPos(pos);if(!h.getLevel().getForcedChunks().contains(chunk.toLong())){h.getLevel().setChunkForced(chunk.x,chunk.z,true);chunks.add(chunk);}for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)h.getLevel().setBlock(pos.offset(dx,-1,dz),Blocks.STONE.defaultBlockState(),2);return pos;}
  <T extends Mob>T spawn(EntityType<T> type,String owner,int x,int z){T m=type.create(h.getLevel());((Unit)m).setOwnerName(owner);m.setNoAi(true);BlockPos p=position(x,z);m.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);h.assertTrue(h.getLevel().addFreshEntity(m),"registered native source/target");entities.add(m);return m;}
  @SubscribeEvent public void start(ExplosionEvent.Start e){for(Case c:cases)if(c.explosion==null&&(e.getExplosion().getExploder()==c.carrier||e.getExplosion().getExploder()==c.source)&&e.getExplosion().getPosition().distanceTo(c.secondary.position())<5)c.explosion=e.getExplosion();}
  @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void diagnoseDetonate(ExplosionEvent.Detonate e){for(Case c:cases)if(e.getExplosion().getExploder()==c.source||e.getExplosion().getExploder()==c.carrier)System.out.println("NATIVE_PROJECTILE_EXPLOSION_DIAG detonate mode="+c.mode+" center="+e.getExplosion().getPosition()+" victim="+c.secondary.position()+" affected="+e.getAffectedEntities().contains(c.secondary)+" exposure="+Explosion.getSeenPercent(e.getExplosion().getPosition(),c.secondary)+" mounted="+c.source.isPassenger());}
  @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void diagnoseAttack(LivingAttackEvent e){for(Case c:cases)if(e.getEntity()==c.secondary)System.out.println("NATIVE_PROJECTILE_EXPLOSION_DIAG attack mode="+c.mode+" source="+e.getSource().getMsgId()+" amount="+e.getAmount()+" canceled="+e.isCanceled());}
  @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void diagnoseDamage(LivingDamageEvent e){for(Case c:cases)if(e.getEntity()==c.secondary)System.out.println("NATIVE_PROJECTILE_EXPLOSION_DIAG damage mode="+c.mode+" source="+e.getSource().getMsgId()+" amount="+e.getAmount()+" canceled="+e.isCanceled());}
  @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void observeBlockImpact(net.minecraftforge.event.entity.ProjectileImpactEvent e){for(Case c:cases)if(c.mode==Mode.MOUNTED_BLOCK&&e.getProjectile()==c.carrier&&e.getRayTraceResult().getType()==net.minecraft.world.phys.HitResult.Type.BLOCK){c.blockImpact=true;System.out.println("NATIVE_PROJECTILE_EXPLOSION_DIAG real_block_impact result="+e.getImpactResult());}}
  @SubscribeEvent public void griefing(EntityMobGriefingEvent e){for(Case c:cases)if(c.mode==Mode.GHAST_NESTED_GRIEFING&&e.getEntity()==c.carrier&&!c.nestedRan){c.nestedRan=true;c.inNativeOnHit=Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(s->s.getClassName().equals("net.minecraft.world.entity.projectile.LargeFireball")&&s.getMethodName().equals("onHit"));h.getLevel().explode(c.carrier,c.nested.getX(),c.nested.getY()+.3,c.nested.getZ(),1,false,Level.ExplosionInteraction.NONE);}}
  @SubscribeEvent public void death(LivingDeathEvent e){for(Case c:cases)if(List.of(c.primary,c.secondary,c.nested).contains(e.getEntity())&&EconomicExplosionHooks.hasExactExplosionHit(c.explosion,e.getEntity(),e.getSource()))c.witnessed.add(e.getEntity().getUUID());}
  @SubscribeEvent(priority=EventPriority.HIGHEST) public void credit(ConfirmedRtsKillEvent e){for(Case c:cases)if(List.of(c.primary,c.secondary,c.nested).stream().anyMatch(v->v.getUUID().equals(e.snapshot().victimId()))&&e.tryClaim(a->ResourceTransaction.creditIncome(h.getLevel().getServer(),a,a.accountEpoch,a.revision,new ResourceTransaction.Cost(0,0,1)).applied()))c.credits.merge(e.snapshot().victimId(),1,Integer::sum);}
  int count(Case c,Entity e){return c.credits.getOrDefault(e.getUUID(),0);}
  void clean(){MinecraftForge.EVENT_BUS.unregister(this);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));entities.forEach(Entity::discard);for(ChunkPos c:chunks)h.getLevel().setChunkForced(c.x,c.z,false);chunks.clear();}
  void at(int tick,Runnable r){h.runAtTickTime(tick,()->{try{r.run();}catch(RuntimeException|Error e){clean();throw e;}});}
 }
 @GameTest(templateNamespace="reignofnether_native_explosion",template="ore_mine_test_arena",batch="native_projectile_explosion",timeoutTicks=130)
 public static void realGhastAndMountedArrowExplosions(GameTestHelper h){
  NativeProjectileExplosionSubscriber f=new NativeProjectileExplosionSubscriber(h);MinecraftForge.EVENT_BUS.register(f);
  for(Mode mode:Mode.values()){Case c=new Case();c.mode=mode;String owner=PREFIX+mode,enemy=owner+"_Enemy",other=owner+"_Other";c.account=f.account(owner);f.account(enemy);f.account(other);int z=5+mode.ordinal()*12;boolean arrow=mode==Mode.MOUNTED_ENTITY||mode==Mode.MOUNTED_BLOCK;
   c.source=arrow?f.spawn(EntityRegistrar.PILLAGER_UNIT.get(),owner,3,z):f.spawn(EntityRegistrar.GHAST_UNIT.get(),owner,3,z);c.alternate=f.spawn(EntityRegistrar.GHAST_UNIT.get(),other,3,z+4);f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),owner,3,z+2);f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),enemy,45,z);
   c.primary=f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),enemy,mode==Mode.MOUNTED_ENTITY?20:45,z+3);c.secondary=f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),enemy,19,z+1);c.nested=f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),enemy,35,z);
   c.primary.setHealth(1);c.secondary.setHealth(1);c.nested.setHealth(1);
   if(arrow){Mob mount=f.spawn(EntityRegistrar.RAVAGER_UNIT.get(),owner,3,z);h.assertTrue(c.source.startRiding(mount,true)&&c.source.isPassenger(),"native Pillager really mounted");Arrow a=new Arrow(h.getLevel(),c.source);a.setNoGravity(true);c.carrier=a;}
   else c.carrier=new GhastUnitFireball(h.getLevel(),c.source,0,0,0,2);
   c.carrier.setPos(c.source.getX(),c.source.getY()+9,c.source.getZ());c.carrier.setDeltaMovement(Vec3.ZERO);h.assertTrue(h.getLevel().addFreshEntity(c.carrier),"actual projectile first join captures launch");f.entities.add(c.carrier);
   if(mode!=Mode.MOUNTED_ENTITY){BlockPos wall=f.position(20,z);for(int y=0;y<3;y++)h.getLevel().setBlock(wall.above(y),Blocks.STONE.defaultBlockState(),2);}
   f.cases.add(c);
  }
  f.at(2,()->{for(Case c:f.cases){if(c.mode==Mode.GHAST_DEAD){c.source.setHealth(1);c.source.hurt(h.getLevel().damageSources().genericKill(),1000);}if(c.mode==Mode.GHAST_ABA){c.carrier.setOwner(c.alternate);c.carrier.setOwner(c.source);}}});
  f.at(30,()->{for(Case c:f.cases){if(c.mode==Mode.GHAST_DEAD)h.assertTrue(c.source.isRemoved()&&c.source.getRemovalReason()==Entity.RemovalReason.KILLED&&c.carrier.getOwner()==null,"genuine normal-dead shooter gone before physical collision");h.assertTrue(!c.carrier.isRemoved(),"actual staged projectile still alive");int z=5+c.mode.ordinal()*12;BlockPos pos=f.position(18,z);c.carrier.setPos(pos.getX()+.5,pos.getY()+.7,pos.getZ()+.5);c.carrier.setDeltaMovement(new Vec3(c.mode==Mode.MOUNTED_BLOCK?1.6:1.5,0,0));if(c.mode==Mode.MOUNTED_BLOCK)h.assertTrue(h.getLevel().clip(new net.minecraft.world.level.ClipContext(c.carrier.position(),c.carrier.position().add(c.carrier.getDeltaMovement()),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,c.carrier)).getType()==net.minecraft.world.phys.HitResult.Type.BLOCK,"staged arrow crosses wall face rather than ending exactly on its boundary");if(c.mode==Mode.MOUNTED_ENTITY){c.primary.setPos(pos.getX()+2.1,pos.getY(),pos.getZ()+.5);c.secondary.setPos(pos.getX()+2.1,pos.getY(),pos.getZ()+1.4);}}});
  f.at(47,()->{for(Case c:f.cases)System.out.println("NATIVE_PROJECTILE_EXPLOSION_DIAG final mode="+c.mode+" hp="+c.secondary.getHealth()+" victim="+c.secondary.position()+" carrier="+c.carrier.position()+" carrierTicks="+c.carrier.tickCount+" carrierRemoved="+c.carrier.isRemoved()+" capturedExplosion="+(c.explosion!=null)+" mounted="+c.source.isPassenger()+" sourceHp="+c.source.getHealth());for(Case c:f.cases){boolean invalid=c.mode==Mode.GHAST_ABA;if(c.mode==Mode.MOUNTED_BLOCK)h.assertTrue(c.blockImpact,"genuine Forge block impact reached, not vanilla in-ground endpoint bypass");h.assertTrue(c.secondary.isDeadOrDying(),"native physical collision explosion actually killed splash victim: "+c.mode);h.assertTrue(f.count(c,c.secondary)==(invalid?0:1),"launch-valid splash earns once: "+c.mode);h.assertTrue(c.witnessed.contains(c.secondary.getUUID())!=invalid,"exact real Explosion witness follows launch eligibility: "+c.mode);if(c.mode==Mode.MOUNTED_ENTITY)h.assertTrue(c.primary.isDeadOrDying()&&f.count(c,c.primary)==1,"one primary plus distinct splash, never same victim twice");if(c.mode==Mode.GHAST_NESTED_GRIEFING)h.assertTrue(c.nestedRan&&c.inNativeOnHit&&c.nested.isDeadOrDying()&&f.count(c,c.nested)==0&&!c.witnessed.contains(c.nested.getUUID()),"pre-constructor MOB griefing nested same-carrier explosion cannot steal pending intent");int expected=invalid?100:c.mode==Mode.MOUNTED_ENTITY?102:101;h.assertTrue(c.account.ore==expected,"exact projectile explosion ledger: "+c.mode+"="+c.account.ore);}System.out.println("NATIVE_PROJECTILE_EXPLOSION_RUNTIME_PASS ghast dead_source carrier_aba preconstructor_nested mounted_entity mounted_block");f.clean();h.succeed();});
 }
}
