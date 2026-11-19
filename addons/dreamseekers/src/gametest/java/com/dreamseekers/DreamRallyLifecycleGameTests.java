// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.production.DreamProductionPlacement;
import com.dreamseekers.unit.JellyfishUnit;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Handoff lifecycle contracts; protected staging is exposed only by this test subclass. */
@GameTestHolder(DreamseekersMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DreamRallyLifecycleGameTests {
    private static final String PREFIX="JellyRallyLife_", BATCH="dreamseekers_jellyfish_rally_lifecycle";
    private static final List<Entity> ACTORS=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) {
        ACTORS.forEach(e->{if(!e.isRemoved())e.discard();});ACTORS.clear();
        BuildingServerEvents.getBuildings().removeIf(b->b.ownerName.startsWith(PREFIX));
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.startsWith(PREFIX));
        ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.startsWith(PREFIX));
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=220)
    public static void actualStopReplacementOwnerCycleAndRemovalCancelPendingHandoff(GameTestHelper h) {
        Fixture f=new Fixture(h,"cancel");
        List<JellyfishUnit> units=new ArrayList<>();for(int i=0;i<5;i++)units.add(f.unit(true,31,3+i*3));
        h.startSequence().thenWaitUntil(()->h.assertTrue(units.stream().allMatch(u->f.level.getEntity(u.getUUID())==u&&u.tickCount>2),"all real entities are published"))
            .thenExecute(()->{
                f.producer.setRallyPoint(f.pos(38,0,8));units.forEach(f.producer::stage);
                long before=units.get(0).rts().commandRevision;
                f.command(units.get(0),UnitAction.STOP,BlockPos.ZERO);
                h.assertTrue(units.get(0).rts().commandRevision>before,"actual STOP advances handoff generation");
                f.command(units.get(1),UnitAction.MOVE,f.pos(31,0,22));
                units.get(2).setOwnerName(f.owner+"other");units.get(2).setOwnerName(f.owner);
                units.get(3).discard();
                units.get(4).hurt(f.level.damageSources().genericKill(),1000);
                f.producer.tick(f.level);
                h.assertTrue(pending(f.producer)==0,"STOP/replacement/owner A-B-A/discard/death each remove stale handoff");
                h.assertTrue(units.get(0).getMoveGoal().getMoveTarget()==null&&units.get(2).getMoveGoal().getMoveTarget()==null,"cancelled commands do not get original rally");
                h.assertTrue(f.pos(31,1,22).equals(units.get(1).getMoveGoal().getMoveTarget()),"replacement MOVE remains authoritative");
            }).thenIdle(70).thenExecute(()->{
                h.assertTrue(units.get(0).getMoveGoal().getMoveTarget()==null&&units.get(2).getMoveGoal().getMoveTarget()==null,"no wall-clock callback restores cancelled rally");
                f.balance();System.out.println("DREAMSEEKERS_RALLY_LIFECYCLE_PASS stop_replacement_owner_aba_discard_death");
            }).thenSucceed();
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=180)
    public static void detachedProducerAndBoundedUnpublishedTimeoutNeverRestoreCommands(GameTestHelper h) {
        Fixture f=new Fixture(h,"expiry");JellyfishUnit delayed=f.unit(false,31,8),detached=f.unit(true,31,12);
        f.producer.setRallyPoint(f.pos(38,0,8));f.producer.stage(delayed);
        h.startSequence().thenIdle(DreamProductionPlacement.RALLY_PUBLICATION_TIMEOUT_TICKS+8).thenExecute(()->{
            h.assertTrue(pending(f.producer)==0,"unpublished exact entity handoff expires within bounded server ticks");
            h.assertTrue(f.level.addFreshEntity(delayed),"same identity is published only after expiry");
            f.producer.stage(detached);BuildingServerEvents.getBuildings().remove(f.producer);f.producer.tick(f.level);
            h.assertTrue(pending(f.producer)==0,"detached producer fails closed");
            BuildingServerEvents.getBuildings().add(f.producer);
            f.producer.stage(detached);f.producer.onRemoved(BuildingPlacement.RemovalReason.SANDBOX);
            h.assertTrue(pending(f.producer)==0,"actual removal clears pending handoffs");
        }).thenIdle(50).thenExecute(()->{
            h.assertTrue(f.level.getEntity(delayed.getUUID())==delayed,"expired identity is now genuinely visible");
            h.assertTrue(delayed.getMoveGoal().getMoveTarget()==null&&detached.getMoveGoal().getMoveTarget()==null,"expired/detached/removed orders never appear later");
            f.balance();System.out.println("DREAMSEEKERS_RALLY_LIFECYCLE_PASS bounded_timeout_detach_removed");
        }).thenSucceed();
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=950)
    public static void realProductionSnapshotsTwoLegRallyAndStopClearsRemainingLeg(GameTestHelper h) {
        Fixture f=new Fixture(h,"legs");BlockPos first=f.pos(31,0,8),second=f.pos(38,0,17);
        f.producer.setRallyPoint(first);f.producer.addRallyPoint(second);
        Entity spawned=f.producer.produceUnit(f.level,DreamseekersMod.JELLYFISH_UNIT.get(),f.owner,false);
        h.assertTrue(spawned instanceof JellyfishUnit,"real production spawn and inherited handoff path");
        JellyfishUnit unit=(JellyfishUnit)spawned;ACTORS.add(unit);
        f.producer.getRallyPoints().clear();f.producer.setRallyPoint(f.pos(31,0,24));
        h.startSequence().thenWaitUntil(()->h.assertTrue(unit.tickCount>2&&unit.getMoveGoal().getMoveTarget()!=null,"real first rally starts after publication"))
            .thenExecute(()->h.assertTrue(first.above().equals(unit.getMoveGoal().getMoveTarget()),"later building rally edits do not mutate the captured first leg"))
            .thenWaitUntil(()->h.assertTrue(second.above().equals(unit.getMoveGoal().getMoveTarget()),"captured second leg starts only after first becomes idle"))
            .thenExecute(()->{
                h.assertTrue(distanceXZ(unit,first)<4,"actual unit reaches first leg before second dispatch");
                f.command(unit,UnitAction.STOP,BlockPos.ZERO);
            }).thenIdle(70).thenExecute(()->{
                h.assertTrue(unit.getMoveGoal().getMoveTarget()==null&&unit.getNavigation().isDone(),"STOP clears queued leg and no late callback reintroduces it");
                h.assertTrue(distanceXZ(unit,first)<4,"stopped unit stays at first leg");
                f.balance();System.out.println("DREAMSEEKERS_RALLY_LIFECYCLE_PASS real_spawn_snapshot_two_legs_stop");
            }).thenSucceed();
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=220)
    public static void enqueuedImmediateOrdersWinBeforeProductionTickAndCannotLeaveStaleSecondLeg(GameTestHelper h) {
        Fixture f=new Fixture(h,"pendingfast");JellyfishUnit stopped=f.unit(true,31,8),replacement=f.unit(true,31,12),foreign=f.unit(true,31,16);
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.level.getEntity(stopped.getUUID())==stopped&&f.level.getEntity(replacement.getUUID())==replacement&&f.level.getEntity(foreign.getUUID())==foreign,"all actors published"))
            .thenExecute(()->{
                f.producer.setRallyPoint(f.pos(38,0,8));f.producer.addRallyPoint(f.pos(38,0,20));
                f.producer.stage(stopped);f.producer.stage(replacement);f.producer.stage(foreign);
                UnitServerEvents.addActionItem(f.owner,UnitAction.STOP,-1,new int[]{stopped.getId()},BlockPos.ZERO,BlockPos.ZERO);
                UnitServerEvents.addActionItem(f.owner,UnitAction.MOVE,-1,new int[]{replacement.getId()},f.pos(31,0,22),BlockPos.ZERO);
                UnitServerEvents.addActionItem(f.owner+"unauthorized",UnitAction.STOP,-1,new int[]{foreign.getId()},BlockPos.ZERO,BlockPos.ZERO);
                f.producer.tick(f.level); // Deliberately precedes the queued immediate dispatch.
                UnitServerEvents.onWorldTick(new net.minecraftforge.event.TickEvent.LevelTickEvent(net.minecraftforge.fml.LogicalSide.SERVER,
                        net.minecraftforge.event.TickEvent.Phase.END,f.level,()->true));
                f.producer.tick(f.level);
                int stale=slowCount(stopped.getId());
                System.out.println("DREAM_RALLY_PENDING_ORDER_DIAGNOSTIC stopped_slow="+stale+" stopped_move="+stopped.getMoveGoal().getMoveTarget());
                h.assertTrue(stale==0,"queued STOP must not leave a late-added second rally leg");
                h.assertTrue(stopped.getMoveGoal().getMoveTarget()==null,"actual queued STOP is effective");
                h.assertTrue(f.pos(38,1,8).equals(foreign.getMoveGoal().getMoveTarget()),"rejected foreign immediate cannot cancel the genuine original rally");
                h.assertTrue(f.pos(31,1,22).equals(replacement.getMoveGoal().getMoveTarget()),"queued replacement is effective");
            }).thenIdle(70).thenExecute(()->{
                h.assertTrue(stopped.getMoveGoal().getMoveTarget()==null&&stopped.getNavigation().isDone(),"no stale second rally survives queued-but-not-yet-dispatched STOP");
                h.assertTrue(pending(f.producer)==0,"both superseded publication handoffs terminate");
                f.balance();System.out.println("DREAMSEEKERS_RALLY_LIFECYCLE_PASS enqueued_immediate_priority");
            }).thenSucceed();
    }
    private static double distanceXZ(Entity e,BlockPos p){double x=e.getX()-p.getX()-.5,z=e.getZ()-p.getZ()-.5;return x*x+z*z;}
    private static int slowCount(int id) {
        try{var field=UnitServerEvents.class.getDeclaredField("unitActionSlowQueue");field.setAccessible(true);
            var queue=(List<UnitActionItem>)field.get(null);synchronized(queue){return (int)queue.stream().filter(a->Arrays.stream(a.getUnitIds()).anyMatch(i->i==id)).count();}}
        catch(ReflectiveOperationException e){throw new RuntimeException(e);}
    }
    private static int pending(DreamProductionPlacement p) {
        try{var field=DreamProductionPlacement.class.getDeclaredField("pendingRallies");field.setAccessible(true);return ((List<?>)field.get(p)).size();}
        catch(ReflectiveOperationException e){throw new RuntimeException(e);}
    }
    private static final class ProbePlacement extends DreamProductionPlacement {
        ProbePlacement(ServerLevel l,BlockPos p,String owner){super(DreamseekersMod.BEETLE_SANCTUARY,l,p,Rotation.NONE,owner,
                BuildingUtils.getAbsoluteBlockData(DreamseekersMod.BEETLE_SANCTUARY.getRelativeBlockData(l),l,p,Rotation.NONE),false);}
        void stage(Unit unit){super.setDelayedRally(unit);}
    }
    private static final class Fixture {
        final GameTestHelper h;final ServerLevel level;final BlockPos origin;final String owner;final ProbePlacement producer;
        Fixture(GameTestHelper h,String suffix){this.h=h;level=h.getLevel();owner=PREFIX+suffix;origin=BuildingAssetGameTests.outdoorPos(h,4,1,4);
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));
            ResourcesServerEvents.resourcesList.add(new Resources(owner,500,500,500));
            for(int x=-3;x<43;x++)for(int z=-3;z<29;z++){
                level.setBlock(pos(x,10,z),Blocks.STONE.defaultBlockState(),2);level.setBlock(pos(x,0,z),Blocks.STONE.defaultBlockState(),2);
                for(int y=1;y<10;y++)level.setBlock(pos(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            for(BuildingBlock block:BuildingUtils.getAbsoluteBlockData(DreamseekersMod.BEETLE_SANCTUARY.getRelativeBlockData(level),level,origin,Rotation.NONE))
                level.setBlock(block.getBlockPos(),block.getBlockState(),2);
            producer=new ProbePlacement(level,origin,owner);
            BuildingServerEvents.getBuildings().add(producer);producer.onBuilt();
            h.assertTrue(!producer.shouldBeDestroyed(),"fixture places physical blocks before placement initializes its block count");
        }
        BlockPos pos(int x,int y,int z){return origin.offset(x,y,z);}
        JellyfishUnit unit(boolean add,int x,int z){JellyfishUnit unit=DreamseekersMod.JELLYFISH_UNIT.get().create(level);h.assertTrue(unit!=null,"create native paid subtype");
            unit.setOwnerName(owner);unit.moveTo(pos(x,1,z).getX()+.5,pos(x,1,z).getY(),pos(x,1,z).getZ()+.5);ACTORS.add(unit);
            if(add)h.assertTrue(level.addFreshEntity(unit),"add real actor");return unit;}
        void command(JellyfishUnit u,UnitAction action,BlockPos pos){new UnitActionItem(owner,action,-1,new int[]{u.getId()},pos,BlockPos.ZERO).action(level);}
        void balance(){Resources r=ResourcesServerEvents.resourcesList.stream().filter(a->a.ownerName.equals(owner)).findFirst().orElseThrow();
            h.assertTrue(r.food==500&&r.wood==500&&r.ore==500,"rally handoff neither charges nor refunds; paid queue billing is separately tested");}
    }
}
