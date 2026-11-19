package com.dreamseekers;
import com.solegendary.reignofnether.research.economy.*;
import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.progression.*;
import com.dreamseekers.economy.DreamPassiveEconomy;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;
/** Reuses the existing real codec/handler/NetworkEvent.Context embedded peer fixture, not a GUI or handshake. */
@GameTestHolder("dreamseekers_wind_protocol") @PrefixGameTestTemplate(false)
public final class WindTierProtocolGameTests {
 private static final String OWNER="WindProtocol_Owner",OBSERVER="WindProtocol_Observer",BATCH="wind_tier_protocol";
 private static final List<ProductionPlacement> HOSTS=new ArrayList<>();private static final Set<Long> CHUNKS=new HashSet<>();
 private static final List<ResearchEconomyFixtures.Peer> PEERS=new ArrayList<>();
 @AfterBatch(batch=BATCH) public static void cleanup(ServerLevel level){for(var p:HOSTS){p.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(p);}HOSTS.clear();for(var p:PEERS)p.close();PEERS.clear();PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(OWNER)||p.name.equals(OBSERVER));ResourcesServerEvents.resourcesList.removeIf(r->r.ownerName.equals(OWNER)||r.ownerName.equals(OBSERVER));for(long k:CHUNKS){var c=new ChunkPos(k);level.setChunkForced(c.x,c.z,false);}CHUNKS.clear();}
 private static Resources account(String owner){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,DreamseekersMod.DREAMSEEKERS));var r=new Resources(owner,10000,10000,10000);r.extra.put(DreamPassiveEconomy.SHADOW,1000);ResourcesServerEvents.resourcesList.add(r);return r;}
 private static ProductionPlacement install(ServerLevel level,Building def,int dx){var pos=new BlockPos(14000+dx,100,14000);for(int x=(pos.getX()-4)>>4;x<=(pos.getX()+30)>>4;x++)for(int z=(pos.getZ()-4)>>4;z<=(pos.getZ()+30)>>4;z++){long k=ChunkPos.asLong(x,z);if(!level.getForcedChunks().contains(k)){level.setChunkForced(x,z,true);CHUNKS.add(k);}}for(int x=-3;x<28;x++)for(int z=-3;z<28;z++){level.setBlock(pos.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<22;y++)level.setBlock(pos.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}for(var b:BuildingUtils.getAbsoluteBlockData(def.getRelativeBlockData(level),level,pos,Rotation.NONE))level.setBlock(b.getBlockPos(),b.getBlockState(),2);var p=(ProductionPlacement)BuildingUtils.getNewBuildingPlacement(def,level,pos,Rotation.NONE,OWNER,false);BuildingServerEvents.getBuildings().add(p);p.onBuilt();HOSTS.add(p);return p;}
 private static List<DreamWindPackets.Snapshot> snapshots(List<Object> out){var list=new ArrayList<DreamWindPackets.Snapshot>();for(var packet:out)if(packet instanceof ClientboundCustomPayloadPacket custom&&custom.getIdentifier().equals(DreamseekersMod.id("wind_progression"))){var b=new FriendlyByteBuf(custom.getData().copy());try{if(b.readVarInt()==1){var snapshot=new DreamWindPackets.Snapshot(b);if(b.readableBytes()!=0)throw new AssertionError("unexpected private payload fields");list.add(snapshot);}}finally{b.release();}}return list;}
 @GameTest(template="dreamseekers_test_arena",batch=BATCH,timeoutTicks=100)
 public static void actualTierPacketReplayReservationEpochAndPrivateSnapshot(GameTestHelper h)throws Exception{
  var a=account(OWNER);account(OBSERVER);install(h.getLevel(),DreamseekersMod.DREAM_ANCHOR,0);var p=install(h.getLevel(),DreamseekersMod.RESONANCE_LABORATORY,40);var q=install(h.getLevel(),DreamseekersMod.RESONANCE_LABORATORY,80);h.assertTrue(DreamWindProgression.earnWind(h.getLevel(),OWNER,a.accountEpoch),"trusted entitlement fixture, branch payment tested separately");
  var owner=new ResearchEconomyFixtures.Peer(h.getLevel(),OWNER);var observer=new ResearchEconomyFixtures.Peer(h.getLevel(),OBSERVER);PEERS.add(owner);PEERS.add(observer);
  h.runAtTickTime(20,()->{
   owner.drain();observer.drain();owner.send(ResearchEconomyFixtures.query(p));var token=ResearchEconomyFixtures.response(owner.drain(),p,a);owner.send(ResearchEconomyFixtures.query(q));var second=ResearchEconomyFixtures.response(owner.drain(),q,a);var tier=DreamWindResearch.TIERS[0][0];
   var request=ResearchEconomyFixtures.request(p,token,1,ResearchEconomyServerboundPacket.Action.START,tier.id(),ResearchEconomyServerboundPacket.ZERO);owner.send(request);h.assertTrue(ResearchEconomyFixtures.response(owner.drain(),p,a).status.equals("started"),"real tier codec/handler accepted canonical first tier");h.assertTrue(a.food==9920&&a.wood==9900&&a.ore==9940&&a.extra.get(DreamPassiveEconomy.SHADOW)==990&&p.productionQueue.size()==1&&a.paidReceipts.size()==1,"one exact canonical payment");var paid=ResearchEconomyFixtures.snapshot(a);
   for(int n=0;n<3;n++){owner.send(request);h.assertTrue(ResearchEconomyFixtures.response(owner.drain(),p,a).status.equals("started")&&ResearchEconomyFixtures.snapshot(a).equals(paid)&&p.productionQueue.size()==1,"same nonce cached success without double debit");}
   owner.send(ResearchEconomyFixtures.request(p,token,1,ResearchEconomyServerboundPacket.Action.START,DreamWindResearch.TIERS[0][1].id(),ResearchEconomyServerboundPacket.ZERO));h.assertTrue(ResearchEconomyFixtures.response(owner.drain(),p,a).status.equals("replay_mismatch")&&ResearchEconomyFixtures.snapshot(a).equals(paid),"same nonce changed tier payload rejects");
   owner.send(ResearchEconomyFixtures.request(q,second,2,ResearchEconomyServerboundPacket.Action.START,tier.id(),ResearchEconomyServerboundPacket.ZERO));h.assertTrue(ResearchEconomyFixtures.response(owner.drain(),q,a).status.equals("duplicate")&&q.productionQueue.isEmpty()&&ResearchEconomyFixtures.snapshot(a).equals(paid),"second lab cannot reserve same owner-family");
   owner.send(ResearchEconomyFixtures.request(q,second,3,ResearchEconomyServerboundPacket.Action.START,DreamWindResearch.TIERS[0][1].id(),ResearchEconomyServerboundPacket.ZERO));h.assertTrue(ResearchEconomyFixtures.response(owner.drain(),q,a).status.equals("forbidden")&&q.productionQueue.isEmpty()&&DreamWindProgression.read(a).level(0)==0&&ResearchEconomyFixtures.snapshot(a).equals(paid),"fresh nonce cannot jump next tier before completion");
   var receipt=p.productionQueue.get(0).receiptResearch.receiptId();owner.send(ResearchEconomyFixtures.request(p,token,4,ResearchEconomyServerboundPacket.Action.CANCEL,tier.id(),receipt));h.assertTrue(ResearchEconomyFixtures.response(owner.drain(),p,a).status.equals("cancelled_refunded")&&a.food==10000&&a.wood==10000&&a.ore==10000&&a.extra.get(DreamPassiveEconomy.SHADOW)==1000&&a.paidReceipts.isEmpty(),"real packet cancellation restores exact original claim");
   DreamWindProgression.commitTier(a,0,1);owner.drain();observer.drain();DreamWindPackets.sendToOwner(h.getLevel().getServer(),OWNER);var privateSnapshots=snapshots(owner.drain());h.assertTrue(privateSnapshots.size()==1&&privateSnapshots.get(0).owner().equals(OWNER)&&privateSnapshots.get(0).epoch().equals(a.accountEpoch)&&privateSnapshots.get(0).wind()&&privateSnapshots.get(0).cost()==1&&privateSnapshots.get(0).train()==0&&privateSnapshots.get(0).research()==0,"actual private encoded typed progression owner snapshot");h.assertTrue(snapshots(observer.drain()).isEmpty(),"connected observer gets no Wind progression snapshot");
   var oldEpoch=a.accountEpoch;a.resetExtendedAccount();h.assertTrue(!a.accountEpoch.equals(oldEpoch)&&!DreamWindProgression.read(a).wind()&&DreamWindProgression.read(a).level(0)==0,"same-name new epoch resets entitlement and tier state");var reset=ResearchEconomyFixtures.snapshot(a);owner.send(request);h.assertTrue(ResearchEconomyFixtures.response(owner.drain(),p,a).status.equals("stale_session")&&ResearchEconomyFixtures.snapshot(a).equals(reset)&&p.productionQueue.isEmpty()&&q.productionQueue.isEmpty(),"old accepted nonce cannot reuse cached success or charge replacement epoch");
   System.out.println("DREAM_WIND_PROTOCOL_PASS real_codec_handler replay_mismatch global_family_reservation skip_rejected epoch_reset owner_private_snapshot");h.succeed();
  });
 }
}
