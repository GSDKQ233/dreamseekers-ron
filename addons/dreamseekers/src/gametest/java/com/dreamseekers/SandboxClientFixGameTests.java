package com.dreamseekers;

import com.dreamseekers.building.DreamBuildingSupport;
import com.dreamseekers.economy.*;
import com.dreamseekers.worldtree.DreamAnchorPlacement;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.faction.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.research.economy.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** Actual server account, paid production and research services. Rendered client coverage is separate. */
@GameTestHolder("dreamseekers_client_fixes")
@PrefixGameTestTemplate(false)
public final class SandboxClientFixGameTests {
    private static Resources account(String owner,Faction faction) {
        PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,faction));
        var r=new Resources(owner,5000,5000,5000);ResourcesServerEvents.resourcesList.add(r);return r;
    }
    private static void cleanup(String... owners) {
        var names=List.of(owners);
        for(var p:List.copyOf(BuildingServerEvents.getBuildings()))if(names.contains(p.ownerName)) {
            p.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(p);
        }
        PlayerServerEvents.rtsPlayers.removeIf(p->names.contains(p.name));
        ResourcesServerEvents.resourcesList.removeIf(r->names.contains(r.ownerName));
    }
    @GameTest(templateNamespace="dreamseekers_client_fixes",template="tree_economy_arena",timeoutTicks=50)
    public static void sandboxStarterPersistsWithoutReconnectRefillAndResetsPerEpoch(GameTestHelper h) {
        String owner="ClientFix_SandboxGrant";var r=account(owner,Factions.NONE);var server=h.getLevel().getServer();
        try {
            h.assertTrue(DreamSandboxEconomy.initialize(server,owner),"real native sandbox receives starter shadow");
            h.assertTrue(r.extra.get(DreamPassiveEconomy.SHADOW)==1000&&ResourceAddonData.valid(r.addonData),"starter is capped and persisted payload is legal compound data");
            var paid=ResourceTransaction.pay(server,r,r.accountEpoch,r.revision,"sandbox-test",new ResourceTransaction.Cost(0,0,0,Map.of(DreamPassiveEconomy.SHADOW,150)));
            h.assertTrue(paid.applied(),"shadow can be spent through actual receipt ledger");
            h.assertTrue(!DreamSandboxEconomy.initialize(server,owner)&&r.extra.get(DreamPassiveEconomy.SHADOW)==850,"ordinary tick/reconnect does not restore spent test funds");
            var tag=new CompoundTag();r.writeExtended(tag);var restored=new Resources(owner,r.food,r.wood,r.ore);restored.readExtended(tag);
            ResourcesServerEvents.resourcesList.remove(r);ResourcesServerEvents.resourcesList.add(restored);r=restored;
            h.assertTrue(!DreamSandboxEconomy.initialize(server,owner)&&r.extra.get(DreamPassiveEconomy.SHADOW)==850,"actual account codec reload preserves once-only grant");
            h.assertTrue(DreamSandboxEconomy.refill(server,owner)&&r.extra.get(DreamPassiveEconomy.SHADOW)==1000,"explicit sandbox refill works");
            h.assertTrue(ResourceTransaction.refund(server,r,r.accountEpoch,r.revision,paid.receiptId(),10000).applied()&&r.extra.get(DreamPassiveEconomy.SHADOW)==1150,"refund above cap survives refill");
            h.assertTrue(DreamSandboxEconomy.refill(server,owner)&&r.extra.get(DreamPassiveEconomy.SHADOW)==1150,"refill never clamps legitimate overflow");
            var epoch=r.accountEpoch;r.resetExtendedAccount();
            h.assertTrue(!epoch.equals(r.accountEpoch)&&DreamSandboxEconomy.initialize(server,owner)&&r.extra.get(DreamPassiveEconomy.SHADOW)==1000,"fresh reset epoch gets new sandbox funds");
            h.succeed();
        } finally {cleanup(owner);}
    }
    @GameTest(templateNamespace="dreamseekers_client_fixes",template="tree_economy_arena",timeoutTicks=50)
    public static void ordinaryFactionsUnknownAccountsAndFuturePayloadCannotReceiveSandboxFunds(GameTestHelper h) {
        String dream="ClientFix_RealDream",villager="ClientFix_RealVillager",future="ClientFix_Future";
        var d=account(dream,DreamseekersMod.DREAMSEEKERS);var v=account(villager,Factions.VILLAGERS);var f=account(future,Factions.NONE);var server=h.getLevel().getServer();
        try {
            for(String name:List.of(dream,villager,"ClientFix_Absent")) {
                h.assertTrue(!DreamSandboxEconomy.initialize(server,name)&&!DreamSandboxEconomy.refill(server,name),"normal/absent owner cannot get sandbox funds: "+name);
            }
            h.assertTrue(d.extra.isEmpty()&&v.extra.isEmpty()&&d.revision==0&&v.revision==0,"normal accounts untouched");
            h.assertTrue(DreamFactionAccess.allows(DreamseekersMod.DREAMSEEKERS)&&DreamFactionAccess.allows(Factions.NONE)&&!DreamFactionAccess.allows(Factions.VILLAGERS)&&!DreamFactionAccess.allows((Faction)null),"content eligibility admits only Dream or registered sandbox faction");
            var payload=new CompoundTag();payload.putInt("schema",99);payload.putString("future","preserve");f.addonData.put(DreamSandboxEconomy.INITIALIZED,payload);
            var before=f.addonData.copy();h.assertTrue(!DreamSandboxEconomy.initialize(server,future)&&f.extra.isEmpty()&&f.addonData.equals(before),"unknown initialization payload never overwritten or granted");
            f.addonData.putString("broken","invalid");
            h.assertTrue(!DreamSandboxEconomy.refill(server,future)&&f.extra.isEmpty(),"malformed attachment fails closed");
            h.succeed();
        } finally {cleanup(dream,villager,future);}
    }
    @GameTest(templateNamespace="dreamseekers_client_fixes",template="tree_economy_arena",timeoutTicks=80)
    public static void sandboxCanBuyUnitsAndResearchWithoutForeignOwnerBypass(GameTestHelper h) {
        String owner="ClientFix_Operations",other="ClientFix_Foreign";var r=account(owner,Factions.NONE);account(other,Factions.VILLAGERS);
        try(var own=new PassiveIncomePeer(h.getLevel(),owner);var foreign=new PassiveIncomePeer(h.getLevel(),other)) {
            DreamSandboxEconomy.initialize(h.getLevel().getServer(),owner);
            var origin=BuildingAssetGameTests.outdoorPos(h,4,1,4);
            for(int x=-2;x<14;x++)for(int z=-2;z<12;z++) {
                h.getLevel().setBlock(origin.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
                for(int y=0;y<20;y++)h.getLevel().setBlock(origin.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            for(var b:BuildingUtils.getAbsoluteBlockData(DreamseekersMod.DREAM_ANCHOR.getRelativeBlockData(h.getLevel()),h.getLevel(),origin,Rotation.NONE))h.getLevel().setBlock(b.getBlockPos(),b.getBlockState(),2);
            var tree=(DreamAnchorPlacement)BuildingUtils.getNewBuildingPlacement(DreamseekersMod.DREAM_ANCHOR,h.getLevel(),origin,Rotation.NONE,owner,false);
            tree.isBuilt=true;BuildingServerEvents.getBuildings().add(tree);
            h.assertTrue(DreamBuildingSupport.availability(DreamseekersMod.DREAM_ANCHOR,owner)==null,"server building gate accepts sandbox");
            h.assertTrue(DreamseekersMod.WORKER_PRODUCTION.getProduceErrorMsg(tree)==null,"sandbox native NONE is legal production owner");
            h.assertTrue(DreamEconomyResearch.eligibleHost(tree,false),"sandbox conversion research host accepts native account");
            h.assertTrue(tree.startProductionItem(DreamseekersMod.WORKER_PRODUCTION,0),"actual paid worker queue starts");
            h.assertTrue(r.food<5000&&!r.paidReceipts.isEmpty(),"worker is paid, not authority bypass/free spawn");
            tree.cancelProductionItem(DreamseekersMod.WORKER_PRODUCTION,true);
            h.assertTrue(r.food==5000&&r.paidReceipts.isEmpty(),"cancel refunds actual original worker payment");
            var id=ResearchPlacementIdentity.serverIdentity(tree);
            String bad=ReceiptResearchService.start(foreign.actor,tree,DreamEconomyResearch.CONVERSION,id,tree.getCaptureRevision(),r.accountEpoch,r.revision);
            h.assertTrue(bad.equals("forbidden")&&r.extra.get(DreamPassiveEconomy.SHADOW)==1000,"sandbox does not permit foreign actor purchase");
            String started=ReceiptResearchService.start(own.actor,tree,DreamEconomyResearch.CONVERSION,id,tree.getCaptureRevision(),r.accountEpoch,r.revision);
            h.assertTrue(started.equals("started")&&r.extra.get(DreamPassiveEconomy.SHADOW)==970,"actual paid sandbox research starts and debits shadow");
            var active=tree.productionQueue.get(0);
            h.assertTrue(ReceiptResearchService.detachAndRefund(tree,active).equals("cancelled_refunded")&&r.extra.get(DreamPassiveEconomy.SHADOW)==1000,"actual research cancellation refunds shadow once");
            h.succeed();
        } finally {cleanup(owner,other);}
    }
}
