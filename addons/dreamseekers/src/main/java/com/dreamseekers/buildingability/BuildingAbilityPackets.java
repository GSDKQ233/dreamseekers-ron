// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.buildingability;

import com.dreamseekers.DreamseekersMod;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.research.economy.EconomyActionGate;
import com.solegendary.reignofnether.research.economy.EconomyBalanceSync;
import com.solegendary.reignofnether.research.economy.ResearchQueueSync;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Fixed directions and owner-private snapshots. No wire prices, multipliers, target lists or target coordinates. */
@Mod.EventBusSubscriber(modid=DreamseekersMod.MOD_ID)
public final class BuildingAbilityPackets {
    public static final UUID ZERO=new UUID(0,0);
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(DreamseekersMod.id("building_abilities"),()->"2","2"::equals,"2"::equals);
    private static final Map<Connection,QueryClock> QUERIES=new WeakHashMap<>();
    private record QueryClock(UUID actor,UUID epoch,ServerLevel level,long tick){}
    private static boolean registered;
    private BuildingAbilityPackets(){}
    public static void register(){
        if(registered)return;registered=true;BuildingAbilityState.bootstrap();TowerMarkPackets.register();
        CHANNEL.messageBuilder(Request.class,0,NetworkDirection.PLAY_TO_SERVER).encoder(Request::encode).decoder(Request::new).consumerMainThread(Request::handle).add();
        CHANNEL.messageBuilder(Snapshot.class,1,NetworkDirection.PLAY_TO_CLIENT).encoder(Snapshot::encode).decoder(Snapshot::new).consumerMainThread(Snapshot::handle).add();
        CHANNEL.messageBuilder(Ack.class,2,NetworkDirection.PLAY_TO_CLIENT).encoder(Ack::encode).decoder(Ack::new).consumerMainThread(Ack::handle).add();
    }
    public enum Action { QUERY,CAST,CODEX_QUERY }
    public record Payload(Action action,ResourceLocation dimension,BlockPos origin,UUID view,UUID instance,
                          long capture,long generation,long revision,int page){
        boolean bounded(){return action!=null&&dimension!=null&&origin!=null&&view!=null&&instance!=null
            &&capture>=0&&generation>=0&&revision>=0&&page>=0&&page<=BuildingCodex.MAX_PAGE;}
    }
    public record Request(Payload payload,UUID epoch,UUID session,long sequence){
        public Request(FriendlyByteBuf b){this(new Payload(b.readEnum(Action.class),b.readResourceLocation(),b.readBlockPos(),b.readUUID(),b.readUUID(),
            b.readLong(),b.readLong(),b.readLong(),b.readVarInt()),b.readUUID(),b.readUUID(),b.readLong());}
        public void encode(FriendlyByteBuf b){
            var p=payload;b.writeEnum(p.action());b.writeResourceLocation(p.dimension());b.writeBlockPos(p.origin());b.writeUUID(p.view());b.writeUUID(p.instance());
            b.writeLong(p.capture());b.writeLong(p.generation());b.writeLong(p.revision());b.writeVarInt(p.page());b.writeUUID(epoch);b.writeUUID(session);b.writeLong(sequence);
        }
        public void handle(Supplier<NetworkEvent.Context> supplier){
            var context=supplier.get();var actor=context.getSender();var connection=context.getNetworkManager();
            context.enqueueWork(()->execute(actor,connection));context.setPacketHandled(true);
        }
        /** Same authenticated handler path is callable by focused runtime tests. */
        public void execute(ServerPlayer actor,Connection connection){
            var p=payload;
            if(actor==null||connection==null||!actor.serverLevel().getServer().isSameThread()||p==null||!p.bounded()
                    ||epoch==null||session==null||!actor.serverLevel().dimension().location().equals(p.dimension()))return;
            BuildingPlacement host=resolve(actor,p.origin());
            if(p.action()!=Action.CAST){
                if(sequence!=0||!BuildingAbilitySource.owns(actor,host)
                        ||p.action()==Action.CODEX_QUERY&&BuildingAbility.forBuilding(host)!=BuildingAbility.ECOLOGY_CODEX)return;
                var token=EconomyActionGate.query(actor,connection);if(token==null)return;
                // ZERO/ZERO is a bootstrap query. Otherwise an old connection/account token is stale.
                if(!(ZERO.equals(epoch)&&ZERO.equals(session))&&(!token.epoch().equals(epoch)||!token.session().equals(session)))return;
                if(!queryDue(actor,connection,token.epoch()))return;
                send(actor,connection,host,p.view(),p.page(),"ready",0);return;
            }
            boolean[] executed={false};
            String status=EconomyActionGate.execute(actor,connection,session,epoch,sequence,
                new EconomyActionGate.Fingerprint("building_ability",p),account->{
                    executed[0]=true;
                    return BuildingAbilityService.cast(actor,host,p.instance(),p.capture(),p.generation(),p.revision());
                });
            // A cached success is completion-only, not another snapshot/query/balance publication.
            // Only this newly executed, actually paid commit may bypass the normal query clock once.
            boolean paid=executed[0]&&("success".equals(status)||"paid_cancelled".equals(status));
            if(paid&&BuildingAbilitySource.owns(actor,host))send(actor,connection,host,p.view(),p.page(),status,sequence);
            else {
                acknowledge(actor,connection,status,sequence);
                if(paid)EconomyBalanceSync.send(actor); // Actual paid cancellation may have lost its building.
            }
        }
    }
    private static BuildingPlacement resolve(ServerPlayer actor,BlockPos origin){
        BuildingPlacement found=null;
        for(var p:BuildingServerEvents.getBuildings())if(p.level==actor.serverLevel()&&p.originPos.equals(origin)){
            if(found!=null)return null; // An ambiguous live registration never selects an arbitrary object.
            found=p;
        }
        return found;
    }
    private static boolean queryDue(ServerPlayer actor,Connection connection,UUID epoch){
        long now=actor.serverLevel().getGameTime();if(now<0)return false;
        var old=QUERIES.get(connection);
        if(old!=null&&old.actor().equals(actor.getUUID())&&old.epoch().equals(epoch)&&old.level()==actor.serverLevel()
                &&now>=old.tick()&&now-old.tick()<20)return false;
        QUERIES.put(connection,new QueryClock(actor.getUUID(),epoch,actor.serverLevel(),now));return true;
    }
    public record Snapshot(UUID stream,long order,UUID session,UUID epoch,String owner,ResourceLocation dimension,
        BlockPos origin,UUID view,UUID instance,long capture,long generation,long revision,BuildingAbility ability,
        int cooldown,int activeTicks,boolean supported,boolean available,String reason,String status,long request,BuildingCodex.Page codex){
        public Snapshot(FriendlyByteBuf b){this(b.readUUID(),b.readLong(),b.readUUID(),b.readUUID(),b.readUtf(256),b.readResourceLocation(),b.readBlockPos(),
            b.readUUID(),b.readUUID(),b.readLong(),b.readLong(),b.readLong(),b.readEnum(BuildingAbility.class),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readBoolean(),
            b.readUtf(96),b.readUtf(96),b.readLong(),readPage(b));}
        private static BuildingCodex.Page readPage(FriendlyByteBuf b){
            int page=b.readVarInt(),total=b.readVarInt();boolean truncated=b.readBoolean();int count=b.readVarInt();
            if(page<0||page>BuildingCodex.MAX_PAGE||total<0||total>BuildingCodex.MAX_ROWS||count<0||count>BuildingCodex.PAGE_SIZE)
                throw new IllegalArgumentException("Invalid codex page bounds");
            List<BuildingCodex.Row> rows=new ArrayList<>();
            for(int i=0;i<count;i++)rows.add(new BuildingCodex.Row(b.readVarInt(),b.readUUID(),b.readResourceLocation(),b.readFloat(),b.readFloat(),b.readVarInt(),b.readBoolean()));
            return new BuildingCodex.Page(page,total,truncated,rows);
        }
        public boolean valid(){return stream!=null&&order>=0&&session!=null&&epoch!=null&&owner!=null&&!owner.isBlank()&&owner.length()<=256
            &&dimension!=null&&origin!=null&&view!=null&&instance!=null&&capture>=0&&generation>=0&&revision>=0&&ability!=null
            &&cooldown>=0&&activeTicks>=0&&activeTicks<=ability.durationTicks()&&reason!=null&&reason.length()<=96
            &&status!=null&&status.length()<=96&&request>=0&&(!available||supported)&&codex!=null&&codex.valid()
            &&(ability==BuildingAbility.ECOLOGY_CODEX||codex.rows().isEmpty()&&codex.totalRows()==0);}
        public void encode(FriendlyByteBuf b){
            b.writeUUID(stream);b.writeLong(order);b.writeUUID(session);b.writeUUID(epoch);b.writeUtf(owner,256);b.writeResourceLocation(dimension);b.writeBlockPos(origin);
            b.writeUUID(view);b.writeUUID(instance);b.writeLong(capture);b.writeLong(generation);b.writeLong(revision);b.writeEnum(ability);b.writeVarInt(cooldown);b.writeVarInt(activeTicks);
            b.writeBoolean(supported);b.writeBoolean(available);b.writeUtf(reason,96);b.writeUtf(status,96);b.writeLong(request);
            b.writeVarInt(codex.page());b.writeVarInt(codex.totalRows());b.writeBoolean(codex.truncated());b.writeVarInt(codex.rows().size());
            for(var row:codex.rows()){
                b.writeVarInt(row.id());b.writeUUID(row.uuid());b.writeResourceLocation(row.typeId());b.writeFloat(row.hp());b.writeFloat(row.maxHp());b.writeVarInt(row.careState());b.writeBoolean(row.careEligible());
            }
        }
        public void handle(Supplier<NetworkEvent.Context> supplier){
            var context=supplier.get();var connection=context.getNetworkManager();
            context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->BuildingAbilityClient.accept(this,connection)));context.setPacketHandled(true);
        }
    }
    /** Completion-only: no building view, roster, balance sync, or presentation TTL refresh. */
    public record Ack(UUID stream,long order,UUID session,UUID epoch,String owner,long request,String status){
        public Ack(FriendlyByteBuf b){this(b.readUUID(),b.readLong(),b.readUUID(),b.readUUID(),b.readUtf(256),b.readLong(),b.readUtf(96));}
        public boolean valid(){return stream!=null&&order>=0&&session!=null&&epoch!=null&&owner!=null&&!owner.isBlank()
            &&owner.length()<=256&&request>=0&&status!=null&&status.length()<=96;}
        public void encode(FriendlyByteBuf b){b.writeUUID(stream);b.writeLong(order);b.writeUUID(session);b.writeUUID(epoch);b.writeUtf(owner,256);b.writeLong(request);b.writeUtf(status,96);}
        public void handle(Supplier<NetworkEvent.Context> supplier){var context=supplier.get();var connection=context.getNetworkManager();
            context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->BuildingAbilityClient.acceptAck(this,connection)));context.setPacketHandled(true);}
    }
    private static void acknowledge(ServerPlayer actor,Connection connection,String status,long request){
        if(request<0)return; // Malformed negative sequences never enter client completion state.
        var token=EconomyActionGate.query(actor,connection);if(token==null)return;
        long order=ResearchQueueSync.nextSequence();if(order<0)return;
        CHANNEL.send(PacketDistributor.PLAYER.with(()->actor),new Ack(ResearchQueueSync.snapshotSession(),order,
            token.session(),token.epoch(),actor.getGameProfile().getName(),request,status));
    }
    public static void send(ServerPlayer actor,Connection connection,BuildingPlacement host,UUID view,int page,String status,long request){
        if(!BuildingAbilitySource.owns(actor,host))return;
        var token=EconomyActionGate.query(actor,connection);var state=BuildingAbilityService.view(actor,host);if(token==null||state==null)return;
        long order=ResearchQueueSync.nextSequence();if(order<0)return;
        QUERIES.put(connection,new QueryClock(actor.getUUID(),token.epoch(),actor.serverLevel(),actor.serverLevel().getGameTime()));
        var codex=state.ability()==BuildingAbility.ECOLOGY_CODEX?BuildingCodex.query(actor,BuildingAbilitySource.capture(actor,host),page):BuildingCodex.EMPTY;
        int active=state.supported()?Math.max(0,Math.min(state.ability().durationTicks(),BuildingAbilityEffects.remaining(host))):0;
        CHANNEL.send(PacketDistributor.PLAYER.with(()->actor),new Snapshot(ResearchQueueSync.snapshotSession(),order,token.session(),token.epoch(),host.ownerName,
            host.level.dimension().location(),host.originPos,view,state.instance(),state.capture(),state.generation(),state.revision(),state.ability(),state.cooldown(),active,
            state.supported(),state.available(),state.reason(),status,request,codex));
        EconomyBalanceSync.send(actor);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){
        if(event.getEntity() instanceof ServerPlayer player)QUERIES.entrySet().removeIf(e->e.getValue().actor().equals(player.getUUID()));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event){QUERIES.clear();}
}
