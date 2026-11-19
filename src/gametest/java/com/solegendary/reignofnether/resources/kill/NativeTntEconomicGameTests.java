package com.solegendary.reignofnether.resources.kill;
import com.solegendary.reignofnether.entities.*;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("reignofnether_native_tnt_economy") @PrefixGameTestTemplate(false)
public final class NativeTntEconomicGameTests {
 static final List<NativeTntEconomicSubscriber> ACTIVE=new ArrayList<>();
 @AfterBatch(batch="native_tnt_economy") public static void cleanupBatch(net.minecraft.server.level.ServerLevel level){for(NativeTntEconomicSubscriber f:new ArrayList<>(ACTIVE))f.clean();}
 static final String PREFIX="NativeTntEconomic_";
 enum Mode { NATIVE, DEAD_CASTER, CASTER_ABA, UNKNOWN_PRIMED, CANCEL }
 static final class Case {Mode mode;Mob source,victim;Resources account;ThrowableTntProjectile projectile;AdjustablePrimedTnt tnt;boolean handoff,sourceDeath;int credits;}
 static final class NativeTntEconomicSubscriber {
  final GameTestHelper h;final List<Case> cases=new ArrayList<>();final List<Entity> entities=new ArrayList<>();final Set<ChunkPos> tickets=new HashSet<>();
  NativeTntEconomicSubscriber(GameTestHelper h){this.h=h;ACTIVE.add(this);}
  Resources account(String owner){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,Factions.PIGLINS));Resources r=new Resources(owner,100,100,100);ResourcesServerEvents.resourcesList.add(r);return r;}
  <T extends Mob>T spawn(EntityType<T> type,String owner,int x,int z){T e=type.create(h.getLevel());((Unit)e).setOwnerName(owner);e.setNoAi(true);BlockPos p=h.absolutePos(new BlockPos(x,2,z));ChunkPos c=new ChunkPos(p);if(!h.getLevel().getForcedChunks().contains(c.toLong())){h.getLevel().setChunkForced(c.x,c.z,true);tickets.add(c);}for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)h.getLevel().setBlock(p.offset(dx,-1,dz),Blocks.STONE.defaultBlockState(),2);e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);h.assertTrue(h.getLevel().addFreshEntity(e),"genuine native RTS unit");entities.add(e);return e;}
  @SubscribeEvent public void join(EntityJoinLevelEvent e){if(e.getEntity() instanceof AdjustablePrimedTnt t)for(Case c:cases)if(t.getOwner()==c.source&&c.tnt==null){c.tnt=t;entities.add(t);c.handoff=Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(s->s.getClassName().equals("com.solegendary.reignofnether.entities.ThrowableTntProjectile")&&s.getMethodName().equals("onHitBlock"));h.assertTrue(t.getFuse()==40,"native unshortened forty-tick TNT fuse");}}
  @SubscribeEvent public void start(ExplosionEvent.Start e){for(Case c:cases)if(c.mode==Mode.CANCEL&&e.getExplosion().getExploder()==c.tnt)e.setCanceled(true);}
  @SubscribeEvent public void death(LivingDeathEvent e){for(Case c:cases)if(e.getEntity()==c.source)c.sourceDeath=true;}
  @SubscribeEvent(priority=EventPriority.HIGHEST) public void credit(ConfirmedRtsKillEvent e){for(Case c:cases)if(e.snapshot().victimId().equals(c.victim.getUUID())&&e.tryClaim(a->ResourceTransaction.creditIncome(h.getLevel().getServer(),a,a.accountEpoch,a.revision,new ResourceTransaction.Cost(0,0,1)).applied()))c.credits++;}
  void clean(){ACTIVE.remove(this);MinecraftForge.EVENT_BUS.unregister(this);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));entities.forEach(Entity::discard);for(ChunkPos c:tickets)h.getLevel().setChunkForced(c.x,c.z,false);tickets.clear();}
 }
 @GameTest(templateNamespace="reignofnether_native_tnt_economy",template="ore_mine_test_arena",batch="native_tnt_economy",timeoutTicks=160)
 public static void realThrowableToPrimedTransferDoesNotInventUnknownOrNewOwnerValue(GameTestHelper h){
  NativeTntEconomicSubscriber f=new NativeTntEconomicSubscriber(h);MinecraftForge.EVENT_BUS.register(f);
  for(Mode mode:Mode.values()){Case c=new Case();c.mode=mode;String owner=PREFIX+mode,enemy=owner+"_Enemy";c.account=f.account(owner);f.account(enemy);int z=5+mode.ordinal()*14;c.source=f.spawn(EntityRegistrar.PIGLIN_MERCHANT_UNIT.get(),owner,3,z);c.victim=f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),enemy,19,z+1);c.victim.setHealth(1);f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),owner,3,z+3);f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),enemy,30,z+3);f.cases.add(c);
   if(mode==Mode.UNKNOWN_PRIMED){AdjustablePrimedTnt t=new AdjustablePrimedTnt(h.getLevel(),c.victim.getX(),c.victim.getY()+.3,c.victim.getZ(),2,c.source);h.assertTrue(h.getLevel().addFreshEntity(t),"owner-labeled but unarmed TNT actually enters level");}
   else {c.projectile=new ThrowableTntProjectile(h.getLevel(),c.source,2);BlockPos p=h.absolutePos(new BlockPos(18,2,z));c.projectile.setPos(p.getX()+.5,p.getY()+.7,p.getZ()+.5);c.projectile.setNoGravity(true);c.projectile.setDeltaMovement(new Vec3(1.5,0,0));for(int y=0;y<3;y++)h.getLevel().setBlock(p.offset(2,y,0),Blocks.STONE.defaultBlockState(),2);h.assertTrue(h.getLevel().addFreshEntity(c.projectile),"real throwable launch");f.entities.add(c.projectile);}
  }
  h.startSequence().thenWaitUntil(()->{for(Case c:f.cases)h.assertTrue(c.tnt!=null&&c.tnt.isAddedToWorld()&&!c.tnt.isRemoved(),"physical block hit really constructed and published primed TNT");})
   .thenExecute(()->{for(Case c:f.cases){h.assertTrue(c.handoff==(c.mode!=Mode.UNKNOWN_PRIMED),"only genuine native handoff carries provenance");if(c.mode==Mode.DEAD_CASTER){c.source.setHealth(1);c.source.hurt(h.getLevel().damageSources().genericKill(),1000);h.assertTrue(c.sourceDeath&&c.source.isDeadOrDying(),"caster genuinely died after actual handoff, not discarded");}if(c.mode==Mode.CASTER_ABA){String owner=((Unit)c.source).getOwnerName();((Unit)c.source).setOwnerName(PREFIX+"Transient");((Unit)c.source).setOwnerName(owner);}}})
   .thenWaitUntil(()->{for(Case c:f.cases)h.assertTrue(c.tnt.isRemoved(),"native forty-tick fuse and original TNT explosion/removal complete");})
   .thenIdle(3).thenExecute(()->{try{for(Case c:f.cases){boolean paid=c.mode==Mode.NATIVE||c.mode==Mode.DEAD_CASTER;h.assertTrue(c.credits==(paid?1:0)&&c.account.ore==(paid?101:100),"true transferred launch only, no ABA/owner-hint/cancel award: "+c.mode);h.assertTrue(c.mode==Mode.CANCEL?c.victim.isAlive():c.victim.isDeadOrDying(),"native blast damage/cancellation unchanged: "+c.mode);}System.out.println("NATIVE_TNT_ECONOMIC_RUNTIME_PASS actual_block_handoff full_fuse dead_caster source_aba unknown_primed cancel");}finally{f.clean();}}).thenSucceed();
 }
}
