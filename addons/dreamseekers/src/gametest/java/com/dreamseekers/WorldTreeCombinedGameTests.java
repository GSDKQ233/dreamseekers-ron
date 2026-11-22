// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.economy.DreamEconomyResearch;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.progression.DreamWindProgression;
import com.dreamseekers.worldtree.*;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.data.DataStorage;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.player.RTSPlayer;
import com.solegendary.reignofnether.resources.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.*;
import static com.dreamseekers.worldtree.WorldTreeModel.*;

/** Native placements/economy; accelerated upgrade timers. Older profiles separately run real 2,400-tick stages. */
@GameTestHolder("dreamseekers_branches") @PrefixGameTestTemplate(false)
public final class WorldTreeCombinedGameTests {
    @GameTest(templateNamespace="dreamseekers_branches",template="branch_test_arena",batch="combined_branches",timeoutTicks=240)
    public static void twoPaidBranchesOnOneDamagedTree(GameTestHelper h){
        for(boolean shadowFirst:new boolean[]{true,false}){
            var f=new Fixture(h,shadowFirst);
            try{f.run();}finally{f.close();}
        }
        h.succeed();
    }
    private record Balance(int food,int wood,int ore,int emerald,int shadow){
        static Balance of(Resources r){return new Balance(r.food,r.wood,r.ore,r.emerald,r.extra.getOrDefault(DreamPassiveEconomy.SHADOW,0));}
        Balance less(ResourceTransaction.Cost charge){return new Balance(food-charge.food(),wood-charge.wood(),ore-charge.ore(),
            emerald-charge.emerald(),shadow-charge.extra().getOrDefault(DreamPassiveEconomy.SHADOW,0));}
    }
    private static final class Fixture {
        final GameTestHelper h;final ServerLevel l;final String owner,visitor;final boolean shadowFirst;
        final WorldTreeModel first,missing;
        final Set<Long> forced=new HashSet<>();Resources account,visitingAccount;
        PassiveIncomePeer peer,other;DreamAnchorPlacement tree;
        Fixture(GameTestHelper h,boolean shadowFirst){
            this.h=h;l=h.getLevel();this.shadowFirst=shadowFirst;owner=shadowFirst?"TreeBothDark":"TreeBothWind";visitor=owner+"B";
            first=shadowFirst?CURRENT_SHADOW:CURRENT_WIND;missing=shadowFirst?CURRENT_WIND:CURRENT_SHADOW;
        }
        void check(boolean flag,String what){h.assertTrue(flag,owner+": "+what);}
        Resources register(String who){
            check(PlayerServerEvents.getRTSPlayer(who)==null,"isolated owner "+who);
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(who,DreamseekersMod.DREAMSEEKERS));
            var r=new Resources(who,8000,9000,7000,17);
            r.extra.put(DreamPassiveEconomy.SHADOW,900);ResourcesServerEvents.resourcesList.add(r);return r;
        }
        String start(WorldTreeModel branch){return DreamWorldTreeService.start(peer.actor,tree,tree.tree().instance,branch);}
        void tick(){tree.tree().lastTick=Long.MIN_VALUE;DreamWorldTreeService.tick(tree,l);}
        void finish(){tree.tree().remaining=1;tick();}
        void run(){
            account=register(owner);visitingAccount=register(visitor);peer=new PassiveIncomePeer(l,owner);other=new PassiveIncomePeer(l,visitor);
            var origin=h.absolutePos(new BlockPos(shadowFirst?432:472,1,136));var rotation=shadowFirst?Rotation.CLOCKWISE_90:Rotation.COUNTERCLOCKWISE_90;
            for(int x=(origin.getX()-16)>>4;x<=(origin.getX()+16)>>4;x++)for(int z=(origin.getZ()-16)>>4;z<=(origin.getZ()+16)>>4;z++){
                long c=ChunkPos.asLong(x,z);if(!l.getForcedChunks().contains(c)){l.setChunkForced(x,z,true);forced.add(c);}
                l.getChunk(x,z);
            }
            for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++){
                l.setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
                for(int y=1;y<=20;y++)l.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
                l.setBlock(origin.offset(x,21,z),Blocks.STONE.defaultBlockState(),2);
            }
            for(var b:BuildingUtils.getAbsoluteBlockData(CURRENT_BASE.blocks(),l,origin,rotation))l.setBlock(b.getBlockPos(),b.getBlockState(),2);
            tree=(DreamAnchorPlacement)BuildingUtils.getNewBuildingPlacement(DreamseekersMod.DREAM_ANCHOR,l,origin,rotation,owner,false);
            tree.isBuilt=true;BuildingServerEvents.getBuildings().add(tree);
            final UUID instance=tree.tree().instance;
            var initial=Balance.of(account);
            check(start(CONFLUENCE_V6).equals("invalid_target"),"cannot buy combined directly");
            check(DreamWorldTreeService.start(other.actor,tree,instance,first).equals("invalid_host"),"another owner cannot debit either account");
            check(start(first).equals("started")&&tree.tree().remaining==2400,"original first-stage price and work");
            check(Balance.of(account).equals(initial.less(DreamWorldTreeService.cost(first))),"first stage debit");
            var firstReceipt=tree.tree().receipt;
            check(start(missing).equals("invalid_state")&&account.paidReceipts.size()==1,"no concurrent or duplicate purchases");
            check(!tree.hasStandingUpgrade()&&!tree.ascended()&&!DreamWindProgression.server(owner).wind(),"no pending first-stage unlocks");
            finish();check(tree.model()==first&&!account.paidReceipts.containsKey(firstReceipt)&&tree.tree().phase==WorldTreeState.Phase.COMPLETE,"first paid commit");
            if(!shadowFirst)DreamWindProgression.commitTier(account,0,1); // fixture: an already-earned tech level must survive the second commit.
            check(DreamEconomyResearch.eligibleHost(tree,true)==shadowFirst&&DreamWindProgression.server(owner).wind()!=shadowFirst,"only actual first branch applies");
            check(start(first).equals("invalid_state"),"first branch cannot be purchased twice");

