package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ItemShopPlacement;
import com.solegendary.reignofnether.items.ItemClientboundPacket;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.*;

/** Existing CW02 dimension root: actual item goal setter/tick and embedded-peer OPEN_SHOP.
 * No GUI is opened, no price/inventory mutation is needed, and no new candidate API is used. */
@GameTestHolder("api65_item_dimension") @PrefixGameTestTemplate(false)
public final class Api65ItemGoalDimensionGameTests {
    @GameTest(templateNamespace="api65_item_dimension",template="ore_mine_test_arena",batch="api65_item_dimension",timeoutTicks=120)
    public static void itemGoalRejectsForeignPlacementAndKeepsLocalOpenShop(GameTestHelper h)throws Exception {
        try(Api65EconomyGameTests.F f=new Api65EconomyGameTests.F(h,"itemdim")){
            ItemShopPlacement local=(ItemShopPlacement)f.install(Buildings.VILLAGER_MARKET);
            var hero=f.hero(local);var goal=hero.getItemGoal();
            ServerLevel foreign=h.getLevel().getServer().getLevel(Level.NETHER);
            h.assertTrue(foreign!=null&&foreign!=h.getLevel(),"real distinct server dimension exists");
            var other=BuildingUtils.getNewBuildingPlacement(Buildings.VILLAGER_MARKET,foreign,local.originPos,Rotation.NONE,f.owner,false);
            other.isBuilt=true;BuildingServerEvents.getBuildings().add(other);f.buildings.add(other);
            // Far enough to observe navigation intent without completing OPEN_SHOP in this first tick.
            hero.moveTo(local.centrePos.getX()+50,local.centrePos.getY()+1,local.centrePos.getZ()+.5);
            var accountBefore=ResearchEconomyFixtures.snapshot(f.bank);goal.stopGoal();
            goal.start(null,null,null,other.originPos,other,false);goal.tick();
            h.assertTrue(goal.getMoveTarget()==null,"foreign BuildingPlacement cannot create item-goal navigation in actor dimension");
            goal.start(null,null,null,local.originPos,local,false);goal.tick();
            h.assertTrue(goal.getMoveTarget()!=null,"same-level genuine shop still supplies a navigation target");
            hero.moveTo(goal.getMoveTarget().getCenter());f.peer.drain();goal.tick();
            int discriminator=ResearchEconomyFixtures.discriminator(new ItemClientboundPacket(hero.getId(),local.originPos));int replies=0;
            for(Object output:f.peer.drain())if(output instanceof ClientboundCustomPayloadPacket payload){
                FriendlyByteBuf b=new FriendlyByteBuf(payload.getData().copy());
                try {
                    if(b.readVarInt()==discriminator){
                        int unit=b.readInt();var items=b.readList(ItemClientboundPacket::readStack);var position=b.readBlockPos();
                        if(unit==hero.getId()&&items.isEmpty()&&position.equals(local.originPos))replies++;
                    }
                } finally {b.release();}
            }
            h.assertTrue(replies==1&&goal.getMoveTarget()==null,"same-level OPEN_SHOP completes once and emits genuine peer packet without GUI");
            h.assertTrue(ResearchEconomyFixtures.snapshot(f.bank).equals(accountBefore),"opening or rejecting shop goals never changes resource account");
        }h.succeed();
    }
}
