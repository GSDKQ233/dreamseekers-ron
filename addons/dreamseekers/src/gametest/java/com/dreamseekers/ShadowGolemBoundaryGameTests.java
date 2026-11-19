// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraftforge.gametest.*;
import com.solegendary.reignofnether.unit.UnitAction;
@GameTestHolder("dreamseekers_golem_boundary") @PrefixGameTestTemplate(false)
public final class ShadowGolemBoundaryGameTests {
    @AfterBatch(batch="golem_boundary") public static void cleanup(ServerLevel l){ShadowGolemFixture.cleanup(l);}
    @GameTest(template="golem_unit_arena",batch="golem_boundary",timeoutTicks=1800)
    public static void cardinalAndDiagonalMovesReachBothExistingArrivalPredicates(GameTestHelper h){
        var f=new ShadowGolemFixture(h);var u=f.unit("Cardinal",7,7);
        BlockPos[] destinations={f.pos(23,0,7),f.pos(23,0,23),f.pos(7,0,23),f.pos(7,0,7),f.pos(22,0,22)};
        var t=new ShadowGolemFixture.Timeline(h);int start=0;
        for(int leg=0;leg<destinations.length;leg++){
            final int index=leg;final BlockPos target=destinations[leg];int budget=leg==4?360:280;
            t.at(start,()->f.order(u,UnitAction.MOVE,target));
            for(int i=1;i<=budget;i++){final int step=i;t.at(start+i,()->{
                h.assertTrue(h.getLevel().noCollision(u)&&!u.isFlyingUnit()&&!u.isNoGravity()&&u.getY()<f.pos(0,2,0).getY(),"real wide ground actor never clips or flies");
                if(step%40==0||step==budget)System.out.println("GOLEM_CARDINAL_TRACE leg="+index+" step="+step+" "+f.state(u)+" center2="+u.distanceToSqr(target.getCenter())+" block2="+u.getOnPos().distSqr(target)+" vel="+u.getDeltaMovement());
            });}
            t.at(start+budget,()->h.assertTrue(f.near(u,target)&&u.getOnPos().distSqr(target)<=u.getMoveGoal().getMinDistToRecalculateSqr()&&u.getMoveGoal().getMoveTarget()==null,"both unchanged ground arrival predicates and real command clear leg="+index+" "+f.state(u)));
            start+=budget+5;
        }
        t.start(u,()->f.ready(u));
    }
    @GameTest(template="golem_unit_arena",batch="golem_boundary",timeoutTicks=750)
    public static void bodyRejectsNarrowLowThenCrossesActualThreeByFour(GameTestHelper h){ShadowGolemMovementGameTests.bodyNeedsBothWidthAndFourBlockHeadroom(h);}
    @GameTest(template="golem_unit_arena",batch="golem_boundary",timeoutTicks=450)
    public static void realDelayedRecomputeAndStopNeverReviveLastNodeButNewMoveStillWorks(GameTestHelper h)throws Exception {
        var f=new ShadowGolemFixture(h);var u=f.unit("StopRecompute",7,7);var first=f.pos(23,0,7);var second=f.pos(7,0,23);
        var delayed=net.minecraft.world.entity.ai.navigation.PathNavigation.class.getDeclaredField("hasDelayedRecomputation");delayed.setAccessible(true);
        var t=new ShadowGolemFixture.Timeline(h);net.minecraft.world.phys.Vec3[] stopped={null};
        t.at(0,()->f.order(u,UnitAction.MOVE,first));
        t.at(25,()->{var nav=u.getNavigation();h.assertTrue(nav.getPath()!=null&&!nav.isDone()&&nav.getTargetPos()!=null,"real active wide ground path before deferred recompute");nav.recomputePath();nav.recomputePath();try{h.assertTrue(delayed.getBoolean(nav),"real two recomputations arm vanilla deferred branch");}catch(Exception e){throw new RuntimeException(e);}
            new com.solegendary.reignofnether.unit.UnitActionItem(u.getOwnerName(),UnitAction.STOP,-1,new int[]{u.getId()},BlockPos.ZERO,BlockPos.ZERO).action(h.getLevel());});
        t.at(29,()->stopped[0]=u.position());
        for(int i=30;i<=90;i++)t.at(i,()->{try{h.assertTrue(u.getNavigation().isDone()&&u.getNavigation().getTargetPos()==null&&!delayed.getBoolean(u.getNavigation())&&u.getMoveGoal().getMoveTarget()==null&&u.position().distanceToSqr(stopped[0])<.1,"actual STOP cannot resurrect original or retained last node beyond20tick recompute window");}catch(Exception e){throw new RuntimeException(e);}});
        t.at(91,()->f.order(u,UnitAction.MOVE,second));t.at(350,()->h.assertTrue(f.near(u,second)&&u.getMoveGoal().getMoveTarget()==null,"genuinely new MOVE works after STOP"));t.start(u,()->f.ready(u));
    }

}
