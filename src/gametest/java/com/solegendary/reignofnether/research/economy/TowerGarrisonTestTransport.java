package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.building.garrison.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import java.util.List;

/** Test-only transport uses the existing real authenticated connection/context, never release API bypass. */
public final class TowerGarrisonTestTransport {
    private TowerGarrisonTestTransport() {}
    public static void send(ResearchEconomyFixtures.Peer peer,TowerGarrisonServerboundPacket packet) {
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try {packet.encode(b);var copy=new TowerGarrisonServerboundPacket(b);
            if(!copy.equals(packet)||b.isReadable())throw new AssertionError("tower request codec");
            copy.handle(()->peer.context);
        }finally{b.release();}peer.channel.runPendingTasks();
    }
    public static TowerGarrisonStatusPacket response(ResearchEconomyFixtures.Peer peer,TowerGarrisonStatusPacket sample) {
        int id=ResearchEconomyFixtures.discriminator(sample);TowerGarrisonStatusPacket result=null;
        for(Object packet:peer.drain())if(packet instanceof ClientboundCustomPayloadPacket custom) {
            var b=new FriendlyByteBuf(custom.getData().copy());
            try{if(b.readVarInt()==id){result=new TowerGarrisonStatusPacket(b);if(b.isReadable())throw new AssertionError("tower response trailing data");}}
            finally{b.release();}
        }
        return result;
    }
}