            var paidFirst=Balance.of(account);
            check(start(missing).equals("started")&&tree.tree().remaining==2400&&tree.tree().target==CONFLUENCE_V6,"remaining branch is offered and paid on same placement");
            var price=DreamWorldTreeService.cost(missing);
            check(Balance.of(account).equals(paidFirst.less(price)),"only missing branch charged");
            var paid=account.paidReceipts.get(tree.tree().receipt);
            check(DreamWorldTreeService.receiptMatches(tree.tree(),paid)&&!paid.purpose().equals(DreamWorldTreeService.purpose(missing)),"second-stage receipt cannot substitute for first-stage receipt");
            var raw=tree.tree().write();var restored=WorldTreeState.read(raw);
            check(restored.supported&&restored.write().equals(raw)&&restored.hasStandingUpgrade(),"pending work codec retains committed branch and exact task");
            tree.getDataStorage().setData(DreamWorldTreeService.STATE,restored);tree.afterRestoredFromSave();
            check(tree.model()==first&&tree.tree().receipt.equals(paid.id())&&tree.tree().instance.equals(instance),"pending save does not install combined geometry early");
            if(shadowFirst){
                tick();int food=account.food,emerald=account.emerald;tree.tree().incomeElapsed=99;tick();
                check(account.food==food+5&&account.emerald==emerald+1&&!DreamWindProgression.server(owner).wind(),"Shadow income continues during second purchase; Wind not awarded early");
            }else check(DreamWindProgression.trainingDiscount(false,owner)==33&&!tree.ascended(),"earned Wind tier works during second purchase");
            var obstacle=tree.upgradedBlocks().stream().filter(b->!b.getBlockState().isAir()&&l.getBlockState(b.getBlockPos()).isAir()).findFirst().orElseThrow().getBlockPos();
            l.setBlock(obstacle,Blocks.GOLD_BLOCK.defaultBlockState(),2);finish();
            check(tree.tree().phase==WorldTreeState.Phase.BLOCKED&&tree.model()==first&&tree.hasStandingUpgrade()
                &&l.getBlockState(obstacle).is(Blocks.GOLD_BLOCK),"blocked second stage never removes the first geometry or foreign block");
            var beforeRefund=Balance.of(account);
            check(DreamWorldTreeService.cancel(peer.actor,tree,instance).equals("cancelled_refunded")
                &&Balance.of(account).less(price).equals(beforeRefund)&&!account.paidReceipts.containsKey(paid.id()),"one exact cancellation refund");
            check(tree.tree().phase==WorldTreeState.Phase.COMPLETE&&tree.model()==first
                &&DreamWorldTreeService.cancel(peer.actor,tree,instance).equals("invalid_state"),"cancel returns first branch, no duplicate refund");
            l.setBlock(obstacle,Blocks.AIR.defaultBlockState(),2);

            // Capture with temporarily unrefundable original payment: no buyer/benefit is reassigned.
            var beforeCapture=Balance.of(account);var otherBalance=Balance.of(visitingAccount);
            check(start(missing).equals("started"),"retry after cancellation");var captureReceipt=tree.tree().receipt;
            int paidFood=account.food;account.food=Integer.MAX_VALUE;tree.setOwnerNameFromServerEditor(visitor);
            check(tree.tree().phase==WorldTreeState.Phase.REFUND_PENDING&&tree.model()==first&&tree.hasStandingUpgrade()
                &&account.paidReceipts.get(captureReceipt).purpose().endsWith(":refund"),"capture binds durable disposition to original payment");
            check(start(missing).equals("invalid_host")&&Balance.of(visitingAccount).equals(otherBalance)&&!DreamWindProgression.server(visitor).wind(),"captor inherits no global research or refund");
            account.food=paidFood;WorldTreeReceiptIntents.retry(l);tick();
            check(Balance.of(account).equals(beforeCapture)&&!account.paidReceipts.containsKey(captureReceipt),"original payer alone receives exact delayed refund");
            tree.setOwnerNameFromServerEditor(owner);

