package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Genuine codec/handler/server-task plus an in-memory Netty connection, not a real client handshake. */
@GameTestHolder("reignofnether_research_economy")
@PrefixGameTestTemplate(false)
public final class ResearchEconomyPacketGameTests {
    private static final String OWNER="ReceiptPacket_Owner",POOR="ReceiptPacket_Poor",WATCHER="ReceiptPacket_Watcher";
    private static final UUID ZERO=ResearchEconomyServerboundPacket.ZERO;
    @GameTest(templateNamespace="reignofnether_research_economy",template="ore_mine_test_arena",batch="receipt_packet",timeoutTicks=200)
    public static void authenticatedPacketReplayQueueAndOrphanRefund(GameTestHelper h)throws Exception{
        ResearchEconomyFixtures.register();Resources a=ResearchEconomyFixtures.account(OWNER,1000),poor=ResearchEconomyFixtures.account(POOR,0);ResearchEconomyFixtures.account(WATCHER,0);a.extra.put(ResearchEconomyFixtures.EXTRA,900); // Leave real income room below the registered cap.
        ProductionPlacement p=ResearchEconomyFixtures.install(h.getLevel(),OWNER,h.absolutePos(new BlockPos(20,1,20))),anchor=ResearchEconomyFixtures.install(h.getLevel(),OWNER,h.absolutePos(new BlockPos(65,1,20))),q=ResearchEconomyFixtures.install(h.getLevel(),POOR,h.absolutePos(new BlockPos(110,1,20)));
        var owner=new ResearchEconomyFixtures.Peer(h.getLevel(),OWNER);var pp=new ResearchEconomyFixtures.Peer(h.getLevel(),POOR);var watcher=new ResearchEconomyFixtures.Peer(h.getLevel(),WATCHER);
        ResearchQueueClientboundPacket[] token={null},poorToken={null};ResearchEconomyServerboundPacket[] original={null};UUID[] receipt={null},orphan={null};int[] unitScores={0};
        Runnable cleanup=()->{ResearchServerEvents.removeAllResearchFor(OWNER);ResearchServerEvents.removeAllResearchFor(POOR);ResearchServerEvents.removeAllResearchFor(WATCHER);PlayerServerEvents.rtsPlayers.removeIf(x->x.name.equals(OWNER)||x.name.equals(POOR)||x.name.equals(WATCHER));ResourcesServerEvents.resourcesList.removeIf(x->x.ownerName.equals(OWNER)||x.ownerName.equals(POOR)||x.ownerName.equals(WATCHER));BuildingServerEvents.getBuildings().removeIf(x->x.ownerName.equals(OWNER)||x.ownerName.equals(POOR));owner.close();pp.close();watcher.close();};
        java.util.function.BiConsumer<Integer,Runnable> at=(tick,action)->h.runAtTickTime(tick,()->{try{action.run();}catch(RuntimeException|Error e){cleanup.run();throw e;}});
        at.accept(20,()->{
            h.assertTrue(h.getLevel().getServer().isSameThread()&&p.isBuilt&&q.isBuilt,"real server task and completed host fixtures");owner.actor.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);h.assertTrue(owner.actor.isSpectator(),"legitimate RTS topdown owner uses spectator game mode and must remain authorized");owner.drain();watcher.drain();owner.send(ResearchEconomyFixtures.query(p));var out=owner.drain();token[0]=ResearchEconomyFixtures.response(out,p,a);ResearchEconomyFixtures.checkBalancesReply(h,out,a);h.assertTrue(watcher.drain().isEmpty(),"private query/reply is not broadcast to another authenticated peer");
            unitScores[0]=PlayerServerEvents.getRTSPlayer(OWNER).scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED);
            original[0]=ResearchEconomyFixtures.request(p,token[0],1,ResearchEconomyServerboundPacket.Action.START,ResearchEconomyFixtures.MIXED_ID,ZERO);
        });
        for(int tick=21;tick<=30;tick++){
            final int i=tick;at.accept(tick,()->{
                h.assertTrue(ResourceTransaction.creditIncome(h.getLevel().getServer(),a,a.accountEpoch,a.revision,new ResourceTransaction.Cost(1,0,0,Map.of(ResearchEconomyFixtures.EXTRA,1))).applied(),"real FWO and extension income changes authoritative revision before each repeated packet");
                long revision=a.revision;int food=a.food,shadow=a.extra.get(ResearchEconomyFixtures.EXTRA);owner.send(original[0]);var out=owner.drain();var response=ResearchEconomyFixtures.response(out,p,a);
                h.assertTrue(response.status.equals("started")&&p.productionQueue.size()==1&&a.paidReceipts.size()==1,"same accepted nonce returns cached success with exactly one job/receipt");h.assertTrue(a.food==food-(i==21?11:0)&&a.extra.get(ResearchEconomyFixtures.EXTRA)==shadow-(i==21?19:0)&&a.revision==revision+(i==21?1:0),"continuous income revision never makes replay debit twice");
                receipt[0]=p.productionQueue.get(0).receiptResearch.receiptId();ResearchEconomyFixtures.checkBalancesReply(h,out,a);
                if(i==25){CompoundTag live=ResearchEconomyFixtures.snapshot(a);int jobs=p.productionQueue.size();ResourcesServerEvents.saveResources(h.getLevel());Resources saved=ResourcesSaveData.getInstance(h.getLevel()).resources.stream().filter(r->r.ownerName.equals(OWNER)).findFirst().orElseThrow();h.assertTrue(saved.food==a.food+11&&saved.wood==a.wood+13&&saved.ore==a.ore+17&&saved.extra.get(ResearchEconomyFixtures.EXTRA)==a.extra.get(ResearchEconomyFixtures.EXTRA)+19,"ordinary save projection refunds all exact mixed components");h.assertTrue(saved.paidReceipts.isEmpty()&&p.productionQueue.size()==jobs&&ResearchEconomyFixtures.snapshot(a).equals(live),"save consumes copied receipt only; live queue/account unchanged");}
            });
        }
        at.accept(31,()->{CompoundTag before=ResearchEconomyFixtures.snapshot(a);owner.send(ResearchEconomyFixtures.request(p,token[0],1,ResearchEconomyServerboundPacket.Action.START,ResearchEconomyFixtures.OTHER_ID,ZERO));h.assertTrue(ResearchEconomyFixtures.response(owner.drain(),p,a).status.equals("replay_mismatch")&&ResearchEconomyFixtures.snapshot(a).equals(before)&&p.productionQueue.size()==1,"same nonce with changed item payload is rejected without payment");});
        at.accept(35,()->{
            int f=a.food,w=a.wood,o=a.ore,s=a.extra.get(ResearchEconomyFixtures.EXTRA);ResearchEconomyFixtures.mixed.defaultCost.food=999;
            owner.send(ResearchEconomyFixtures.request(p,token[0],2,ResearchEconomyServerboundPacket.Action.CANCEL,ResearchEconomyFixtures.MIXED_ID,receipt[0]));ResearchEconomyFixtures.mixed.defaultCost.food=11;
            h.assertTrue(p.productionQueue.isEmpty()&&a.paidReceipts.isEmpty()&&a.food==f+11&&a.wood==w+13&&a.ore==o+17&&a.extra.get(ResearchEconomyFixtures.EXTRA)==s+19,"cancel uses original paid receipt despite changed current quote");
            pp.drain();var identity=PlayerServerEvents.getRTSPlayer(POOR);PlayerServerEvents.rtsPlayers.remove(identity);pp.send(ResearchEconomyFixtures.query(q));h.assertTrue(pp.drain().isEmpty()&&q.productionQueue.isEmpty(),"same-owner non-RTS spectator gets no action session");PlayerServerEvents.rtsPlayers.add(identity);
            ResourcesServerEvents.resourcesList.remove(poor);pp.send(ResearchEconomyFixtures.query(q));h.assertTrue(pp.drain().isEmpty(),"same-owner missing account gets no action session");ResourcesServerEvents.resourcesList.add(poor);
            pp.send(ResearchEconomyFixtures.query(q));poorToken[0]=ResearchEconomyFixtures.response(pp.drain(),q,poor);var request=ResearchEconomyFixtures.request(q,poorToken[0],1,ResearchEconomyServerboundPacket.Action.START,ResearchEconomyFixtures.MIXED_ID,ZERO);pp.send(request);h.assertTrue(ResearchEconomyFixtures.response(pp.drain(),q,poor).status.equals("insufficient_resources")&&q.productionQueue.isEmpty(),"insufficient request consumes a nonce without debit");
            h.assertTrue(ResourceTransaction.creditIncome(h.getLevel().getServer(),poor,poor.accountEpoch,poor.revision,new ResourceTransaction.Cost(100,100,100,Map.of(ResearchEconomyFixtures.EXTRA,100))).applied(),"actual ledger replenishment");CompoundTag before=ResearchEconomyFixtures.snapshot(poor);pp.send(request);var replayOutput=pp.drain();h.assertTrue(ResearchEconomyFixtures.response(replayOutput,q,poor).status.equals("insufficient_resources")&&ResearchEconomyFixtures.snapshot(poor).equals(before),"same rejected nonce cannot become a new payment after replenishment");ResearchEconomyFixtures.checkBalancesReply(h,replayOutput,poor);
            pp.send(ResearchEconomyFixtures.request(q,poorToken[0],2,ResearchEconomyServerboundPacket.Action.START,ResearchEconomyFixtures.MIXED_ID,ZERO));h.assertTrue(q.productionQueue.size()==1,"new nonce can retry successfully");UUID claim=q.productionQueue.get(0).receiptResearch.receiptId();pp.send(ResearchEconomyFixtures.request(q,poorToken[0],3,ResearchEconomyServerboundPacket.Action.CANCEL,ResearchEconomyFixtures.MIXED_ID,claim));h.assertTrue(q.productionQueue.isEmpty()&&poor.paidReceipts.isEmpty(),"new cancel request settles only its claim");
        });
        at.accept(45,()->{owner.send(ResearchEconomyFixtures.request(p,token[0],3,ResearchEconomyServerboundPacket.Action.START,ResearchEconomyFixtures.OTHER_ID,ZERO));h.assertTrue(p.productionQueue.size()==1,"real start for completion test");p.productionQueue.get(0).ticksLeft=3;});
        at.accept(52,()->{h.assertTrue(p.productionQueue.isEmpty()&&a.paidReceipts.isEmpty()&&ResearchServerEvents.playerHasResearch(OWNER,ResearchEconomyFixtures.OTHER_ID),"genuine production ticks consume receipt and grant research once");h.assertTrue(PlayerServerEvents.getRTSPlayer(OWNER).scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED)==unitScores[0],"research completion does not inflate unit-production score");});
        at.accept(60,()->{owner.send(ResearchEconomyFixtures.request(p,token[0],4,ResearchEconomyServerboundPacket.Action.START,ResearchEconomyFixtures.MIXED_ID,ZERO));orphan[0]=p.productionQueue.get(0).receiptResearch.receiptId();a.food=Integer.MAX_VALUE;p.destroy(h.getLevel());BuildingServerEvents.getBuildings().remove(p);h.assertTrue(p.productionQueue.isEmpty()&&a.paidReceipts.containsKey(orphan[0])&&a.food==Integer.MAX_VALUE,"real destroy detaches once without looping or losing overflow receipt");});
        at.accept(85,()->{h.assertTrue(a.paidReceipts.containsKey(orphan[0]),"20-tick retry cannot discard a still-overflowing refund");a.food=1000;});
        at.accept(110,()->{h.assertTrue(!a.paidReceipts.containsKey(orphan[0])&&a.food==1011,"normal detached retry refunds once after reclaiming room");CompoundTag before=ResearchEconomyFixtures.snapshot(a);ReceiptResearchService.retryDetachedRefunds(h.getLevel().getServer());h.assertTrue(ResearchEconomyFixtures.snapshot(a).equals(before),"later retry does not repeat settled claim");
            owner.send(ResearchEconomyFixtures.query(anchor));var fresh=ResearchEconomyFixtures.response(owner.drain(),anchor,a);
            try(var reconnected=new ResearchEconomyFixtures.Peer(h.getLevel(),OWNER)){
                before=ResearchEconomyFixtures.snapshot(a);reconnected.send(ResearchEconomyFixtures.query(anchor));var newToken=ResearchEconomyFixtures.response(reconnected.drain(),anchor,a);h.assertTrue(!newToken.actionSession.equals(fresh.actionSession),"new exact connection gets a new action session");
                reconnected.send(ResearchEconomyFixtures.request(anchor,fresh,1,ResearchEconomyServerboundPacket.Action.START,ResearchEconomyFixtures.MIXED_ID,ZERO));owner.send(ResearchEconomyFixtures.request(anchor,fresh,5,ResearchEconomyServerboundPacket.Action.START,ResearchEconomyFixtures.MIXED_ID,ZERO));h.assertTrue(ResearchEconomyFixtures.snapshot(a).equals(before)&&anchor.productionQueue.isEmpty(),"old session on new connection and old noncurrent connection both reject without payment");
            }catch(Exception e){throw new RuntimeException(e);}
            BuildingServerEvents.getBuildings().remove(anchor);ProductionPlacement replacement=(ProductionPlacement)BuildingUtils.getNewBuildingPlacement(Buildings.BLACKSMITH,h.getLevel(),anchor.originPos,anchor.rotation,OWNER,false);BuildingServerEvents.getBuildings().add(replacement);replacement.onBuilt();h.assertTrue(!ResearchPlacementIdentity.serverIdentity(replacement).equals(fresh.instance),"same-coordinate new placement has different persisted identity");
            before=ResearchEconomyFixtures.snapshot(a);owner.send(ResearchEconomyFixtures.request(replacement,fresh,5,ResearchEconomyServerboundPacket.Action.START,ResearchEconomyFixtures.MIXED_ID,ZERO));h.assertTrue(ResearchEconomyFixtures.snapshot(a).equals(before)&&replacement.productionQueue.isEmpty(),"stale placement identity cannot charge replacement building");
            owner.send(ResearchEconomyFixtures.query(replacement));var latest=ResearchEconomyFixtures.response(owner.drain(),replacement,a);a.resetExtendedAccount();before=ResearchEconomyFixtures.snapshot(a);owner.send(ResearchEconomyFixtures.request(replacement,latest,6,ResearchEconomyServerboundPacket.Action.START,ResearchEconomyFixtures.MIXED_ID,ZERO));h.assertTrue(ResearchEconomyFixtures.snapshot(a).equals(before)&&replacement.productionQueue.isEmpty(),"old epoch/session cannot mutate recreated same-name account");
            System.out.println("RECEIPT_RESEARCH_PACKET_RUNTIME_PASS real_codec_handler embedded_exact_connection replay continuous_income queue cancel destroy projection orphan");cleanup.run();h.succeed();});
    }
}
