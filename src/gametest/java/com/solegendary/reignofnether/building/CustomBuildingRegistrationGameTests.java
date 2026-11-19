package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.building.custombuilding.*;
import com.solegendary.reignofnether.research.economy.ResearchEconomyFixtures.Peer;
import com.solegendary.reignofnether.registrars.PacketHandler;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Uses only API63 entry points; runs unchanged against the baseline for the missing-packet regression. */
@GameTestHolder("api64_custom_registration") @PrefixGameTestTemplate(false)
public final class CustomBuildingRegistrationGameTests {
    @GameTest(templateNamespace="api64_custom_registration",template="ore_mine_test_arena",batch="custom_registration",timeoutTicks=100)
    public static void registeringSecondCustomBuildingSendsItsPlacement(GameTestHelper h) throws Exception {
        String owner="CustomReg"+Math.abs(h.absolutePos(BlockPos.ZERO).asLong());
        BlockPos first=h.absolutePos(new BlockPos(2,2,2)),second=first.offset(4,0,0);
        h.getLevel().setBlockAndUpdate(first,Blocks.STONE.defaultBlockState());h.getLevel().setBlockAndUpdate(second,Blocks.STONE.defaultBlockState());
        var existing=new BuildingPlacement(Buildings.BARRACKS,h.getLevel(),first,Rotation.NONE,owner,new ArrayList<>(List.of(new BuildingBlock(first,Blocks.STONE.defaultBlockState()))),false);
        existing.isBuilt=true;BuildingServerEvents.getBuildings().add(existing);
        ResourceLocation template=ResourceLocation.fromNamespaceAndPath("api64_custom_registration","single_stone");
        h.getLevel().getStructureManager().getOrCreate(template).fillFromWorld(h.getLevel(),second,new Vec3i(1,1,1),false,Blocks.STRUCTURE_VOID);
        var before=new HashSet<>(CustomBuildingServerEvents.customBuildings);
        try(Peer peer=new Peer(h.getLevel(),owner)) {
            h.assertTrue(CustomBuildingServerEvents.createAndRegisterNewCustomBuilding(template,"PacketReg"+owner,h.getLevel(),second,new Vec3i(1,1,1)),"actual structure-save registration succeeds");
            var added=BuildingServerEvents.getBuildings().stream().filter(b->b.originPos.equals(second)).findFirst().orElseThrow();
            h.assertTrue(BuildingServerEvents.getBuildings().indexOf(added)>0,"regression target is not first entry");
            var sample=new BuildingClientboundPacket(BuildingAction.PLACE_CUSTOM,BuildingClientboundPacket.EMPTY,added.getBuilding().name,second,Rotation.NONE,owner,-1,0,0,false,0,false,com.solegendary.reignofnether.building.buildings.placements.PortalPlacement.PortalType.BASIC,BlockPos.ZERO);
            FriendlyByteBuf encoded=new FriendlyByteBuf(Unpooled.buffer());int discriminator;
            try{PacketHandler.INSTANCE.encodeMessage(sample,encoded);discriminator=encoded.readVarInt();}finally{encoded.release();}
            int found=0;
            for(Object packet:peer.drain()) if(packet instanceof ClientboundCustomPayloadPacket payload) {
                FriendlyByteBuf b=new FriendlyByteBuf(payload.getData().copy());
                try{if(b.readVarInt()==discriminator){var decoded=new BuildingClientboundPacket(b);if(decoded.action==BuildingAction.PLACE_CUSTOM&&decoded.buildingPos.equals(second))found++;}}finally{b.release();}
            }
            h.assertTrue(found==1,"newly registered non-first custom building sends exactly one real placement packet");
        } finally {
            BuildingServerEvents.getBuildings().removeIf(b->b==existing||b.originPos.equals(second));
            CustomBuildingServerEvents.customBuildings.removeIf(b->!before.contains(b));
            CustomBuildingServerEvents.saveCustomBuildings(h.getLevel());BuildingServerEvents.saveBuildings(h.getLevel());
        }
        h.succeed();
    }
}
