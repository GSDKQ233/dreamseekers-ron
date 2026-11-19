package com.solegendary.reignofnether.research.economy;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.resources.kill.RtsKillValueClaims;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("reignofnether_research_economy")
@PrefixGameTestTemplate(false)
public final class MonsterSalvageGameTests {
    static final String OWNER="SalvageReal_Owner",ENEMY="SalvageReal_Enemy";
    static <T extends Mob> T spawn(GameTestHelper h,EntityType<T> type,String owner,int x,List<Entity> cleanup){
        T e=type.create(h.getLevel());((Unit)e).setOwnerName(owner);e.setNoAi(true);BlockPos pos=h.absolutePos(new BlockPos(x,2,3));e.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);h.getLevel().setBlock(pos.below(),Blocks.STONE.defaultBlockState(),2);h.assertTrue(h.getLevel().addFreshEntity(e),"true RTS entity publication");cleanup.add(e);return e;
    }
    static void kill(GameTestHelper h,Mob victim,Mob attacker){
        var cost=((Unit)victim).getCost();int f=cost.food,w=cost.wood,o=cost.ore;
        try{cost.food=11;cost.wood=13;cost.ore=17;victim.setHealth(1);h.assertTrue(victim.hurt(h.getLevel().damageSources().mobAttack(attacker),1000)&&victim.isDeadOrDying(),"real native fatal attack");}
        finally{cost.food=f;cost.wood=w;cost.ore=o;}
    }
    @GameTest(templateNamespace="reignofnether_research_economy",template="ore_mine_test_arena",batch="monster_salvage_income",timeoutTicks=80)
    public static void nativeGlobalKillsPayFractionalFwoOnceWithoutMine(GameTestHelper h) {
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(OWNER,Factions.MONSTERS));PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(ENEMY,Factions.VILLAGERS));
        Resources a=new Resources(OWNER,0,0,0);ResourcesServerEvents.resourcesList.add(a);ResourcesServerEvents.resourcesList.add(new Resources(ENEMY,0,0,0));List<Entity> entities=new ArrayList<>();
        Mob attacker=spawn(h,EntityRegistrar.ZOMBIE_UNIT.get(),OWNER,2,entities);spawn(h,EntityRegistrar.VILLAGER_UNIT.get(),ENEMY,16,entities);
        Mob denied=spawn(h,EntityRegistrar.VILLAGER_UNIT.get(),ENEMY,4,entities);kill(h,denied,attacker);List<Mob> kills=new ArrayList<>();
        Runnable cleanup=()->{ResearchServerEvents.removeAllResearchFor(OWNER);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith("SalvageReal_"));ResourcesServerEvents.resourcesList.removeIf(p->p.ownerName.startsWith("SalvageReal_"));entities.forEach(Entity::discard);};
        java.util.function.BiConsumer<Integer,Runnable> at=(t,run)->h.runAtTickTime(t,()->{try{run.run();}catch(RuntimeException|Error e){cleanup.run();throw e;}});
        at.accept(4,()->{
            h.assertTrue(a.food==0&&a.wood==0&&a.ore==0&&!RtsKillValueClaims.isClaimed(denied),"unresearched monster receives no global salvage");
            ResearchServerEvents.addResearch(OWNER,CoreEconomyResearch.id("research_global_salvage"));
            for(int i=0;i<10;i++){Mob v=spawn(h,EntityRegistrar.VILLAGER_UNIT.get(),ENEMY,5+i,entities);kills.add(v);kill(h,v,attacker);}
            ResearchServerEvents.removeAllResearchFor(OWNER); // authoritative death-time research is frozen already
        });
        at.accept(10,()->{
            h.assertTrue(a.food==11&&a.wood==13&&a.ore==17,"ten true 11F13W17O deaths at10percent conserve fractions separately");
            h.assertTrue(a.conversionRemainders.get("monster_salvage_food")==0&&a.conversionRemainders.get("monster_salvage_wood")==0&&a.conversionRemainders.get("monster_salvage_ore")==0,"three independent carried fractions settle exactly");
            h.assertTrue(kills.stream().allMatch(RtsKillValueClaims::isClaimed),"each paid victim permanently claimed");
            h.assertTrue(PlayerServerEvents.getRTSPlayer(OWNER).scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_RESOURCES_HARVESTED)==41,"only actual FWO received counted");
            for(Mob victim:kills)MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(victim,h.getLevel().damageSources().mobAttack(attacker)));
        });
        at.accept(15,()->{
            h.assertTrue(a.food==11&&a.wood==13&&a.ore==17,"duplicate death notifications cannot repay claimed values");
            a.food=Integer.MAX_VALUE-1;a.wood=Integer.MAX_VALUE-1;a.ore=Integer.MAX_VALUE-1;
            a.conversionRemainders.put("monster_salvage_food",9000L);a.conversionRemainders.put("monster_salvage_wood",7000L);a.conversionRemainders.put("monster_salvage_ore",3000L);
            var scores=PlayerServerEvents.getRTSPlayer(OWNER).scores.scoreList;
            for(var key:List.of(RTSPlayerScoresEnum.FOOD_HARVESTED,RTSPlayerScoresEnum.WOOD_HARVESTED,RTSPlayerScoresEnum.ORES_HARVESTED,RTSPlayerScoresEnum.TOTAL_RESOURCES_HARVESTED))scores.put(key,Integer.MAX_VALUE-1);
            ResearchServerEvents.addResearch(OWNER,CoreEconomyResearch.id("research_global_salvage"));
            kill(h,spawn(h,EntityRegistrar.VILLAGER_UNIT.get(),ENEMY,6,entities),attacker);
        });
        at.accept(20,()->{
            h.assertTrue(a.food==Integer.MAX_VALUE&&a.wood==Integer.MAX_VALUE&&a.ore==Integer.MAX_VALUE,"real kill clips each requested2 component to actual1 received");
            h.assertTrue(a.conversionRemainders.get("monster_salvage_food")==0&&a.conversionRemainders.get("monster_salvage_wood")==0&&a.conversionRemainders.get("monster_salvage_ore")==0,"each saturated output discards its carry instead of banking cap overflow");
            var scores=PlayerServerEvents.getRTSPlayer(OWNER).scores.scoreList;
            for(var key:List.of(RTSPlayerScoresEnum.FOOD_HARVESTED,RTSPlayerScoresEnum.WOOD_HARVESTED,RTSPlayerScoresEnum.ORES_HARVESTED,RTSPlayerScoresEnum.TOTAL_RESOURCES_HARVESTED))h.assertTrue(scores.get(key)==Integer.MAX_VALUE,"actual received stats saturate rather than overflow nativeint");
            System.out.println("MONSTER_SALVAGE_RUNTIME_PASS native_kills research_gate death_time_research three_fraction_streams global_no_mine permanent_claim actual_clipping saturated_stats");cleanup.run();h.succeed();
        });
    }
}
