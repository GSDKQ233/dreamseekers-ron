package com.dreamseekers;
import com.dreamseekers.skill.*;
import com.dreamseekers.unit.WindKnightUnit;
import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.resources.kill.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.EvokerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.util.*;
/** An actual authorized Wind dash grants a lease, then an actual Evoker attack kills through enhanced fangs. */
@GameTestHolder("dreamseekers_kill_fang") @PrefixGameTestTemplate(false)
public final class EnhancedFangEconomicGameTests {
 static final List<EnhancedFangEconomicSubscriber> ACTIVE=new ArrayList<>();
 @AfterBatch(batch="economic_enhanced_fang") public static void cleanup(net.minecraft.server.level.ServerLevel level){for(EnhancedFangEconomicSubscriber p:List.copyOf(ACTIVE))p.clean();ACTIVE.clear();}
 static final String OWNER="EconomicEnhancedFang_Owner",ENEMY="EconomicEnhancedFang_Enemy";
 static final class EnhancedFangEconomicSubscriber {
  final GameTestHelper h;final List<Entity> entities=new ArrayList<>();final List<EvokerFangs> fangs=new ArrayList<>();WindKnightUnit knight;EvokerUnit caster;Mob dashTarget,victim;Resources account;int credits;boolean enhanced,scope;float actualDamage;
  EnhancedFangEconomicSubscriber(GameTestHelper h){this.h=h;}
  @SubscribeEvent(priority=EventPriority.HIGHEST) public void commandPosition(LivingDamageEvent event){if(event.getEntity()==dashTarget)caster.setPos(knight.position().add(0,0,-2));if(event.getEntity()==victim)actualDamage=event.getAmount();}
  @SubscribeEvent public void join(EntityJoinLevelEvent e){if(e.getEntity() instanceof EvokerFangs fang&&fang.getOwner()==caster){fangs.add(fang);entities.add(fang);}}
  @SubscribeEvent public void death(LivingDeathEvent e){if(e.getEntity()==victim){scope=fangs.stream().anyMatch(f->EconomicFangHooks.hasExactFangHit(f,victim,e.getSource()));enhanced=Arrays.stream(Thread.currentThread().getStackTrace()).anyMatch(s->s.getClassName().equals("com.dreamseekers.skill.WindFangDamage")&&s.getMethodName().equals("hurtIfEnhanced"));}}
  @SubscribeEvent(priority=EventPriority.HIGHEST) public void credit(ConfirmedRtsKillEvent e){if(e.snapshot().victimId().equals(victim.getUUID())&&e.tryClaim(a->ResourceTransaction.creditIncome(h.getLevel().getServer(),a,a.accountEpoch,a.revision,new ResourceTransaction.Cost(0,0,1)).applied()))credits++;}
  <T extends Mob>T spawn(EntityType<T> type,String owner,int x,int z){T mob=type.create(h.getLevel());((Unit)mob).setOwnerName(owner);mob.setNoAi(true);BlockPos pos=h.absolutePos(new BlockPos(x,1,z));mob.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);h.assertTrue(h.getLevel().addFreshEntity(mob),"actual registered entity");entities.add(mob);return mob;}
  void clean(){MinecraftForge.EVENT_BUS.unregister(this);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(OWNER)||p.name.equals(ENEMY));ResourcesServerEvents.resourcesList.removeIf(p->p.ownerName.equals(OWNER)||p.ownerName.equals(ENEMY));entities.forEach(Entity::discard);}
 }
 @GameTest(templateNamespace="dreamseekers_kill_fang",template="unit_isolation_arena",batch="economic_enhanced_fang",timeoutTicks=900)
 public static void realWindCommandThenEnhancedFangConfirmsOneKill(GameTestHelper h){
  EnhancedFangEconomicSubscriber p=new EnhancedFangEconomicSubscriber(h);ACTIVE.add(p);MinecraftForge.EVENT_BUS.register(p);for(String owner:List.of(OWNER,ENEMY)){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));Resources r=new Resources(owner,500,500,500);ResourcesServerEvents.resourcesList.add(r);if(owner.equals(OWNER))p.account=r;}
  BlockPos origin=h.absolutePos(BlockPos.ZERO);for(int x=0;x<24;x++)for(int z=0;z<24;z++){h.getLevel().setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);h.getLevel().setBlock(origin.offset(x,7,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<7;y++)h.getLevel().setBlock(origin.offset(x,y,z),(x==0||x==23||z==0||z==23?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);}
  p.knight=p.spawn(DreamseekersMod.WIND_KNIGHT_UNIT.get(),OWNER,5,20);p.knight.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(10);p.knight.getAttribute(AttributeRegistrar.AGGRO_RANGE.get()).setBaseValue(0);p.knight.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);
  p.dashTarget=p.spawn(EntityRegistrar.VILLAGER_UNIT.get(),ENEMY,11,20);p.dashTarget.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);p.dashTarget.setHealth(200);
  p.caster=p.spawn(EntityRegistrar.EVOKER_UNIT.get(),OWNER,4,4);p.caster.setupEquipmentAndUpgradesServer();p.victim=p.spawn(EntityRegistrar.VILLAGER_UNIT.get(),ENEMY,6,4);p.victim.getAttribute(Attributes.ARMOR).setBaseValue(0);p.victim.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0);p.victim.getAttribute(AttributeRegistrar.MAGIC_DAMAGE_RESIST.get()).setBaseValue(0);p.victim.setHealth(1);
  long[] grounded={Long.MIN_VALUE};h.startSequence().thenWaitUntil(()->h.assertTrue(p.knight.tickCount>3,"real publication ticks"))
   .thenExecute(()->p.knight.setNoAi(false)).thenWaitUntil(()->{if(!p.knight.onGround()||!h.getLevel().noCollision(p.knight)){grounded[0]=Long.MIN_VALUE;h.assertTrue(false,"real ground collision");}long now=h.getLevel().getGameTime();if(grounded[0]==Long.MIN_VALUE)grounded[0]=now;h.assertTrue(now>grounded[0],"two grounded world ticks");})
   .thenExecute(()->{var actor=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.nameUUIDFromBytes(OWNER.getBytes(java.nio.charset.StandardCharsets.UTF_8)),OWNER));var request=new SkillRequest(UnitSkill.WIND_DASH,p.knight.getId(),p.knight.getUUID(),p.knight.rts().skills().revision(),p.dashTarget.getId(),p.dashTarget.getUUID());h.assertTrue(SkillService.request(actor,request)==SkillResult.SUCCESS,"real authenticated Wind dash, not injected buff");})
   .thenWaitUntil(()->h.assertTrue(!WindDash.isActive(p.knight),"real dash resolves"))
   .thenExecute(()->{p.knight.setNoAi(true);h.assertTrue(SkillEffects.hasNamedBuff(p.caster,WindDash.COMMAND_KEY,BuffKind.ATTACK_DAMAGE),"actual ordinary-hit command enhanced native Evoker");BlockPos pos=h.absolutePos(new BlockPos(4,1,4));p.caster.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);p.victim.setPos(p.caster.position().add(1.25,0,0));p.caster.isUsingLineFangs=true;p.caster.performUnitRangedAttack(p.victim,1);})
   .thenIdle(50).thenExecute(()->{try{h.assertTrue(!p.fangs.isEmpty()&&p.victim.isDeadOrDying(),"actual Evoker spawned delayed fangs caused death");h.assertTrue(p.enhanced&&p.scope,"enhanced addon branch retains exact native fang economic witness");h.assertTrue(Math.abs(p.actualDamage-6.6f)<.001,"original native6 plus10 percent Wind modifier unchanged: "+p.actualDamage);h.assertTrue(p.credits==1&&p.account.ore==501&&RtsKillValueClaims.isClaimed(p.victim),"one true enhanced fang kill, no repeated multi-fang value");h.assertTrue(p.account.extra.getOrDefault(new net.minecraft.resources.ResourceLocation("dreamseekers","shadow_energy"),0)==0,"fixture preclaims once; normal faction consumer cannot double-pay");System.out.println("ENHANCED_FANG_ECONOMIC_RUNTIME_PASS real_wind_command actual_evoker_attack native_enhanced_damage_one_claim");}finally{p.clean();}}).thenSucceed();
 }
}
