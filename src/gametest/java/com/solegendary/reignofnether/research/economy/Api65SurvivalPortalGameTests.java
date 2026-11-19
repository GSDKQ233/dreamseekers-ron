package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.PortalPlacement;
import com.solegendary.reignofnether.survival.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.gametest.*;
import java.lang.reflect.Field;
import java.util.*;

/** API64-compatible normal portal callback probes, not a hand-made duplicate enemy list.
 * Reflection only scopes existing private survival scheduler state and reads counters.
 * No new candidate helper is required for the baseline to compile/run. */
@GameTestHolder("api65_survival") @PrefixGameTestTemplate(false)
public final class Api65SurvivalPortalGameTests {
    private static final String TEMPLATE="ore_mine_test_arena";
    private static Field field(Class<?> type,String name)throws ReflectiveOperationException {
        Field f=type.getDeclaredField(name);f.setAccessible(true);return f;
    }
    private static Object read(Class<?> type,String name,Object target)throws ReflectiveOperationException{return field(type,name).get(target);}
    private static void write(Class<?> type,String name,Object target,Object value)throws ReflectiveOperationException{field(type,name).set(target,value);}
    private static final class F implements AutoCloseable {
        final GameTestHelper h; final Api65EconomyGameTests.F base; final PortalPlacement portal; final WavePortal wavePortal;
        final Map<String,Object> globals=new LinkedHashMap<>();
        final List<WaveEnemy> oldEnemies=new ArrayList<>(SurvivalServerEvents.getCurrentEnemies());
        final List<WavePortal> oldPortals=new ArrayList<>(SurvivalServerEvents.portals);
        final List<NetherZone> oldNetherZones=new ArrayList<>(BuildingServerEvents.netherZones);
        final List<BuildingPlacement> lastPortals; final List<BuildingPlacement> oldLastPortals;
        final Set<Entity> oldEntities=Collections.newSetFromMap(new IdentityHashMap<>());
        final List<Entity> rejected=new ArrayList<>();
        @SuppressWarnings("unchecked") F(GameTestHelper h,String suffix)throws Exception {
            this.h=h;oldEntities.addAll(UnitServerEvents.getAllUnits());
            for(String name:List.of("isEnabled","ticks","lastTime","lastEnemyCount","serverLevel","currentWave","nextWave"))
                globals.put(name,read(SurvivalServerEvents.class,name,null));
            lastPortals=(List<BuildingPlacement>)read(SurvivalServerEvents.class,"lastPortals",null);
            oldLastPortals=new ArrayList<>(lastPortals);
            SurvivalServerEvents.getCurrentEnemies().clear();SurvivalServerEvents.portals.clear();lastPortals.clear();
            base=new Api65EconomyGameTests.F(h,"surv"+suffix);base.install(Buildings.TOWN_CENTRE);
            portal=(PortalPlacement)base.install(Buildings.PORTAL_MILITARY);portal.ownerName=SurvivalServerEvents.ENEMY_OWNER_NAME;
            Wave wave=new Wave(1,20,1);wavePortal=new WavePortal(portal,wave);
            SurvivalServerEvents.currentWave=wave;SurvivalServerEvents.nextWave=wave;
            write(SurvivalServerEvents.class,"isEnabled",null,true);
            write(SurvivalServerEvents.class,"lastTime",null,-1L);write(SurvivalServerEvents.class,"lastEnemyCount",null,0L);
            lastPortals.add(portal);SurvivalServerEvents.portals.add(wavePortal);
            h.assertTrue(portal.isBuilt&&SurvivalServerEvents.isStarted(),"real military portal and owned completed capitol make active survival fixture");
        }
        Set<Entity> liveBodies(){
            Set<Entity> bodies=Collections.newSetFromMap(new IdentityHashMap<>());
            for(var e:UnitServerEvents.getAllUnits())if(!oldEntities.contains(e)&&e instanceof Unit u
                    &&SurvivalServerEvents.ENEMY_OWNER_NAME.equals(u.getOwnerName())&&e.isAlive()&&!e.isRemoved())bodies.add(e);
            return bodies;
        }
        void oneSchedulerTick()throws Exception {
            // Suppress additional portal births only during the separate AI-count observation.
            boolean built=portal.isBuilt;portal.isBuilt=false;
            try {
                write(SurvivalServerEvents.class,"ticks",null,SurvivalServerEvents.TICK_INTERVAL-1);
                SurvivalServerEvents.onLevelTick(new TickEvent.LevelTickEvent(LogicalSide.SERVER,TickEvent.Phase.END,h.getLevel(),()->true));
            } finally {portal.isBuilt=built;}
        }
        @Override public void close()throws Exception {
            write(SurvivalServerEvents.class,"isEnabled",null,false);
            for(var e:List.copyOf(UnitServerEvents.getAllUnits()))if(!oldEntities.contains(e)&&e instanceof Unit u
                    &&SurvivalServerEvents.ENEMY_OWNER_NAME.equals(u.getOwnerName()))e.discard();
            for(Entity e:rejected)if(!e.isRemoved())e.discard();
            base.close();
            // Portal.onBuilt persists a conversion zone even though RELOAD removal is non-destructive.
            BuildingServerEvents.netherZones.clear();BuildingServerEvents.netherZones.addAll(oldNetherZones);
            BuildingServerEvents.saveNetherZones(h.getLevel());
            SurvivalServerEvents.getCurrentEnemies().clear();SurvivalServerEvents.getCurrentEnemies().addAll(oldEnemies);
            SurvivalServerEvents.portals.clear();SurvivalServerEvents.portals.addAll(oldPortals);
            lastPortals.clear();lastPortals.addAll(oldLastPortals);
            for(var e:globals.entrySet())write(SurvivalServerEvents.class,e.getKey(),null,e.getValue());
        }
    }
    public static final class RejectPortalBirth {
        final F fixture; int attempts;
        RejectPortalBirth(F fixture){this.fixture=fixture;}
        @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true)
        public void join(EntityJoinLevelEvent event){
            if(event.getLevel()==fixture.h.getLevel()&&event.getEntity() instanceof Unit
                    &&event.getEntity().position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(fixture.portal.centrePos))<64*64){
                attempts++;fixture.rejected.add(event.getEntity());event.setCanceled(true);
            }
        }
    }
    @GameTest(templateNamespace="api65_survival",template=TEMPLATE,batch="api65_survival_unique",timeoutTicks=120)
    public static void actualPortalBirthsHaveUniqueEnemyPopulationAndOneAiTick(GameTestHelper h)throws Exception {
        try(F f=new F(h,"unique")){
            f.wavePortal.tick(SurvivalServerEvents.TICK_INTERVAL);f.wavePortal.tick(SurvivalServerEvents.TICK_INTERVAL);
            Set<Entity> bodies=f.liveBodies();
            h.assertTrue(bodies.size()==2&&bodies.stream().map(Entity::getUUID).distinct().count()==2,
                    "two normal portal production callbacks really publish two distinct bodies");
            h.assertTrue(SurvivalServerEvents.getCurrentEnemies().size()==2,
                    "two actual portal bodies must have exactly two WaveEnemy entries, not duplicate wrappers");
            int expectedPop=bodies.stream().mapToInt(e->((Unit)e).getCost().population).sum();
            h.assertTrue(SurvivalServerEvents.getTotalEnemyPopulation()==expectedPop,"enemy population counts each published body exactly once");
            f.oneSchedulerTick();
            Map<Entity,Long> ticksByBody=new IdentityHashMap<>();
            for(WaveEnemy e:SurvivalServerEvents.getCurrentEnemies())
                ticksByBody.merge(e.getEntity(),((Number)read(WaveEnemy.class,"ticks",e)).longValue(),Long::sum);
            h.assertTrue(ticksByBody.size()==2&&ticksByBody.values().stream().allMatch(t->t==SurvivalServerEvents.TICK_INTERVAL),
                    "registered survival scheduler advances each actual body's AI counter only once");
        }h.succeed();
    }
    @GameTest(templateNamespace="api65_survival",template=TEMPLATE,batch="api65_survival_rejected",timeoutTicks=120)
    public static void rejectedActualPortalPublicationDoesNotCrashOrSpendSpawnBudget(GameTestHelper h)throws Exception {
        try(F f=new F(h,"rejected")){
            int initial=((Number)read(WavePortal.class,"initialSpawnPop",f.wavePortal)).intValue();
            RejectPortalBirth reject=new RejectPortalBirth(f);MinecraftForge.EVENT_BUS.register(reject);
            RuntimeException failure=null;
            try {f.wavePortal.tick(SurvivalServerEvents.TICK_INTERVAL);}catch(RuntimeException e){failure=e;}
            finally {MinecraftForge.EVENT_BUS.unregister(reject);}
            h.assertTrue(reject.attempts==1,"real EntityType.spawn publication was rejected exactly once by Forge join event");
            h.assertTrue(failure==null,"rejected real portal birth must be handled without null WaveEnemy crash; got "+failure);
            h.assertTrue(f.liveBodies().isEmpty()&&SurvivalServerEvents.getCurrentEnemies().isEmpty(),"rejected publication leaves neither live body nor WaveEnemy ghost");
            h.assertTrue(((Number)read(WavePortal.class,"initialSpawnPop",f.wavePortal)).intValue()==initial,"rejected birth cannot consume initial spawn population budget");
            f.wavePortal.tick(SurvivalServerEvents.TICK_INTERVAL);
            h.assertTrue(f.liveBodies().size()==1&&SurvivalServerEvents.getCurrentEnemies().size()==1,"removing rejection permits one genuine retry birth and one registry entry");
            int population=f.liveBodies().stream().mapToInt(e->((Unit)e).getCost().population).sum();
            h.assertTrue(SurvivalServerEvents.getTotalEnemyPopulation()==population,"successful retry contributes exact body population");
        }h.succeed();
    }
}
