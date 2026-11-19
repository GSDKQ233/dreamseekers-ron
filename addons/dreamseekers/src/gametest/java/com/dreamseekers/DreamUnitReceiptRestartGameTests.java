// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.production.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.io.File;
import java.util.*;

/** Two independent JVMs, ordinary server stop/save/start loaders. Verify never injects accounts or buildings. */
@GameTestHolder("dreamseekers_unit_receipt_restart")
@PrefixGameTestTemplate(false)
public final class DreamUnitReceiptRestartGameTests {
    private static final String NORMAL="UnitReceiptRestart_Normal",OVERFLOW="UnitReceiptRestart_Overflow";
    private static final File MANIFEST=new File("unit-receipt-restart-expectations.nbt");
    private static final BlockPos ORIGIN=new BlockPos(10240,100,10240);
    @GameTest(template="dreamseekers_test_arena",batch="dreamseekers_unit_receipt_restart",timeoutTicks=200)
    public static void realRestartRefundsOriginalPaidAndRetainsOverflowClaims(GameTestHelper h)throws Exception {
        switch(System.getProperty("dreamseekers.unit.receipt.restart.phase","")) {
            case "prepare" -> prepare(h);
            case "verify" -> verify(h);
            default -> throw new IllegalArgumentException("prepare|verify required");
        }
    }
    private static Resources account(String owner){return ExtendedResourcesSync.account(owner);}
    private static void prepare(GameTestHelper h)throws Exception {
        h.assertTrue(!MANIFEST.exists(),"fresh independent restart world");
        var expected=new CompoundTag();expected.putLong("jvmStart",java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        var chunks=new ArrayList<Long>();
        for(int x=639;x<=647;x++)for(int z=639;z<=645;z++) {
            long c=ChunkPos.asLong(x,z);h.assertTrue(!h.getLevel().getForcedChunks().contains(c),"fresh local fixture chunk");h.getLevel().setChunkForced(x,z,true);chunks.add(c);
        }
        expected.putLongArray("chunks",chunks);var buildings=new ListTag();
        for(int index=0;index<2;index++) {
            String owner=index==0?NORMAL:OVERFLOW;
            h.assertTrue(PlayerServerEvents.getRTSPlayer(owner)==null,"fresh named account");
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(owner,DreamseekersMod.DREAMSEEKERS,95100+index,0,false,0L,0L));
            Resources r=new Resources(owner,10000,10000,10000);ResourcesServerEvents.resourcesList.add(r);expected.putUUID(owner+"Epoch",r.accountEpoch);
            var definitions=List.<Building>of(DreamseekersMod.DREAM_ANCHOR,DreamseekersMod.BEETLE_SANCTUARY,DreamseekersMod.DREAM_HALL);
            var recipes=List.of(DreamseekersMod.WORKER_PRODUCTION,DreamseekersMod.BEETLE_PRODUCTION,DreamseekersMod.BONE_WING_PRODUCTION);
            var hosts=new ArrayList<DreamProductionPlacement>();
            for(int i=0;i<3;i++) {
                BlockPos at=ORIGIN.offset(i*36,0,index*48);var definition=definitions.get(i);
                for(int x=-4;x<28;x++)for(int z=-4;z<28;z++) {
                    h.getLevel().setBlock(at.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
                    for(int y=1;y<20;y++)h.getLevel().setBlock(at.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
                }
                for(var block:BuildingUtils.getAbsoluteBlockData(definition.getRelativeBlockData(h.getLevel()),h.getLevel(),at,Rotation.NONE))h.getLevel().setBlock(block.getBlockPos(),block.getBlockState(),2);
                var p=(DreamProductionPlacement)BuildingUtils.getNewBuildingPlacement(definition,h.getLevel(),at,Rotation.NONE,owner,false);
                BuildingServerEvents.getBuildings().add(p);p.onBuilt();hosts.add(p);
                var recipe=recipes.get(i);int original=recipe.defaultCost.food;
                try {
                    recipe.defaultCost.food=original+37;h.assertTrue(p.startProductionItem(recipe),"real paid admission with deliberately distinct original quote");
                } finally {recipe.defaultCost.food=original;}
                p.productionQueue.get(0).ticksLeft=10000; // isolate persistence, not a normal-duration claim
                var row=new CompoundTag();row.putString("owner",owner);row.putLong("origin",at.asLong());buildings.add(row);
            }
            h.assertTrue(r.paidReceipts.size()==3,"three durable original unit claims");
            if(index==1) {
                r.food=Integer.MAX_VALUE;
                for(var host:hosts)h.assertTrue(host.cancelProductionItem(host.productionQueue.get(0).item,true),"overflow cancellation detaches runtime queue");
                h.assertTrue(r.paidReceipts.size()==3,"overflow doesn't consume claims");
                var receipts=new ListTag();int total=0;
                for(var receipt:r.paidReceipts.values()){var t=new CompoundTag();t.putUUID("id",receipt.id());t.putInt("food",receipt.food());receipts.add(t);total+=receipt.food();}
                expected.put("overflowReceipts",receipts);expected.putInt("overflowFood",total);
            }
        }
        expected.put("buildings",buildings);NbtIo.writeCompressed(expected,MANIFEST);
        h.runAtTickTime(5,()->{h.assertTrue(account(NORMAL).paidReceipts.size()==3,"live queue not refunded by unrelated save");System.out.println("DREAM_UNIT_RECEIPT_RESTART_PREPARE_PASS");h.succeed();});
    }
    private static void verify(GameTestHelper h)throws Exception {
        var expected=NbtIo.readCompressed(MANIFEST);
        h.assertTrue(expected.getLong("jvmStart")!=java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime(),"distinct real JVM, not in-memory reload");
        Resources normal=account(NORMAL),overflow=account(OVERFLOW);
        h.assertTrue(normal!=null&&overflow!=null&&PlayerServerEvents.getRTSPlayer(NORMAL)!=null&&PlayerServerEvents.getRTSPlayer(OVERFLOW)!=null,"ordinary account/RTS loaders restored identities");
        h.assertTrue(normal.accountEpoch.equals(expected.getUUID(NORMAL+"Epoch"))&&overflow.accountEpoch.equals(expected.getUUID(OVERFLOW+"Epoch")),"original epochs preserved");
        h.assertTrue(normal.food==10000&&normal.wood==10000&&normal.ore==10000&&normal.paidReceipts.isEmpty(),"normal stop refunded exact original price not current base price");
        var restored=new ArrayList<DreamProductionPlacement>();
        for(var raw:expected.getList("buildings",Tag.TAG_COMPOUND)) {
            var row=(CompoundTag)raw;
            var p=(DreamProductionPlacement)BuildingServerEvents.getBuildings().stream().filter(b->b.ownerName.equals(row.getString("owner"))&&b.originPos.asLong()==row.getLong("origin")).findFirst().orElseThrow();
            h.assertTrue(p.productionQueue.isEmpty(),"ordinary restarted producer never restores already-refunded queue");restored.add(p);
        }
        h.assertTrue(overflow.food==Integer.MAX_VALUE&&overflow.paidReceipts.size()==3,"saved overflow claims remain pending without clipping");
        for(var raw:expected.getList("overflowReceipts",Tag.TAG_COMPOUND)){var row=(CompoundTag)raw;var paid=overflow.paidReceipts.get(row.getUUID("id"));h.assertTrue(paid!=null&&paid.food()==row.getInt("food"),"exact original immutable quote survives disk");}
        overflow.food=Integer.MAX_VALUE-expected.getInt("overflowFood");
        h.runAtTickTime(25,()->{
            h.assertTrue(overflow.food==Integer.MAX_VALUE&&overflow.wood==10000&&overflow.ore==10000&&overflow.paidReceipts.isEmpty(),"ordinary new-JVM retry refunds exactly once after capacity returns");
            h.assertTrue(normal.food==10000&&normal.paidReceipts.isEmpty(),"normal saved refund not duplicated on retry");
            BuildingServerEvents.getBuildings().removeAll(restored);for(var p:restored)p.onRemoved(BuildingPlacement.RemovalReason.RELOAD);
            PlayerServerEvents.rtsPlayers.removeIf(p->Set.of(NORMAL,OVERFLOW).contains(p.name));ResourcesServerEvents.resourcesList.removeIf(r->Set.of(NORMAL,OVERFLOW).contains(r.ownerName));
            for(long c:expected.getLongArray("chunks")){var pos=new ChunkPos(c);h.getLevel().setChunkForced(pos.x,pos.z,false);}
            System.out.println("DREAM_UNIT_RECEIPT_RESTART_VERIFY_PASS exact_original_refund pending_overflow empty_queues distinct_JVM");h.succeed();
        });
    }
}
