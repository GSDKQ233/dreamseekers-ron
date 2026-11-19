package com.solegendary.reignofnether.resources.kill;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.util.*;
/** Native entity tick delivers delayed fang hurt; no fake callback or synthetic claim. */
@GameTestHolder("reignofnether_native_kill_c") @PrefixGameTestTemplate(false)
public final class NativeFangKillGameTests {
 static final String PREFIX="NativeFangC_";
 enum Mode { LIVE, DEAD_SOURCE, SOURCE_DISCARD, SOURCE_ABA, FANG_ABA, FANG_NULL_ABA, ACCOUNT_RESET, FAKE_MAGIC, LATE_CANCEL }
 static final class Case {Mode mode;Mob source,target,alternate;EvokerFangs fang;Resources account;int credits;boolean scope;}
 static final class NativeFangKillSubscriber {
  final GameTestHelper h;final List<Entity> entities=new ArrayList<>();final List<Case> cases=new ArrayList<>();
  NativeFangKillSubscriber(GameTestHelper h){this.h=h;}
  Resources account(String n){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(n,Factions.VILLAGERS));Resources r=new Resources(n,100,100,100);ResourcesServerEvents.resourcesList.add(r);return r;}
  <T extends Mob>T spawn(EntityType<T> type,String owner,int x,int z){T e=type.create(h.getLevel());((Unit)e).setOwnerName(owner);e.setNoAi(true);BlockPos p=h.absolutePos(new BlockPos(x,2,z));h.getLevel().setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);h.assertTrue(h.getLevel().addFreshEntity(e),"unit enters real server level");entities.add(e);return e;}
  @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public void death(LivingDeathEvent e){for(Case c:cases)if(e.getEntity()==c.target){c.scope=EconomicFangHooks.hasExactFangHit(c.fang,c.target,e.getSource());if(c.mode==Mode.LATE_CANCEL){e.setCanceled(true);c.target.setHealth(1);}}}
  @SubscribeEvent public void credit(ConfirmedRtsKillEvent e){for(Case c:cases)if(e.snapshot().victimId().equals(c.target.getUUID())&&e.tryClaim(a->ResourceTransaction.creditIncome(h.getLevel().getServer(),a,a.accountEpoch,a.revision,new ResourceTransaction.Cost(0,0,1)).applied()))c.credits++;}
  void clean(){MinecraftForge.EVENT_BUS.unregister(this);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));entities.forEach(Entity::discard);}
  void at(int tick,Runnable r){h.runAtTickTime(tick,()->{try{r.run();}catch(RuntimeException|Error e){clean();throw e;}});}
 }
 @GameTest(templateNamespace="reignofnether_native_kill_c",template="ore_mine_test_arena",batch="native_fangs",timeoutTicks=100)
 public static void delayedFangOriginsAndCancellation(GameTestHelper h){
  NativeFangKillSubscriber f=new NativeFangKillSubscriber(h);MinecraftForge.EVENT_BUS.register(f);
  for(Mode mode:Mode.values()){
   Case c=new Case();c.mode=mode;String owner=PREFIX+mode,victim=owner+"_Enemy",other=owner+"_Other";c.account=f.account(owner);f.account(victim);f.account(other);int z=5+mode.ordinal()*7;
   c.source=f.spawn(EntityRegistrar.EVOKER_UNIT.get(),owner,3,z);c.target=f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),victim,20,z);c.target.setHealth(1);c.alternate=f.spawn(EntityRegistrar.EVOKER_UNIT.get(),other,30,z);
   f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),owner,3,z+2);f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),victim,20,z+2);
   c.fang=new EvokerFangs(h.getLevel(),c.target.getX(),c.target.getY(),c.target.getZ(),0,30,c.source);
   if(mode!=Mode.FAKE_MAGIC){h.assertTrue(h.getLevel().addFreshEntity(c.fang),"real delayed fang first join");f.entities.add(c.fang);}f.cases.add(c);
  }
  f.at(2,()->{for(Case c:f.cases)switch(c.mode){
   case DEAD_SOURCE -> {c.source.setHealth(1);c.source.hurt(h.getLevel().damageSources().genericKill(),1000);}
   case SOURCE_DISCARD -> c.source.discard();
   case SOURCE_ABA -> {String owner=((Unit)c.source).getOwnerName();((Unit)c.source).setOwnerName(PREFIX+"Temporary");((Unit)c.source).setOwnerName(owner);}
   case FANG_ABA -> {c.fang.setOwner(c.alternate);c.fang.setOwner(c.source);}
   case FANG_NULL_ABA -> {c.fang.setOwner(null);h.assertTrue(c.fang.getOwner()==null,"fang null setter genuinely clears owner");c.fang.setOwner(c.source);}
   case ACCOUNT_RESET -> c.account.resetExtendedAccount();
   default -> {}
  }});
  f.at(29,()->{Case c=f.cases.stream().filter(v->v.mode==Mode.DEAD_SOURCE).findFirst().orElseThrow();h.assertTrue(c.source.isRemoved()&&c.source.getRemovalReason()==Entity.RemovalReason.KILLED,"source completed natural death before delayed fang");h.assertTrue(c.fang.getOwner()==c.source,"native fang retains original dead caster object");});
  f.at(38,()->{for(Case c:f.cases)if(c.mode==Mode.FAKE_MAGIC)c.target.hurt(h.getLevel().damageSources().indirectMagic(c.source,c.source),1000);});
  f.at(48,()->{for(Case c:f.cases){boolean paid=c.mode==Mode.LIVE||c.mode==Mode.DEAD_SOURCE;h.assertTrue(c.credits==(paid?1:0)&&c.account.ore==(paid?101:100),"exact original fang or rejected authority: "+c.mode);
    h.assertTrue(c.mode==Mode.LATE_CANCEL?c.target.isAlive():c.target.isDeadOrDying(),"unchanged native actual damage/cancel: "+c.mode);
    h.assertTrue(c.scope==(paid||c.mode==Mode.LATE_CANCEL),"exact scope validity follows carrier/source evidence: "+c.mode);
   }System.out.println("NATIVE_FANG_KILL_RUNTIME_PASS live dead_source discard source_aba fang_aba null_aba reset fake_magic late_cancel");f.clean();h.succeed();});
 }
}
