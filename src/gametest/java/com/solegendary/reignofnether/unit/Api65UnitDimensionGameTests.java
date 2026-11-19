package com.solegendary.reignofnether.unit;

import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.PortalPlacement;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.unit.goals.*;
import com.solegendary.reignofnether.unit.interfaces.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** API64 entry points only: baseline-compatible positional-building dimension regressions. */
@GameTestHolder("api65_unit_dimensions") @PrefixGameTestTemplate(false)
public final class Api65UnitDimensionGameTests {
    private static final String BATCH="api65_unit_dimensions";
    private static ServerLevel other(GameTestHelper h) {
        ServerLevel other=h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(other!=null&&other!=h.getLevel(),"real second dimension exists");return other;
    }
    private static <T extends Mob> T publish(GameTestHelper h,T mob,String suffix) {
        ((Unit)mob).setOwnerName("Api65Dim_"+suffix);mob.setNoAi(true);mob.setNoGravity(true);mob.setPersistenceRequired();
        BlockPos p=h.absolutePos(new BlockPos(4,3,4));mob.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5);
        h.assertTrue(h.getLevel().addFreshEntity(mob),"real registered native unit is published");return mob;
    }
    private static BuildingPlacement building(ServerLevel level,BlockPos p,String owner,boolean portal) {
        ArrayList<BuildingBlock> blocks=new ArrayList<>(List.of(new BuildingBlock(p,Blocks.STONE.defaultBlockState())));
        BuildingPlacement b=portal
                ?new PortalPlacement(Buildings.PORTAL_TRANSPORT,level,p,Rotation.NONE,owner,blocks,false)
                :new BuildingPlacement(Buildings.BARRACKS,level,p,Rotation.NONE,owner,blocks,false);
        b.isBuilt=true;
        if(b instanceof PortalPlacement pp)pp.destination=p.offset(8,0,0);
        return b;
    }
    private static void command(GameTestHelper h,Unit unit,UnitAction action,BlockPos target) {
        new UnitActionItem(unit.getOwnerName(),action,-1,new int[]{((Entity)unit).getId()},target,BlockPos.ZERO).action(h.getLevel());
    }
    @GameTest(templateNamespace="api65_unit_dimensions",template="ore_mine_test_arena",batch=BATCH)
    public static void manualMeleeOrderRejectsForeignBuildingAndKeepsLocalPositive(GameTestHelper h) {
        var unit=publish(h,EntityRegistrar.VINDICATOR_UNIT.get().create(h.getLevel()),"Melee");
        BlockPos p=unit.blockPosition().offset(5,0,0);
        var foreign=building(other(h),p,"Api65ForeignMelee",false);var local=building(h.getLevel(),p,"Api65LocalMelee",false);
        var goal=(MeleeAttackBuildingGoal)unit.getAttackBuildingGoal();
        try {
            h.assertTrue(unit.canAttackBuildings()&&foreign.isAttackable(),"registered unit and building satisfy non-dimensional attack preconditions");
            BuildingServerEvents.getBuildings().add(foreign);
            command(h,unit,UnitAction.ATTACK_BUILDING,p);
            h.assertTrue(goal.getBuildingTarget()==null,"manual ATTACK_BUILDING does not install a foreign-level melee target");
            BuildingServerEvents.getBuildings().add(local);
            command(h,unit,UnitAction.ATTACK_BUILDING,p);
            h.assertTrue(goal.getBuildingTarget()==local,"manual melee targeting still selects same-level building even after a foreign same-coordinate entry");
        } finally {BuildingServerEvents.getBuildings().remove(foreign);BuildingServerEvents.getBuildings().remove(local);unit.discard();}
        h.succeed();
    }
    @GameTest(templateNamespace="api65_unit_dimensions",template="ore_mine_test_arena",batch=BATCH)
    public static void manualRangedOrderAndDirectSetterRejectForeignBuilding(GameTestHelper h) {
        var unit=publish(h,EntityRegistrar.GHAST_UNIT.get().create(h.getLevel()),"Ranged");
        BlockPos p=unit.blockPosition().offset(5,0,0);
        var foreign=building(other(h),p,"Api65ForeignRanged",false);var local=building(h.getLevel(),p,"Api65LocalRanged",false);
        var goal=(RangedAttackBuildingGoal<?>)unit.getAttackBuildingGoal();
        try {
            h.assertTrue(unit.canAttackBuildings()&&foreign.isAttackable(),"registered Ghast ranged building attack is available");
            BuildingServerEvents.getBuildings().add(foreign);
            command(h,unit,UnitAction.ATTACK_BUILDING,p);
            h.assertTrue(goal.getBuildingTarget()==null,"manual ATTACK_BUILDING does not install a foreign-level ranged target");
            goal.setBuildingTarget(foreign);
            h.assertTrue(goal.getBuildingTarget()==null,"direct placement setter cannot bypass the dimension contract");
            BuildingServerEvents.getBuildings().add(local);
            command(h,unit,UnitAction.ATTACK_BUILDING,p);
            h.assertTrue(goal.getBuildingTarget()==local,"same-level ranged building remains selectable");
            goal.setBuildingTarget(foreign);
            h.assertTrue(goal.getBuildingTarget()==local,"rejected foreign setter preserves the existing legitimate target");
        } finally {BuildingServerEvents.getBuildings().remove(foreign);BuildingServerEvents.getBuildings().remove(local);unit.discard();}
        h.succeed();
    }
    @GameTest(templateNamespace="api65_unit_dimensions",template="ore_mine_test_arena",batch=BATCH)
    public static void moveOrderDoesNotUseForeignGroundOrFlyingPortal(GameTestHelper h) {
        var ground=publish(h,EntityRegistrar.VINDICATOR_UNIT.get().create(h.getLevel()),"Portal");
        var flying=publish(h,EntityRegistrar.GHAST_UNIT.get().create(h.getLevel()),"Portal");
        BlockPos p=ground.blockPosition().offset(5,0,0);
        var foreign=building(other(h),p,ground.getOwnerName(),true);var local=building(h.getLevel(),p,ground.getOwnerName(),true);
        var groundGoal=(UsePortalGoal)ground.getUsePortalGoal();var flyingGoal=(FlyingUsePortalGoal)flying.getUsePortalGoal();
        try {
            h.assertTrue(ground.canUsePortal()&&flying.canUsePortal()&&((PortalPlacement)foreign).hasDestination(),"native walking/flying transport prerequisites are real");
            BuildingServerEvents.getBuildings().add(foreign);
            command(h,ground,UnitAction.MOVE,p);command(h,flying,UnitAction.MOVE,p);
            h.assertTrue(groundGoal.getBuildingTarget()==null&&flyingGoal.getBuildingTarget()==null,"MOVE cannot resolve another dimension's portal");
            // Native goal callers must not bypass the action-layer lookup fix.
            groundGoal.setBuildingTarget(p);flyingGoal.setBuildingTarget(p);
            h.assertTrue(groundGoal.getBuildingTarget()==null&&flyingGoal.getBuildingTarget()==null,"walking and flying goal lookups themselves are level-aware");
            // The rejected portal command legitimately became ordinary MOVE. Explicit STOP makes
            // the later same-position portal command a new order, rather than a redundant move.
            command(h,ground,UnitAction.STOP,p);command(h,flying,UnitAction.STOP,p);
            BuildingServerEvents.getBuildings().add(local);
            command(h,ground,UnitAction.MOVE,p);command(h,flying,UnitAction.MOVE,p);
            h.assertTrue(groundGoal.getBuildingTarget()==local&&flyingGoal.getBuildingTarget()==local,"same-level transport remains assignable to both registered goal families");
        } finally {BuildingServerEvents.getBuildings().remove(foreign);BuildingServerEvents.getBuildings().remove(local);ground.discard();flying.discard();}
        h.succeed();
    }
}
