package com.solegendary.reignofnether.resources.kill;
import com.solegendary.reignofnether.entities.*;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.util.*;
/** Physical native carriers, not ability-rank/mana/UI tests. Controlled Forge cancels isolate their distinct existing hurt branches. */
@GameTestHolder("reignofnether_native_spell_carriers") @PrefixGameTestTemplate(false)
public final class NativeSpellCarrierEconomicGameTests {
 static final List<NativeSpellCarrierEconomicSubscriber> ACTIVE=new ArrayList<>();
 @AfterBatch(batch="native_spell_carriers") public static void cleanupBatch(net.minecraft.server.level.ServerLevel level){for(NativeSpellCarrierEconomicSubscriber f:new ArrayList<>(ACTIVE))f.clean();}
 static final String PREFIX="NativeSpellCarrier_";
 enum Mode { MOLTEN, MOLTEN_SOURCE_ABA, MOLTEN_LATE_OWNER, WRAITH, WRAITH_SOURCE_ABA, WRAITH_LATE_OWNER }
 static final class Case {Mode mode;Mob source,victim,burst;Projectile carrier;Resources account;int primaryCredits,burstCredits;boolean primaryExplosion,primaryGeneric,burstProjectile;float genericAmount;boolean molten(){return mode.ordinal()<3;}boolean positive(){return mode==Mode.MOLTEN||mode==Mode.WRAITH;}}
 static final class NativeSpellCarrierEconomicSubscriber {
  final GameTestHelper h;final List<Case> cases=new ArrayList<>();final List<Entity> entities=new ArrayList<>();final Set<ChunkPos> chunks=new HashSet<>();
  NativeSpellCarrierEconomicSubscriber(GameTestHelper h){this.h=h;ACTIVE.add(this);}
  Resources account(String owner){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,Factions.PIGLINS));Resources r=new Resources(owner,100,100,100);ResourcesServerEvents.resourcesList.add(r);return r;}
  <T extends Mob>T spawn(EntityType<T> type,String owner,int x,int z){T e=type.create(h.getLevel());((Unit)e).setOwnerName(owner);e.setNoAi(true);BlockPos p=h.absolutePos(new BlockPos(x,2,z));ChunkPos c=new ChunkPos(p);if(!h.getLevel().getForcedChunks().contains(c.toLong())){h.getLevel().setChunkForced(c.x,c.z,true);chunks.add(c);}for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++)h.getLevel().setBlock(p.offset(dx,-1,dz),Blocks.STONE.defaultBlockState(),2);e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);h.assertTrue(h.getLevel().addFreshEntity(e),"real native RTS body");entities.add(e);return e;}
  @SubscribeEvent(priority=EventPriority.LOWEST) public void isolateBranches(LivingAttackEvent e){for(Case c:cases){if(c.molten()&&e.getEntity()==c.burst&&e.getSource().is(DamageTypeTags.IS_EXPLOSION))e.setCanceled(true);if(!c.molten()&&e.getEntity()==c.victim&&e.getSource().is(DamageTypeTags.IS_PROJECTILE))e.setCanceled(true);}}
  @SubscribeEvent(priority=EventPriority.LOWEST) public void amount(LivingDamageEvent e){for(Case c:cases)if(!c.molten()&&e.getEntity()==c.victim&&e.getSource().getEntity()==null&&!e.getSource().is(DamageTypeTags.IS_PROJECTILE))c.genericAmount=e.getAmount();}
  @SubscribeEvent public void death(LivingDeathEvent e){for(Case c:cases){if(e.getEntity()==c.victim){c.primaryExplosion=e.getSource().is(DamageTypeTags.IS_EXPLOSION);c.primaryGeneric=e.getSource().getEntity()==null&&!e.getSource().is(DamageTypeTags.IS_PROJECTILE)&&!c.primaryExplosion;}if(e.getEntity()==c.burst)c.burstProjectile=e.getSource().is(DamageTypeTags.IS_PROJECTILE)&&e.getSource().getDirectEntity()==c.carrier;}}
  @SubscribeEvent(priority=EventPriority.HIGHEST) public void credit(ConfirmedRtsKillEvent e){for(Case c:cases){boolean primary=e.snapshot().victimId().equals(c.victim.getUUID()),burst=e.snapshot().victimId().equals(c.burst.getUUID());if((primary||burst)&&e.tryClaim(a->ResourceTransaction.creditIncome(h.getLevel().getServer(),a,a.accountEpoch,a.revision,new ResourceTransaction.Cost(0,0,1)).applied())){if(primary)c.primaryCredits++;else c.burstCredits++;}}}
  void clean(){ACTIVE.remove(this);MinecraftForge.EVENT_BUS.unregister(this);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));entities.forEach(Entity::discard);for(ChunkPos c:chunks)h.getLevel().setChunkForced(c.x,c.z,false);chunks.clear();}
 }
 @GameTest(templateNamespace="reignofnether_native_spell_carriers",template="ore_mine_test_arena",batch="native_spell_carriers",timeoutTicks=110)
 public static void actualMoltenExplosionBurstAndWraithGenericCollisionRequireOriginalLaunch(GameTestHelper h){
  NativeSpellCarrierEconomicSubscriber f=new NativeSpellCarrierEconomicSubscriber(h);MinecraftForge.EVENT_BUS.register(f);
  for(Mode mode:Mode.values()){Case c=new Case();c.mode=mode;String owner=PREFIX+mode,enemy=owner+"_Enemy";c.account=f.account(owner);f.account(enemy);int z=5+mode.ordinal()*14;c.source=c.molten()?f.spawn(EntityRegistrar.WILDFIRE_UNIT.get(),owner,3,z):f.spawn(EntityRegistrar.WRETCHED_WRAITH_UNIT.get(),owner,3,z);c.victim=f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),enemy,20,z);c.burst=f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),enemy,c.molten()?21:35,z+2);c.victim.setHealth(1);c.burst.setHealth(.25f);f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),owner,3,z+3);f.spawn(EntityRegistrar.VILLAGER_UNIT.get(),enemy,35,z+4);
   boolean late=mode==Mode.MOLTEN_LATE_OWNER||mode==Mode.WRAITH_LATE_OWNER;
   if(c.molten())c.carrier=late?new MoltenBombProjectile(EntityRegistrar.MOLTEN_BOMB_PROJECTILE.get(),h.getLevel()):new MoltenBombProjectile(h.getLevel(),c.source,0,0,0);
   else {c.carrier=new WraithSnowball(EntityRegistrar.WRAITH_SNOWBALL.get(),h.getLevel());if(!late)c.carrier.setOwner(c.source);}
   c.carrier.setNoGravity(true);c.carrier.setPos(c.source.getX(),c.source.getY()+8,c.source.getZ());c.carrier.setDeltaMovement(Vec3.ZERO);h.assertTrue(h.getLevel().addFreshEntity(c.carrier),"actual first join before any late owner mutation");f.entities.add(c.carrier);if(late)c.carrier.setOwner(c.source);f.cases.add(c);
  }
  h.startSequence().thenWaitUntil(()->{for(Case c:f.cases)h.assertTrue(c.source.tickCount>=3&&c.victim.tickCount>=3&&c.carrier.tickCount>=3,"real ticking native sources/targets/carriers before launch controls");})
   .thenExecute(()->{for(Case c:f.cases){if(c.mode==Mode.MOLTEN_SOURCE_ABA||c.mode==Mode.WRAITH_SOURCE_ABA){String owner=((Unit)c.source).getOwnerName();((Unit)c.source).setOwnerName(PREFIX+"Transient");((Unit)c.source).setOwnerName(owner);}c.carrier.setPos(c.victim.getX()-1.2,c.victim.getY()+.7,c.victim.getZ());c.carrier.setDeltaMovement(new Vec3(1.5,0,0));}})
   .thenWaitUntil(()->{for(Case c:f.cases)h.assertTrue(c.carrier.isRemoved(),"real native physical collision and own detonation/discard completed");})
   .thenIdle(3).thenExecute(()->{try{for(Case c:f.cases){h.assertTrue(c.victim.isDeadOrDying(),"actual carrier branch killed victim: "+c.mode);h.assertTrue(c.primaryCredits==(c.positive()?1:0),"only valid first launch owner gets primary value: "+c.mode);if(c.molten()){h.assertTrue(c.primaryExplosion&&c.burst.isDeadOrDying()&&c.burstProjectile,"actual molten explosion then separate mobProjectile burst; controlled explosion cancel isolates burst target");h.assertTrue(c.burstCredits==(c.positive()?1:0),"native post-explosion burst shares original launch only");}else{h.assertTrue(c.primaryGeneric&&Math.abs(c.genericAmount-5)<.001,"actual Wraith fixed5 generic branch with no DamageSource owner; superclass projectile hurt canceled only in fixture");h.assertTrue(c.burst.isAlive()&&c.burstCredits==0,"outside Wraith collision unaffected");}int expected=100+(c.positive()?(c.molten()?2:1):0);h.assertTrue(c.account.ore==expected,"exact native carrier ledger "+c.mode);}System.out.println("NATIVE_SPELL_CARRIER_ECONOMIC_RUNTIME_PASS molten_explosion molten_actual_burst wraith_generic source_aba late_owner_no_launch");}finally{f.clean();}}).thenSucceed();
 }
}
