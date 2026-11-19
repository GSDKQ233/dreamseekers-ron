package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.ItemShopPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.hero.HeroExperienceOrb;
import com.solegendary.reignofnether.items.*;
import com.solegendary.reignofnether.registrars.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.interfaces.HeroUnit;
import com.solegendary.reignofnether.unit.units.monsters.*;
import com.solegendary.reignofnether.unit.units.villagers.RoyalGuardUnit;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** API64-compatible behavioral probes. Default free heroes test infection/souls, not imaginary
 * default bounty; the separate configured-cost case temporarily supplies a genuine cost of 100. */
@GameTestHolder("api65_death") @PrefixGameTestTemplate(false)
public final class Api65DeathGameTests {
    private static final String TEMPLATE="ore_mine_test_arena";
    private static final Set<C> OPEN=Collections.newSetFromMap(new IdentityHashMap<>());
    public static final class LateCancel {
        final LivingEntity target; boolean enabled=true;
        LateCancel(LivingEntity target){this.target=target;}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
        public void cancel(LivingDeathEvent event){if(enabled&&event.getEntity()==target){event.setCanceled(true);target.setHealth(target.getMaxHealth()/2);}}
    }
    private static final class C implements AutoCloseable {
        final GameTestHelper h; final Api65EconomyGameTests.F victim,attacker;
        final RoyalGuardUnit hero,collector; final SlimeUnit slime; final NecromancerUnit necro;
        LateCancel cancel;
        C(GameTestHelper h,String name)throws Exception{
            this.h=h;victim=new Api65EconomyGameTests.F(h,"dv"+name);attacker=new Api65EconomyGameTests.F(h,"da"+name);
            OPEN.add(this);
            ItemShopPlacement shop=(ItemShopPlacement)victim.install(Buildings.VILLAGER_MARKET);hero=victim.hero(shop);
            slime=EntityRegistrar.SLIME_UNIT.get().create(h.getLevel());slime.setOwnerName(attacker.owner);slime.setNoAi(true);slime.setSize(2,true);
            slime.moveTo(hero.getX()+2,hero.getY(),hero.getZ());h.assertTrue(h.getLevel().addFreshEntity(slime),"real slime published");attacker.entities.add(slime);
            ItemStack sword=new ItemStack(Items.DIAMOND_SWORD);sword.enchant(Enchantments.MOB_LOOTING,1);slime.setItemSlot(EquipmentSlot.MAINHAND,sword);
            ResearchServerEvents.addResearch(attacker.owner,ProductionItems.RESEARCH_SLIME_CONVERSION);
            collector=EntityRegistrar.ROYAL_GUARD_UNIT.get().create(h.getLevel());collector.setOwnerName(attacker.owner);collector.setNoAi(true);collector.setCanPickUpLoot(true);
            collector.moveTo(hero.getX()+3,hero.getY(),hero.getZ());h.assertTrue(h.getLevel().addFreshEntity(collector),"real collector hero published");attacker.entities.add(collector);
            ((UnitInventory)collector).set(0,UnitItems.SOUL_COLLECTOR.getNewItemStack());collector.setMana(0);
            necro=EntityRegistrar.NECROMANCER_UNIT.get().create(h.getLevel());necro.setOwnerName(attacker.owner);necro.setNoAi(true);
            necro.moveTo(hero.getX()+4,hero.getY(),hero.getZ());h.assertTrue(h.getLevel().addFreshEntity(necro),"real necromancer published");attacker.entities.add(necro);
            necro.getSoulSiphon().setRank(necro,1);necro.getSoulSiphon().updateStatsForRank(necro);necro.souls=0;
        }
        void hit(boolean priced){
            hero.removeEffect(MobEffectRegistrar.INVINCIBLE.get());hero.invulnerableTime=0;hero.setHealth(1);hero.setLastHurtByMob(slime);
            hero.addEffect(new MobEffectInstance(MobEffectRegistrar.SLIME_INFECTED.get(),100,0),slime);
            int f=ResourceCosts.ROYAL_GUARD.food,w=ResourceCosts.ROYAL_GUARD.wood,o=ResourceCosts.ROYAL_GUARD.ore;
            try{
                if(priced){ResourceCosts.ROYAL_GUARD.food=100;ResourceCosts.ROYAL_GUARD.wood=0;ResourceCosts.ROYAL_GUARD.ore=0;}
                hero.hurt(h.getLevel().damageSources().mobAttack(slime),10000);
            }finally{ResourceCosts.ROYAL_GUARD.food=f;ResourceCosts.ROYAL_GUARD.wood=w;ResourceCosts.ROYAL_GUARD.ore=o;}
        }
        long slimes(){return attacker.owned(SlimeUnit.class);}
        void addLateCancel(){cancel=new LateCancel(hero);MinecraftForge.EVENT_BUS.register(cancel);}
        @Override public void close(){
            if(!OPEN.remove(this))return;if(cancel!=null)MinecraftForge.EVENT_BUS.unregister(cancel);
            for(var orb:h.getLevel().getEntitiesOfClass(HeroExperienceOrb.class,hero.getBoundingBox().inflate(20)))orb.discard();
            victim.close();attacker.close();
        }
        void done(){close();h.succeed();}
    }
    @AfterBatch(batch="api65_death_totem")public static void cleanupTotem(ServerLevel l){for(C c:List.copyOf(OPEN))c.close();}
    @AfterBatch(batch="api65_death_late")public static void cleanupLate(ServerLevel l){for(C c:List.copyOf(OPEN))c.close();}
    @AfterBatch(batch="api65_death_fallen")public static void cleanupFallen(ServerLevel l){for(C c:List.copyOf(OPEN))c.close();}
    @AfterBatch(batch="api65_death_creep")public static void cleanupCreep(ServerLevel l){for(C c:List.copyOf(OPEN))c.close();}
    @AfterBatch(batch="api65_death_xp")public static void cleanupXp(ServerLevel l){for(C c:List.copyOf(OPEN))c.close();}

