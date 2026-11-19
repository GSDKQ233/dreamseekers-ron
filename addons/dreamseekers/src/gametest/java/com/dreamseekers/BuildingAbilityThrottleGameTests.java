// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers;

import com.dreamseekers.buildingability.*;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.research.economy.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Real decoded authenticated requests and exact outgoing wire counts, not simulated quota counters. */
@GameTestHolder("dreamseekers_building_abilities") @PrefixGameTestTemplate(false)
public final class BuildingAbilityThrottleGameTests {
    private static final String BATCH="building_ability_throttle";
    private static final List<NeutralContractGameTests.F> FIXTURES=new ArrayList<>();
    private static final List<PassiveIncomePeer> RECONNECTED=new ArrayList<>();
    @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel ignored){
        for(var peer:List.copyOf(RECONNECTED))peer.close();RECONNECTED.clear();
        for(var f:List.copyOf(FIXTURES))f.close();FIXTURES.clear();EconomyActionClient.clear();
    }
    private static NeutralContractGameTests.F fixture(GameTestHelper h,String suffix){var f=new NeutralContractGameTests.F(h,"API53_Throttle_"+suffix);FIXTURES.add(f);return f;}
    private record Counts(int full,int ack,int balances,String status) { }
    private static Counts send(GameTestHelper h,PassiveIncomePeer peer,BuildingAbilityPackets.Request packet){
        peer.drain();
        try{
            var ctor=net.minecraftforge.network.NetworkEvent.Context.class.getDeclaredConstructor(net.minecraft.network.Connection.class,net.minecraftforge.network.NetworkDirection.class,int.class);
            ctor.setAccessible(true);var context=ctor.newInstance(peer.connection,net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER,-1);
            var wire=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try{packet.encode(wire);var decoded=new BuildingAbilityPackets.Request(wire);h.assertTrue(decoded.equals(packet)&&wire.readableBytes()==0,"exact real request codec");decoded.handle(()->context);}finally{wire.release();}
        }catch(ReflectiveOperationException error){throw new AssertionError(error);}
        int full=0,ack=0,balances=0;String status="";
        int fwo=PassiveIncomePeer.discriminator(new ResourcesClientboundPacket(ResourcesAction.SYNC,"",0,0,0,0,BlockPos.ZERO,""));
        int extra=PassiveIncomePeer.discriminator(new ExtendedResourcesClientboundPacket(UUID.randomUUID(),0,"",null));
        for(Object out:peer.drain())if(out instanceof ClientboundCustomPayloadPacket payload){
            var bytes=new FriendlyByteBuf(payload.getData().copy());
            try{
                int id=bytes.readVarInt();
                if(payload.getIdentifier().equals(DreamseekersMod.id("building_abilities"))){
                    if(id==1){var value=new BuildingAbilityPackets.Snapshot(bytes);h.assertTrue(value.valid(),"valid full view");full++;status=value.status();}
                    else if(id==2){var value=new BuildingAbilityPackets.Ack(bytes);h.assertTrue(value.valid(),"valid completion-only ACK");ack++;status=value.status();}
                    else throw new AssertionError("unexpected building response discriminator "+id);
                    h.assertTrue(bytes.readableBytes()==0,"bounded response consumes exactly its wire");
                }else if(payload.getIdentifier().getNamespace().equals(ReignOfNether.MOD_ID)&&payload.getIdentifier().getPath().equals("main")&&(id==fwo||id==extra))balances++;
            }finally{bytes.release();}
        }
        return new Counts(full,ack,balances,status);
    }
    private static BuildingAbilityPackets.Payload payload(NeutralContractGameTests.F f,BuildingPlacement host,BuildingAbilityPackets.Action action){
        var view=BuildingAbilityService.view(f.peer.actor,host);
        return new BuildingAbilityPackets.Payload(action,f.level.dimension().location(),host.originPos,UUID.randomUUID(),view.instance(),view.capture(),view.generation(),view.revision(),0);
    }
    private static void ackOnly(GameTestHelper h,Counts counts,String reason){h.assertTrue(counts.full()==0&&counts.ack()==1&&counts.balances()==0,reason+": "+counts);}
    @GameTest(templateNamespace="dreamseekers_building_abilities",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=100)
    public static void codexCastFloodCannotBypassQueryClockAndReconnectRejectsOldSession(GameTestHelper h){
        var f=fixture(h,"Codex");BuildingAbilityPackets.Request[] old={null};EconomyActionGate.Token[] token={null};
        h.startSequence().thenWaitUntil(()->h.assertTrue(f.court.isBuilt,"actual court complete")).thenExecute(()->{
            token[0]=EconomyActionGate.query(f.peer.actor,f.peer.connection);
            var query=payload(f,f.court,BuildingAbilityPackets.Action.CODEX_QUERY);
            var first=send(h,f.peer,new BuildingAbilityPackets.Request(query,token[0].epoch(),token[0].session(),0));
            h.assertTrue(first.full()==1&&first.ack()==0&&first.balances()==2,"first genuine query publishes exactly one roster plus original two balance snapshots");
            int food=f.account.food,shadow=f.account.extra.get(com.dreamseekers.economy.DreamPassiveEconomy.SHADOW);long revision=f.account.revision;
            var cast=payload(f,f.court,BuildingAbilityPackets.Action.CAST);
            for(int i=1;i<=64;i++){
                var request=new BuildingAbilityPackets.Request(cast,token[0].epoch(),token[0].session(),i);if(i==1)old[0]=request;
                var result=send(h,f.peer,request);ackOnly(h,result,"unavailable Codex CAST never queries roster/balance");h.assertTrue(result.status().equals("unavailable"),"fixed informational action stays unavailable");
                ackOnly(h,send(h,f.peer,request),"exact duplicate is completion-only");
                var blocked=send(h,f.peer,new BuildingAbilityPackets.Request(query,token[0].epoch(),token[0].session(),0));
                h.assertTrue(blocked.full()==0&&blocked.ack()==0&&blocked.balances()==0,"same-tick explicit query remains throttled despite CAST traffic");
            }
            var different=new BuildingAbilityPackets.Payload(cast.action(),cast.dimension(),cast.origin(),UUID.randomUUID(),cast.instance(),cast.capture(),cast.generation(),cast.revision(),0);
            var mismatch=send(h,f.peer,new BuildingAbilityPackets.Request(different,token[0].epoch(),token[0].session(),1));ackOnly(h,mismatch,"changed old sequence rejected without heavy view");h.assertTrue(mismatch.status().equals("replay_mismatch"),"cached changed seq1 is explicitly rejected");
            var zero=send(h,f.peer,new BuildingAbilityPackets.Request(cast,token[0].epoch(),token[0].session(),0));ackOnly(h,zero,"invalid zero CAST sequence is light acknowledgement only");h.assertTrue(zero.status().equals("invalid_sequence"),"zero CAST is explicitly rejected");
            h.assertTrue(f.account.food==food&&f.account.extra.get(com.dreamseekers.economy.DreamPassiveEconomy.SHADOW)==shadow&&f.account.revision==revision
                &&f.court.getDataStorage().getData(BuildingAbilityState.TYPE).revision()==0,"flood creates no debit, credit, cast revision or cooldown");
        }).thenExecuteAfter(19,()->{
            var query=payload(f,f.court,BuildingAbilityPackets.Action.CODEX_QUERY);
            h.assertTrue(send(h,f.peer,new BuildingAbilityPackets.Request(query,token[0].epoch(),token[0].session(),0)).full()==0,"query still blocked at nineteen ticks");
        }).thenExecuteAfter(1,()->{
            var query=payload(f,f.court,BuildingAbilityPackets.Action.CODEX_QUERY);
            h.assertTrue(send(h,f.peer,new BuildingAbilityPackets.Request(query,token[0].epoch(),token[0].session(),0)).full()==1,"query becomes due at twenty ticks, not reset by ACK traffic");
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent(f.peer.actor));f.peer.close();
            var online=new PassiveIncomePeer(f.level,f.owner);RECONNECTED.add(online);var fresh=EconomyActionGate.query(online.actor,online.connection);
            var stale=send(h,online,old[0]);ackOnly(h,stale,"old connection token never triggers reconnect roster query");h.assertTrue(stale.status().equals("stale_session"),"old session rejected");
            var available=send(h,online,new BuildingAbilityPackets.Request(query,fresh.epoch(),fresh.session(),0));h.assertTrue(available.full()==1,"new connection gets its own query clock and current roster");
            online.close();RECONNECTED.remove(online);f.close();h.succeed();
        });
    }
    @GameTest(templateNamespace="dreamseekers_building_abilities",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=350)
    public static void actualPaidSuccessPublishesOnceAndCachedSuccessCannotRefreshViewOrBalance(GameTestHelper h){
        var f=fixture(h,"Paid");var garden=f.install(DreamseekersMod.REVERIE_GARDEN,40,0);var at=garden.originPos.offset(3,1,-3);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){f.level.setBlock(at.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);for(int y=0;y<4;y++)f.level.setBlock(at.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        var unit=EntityRegistrar.VILLAGER_UNIT.get().create(f.level);unit.setOwnerName(f.owner);unit.setNoAi(true);unit.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5);h.assertTrue(f.level.addFreshEntity(unit),"real own recipient");f.bodies.add(unit);unit.setHealth(unit.getMaxHealth()-8);
        h.startSequence().thenWaitUntil(()->h.assertTrue(garden.isBuilt&&unit.tickCount>205&&UnitServerEvents.getAllUnits().contains(unit),"natural rest with published injured recipient")).thenExecute(()->{
            var token=EconomyActionGate.query(f.peer.actor,f.peer.connection);var p=payload(f,garden,BuildingAbilityPackets.Action.CAST);var request=new BuildingAbilityPackets.Request(p,token.epoch(),token.session(),1);int food=f.account.food;
            var paid=send(h,f.peer,request);h.assertTrue(paid.full()==1&&paid.ack()==0&&paid.balances()==2&&paid.status().equals("success"),"new real paid commit alone gets immediate full confirmation+two balance packets");
            var state=garden.getDataStorage().getData(BuildingAbilityState.TYPE);long ready=state.readyAt(BuildingAbility.GARDEN_RECOVERY),revision=f.account.revision;
            for(int i=0;i<64;i++){var duplicate=send(h,f.peer,request);ackOnly(h,duplicate,"cached successful CAST is not another view or balance");h.assertTrue(duplicate.status().equals("success"),"cached completion status retained");}
            var current=payload(f,garden,BuildingAbilityPackets.Action.CAST);var cd=send(h,f.peer,new BuildingAbilityPackets.Request(current,token.epoch(),token.session(),2));ackOnly(h,cd,"genuine cooldown denial stays light");h.assertTrue(cd.status().equals("cooldown"),"original paid cooldown remains enforced");
            var query=payload(f,garden,BuildingAbilityPackets.Action.QUERY);h.assertTrue(send(h,f.peer,new BuildingAbilityPackets.Request(query,token.epoch(),token.session(),0)).full()==0,"paid immediate snapshot also records ordinary query clock");
            h.assertTrue(f.account.food==food-30&&f.account.revision==revision&&state.revision()==1&&state.readyAt(BuildingAbility.GARDEN_RECOVERY)==ready,"all repeats have zero additional payment/refund/CD refresh");f.close();h.succeed();
        });
    }
    @GameTest(templateNamespace="dreamseekers_building_abilities",template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=30)
    public static void delayedAckRetiresOnlyItsOwnPendingWithoutOverwritingNewerAuthority(GameTestHelper h){
        EconomyActionClient.clear();var stream=UUID.randomUUID();var session=UUID.randomUUID();var epoch=UUID.randomUUID();var sent=new ArrayList<Long>();
        try{
            h.assertTrue(EconomyActionClient.accept(stream,10,session,epoch,0),"established real shared action authority");
            EconomyActionClient.submit("first",(s,n)->n,packet->sent.add((Long)packet));EconomyActionClient.submit("second",(s,n)->n,packet->sent.add((Long)packet));
            h.assertTrue(sent.equals(List.of(1L,2L)),"two independent pending action slots");
            h.assertTrue(EconomyActionClient.accept(stream,12,session,epoch,0),"newer view authority arrives first");
            var ack=new BuildingAbilityPackets.Ack(stream,11,session,epoch,"Owner",1,"cooldown");var bytes=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try{ack.encode(bytes);var copy=new BuildingAbilityPackets.Ack(bytes);h.assertTrue(copy.valid()&&copy.equals(ack)&&bytes.readableBytes()==0,"minimal ACK exact real codec");h.assertTrue(!EconomyActionClient.accept(copy.stream(),copy.order(),copy.session(),copy.epoch(),copy.request()),"old-order ACK cannot replace newer shared/view authority");}finally{bytes.release();}
            sent.clear();EconomyActionClient.retryPending();h.assertTrue(sent.equals(List.of(2L)),"old completion retired only first pending, preserving unrelated second action");
            h.assertTrue(!EconomyActionClient.accept(stream,9,UUID.randomUUID(),UUID.randomUUID(),2),"old-context ACK cannot reset current context");sent.clear();EconomyActionClient.retryPending();h.assertTrue(sent.equals(List.of(2L))&&EconomyActionClient.matchesContext(session,epoch),"unrelated pending and current epoch remain intact");
            h.succeed();
        }finally{EconomyActionClient.clear();}
    }
}
