// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.gametest.*;
@GameTestHolder(DreamseekersMod.MOD_ID) @PrefixGameTestTemplate(false)
public final class AaroncosArenaIsolationGameTests {
    static final String BATCH="dreamseekers_arena_cleanup_isolation";
    @AfterBatch(batch=BATCH)public static void cleanup(ServerLevel level){TerrorbeakFixture.cleanup(level);}
    @GameTest(template="terror_unit_arena",batch=BATCH,timeoutTicks=580)
    public static void realArenaBlockSchedulesWildCleanupButPreservesPaidContracts(GameTestHelper h){
        var f=new TerrorbeakFixture(h);var paid=f.unit("Arena",5,8);paid.setNoAi(true);var passive=f.fox("Arena",12,8);passive.setNoAi(true);var worker=DreamseekersMod.FIREFLY_WORKER.get().create(h.getLevel());worker.setOwnerName(TerrorbeakFixture.account("Arena"));worker.setNoAi(true);f.place(worker,18,8);var wild=f.place(EntityType.COW.create(h.getLevel()),25,8);wild.setNoAi(true);var block=f.pos(15,1,22);var t=new TerrorbeakFixture.Timeline(h);
        t.at(0,()->{h.assertTrue(h.getLevel().players().isEmpty(),"no players: native Overworld early-return must not short-circuit cleanup");h.getLevel().setBlock(block,net.pasterdream.init.PasterdreamModBlocks.AARONCOSHANDSPAWNBLOCK.get().defaultBlockState(),3);var be=h.getLevel().getBlockEntity(block);h.assertTrue(be!=null,"actual native trigger block entity");be.getPersistentData().putBoolean("switch",true);be.getPersistentData().putDouble("time0",0);});
        t.at(45,()->h.assertTrue(h.getLevel().getBlockState(block).isAir()&&h.getLevel().getBlockState(block.below()).is(net.pasterdream.init.PasterdreamModBlocks.AARONCOS_HAND_CHEST.get()),"real scheduled native block tick consumes trigger and leaves original reward chest"));
        t.at(400,()->h.assertTrue(wild.isAlive()&&paid.isAlive()&&passive.isAlive()&&worker.isAlive(),"no early cleanup or paid removal"));
        t.at(465,()->{h.assertTrue(h.getLevel().players().isEmpty(),"no player changed native cleanup branch");h.assertTrue(wild.isRemoved()&&wild.getRemovalReason()==net.minecraft.world.entity.Entity.RemovalReason.DISCARDED,"real410tick callback discards original wild entity");h.assertTrue(paid.isAlive()&&!paid.isRemoved()&&passive.isAlive()&&!passive.isRemoved()&&worker.isAlive()&&!worker.isRemoved(),"combat, noncombat and worker RTS contracts excluded only from native cleanup query");System.out.println("DREAMSEEKERS_REAL_ARENA_BLOCK_20_PLUS_410_TICK_ISOLATION_PASS");});
        t.start(()->f.ready(paid,passive,worker,wild));
    }
}
