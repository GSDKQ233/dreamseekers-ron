package com.solegendary.reignofnether.building.production;

import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hero.HeroServerEvents;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Real registered recipes, native join vetoes and queue dispatch. Work duration alone is shortened
 * after normal debit; this suite does not remove a failed queue to manufacture successful retry. */
@GameTestHolder("api63_native_production") @PrefixGameTestTemplate(false)
public final class NativeProductionCompletionGameTests {
    static final String TEMPLATE="ore_mine_test_arena";
    static final Set<F> FIXTURES=Collections.newSetFromMap(new IdentityHashMap<>());
    static final class F {
        final GameTestHelper h;final String owner;final Resources account;final ProductionPlacement town,shrine;
        final List<BuildingPlacement> buildings=new ArrayList<>();final List<Object> listeners=new ArrayList<>();
        final int oldHeroLimit;
        F(GameTestHelper h,String name){this.h=h;owner="Api63Native_"+name;oldHeroLimit=h.getLevel().getGameRules().getInt(GameRuleRegistrar.ALLOWED_HEROES);
            h.getLevel().getGameRules().getRule(GameRuleRegistrar.ALLOWED_HEROES).set(1,h.getLevel().getServer());
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,Factions.VILLAGERS));account=new Resources(owner,10000,10000,10000);ResourcesServerEvents.resourcesList.add(account);
            town=install(Buildings.TOWN_CENTRE,50,50);shrine=install(Buildings.SHRINE_OF_PROSPERITY,90,50);FIXTURES.add(this);
        }
        ProductionPlacement install(Building type,int x,int z){
            var l=h.getLevel();BlockPos pos=h.absolutePos(new BlockPos(x,1,z));
            for(int dx=-2;dx<30;dx++)for(int dz=-2;dz<30;dz++){l.setBlock(pos.offset(dx,0,dz),Blocks.STONE.defaultBlockState(),2);for(int dy=1;dy<20;dy++)l.setBlock(pos.offset(dx,dy,dz),Blocks.AIR.defaultBlockState(),2);}
            for(BuildingBlock b:BuildingUtils.getAbsoluteBlockData(type.getRelativeBlockData(l),l,pos,Rotation.NONE))l.setBlock(b.getBlockPos(),b.getBlockState(),2);
            var p=(ProductionPlacement)BuildingUtils.getNewBuildingPlacement(type,l,pos,Rotation.NONE,owner,false);BuildingServerEvents.getBuildings().add(p);buildings.add(p);return p;
        }
        void ready(){h.assertTrue(town.isBuilt&&shrine.isBuilt,"real native buildings completed");}
        ActiveProduction start(ProductionPlacement p,ProductionItem item){int f=account.food,w=account.wood,o=account.ore;var cost=item.getCost(false,owner);
            h.assertTrue(p.startProductionItem(item),"actual registered recipe queued");h.assertTrue(account.food==f-cost.food&&account.wood==w-cost.wood&&account.ore==o-cost.ore,"one real training debit");
            var a=p.productionQueue.get(p.productionQueue.size()-1);a.ticksLeft=2;return a;}
        long count(EntityType<?> type){return UnitServerEvents.getAllUnits().stream().filter(e->e.isAlive()&&!e.isRemoved()&&e.getType()==type&&e instanceof Unit u&&u.getOwnerName().equals(owner)).count();}
        void score(int total,int worker){var scores=PlayerServerEvents.getRTSPlayer(owner).scores.getScoreList();h.assertTrue(scores.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED)==total&&scores.get(RTSPlayerScoresEnum.WORKER_UNITS_PRODUCED)==worker&&scores.get(RTSPlayerScoresEnum.MILITARY_UNITS_PRODUCED)==total-worker,"only successful production scores once: "+scores);}
        Join join(ProductionPlacement p,EntityType<?> type,int mode){var j=new Join(this,p,type,mode);MinecraftForge.EVENT_BUS.register(j);listeners.add(j);return j;}
        void close(){listeners.forEach(MinecraftForge.EVENT_BUS::unregister);for(var e:List.copyOf(UnitServerEvents.getAllUnits()))if(e instanceof Unit u&&u.getOwnerName().equals(owner))e.discard();
            for(var b:buildings){b.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(b);}HeroServerEvents.fallenHeroes.removeIf(s->s.ownerName.equals(owner));PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(owner));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(owner));h.getLevel().getGameRules().getRule(GameRuleRegistrar.ALLOWED_HEROES).set(oldHeroLimit,h.getLevel().getServer());FIXTURES.remove(this);}
        void done(){close();h.succeed();}
    }
    static final class Join {
        final F f;final ProductionPlacement host;final EntityType<?> type;final int mode;int calls;boolean enabled=true;
        Join(F f,ProductionPlacement p,EntityType<?> t,int mode){this.f=f;host=p;type=t;this.mode=mode;}
        @SubscribeEvent(priority=EventPriority.LOWEST)public void join(EntityJoinLevelEvent e){if(!enabled||e.getLevel()!=f.h.getLevel()||e.getEntity().getType()!=type)return;
            // Tests run distinct batches; matching exact recipe type is enough within this isolated batch.
            calls++;
            if(mode==0)e.setCanceled(true);
            else if(mode==1){host.cancelProductionItem(host.productionQueue.get(0).item,true);}
            else if(mode==2)f.h.getLevel().getGameRules().getRule(GameRuleRegistrar.ALLOWED_HEROES).set(0,f.h.getLevel().getServer());
        }
    }
    @AfterBatch(batch="native_worker_veto")public static void cleanup(net.minecraft.server.level.ServerLevel level){for(F f:List.copyOf(FIXTURES))f.close();}

    @AfterBatch(batch="native_first_hero") public static void cleanup_native_first_hero(net.minecraft.server.level.ServerLevel level){for(F f:List.copyOf(FIXTURES))f.close();}
    @AfterBatch(batch="native_join_cancel") public static void cleanup_native_join_cancel(net.minecraft.server.level.ServerLevel level){for(F f:List.copyOf(FIXTURES))f.close();}
    @AfterBatch(batch="native_cap_changed") public static void cleanup_native_cap_changed(net.minecraft.server.level.ServerLevel level){for(F f:List.copyOf(FIXTURES))f.close();}
    @AfterBatch(batch="native_revive_veto") public static void cleanup_native_revive_veto(net.minecraft.server.level.ServerLevel level){for(F f:List.copyOf(FIXTURES))f.close();}

    @GameTest(templateNamespace="api63_native_production",template=TEMPLATE,batch="native_first_hero",timeoutTicks=160)
    public static void firstRegisteredHeroStillProducesNormally(GameTestHelper h){F f=new F(h,"first");h.startSequence().thenWaitUntil(f::ready).thenExecute(()->f.start(f.shrine,ProductionItems.ROYAL_GUARD)).thenIdle(8).thenExecute(()->{h.assertTrue(f.count(EntityRegistrar.ROYAL_GUARD_UNIT.get())==1&&f.shrine.productionQueue.isEmpty(),"one normal first registered hero");f.score(1,0);f.done();});}

    @GameTest(templateNamespace="api63_native_production",template=TEMPLATE,batch="native_worker_veto",timeoutTicks=180)
    public static void rejectedWorkerKeepsPaymentQueueAndRetriesOnce(GameTestHelper h){F f=new F(h,"worker");Join j=f.join(f.town,EntityRegistrar.VILLAGER_UNIT.get(),0);final ActiveProduction[] active={null};final int[] paid={0};h.startSequence().thenWaitUntil(f::ready).thenExecute(()->{active[0]=f.start(f.town,ProductionItems.VILLAGER);paid[0]=f.account.food;}).thenIdle(8).thenExecute(()->{h.assertTrue(j.calls>0&&f.count(EntityRegistrar.VILLAGER_UNIT.get())==0&&f.town.productionQueue.contains(active[0])&&!active[0].completed,"veto retains same paid queue with no body");h.assertTrue(f.account.food==paid[0],"veto no extra debit/refund");f.score(0,0);j.enabled=false;}).thenIdle(8).thenExecute(()->{h.assertTrue(f.count(EntityRegistrar.VILLAGER_UNIT.get())==1&&f.town.productionQueue.isEmpty()&&f.account.food==paid[0],"natural retry yields one worker without second payment");f.score(1,1);}).thenIdle(8).thenExecute(()->{f.score(1,1);f.done();});}

    @GameTest(templateNamespace="api63_native_production",template=TEMPLATE,batch="native_join_cancel",timeoutTicks=180)
    public static void cancelDuringJoinRefundsAndRollsBackBirth(GameTestHelper h){F f=new F(h,"cancel");Join j=f.join(f.town,EntityRegistrar.VILLAGER_UNIT.get(),1);h.startSequence().thenWaitUntil(f::ready).thenExecute(()->f.start(f.town,ProductionItems.VILLAGER)).thenIdle(8).thenExecute(()->{h.assertTrue(j.calls==1&&f.town.productionQueue.isEmpty()&&f.count(EntityRegistrar.VILLAGER_UNIT.get())==0,"reentrant cancel removes original queue and rejected birth");h.assertTrue(f.account.food==10000&&f.account.wood==10000&&f.account.ore==10000,"original cancel refund exactly once");f.score(0,0);f.done();});}

    @GameTest(templateNamespace="api63_native_production",template=TEMPLATE,batch="native_cap_changed",timeoutTicks=180)
    public static void heroLimitChangedDuringJoinDoesNotCommit(GameTestHelper h){F f=new F(h,"cap");Join j=f.join(f.shrine,EntityRegistrar.ROYAL_GUARD_UNIT.get(),2);final ActiveProduction[] a={null};h.startSequence().thenWaitUntil(f::ready).thenExecute(()->a[0]=f.start(f.shrine,ProductionItems.ROYAL_GUARD)).thenIdle(8).thenExecute(()->{h.assertTrue(j.calls==1&&f.count(EntityRegistrar.ROYAL_GUARD_UNIT.get())==0&&f.shrine.productionQueue.contains(a[0]),"changed hero limit rolls back native publication and keeps queue");f.score(0,0);j.enabled=false;h.getLevel().getGameRules().getRule(GameRuleRegistrar.ALLOWED_HEROES).set(1,h.getLevel().getServer());}).thenIdle(8).thenExecute(()->{h.assertTrue(f.count(EntityRegistrar.ROYAL_GUARD_UNIT.get())==1&&f.shrine.productionQueue.isEmpty(),"restored legitimate limit allows retry");f.score(1,0);f.done();});}

    @GameTest(templateNamespace="api63_native_production",template=TEMPLATE,batch="native_revive_veto",timeoutTicks=220)
    public static void rejectedRevivalPreservesFallenRecordAndPaidRetry(GameTestHelper h){F f=new F(h,"revive");final LivingEntity[] original={null};final HeroUnitSave[] fallen={null};final ActiveProduction[] a={null};final int[] paid={0};final Join[] veto={null};
        h.startSequence().thenWaitUntil(f::ready).thenExecute(()->{var hero=EntityRegistrar.ROYAL_GUARD_UNIT.get().create(h.getLevel());hero.setOwnerName(f.owner);hero.setNoAi(true);var p=h.absolutePos(new BlockPos(40,2,40));hero.moveTo(p.getX(),p.getY(),p.getZ());h.assertTrue(h.getLevel().addFreshEntity(hero),"original hero published");original[0]=hero;hero.hurt(h.getLevel().damageSources().genericKill(),10000);h.assertTrue(hero.isDeadOrDying(),"real fatal hit creates fallen record");}).thenIdle(25).thenExecute(()->{fallen[0]=HeroUnit.getFallenHero(false,f.owner,EntityRegistrar.ROYAL_GUARD_UNIT.get().getDescriptionId());h.assertTrue(fallen[0]!=null,"native fallen record exists");veto[0]=f.join(f.shrine,EntityRegistrar.ROYAL_GUARD_UNIT.get(),0);a[0]=f.start(f.shrine,ProductionItems.ROYAL_GUARD_REVIVE);paid[0]=f.account.food;}).thenIdle(8).thenExecute(()->{h.assertTrue(veto[0].calls>0&&f.shrine.productionQueue.contains(a[0])&&HeroUnit.getFallenHero(false,f.owner,EntityRegistrar.ROYAL_GUARD_UNIT.get().getDescriptionId())==fallen[0]&&f.count(EntityRegistrar.ROYAL_GUARD_UNIT.get())==0,"rejected revival preserves original fallen record and queue");f.score(0,0);h.assertTrue(f.account.food==paid[0],"failed revival no repeat payment");veto[0].enabled=false;}).thenIdle(8).thenExecute(()->{h.assertTrue(f.count(EntityRegistrar.ROYAL_GUARD_UNIT.get())==1&&HeroUnit.getFallenHero(false,f.owner,EntityRegistrar.ROYAL_GUARD_UNIT.get().getDescriptionId())==null&&f.shrine.productionQueue.isEmpty()&&f.account.food==paid[0],"one accepted revival consumes original record once");f.score(1,0);f.done();});}
    @AfterBatch(batch="native_prebuild")public static void cleanupPrebuild(net.minecraft.server.level.ServerLevel l){for(F f:List.copyOf(FIXTURES))f.close();}
    @GameTest(templateNamespace="api63_native_production",template=TEMPLATE,batch="native_prebuild",timeoutTicks=180)
    public static void permittedPrebuildReadyItemStillCompletes(GameTestHelper h){F f=new F(h,"prebuild");
        var l=h.getLevel();var type=Buildings.SHRINE_OF_PROSPERITY;var pos=h.absolutePos(new BlockPos(130,1,50));
        var abs=BuildingUtils.getAbsoluteBlockData(type.getRelativeBlockData(l),l,pos,Rotation.NONE);
        // A real incomplete shrine: only its native starting block is placed, not a completed building with a toggled flag.
        for(var b:abs)l.setBlock(b.getBlockPos(),Blocks.AIR.defaultBlockState(),2);
        var first=abs.stream().filter(b->!b.getBlockState().isAir()).findFirst().orElseThrow();l.setBlock(first.getBlockPos(),first.getBlockState(),2);
        var partial=(ProductionPlacement)BuildingUtils.getNewBuildingPlacement(type,l,pos,Rotation.NONE,f.owner,false);BuildingServerEvents.getBuildings().add(partial);f.buildings.add(partial);
        h.startSequence().thenWaitUntil(f::ready).thenExecute(()->{h.assertTrue(!partial.isBuilt&&partial.allowProdWhileBuilding,"actual native incomplete shrine permits queued production");var a=f.start(partial,ProductionItems.ROYAL_GUARD);a.ticksLeft=0;partial.tick(l);}).thenIdle(3).thenExecute(()->{h.assertTrue(!partial.isBuilt&&partial.productionQueue.isEmpty()&&f.count(EntityRegistrar.ROYAL_GUARD_UNIT.get())==1,"ready item preserves existing allowed incomplete-host completion");f.score(1,0);f.done();});
    }

    @AfterBatch(batch="native_custom")public static void cleanupCustom(net.minecraft.server.level.ServerLevel l){for(F f:List.copyOf(FIXTURES))f.close();}
    @GameTest(templateNamespace="api63_native_production",template=TEMPLATE,batch="native_custom",timeoutTicks=220)
    public static void creativeCustomHeroKeepsEligibilityButRejectsCancelledBirth(GameTestHelper h){F f=new F(h,"custom");
        var nbt=BuildingBlockData.getBuildingNbt("shrine_of_prosperity",h.getLevel().getServer().getResourceManager());
        var type=new com.solegendary.reignofnether.building.custombuilding.CustomBuilding("api63_custom",new net.minecraft.core.Vec3i(24,20,24),Blocks.STONE,nbt);
        var recipe=new CustomProductionItem(ResourceCost.Unit(23,0,0,2,0),"Api63 custom hero",ProductionItems.ROYAL_GUARD,new ArrayList<>(),true);
        type.productions.add(recipe,com.solegendary.reignofnether.keybinds.Keybindings.abilitySlot1);
        var host=f.install(type,130,50);Join j=f.join(host,EntityRegistrar.ROYAL_GUARD_UNIT.get(),0);final ActiveProduction[] active={null};
        h.startSequence().thenWaitUntil(()->{f.ready();h.assertTrue(host.isBuilt,"actual custom building built");}).thenExecute(()->{h.getLevel().getGameRules().getRule(GameRuleRegistrar.ALLOWED_HEROES).set(0,h.getLevel().getServer());active[0]=f.start(host,recipe);}).thenIdle(8).thenExecute(()->{h.assertTrue(j.calls>0&&host.productionQueue.contains(active[0])&&f.count(EntityRegistrar.ROYAL_GUARD_UNIT.get())==0&&f.account.food==9977,"custom veto keeps exact paid queue and no body");f.score(0,0);j.enabled=false;}).thenIdle(8).thenExecute(()->{h.assertTrue(f.count(EntityRegistrar.ROYAL_GUARD_UNIT.get())==1&&host.productionQueue.isEmpty()&&f.account.food==9977,"custom creative historical eligibility preserved even heroLimit0, paid once");f.score(1,0);f.done();});
    }

    @AfterBatch(batch="native_skull")public static void cleanupSkull(net.minecraft.server.level.ServerLevel l){for(F f:List.copyOf(FIXTURES))f.close();}
    @GameTest(templateNamespace="api63_native_production",template=TEMPLATE,batch="native_skull",timeoutTicks=180)
    public static void upgradedGraveyardStillCompletesStoredSkull(GameTestHelper h){F f=new F(h,"skull");var l=h.getLevel();var type=Buildings.GRAVEYARD;var pos=h.absolutePos(new BlockPos(130,1,50));
        var relative=BuildingBlockData.getBuildingBlocksFromNbt(type.getUpgradedStructureName(1),l);var blocks=BuildingUtils.getAbsoluteBlockData(relative,l,pos,Rotation.NONE);
        for(var b:blocks)l.setBlock(b.getBlockPos(),b.getBlockState(),2);
        var host=new com.solegendary.reignofnether.building.buildings.placements.GraveyardPlacement(type,l,pos,Rotation.NONE,f.owner,blocks,false);BuildingServerEvents.getBuildings().add(host);f.buildings.add(host);
        h.startSequence().thenWaitUntil(()->{f.ready();h.assertTrue(host.isBuilt&&host.getUpgradeLevel()>0,"real upgraded graveyard");}).thenExecute(()->f.start(host,ProductionItems.ZOMBIE)).thenIdle(8).thenExecute(()->{h.assertTrue(host.productionQueue.isEmpty()&&host.getTotalSkulls()==1&&f.count(EntityRegistrar.ZOMBIE_UNIT.get())==0,"native skull branch commits without requiring a live birth");f.score(1,0);f.done();});
    }
}
