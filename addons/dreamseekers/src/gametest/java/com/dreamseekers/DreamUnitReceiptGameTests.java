// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.production.*;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.production.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.sandbox.SandboxServer;
import com.solegendary.reignofnether.commands.rtsapi.data_accessor.BuildingDataAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Real server admission, ledgers, editor paths and save projection. No socket/GUI claim. */
@GameTestHolder("dreamseekers_unit_receipts")
@PrefixGameTestTemplate(false)
public final class DreamUnitReceiptGameTests {
    private static final String BATCH="dreamseekers_unit_receipts";
    private static final String PREFIX="UnitReceiptGT_";
    private static final Set<String> OWNERS=Set.of(PREFIX+"Original",PREFIX+"Overflow",PREFIX+"ABA",PREFIX+"Epoch",PREFIX+"Destroy",PREFIX+"CommitFailure",PREFIX+"Detach",PREFIX+"Unavailable");
    private static final List<java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent>> JOIN_GUARDS=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level) {
        for(var guard:JOIN_GUARDS)net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(guard);JOIN_GUARDS.clear();
        PlayerServerEvents.rtsPlayers.removeIf(p->OWNERS.contains(p.name));
        for(var entity:List.copyOf(com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits()))
            if(entity instanceof com.solegendary.reignofnether.unit.interfaces.Unit unit&&OWNERS.contains(unit.getOwnerName()))entity.discard();
        for(var building:List.copyOf(BuildingServerEvents.getBuildings()))if(OWNERS.contains(building.ownerName)) {
            building.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(building);
        }
        PlayerServerEvents.rtsPlayers.removeIf(p->OWNERS.contains(p.name));
        ResourcesServerEvents.resourcesList.removeIf(r->OWNERS.contains(r.ownerName));
    }
    private record UnitReceiptHost(DreamProductionPlacement placement,DreamUnitProduction recipe) {}
    private static final class UnitReceiptCase {
        final GameTestHelper h;final ServerLevel level;final String owner;final Resources account;
        final List<UnitReceiptHost> hosts=new ArrayList<>();
        UnitReceiptCase(GameTestHelper helper,String suffix) {
            h=helper;level=h.getLevel();owner=PREFIX+suffix;
            h.assertTrue(PlayerServerEvents.getRTSPlayer(owner)==null,"unique owner");
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));
            account=new Resources(owner,10000,10000,10000);ResourcesServerEvents.resourcesList.add(account);
            install(DreamseekersMod.DREAM_ANCHOR,DreamseekersMod.WORKER_PRODUCTION,4);
            install(DreamseekersMod.BEETLE_SANCTUARY,DreamseekersMod.BEETLE_PRODUCTION,38);
            install(DreamseekersMod.DREAM_HALL,DreamseekersMod.BONE_WING_PRODUCTION,72);
        }
        void install(Building definition,DreamUnitProduction recipe,int x) {
            BlockPos at=BuildingAssetGameTests.outdoorPos(h,x,1,4);
            for(int dx=-3;dx<28;dx++)for(int dz=-3;dz<28;dz++) {
                level.setBlock(at.offset(dx,0,dz),Blocks.STONE.defaultBlockState(),2);
                for(int dy=1;dy<20;dy++)level.setBlock(at.offset(dx,dy,dz),Blocks.AIR.defaultBlockState(),2);
            }
            for(var b:BuildingUtils.getAbsoluteBlockData(definition.getRelativeBlockData(level),level,at,Rotation.NONE))level.setBlock(b.getBlockPos(),b.getBlockState(),2);
            var p=(DreamProductionPlacement)BuildingUtils.getNewBuildingPlacement(definition,level,at,Rotation.NONE,owner,false);
            BuildingServerEvents.getBuildings().add(p);p.onBuilt();hosts.add(new UnitReceiptHost(p,recipe));
        }
        void start(UnitReceiptHost host){h.assertTrue(host.placement.startProductionItem(host.recipe),"real paid queue starts: "+host.recipe.getItemName());}
        void pass(String label){System.out.println("DREAM_UNIT_RECEIPT_PASS "+label);h.succeed();}
    }
    private static boolean attemptedAt(UnitReceiptHost host,net.minecraft.world.entity.Entity entity,BlockPos expected) {
        return net.minecraft.world.entity.EntityType.getKey(entity.getType()).toString().equals(host.recipe.getItemName())
                &&entity.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(expected))<16;
    }
    private static void guard(java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> callback) {
        JOIN_GUARDS.add(callback);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.LOWEST,false,net.minecraftforge.event.entity.EntityJoinLevelEvent.class,callback);
    }
    private static void assertRolledBack(UnitReceiptCase f,net.minecraft.world.entity.Entity spawned) {
        f.h.assertTrue(spawned.isRemoved(),"exact attempted entity removed");
        f.h.assertTrue(com.solegendary.reignofnether.unit.UnitServerEvents.getAllUnits().stream().noneMatch(e->e==spawned),"no native unit tracking remains");
        try {
            var field=com.solegendary.reignofnether.unit.UnitServerEvents.class.getDeclaredField("forcedUnitChunks");field.setAccessible(true);
            var tickets=(java.util.Map<?,?>)field.get(null);f.h.assertTrue(!tickets.containsKey(spawned.getId()),"exact attempted unit ticket removed");
            var rallies=DreamProductionPlacement.class.getDeclaredField("pendingRallies");rallies.setAccessible(true);
            for(var host:f.hosts)f.h.assertTrue(((java.util.List<?>)rallies.get(host.placement)).isEmpty(),"no rejected lifetime rally remains");
        } catch(ReflectiveOperationException error){throw new RuntimeException(error);}
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=140)
    public static void reentrantDestroyDefersThenActuallyDestroysAllThree(GameTestHelper h) {
        var f=new UnitReceiptCase(h,"Destroy");
        f.install(DreamseekersMod.DREAM_ANCHOR,DreamseekersMod.WORKER_PRODUCTION,110);f.hosts.remove(3); // unrelated completed owner building prevents legitimate last-building defeat
        var attempts=new ArrayList<net.minecraft.world.entity.Entity>();
        for(var host:f.hosts) {
            BlockPos expected=host.placement.getUnitSpawnPoint(f.level,false,net.minecraft.core.Vec3i.ZERO);
            guard(event->{if(event.getLevel()==f.level&&attemptedAt(host,event.getEntity(),expected)) {
                attempts.add(event.getEntity());host.placement.destroy(f.level);
            }});
            f.start(host);host.placement.productionQueue.get(0).ticksLeft=1;
        }
        h.runAtTickTime(20,()->{
            h.assertTrue(attempts.size()==3,"each producer reached one real Join callback; actual="+attempts.size());attempts.forEach(e->assertRolledBack(f,e));
            for(var host:f.hosts)h.assertTrue(host.placement.getBlocksPlaced()==0&&host.placement.productionQueue.isEmpty(),"deferred request actually destroys geometry and drains queue");
            h.assertTrue(f.account.food==10000&&f.account.wood==10000&&f.account.ore==10000&&f.account.paidReceipts.isEmpty(),"all3 original payments refunded once");
            h.assertTrue(PlayerServerEvents.getRTSPlayer(f.owner)!=null,"uncommitted rollback did not defeat payer");
            h.assertTrue(PlayerServerEvents.getRTSPlayer(f.owner).scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED)==0,"no phantom production scores");
            f.pass("all3_reentrant_destroy");
        });
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=140)
    public static void acceptedSpawnConsumeFailureRollsBackWithoutLosingReceipt(GameTestHelper h) {
        var f=new UnitReceiptCase(h,"CommitFailure");var attempts=new ArrayList<net.minecraft.world.entity.Entity>();
        for(var host:f.hosts){f.start(host);host.placement.productionQueue.get(0).ticksLeft=1;}
        var expected=f.hosts.stream().map(host->host.placement.getUnitSpawnPoint(f.level,false,net.minecraft.core.Vec3i.ZERO)).toList();
        guard(event->{if(event.getLevel()==f.level&&java.util.stream.IntStream.range(0,f.hosts.size()).anyMatch(i->attemptedAt(f.hosts.get(i),event.getEntity(),expected.get(i)))) {
            attempts.add(event.getEntity());f.account.revision=Long.MAX_VALUE;
            // Prevent another attempted lifetime while the fault remains injected.
            for(var host:f.hosts)if(!host.placement.productionQueue.isEmpty())host.placement.productionQueue.get(0).ticksLeft=100;
        }});
        h.runAtTickTime(10,()->{
            h.assertTrue(attempts.size()==1,"one genuinely attempted first completion; actual="+attempts.size());attempts.forEach(e->assertRolledBack(f,e));
            h.assertTrue(f.account.paidReceipts.size()==3,"consume failure retains every paid claim");
            h.assertTrue(f.hosts.stream().allMatch(host->host.placement.productionQueue.size()==1),"no phantom queue completion");
            f.account.revision=100;
            for(var host:f.hosts)h.assertTrue(host.placement.cancelProductionItem(host.recipe,true),"fault recovered queue cancels");
            h.assertTrue(f.account.food==10000&&f.account.wood==10000&&f.account.ore==10000&&f.account.paidReceipts.isEmpty(),"recovered refunds exact once");
            h.assertTrue(PlayerServerEvents.getRTSPlayer(f.owner).scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED)==0,"failed commit never scores");
            f.pass("accepted_spawn_failed_commit");
        });
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=140)
    public static void reentrantDetachCannotRestartOrCompleteOldLifetime(GameTestHelper h) {
        var f=new UnitReceiptCase(h,"Detach");var attempts=new ArrayList<net.minecraft.world.entity.Entity>();
        var host=f.hosts.get(0);
        BlockPos expected=host.placement.getUnitSpawnPoint(f.level,false,net.minecraft.core.Vec3i.ZERO);
        guard(event->{if(event.getLevel()==f.level&&attemptedAt(host,event.getEntity(),expected)) {
            attempts.add(event.getEntity());host.placement.onRemoved(BuildingPlacement.RemovalReason.REPLACED);
            BuildingServerEvents.getBuildings().remove(host.placement);
            for(var other:f.hosts)if(other!=host){other.placement.onRemoved(BuildingPlacement.RemovalReason.REPLACED);BuildingServerEvents.getBuildings().remove(other.placement);}
            h.assertTrue(!host.placement.startProductionItem(host.recipe),"callback cannot restart detached producer");
        }});
        f.start(host);host.placement.productionQueue.get(0).ticksLeft=1;
        h.runAtTickTime(25,()->{
            h.assertTrue(attempts.size()==1,"one old lifetime Join; actual="+attempts.size());attempts.forEach(e->assertRolledBack(f,e));
            h.assertTrue(host.placement.productionQueue.isEmpty()&&!host.placement.startProductionItem(host.recipe),"detached producer stays terminal after unwind");
            h.assertTrue(f.account.food==10000&&f.account.paidReceipts.isEmpty(),"detached claim retries once");
            h.assertTrue(PlayerServerEvents.getRTSPlayer(f.owner)!=null,"rollback with no remaining buildings does not trigger native defeat");f.pass("reentrant_detach_terminal");
        });
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=120)
    public static void allThreeOriginalPaidSaveCopyAndCancel(GameTestHelper h) {
        var f=new UnitReceiptCase(h,"Original");
        for(var host:f.hosts) {
            int before=f.account.food;f.start(host);var recipe=host.recipe;
            h.assertTrue(f.account.food==before-recipe.defaultCost.food&&f.account.paidReceipts.size()==1,"one debit and durable claim");
            int original=recipe.defaultCost.food;
            try {
                recipe.defaultCost.food=original+37;
                ResourcesServerEvents.saveResources(f.level);
                var copy=ResourcesSaveData.getInstance(f.level).resources.stream().filter(r->r.ownerName.equals(f.owner)).findFirst().orElseThrow();
                h.assertTrue(copy.food==before&&copy.paidReceipts.isEmpty(),"save copy refunds original once and removes copy claim");
                h.assertTrue(f.account.food==before-original&&f.account.paidReceipts.size()==1&&host.placement.productionQueue.size()==1,"save does not refund/drain live queue");
                h.assertTrue(host.placement.cancelProductionItem(recipe,true),"cancel accepted");
                h.assertTrue(f.account.food==before&&f.account.paidReceipts.isEmpty(),"live cancel original price, not changed quote");
                h.assertTrue(!host.placement.cancelProductionItem(recipe,true)&&f.account.food==before,"duplicate cancel no windfall");
            } finally {recipe.defaultCost.food=original;}
        }
        f.pass("all3_original_save_cancel");
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=120)
    public static void overflowRefundRetainsClaimUntilRoom(GameTestHelper h) {
        var f=new UnitReceiptCase(h,"Overflow");
        for(var host:f.hosts) {
            f.account.food=10000;f.start(host);UUID receipt=f.account.paidReceipts.keySet().iterator().next();
            f.account.food=Integer.MAX_VALUE;
            h.assertTrue(host.placement.cancelProductionItem(host.recipe,true),"cancel detaches paid job");
            h.assertTrue(host.placement.productionQueue.isEmpty()&&f.account.paidReceipts.containsKey(receipt)&&f.account.food==Integer.MAX_VALUE,"overflow retains original ledger claim without wrap");
            ResourcesServerEvents.saveResources(f.level);
            var copy=ResourcesSaveData.getInstance(f.level).resources.stream().filter(r->r.ownerName.equals(f.owner)).findFirst().orElseThrow();
            h.assertTrue(copy.paidReceipts.containsKey(receipt)&&copy.food==Integer.MAX_VALUE,"overflow claim survives saved account copy");
            int paid=f.account.paidReceipts.get(receipt).food();f.account.food=Integer.MAX_VALUE-paid;
            h.assertTrue(ResourceTransaction.refund(f.level.getServer(),f.account,f.account.accountEpoch,f.account.revision,receipt,10000).applied(),"original claim settles exactly when room exists");
            h.assertTrue(f.account.food==Integer.MAX_VALUE&&!f.account.paidReceipts.containsKey(receipt),"no residual duplicated claim");
        }
        f.pass("all3_overflow_claim");
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=120)
    public static void supportedEditorABACancelsOriginalPayerAndSameOwnerNoop(GameTestHelper h) throws Exception {
        var f=new UnitReceiptCase(h,"ABA");
        for(var host:f.hosts) {
            f.start(host);long revision=host.placement.getCaptureRevision();
            SandboxServer.setBuildingOwner(host.placement.originPos,f.owner);
            h.assertTrue(host.placement.getCaptureRevision()==revision,"same owner no-op");
            SandboxServer.setBuildingOwner(host.placement.originPos,f.owner+"Other");
            SandboxServer.setBuildingOwner(host.placement.originPos,f.owner);
            h.assertTrue(host.placement.getCaptureRevision()==revision+2,"actual sandbox ABA observes both transitions");
            h.assertTrue(host.placement.cancelProductionItem(host.recipe,true)&&f.account.food==10000,"ABA original payment settles once");
            f.start(host);revision=host.placement.getCaptureRevision();
            var accessor=new BuildingDataAccessor(host.placement);var data=accessor.getData();
            data.putString("ownerName",f.owner+"Other");accessor.setData(data);
            data.putString("ownerName",f.owner);accessor.setData(data);
            h.assertTrue(host.placement.getCaptureRevision()==revision+2,"actual data accessor ABA observes both transitions");
            h.assertTrue(host.placement.cancelProductionItem(host.recipe,true)&&f.account.food==10000,"data ABA original payment settles once");
        }
        f.pass("all3_supported_editor_ABA");
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=120)
    public static void temporarilyUnavailableRtsActorRetainsOriginalAccountClaims(GameTestHelper h) {
        var f=new UnitReceiptCase(h,"Unavailable");for(var host:f.hosts)f.start(host);
        var player=PlayerServerEvents.getRTSPlayer(f.owner);PlayerServerEvents.rtsPlayers.remove(player);
        for(var host:f.hosts)h.assertTrue(host.placement.cancelProductionItem(host.recipe,true),"queue detaches while transaction actor unavailable");
        h.assertTrue(f.account.paidReceipts.size()==3,"original authoritative account retains all pending claims");
        var nbt=new net.minecraft.nbt.CompoundTag();ResourceAccountCodec.write(f.account,nbt);
        var roundtrip=new Resources(f.owner,f.account.food,f.account.wood,f.account.ore);ResourceAccountCodec.read(roundtrip,nbt);
        h.assertTrue(roundtrip.accountEpoch.equals(f.account.accountEpoch)&&roundtrip.paidReceipts.equals(f.account.paidReceipts),"pending claims and epoch survive core codec without available actor");
        PlayerServerEvents.rtsPlayers.add(player);
        h.runAtTickTime(25,()->{h.assertTrue(f.account.food==10000&&f.account.wood==10000&&f.account.ore==10000&&f.account.paidReceipts.isEmpty(),"ordinary retry after actor returns settles once");f.pass("retained_account_unavailable_actor");});
    }
    @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=120)
    public static void accountEpochReplacementCannotReceiveOldRefund(GameTestHelper h) {
        var f=new UnitReceiptCase(h,"Epoch");
        for(var host:f.hosts)f.start(host);
        ResourcesServerEvents.resourcesList.remove(f.account);
        var replacement=new Resources(f.owner,1234,2345,3456);ResourcesServerEvents.resourcesList.add(replacement);
        for(var host:f.hosts)host.placement.cancelProductionItem(host.recipe,true);
        h.assertTrue(replacement.food==1234&&replacement.wood==2345&&replacement.ore==3456&&replacement.paidReceipts.isEmpty(),"replacement epoch never receives old claims");
        h.assertTrue(f.account.paidReceipts.size()==3,"temporarily detached original account retains claims");
        ResourcesServerEvents.resourcesList.remove(replacement);ResourcesServerEvents.resourcesList.add(f.account);
        h.runAtTickTime(25,()->{h.assertTrue(f.account.food==10000&&f.account.wood==10000&&f.account.ore==10000&&f.account.paidReceipts.isEmpty(),"restored original account retries detached refunds exactly once");f.pass("all3_epoch_and_retry");});
    }
}