            List<String> refusal=new ArrayList<>();
            check(tree.startRequestedProduction(DreamseekersMod.WORKER_PRODUCTION,refusal::add),"unrelated worker queue accepted: "+refusal);
            var queued=tree.productionQueue.get(0);var unitClaims=tree.attachedUnitReceipts();check(unitClaims.size()==1,"live paid worker receipt");
            var rally=origin.offset(12,1,12);tree.setRallyPoint(rally);
            check(start(missing).equals("started"),"repurchase missing branch after original owner recaptures");
            tree.destroyRandomBlocks(37.25);int damageHP=tree.getHealth();check(damageHP>0&&damageHP<tree.getMaxHealth(),"physical damage before confluence");
            finish();
            check(tree.model()==CONFLUENCE_V6&&tree.tree().phase==WorldTreeState.Phase.COMPLETE&&tree.tree().receipt==null,"both orders commit same final geometry");
            check(tree.ascended()&&tree.getUpgradeLevel()==1&&DreamEconomyResearch.eligibleHost(tree,true)
                &&DreamWindProgression.server(owner).wind()&&DreamWindProgression.trainingDiscount(false,owner)==(shadowFirst?30:33),"both real perks operate concurrently, earned Wind tier preserved");
            check(Math.abs(tree.getHealth()-damageHP)<=1&&tree.getBlocksPlaced()<CONFLUENCE_V6.count&&tree.tree().instance.equals(instance)
                &&tree.rotation==rotation&&tree.originPos.equals(origin)&&owner.equals(tree.ownerName),"damage, identity, rotation and owner survive both morphs");
            check(tree.productionQueue.size()==1&&tree.productionQueue.get(0)==queued&&tree.attachedUnitReceipts().equals(unitClaims)
                &&account.paidReceipts.keySet().equals(unitClaims)&&rally.equals(tree.getFinalRallyPoint()),"live worker queue, original unit payment and rally survive morph untouched");
            tick();tree.tree().incomeElapsed=99;var beforeCycle=Balance.of(account);tick();
            check(account.food==beforeCycle.food()+5&&account.wood==beforeCycle.wood()+5&&account.ore==beforeCycle.ore()+5
                &&account.emerald==beforeCycle.emerald()+1&&account.extra.get(DreamPassiveEconomy.SHADOW)==beforeCycle.shadow()+1
                &&DreamWindProgression.trainingDiscount(false,owner)==(shadowFirst?30:33),"one ordinary Shadow resource cycle while Wind discount is simultaneously active");
            var finalBalance=Balance.of(account);check(start(CURRENT_SHADOW).equals("invalid_state")&&start(CURRENT_WIND).equals("invalid_state")
                &&Balance.of(account).equals(finalBalance),"combined form never charges a third time");
            var snap=tree.structureSnapshot();check(DreamAnchorPlacement.validSnapshot(snap)&&WorldTreeModel.read(snap,"model")==CONFLUENCE_V6,"combined geometry sync round-trips");
            var data=new DataStorage();var state=WorldTreeState.read(tree.tree().write());data.setData(DreamWorldTreeService.STATE,state);
            var reloaded=(DreamAnchorPlacement)DreamseekersMod.DREAM_ANCHOR.createRestoredBuildingPlacement(l,origin,rotation,owner,data);
            reloaded.getDataStorage().setData(DreamWorldTreeService.STATE,state);reloaded.isBuilt=true;reloaded.partialBlocksDestroyed=tree.partialBlocksDestroyed;reloaded.afterRestoredFromSave();
            check(reloaded.ascended()&&reloaded.model()==CONFLUENCE_V6&&reloaded.tree().instance.equals(instance)
                &&reloaded.getHealth()==tree.getHealth()&&reloaded.getBlocksPlaced()==tree.getBlocksPlaced(),"native save constructor restores damaged combined form without healing");
        }
        void close(){
            if(tree!=null)BuildingServerEvents.getBuildings().remove(tree);
            if(other!=null)other.close();if(peer!=null)peer.close();
            PlayerServerEvents.rtsPlayers.removeIf(p->owner.equals(p.name)||visitor.equals(p.name));
            ResourcesServerEvents.removeAccount(owner);ResourcesServerEvents.removeAccount(visitor);
            for(var key:forced){var c=new ChunkPos(key);l.setChunkForced(c.x,c.z,false);}
        }
    }
}
