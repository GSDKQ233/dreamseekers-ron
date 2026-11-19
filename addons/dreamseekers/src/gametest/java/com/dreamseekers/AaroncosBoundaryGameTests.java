// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import com.solegendary.reignofnether.unit.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
@GameTestHolder("dreamseekers_aaroncos_boundary") @PrefixGameTestTemplate(false)
public final class AaroncosBoundaryGameTests {
 @GameTest(template="aaroncos_unit_arena",batch="aaron_boundary",timeoutTicks=1140)
 public static void leftWideActualMoveAndShift(GameTestHelper h){move(h,false);}
 @GameTest(template="aaroncos_unit_arena",batch="aaron_boundary",timeoutTicks=1140)
 public static void rightWideActualMoveAndShift(GameTestHelper h){move(h,true);}
 static void move(GameTestHelper h,boolean right){var f=new AaroncosTestFixture(h,"Wide"+right);var m=f.hand(right,"Owner",6,1,8);var a=f.p(24,0,8);var b=f.p(24,0,26);boolean[] first={false};
 var seq=f.ready(m).thenExecute(()->{f.order(m,UnitAction.MOVE,a);UnitServerEvents.addActionItem(AaroncosTestFixture.unit(m).getOwnerName(),UnitAction.MOVE,-1,new int[]{m.getId()},b,net.minecraft.core.BlockPos.ZERO,true);});
 for(int i=1;i<=1000;i++){final int tick=i;seq.thenIdle(1).thenExecute(()->{h.assertTrue(h.getLevel().noCollision(m)&&!m.noPhysics&&m.isNoGravity(),"real 3.5 body never clips");if(m.position().distanceToSqr(new net.minecraft.world.phys.Vec3(a.getX()+.5,a.getY()+1,a.getZ()+.5))<.8*.8)first[0]=true;if(tick%40==0)System.out.println("AARON_WIDE_TRACE tick="+tick+" first="+first[0]+" "+f.describe(m));});}
 seq.thenExecute(()->h.assertTrue(first[0]&&f.arrived(m,b),"true wide flight visits first existing .8-foot destination then shifted second and clears path: first="+first[0]+" "+f.describe(m))).thenSucceed();}
 @AfterBatch(batch="aaron_boundary") public static void cleanup(ServerLevel l){AaroncosTestFixture.cleanup(l);}
}
