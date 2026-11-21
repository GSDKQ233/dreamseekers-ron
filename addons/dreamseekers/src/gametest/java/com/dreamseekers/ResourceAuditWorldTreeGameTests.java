package com.dreamseekers;

import com.dreamseekers.worldtree.*;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.sandbox.SandboxServer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Regression coverage for command/editor ownership lifecycle and paid-upgrade receipt disposition.
 * State persistence checks use real state-codec roundtrips, NOT two-JVM restart coverage.
 * Also verifies externally reachable sandbox removal and the normal receipt retry handler.
 */
@GameTestHolder("dreamseekers_audit_fixes")
@PrefixGameTestTemplate(false)
public final class ResourceAuditWorldTreeGameTests {
    private record Fixture(String owner, Resources account, DreamAnchorPlacement tree) {}
    private static Fixture setup(GameTestHelper h,String suffix) {
        String owner="ResourceAudit_"+suffix;
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));
        Resources r=new Resources(owner,10000,10000,10000);
        r.extra.put(DreamPassiveEconomy.SHADOW,1000);
        ResourcesServerEvents.resourcesList.add(r);
        BlockPos origin=BuildingAssetGameTests.outdoorPos(h,4,1,4);
        for(int x=-2;x<14;x++)for(int z=-2;z<12;z++) {
            h.getLevel().setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
            for(int y=1;y<20;y++)h.getLevel().setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
        }
        for(var b:BuildingUtils.getAbsoluteBlockData(DreamseekersMod.DREAM_ANCHOR.getRelativeBlockData(h.getLevel()),h.getLevel(),origin,Rotation.NONE))
            h.getLevel().setBlock(b.getBlockPos(),b.getBlockState(),2);
        var p=(DreamAnchorPlacement)BuildingUtils.getNewBuildingPlacement(DreamseekersMod.DREAM_ANCHOR,h.getLevel(),origin,Rotation.NONE,owner,false);
        p.isBuilt=true; BuildingServerEvents.getBuildings().add(p);
        try(var peer=new PassiveIncomePeer(h.getLevel(),owner)) {
            h.assertTrue(DreamWorldTreeService.start(peer.actor,p,p.tree().instance).equals("started"),"real paid upgrade starts");
        }
        return new Fixture(owner,r,p);
    }
    private static void cleanup(Fixture f) {
        f.tree.onRemoved(BuildingPlacement.RemovalReason.RELOAD);
        BuildingServerEvents.getBuildings().remove(f.tree);
        PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(f.owner));
        ResourcesServerEvents.removeAccount(f.owner);
    }
    @GameTest(templateNamespace="dreamseekers_audit_fixes",template="tree_economy_arena",batch="resource_audit_tree",timeoutTicks=100)
    public static void playerOwnerCommandSynchronouslyDisposesPaidUpgradeAndPreservesNoop(GameTestHelper h) {
        var f=setup(h,"PlayerCommand");
        try {
            UUID receipt=f.tree.tree().receipt;
            long revision=f.tree.getCaptureRevision(), lastTick=f.tree.tree().lastTick;
            int remaining=f.tree.tree().remaining;
            h.assertTrue(BuildingOwnerCommandFixture.transfer(h,f.tree,f.owner,f.owner)==1,"same-owner command still reports selected building");
            h.assertTrue(f.tree.getCaptureRevision()==revision&&f.tree.tree().receipt.equals(receipt)
                &&f.tree.tree().remaining==remaining&&f.tree.tree().phase==WorldTreeState.Phase.RUNNING,
                "same-owner command does not capture or cancel a paid upgrade");
            // Keep the refund pending so its persisted disposition, not merely a balance change, is observable.
            f.account.food=Integer.MAX_VALUE;
            String other=f.owner+"Other";
            h.assertTrue(BuildingOwnerCommandFixture.transfer(h,f.tree,f.owner,other)==1,"ordinary transfer selects one building");
            h.assertTrue(f.tree.ownerName.equals(other)&&f.tree.getCaptureRevision()==revision+1,
                "ordinary command transfer advances authority immediately");
            h.assertTrue(f.tree.tree().phase==WorldTreeState.Phase.REFUND_PENDING
                &&f.account.paidReceipts.get(receipt).purpose().equals(DreamWorldTreeService.purpose(WorldTreeModel.SHADOW_V5)+":refund"),
                "capture callback establishes persistent refund before any subsequent tick");
            h.assertTrue(BuildingOwnerCommandFixture.transfer(h,f.tree,other,f.owner)==1,"same-tick return transfer succeeds");
            h.assertTrue(f.tree.ownerName.equals(f.owner)&&f.tree.getCaptureRevision()==revision+2
                &&f.tree.tree().lastTick==lastTick,"A to B to A cannot hide either capture and runs without a tree tick");
            var restored=WorldTreeState.read(f.tree.tree().write());
            h.assertTrue(restored.supported&&restored.phase==WorldTreeState.Phase.REFUND_PENDING,
                "same-tick command ABA persists terminal upgrade disposition");
            f.tree.getDataStorage().setData(DreamWorldTreeService.STATE,restored);
            f.account.food=9400;
            WorldTreeReceiptIntents.retry(h.getLevel());
            h.assertTrue(!f.account.paidReceipts.containsKey(receipt)&&f.account.food==10000
                &&f.account.wood==10000&&f.account.ore==10000&&f.account.extra.get(DreamPassiveEconomy.SHADOW)==1000,
                "original payer receives exact paid vector once after overflow clears");
            WorldTreeReceiptIntents.retry(h.getLevel());
            h.assertTrue(f.account.food==10000&&f.account.wood==10000,"repeated retry cannot duplicate refund");
            h.assertTrue(BuildingOwnerCommandFixture.execute(h,f.tree," \""+f.owner+"\"")==1,
                "owner-filtered remove command succeeds");
            h.assertTrue(f.tree.ownerName.isEmpty()&&f.tree.getCaptureRevision()==revision+3,
                "owner-filtered removal uses capture lifecycle");
            BuildingOwnerCommandFixture.transfer(h,f.tree,"",f.owner);
            h.assertTrue(BuildingOwnerCommandFixture.execute(h,f.tree,"")==1
                &&f.tree.ownerName.isEmpty()&&f.tree.getCaptureRevision()==revision+5,
                "unfiltered removal uses capture lifecycle");
            System.out.println("PLAYER_BUILDING_OWNER_COMMAND_PASS noop transfer same_tick_ABA refund_persistence both_remove_branches");
            h.succeed();
        } finally {cleanup(f);}
    }
    @GameTest(templateNamespace="dreamseekers_audit_fixes",template="tree_economy_arena",batch="resource_audit_tree",timeoutTicks=100)
    public static void editorAbaBeforeStateRoundtripMustNotResumeOldPaidUpgrade(GameTestHelper h) {
        var f=setup(h,"Aba");
        try {
            UUID id=f.tree.tree().receipt;
            SandboxServer.setBuildingOwner(f.tree.originPos,f.owner+"Other");
            SandboxServer.setBuildingOwner(f.tree.originPos,f.owner);
            h.assertTrue(f.tree.getCaptureRevision()==2,"both supported editor owner transitions observed");
            // Same serialization used by DataStorage ordinary building save/load. No native tick in between.
            var encoded=f.tree.tree().write();
            var restored=WorldTreeState.read(encoded);
            h.assertTrue(restored.supported,"pending payload roundtrips as supported");
            f.tree.getDataStorage().setData(DreamWorldTreeService.STATE,restored);
            DreamWorldTreeService.tick(f.tree,h.getLevel());
            WorldTreeReceiptIntents.retry(h.getLevel());
            System.out.println("RESOURCE_AUDIT_ABA actual_phase="+f.tree.tree().phase+" remaining="+f.tree.tree().remaining+" receipt="+f.account.paidReceipts.get(id));
            h.assertTrue(!f.account.paidReceipts.containsKey(id)&&f.tree.tree().phase==WorldTreeState.Phase.IDLE,
                "supported editor capture must cancel/refund original upgrade even if saved before the next tick");
            h.succeed();
        } finally {cleanup(f);}
    }
    @GameTest(templateNamespace="dreamseekers_audit_fixes",template="tree_economy_arena",batch="resource_audit_tree",timeoutTicks=100)
    public static void administrativeRemovalMustNotLeaveForeverPendingTreeReceipt(GameTestHelper h) {
        var f=setup(h,"Removal");UUID id=f.tree.tree().receipt;
        SandboxServer.removeBuilding(f.tree.originPos);
        h.assertTrue(!BuildingServerEvents.getBuildings().contains(f.tree),"real sandbox removal detached tree");
        h.runAtTickTime(30,()-> {
            try {
                WorldTreeReceiptIntents.retry(h.getLevel());
                var receipt=f.account.paidReceipts.get(id);
                System.out.println("RESOURCE_AUDIT_REMOVE surviving_receipt="+receipt);
                // Administrative destruction may refund or forfeit by policy; neither should stay pending forever.
                h.assertTrue(receipt==null||!receipt.purpose().equals(DreamWorldTreeService.purpose(WorldTreeModel.SHADOW_V5)),
                    "permanently removed tree must assign refund/forfeit disposition instead of orphan pending receipt");
                h.succeed();
            } finally {cleanup(f);}
        });
    }
}
