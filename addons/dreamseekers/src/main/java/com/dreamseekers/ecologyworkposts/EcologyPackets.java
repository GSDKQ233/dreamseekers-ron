package com.dreamseekers.ecologyworkposts;

import com.dreamseekers.DreamseekersMod;
import com.dreamseekers.unitprogression.DreamSpecialistResearch;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.research.economy.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;
import java.util.function.Supplier;

/** Bounded requests. Identity, account and live membership are checked on the server thread. */
public final class EcologyPackets {
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(DreamseekersMod.id("ecology_workposts"),()->"1","1"::equals,"1"::equals);
    private static boolean registered;
    public static final UUID ZERO=new UUID(0,0);
    private EcologyPackets(){}
    public static void register(){if(registered)return;registered=true;
        CHANNEL.messageBuilder(Request.class,0,NetworkDirection.PLAY_TO_SERVER).encoder(Request::encode).decoder(Request::new).consumerMainThread(Request::handle).add();
        CHANNEL.messageBuilder(Snapshot.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder(Snapshot::encode).decoder(Snapshot::new).consumerMainThread(Snapshot::handle).add();
    }
    public enum Action { QUERY, ASSIGN, RELEASE, RELEASE_ALL }
    public record Target(int id,UUID uuid) {}
    public record Payload(Action action,ResourceLocation dimension,BlockPos origin,UUID view,UUID instance,long capture,UUID lease,List<Target> targets) {
        public Payload {targets=List.copyOf(targets);}
    }
    public record Request(Payload payload,UUID epoch,UUID session,long sequence) {
        public Request(FriendlyByteBuf b){this(readPayload(b),b.readUUID(),b.readUUID(),b.readLong());}
        private static Payload readPayload(FriendlyByteBuf b){
            Action action=b.readEnum(Action.class);ResourceLocation dimension=b.readResourceLocation();BlockPos origin=b.readBlockPos();
            UUID view=b.readUUID(),instance=b.readUUID();long capture=b.readLong();UUID lease=b.readUUID();int count=b.readVarInt();
            if(count<0||count>64)throw new IllegalArgumentException("Invalid ecology target count");
            List<Target> targets=new ArrayList<>();for(int i=0;i<count;i++)targets.add(new Target(b.readVarInt(),b.readUUID()));
            return new Payload(action,dimension,origin,view,instance,capture,lease,targets);
        }
        public void encode(FriendlyByteBuf b){var p=payload;b.writeEnum(p.action());b.writeResourceLocation(p.dimension());b.writeBlockPos(p.origin());
            b.writeUUID(p.view());b.writeUUID(p.instance());b.writeLong(p.capture());b.writeUUID(p.lease());b.writeVarInt(p.targets().size());
            for(var t:p.targets()){b.writeVarInt(t.id());b.writeUUID(t.uuid());}b.writeUUID(epoch);b.writeUUID(session);b.writeLong(sequence);}
        public void handle(Supplier<NetworkEvent.Context> supplier){var c=supplier.get();var actor=c.getSender();var connection=c.getNetworkManager();
            c.enqueueWork(()->{
                var p=payload;if(actor==null||!actor.level().dimension().location().equals(p.dimension())||p.targets().size()>64)return;
                BuildingPlacement court=BuildingServerEvents.getBuildings().stream().filter(b->b.level==actor.level()&&b.originPos.equals(p.origin())).findFirst().orElse(null);
                if(p.action()==Action.QUERY){if(EcologyWorkPosts.owns(actor,court))send(actor,connection,court,p.view(),"ready",0);return;}
                String status=EconomyActionGate.execute(actor,connection,session,epoch,sequence,new EconomyActionGate.Fingerprint("ecology_workposts",p),a->{
                    if(p.action()==Action.ASSIGN)return EcologyWorkPosts.assign(actor,court,p.instance(),p.capture(),p.targets());
                    return EcologyWorkPosts.release(actor,court,p.instance(),p.capture(),p.action()==Action.RELEASE?p.lease():null);
                });
                if(EcologyWorkPosts.owns(actor,court))send(actor,connection,court,p.view(),status,sequence);
                else EconomyActionResultPacket.send(actor,connection,status,sequence);
            });c.setPacketHandled(true);
        }
    }
    public record Snapshot(UUID stream,long order,UUID session,UUID epoch,String owner,ResourceLocation dimension,
        BlockPos origin,UUID view,UUID instance,long capture,boolean researched,List<EcologyWorkPosts.Occupant> occupants,String status,long request) {
        public Snapshot {occupants=List.copyOf(occupants);}
        public Snapshot(FriendlyByteBuf b){this(b.readUUID(),b.readLong(),b.readUUID(),b.readUUID(),b.readUtf(256),b.readResourceLocation(),
            b.readBlockPos(),b.readUUID(),b.readUUID(),b.readLong(),b.readBoolean(),readOccupants(b),b.readUtf(96),b.readLong());}
        private static List<EcologyWorkPosts.Occupant> readOccupants(FriendlyByteBuf b){
            int count=b.readVarInt();if(count<0||count>EcologyWorkPosts.CAPACITY)throw new IllegalArgumentException("Invalid ecology occupancy");
            List<EcologyWorkPosts.Occupant> out=new ArrayList<>();for(int i=0;i<count;i++)out.add(new EcologyWorkPosts.Occupant(b.readVarInt(),b.readUUID(),b.readUUID(),b.readBlockPos(),b.readBoolean()));return out;
        }
        public boolean valid(){if(capture<0||occupants.size()>EcologyWorkPosts.CAPACITY)return false;
            Set<Integer> ids=new HashSet<>();Set<UUID> uuids=new HashSet<>(),leases=new HashSet<>();Set<BlockPos> posts=new HashSet<>();
            for(var e:occupants)if(!ids.add(e.id())||!uuids.add(e.uuid())||!leases.add(e.lease())||!posts.add(e.post()))return false;return true;}
        public void encode(FriendlyByteBuf b){b.writeUUID(stream);b.writeLong(order);b.writeUUID(session);b.writeUUID(epoch);b.writeUtf(owner,256);b.writeResourceLocation(dimension);
            b.writeBlockPos(origin);b.writeUUID(view);b.writeUUID(instance);b.writeLong(capture);b.writeBoolean(researched);b.writeVarInt(occupants.size());
            for(var e:occupants){b.writeVarInt(e.id());b.writeUUID(e.uuid());b.writeUUID(e.lease());b.writeBlockPos(e.post());b.writeBoolean(e.arrived());}b.writeUtf(status,96);b.writeLong(request);}
        public void handle(Supplier<NetworkEvent.Context> supplier){var c=supplier.get();var connection=c.getNetworkManager();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->EcologyClient.accept(this,connection)));c.setPacketHandled(true);}
    }
    public static void send(ServerPlayer actor,Connection connection,BuildingPlacement court,UUID view,String status,long request){
        if(!EcologyWorkPosts.owns(actor,court))return;var token=EconomyActionGate.query(actor,connection);if(token==null)return;
        long order=ResearchQueueSync.nextSequence();if(order<0)return;
        CHANNEL.send(PacketDistributor.PLAYER.with(()->actor),new Snapshot(ResearchQueueSync.snapshotSession(),order,token.session(),token.epoch(),court.ownerName,
            court.level.dimension().location(),court.originPos,view,ResearchPlacementIdentity.serverIdentity(court),court.getCaptureRevision(),
            DreamSpecialistResearch.careUnlocked(court.ownerName,false),EcologyWorkPosts.snapshot(court),status,request));
        EconomyBalanceSync.send(actor);
    }
}
