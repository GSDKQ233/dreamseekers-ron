package com.dreamseekers;

import com.dreamseekers.neutralcontract.*;
import com.dreamseekers.economy.*;
import com.dreamseekers.unit.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.research.economy.CoreEconomyResearch;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.resources.kill.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

/** Actual paid contracts and actual paid native training, followed by real enemy damage/death events. */
@GameTestHolder("dreamseekers_neutral_contract") @PrefixGameTestTemplate(false)
public final class NeutralContractKillIncomeGameTests {
    private static int shadow(Resources r){return r.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0);}
    private static void balance(GameTestHelper h,Resources r,int food,int wood,int ore,int emerald,int shadow,String label){
        h.assertTrue(r.food==food&&r.wood==wood&&r.ore==ore&&r.emerald==emerald&&shadow(r)==shadow,label+" actual F/W/O/E/S="+r.food+"/"+r.wood+"/"+r.ore+"/"+r.emerald+"/"+shadow(r));
    }
    private static void kill(GameTestHelper h,Mob victim,Mob killer){
        victim.setNoAi(true);victim.setHealth(1);victim.hurt(victim.damageSources().mobAttack(killer),100000);
        h.assertTrue(victim.isDeadOrDying()&&victim.getLastDamageSource()!=null&&victim.getLastDamageSource().getEntity()==killer,"actual native enemy damage pipeline kills exact body");
    }
    @GameTest(templateNamespace="dreamseekers_neutral_contract",template="dreamseekers_test_arena",batch="neutral_contract",timeoutTicks=1000)
    public static void RealContractDeathsPayZeroWhilePaidTrainingControlsPayBothIncomePipelines(GameTestHelper h){
        var f=new NeutralContractGameTests.F(h,"kill_origin");String monster="Neutral_KillMonster",dream="Neutral_KillDream";
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(monster,Factions.MONSTERS));PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(dream,DreamseekersMod.DREAMSEEKERS));
        var monsterAccount=new Resources(monster,1000,1000,1000);monsterAccount.emerald=100;monsterAccount.extra.put(DreamPassiveEconomy.SHADOW,100);ResourcesServerEvents.resourcesList.add(monsterAccount);
        var dreamAccount=new Resources(dream,1000,1000,1000);dreamAccount.emerald=100;dreamAccount.extra.put(DreamPassiveEconomy.SHADOW,100);ResourcesServerEvents.resourcesList.add(dreamAccount);
        ResearchServerEvents.addResearch(monster,CoreEconomyResearch.id("research_global_salvage"));ResearchServerEvents.addResearch(dream,DreamEconomyResearch.CONVERSION_ID);
        var monsterKiller=EntityRegistrar.SPIDER_UNIT.get().create(f.level);monsterKiller.setOwnerName(monster);monsterKiller.setNoAi(true);f.place(monsterKiller,-15,-15);
        var dreamKiller=DreamseekersMod.BEETLE.get().create(f.level);dreamKiller.setOwnerName(dream);dreamKiller.setNoAi(true);f.place(dreamKiller,-15,-10);
        var producer1=(ProductionPlacement)f.install(DreamseekersMod.BEETLE_SANCTUARY,40,0);var producer2=(ProductionPlacement)f.install(DreamseekersMod.BEETLE_SANCTUARY,72,0);
        var worker1=f.worker(3,-10,false);var worker2=f.worker(3,-15,false);var native1=f.nativeBody(false,5,-10);var native2=f.nativeBody(true,5,-15);
        Set<UUID> freeIds=new HashSet<>(),paidIds=new HashSet<>();List<ConfirmedRtsKillEvent> events=new ArrayList<>();
        Consumer<ConfirmedRtsKillEvent> observer=e->{if(e.snapshot().victimOwner().equals(f.owner))events.add(e);};MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST,observer);
        Mob[] freeBodies=new Mob[2],paidBodies=new Mob[2];long[] negativeRevisions=new long[2];long[] paidStarted={0};
        f.cleanupActions.add(()->{MinecraftForge.EVENT_BUS.unregister(observer);for(String owner:List.of(monster,dream)){ResearchServerEvents.removeAllResearchFor(owner);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(owner));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(owner));}});
        h.startSequence().thenWaitUntil(()->{f.ready(worker1,worker2,native1,native2,monsterKiller,dreamKiller);h.assertTrue(producer1.isBuilt&&producer2.isBuilt,"two genuine completed training producers");})
          .thenExecute(()->{
            h.assertTrue(!ResearchServerEvents.playerHasCheat(f.owner,"warpten")&&!com.dreamseekers.progression.DreamWindProgression.server(f.owner).wind()&&com.dreamseekers.progression.DreamWindProgression.trainingDiscount(false,f.owner)==0&&DreamseekersMod.BEETLE_PRODUCTION.defaultCost.ticks==400&&DreamseekersMod.SNAIL_PRODUCTION.defaultCost.ticks==600,"no wind discount, no training speed cheat, native400/600tick prices");paidStarted[0]=f.level.getGameTime();
            h.assertTrue(producer1.startProductionItem(DreamseekersMod.BEETLE_PRODUCTION)&&producer2.startProductionItem(DreamseekersMod.SNAIL_PRODUCTION),"actual paid training queues start normal nonfree counterparts");
            h.assertTrue(f.account.food==835&&f.account.wood==980&&f.account.ore==965,"normal training charges actual 75F20W plus 90F35O once");
            h.assertTrue(f.start(worker1,native1).equals("approaching")&&f.start(worker2,native2).equals("approaching"),"both real native contracts begin full160tick channel");
          }).thenWaitUntil(()->h.assertTrue(f.converted().size()==2,"two actual contract conversions completed"))
          .thenExecute(()->{
            freeBodies[0]=f.converted().stream().filter(e->e instanceof BeetleUnit).findFirst().orElseThrow();freeBodies[1]=f.converted().stream().filter(e->e instanceof BasaltSnailUnit).findFirst().orElseThrow();
            h.assertTrue(f.account.food==725&&f.account.wood==980&&f.account.ore==945&&shadow(f.account)==465,"training plus exact contract fees paid, not injected free markers");
            h.assertTrue(ContractLedger.get(f.level.getServer()).count(f.owner,f.account.accountEpoch)==2,"both completed contracts really consume slots");
            for(var body:freeBodies){freeIds.add(body.getUUID());h.assertTrue(body.getPersistentData().getCompound(RtsKillValueClaims.FREE_ORIGIN_KEY).getString("origin").equals("NEUTRAL_CONTRACT"),"actual service assigned exact new free source");}
            negativeRevisions[0]=monsterAccount.revision;negativeRevisions[1]=dreamAccount.revision;
            kill(h,freeBodies[0],monsterKiller);kill(h,freeBodies[1],dreamKiller);
          }).thenExecuteAfter(5,()->{
            balance(h,monsterAccount,1000,1000,1000,100,100,"real contracted beetle yields no salvage or other currency");balance(h,dreamAccount,1000,1000,1000,100,100,"real contracted snail yields no shadow or other currency");
            h.assertTrue(monsterAccount.revision==negativeRevisions[0]&&dreamAccount.revision==negativeRevisions[1]&&monsterAccount.conversionRemainders.isEmpty()&&dreamAccount.conversionRemainders.isEmpty(),"zero credit transaction and zero fractional-value banking");
            h.assertTrue(events.stream().noneMatch(e->freeIds.contains(e.snapshot().victimId()))&&Arrays.stream(freeBodies).noneMatch(RtsKillValueClaims::isClaimed),"contract deaths cannot publish or reserve a cost-based economic claim");
            h.assertTrue(ContractLedger.get(f.level.getServer()).count(f.owner,f.account.accountEpoch)==0,"actual final deaths release both payer slots");
            for(int i=0;i<2;i++)freeBodies[i].hurt(f.level.damageSources().mobAttack(i==0?monsterKiller:dreamKiller),100000);
          }).thenWaitUntil(()->h.assertTrue(producer1.productionQueue.isEmpty()&&producer2.productionQueue.isEmpty()&&UnitServerEvents.getAllUnits().stream().filter(e->e instanceof Unit u&&u.getOwnerName().equals(f.owner)&&e.isAlive()&&(e instanceof BeetleUnit||e instanceof BasaltSnailUnit)&&!e.getPersistentData().contains(NeutralContracts.ORIGIN)).count()==2,"full normal400/600tick paid training produces actual positive controls"))
          .thenExecute(()->{
            h.assertTrue(f.level.getGameTime()-paidStarted[0]>=599,"actual natural paid train clock was not shortened");
            paidBodies[0]=(Mob)UnitServerEvents.getAllUnits().stream().filter(e->e instanceof BeetleUnit u&&u.getOwnerName().equals(f.owner)&&e.isAlive()&&!e.getPersistentData().contains(NeutralContracts.ORIGIN)).findFirst().orElseThrow();
            paidBodies[1]=(Mob)UnitServerEvents.getAllUnits().stream().filter(e->e instanceof BasaltSnailUnit u&&u.getOwnerName().equals(f.owner)&&e.isAlive()&&!e.getPersistentData().contains(NeutralContracts.ORIGIN)).findFirst().orElseThrow();
            for(var body:paidBodies){paidIds.add(body.getUUID());h.assertTrue(!RtsKillValueClaims.isFree(body),"actual training was not marked neutral/free");}
            kill(h,paidBodies[0],monsterKiller);kill(h,paidBodies[1],dreamKiller);
          }).thenExecuteAfter(5,()->{
            balance(h,monsterAccount,1007,1002,1000,100,100,"normal paid beetle yields actual10percent F/W salvage");balance(h,dreamAccount,1000,1000,1000,100,112,"normal paid snail yields actual10percent shadow conversion");
            h.assertTrue(events.size()==2&&events.stream().allMatch(e->paidIds.contains(e.snapshot().victimId())&&e.isClaimed()),"only two real paid controls publish and commit economic claims");
            for(int i=0;i<2;i++)paidBodies[i].hurt(f.level.damageSources().mobAttack(i==0?monsterKiller:dreamKiller),100000);
          }).thenExecuteAfter(5,()->{balance(h,monsterAccount,1007,1002,1000,100,100,"repeat death cannot mint salvage");balance(h,dreamAccount,1000,1000,1000,100,112,"repeat death cannot mint shadow");h.assertTrue(events.size()==2&&ContractLedger.get(f.level.getServer()).count(f.owner,f.account.accountEpoch)==0,"no duplicate economic events or phantom slots");f.close();h.succeed();});
    }
}
