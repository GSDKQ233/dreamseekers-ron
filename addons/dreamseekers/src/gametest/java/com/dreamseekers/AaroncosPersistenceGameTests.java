// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import com.dreamseekers.aaroncos.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.*;
@GameTestHolder(DreamseekersMod.MOD_ID) @PrefixGameTestTemplate(false)
public final class AaroncosPersistenceGameTests {
 static net.minecraft.world.Difficulty previous;
 @GameTest(template="aaroncos_unit_arena",batch="aaron_peaceful",timeoutTicks=140)
 public static void bothControlledHandsPersistInPeacefulAndWildBossesDisappear(GameTestHelper h){var f=new AaroncosTestFixture(h,"Peaceful");var l=f.hand(false,"Owner",10,1,10);var r=f.hand(true,"Owner",40,1,10);var wl=AaroncosNativeGameTests.wild(f,false,10,32);var wr=AaroncosNativeGameTests.wild(f,true,40,32);f.ready(l,r,wl,wr).thenExecute(()->{previous=h.getLevel().getDifficulty();h.getLevel().getServer().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL,true);}).thenIdle(8).thenExecute(()->{h.getLevel().getServer().setDifficulty(previous,true);previous=null;h.assertTrue(l.isAlive()&&r.isAlive()&&!l.isRemoved()&&!r.isRemoved()&&wl.isRemoved()&&wr.isRemoved(),"both paid persistent; both original Monsters still peaceful-despawn despite NoAI");}).thenSucceed();}
 @AfterBatch(batch="aaron_peaceful") public static void cleanup(ServerLevel l){if(previous!=null){l.getServer().setDifficulty(previous,true);previous=null;}AaroncosTestFixture.cleanup(l);}
}
