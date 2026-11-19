package com.solegendary.reignofnether.resources.kill;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.monsters.CreeperUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("reignofnether_native_kill_c") @PrefixGameTestTemplate(false)
public final class NativeCreeperAndExactKillGameTests {
 static final String PREFIX="NativeVictimC_";
 static final class NativeCreeperAndExactKillSubscriber {
  final GameTestHelper h;final List<Entity> entities=new ArrayList<>();final Map<UUID,Integer> credits=new HashMap<>();final Set<UUID> eligible=new HashSet<>();
  LivingEntity cancel,discard,aba,outer,nested;boolean recurse,sameVictim;Resources account;Mob attacker;
  NativeCreeperAndExactKillSubscriber(GameTestHelper h){this.h=h;}
  Resources account(String n){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(n,Factions.MONSTERS));Resources r=new Resources(n,100,100,100);ResourcesServerEvents.resourcesList.add(r);return r;}
  <T extends Mob>T spawn(EntityType<T> type,String owner,int x,int z){T e=type.create(h.getLevel());((Unit)e).setOwnerName(owner);e.setNoAi(true);BlockPos p=h.absolutePos(new BlockPos(x,2,z));h.getLevel().setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);h.assertTrue(h.getLevel().addFreshEntity(e),"real RTS unit enters level");entities.add(e);return e;}
  @SubscribeEvent(priority=EventPriority.HIGH) public void early(LivingDeathEvent e){if(e.getEntity()==discard)e.getEntity().discard();}
  @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void late(LivingDeathEvent e){if(e.getEntity()==cancel){e.setCanceled(true);e.getEntity().setHealth(1);}if(e.getEntity()==aba){Unit u=(Unit)e.getEntity();String old=u.getOwnerName();u.setOwnerName(PREFIX+"Transient");u.setOwnerName(old);}}
  @SubscribeEvent(priority=EventPriority.HIGHEST) public void reentrant(LivingHurtEvent e){if(recurse&&e.getEntity()==outer){recurse=false;LivingEntity target=sameVictim?outer:nested;target.invulnerableTime=0;target.hurt(e.getSource(),2000);}}
  @SubscribeEvent public void credit(ConfirmedRtsKillEvent e){if(eligible.contains(e.snapshot().victimId())&&e.tryClaim(a->ResourceTransaction.creditIncome(h.getLevel().getServer(),a,a.accountEpoch,a.revision,new ResourceTransaction.Cost(0,0,1)).applied()))credits.merge(e.snapshot().victimId(),1,Integer::sum);}
  int credits(Entity e){return credits.getOrDefault(e.getUUID(),0);}
  void clean(){MinecraftForge.EVENT_BUS.unregister(this);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));entities.forEach(Entity::discard);}
  void at(int tick,Runnable r){h.runAtTickTime(tick,()->{try{r.run();}catch(RuntimeException|Error e){clean();throw e;}});}
  void eligible(Entity e){eligible.add(e.getUUID());}
 }
 @GameTest(templateNamespace="reignofnether_native_kill_c",template="ore_mine_test_arena",batch="native_creeper_victim",timeoutTicks=100)
 public static void killedCreeperNativeDiscardAndNoFalseVictims(GameTestHelper h){
  NativeCreeperAndExactKillSubscriber f=new NativeCreeperAndExactKillSubscriber(h);MinecraftForge.EVENT_BUS.register(f);String owner=PREFIX+"Killer",victim=PREFIX+"Enemy";f.account=f.account(owner);f.account(victim);
  f.attacker=f.spawn(EntityRegistrar.ZOMBIE_UNIT.get(),owner,1,2);f.attacker.setInvulnerable(true);f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),owner,1,4);f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),victim,1,6);
  CreeperUnit killed=f.spawn(EntityRegistrar.CREEPER_UNIT.get(),victim,20,2),autonomous=f.spawn(EntityRegistrar.CREEPER_UNIT.get(),victim,40,2),environment=f.spawn(EntityRegistrar.CREEPER_UNIT.get(),victim,60,2);
  CreeperUnit canceled=f.spawn(EntityRegistrar.CREEPER_UNIT.get(),victim,20,20),changed=f.spawn(EntityRegistrar.CREEPER_UNIT.get(),victim,40,20);Mob removed=f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),victim,60,20);
  for(Entity e:List.of(killed,autonomous,environment,canceled,changed,removed))f.eligible(e);f.cancel=canceled;f.aba=changed;f.discard=removed;
  f.at(3,()->{
   killed.setHealth(1);killed.hurt(h.getLevel().damageSources().mobAttack(f.attacker),1000);
   h.assertTrue(killed.isRemoved()&&killed.getRemovalReason()==Entity.RemovalReason.DISCARDED,"genuine native death-triggered Creeper discard before LOWEST");
   MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(killed,h.getLevel().damageSources().mobAttack(f.attacker)));
   autonomous.explodeCreeper();environment.setHealth(1);environment.hurt(h.getLevel().damageSources().genericKill(),1000);
   canceled.setHealth(1);canceled.hurt(h.getLevel().damageSources().mobAttack(f.attacker),1000);
   changed.setHealth(1);changed.hurt(h.getLevel().damageSources().mobAttack(f.attacker),1000);
   removed.setHealth(1);removed.hurt(h.getLevel().damageSources().mobAttack(f.attacker),1000);
  });
  f.at(8,()->{h.assertTrue(f.credits(killed)==1&&RtsKillValueClaims.isClaimed(killed),"true killed Creeper economic value claimed exactly once");for(Entity e:List.of(autonomous,environment,canceled,changed,removed))h.assertTrue(f.credits(e)==0&&!RtsKillValueClaims.isClaimed(e),"no autonomous/environment/cancel/ABA/unknown discard value: "+e.getUUID());h.assertTrue(f.account.ore==101,"only genuine victim value credited");System.out.println("NATIVE_CREEPER_VICTIM_RUNTIME_PASS real_kill native_discard autonomous environment canceled aba unknown_discard duplicate");f.clean();h.succeed();});
 }
 @GameTest(templateNamespace="reignofnether_native_kill_c",template="ore_mine_test_arena",batch="native_exact_nested",timeoutTicks=80)
 public static void exactScopeCannotLendAuthorityToNestedHurt(GameTestHelper h){
  NativeCreeperAndExactKillSubscriber f=new NativeCreeperAndExactKillSubscriber(h);MinecraftForge.EVENT_BUS.register(f);String owner=PREFIX+"Exact",victim=PREFIX+"ExactEnemy";f.account=f.account(owner);f.account(victim);f.attacker=f.spawn(EntityRegistrar.ZOMBIE_UNIT.get(),owner,1,2);f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),victim,1,5);
  Mob outer=f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),victim,15,2),nested=f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),victim,18,2),same=f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),victim,22,2),after=f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),victim,26,2);
  for(Mob e:List.of(outer,nested,same,after)){e.setHealth(1);f.eligible(e);}
  f.at(3,()->{f.outer=outer;f.nested=nested;f.recurse=true;DamageSource generic=h.getLevel().damageSources().generic();EconomicExactDamageHooks.wrap(f.attacker,outer,outer::hurt).hurt(generic,1000);});
  f.at(7,()->{h.assertTrue(f.credits(outer)==1&&f.credits(nested)==0,"unrelated nested same DamageSource/other target cannot borrow exact scope");h.assertTrue(outer.isDeadOrDying()&&nested.isDeadOrDying(),"both real hurts happened without economic damage changes");f.outer=same;f.sameVictim=true;f.recurse=true;EconomicExactDamageHooks.wrap(f.attacker,same,same::hurt).hurt(h.getLevel().damageSources().generic(),1000);});
  f.at(11,()->{h.assertTrue(same.isDeadOrDying()&&f.credits(same)==0,"same target same DamageSource reentry taints the authority instead of transferring it");try{EconomicExactDamageHooks.wrap(f.attacker,after,(d,a)->{throw new IllegalStateException("fixture exception");}).hurt(h.getLevel().damageSources().generic(),1000);throw new AssertionError("exception preserved");}catch(IllegalStateException expected){}after.hurt(h.getLevel().damageSources().generic(),1000);});
  f.at(15,()->{h.assertTrue(f.credits(after)==0&&after.isDeadOrDying(),"exception finally clears all economic scope");h.assertTrue(f.account.ore==101,"only first exact real kill paid");System.out.println("EXACT_KILL_SCOPE_RUNTIME_PASS nested_other nested_same exception_cleanup");f.clean();h.succeed();});
 }
}
