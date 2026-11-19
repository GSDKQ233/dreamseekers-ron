package com.solegendary.reignofnether.research.economy;

import com.mojang.authlib.GameProfile;
import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.*;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.PacketHandler;
import com.solegendary.reignofnether.resources.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.*;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.network.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Dedicated runtime test fixture, never included in a release jar. */
public final class ResearchEconomyFixtures {
    static final ResourceLocation EXTRA=ResourceLocation.parse("test_receipt_research:shadow");
    static final ResourceLocation MIXED_ID=ResourceLocation.parse("test_receipt_research:mixed");
    static final ResourceLocation OTHER_ID=ResourceLocation.parse("test_receipt_research:other");
    static ReceiptBackedResearchItem mixed,other;
    static void register(){
        ResourceExtensionRegistry.register(EXTRA,1000,"resources.test.shadow",ResourceLocation.parse("minecraft:textures/item/amethyst_shard.png"));
        if(mixed!=null)return;
        mixed=Registry.register(ReignOfNetherRegistries.PRODUCTION_ITEM,MIXED_ID,new ReceiptBackedResearchItem(MIXED_ID,ResourceCost.Research(11,13,17,60),Map.of(EXTRA,19),ResourceLocation.parse("minecraft:textures/item/amethyst_shard.png"),"test","test",p->p.getBuilding()==Buildings.BLACKSMITH));
        other=Registry.register(ReignOfNetherRegistries.PRODUCTION_ITEM,OTHER_ID,new ReceiptBackedResearchItem(OTHER_ID,ResourceCost.Research(7,5,3,60),Map.of(EXTRA,2),ResourceLocation.parse("minecraft:textures/item/amethyst_shard.png"),"test2","test2",p->p.getBuilding()==Buildings.BLACKSMITH));
        // Test-only item additions in this isolated GameTest JVM; core research prices remain unchanged.
        ((ProductionBuilding)Buildings.BLACKSMITH).productions.add(mixed,null);((ProductionBuilding)Buildings.BLACKSMITH).productions.add(other,null);
    }
    static Resources account(String name,int amount){PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(name,Factions.VILLAGERS));Resources r=new Resources(name,amount,amount,amount);r.extra.put(EXTRA,amount);ResourcesServerEvents.resourcesList.add(r);return r;}
    public static CompoundTag snapshot(Resources r){CompoundTag t=new CompoundTag();t.putInt("food",r.food);t.putInt("wood",r.wood);t.putInt("ore",r.ore);t.putInt("emerald",r.emerald);r.writeExtended(t);return t;}
    static ProductionPlacement install(ServerLevel l,String owner,BlockPos origin){
        for(int x=-2;x<28;x++)for(int z=-2;z<28;z++){l.setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);for(int y=1;y<20;y++)l.setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);}
        for(BuildingBlock b:BuildingUtils.getAbsoluteBlockData(Buildings.BLACKSMITH.getRelativeBlockData(l),l,origin,Rotation.NONE))l.setBlock(b.getBlockPos(),b.getBlockState(),2);
        ProductionPlacement p=(ProductionPlacement)BuildingUtils.getNewBuildingPlacement(Buildings.BLACKSMITH,l,origin,Rotation.NONE,owner,false);BuildingServerEvents.getBuildings().add(p);return p;
    }
    public static final class Peer implements AutoCloseable {
        final ServerPlayer actor;final Connection connection;final EmbeddedChannel channel;final NetworkEvent.Context context;final ServerGamePacketListenerImpl previous;final PlayerRegistration registration;private boolean closed;
        public Peer(ServerLevel level,String owner)throws Exception{
            actor=FakePlayerFactory.get(level,new GameProfile(UUID.nameUUIDFromBytes(owner.getBytes(StandardCharsets.UTF_8)),owner));previous=actor.connection;
            connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);
            PlayerRegistration attached=null;
            try {
                connection.setProtocol(ConnectionProtocol.PLAY);actor.connection=new ServerGamePacketListenerImpl(level.getServer(),connection,actor);connection.setListener(actor.connection);
                attached=new PlayerRegistration(actor);
                var ctor=NetworkEvent.Context.class.getDeclaredConstructor(Connection.class,NetworkDirection.class,int.class);ctor.setAccessible(true);context=ctor.newInstance(connection,NetworkDirection.PLAY_TO_SERVER,-1);
                if(!connection.isConnected()||context.getSender()!=actor||connection.getPacketListener()!=actor.connection||level.getServer().getPlayerList().getPlayerByName(owner)!=actor||level.getServer().getPlayerList().getPlayer(actor.getUUID())!=actor)throw new AssertionError("exact connected peer fixture not established");
                registration=attached;
            } catch(Exception|Error failure) {
                try{if(attached!=null)attached.close();}catch(RuntimeException|Error cleanup){failure.addSuppressed(cleanup);}
                try{channel.finishAndReleaseAll();}catch(RuntimeException|Error cleanup){failure.addSuppressed(cleanup);}
                finally{actor.connection=previous;}
                failure.printStackTrace();throw failure;
            }
        }
        public void send(ResearchEconomyServerboundPacket packet){
            FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());try{packet.encode(b);var decoded=new ResearchEconomyServerboundPacket(b);if(!decoded.equals(packet)||b.readableBytes()!=0)throw new AssertionError("request codec differs");decoded.handle(()->context);}finally{b.release();}
            channel.runPendingTasks();
        }
        /** Test-only authenticated sandbox transport. Exposes no connection/authority bypass to production. */
        public void send(com.solegendary.reignofnether.sandbox.SandboxServerboundPacket packet){
            FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());
            try{
                packet.encode(b);
                var decoded=new com.solegendary.reignofnether.sandbox.SandboxServerboundPacket(b);
                if(b.readableBytes()!=0||decoded.sandboxAction!=packet.sandboxAction
                        ||!decoded.playerName.equals(packet.playerName)||!decoded.unitName.equals(packet.unitName)
                        ||!decoded.blockPos.equals(packet.blockPos)||!Arrays.equals(decoded.entityIds,packet.entityIds))
                    throw new AssertionError("sandbox request codec differs");
                decoded.handle(()->context);
            }finally{b.release();}
            channel.runPendingTasks();
        }
        public List<Object> drain(){channel.runPendingTasks();List<Object> output=new ArrayList<>();Object o;while((o=channel.readOutbound())!=null)output.add(o);return output;}
        public void close(){if(closed)return;closed=true;try{registration.close();}finally{try{channel.finishAndReleaseAll();}finally{actor.connection=previous;}}}
    }
    public static ResearchEconomyServerboundPacket query(ProductionPlacement p){return new ResearchEconomyServerboundPacket(ResearchEconomyServerboundPacket.Action.QUERY,p.level.dimension().location(),p.originPos,MIXED_ID,ResearchEconomyServerboundPacket.ZERO,ResearchEconomyServerboundPacket.ZERO,0,ResearchEconomyServerboundPacket.ZERO,ResearchEconomyServerboundPacket.ZERO,0);}
    public static ResearchEconomyServerboundPacket request(ProductionPlacement p,ResearchQueueClientboundPacket token,long sequence,ResearchEconomyServerboundPacket.Action action,ResourceLocation item,UUID receipt){return new ResearchEconomyServerboundPacket(action,p.level.dimension().location(),p.originPos,item,receipt,token.instance,token.capture,token.epoch,token.actionSession,sequence);}
    static int discriminator(Object message){FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());try{PacketHandler.INSTANCE.encodeMessage(message,b);return b.readVarInt();}finally{b.release();}}
    public static ResearchQueueClientboundPacket response(List<Object> output,ProductionPlacement p,Resources r){
        var sample=new ResearchQueueClientboundPacket(UUID.randomUUID(),0,"",p,r,new EconomyActionGate.Token(UUID.randomUUID(),r.accountEpoch),0);int id=discriminator(sample);
        ResearchQueueClientboundPacket latest=null;
        for(Object packet:output)if(packet instanceof ClientboundCustomPayloadPacket custom){FriendlyByteBuf b=new FriendlyByteBuf(custom.getData().copy());try{if(b.readVarInt()==id){var decoded=new ResearchQueueClientboundPacket(b);if(decoded.origin.equals(p.originPos))latest=decoded;}}finally{b.release();}}
        if(latest!=null)return latest;throw new AssertionError("real handler sent no queue/session response on owner channel");
    }
    static void checkBalancesReply(GameTestHelper h,List<Object> output,Resources r){
        int id=discriminator(new ResourcesClientboundPacket(ResourcesAction.SYNC,r.ownerName,0,0,0,0,BlockPos.ZERO,""));ResourcesClientboundPacket latest=null;
        for(Object packet:output)if(packet instanceof ClientboundCustomPayloadPacket custom){FriendlyByteBuf b=new FriendlyByteBuf(custom.getData().copy());try{if(b.readVarInt()==id){b.markReaderIndex();ResourcesAction action=b.readEnum(ResourcesAction.class);b.resetReaderIndex();var reply=new ResourcesClientboundPacket(b);if(action==ResourcesAction.SYNC&&reply.ownerName.equals(r.ownerName))latest=reply;}}finally{b.release();}}
        h.assertTrue(latest!=null,"real action reply includes authoritative FWO sync");
        h.assertTrue(latest.food==r.food&&latest.wood==r.wood&&latest.ore==r.ore,"last ordered owner-private absolute FWO snapshot matches live account; older periodic snapshots may differ");
        int extensionId=discriminator(new ExtendedResourcesClientboundPacket(UUID.randomUUID(),0,r.ownerName,r));ExtendedResourcesClientboundPacket latestExtension=null;
        for(Object packet:output)if(packet instanceof ClientboundCustomPayloadPacket custom){FriendlyByteBuf b=new FriendlyByteBuf(custom.getData().copy());try{if(b.readVarInt()==extensionId){var reply=new ExtendedResourcesClientboundPacket(b);if(reply.owner.equals(r.ownerName))latestExtension=reply;}}finally{b.release();}}
        h.assertTrue(latestExtension!=null,"real action reply includes owner-private extension snapshot");
        h.assertTrue(latestExtension.present&&latestExtension.epoch.equals(r.accountEpoch)&&latestExtension.revision==r.revision&&Objects.equals(latestExtension.balances.get(EXTRA),r.extra.get(EXTRA)),"last ordered extension snapshot uses current epoch/revision/shadow balance, never cached action-time ledger");
    }

    /** Test-only registry attachment: Forge getPlayers() is an unmodifiable view.
     * Never calls a fake login or relaxes product sender/connection/account authority.
     * Backing fields verified in this pinned1.20.1 mapping: f_11196_=players, f_11197_=playersByUUID. */
    private static final class PlayerRegistration implements AutoCloseable {
        private final net.minecraft.server.players.PlayerList list;
        private final List<ServerPlayer> players;
        private final Map<UUID,ServerPlayer> byId;
        private final ServerPlayer actor;
        private final boolean addedList,addedMap;
        private boolean closed;
        PlayerRegistration(ServerPlayer actor) {
            if(!actor.getServer().isSameThread())throw new AssertionError("peer registration must use real server thread");
            this.actor=actor;list=actor.getServer().getPlayerList();
            players=net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(net.minecraft.server.players.PlayerList.class,list,"f_11196_");
            byId=net.minecraftforge.fml.util.ObfuscationReflectionHelper.getPrivateValue(net.minecraft.server.players.PlayerList.class,list,"f_11197_");
            if(players==null||byId==null)throw new AssertionError("pinned player registry fields unavailable");
            ServerPlayer named=list.getPlayerByName(actor.getName().getString()),identified=byId.get(actor.getUUID());
            if((named!=null&&named!=actor)||(identified!=null&&identified!=actor))throw new AssertionError("never replace another registered player identity");
            if(players.stream().filter(p->p==actor).count()>1)throw new AssertionError("duplicate live player registry entry");
            addedList=!players.contains(actor);addedMap=identified==null;
            if(addedList)players.add(actor);
            if(addedMap)byId.put(actor.getUUID(),actor);
            if(list.getPlayerByName(actor.getName().getString())!=actor||list.getPlayer(actor.getUUID())!=actor) {
                close();throw new AssertionError("native name and UUID lookups must resolve exact peer");
            }
        }
        @Override public void close(){
            if(closed)return;closed=true;
            if(addedMap)byId.remove(actor.getUUID(),actor);
            if(addedList)players.removeIf(p->p==actor);
        }
    }
}
