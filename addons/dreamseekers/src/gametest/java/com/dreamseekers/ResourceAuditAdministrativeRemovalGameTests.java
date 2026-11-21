// Review-only positive regressions for the narrowly scoped permanent-admin-removal proposal.
package com.dreamseekers;
import com.dreamseekers.worldtree.*;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.sandbox.SandboxServer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("dreamseekers_audit_fixes")
@PrefixGameTestTemplate(false)
public final class ResourceAuditAdministrativeRemovalGameTests {
    private record Fixture(String owner,Resources account,DreamAnchorPlacement tree,UUID receipt){}
    private static CompoundTag snapshot(Resources r){var t=new CompoundTag();t.putInt("food",r.food);t.putInt("wood",r.wood);t.putInt("ore",r.ore);t.putInt("emerald",r.emerald);r.writeExtended(t);return t;}
    private static Fixture setup(GameTestHelper h,String suffix,int offset){
        String owner="AdminRemovalAudit_"+suffix;var level=h.getLevel();
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));
        var r=new Resources(owner,10000,10000,10000);r.extra.put(DreamPassiveEconomy.SHADOW,1000);ResourcesServerEvents.resourcesList.add(r);
        BlockPos at=BuildingAssetGameTests.outdoorPos(h,4+offset,1,4);
        for(int x=-2;x<14;x++)for(int z=-2;z<12;z++){level.setBlock(at.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<20;y++)level.setBlock(at.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        for(var b:BuildingUtils.getAbsoluteBlockData(DreamseekersMod.DREAM_ANCHOR.getRelativeBlockData(level),level,at,Rotation.NONE))level.setBlock(b.getBlockPos(),b.getBlockState(),2);
        var p=(DreamAnchorPlacement)BuildingUtils.getNewBuildingPlacement(DreamseekersMod.DREAM_ANCHOR,level,at,Rotation.NONE,owner,false);p.isBuilt=true;BuildingServerEvents.getBuildings().add(p);
        try(var peer=new PassiveIncomePeer(level,owner)){h.assertTrue(DreamWorldTreeService.start(peer.actor,p,p.tree().instance).equals("started"),"actual paid upgrade");}
        return new Fixture(owner,r,p,p.tree().receipt);
    }
    private static void cleanup(Fixture f){f.tree.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(f.tree);PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(f.owner));ResourcesServerEvents.removeAccount(f.owner);}
    @GameTest(templateNamespace="dreamseekers_audit_fixes",template="tree_economy_arena",batch="admin_removal_audit",timeoutTicks=100)
    public static void permanentRemoveForfeitsWithoutRefundOrWorldWrites(GameTestHelper h){
        var f=setup(h,"Forfeit",0);var player=PlayerServerEvents.getRTSPlayer(f.owner);
        try{
            var before=snapshot(f.account);var blocks=new LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();for(var b:f.tree.getBlocks())blocks.put(b.getBlockPos(),h.getLevel().getBlockState(b.getBlockPos()));
            try(var peer=new PassiveIncomePeer(h.getLevel(),f.owner)){
                peer.drain();PlayerServerEvents.rtsPlayers.remove(player);
                h.assertTrue(com.solegendary.reignofnether.fogofwar.FogOfWarServerEvents.canPlayerSeeBuilding(peer.actor,f.tree),"connected witness would receive the native removal packet");
                SandboxServer.removeBuilding(f.tree.originPos);
                h.assertTrue(BuildingServerEvents.getBuildings().contains(f.tree)&&snapshot(f.account).equals(before),"unavailable payer refuses administrative detachment without inventing terminal disposition");
                h.assertTrue(peer.drain().isEmpty(),"refused removal publishes no client removal or spurious balance packet");
            }
            PlayerServerEvents.rtsPlayers.add(player);SandboxServer.removeBuilding(f.tree.originPos);
            h.assertTrue(!BuildingServerEvents.getBuildings().contains(f.tree)&&!f.account.paidReceipts.containsKey(f.receipt),"real permanent remove consumed original forfeit receipt");
            h.assertTrue(f.account.food==9400&&f.account.wood==9200&&f.account.ore==9400&&f.account.extra.get(DreamPassiveEconomy.SHADOW)==850,"administrative removal does not earn a refund");
            for(var e:blocks.entrySet())h.assertTrue(h.getLevel().getBlockState(e.getKey()).equals(e.getValue()),"economic disposition writes no world blocks");
            System.out.println("RESOURCE_ADMIN_REMOVE_PASS forfeit_no_refund_no_world_write_fail_closed");h.succeed();
        }finally{if(!PlayerServerEvents.rtsPlayers.contains(player))PlayerServerEvents.rtsPlayers.add(player);cleanup(f);}
    }
    @GameTest(templateNamespace="dreamseekers_audit_fixes",template="tree_economy_arena",batch="admin_removal_audit",timeoutTicks=100)
    public static void permanentRemovePreservesEarnedOverflowRefund(GameTestHelper h){
        var f=setup(h,"Earned",0);var r=f.account;
        h.assertTrue(ResourceTransaction.creditIncome(h.getLevel().getServer(),r,r.accountEpoch,r.revision,new ResourceTransaction.Cost(Integer.MAX_VALUE,0,0)).applied(),"real income fills food capacity");
        try(var peer=new PassiveIncomePeer(h.getLevel(),f.owner)){h.assertTrue(DreamWorldTreeService.cancel(peer.actor,f.tree,f.tree.tree().instance).equals("refund_pending"),"explicit cancellation earned refund despite overflow");}
        var before=snapshot(r);SandboxServer.removeBuilding(f.tree.originPos);
        h.assertTrue(!BuildingServerEvents.getBuildings().contains(f.tree)&&snapshot(r).equals(before)&&r.paidReceipts.get(f.receipt).purpose().endsWith(":refund"),"permanent removal cannot revoke earned claim or partially refund");
        h.assertTrue(ResourceTransaction.exchange(h.getLevel().getServer(),r,r.accountEpoch,r.revision,new ResourceTransaction.Cost(600,0,0),ResourceTransaction.Cost.ZERO).applied(),"real debit frees exact refund room");
        h.runAtTickTime(40,()->{try{
            h.assertTrue(!r.paidReceipts.containsKey(f.receipt)&&r.food==Integer.MAX_VALUE&&r.wood==10000&&r.ore==10000&&r.extra.get(DreamPassiveEconomy.SHADOW)==1000,"normal retry settles exact original-epoch payment once");
            var settled=snapshot(r);WorldTreeReceiptIntents.retry(h.getLevel());h.assertTrue(snapshot(r).equals(settled),"duplicate retry mutation-free");
            System.out.println("RESOURCE_ADMIN_REMOVE_PASS earned_refund_preserved_settled_once");h.succeed();
        }finally{cleanup(f);}});
    }
    @GameTest(templateNamespace="dreamseekers_audit_fixes",template="tree_economy_arena",batch="admin_removal_audit",timeoutTicks=100)
    public static void nonPermanentCleanupNeverChangesPaidDisposition(GameTestHelper h){
        var fixtures=new ArrayList<Fixture>();
        try{
            var reasons=List.of(BuildingPlacement.RemovalReason.RELOAD,BuildingPlacement.RemovalReason.REPLACED,BuildingPlacement.RemovalReason.SERVER_STOP);
            for(int i=0;i<reasons.size();i++){
                var f=setup(h,"Cleanup"+i,i*40);fixtures.add(f);var before=snapshot(f.account);
                f.tree.onRemoved(reasons.get(i));
                h.assertTrue(snapshot(f.account).equals(before)&&f.account.paidReceipts.get(f.receipt).purpose().equals(DreamWorldTreeService.purpose(WorldTreeModel.SHADOW_V5)),"cleanup "+reasons.get(i)+" does not settle, refund, or forfeit paid gameplay state");
            }
            System.out.println("RESOURCE_ADMIN_REMOVE_PASS reload_replaced_stop_cleanup_unchanged");h.succeed();
        }finally{fixtures.forEach(ResourceAuditAdministrativeRemovalGameTests::cleanup);}
    }
}
