package com.solegendary.reignofnether.resources;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Real wire codec plus GUI-independent client ordering; no client or world startup. */
public final class ResourceSnapshotStateTest {
    private static int assertions;
    private static final ResourceLocation ID=ResourceLocation.fromNamespaceAndPath("test_snapshot","shadow");
    private static final ResourceLocation UNKNOWN=ResourceLocation.fromNamespaceAndPath("unloaded_snapshot","private");
    private static void check(boolean ok,String why){assertions++;if(!ok)throw new AssertionError(why);}
    private static ExtendedResourcesClientboundPacket wire(ExtendedResourcesClientboundPacket packet){FriendlyByteBuf buf=new FriendlyByteBuf(Unpooled.buffer());try{packet.encode(buf);var copy=new ExtendedResourcesClientboundPacket(buf);check(buf.readableBytes()==0,"codec consumes exact packet");return copy;}finally{buf.release();}}
    public static void main(String[]args){
        ResourceExtensionRegistry.register(ID,1000,"resources.test_snapshot.shadow",ResourceLocation.parse("minecraft:textures/item/amethyst_shard.png"));
        Resources a=new Resources("Owner",0,0,0);a.revision=7;a.extra.put(ID,31);a.extra.put(UNKNOWN,99);UUID session=UUID.randomUUID();
        var first=wire(new ExtendedResourcesClientboundPacket(session,10,"Owner",a));check(first.owner.equals("Owner")&&first.session.equals(session)&&first.epoch.equals(a.accountEpoch)&&first.revision==7,"wire preserves owner/session/epoch/revision");check(first.balances.get(ID)==31&&!first.balances.containsKey(UNKNOWN),"wire excludes unregistered/private extension keys");
        ResourceSnapshotState state=new ResourceSnapshotState();check(state.accept(first)&&state.ready()&&state.balance(ID)==31,"first complete snapshot accepted");check(!state.accept(first),"duplicate sequence rejected");
        a.revision=8;a.extra.put(ID,40);check(!state.accept(wire(new ExtendedResourcesClientboundPacket(session,9,"Owner",a))),"out-of-order sequence rejected");check(state.balance(ID)==31,"rejected packet leaves displayed balance unchanged");
        a.revision=6;check(!state.accept(wire(new ExtendedResourcesClientboundPacket(session,11,"Owner",a))),"same epoch cannot rewind revision");
        a.revision=8;check(state.accept(wire(new ExtendedResourcesClientboundPacket(session,11,"Owner",a)))&&state.balance(ID)==40,"rejected packet did not consume later sequence");
        Resources fresh=new Resources("Owner",0,0,0);fresh.extra.put(ID,3);check(state.accept(wire(new ExtendedResourcesClientboundPacket(session,12,"Owner",fresh)))&&state.revision()==0&&state.epoch().equals(fresh.accountEpoch),"new account epoch may restart revision");
        check(!state.accept(wire(new ExtendedResourcesClientboundPacket(UUID.randomUUID(),13,"Owner",fresh))),"different connection session cannot overwrite established session");
        check(state.accept(wire(new ExtendedResourcesClientboundPacket(session,13,"Owner",null)))&&!state.ready()&&state.balance(ID)==0,"account removed full snapshot clears old balance");
        state.clear();UUID reconnect=UUID.randomUUID();check(!state.ready()&&state.revision()==0,"disconnect clear removes account token");check(state.accept(wire(new ExtendedResourcesClientboundPacket(reconnect,0,"Owner",fresh)))&&state.ready(),"reconnect accepts new session beginning at zero sequence");
        rejects(-1,0,0,false);rejects(0,-1,0,false);rejects(0,0,65,false);rejects(0,0,-1,false);rejects(0,0,2,true);
        System.out.println("RESOURCE_SNAPSHOT_STATE_PASS scenarios=7 assertions="+assertions);
    }
    private static void rejects(long sequence,long revision,int count,boolean duplicate){
        FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());try{
            b.writeUUID(UUID.randomUUID());b.writeLong(sequence);b.writeUtf("Owner",256);b.writeBoolean(true);b.writeUUID(UUID.randomUUID());b.writeLong(revision);b.writeVarInt(count);
            if(duplicate)for(int i=0;i<2;i++){b.writeResourceLocation(ID);b.writeVarInt(10);}
            boolean rejected=false;try{new ExtendedResourcesClientboundPacket(b);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"malformed snapshot bounds/duplicate IDs rejected");
        }finally{b.release();}
    }
}
