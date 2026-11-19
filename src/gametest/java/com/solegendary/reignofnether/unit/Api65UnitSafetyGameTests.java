package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.PortalPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.registrars.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.unit.goals.FlyingUsePortalGoal;
import com.solegendary.reignofnether.unit.interfaces.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Behavior regressions intentionally use API64 entry points only: this file can overlay the frozen baseline. */
@GameTestHolder("api65_units") @PrefixGameTestTemplate(false)
public final class Api65UnitSafetyGameTests {
    private static final String BATCH="api65_units";
    private static <T extends Mob> T publish(GameTestHelper h,T mob,String suffix) {
        ((Unit)mob).setOwnerName("Api65Unit_"+suffix);mob.setNoAi(true);mob.setNoGravity(true);mob.setPersistenceRequired();
        var p=h.absolutePos(new BlockPos(5,4,5));mob.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);
        h.assertTrue(h.getLevel().addFreshEntity(mob),"registered native body published");return mob;
    }
    private static Ability ability(Unit unit,UnitAction action){return unit.getAbilities().get().stream().filter(a->a.action==action).findFirst().orElseThrow();}
    private static void command(GameTestHelper h,Unit unit,UnitAction action,int target){new UnitActionItem(unit.getOwnerName(),action,target,new int[]{((Entity)unit).getId()},((Entity)unit).blockPosition(),BlockPos.ZERO).action(h.getLevel());}
    private static void eq(GameTestHelper h,float actual,float wanted,String why){h.assertTrue(Math.abs(actual-wanted)<.001,why+" actual="+actual+" expected="+wanted);}
    public static final class DamageListener {
        final LivingEntity victim; final boolean cancel; int seen;
        DamageListener(LivingEntity victim,boolean cancel){this.victim=victim;this.cancel=cancel;}
        @SubscribeEvent(priority=EventPriority.LOWEST) public void damage(LivingDamageEvent e){if(e.getEntity()==victim){seen++;if(cancel)e.setCanceled(true);else e.setAmount(2);}}
    }
    private static void finalDamage(GameTestHelper h,boolean cancel){
        var attacker=publish(h,EntityRegistrar.ZOMBIE_UNIT.get().create(h.getLevel()),"DamageSource"+cancel);
        var victim=publish(h,EntityRegistrar.VILLAGER_UNIT.get().create(h.getLevel()),"DamageVictim"+cancel);
        var listener=new DamageListener(victim,cancel);MinecraftForge.EVENT_BUS.register(listener);
        try {attacker.getAttribute(AttributeRegistrar.ATTACK_DAMAGE.get()).setBaseValue(12);attacker.getAttribute(AttributeRegistrar.CRITICAL_HIT_CHANCE.get()).setBaseValue(0);
            victim.getAttribute(Attributes.ARMOR).setBaseValue(0);victim.setAbsorptionAmount(0);float before=victim.getHealth();
            victim.hurt(attacker.damageSources().mobAttack(attacker),12);
            h.assertTrue(listener.seen==1,"real native damage reaches final Forge event once");eq(h,victim.getHealth(),before-(cancel?0:2),"final event is authoritative for unmanaged native hit");
        }finally{MinecraftForge.EVENT_BUS.unregister(listener);attacker.discard();victim.discard();}h.succeed();
    }
    @GameTest(templateNamespace="api65_units",template="ore_mine_test_arena",batch=BATCH) public static void finalDamageCancellationIsHonored(GameTestHelper h){finalDamage(h,true);}
    @GameTest(templateNamespace="api65_units",template="ore_mine_test_arena",batch=BATCH) public static void finalDamageAmountIsHonored(GameTestHelper h){finalDamage(h,false);}
    @GameTest(templateNamespace="api65_units",template="ore_mine_test_arena",batch=BATCH)
    public static void conversionRetiresOldBodyWithoutAnyClientAck(GameTestHelper h){
        var old=publish(h,EntityRegistrar.SKELETON_UNIT.get().create(h.getLevel()),"OfflineConversion");LivingEntity replacement=null;
        try {old.getItems().add(new ItemStack(Items.OAK_LOG,3));replacement=old.convertToUnit(EntityRegistrar.STRAY_UNIT.get());
            h.assertTrue(replacement!=null&&h.getLevel().getEntity(replacement.getUUID())==replacement,"replacement successfully published");
            h.assertTrue(old.isRemoved()&&h.getLevel().getEntity(old.getUUID())!=old,"old body retired by server, no network ACK");
            h.assertTrue(((Unit)replacement).getItems().stream().mapToInt(ItemStack::getCount).sum()==3,"cargo transferred exactly once");
        }finally{old.discard();if(replacement!=null)replacement.discard();}h.succeed();
    }
    public static final class RejectStray {
        final String owner;RejectStray(String owner){this.owner=owner;}
        @SubscribeEvent public void join(EntityJoinLevelEvent e){if(e.getEntity().getType()==EntityRegistrar.STRAY_UNIT.get()&&e.getEntity() instanceof Unit u&&u.getOwnerName().equals(owner))e.setCanceled(true);}
    }
    @GameTest(templateNamespace="api65_units",template="ore_mine_test_arena",batch=BATCH)
    public static void rejectedConversionKeepsOriginalAndCargo(GameTestHelper h){
        var old=publish(h,EntityRegistrar.SKELETON_UNIT.get().create(h.getLevel()),"RejectedConversion");var listener=new RejectStray(old.getOwnerName());MinecraftForge.EVENT_BUS.register(listener);
        try {old.getItems().add(new ItemStack(Items.OAK_LOG,3));var replacement=old.convertToUnit(EntityRegistrar.STRAY_UNIT.get());
            h.assertTrue(replacement==null,"a rejected publication is a failed conversion");h.assertTrue(old.isAlive()&&h.getLevel().getEntity(old.getUUID())==old&&old.getItems().get(0).getCount()==3,"failed conversion leaves original body and cargo untouched");
        }finally{MinecraftForge.EVENT_BUS.unregister(listener);old.discard();}h.succeed();
    }
    @GameTest(templateNamespace="api65_units",template="ore_mine_test_arena",batch=BATCH)
    public static void nonLivingAbilityTargetIsRejectedWithoutException(GameTestHelper h){
        var witch=publish(h,EntityRegistrar.WITCH_UNIT.get().create(h.getLevel()),"BadTarget");var item=new ItemEntity(h.getLevel(),witch.getX(),witch.getY(),witch.getZ(),new ItemStack(Items.STONE));h.getLevel().addFreshEntity(item);
        try {ResearchServerEvents.addResearch(witch.getOwnerName(),ProductionItems.RESEARCH_HEALING_POTIONS);command(h,witch,UnitAction.THROW_HEALING_POTION,item.getId());
            h.assertTrue(witch.getThrowPotionGoal().getMoveTarget()==null,"nonliving ID does not become a ground command");eq(h,witch.getCooldown(ability(witch,UnitAction.THROW_HEALING_POTION)),0,"rejected target consumes no cooldown");
        }finally{item.discard();witch.discard();ResearchServerEvents.removeAllResearchFor(witch.getOwnerName());}h.succeed();
    }
    @GameTest(templateNamespace="api65_units",template="ore_mine_test_arena",batch=BATCH)
    public static void vanishedAbilityTargetIsRejectedWithoutException(GameTestHelper h){
        var blaze=publish(h,EntityRegistrar.BLAZE_UNIT.get().create(h.getLevel()),"MissingTarget");var target=publish(h,EntityRegistrar.VILLAGER_UNIT.get().create(h.getLevel()),"Vanished");int targetId=target.getId();target.discard();
        try {ResearchServerEvents.addResearch(blaze.getOwnerName(),ProductionItems.RESEARCH_BLAZE_FIREWALL);command(h,blaze,UnitAction.SHOOT_FIREWALL,targetId);eq(h,blaze.getCooldown(ability(blaze,UnitAction.SHOOT_FIREWALL)),0,"removed target consumes no cooldown");
        }finally{blaze.discard();ResearchServerEvents.removeAllResearchFor(blaze.getOwnerName());}h.succeed();
    }
    @GameTest(templateNamespace="api65_units",template="ore_mine_test_arena",batch=BATCH)
    public static void unlearntAvatarCannotStartCasting(GameTestHelper h){
        var guard=publish(h,EntityRegistrar.ROYAL_GUARD_UNIT.get().create(h.getLevel()),"UnlearntAvatar");
        try {guard.setMana(100);h.assertTrue(guard.getAvatar().getRank(guard)==0,"fresh hero has unlearnt ultimate");command(h,guard,UnitAction.AVATAR,-1);
            h.assertTrue(!guard.getCastAvatarGoal().isCasting()&&!guard.getAvatarScalingStarted(),"unlearnt ability rejected by server before casting/visual state");eq(h,guard.getMana(),100,"no rejected mana charge");
        }finally{guard.discard();}h.succeed();
    }
    @GameTest(templateNamespace="api65_units",template="ore_mine_test_arena",batch=BATCH)
    public static void unresearchedHealingPotionCannotEnableAutocast(GameTestHelper h){
        var witch=publish(h,EntityRegistrar.WITCH_UNIT.get().create(h.getLevel()),"Unresearched");
        try {var ability=ability(witch,UnitAction.THROW_HEALING_POTION);command(h,witch,UnitAction.THROW_HEALING_POTION_AUTOCAST_ENABLE,-1);h.assertTrue(!ability.isAutocasting(witch),"server refuses unresearched autocast");
        }finally{witch.discard();}h.succeed();
    }
    @GameTest(templateNamespace="api65_units",template="ore_mine_test_arena",batch=BATCH)
    public static void destroyedFlyingPortalCancelsGoalWithoutException(GameTestHelper h){
        var ghast=publish(h,EntityRegistrar.GHAST_UNIT.get().create(h.getLevel()),"DestroyedPortal");
        try {var goal=(FlyingUsePortalGoal)ghast.getUsePortalGoal();var portal=new PortalPlacement(Buildings.PORTAL_TRANSPORT,h.getLevel(),ghast.blockPosition(),Rotation.NONE,ghast.getOwnerName(),new ArrayList<>(),false);
            var field=FlyingUsePortalGoal.class.getDeclaredField("buildingTarget");field.setAccessible(true);field.set(goal,portal);
            // Model the exact boundary after native destruction: goal still holds a portal whose blocks reached zero.
            var moveField=com.solegendary.reignofnether.unit.goals.MoveToTargetBlockGoal.class.getDeclaredField("moveTarget");moveField.setAccessible(true);moveField.set(goal,ghast.blockPosition());h.assertTrue(portal.getBlocksPlaced()==0,"portal has no surviving blocks");goal.tick();h.assertTrue(goal.getBuildingTarget()==null&&goal.getMoveTarget()==null,"destroyed portal cleanly stops the registered Ghast goal");
        }catch(ReflectiveOperationException e){throw new RuntimeException(e);}finally{ghast.discard();}h.succeed();
    }
    @GameTest(templateNamespace="api65_units",template="ore_mine_test_arena",batch=BATCH)
    public static void channelingWraithRejectsInterruptWithoutNoncancelableEventException(GameTestHelper h){
        var wraith=publish(h,EntityRegistrar.WRETCHED_WRAITH_UNIT.get().create(h.getLevel()),"Interrupt");
        try {wraith.blizzard();h.assertTrue(wraith.uninterruptable(),"real native Blizzard channel is active");boolean accepted=wraith.addEffect(new MobEffectInstance(MobEffectRegistrar.STUN.get(),20));
            h.assertTrue(!accepted&&!wraith.hasEffect(MobEffectRegistrar.STUN.get()),"Applicable rejects interrupt before Added; no UnsupportedOperationException");
        }finally{wraith.discard();}h.succeed();
    }
}
