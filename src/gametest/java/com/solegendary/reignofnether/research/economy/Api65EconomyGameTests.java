package com.solegendary.reignofnether.research.economy;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.*;
import com.solegendary.reignofnether.building.addon.ItemShopAddon;
import com.solegendary.reignofnether.building.buildings.placements.*;
import com.solegendary.reignofnether.building.production.*;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.hero.HeroServerEvents;
import com.solegendary.reignofnether.items.*;
import com.solegendary.reignofnether.player.*;
import com.solegendary.reignofnether.registrars.*;
import com.solegendary.reignofnether.research.ResearchServerEvents;
import com.solegendary.reignofnether.resources.*;
import com.solegendary.reignofnether.unit.UnitServerEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.units.villagers.RoyalGuardUnit;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.*;
import net.minecraftforge.gametest.*;
import java.util.*;

/** These tests use only API64 production APIs: overlaying this source on API64 must compile.
 * Real registered recipes and codec/handler/connected embedded peer, not a GUI/client handshake. */
@GameTestHolder("api65_economy") @PrefixGameTestTemplate(false)
public final class Api65EconomyGameTests {
    private static final String TEMPLATE="ore_mine_test_arena";
    static final class F implements AutoCloseable {
        final GameTestHelper h; final String owner; final Resources bank; final ResearchEconomyFixtures.Peer peer;
        final List<BuildingPlacement> buildings=new ArrayList<>(); final List<Entity> entities=new ArrayList<>();
        int slot;
        F(GameTestHelper h,String suffix)throws Exception {
            this.h=h;owner="Api65E_"+suffix;
            PlayerServerEvents.rtsPlayers.add(RTSPlayer.getNewBot(owner,Factions.VILLAGERS));
            bank=new Resources(owner,100000,100000,100000,100000);ResourcesServerEvents.resourcesList.add(bank);
            peer=new ResearchEconomyFixtures.Peer(h.getLevel(),owner);
        }
        ProductionPlacement install(Building type) { return install(type,Rotation.NONE); }
        ProductionPlacement install(Building type,Rotation rotation) {
            ServerLevel l=h.getLevel();BlockPos p=h.absolutePos(new BlockPos(40+slot++*38,1,40));
            for(int x=-2;x<30;x++)for(int z=-2;z<30;z++){
                l.setBlock(p.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
                for(int y=1;y<22;y++)l.setBlock(p.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            for(BuildingBlock b:BuildingUtils.getAbsoluteBlockData(type.getRelativeBlockData(l),l,p,rotation))
                l.setBlock(b.getBlockPos(),b.getBlockState(),2);
            ProductionPlacement result=(ProductionPlacement)BuildingUtils.getNewBuildingPlacement(type,l,p,rotation,owner,false);
            BuildingServerEvents.getBuildings().add(result);buildings.add(result);result.onBuilt();return result;
        }
        RoyalGuardUnit hero(ItemShopPlacement shop) {
            RoyalGuardUnit hero=EntityRegistrar.ROYAL_GUARD_UNIT.get().create(h.getLevel());
            hero.setOwnerName(owner);hero.setNoAi(true);hero.setCanPickUpLoot(true);
            BlockPos p=shop.getClosestGroundPos(shop.centrePos,1);
            hero.moveTo(p.getX()+.5,p.getY()+1,p.getZ()+.5);
            h.assertTrue(h.getLevel().addFreshEntity(hero),"genuine hero entered server level");entities.add(hero);
            h.assertTrue(shop.canServeUnit(hero),"fixture hero in allied shop service range");return hero;
        }
        void production(ProductionPlacement host,ProductionItem recipe) {
            var packet=new BuildingProductionServerboundPacket(BuildingAction.START_PRODUCTION,
                    ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(recipe).toString(),host.originPos);
            FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());
            try {packet.encode(b);new BuildingProductionServerboundPacket(b).handle(()->peer.context);}
            finally {b.release();}peer.channel.runPendingTasks();
        }
        void action(ItemAction action,RoyalGuardUnit hero,UUID uuid,String item,int target,BlockPos pos) {
            var packet=new ItemServerboundPacket(action,hero.getId(),uuid,item,target,pos,-1,-1);
            FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.buffer());
            try {packet.encode(b);new ItemServerboundPacket(b).handle(()->peer.context);}
            finally {b.release();}peer.channel.runPendingTasks();
        }
        void sell(RoyalGuardUnit hero,UUID uuid,ItemShopPlacement shop){action(ItemAction.SELL,hero,uuid,null,-1,shop.originPos);hero.getItemGoal().tick();}
        void buy(RoyalGuardUnit hero,String item,ItemShopPlacement shop){action(ItemAction.BUY,hero,null,item,-1,shop.originPos);}
        long owned(Class<?> type){return UnitServerEvents.getAllUnits().stream().filter(e->e.isAlive()&&type.isInstance(e)&&e instanceof Unit u&&owner.equals(u.getOwnerName())).count();}
        @Override public void close(){
            // Remove fixture RTS identity before discarded entities can invoke ordinary defeat handling.
            PlayerServerEvents.rtsPlayers.removeIf(p->owner.equals(p.name));ResourcesServerEvents.resourcesList.removeIf(r->owner.equals(r.ownerName));
            ResearchServerEvents.removeAllResearchFor(owner);
            for(Entity e:entities)if(!e.isRemoved())e.discard();
            for(var e:List.copyOf(UnitServerEvents.getAllUnits()))if(e instanceof Unit u&&owner.equals(u.getOwnerName()))e.discard();
            for(var b:buildings){b.onRemoved(BuildingPlacement.RemovalReason.RELOAD);BuildingServerEvents.getBuildings().remove(b);}
            HeroServerEvents.fallenHeroes.removeIf(x->owner.equals(x.ownerName));peer.close();
        }
    }
    @GameTest(templateNamespace="api65_economy",template=TEMPLATE,batch="api65_econ_bound",timeoutTicks=120)
    public static void boundSaleCannotCreditAndOrdinarySaleCreditsOnce(GameTestHelper h)throws Exception {
        try(F f=new F(h,"bound")){
            ItemShopPlacement shop=(ItemShopPlacement)f.install(Buildings.VILLAGER_MARKET);RoyalGuardUnit hero=f.hero(shop);UnitInventory inv=(UnitInventory)hero;
            ItemStack bound=new ItemStack(Items.NETHERITE_CHESTPLATE);bound.enchant(Enchantments.BINDING_CURSE,1);
            ItemEntity ground=new ItemEntity(h.getLevel(),hero.getX()+.5,hero.getY(),hero.getZ(),bound);h.assertTrue(h.getLevel().addFreshEntity(ground),"bound ground item published");f.entities.add(ground);
            f.action(ItemAction.PICKUP,hero,null,null,ground.getId(),null);hero.getItemGoal().tick();
            ItemStack held=inv.get(0);h.assertTrue(ItemUtil.getUnitItem(held)==UnitItems.MERCHANT_CHESTPLATE&&!ground.isAlive(),"real pickup retains recognized bound merchant chestplate");
            UUID id=ItemUtil.getUUID(held);int before=f.bank.emerald;
            f.sell(hero,id,shop);f.sell(hero,id,shop);
            h.assertTrue(f.bank.emerald==before&&inv.get(id)!=null,"failed bound deletion must never credit, including replay");
            inv.set(0,UnitItems.MERCHANT_CHESTPLATE.getNewItemStack());id=ItemUtil.getUUID(inv.get(0));f.sell(hero,id,shop);f.sell(hero,id,shop);
            h.assertTrue(inv.get(id)==null&&f.bank.emerald==before+50,"ordinary sale removes once and credits exactly once");
        }h.succeed();
    }
    @GameTest(templateNamespace="api65_economy",template=TEMPLATE,batch="api65_econ_beacon",timeoutTicks=120)
    public static void beaconCannotSkipAndAcceptsEachNextStage(GameTestHelper h)throws Exception {
        try(F f=new F(h,"beacon")){
            BeaconPlacement beacon=(BeaconPlacement)f.install(Buildings.BEACON);var before=ResearchEconomyFixtures.snapshot(f.bank);
            f.production(beacon,ProductionItems.RESEARCH_BEACON_LEVEL_5);
            h.assertTrue(beacon.productionQueue.isEmpty()&&ResearchEconomyFixtures.snapshot(f.bank).equals(before)&&beacon.getUpgradeLevel()==0,"real C2S cannot skip Beacon stages or charge");
            List<ProductionItem> stages=List.of(ProductionItems.RESEARCH_BEACON_LEVEL_1,ProductionItems.RESEARCH_BEACON_LEVEL_2,ProductionItems.RESEARCH_BEACON_LEVEL_3,ProductionItems.RESEARCH_BEACON_LEVEL_4,ProductionItems.RESEARCH_BEACON_LEVEL_5);
            for(int i=0;i<stages.size();i++){
                f.production(beacon,stages.get(i));h.assertTrue(beacon.productionQueue.size()==1,"legitimate next Beacon stage queued");
                beacon.productionQueue.get(0).ticksLeft=0;beacon.tick(h.getLevel());
                h.assertTrue(beacon.productionQueue.isEmpty()&&beacon.getUpgradeLevel()==i+1,"legitimate Beacon stage completed");
            }
            before=ResearchEconomyFixtures.snapshot(f.bank);f.production(beacon,ProductionItems.RESEARCH_BEACON_LEVEL_1);
            h.assertTrue(beacon.productionQueue.isEmpty()&&ResearchEconomyFixtures.snapshot(f.bank).equals(before),"completed Beacon cannot regress to lower tier");
        }h.succeed();
    }
    @GameTest(templateNamespace="api65_economy",template=TEMPLATE,batch="api65_econ_tech",timeoutTicks=120)
    public static void nativeTechPrerequisitesGateRealOwnerPackets(GameTestHelper h)throws Exception {
        try(F f=new F(h,"tech")){
            f.install(Buildings.TOWN_CENTRE);ProductionPlacement tower=f.install(Buildings.ARCANE_TOWER),grave=f.install(Buildings.GRAVEYARD);
            var before=ResearchEconomyFixtures.snapshot(f.bank);f.production(tower,ProductionItems.WINDCALLER);f.production(grave,ProductionItems.STRAY);
            h.assertTrue(tower.productionQueue.isEmpty()&&grave.productionQueue.isEmpty()&&ResearchEconomyFixtures.snapshot(f.bank).equals(before),"registered native advanced recipes require actual owner tech");
            f.install(Buildings.LIBRARY);ResearchServerEvents.addResearch(f.owner,ProductionItems.RESEARCH_STRAYS);
            f.production(tower,ProductionItems.WINDCALLER);f.production(grave,ProductionItems.STRAY);
            h.assertTrue(tower.productionQueue.size()==1&&grave.productionQueue.size()==1,"normal prerequisites permit corresponding native recipes");
        }h.succeed();
    }
    @GameTest(templateNamespace="api65_economy",template=TEMPLATE,batch="api65_econ_recheck",timeoutTicks=120)
    public static void nativeCompletionRechecksLostPrerequisiteWithoutAnotherPayment(GameTestHelper h)throws Exception {
        try(F f=new F(h,"recheck")){
            f.install(Buildings.TOWN_CENTRE);ProductionPlacement tower=f.install(Buildings.ARCANE_TOWER),library=f.install(Buildings.LIBRARY);
            f.production(tower,ProductionItems.WINDCALLER);h.assertTrue(tower.productionQueue.size()==1,"valid start paid once");ActiveProduction paid=tower.productionQueue.get(0);int food=f.bank.food;
            BuildingServerEvents.getBuildings().remove(library);paid.ticksLeft=0;tower.tick(h.getLevel());
            h.assertTrue(tower.productionQueue.contains(paid)&&f.owned(com.solegendary.reignofnether.unit.units.villagers.WindcallerUnit.class)==0&&f.bank.food==food,"lost prerequisite blocks birth, retains paid queue without charge/refund");
            BuildingServerEvents.getBuildings().add(library);tower.tick(h.getLevel());
            h.assertTrue(tower.productionQueue.isEmpty()&&f.owned(com.solegendary.reignofnether.unit.units.villagers.WindcallerUnit.class)==1&&f.bank.food==food,"restored prerequisite completes exactly one paid body");
        }h.succeed();
    }
    @GameTest(templateNamespace="api65_economy",template=TEMPLATE,batch="api65_econ_price",timeoutTicks=120)
    public static void shopUsesConfiguredPriceForPaymentAndResale(GameTestHelper h)throws Exception {
        try(F f=new F(h,"price")){
            ItemShopPlacement shop=(ItemShopPlacement)f.install(Buildings.VILLAGER_MARKET);RoyalGuardUnit hero=f.hero(shop);UnitInventory inv=(UnitInventory)hero;
            StockedShopItem stock=new StockedShopItem(UnitItems.BROADSWORD,1000,3,3,1200);shop.setStockedItems(new ArrayList<>(List.of(stock)));int before=f.bank.emerald;
            f.buy(hero,UnitItems.BROADSWORD.descId,shop);h.assertTrue(f.bank.emerald==before-1000&&stock.stock==2,"configured shop price is the actual debit");
            f.sell(hero,ItemUtil.getUUID(inv.get(0)),shop);h.assertTrue(f.bank.emerald==before-500&&inv.isEmpty(),"resale is half same configured paid price");
            f.bank.emerald=999;f.buy(hero,UnitItems.BROADSWORD.descId,shop);h.assertTrue(f.bank.emerald==999&&stock.stock==2&&inv.isEmpty(),"insufficient configured-price funds cause zero mutation");
        }h.succeed();
    }
    @GameTest(templateNamespace="api65_economy",template=TEMPLATE,batch="api65_econ_capacity",timeoutTicks=120)
    public static void groupedShopPurchaseIsAllOrNothing(GameTestHelper h)throws Exception {
        try(F f=new F(h,"capacity")){
            ItemShopPlacement shop=(ItemShopPlacement)f.install(Buildings.VILLAGER_MARKET);RoyalGuardUnit hero=f.hero(shop);UnitInventory inv=(UnitInventory)hero;
            StockedShopItem stock=new StockedShopItem(UnitItems.ENDER_PEARL,2,1200);shop.setStockedItems(new ArrayList<>(List.of(stock)));
            ItemStack pearls=UnitItems.ENDER_PEARL.getNewItemStack();pearls.setCount(8);inv.set(0,pearls);for(int i=1;i<UnitInventory.MAX_INVENTORY_SIZE;i++)inv.set(i,UnitItems.BROADSWORD.getNewItemStack());
            int before=f.bank.emerald;f.buy(hero,UnitItems.ENDER_PEARL.descId,shop);
            h.assertTrue(f.bank.emerald==before&&stock.stock==2&&inv.get(0).getCount()==8,"room for only one of three cannot consume money, stock or remainder");
            inv.get(0).setCount(6);f.buy(hero,UnitItems.ENDER_PEARL.descId,shop);
            h.assertTrue(f.bank.emerald==before-UnitItems.ENDER_PEARL.buyCost&&stock.stock==1&&inv.get(0).getCount()==9,"exact full-pack capacity buys exactly three");
        }h.succeed();
    }
    @GameTest(templateNamespace="api65_economy",template=TEMPLATE,batch="api65_econ_attributes",timeoutTicks=120)
    public static void remainingNonStackableItemTakesOverAttribute(GameTestHelper h)throws Exception {
        try(F f=new F(h,"attributes")){
            ItemShopPlacement shop=(ItemShopPlacement)f.install(Buildings.VILLAGER_MARKET);RoyalGuardUnit hero=f.hero(shop);UnitInventory inv=(UnitInventory)hero;
            double base=hero.getAttributeValue(Attributes.MOVEMENT_SPEED);
            inv.set(0,UnitItems.BOOTS_OF_SWIFTNESS.getNewItemStack());double one=hero.getAttributeValue(Attributes.MOVEMENT_SPEED);
            inv.set(1,UnitItems.BOOTS_OF_SWIFTNESS.getNewItemStack());h.assertTrue(one>base&&Math.abs(hero.getAttributeValue(Attributes.MOVEMENT_SPEED)-one)<1e-8,"duplicate boots never stack");
            inv.deleteItem(ItemUtil.getUUID(inv.get(0)));h.assertTrue(Math.abs(hero.getAttributeValue(Attributes.MOVEMENT_SPEED)-one)<1e-8,"remaining boot takes over removed active modifier");
            inv.deleteItem(ItemUtil.getUUID(inv.get(1)));h.assertTrue(Math.abs(hero.getAttributeValue(Attributes.MOVEMENT_SPEED)-base)<1e-8,"removing last boot restores base");
        }h.succeed();
    }
    @GameTest(templateNamespace="api65_economy",template=TEMPLATE,batch="api65_econ_infinite",timeoutTicks=120)
    public static void declaredInfiniteShopNeverExhausts(GameTestHelper h)throws Exception {
        try(F f=new F(h,"infinite")){
            ItemShopPlacement shop=(ItemShopPlacement)f.install(Buildings.VILLAGER_MARKET);RoyalGuardUnit hero=f.hero(shop);UnitInventory inv=(UnitInventory)hero;
            StockedShopItem stock=new StockedShopItem(UnitItems.BROADSWORD,UnitItems.BROADSWORD.buyCost,-1,0,1200);shop.setStockedItems(new ArrayList<>(List.of(stock)));int before=f.bank.emerald;
            for(int i=0;i<3;i++){f.buy(hero,UnitItems.BROADSWORD.descId,shop);h.assertTrue(!inv.isEmpty(),"negative maxStock declares an available infinite item");inv.deleteItem(ItemUtil.getUUID(inv.get(0)));}
            h.assertTrue(f.bank.emerald==before-3*UnitItems.BROADSWORD.buyCost,"infinite stock still pays once per item");
        }h.succeed();
    }
    @GameTest(templateNamespace="api65_economy",template=TEMPLATE,batch="api65_econ_badbuy",timeoutTicks=120)
    public static void malformedAndUnstockedBuyAreNoOp(GameTestHelper h)throws Exception {
        try(F f=new F(h,"badbuy")){
            ItemShopPlacement shop=(ItemShopPlacement)f.install(Buildings.VILLAGER_MARKET);RoyalGuardUnit hero=f.hero(shop);UnitInventory inv=(UnitInventory)hero;
            StockedShopItem stock=new StockedShopItem(UnitItems.BROADSWORD,2,1200);shop.setStockedItems(new ArrayList<>(List.of(stock)));int before=f.bank.emerald;
            f.buy(hero,null,shop);f.buy(hero,"not_a_registered_item",shop);f.buy(hero,UnitItems.ENDER_PEARL.descId,shop);
            h.assertTrue(f.bank.emerald==before&&stock.stock==2&&inv.isEmpty(),"null, unknown and unstocked item never throw or mutate");
            f.buy(hero,UnitItems.BROADSWORD.descId,shop);h.assertTrue(f.bank.emerald==before-UnitItems.BROADSWORD.buyCost&&stock.stock==1&&!inv.isEmpty(),"valid registered stocked item still buys");
        }h.succeed();
    }
    // This historical geometry test is also overlaid on API64. On repair builds it must
    // supply the real observed ID; only a genuinely pre-identity API uses the old shape.
    private static BuildingProductionServerboundPacket rotatedFrontRequest(ProductionPlacement host) throws Exception {
        String name = ReignOfNetherRegistries.PRODUCTION_ITEM.getKey(ProductionItems.VILLAGER).toString();
        java.lang.reflect.Field identity;
        try { identity = ActiveProduction.class.getField("queueEntryId"); }
        catch (NoSuchFieldException preIdentityApi) {
            return new BuildingProductionServerboundPacket(BuildingAction.CANCEL_PRODUCTION, name, host.minCorner);
        }
        return BuildingProductionServerboundPacket.class
                .getConstructor(BuildingAction.class, String.class, BlockPos.class, UUID.class)
                .newInstance(BuildingAction.CANCEL_PRODUCTION, name, host.minCorner, identity.get(host.productionQueue.get(0)));
    }

