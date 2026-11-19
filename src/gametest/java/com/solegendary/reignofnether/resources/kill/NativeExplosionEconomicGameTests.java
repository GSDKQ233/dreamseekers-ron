package com.solegendary.reignofnether.resources.kill;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.CreeperUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.util.*;
/** Native Creeper.explodeCreeper and Forge callbacks, not a direct EconomicExplosionHooks test. */
@GameTestHolder("reignofnether_native_explosion") @PrefixGameTestTemplate(false)
public final class NativeExplosionEconomicGameTests {
 static final String PREFIX="NativeExplosion_";
 enum Mode { ORDINARY, CHARGED, CANCELED, NESTED_START, NESTED_DETONATE, EXCEPTION }
 static final class Case {Mode mode;CreeperUnit source;Mob one,two,own,nested;Resources account;Explosion explosion;boolean nesting,nestedRan,thrown;Map<UUID,Integer> credits=new HashMap<>();Map<UUID,Boolean> scopes=new HashMap<>();}
 static final class NativeExplosionEconomicSubscriber {
  final GameTestHelper h;final List<Entity> entities=new ArrayList<>();final List<Case> cases=new ArrayList<>();final Set<ChunkPos> chunks=new HashSet<>();
  NativeExplosionEconomicSubscriber(GameTestHelper h){this.h=h;}
  Resources account(String owner){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,Factions.MONSTERS));Resources r=new Resources(owner,100,100,100);ResourcesServerEvents.resourcesList.add(r);return r;}
  <T extends Mob>T spawn(EntityType<T> type,String owner,int x,int z){T e=type.create(h.getLevel());((Unit)e).setOwnerName(owner);e.setNoAi(true);BlockPos pos=h.absolutePos(new BlockPos(x,2,z));ChunkPos chunk=new ChunkPos(pos);if(!h.getLevel().getForcedChunks().contains(chunk.toLong())){h.getLevel().setChunkForced(chunk.x,chunk.z,true);chunks.add(chunk);}for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)h.getLevel().setBlock(pos.offset(dx,-1,dz),Blocks.STONE.defaultBlockState(),2);e.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);h.assertTrue(h.getLevel().addFreshEntity(e),"real spawned unit");entities.add(e);return e;}
  void unknown(Case c){c.nesting=true;try{h.getLevel().explode(c.source,c.nested.getX(),c.nested.getY()+.3,c.nested.getZ(),1,false,Level.ExplosionInteraction.NONE);c.nestedRan=true;}finally{c.nesting=false;}}
  @SubscribeEvent public void start(ExplosionEvent.Start e){for(Case c:cases)if(e.getExplosion().getExploder()==c.source&&!c.nesting){if(c.explosion==null)c.explosion=e.getExplosion();if(c.mode==Mode.CANCELED)e.setCanceled(true);if(c.mode==Mode.NESTED_START&&!c.nestedRan)unknown(c);if(c.mode==Mode.EXCEPTION&&!c.thrown){c.thrown=true;throw new IllegalStateException("expected native explosion fixture exception");}}}
  @SubscribeEvent public void detonate(ExplosionEvent.Detonate e){for(Case c:cases)if(c.mode==Mode.NESTED_DETONATE&&e.getExplosion()==c.explosion&&!c.nestedRan)unknown(c);}
  @SubscribeEvent public void death(LivingDeathEvent e){for(Case c:cases)if(List.of(c.one,c.two,c.own,c.nested).contains(e.getEntity()))c.scopes.put(e.getEntity().getUUID(),EconomicExplosionHooks.hasExactExplosionHit(c.explosion,e.getEntity(),e.getSource()));}
  @SubscribeEvent(priority=EventPriority.HIGHEST) public void credit(ConfirmedRtsKillEvent e){for(Case c:cases)if(List.of(c.one,c.two,c.own,c.nested,c.source).stream().anyMatch(v->v.getUUID().equals(e.snapshot().victimId()))&&e.tryClaim(a->ResourceTransaction.creditIncome(h.getLevel().getServer(),a,a.accountEpoch,a.revision,new ResourceTransaction.Cost(0,0,1)).applied()))c.credits.merge(e.snapshot().victimId(),1,Integer::sum);}
  int count(Case c,Entity e){return c.credits.getOrDefault(e.getUUID(),0);}
  void clean(){MinecraftForge.EVENT_BUS.unregister(this);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));entities.forEach(Entity::discard);for(ChunkPos c:chunks)h.getLevel().setChunkForced(c.x,c.z,false);chunks.clear();}
  void at(int tick,Runnable r){h.runAtTickTime(tick,()->{try{r.run();}catch(RuntimeException|Error e){clean();throw e;}});}
 }
 @GameTest(templateNamespace="reignofnether_native_explosion",template="ore_mine_test_arena",batch="native_explosion_creeper",timeoutTicks=100)
 public static void nativeCreeperBlastsAndNestedCallbacks(GameTestHelper h){
  NativeExplosionEconomicSubscriber f=new NativeExplosionEconomicSubscriber(h);MinecraftForge.EVENT_BUS.register(f);
  for(Mode mode:Mode.values()){Case c=new Case();c.mode=mode;String owner=PREFIX+mode,enemy=owner+"_Enemy";c.account=f.account(owner);f.account(enemy);int z=5+mode.ordinal()*14;
   c.source=f.spawn(EntityRegistrar.CREEPER_UNIT.get(),owner,10,z);c.one=f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),enemy,11,z);c.two=f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),enemy,10,z+1);c.own=f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),owner,9,z);c.nested=f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),enemy,35,z);f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),owner,1,z);f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),enemy,45,z);
   for(Mob m:List.of(c.one,c.two,c.own,c.nested))m.setHealth(1);
   if(mode==Mode.CHARGED){LightningBolt lightning=EntityType.LIGHTNING_BOLT.create(h.getLevel());c.source.thunderHit(h.getLevel(),lightning);h.assertTrue(c.source.isPowered(),"native thunderHit genuinely charged the Creeper");}
   f.cases.add(c);
  }
  f.at(4,()->{for(Case c:f.cases){if(c.mode==Mode.EXCEPTION){boolean threw=false;try{c.source.explodeCreeper();}catch(IllegalStateException expected){threw=true;}h.assertTrue(threw&&c.thrown,"native exception propagated unchanged");f.unknown(c);}else c.source.explodeCreeper();}});
  f.at(10,()->{for(Case c:f.cases){boolean actual=c.mode!=Mode.CANCELED&&c.mode!=Mode.EXCEPTION;
   h.assertTrue(f.count(c,c.one)==(actual?1:0)&&f.count(c,c.two)==(actual?1:0),"each actual enemy explosion death pays once: "+c.mode);
   h.assertTrue(f.count(c,c.source)==0&&f.count(c,c.own)==0,"self-discard and own-unit deaths never generate enemy value");
   h.assertTrue(f.count(c,c.nested)==0,"unwrapped nested/after-exception same-owner explosion cannot borrow authority");
   h.assertTrue(c.account.ore==(actual?102:100),"exact per-victim ledger count: "+c.mode);
   if(actual){h.assertTrue(c.one.isDeadOrDying()&&c.two.isDeadOrDying(),"native explosion genuinely killed both victims");h.assertTrue(Boolean.TRUE.equals(c.scopes.get(c.one.getUUID()))&&Boolean.TRUE.equals(c.scopes.get(c.two.getUUID())),"exact original Explosion object witnessed both real hurts");}
   else h.assertTrue(c.one.isAlive()&&c.two.isAlive(),"canceled/throwing explosion did not fabricate deaths");
   if(c.mode==Mode.NESTED_START||c.mode==Mode.NESTED_DETONATE||c.mode==Mode.EXCEPTION)h.assertTrue(c.nestedRan&&c.nested.isDeadOrDying()&&!Boolean.TRUE.equals(c.scopes.get(c.nested.getUUID())),"nested same-source blast really hurt but had no outer witness");
  }System.out.println("NATIVE_EXPLOSION_ECONOMIC_RUNTIME_PASS ordinary charged multi_victim own_zero autonomous_zero cancel nested_start nested_detonate exception_cleanup");f.clean();h.succeed();});
 }
}
