package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("reignofnether_research_economy")
@PrefixGameTestTemplate(false)
public final class SandboxCoreEconomyResearchGameTests {
    @GameTest(templateNamespace="reignofnether_research_economy",template="ore_mine_test_arena",batch="sandbox_core_economy_research",timeoutTicks=80)
    public static void realSandboxOwnerCanPayAndRefundAllThreeButNormalOtherFactionCannot(GameTestHelper h)throws Exception {
        final String name="SandboxCoreTech";
        ResearchEconomyFixtures.register();
        var account=ResearchEconomyFixtures.account(name,1000);
        var player=PlayerServerEvents.getRTSPlayer(name);player.faction=Factions.NONE;
        Building[] hosts={Buildings.BLACKSMITH,Buildings.LABORATORY,Buildings.BASALT_SPRINGS};
        ReceiptBackedResearchItem[] items={ProductionItems.RESEARCH_EXPANDED_WORKFORCE,ProductionItems.RESEARCH_GLOBAL_SALVAGE,ProductionItems.RESEARCH_INSTANT_SMELTING};
        List<ProductionPlacement> placements=new ArrayList<>();
        for(int i=0;i<hosts.length;i++) {
            BlockPos origin=h.absolutePos(new BlockPos(5+i*32,1,5));
            for(int x=-1;x<27;x++)for(int z=-1;z<27;z++) {
                h.getLevel().setBlock(origin.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
                for(int y=1;y<22;y++)h.getLevel().setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            for(var block:BuildingUtils.getAbsoluteBlockData(hosts[i].getRelativeBlockData(h.getLevel()),h.getLevel(),origin,Rotation.NONE))
                h.getLevel().setBlock(block.getBlockPos(),block.getBlockState(),2);
            var p=(ProductionPlacement)BuildingUtils.getNewBuildingPlacement(hosts[i],h.getLevel(),origin,Rotation.NONE,name,false);
            BuildingServerEvents.getBuildings().add(p);placements.add(p);
        }
        var peer=new ResearchEconomyFixtures.Peer(h.getLevel(),name);
        h.runAtTickTime(20,()->{
            try {
                long sequence=1;
                for(int i=0;i<placements.size();i++) {
                    var p=placements.get(i);var item=items[i];
                    h.assertTrue(p.isBuilt,"real completed research host "+i);
                    peer.drain();peer.send(ResearchEconomyFixtures.query(p));
                    var token=ResearchEconomyFixtures.response(peer.drain(),p,account);
                    int f=account.food,w=account.wood,o=account.ore;
                    player.faction=i==0?Factions.MONSTERS:Factions.VILLAGERS;
                    peer.send(ResearchEconomyFixtures.request(p,token,sequence++,ResearchEconomyServerboundPacket.Action.START,item.id(),ResearchEconomyServerboundPacket.ZERO));
                    var denied=ResearchEconomyFixtures.response(peer.drain(),p,account);
                    h.assertTrue(denied.status.equals("forbidden")&&p.productionQueue.isEmpty()&&account.food==f&&account.wood==w&&account.ore==o,"normal wrong faction rejected without debit "+i);
                    player.faction=Factions.NONE;
                    var request=ResearchEconomyFixtures.request(p,token,sequence++,ResearchEconomyServerboundPacket.Action.START,item.id(),ResearchEconomyServerboundPacket.ZERO);
                    peer.send(request);var started=ResearchEconomyFixtures.response(peer.drain(),p,account);
                    var cost=item.quote(p);
                    h.assertTrue(started.status.equals("started")&&p.productionQueue.size()==1&&account.food==f-cost.food()&&account.wood==w-cost.wood()&&account.ore==o-cost.ore(),"sandbox research uses exact real paid cost "+i);
                    peer.send(request);ResearchEconomyFixtures.response(peer.drain(),p,account);
                    h.assertTrue(p.productionQueue.size()==1&&account.food==f-cost.food()&&account.wood==w-cost.wood()&&account.ore==o-cost.ore(),"replayed start neither charges nor enqueues twice "+i);
                    var receipt=p.productionQueue.get(0).receiptResearch.receiptId();
                    var cancel=ResearchEconomyFixtures.request(p,token,sequence++,ResearchEconomyServerboundPacket.Action.CANCEL,item.id(),receipt);
                    peer.send(cancel);var refunded=ResearchEconomyFixtures.response(peer.drain(),p,account);
                    h.assertTrue(refunded.status.equals("cancelled_refunded")&&p.productionQueue.isEmpty()&&account.food==f&&account.wood==w&&account.ore==o,"sandbox cancellation refunds original payer exactly "+i);
                    peer.send(cancel);ResearchEconomyFixtures.response(peer.drain(),p,account);
                    h.assertTrue(account.food==f&&account.wood==w&&account.ore==o,"repeated cancellation cannot duplicate refund "+i);
                }
                System.out.println("SANDBOX_CORE_RESEARCH_RUNTIME_PASS three_hosts real_packets exact_cost refunds replay normal_faction_denial");
                h.succeed();
            } finally {
                BuildingServerEvents.getBuildings().removeAll(placements);
                PlayerServerEvents.rtsPlayers.removeIf(p->p.name.equals(name));
                ResourcesServerEvents.resourcesList.removeIf(a->a.ownerName.equals(name));
                peer.close();
            }
        });
    }
}