    @GameTest(templateNamespace="api65_economy",template=TEMPLATE,batch="api65_econ_rotated_cancel",timeoutTicks=120)
    public static void rotatedMinCornerCancellationStillRepliesToOwner(GameTestHelper h)throws Exception {
        try(F f=new F(h,"rotcancel")) {
            boolean distinct=false;
            for(Rotation rotation:List.of(Rotation.CLOCKWISE_90,Rotation.COUNTERCLOCKWISE_90)) {
                ProductionPlacement host=f.install(Buildings.TOWN_CENTRE,rotation);
                distinct|=!host.originPos.equals(host.minCorner);
                f.production(host,ProductionItems.VILLAGER);
                h.assertTrue(host.productionQueue.size()==1,"real rotated native queue started");f.peer.drain();
                var request=rotatedFrontRequest(host);
                FriendlyByteBuf encoded=new FriendlyByteBuf(Unpooled.buffer());
                try {request.encode(encoded);new BuildingProductionServerboundPacket(encoded).handle(()->f.peer.context);}
                finally {encoded.release();}
                h.assertTrue(host.productionQueue.isEmpty(),"legacy minCorner resolves and cancels actual server queue");
                var sample=new BuildingProductionClientboundPacket(BuildingAction.CANCEL_PRODUCTION,"",host.minCorner);
                int discriminator=ResearchEconomyFixtures.discriminator(sample);int replies=0;
                for(Object output:f.peer.drain()) if(output instanceof net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket payload) {
                    FriendlyByteBuf data=new FriendlyByteBuf(payload.getData().copy());
                    try {
                        if(data.readVarInt()==discriminator) {
                            var reply=new BuildingProductionClientboundPacket(data);
                            if(reply.action==BuildingAction.CANCEL_PRODUCTION&&reply.buildingPos.equals(host.minCorner))replies++;
                        }
                    } finally {data.release();}
                }
                h.assertTrue(replies==1,"exact owner receives one cancellation queue packet for valid rotated minCorner");
            }
            h.assertTrue(distinct,"fixture actually exercises minCorner different from origin");
        }h.succeed();
    }

}
