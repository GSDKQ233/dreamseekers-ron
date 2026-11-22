// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.economy.DreamPassiveEconomy;
import com.dreamseekers.worldtree.WorldTreeModel;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.*;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.research.economy.*;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Function;

/** Server tick, actual ledger and paired embedded Forge peers. This does not render a client. */
@GameTestHolder("api65_repair2_training")
@PrefixGameTestTemplate(false)
public final class Repair2TrainingGameTests {
    private static final String BATCH = "api65_repair2_training";
    private static final List<AaroncosTrainingGameTests.F> WORLDS = new ArrayList<>();
    private static final List<PassiveIncomePeer> PEERS = new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel ignored) {
        PEERS.forEach(PassiveIncomePeer::close); PEERS.clear();
        WORLDS.forEach(AaroncosTrainingGameTests.F::close); WORLDS.clear();
    }
    private static AaroncosTrainingGameTests.F world(GameTestHelper h,String name,int x) {
        var f = new AaroncosTrainingGameTests.F(h,name,x);
        WORLDS.add(f); return f;
    }
    private static PassiveIncomePeer peer(GameTestHelper h,String name) {
        var p = new PassiveIncomePeer(h.getLevel(),name); PEERS.add(p); return p;
    }
    private static List<String> hud(PassiveIncomePeer p) {
        return decode(p,new HudClientboundPacket("",1),b -> { String key=b.readUtf(); b.readInt(); return key; });
    }
    private static List<BuildingProductionClientboundPacket> updates(PassiveIncomePeer p,BuildingAction action,BlockPos origin) {
        return decode(p,new BuildingProductionClientboundPacket(BuildingAction.CLEAR_PRODUCTION,"",BlockPos.ZERO),
                BuildingProductionClientboundPacket::new).stream().filter(msg -> msg.action==action && msg.buildingPos.equals(origin)).toList();
    }
    private static <T> List<T> decode(PassiveIncomePeer peer,Object sample,Function<FriendlyByteBuf,T> reader) {
        int id=PassiveIncomePeer.discriminator(sample); var result=new ArrayList<T>();
        for(Object msg:peer.drain()) if(msg instanceof ClientboundCustomPayloadPacket wire
                && wire.getIdentifier().getNamespace().equals("reignofnether") && wire.getIdentifier().getPath().equals("main")) {
            var b=new FriendlyByteBuf(wire.getData().copy());
            try {
                if(b.readVarInt()==id) {
                    result.add(reader.apply(b));
                    if(b.isReadable())throw new AssertionError("Trailing queue packet data");
                }
            } finally { b.release(); }
        }
        return result;
    }
    private static void request(GameTestHelper h,PassiveIncomePeer sender,ProductionPlacement host,ProductionItem item) {
        BuildingAction action = item == null ? BuildingAction.REQUEST_PRODUCTION_SYNC : BuildingAction.START_PRODUCTION;
        String name = item == null ? "" : ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(item).toString();
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var constructor=NetworkEvent.Context.class.getDeclaredConstructor(Connection.class,NetworkDirection.class,int.class);
            constructor.setAccessible(true);
            var context=constructor.newInstance(sender.connection,NetworkDirection.PLAY_TO_SERVER,-1);
            new BuildingProductionServerboundPacket(action,name,host.originPos).encode(buf);
            new BuildingProductionServerboundPacket(buf).handle(()->context);
            h.assertTrue(!buf.isReadable() && context.getSender()==sender.actor,"real request codec and authenticated sender");
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        finally {buf.release();}
        sender.channel.runPendingTasks();
    }
    private static CompoundTag bank(Resources r) {
        var tag=new CompoundTag(); tag.putInt("f",r.food);tag.putInt("w",r.wood);tag.putInt("o",r.ore);tag.putInt("e",r.emerald);
        r.writeExtended(tag);return tag;
    }
    private static void rejects(GameTestHelper h,PassiveIncomePeer sender,PassiveIncomePeer owner,ProductionPlacement host,
            ProductionItem item,Resources account,String key) {
        var before=bank(account);var queue=List.copyOf(host.productionQueue);sender.drain();owner.drain();
        request(h,sender,host,item);
        h.assertTrue(hud(sender).equals(List.of(key)) && hud(owner).isEmpty(),"only actual allied controller receives "+key);
        h.assertTrue(before.equals(bank(account)) && host.productionQueue.equals(queue),"rejection changes no balances, claims or orders");
    }

    @GameTest(template="ore_mine_test_arena",batch=BATCH,timeoutTicks=100)
    public static void requesterGetsSpecificFailureAndOnlyOneFundedOrder(GameTestHelper h) {
        var f=world(h,"R2Req",36800);
        var tree=f.tree(WorldTreeModel.WIND_V4,0);var hall=f.hall();var lab=f.install(DreamseekersMod.RESONANCE_LABORATORY,128);
        f.at(12,()->{
            var owner=peer(h,f.owner);var ally=peer(h,"R2TrainAlly");
            PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(f.owner));
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer(f.owner,DreamseekersMod.DREAMSEEKERS,owner.actor.getId(),0,false,-1L,-1L));
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewPlayer("R2TrainAlly",DreamseekersMod.DREAMSEEKERS,ally.actor.getId(),1,false,-1L,-1L));
            var item=DreamseekersMod.AARONCOS_LEFT_HAND_PRODUCTION;
            AlliancesServerEvents.addAlliance(f.owner,"R2TrainAlly");
            AlliancesServerEvents.playersWithAlliedControl.add(f.owner);
            try {
                BuildingServerEvents.getBuildings().remove(lab);
                rejects(h,ally,owner,hall,item,f.r,"dreamseekers.error.hands_laboratory");
                BuildingServerEvents.getBuildings().add(lab);
                BuildingServerEvents.getBuildings().remove(tree);
                rejects(h,ally,owner,hall,item,f.r,"dreamseekers.error.hands_upgraded_tree");
                rejects(h,ally,owner,hall,DreamseekersMod.BONE_WING_PRODUCTION,f.r,"server.resources.reignofnether.not_enough_pop");
                BuildingServerEvents.getBuildings().add(tree);
                f.r.wood=249;
                rejects(h,ally,owner,hall,item,f.r,"server.resources.reignofnether.not_enough_wood");
                f.r.wood=10000; f.r.extra.put(DreamPassiveEconomy.SHADOW,99);
                rejects(h,ally,owner,hall,item,f.r,"dreamseekers.production.not_enough_shadow");
                f.r.extra.put(DreamPassiveEconomy.SHADOW,1000);
                long rev=f.r.revision;
                request(h,ally,hall,item);
                h.assertTrue(hud(ally).isEmpty() && hall.productionQueue.size()==1 && f.r.paidReceipts.size()==1
                        && f.r.revision==rev+1 && f.r.food==9600 && f.r.wood==9750 && f.r.ore==9650
                        && f.r.extra.get(DreamPassiveEconomy.SHADOW)==900 && UnitServerEvents.getCurrentPopulation(f.owner)==8,
                        "fresh ordinary request debits its actual quote and reserves a single order on the owner's ledger");
                h.assertTrue(hall.cancelObservedHead(item,hall.productionQueue.get(0).queueEntryId) && f.r.food==10000,"cleanly cancel original order");
            } finally {
                AlliancesServerEvents.playersWithAlliedControl.remove(f.owner);AlliancesServerEvents.removeAlliance(f.owner,"R2TrainAlly");
                PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals("R2TrainAlly"));
                owner.close();ally.close();
            }
            h.succeed();
        });
    }

    @GameTest(template="ore_mine_test_arena",batch=BATCH,timeoutTicks=105)
    public static void paidHeadReportsRealGateAndBlockedExitUntilSingleSpawn(GameTestHelper h) {
        var f=world(h,"R2Hold",37200);f.tree(WorldTreeModel.WIND_V4,0);var hall=f.hall();
        var lab=f.install(DreamseekersMod.RESONANCE_LABORATORY,128);
        var item=DreamseekersMod.AARONCOS_RIGHT_HAND_PRODUCTION;var owner=peer(h,f.owner);
        var head=new ActiveProduction[1];var hold=new ProductionPause[1];var paid=new CompoundTag[1];
        BlockPos exit=hall.getUnitSpawnPoint(h.getLevel(),false,Vec3i.ZERO).above();
        f.at(10,()->{
            request(h,owner,hall,item);
            h.assertTrue(hall.productionQueue.size()==1,"one paid hand");
            head[0]=hall.productionQueue.get(0);paid[0]=bank(f.r);
            BuildingServerEvents.getBuildings().remove(lab);lab.onRemoved(BuildingPlacement.RemovalReason.REPLACED);
            owner.drain();
        });
        f.at(18,()->{
            hold[0]=head[0].pause();
            var delta=updates(owner,BuildingAction.SYNC_PRODUCTION_HEAD,hall.originPos);
            h.assertTrue(hold[0].reasonKey().equals("dreamseekers.error.hands_laboratory") && delta.size()==1
                    && delta.get(0).entryId.equals(head[0].queueEntryId) && delta.get(0).headPause().equals(hold[0]),
                    "one bounded transition identifies the paid entry and actual missing laboratory");
            request(h,owner,hall,null);
            var snapshot=updates(owner,BuildingAction.SYNC_PRODUCTION,hall.originPos);
            h.assertTrue(snapshot.size()==1 && snapshot.get(0).rows().size()==1
                    && snapshot.get(0).rows().get(0).entryId().equals(head[0].queueEntryId)
                    && snapshot.get(0).headPause().equals(hold[0]),"requery preserves held ordinary head");
        });
        f.at(25,()->{
            h.assertTrue(head[0].pause().equals(hold[0]) && updates(owner,BuildingAction.SYNC_PRODUCTION_HEAD,hall.originPos).isEmpty()
                    && paid[0].equals(bank(f.r)) && head[0].ticksLeft==1440,"unchanged reason does not push, advance or settle");
            f.install(DreamseekersMod.RESONANCE_LABORATORY,176); // A new building instance, not a revived stale pointer.
        });
        f.at(36,()->{
            var delta=updates(owner,BuildingAction.SYNC_PRODUCTION_HEAD,hall.originPos);
            h.assertTrue(delta.size()==1 && !delta.get(0).headPause().paused() && head[0].ticksLeft<1440
                    && hall.productionQueue.get(0)==head[0] && paid[0].equals(bank(f.r)),"same order/receipt resumes when the new laboratory is usable");
            h.getLevel().setBlock(exit,Blocks.STONE.defaultBlockState(),2);
            head[0].ticksLeft=1; // Shorten only the independent final-window wait.
        });
        f.at(45,()->{
            var delta=updates(owner,BuildingAction.SYNC_PRODUCTION_HEAD,hall.originPos);
            h.assertTrue(delta.size()==1 && delta.get(0).headPause().reasonKey().equals("dreamseekers.production.exit_blocked")
                    && delta.get(0).entryId.equals(head[0].queueEntryId) && delta.get(0).ticksLeft==1 && f.handCount()==0
                    && head[0].pause().paused() && paid[0].equals(bank(f.r)),"blocked exit does not charge or spawn; wire reports original remaining work");
            h.getLevel().setBlock(exit,Blocks.AIR.defaultBlockState(),2);
        });
        f.at(65,()->{
            h.assertTrue(hall.productionQueue.isEmpty() && f.handCount()==1 && f.r.food==9600 && f.r.wood==9750
                    && f.r.ore==9650 && f.r.extra.get(DreamPassiveEconomy.SHADOW)==900 && f.r.paidReceipts.isEmpty()
                    && UnitServerEvents.getCurrentPopulation(f.owner)==8
                    && PlayerServerEvents.getRTSPlayer(f.owner).scores.scoreList.get(RTSPlayerScoresEnum.TOTAL_UNITS_PRODUCED)==1,
                    "one real spawn, one settlement and one score after restoring both conditions");
            owner.close();h.succeed();
        });
    }

    @GameTest(template="ore_mine_test_arena",batch=BATCH,timeoutTicks=85)
    public static void mixedResearchSnapshotKeepsZeroWorkWorkerPaused(GameTestHelper h) {
        var f=world(h,"R2Mixed",37600);var tree=f.tree(WorldTreeModel.BASE_V3,0);var owner=peer(h,f.owner);
        var worker=DreamseekersMod.WORKER_PRODUCTION;
        BlockPos exit=tree.getUnitSpawnPoint(h.getLevel(),false,Vec3i.ZERO).above();
        f.at(12,()->{
            h.assertTrue(ResearchQueueSync.isOptIn(tree) && tree.startProductionItem(worker),"real mixed research/training host");
            tree.productionQueue.get(0).ticksLeft=0;
            h.getLevel().setBlock(exit,Blocks.STONE.defaultBlockState(),2);
        });
        f.at(20,()->{
            var head=tree.productionQueue.get(0);
            h.assertTrue(head.pause().reasonKey().equals("dreamseekers.production.exit_blocked") && head.ticksLeft==0 && !head.completed,
                    "zero work is not a completed order");
            var token=EconomyActionGate.query(owner.actor,owner.connection);
            var sample=new ResearchQueueClientboundPacket(UUID.randomUUID(),1,"ready",tree,f.r,token,0);
            owner.drain(); request(h,owner,tree,null);
            var snapshots=decode(owner,sample,ResearchQueueClientboundPacket::new).stream().filter(m->m.origin.equals(tree.originPos)).toList();
            h.assertTrue(snapshots.size()==1 && snapshots.get(0).entries.size()==1 && snapshots.get(0).headPause.equals(head.pause())
                    && snapshots.get(0).entries.get(0).queueEntryId().equals(head.queueEntryId)
                    && snapshots.get(0).entries.get(0).receipt()==null,"owner resync carries the hold without disclosing a unit receipt ID");
            h.assertTrue(tree.cancelObservedHead(worker,head.queueEntryId) && !tree.cancelObservedHead(worker,head.queueEntryId)
                    && f.r.paidReceipts.isEmpty() && f.r.food==10000 && f.r.wood==10000 && f.r.ore==10000,
                    "a held order still cancels/refunds just once");
            h.getLevel().setBlock(exit,Blocks.AIR.defaultBlockState(),2); owner.close();h.succeed();
        });
    }
}
