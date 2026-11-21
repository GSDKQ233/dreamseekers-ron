package com.dreamseekers;
import com.dreamseekers.worldtree.*;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;
@GameTestHolder("dreamseekers_tree_e") @PrefixGameTestTemplate(false)
public final class WorldTreeReceiptGameTests {
 @GameTest(templateNamespace="dreamseekers_tree_e",template="ore_mine_test_arena",batch="tree_e",timeoutTicks=200)
 public static void captureAbaAndDestroyKeepOriginalRefundIntent(GameTestHelper h){
  String a="TreeReceipt_A",b="TreeReceipt_B";var placements=new ArrayList<BuildingPlacement>();var mobs=new ArrayList<Mob>();
  PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(a,DreamseekersMod.DREAMSEEKERS,94331,0,false,0L,0L));PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(b,DreamseekersMod.DREAMSEEKERS,94332,0,false,0L,0L));
  var ra=new Resources(a,10000,10000,10000);ra.extra.put(DreamPassiveEconomy.SHADOW,1000);ResourcesServerEvents.resourcesList.add(ra);var rb=new Resources(b,10000,10000,10000);ResourcesServerEvents.resourcesList.add(rb);
  try(var peer=new PassiveIncomePeer(h.getLevel(),a)){
   DreamAnchorPlacement p=null;
   for(int n=0;n<3;n++){
    var origin=h.absolutePos(new BlockPos(250+n*45,1,250));for(int x=-2;x<13;x++)for(int z=-2;z<11;z++){h.getLevel().setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<19;y++)h.getLevel().setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
    for(var block:BuildingUtils.getAbsoluteBlockData(DreamseekersMod.DREAM_ANCHOR.getRelativeBlockData(h.getLevel()),h.getLevel(),origin,Rotation.NONE))h.getLevel().setBlock(block.getBlockPos(),block.getBlockState(),2);
    var tree=(DreamAnchorPlacement)BuildingUtils.getNewBuildingPlacement(DreamseekersMod.DREAM_ANCHOR,h.getLevel(),origin,Rotation.NONE,n==2?b:a,false);tree.isBuilt=true;BuildingServerEvents.getBuildings().add(tree);placements.add(tree);if(n==0)p=tree;
   }
   h.assertTrue(DreamWorldTreeService.start(peer.actor,p,p.tree().instance).equals("started"),"realpendingpurchase before capture");UUID receipt=p.tree().receipt;var paid=ra.paidReceipts.get(receipt);ra.food=Integer.MAX_VALUE; // explicit legitimate refund-overflow boundary
   var attacker=EntityRegistrar.PILLAGER_UNIT.get().create(h.getLevel());((Unit)attacker).setOwnerName(b);attacker.setNoAi(true);attacker.moveTo(p.centrePos.getX()+.5,p.originPos.getY()+2,p.centrePos.getZ()+.5);h.getLevel().addFreshEntity(attacker);mobs.add(attacker);
   h.assertTrue(PassiveCaptureFixture.capture(p,h.getLevel())&&p.ownerName.equals(b),"canonical capture invokes pre-defeat lifecycle hook");
   h.assertTrue(ra.paidReceipts.get(receipt).purpose().equals((DreamWorldTreeService.purpose(WorldTreeModel.SHADOW_V5)+":refund"))&&p.tree().phase==WorldTreeState.Phase.REFUND_PENDING,"same-tick capture immediately establishes persistent earnedrefund before nexttick");
   attacker.discard();var counter=EntityRegistrar.PILLAGER_UNIT.get().create(h.getLevel());((Unit)counter).setOwnerName(a);counter.setNoAi(true);counter.moveTo(p.centrePos.getX()+.5,p.originPos.getY()+2,p.centrePos.getZ()+.5);h.getLevel().addFreshEntity(counter);mobs.add(counter);
   h.assertTrue(PassiveCaptureFixture.capture(p,h.getLevel())&&p.ownerName.equals(a),"canonical same-tick capture ABA");
   var encoded=new CompoundTag();ra.writeExtended(encoded);var decoded=new Resources(a,ra.food,ra.wood,ra.ore);decoded.readExtended(encoded);
   var saved=decoded.paidReceipts.get(receipt);h.assertTrue(saved.id().equals(paid.id())&&saved.payerEpoch().equals(paid.payerEpoch())&&saved.food()==paid.food()&&saved.wood()==paid.wood()&&saved.ore()==paid.ore()&&saved.extra().equals(paid.extra())&&saved.purpose().equals((DreamWorldTreeService.purpose(WorldTreeModel.SHADOW_V5)+":refund")),"actual accountNBT preserves same receiptID/epoch/paidvector and terminalintent afterABA");
   h.assertTrue(!ResourceTransaction.markReceiptDisposition(h.getLevel().getServer(),ra,ra.accountEpoch,ra.revision,receipt,DreamWorldTreeService.PURPOSE,ResourceTransaction.ReceiptDisposition.FORFEIT).applied(),"refund cannotdowngrade toforfeit");
   BuildingServerEvents.getBuildings().remove(p);p.destroy(h.getLevel());
   h.assertTrue(ra.paidReceipts.get(receipt).purpose().equals((DreamWorldTreeService.purpose(WorldTreeModel.SHADOW_V5)+":refund")),"same-tick destruction does not revoke alreadyearnedrefund");
   ra.food=9400;WorldTreeReceiptIntents.retry(h.getLevel());h.assertTrue(ra.food==10000&&ra.wood==10000&&ra.ore==10000&&ra.extra.get(DreamPassiveEconomy.SHADOW)==1000&&!ra.paidReceipts.containsKey(receipt),"orphan refund replays originalpaidvectoronce aftercapacityreturns");
   long revision=ra.revision;WorldTreeReceiptIntents.retry(h.getLevel());h.assertTrue(ra.revision==revision,"duplicate retry noledgerchange");
   var forfeit=ResourceTransaction.pay(h.getLevel().getServer(),ra,ra.accountEpoch,ra.revision,DreamWorldTreeService.PURPOSE,DreamWorldTreeService.COST);h.assertTrue(forfeit.applied(),"separateordinary destructionreceipt");
   h.assertTrue(ResourceTransaction.markReceiptDisposition(h.getLevel().getServer(),ra,ra.accountEpoch,ra.revision,forfeit.receiptId(),DreamWorldTreeService.PURPOSE,ResourceTransaction.ReceiptDisposition.FORFEIT).applied(),"ordinarydestruction irreversibleforfeit");
   int food=ra.food;WorldTreeReceiptIntents.retry(h.getLevel());h.assertTrue(ra.food==food&&!ra.paidReceipts.containsKey(forfeit.receiptId()),"forfeit settleswithoutrefund");
   System.out.println("WORLD_TREE_RECEIPT_PASS canonicalcaptureABA same-tickdestroy originalreceiptNBT terminalintent overflowretry");h.succeed();
  }finally{PlayerServerEvents.rtsPlayers.removeIf(x->x.name.equals(a)||x.name.equals(b));ResourcesServerEvents.removeAccount(a);ResourcesServerEvents.removeAccount(b);for(var mob:mobs)mob.discard();BuildingServerEvents.getBuildings().removeAll(placements);}
 }
}
