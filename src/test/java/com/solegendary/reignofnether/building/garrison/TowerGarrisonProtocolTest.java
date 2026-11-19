package com.solegendary.reignofnether.building.garrison;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TowerGarrisonProtocolTest {
    private final UUID id=UUID.randomUUID();
    private TowerGarrisonStatusPacket packet(int cap,int inside,int mine,int approaching,long capture) {
        return new TowerGarrisonStatusPacket(id,1,id,id,"Viewer","Host",ResourceLocation.parse("minecraft:overworld"),
            new BlockPos(-13,70,28),id,id,capture,cap,inside,mine,approaching,"ready",0);
    }
    @Test void aggregateStatusRoundTripPreservesHostAndViewerSeparately() {
        var p=packet(3,3,2,1,4);var b=new FriendlyByteBuf(Unpooled.buffer());
        try{p.encode(b);assertEquals(p,new TowerGarrisonStatusPacket(b));assertFalse(b.isReadable());}finally{b.release();}
        assertTrue(p.validOccupancy());assertNotEquals(p.viewer(),p.owner());
    }
    @Test void requestsCannotClaimOccupancyOrSupplyArbitraryUnits() {
        assertEquals(List.of("action","dimension","origin","view","instance","capture","epoch","session","sequence"),
            Arrays.stream(TowerGarrisonServerboundPacket.class.getRecordComponents()).map(c->c.getName()).toList());
        for(var action:TowerGarrisonServerboundPacket.Action.values()){
            var p=new TowerGarrisonServerboundPacket(action,ResourceLocation.parse("minecraft:overworld"),BlockPos.ZERO,id,id,3,id,id,9);
            var b=new FriendlyByteBuf(Unpooled.buffer());try{p.encode(b);assertEquals(p,new TowerGarrisonServerboundPacket(b));assertFalse(b.isReadable());}finally{b.release();}
        }
    }
    @Test void invalidOrUnboundedAggregateRejected() {
        assertTrue(packet(3,0,0,0,0).validOccupancy());
        assertFalse(packet(4,3,2,1,0).validOccupancy());assertFalse(packet(3,2,3,1,0).validOccupancy());
        assertFalse(packet(3,-1,0,0,0).validOccupancy());assertFalse(packet(3,0,0,-1,0).validOccupancy());
        assertFalse(packet(3,0,0,4097,0).validOccupancy());assertFalse(packet(3,0,0,0,-1).validOccupancy());
    }
    @Test void statusDoesNotExposeAlliedEntityIdentityList() {
        assertEquals(List.of("stream","order","session","epoch","viewer","owner","dimension","origin","view","instance","capture",
            "capacity","occupied","mine","approaching","status","request"),
            Arrays.stream(TowerGarrisonStatusPacket.class.getRecordComponents()).map(c->c.getName()).toList());
    }
}