    @GameTest(templateNamespace="api65_death",template=TEMPLATE,batch="api65_death_totem",timeoutTicks=120)
    public static void defaultCostTotemPreventsInfectionSoulsAndManaUntilRealDeath(GameTestHelper h)throws Exception{
        C c=new C(h,"totem");int before=c.attacker.bank.emerald;
        h.assertTrue(ResourceCosts.ROYAL_GUARD.food==0&&ResourceCosts.ROYAL_GUARD.wood==0&&ResourceCosts.ROYAL_GUARD.ore==0,"default free hero does not claim a bounty counterexample");
        ((UnitInventory)c.hero).set(0,UnitItems.TOTEM_OF_UNDYING.getNewItemStack());c.hit(false);h.assertTrue(c.hero.isAlive(),"native inventory totem rescued lethal hit");
        h.startSequence().thenIdle(3).thenExecute(()->{
            h.assertTrue(c.slimes()==1&&c.necro.souls==0&&c.collector.getMana()<3&&c.attacker.bank.emerald==before,"canceled default-cost death creates no infection, souls, collector mana or money");
            c.hit(false);h.assertTrue(c.hero.isDeadOrDying(),"subsequent non-totem lethal hit genuinely dies");
        }).thenIdle(3).thenExecute(()->{
            h.assertTrue(c.slimes()==2&&c.necro.souls>0&&c.collector.getMana()>=13&&c.attacker.bank.emerald==before,"one actual default-cost death applies native infection/souls/mana exactly once, without bounty");
            MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(c.hero,h.getLevel().damageSources().mobAttack(c.slime)));
        }).thenIdle(3).thenExecute(()->{h.assertTrue(c.slimes()==2,"replayed death cannot create a second infection body");c.done();});
    }
    @GameTest(templateNamespace="api65_death",template=TEMPLATE,batch="api65_death_late",timeoutTicks=120)
    public static void lateCancellationDoesNotPayConfiguredBountyAndRealDeathPaysOnce(GameTestHelper h)throws Exception{
        C c=new C(h,"late");c.addLateCancel();int before=c.attacker.bank.emerald;c.hit(true);
        h.assertTrue(c.hero.isAlive(),"LOWEST fixture cancels and restores life");
        h.startSequence().thenIdle(3).thenExecute(()->{
            h.assertTrue(c.slimes()==1&&c.attacker.bank.emerald==before&&c.necro.souls==0,"late canceled configured-cost death causes no native economic/army side effects");
            c.cancel.enabled=false;c.hit(true);
        }).thenIdle(3).thenExecute(()->{
            h.assertTrue(c.slimes()==2&&c.attacker.bank.emerald==before+25,"one configured 100-food death gives exactly 25 legacy Looting bounty and one infection");
            MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(c.hero,h.getLevel().damageSources().mobAttack(c.slime)));
        }).thenIdle(3).thenExecute(()->{h.assertTrue(c.slimes()==2&&c.attacker.bank.emerald==before+25,"death replay pays neither another bounty nor body");c.done();});
    }
    @GameTest(templateNamespace="api65_death",template=TEMPLATE,batch="api65_death_fallen",timeoutTicks=120)
    public static void lateCancelledHeroMustNotCreateFallenRevivalRecord(GameTestHelper h)throws Exception{
        try(C c=new C(h,"fallen")){
            c.addLateCancel();c.hit(false);h.assertTrue(c.hero.isAlive(),"late-cancelled hero alive");
            h.assertTrue(HeroUnit.getFallenHero(false,c.victim.owner,c.hero.getType().getDescriptionId())==null,"final canceled death must not publish fallen hero or revival entitlement");
        }h.succeed();
    }
    @GameTest(templateNamespace="api65_death",template=TEMPLATE,batch="api65_death_xp",timeoutTicks=120)
    public static void lateCancelledHeroMustNotEmitExperienceOrbs(GameTestHelper h)throws Exception{
        try(C c=new C(h,"xp")){
            c.addLateCancel();long before=h.getLevel().getEntitiesOfClass(HeroExperienceOrb.class,c.hero.getBoundingBox().inflate(20)).size();c.hit(false);
            h.assertTrue(c.hero.isAlive(),"late-cancelled hero alive");
            h.assertTrue(h.getLevel().getEntitiesOfClass(HeroExperienceOrb.class,c.hero.getBoundingBox().inflate(20)).size()==before,"final canceled death must not emit enemy hero experience orbs");
        }h.succeed();
    }
    @GameTest(templateNamespace="api65_death",template=TEMPLATE,batch="api65_death_creep",timeoutTicks=120)
    public static void lateCancelledNeutralDeathCannotConsumeCreepDropQueue(GameTestHelper h)throws Exception{
        C c=new C(h,"creep");c.hero.setOwnerName("");c.addLateCancel();
        var player=com.solegendary.reignofnether.player.PlayerServerEvents.getRTSPlayer(c.attacker.owner);
        player.creepScore=10;player.itemsDropped=0;player.itemDropQueue.clear();player.itemDropQueue.add(UnitItems.BROADSWORD);
        c.hit(false);h.assertTrue(c.hero.isAlive(),"neutral hero remains alive after final cancellation");
        h.startSequence().thenIdle(3).thenExecute(()->{
            h.assertTrue(player.creepScore==10&&player.itemsDropped==0&&player.itemDropQueue.size()==1,
                    "late canceled neutral death cannot advance creep score, consume queue or grant item drop");
            c.cancel.enabled=false;c.hit(false);
        }).thenIdle(3).thenExecute(()->{
            h.assertTrue(player.creepScore==18&&player.itemsDropped==1&&player.itemDropQueue.isEmpty(),
                    "one genuine 5-pop neutral death adds 8 creep score and crosses first drop threshold once");
            MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(c.hero,h.getLevel().damageSources().mobAttack(c.slime)));
        }).thenIdle(3).thenExecute(()->{
            h.assertTrue(player.creepScore==18&&player.itemsDropped==1,"replayed neutral death cannot earn another creep score or drop");c.done();
        });
    }

}
