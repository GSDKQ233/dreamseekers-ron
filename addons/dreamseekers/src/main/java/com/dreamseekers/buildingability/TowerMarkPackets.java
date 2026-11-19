// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.buildingability;

import com.dreamseekers.DreamseekersMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.*;
import java.util.function.Supplier;

/** S2C only. Enemy data is IDs/UUIDs, never positions, global glow or a fog update. */
public final class TowerMarkPackets {
    private static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(DreamseekersMod.id("tower_observations"),()->"1","1"::equals,"1"::equals);
    private static boolean registered;
    private TowerMarkPackets() { }
    public static void register(){if(registered)return;registered=true;
        CHANNEL.messageBuilder(Snapshot.class,0,NetworkDirection.PLAY_TO_CLIENT).encoder(Snapshot::encode).decoder(Snapshot::new).consumerMainThread(Snapshot::handle).add();}
    public record Target(int id,UUID uuid) { }
    public record Snapshot(long order,UUID session,UUID epoch,String owner,ResourceLocation dimension,BlockPos origin,
            UUID instance,long generation,long revision,int remaining,List<Target> targets) {
        public Snapshot{targets=List.copyOf(targets);}
        public Snapshot(FriendlyByteBuf b){this(b.readLong(),b.readUUID(),b.readUUID(),b.readUtf(256),b.readResourceLocation(),b.readBlockPos(),
                b.readUUID(),b.readLong(),b.readLong(),b.readVarInt(),readTargets(b));}
        private static List<Target> readTargets(FriendlyByteBuf b){int n=b.readVarInt();if(n<0||n>6)throw new IllegalArgumentException("Tower target limit");
            List<Target> out=new ArrayList<>();for(int i=0;i<n;i++)out.add(new Target(b.readVarInt(),b.readUUID()));return out;}
        public boolean valid(){if(order<0||session==null||epoch==null||owner==null||owner.isBlank()||owner.length()>256||dimension==null||origin==null
                ||instance==null||generation<0||revision<0||remaining<0||remaining>120||targets.size()>6||remaining==0&&!targets.isEmpty())return false;
            Set<Integer> ids=new HashSet<>();Set<UUID> uuids=new HashSet<>();for(var target:targets)
                if(target.id()<0||target.uuid()==null||!ids.add(target.id())||!uuids.add(target.uuid()))return false;return true;}
        public void encode(FriendlyByteBuf b){b.writeLong(order);b.writeUUID(session);b.writeUUID(epoch);b.writeUtf(owner,256);b.writeResourceLocation(dimension);b.writeBlockPos(origin);
            b.writeUUID(instance);b.writeLong(generation);b.writeLong(revision);b.writeVarInt(remaining);b.writeVarInt(targets.size());for(var target:targets){b.writeVarInt(target.id());b.writeUUID(target.uuid());}}
        public void handle(Supplier<NetworkEvent.Context> supplier){var c=supplier.get();Connection connection=c.getNetworkManager();
            c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->TowerMarkClient.accept(this,connection)));c.setPacketHandled(true);}
    }
    static void send(ServerPlayer recipient,Connection connection,Snapshot snapshot){
        if(recipient!=null&&connection!=null&&connection.isConnected()&&connection.getPacketListener()==recipient.connection&&snapshot.valid())
            CHANNEL.send(PacketDistributor.PLAYER.with(()->recipient),snapshot);
    }
}
