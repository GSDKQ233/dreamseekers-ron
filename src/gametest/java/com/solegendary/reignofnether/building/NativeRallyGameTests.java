package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.unit.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.VillagerUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Native command dispatch and real entity publication. Uses only pre-API63 public/protected APIs. */
@GameTestHolder("api63_native_rally") @PrefixGameTestTemplate(false)
public final class NativeRallyGameTests {
    private static final Set<Fixture> FIXTURES=Collections.newSetFromMap(new IdentityHashMap<>());
    @AfterBatch(batch="native_rally") public static void cleanup(ServerLevel ignored){for(var f:List.copyOf(FIXTURES))f.close();}
    private static final class Host extends ProductionPlacement {
        Host(ServerLevel level,BlockPos pos,String owner) {
            super(Buildings.BARRACKS,level,pos,Rotation.NONE,owner,
                    new ArrayList<>(List.of(new BuildingBlock(pos,Blocks.STONE.defaultBlockState()))),false);
            isBuilt=true;
        }
        void rally(Unit unit,List<BlockPos> points,ResourceName resource) {
            rallyPoints=new ArrayList<>(points);rallyPointEntity=null;attackRally=false;rallyResourceName=resource;setDelayedRally(unit);
        }
        void target(Unit unit,VillagerUnit target) {rallyPointEntity=target;rallyPoints.clear();setDelayedRally(unit);}
        void mutate(BlockPos point) {rallyPoints.clear();rallyPoints.add(point);rallyResourceName=ResourceName.FOOD;}
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final String owner;final Host host;final BlockPos first,second,other;
        final List<VillagerUnit> units=new ArrayList<>();
        Fixture(GameTestHelper h) {
            this.h=h;FIXTURES.add(this);owner="NativeRally_"+h.absolutePos(BlockPos.ZERO).asLong();
            BlockPos pos=h.absolutePos(new BlockPos(3,2,3));h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
            host=new Host(h.getLevel(),pos,owner);BuildingServerEvents.getBuildings().add(host);
            first=h.absolutePos(new BlockPos(20,2,15));second=h.absolutePos(new BlockPos(21,2,17));other=h.absolutePos(new BlockPos(9,2,22));
        }
        VillagerUnit worker() {
            var unit=EntityRegistrar.VILLAGER_UNIT.get().create(h.getLevel());h.assertTrue(unit!=null,"native worker created");
            unit.setOwnerName(owner);unit.setNoAi(true);unit.setPersistenceRequired();unit.moveTo(first.getX()-5,first.getY(),first.getZ());
            units.add(unit);h.assertTrue(h.getLevel().addFreshEntity(unit),"native worker published");
            unit.getGatherResourceGoal().setTargetResourceName(ResourceName.NONE);return unit;
        }
        void command(String actor,VillagerUnit unit,UnitAction action,BlockPos pos) {
            UnitServerEvents.addActionItem(actor,action,-1,new int[]{unit.getId()},pos,BlockPos.ZERO);
        }
        boolean queued(VillagerUnit unit) {
            return UnitServerEvents.getUnitActionSlowQueue().stream().anyMatch(a->Arrays.stream(a.getUnitIds()).anyMatch(id->id==unit.getId()));
        }
        void done() {close();h.succeed();}
        public void close() {
            FIXTURES.remove(this);BuildingServerEvents.getBuildings().remove(host);
            for(var unit:units){UnitServerEvents.getUnitActionSlowQueue().removeIf(a->Arrays.stream(a.getUnitIds()).anyMatch(id->id==unit.getId()));if(!unit.isRemoved())unit.discard();}
        }
    }
    @GameTest(templateNamespace="api63_native_rally",template="ore_mine_test_arena",batch="native_rally",timeoutTicks=100)
    public static void stopAndNewMoveSuppressAllDelayedRallyLegs(GameTestHelper h) {
        Fixture f=new Fixture(h);var stopped=f.worker();var moved=f.worker();
        f.host.rally(stopped,List.of(f.first,f.second),ResourceName.ORE);f.host.rally(moved,List.of(f.first,f.second),ResourceName.ORE);
        f.command(f.owner,stopped,UnitAction.STOP,BlockPos.ZERO);f.command(f.owner,moved,UnitAction.MOVE,f.other);
        h.runAfterDelay(35,()->{h.assertTrue(stopped.getMoveGoal().getMoveTarget()==null&&!f.queued(stopped),"STOP cannot be overwritten by a delayed leg");
            h.assertTrue(f.other.equals(moved.getMoveGoal().getMoveTarget())&&!f.queued(moved),"new MOVE remains authoritative");
            h.assertTrue(stopped.getGatherResourceGoal().getTargetResourceName()==ResourceName.NONE,"no background resource callback after STOP");f.done();});
    }
    @GameTest(templateNamespace="api63_native_rally",template="ore_mine_test_arena",batch="native_rally",timeoutTicks=100)
    public static void rallyCapturesWaypointsAndWorkerResourceOnce(GameTestHelper h) {
        Fixture f=new Fixture(h);var unit=f.worker();f.host.rally(unit,List.of(f.first,f.second),ResourceName.ORE);f.host.mutate(f.other);
        h.runAfterDelay(4,()->{h.assertTrue(f.first.equals(unit.getMoveGoal().getMoveTarget()),"immutable original first waypoint dispatched");
            var queued=UnitServerEvents.getUnitActionSlowQueue().stream().filter(a->Arrays.stream(a.getUnitIds()).anyMatch(id->id==unit.getId())).toList();
            h.assertTrue(queued.size()==1&&f.second.equals(queued.get(0).getPreselectedBlockPos()),"remaining waypoint queued once in original order");
            h.assertTrue(unit.getGatherResourceGoal().getTargetResourceName()==ResourceName.ORE,"original resource preference captured");
            f.command(f.owner,unit,UnitAction.STOP,BlockPos.ZERO);});
        h.runAfterDelay(35,()->{h.assertTrue(!f.queued(unit)&&unit.getMoveGoal().getMoveTarget()==null,"consumed handoff never reinserts stopped route");f.done();});
    }
    @GameTest(templateNamespace="api63_native_rally",template="ore_mine_test_arena",batch="native_rally",timeoutTicks=100)
    public static void foreignRejectedCommandDoesNotConsumeRally(GameTestHelper h) {
        Fixture f=new Fixture(h);var unit=f.worker();f.host.rally(unit,List.of(f.first),ResourceName.NONE);
        f.command("UnauthorisedOtherOwner",unit,UnitAction.STOP,BlockPos.ZERO);
        h.runAfterDelay(4,()->{h.assertTrue(f.first.equals(unit.getMoveGoal().getMoveTarget()),"unauthorized STOP does not cancel owner's first rally");f.done();});
    }
    @GameTest(templateNamespace="api63_native_rally",template="ore_mine_test_arena",batch="native_rally",timeoutTicks=100)
    public static void ownerAbaAndProducerCaptureRevokePendingRally(GameTestHelper h) {
        Fixture f=new Fixture(h);var unit=f.worker();f.host.rally(unit,List.of(f.first),ResourceName.NONE);
        unit.setOwnerName("ChangedOwner");unit.setOwnerName(f.owner);
        h.runAfterDelay(4,()->{h.assertTrue(unit.getMoveGoal().getMoveTarget()==null,"unit owner A-B-A cannot revive old rally");
            f.host.rally(unit,List.of(f.second),ResourceName.NONE);f.host.setOwnerNameFromServerEditor("CapturedOwner");f.host.setOwnerNameFromServerEditor(f.owner);});
        h.runAfterDelay(8,()->{h.assertTrue(unit.getMoveGoal().getMoveTarget()==null,"producer supported capture A-B-A revokes pending rally");f.done();});
    }
    @GameTest(templateNamespace="api63_native_rally",template="ore_mine_test_arena",batch="native_rally",timeoutTicks=100)
    public static void reusedSourceIdCannotReceiveOldRally(GameTestHelper h) {
        Fixture f=new Fixture(h);var old=f.worker();f.host.rally(old,List.of(f.first,f.second),ResourceName.ORE);int id=old.getId();old.discard();
        var replacement=EntityRegistrar.VILLAGER_UNIT.get().create(h.getLevel());replacement.setOwnerName(f.owner);replacement.setNoAi(true);replacement.setId(id);replacement.moveTo(f.other.getX(),f.other.getY(),f.other.getZ());
        f.units.add(replacement);h.assertTrue(h.getLevel().addFreshEntity(replacement),"different UUID reuses old runtime ID");
        h.runAfterDelay(35,()->{h.assertTrue(replacement.getMoveGoal().getMoveTarget()==null&&!f.queued(replacement),"old rally cannot command replacement lifetime");f.done();});
    }
    @GameTest(templateNamespace="api63_native_rally",template="ore_mine_test_arena",batch="native_rally",timeoutTicks=100)
    public static void removedProducerAndReusedTargetCannotDispatch(GameTestHelper h) {
        Fixture f=new Fixture(h);var follower=f.worker();var target=f.worker();f.host.target(follower,target);int id=target.getId();target.discard();
        var impostor=EntityRegistrar.VILLAGER_UNIT.get().create(h.getLevel());impostor.setOwnerName(f.owner);impostor.setNoAi(true);impostor.setId(id);impostor.moveTo(f.other.getX(),f.other.getY(),f.other.getZ());f.units.add(impostor);
        h.assertTrue(h.getLevel().addFreshEntity(impostor),"different target UUID reuses old ID");
        h.runAfterDelay(15,()->{h.assertTrue(follower.getFollowTarget()==null,"target identity cannot be replaced by numeric ID");
            f.host.rally(follower,List.of(f.first,f.second),ResourceName.ORE);BuildingServerEvents.getBuildings().remove(f.host);});
        h.runAfterDelay(45,()->{h.assertTrue(follower.getMoveGoal().getMoveTarget()==null&&!f.queued(follower),"removed producer cannot issue later route or resource mutation");f.done();});
    }
}
