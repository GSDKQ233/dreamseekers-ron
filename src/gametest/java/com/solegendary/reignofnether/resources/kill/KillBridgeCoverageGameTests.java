package com.solegendary.reignofnether.resources.kill;

import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hero.HeroServerEvents;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Draft genuine runtime coverage, separate from the earlier minimal bridge suite. */
@GameTestHolder("reignofnether_kill_economy")
@PrefixGameTestTemplate(false)
public final class KillBridgeCoverageGameTests {
    static final String PREFIX="KillBridgeExtra_",KILLER=PREFIX+"Killer",VICTIM=PREFIX+"Victim";
    static final class Observer {
        final Set<UUID> watched=new HashSet<>();final List<Entity> entities=new ArrayList<>();final List<BuildingPlacement> buildings=new ArrayList<>();
        final List<ConfirmedRtsKillEvent.Snapshot> snapshots=new ArrayList<>();int committed,lateCalls;UUID lateVictim,vetoVictim,arrowVictim;Arrow actualArrow;boolean exactArrowWitness,nativeProxyWitness,nestedScopeWitness,exceptionScopeWitness;LivingEntity scopeVictim;
        @SubscribeEvent public void credit(ConfirmedRtsKillEvent event){
            if(!watched.contains(event.snapshot().victimId()))return;snapshots.add(event.snapshot());
            if(event.tryClaim(a->ResourceTransaction.creditIncome(net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer(),a,a.accountEpoch,a.revision,new ResourceTransaction.Cost(0,0,1)).applied()))committed++;
        }
        @SubscribeEvent public void witnessActualArrowScope(LivingDeathEvent event){
            if(event.getEntity().getUUID().equals(arrowVictim)){
                exactArrowWitness=RtsProjectileProvenance.hasExactPrimaryHit(actualArrow,event.getEntity(),event.getSource());
                nativeProxyWitness=event.getSource().getDirectEntity()!=actualArrow;
                // Diagnostic-only zero-damage wrappers: do not register a hit provider or alter the real impact.
                var originalSource=event.getSource();var independentSource=scopeVictim.level().damageSources().mobAttack((LivingEntity)actualArrow.getOwner());
                boolean[] inner={false};
                RtsProjectileProvenance.wrapPrimary(actualArrow,scopeVictim,(source,amount)->{
                    // No LivingEntity.hurt was entered: a wrapper alone is not economic authority.
                    inner[0]=!RtsProjectileProvenance.hasExactPrimaryHit(actualArrow,scopeVictim,source)
                            &&!RtsProjectileProvenance.hasExactPrimaryHit(actualArrow,event.getEntity(),originalSource);return false;
                }).hurt(independentSource,0);
                nestedScopeWitness=inner[0]&&independentSource!=originalSource
                        &&RtsProjectileProvenance.hasExactPrimaryHit(actualArrow,event.getEntity(),originalSource);
                boolean caught=false;
                try {RtsProjectileProvenance.wrapPrimary(actualArrow,scopeVictim,(source,amount)->{throw new IllegalStateException("expected scope fixture");}).hurt(independentSource,0);}
                catch(IllegalStateException expected){caught=true;}
                exceptionScopeWitness=caught&&RtsProjectileProvenance.hasExactPrimaryHit(actualArrow,event.getEntity(),originalSource);
            }
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void lateCancellation(LivingDeathEvent event){
            if(event.getEntity().getUUID().equals(lateVictim)){lateCalls++;event.getEntity().setHealth(10);event.setCanceled(true);}
        }
        @SubscribeEvent public void veto(RtsKillValueEligibilityEvent event){if(event.victim().getUUID().equals(vetoVictim))event.setCanceled(true);}
    }
    /** Test-only configured-price scope: normal fatal damage captures a cost snapshot, then configuration is restored before deferred dispatch. */
    static void configuredHeroDeath(GameTestHelper h, LivingEntity hero, LivingEntity attacker) {
        h.assertTrue(UnitServerEvents.getAllUnits().contains(hero)&&UnitServerEvents.getAllUnits().contains(attacker),"real registered hero and killer before fatal scope");
        h.assertTrue(PlayerServerEvents.getRTSPlayer(((Unit)hero).getOwnerName())!=null&&PlayerServerEvents.getRTSPlayer(((Unit)attacker).getOwnerName())!=null,"both real RTS identities before fatal scope");
        int originalFood=ResourceCosts.ROYAL_GUARD.food;
        try {ResourceCosts.ROYAL_GUARD.food=100;hero.setHealth(1);hero.hurt(h.getLevel().damageSources().mobAttack(attacker),1000);}
        finally {ResourceCosts.ROYAL_GUARD.food=originalFood;}
        h.assertTrue(ResourceCosts.ROYAL_GUARD.food==originalFood&&hero.isDeadOrDying(),"configured fatal scope restores original price and actually kills");
    }
    static Resources account(String owner){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,Factions.VILLAGERS));Resources r=new Resources(owner,10000,10000,10000);ResourcesServerEvents.resourcesList.add(r);return r;}
    static <T extends Mob> T spawn(GameTestHelper h,Observer o,EntityType<T> type,String owner,int x,int z){
        T e=type.create(h.getLevel());h.assertTrue(e instanceof Unit,"registered RTS body");((Unit)e).setOwnerName(owner);e.setNoAi(true);BlockPos p=h.absolutePos(new BlockPos(x,2,z));e.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);h.getLevel().setBlock(p.below(),Blocks.STONE.defaultBlockState(),2);h.assertTrue(h.getLevel().addFreshEntity(e),"real unit enters level");o.entities.add(e);o.watched.add(e.getUUID());return e;
    }
    static BuildingPlacement install(GameTestHelper h,Observer o,Building type,String owner,int x,int z){
        BlockPos origin=h.absolutePos(new BlockPos(x,1,z));for(int dx=-2;dx<30;dx++)for(int dz=-2;dz<30;dz++){h.getLevel().setBlock(origin.offset(dx,0,dz),Blocks.STONE.defaultBlockState(),2);for(int dy=1;dy<20;dy++)h.getLevel().setBlock(origin.offset(dx,dy,dz),Blocks.AIR.defaultBlockState(),2);}
        for(BuildingBlock b:BuildingUtils.getAbsoluteBlockData(type.getRelativeBlockData(h.getLevel()),h.getLevel(),origin,Rotation.NONE))h.getLevel().setBlock(b.getBlockPos(),b.getBlockState(),2);
        BuildingPlacement p=BuildingUtils.getNewBuildingPlacement(type,h.getLevel(),origin,Rotation.NONE,owner,false);BuildingServerEvents.getBuildings().add(p);o.buildings.add(p);return p;
    }
    static void cleanup(Observer o){MinecraftForge.EVENT_BUS.unregister(o);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));o.entities.forEach(Entity::discard);BuildingServerEvents.getBuildings().removeAll(o.buildings);UnitServerEvents.getAllUnits().stream().filter(e->e instanceof Unit u&&u.getOwnerName().startsWith(PREFIX)).toList().forEach(Entity::discard);HeroServerEvents.fallenHeroes.removeIf(s->s.ownerName.startsWith(PREFIX));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));}
    static void at(GameTestHelper h,Observer o,int tick,Runnable r){h.runAtTickTime(tick,()->{try{r.run();}catch(RuntimeException|Error e){cleanup(o);throw e;}});}
    @GameTest(templateNamespace="reignofnether_kill_economy",template="ore_mine_test_arena",batch="kill_bridge_extra",timeoutTicks=200)
    public static void projectileLateCancelFreeBodiesAndNativePaidRevival(GameTestHelper h){
        Observer o=new Observer();MinecraftForge.EVENT_BUS.register(o);Resources killer=account(KILLER);Resources captor=account(PREFIX+"Captor");account(VICTIM);int initialOre=killer.ore;final LivingEntity[] capturedShotVictim={null};
        install(h,o,Buildings.TOWN_CENTRE,VICTIM,80,60);ProductionPlacement shrine=(ProductionPlacement)install(h,o,Buildings.SHRINE_OF_PROSPERITY,VICTIM,120,60);
        // Real ranged shooter and wounded targets: native RTS damage uses unit AD, not arrow baseDamage.
        // This fixture validates actual lethal collision/attribution, not overriding the combat damage pipeline.
        var attacker=spawn(h,o,EntityRegistrar.SKELETON_UNIT.get(),KILLER,2,3);var projectileVictim=spawn(h,o,EntityRegistrar.VILLAGER_UNIT.get(),VICTIM,8,3);projectileVictim.setHealth(1);
        o.scopeVictim=spawn(h,o,EntityRegistrar.VILLAGER_UNIT.get(),VICTIM,18,12);Arrow arrow=new Arrow(h.getLevel(),attacker);o.actualArrow=arrow;o.arrowVictim=projectileVictim.getUUID();arrow.setBaseDamage(1000);arrow.setPos(projectileVictim.getX()-.9,projectileVictim.getY()+.7,projectileVictim.getZ());arrow.shoot(1,0,0,3,0);o.entities.add(arrow);h.assertTrue(h.getLevel().addFreshEntity(arrow),"real shooter-owned arrow enters simulation");
        at(h,o,8,()->{
            h.assertTrue(projectileVictim.isDeadOrDying(),"real arrow collision killed target; health="+projectileVictim.getHealth()+", last="+projectileVictim.getLastDamageSource()+", arrow="+arrow.position()+", removed="+arrow.isRemoved());h.assertTrue(projectileVictim.getLastDamageSource()!=null&&projectileVictim.getLastDamageSource().getEntity()==attacker&&o.exactArrowWitness&&o.nativeProxyWitness,"native proxy source keeps actual shooter and exact real-arrow hurt-scope witness");h.assertTrue(o.nestedScopeWitness&&o.exceptionScopeWitness,"nested and throwing diagnostic impacts restore the enclosing exact hit frame");h.assertTrue(!RtsProjectileProvenance.hasExactPrimaryHit(arrow,projectileVictim,projectileVictim.getLastDamageSource()),"real hit scope is cleared after native hurt returns");h.assertTrue(killer.ore==initialOre+1&&o.committed==1,"projectile pays the unique actual shooter owner");
            var capturedShooter=spawn(h,o,EntityRegistrar.SKELETON_UNIT.get(),KILLER,2,10);var capturedTarget=spawn(h,o,EntityRegistrar.VILLAGER_UNIT.get(),VICTIM,8,10);capturedTarget.setHealth(1);capturedShotVictim[0]=capturedTarget;
            Arrow capturedArrow=new Arrow(h.getLevel(),capturedShooter);capturedArrow.setBaseDamage(1000);capturedArrow.setPos(capturedTarget.getX()-.9,capturedTarget.getY()+.7,capturedTarget.getZ());capturedArrow.shoot(1,0,0,3,0);o.entities.add(capturedArrow);h.assertTrue(h.getLevel().addFreshEntity(capturedArrow),"projectile publishes while original shooter owner/epoch are authoritative");capturedShooter.setOwnerName(PREFIX+"Captor");
            var canceled=spawn(h,o,EntityRegistrar.VILLAGER_UNIT.get(),VICTIM,11,3);o.lateVictim=canceled.getUUID();canceled.setHealth(1);canceled.hurt(h.getLevel().damageSources().mobAttack(attacker),1000);h.assertTrue(canceled.isAlive()&&o.lateCalls==1,"late LOWEST callback cancels actual death");
            var zombie=spawn(h,o,EntityRegistrar.ZOMBIE_UNIT.get(),VICTIM,14,3);zombie.setIsSummoned(true);
            var bee=spawn(h,o,EntityRegistrar.BEE_UNIT.get(),VICTIM,17,3);bee.setIsSummoned(true);
            var silver=spawn(h,o,EntityRegistrar.SILVERFISH_UNIT.get(),VICTIM,20,3);
            var slime=spawn(h,o,EntityRegistrar.SLIME_UNIT.get(),VICTIM,23,3);slime.setSize(1,true);
            var totem=spawn(h,o,EntityRegistrar.TOTEM_OF_REGENERATION.get(),VICTIM,26,3);
            for(Mob free:List.of(zombie,bee,silver,slime,totem)){free.setHealth(1);free.hurt(h.getLevel().damageSources().mobAttack(attacker),1000);h.assertTrue(free.isDeadOrDying(),"free body really dies: "+free.getType());}
            var veto=spawn(h,o,EntityRegistrar.VILLAGER_UNIT.get(),VICTIM,29,3);o.vetoVictim=veto.getUUID();veto.setHealth(1);veto.hurt(h.getLevel().damageSources().mobAttack(attacker),1000);
        });
        final UUID[] lineage={null};final Entity[] original={null},revived={null},freeHero={null};
        at(h,o,16,()->{
            h.assertTrue(capturedShotVictim[0].isDeadOrDying(),"in-flight owner-changed projectile still physically kills");h.assertTrue(captor.ore==initialOre&&killer.ore==initialOre+1,"launch-to-impact shooter capture pays neither prior nor current owner");
            h.assertTrue(killer.ore==initialOre+1&&o.committed==1,"late canceled death, five free categories and explicit addon veto pay zero");
            h.assertTrue(ResourceCosts.ROYAL_GUARD.food==0&&ResourceCosts.ROYAL_GUARD.wood==0&&ResourceCosts.ROYAL_GUARD.ore==0,"native default hero configured FWO is zero, independent of revival fee");
            String freeOwner=PREFIX+"FreeHero";account(freeOwner);spawn(h,o,EntityRegistrar.VILLAGER_UNIT.get(),freeOwner,45,12);
            var free=spawn(h,o,EntityRegistrar.ROYAL_GUARD_UNIT.get(),freeOwner,42,3);freeHero[0]=free;free.setHealth(1);free.hurt(h.getLevel().damageSources().mobAttack(attacker),1000);h.assertTrue(free.isDeadOrDying(),"separate-owner default zero-cost hero genuinely dies");
            var hero=spawn(h,o,EntityRegistrar.ROYAL_GUARD_UNIT.get(),VICTIM,35,3);original[0]=hero;configuredHeroDeath(h,hero,attacker);lineage[0]=RtsKillValueClaims.valueId(hero);
            h.assertTrue(hero.isDeadOrDying()&&!hero.isRemoved()&&HeroUnit.getHeroes(false,VICTIM,hero.getType().getDescriptionId()).contains(hero),"immediate real fatal-hurt corpse remains registered before its natural removal");
            h.assertTrue(ProductionItems.ROYAL_GUARD_REVIVE.canProduce(shrine),"registered dead body does not count as a live hero blocking revival");
        });
        at(h,o,44,()->{
            h.assertTrue(!RtsKillValueClaims.isClaimed(freeHero[0])&&o.snapshots.stream().noneMatch(v->v.victimId().equals(freeHero[0].getUUID())),"default zero-cost hero has neither economic event nor claim; separate fallen owner");
            h.assertTrue(o.snapshots.stream().anyMatch(v->v.victimId().equals(original[0].getUUID())&&v.food()==100)&&ResourceCosts.ROYAL_GUARD.food==0,"deferred event retained configured death snapshot after test config restoration");
            h.assertTrue(killer.ore==initialOre+2&&RtsKillValueClaims.isClaimed(original[0]),"first hero death has one permanent economic claim");
            HeroUnitSave fallen=HeroUnit.getFallenHero(false,VICTIM,EntityRegistrar.ROYAL_GUARD_UNIT.get().getDescriptionId());h.assertTrue(fallen!=null&&fallen.economicValue!=null,"native fallen hero record carries confirmed provenance");
            h.assertTrue(shrine.isBuilt,"native revival building genuinely completed");Resources payer=ResourcesServerEvents.resourcesList.stream().filter(a->a.ownerName.equals(VICTIM)).findFirst().orElseThrow();var quote=ProductionItems.ROYAL_GUARD_REVIVE.getCost(false,VICTIM);int food=payer.food,wood=payer.wood,ore=payer.ore;
            h.assertTrue(shrine.startProductionItem(ProductionItems.ROYAL_GUARD_REVIVE),"real native production entry accepts revival and charges");h.assertTrue(payer.food==food-quote.food&&payer.wood==wood-quote.wood&&payer.ore==ore-quote.ore,"native revival fee paid once");
            // Shorten duration only after genuine validation/payment/queue. Real production tick invokes original completion.
            shrine.productionQueue.get(0).ticksLeft=2;
        });
        at(h,o,55,()->{
            var heroes=HeroUnit.getHeroes(false,VICTIM,EntityRegistrar.ROYAL_GUARD_UNIT.get().getDescriptionId());h.assertTrue(heroes.size()==1,"native production spawned one new hero");LivingEntity hero=(LivingEntity)heroes.get(0);revived[0]=hero;o.entities.add(hero);o.watched.add(hero.getUUID());((Mob)hero).setNoAi(true);
            h.assertTrue(!hero.getUUID().equals(original[0].getUUID())&&RtsKillValueClaims.valueId(hero).equals(lineage[0]),"new entity keeps original economic lineage");h.assertTrue(RtsKillValueClaims.isClaimed(hero),"native paid revival cannot reset permanent economic claim");h.assertTrue(HeroUnit.getFallenHero(false,VICTIM,hero.getType().getDescriptionId())==null,"normal revival consumed fallen record");configuredHeroDeath(h,hero,attacker);
        });
        at(h,o,62,()->{h.assertTrue(((LivingEntity)revived[0]).isDeadOrDying()&&killer.ore==initialOre+2&&o.committed==2,"revived hero dies again without minting economic value twice");System.out.println("RTS_KILL_BRIDGE_EXTRA_RUNTIME_PASS projectile late_cancel free_matrix native_paid_revive");cleanup(o);h.succeed();});
    }
}
