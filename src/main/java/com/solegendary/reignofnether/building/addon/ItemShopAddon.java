package com.solegendary.reignofnether.building.addon;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.alliance.AlliancesServerEvents;
import com.solegendary.reignofnether.building.buildings.placements.ItemShopPlacement;
import com.solegendary.reignofnether.building.data.DataType;
import com.solegendary.reignofnether.hud.HudClientboundPacket;
import com.solegendary.reignofnether.items.*;
import com.solegendary.reignofnether.resources.ResourceName;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.sounds.SoundAction;
import com.solegendary.reignofnether.sounds.SoundClientboundPacket;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;

import static com.solegendary.reignofnether.util.MiscUtil.fcs;

public interface ItemShopAddon extends BuildingAddon {

    DataType<ArrayList<StockedShopItem>> STOCKED_ITEMS = DataType.createRegistered(
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "stocked_items"),
            (nbt, server) -> { // decode
                ArrayList<StockedShopItem> stockedItems = new ArrayList<>();
                ListTag ltag = nbt.getList("list", Tag.TAG_COMPOUND);
                for (int i = 0; i < ltag.size(); i++) {
                    CompoundTag tag = ltag.getCompound(i);
                    UnitItem unitItem = ItemUtil.getUnitItem(tag.getString("descId"));
                    if (unitItem != null) {
                        StockedShopItem stock = new StockedShopItem(
                                unitItem,
                                tag.getInt("buyCost"),
                                tag.getInt("maxStock"),
                                tag.getInt("stock"),
                                tag.getInt("maxRestockTicks"),
                                tag.getInt("restockTicks")
                        );
                        stockedItems.add(stock);
                    }
                }
                return stockedItems;
            },
            stockedItems -> { // encode
                CompoundTag tag = new CompoundTag();
                ListTag ltag = new ListTag();
                for (StockedShopItem stockedShopItem : stockedItems) {
                    CompoundTag tag2 = new CompoundTag();
                    tag2.putString("descId", stockedShopItem.item.descId);
                    tag2.putInt("buyCost", stockedShopItem.getBuyCost());
                    tag2.putInt("maxStock", stockedShopItem.maxStock);
                    tag2.putInt("stock", stockedShopItem.stock);
                    tag2.putInt("maxRestockTicks", stockedShopItem.maxRestockTicks);
                    tag2.putInt("restockTicks", stockedShopItem.getTicksToNextRestock());
                    ltag.add(tag2);
                }
                tag.put("list", ltag);
                return tag;
            },
            ArrayList::new
    );

    default void buyItem(Unit unit, ItemShopPlacement bpl, UnitItem item) {
        // This service is also called directly: a valid C2S envelope is not its only safety boundary.
        if (unit == null || bpl == null || item == null || !(unit instanceof LivingEntity living)
                || !living.isAlive() || living.level().isClientSide() || living.level() != bpl.getLevel()
                || living.getServer() == null || !living.getServer().isSameThread()
                || !com.solegendary.reignofnether.building.BuildingServerEvents.getBuildings().contains(bpl)
                || bpl.isDestroyedServerside) return;
        ArrayList<StockedShopItem> shopStocks = bpl.getDataStorage().getData(ItemShopAddon.STOCKED_ITEMS);
        if (!bpl.isBuilt || shopStocks == null || !bpl.canServeUnit(unit)) return;
        if (!(unit instanceof UnitInventory inv && inv.canPickupUnitItems())) return;

        for (StockedShopItem shopStock : shopStocks) {
            if (shopStock == null || shopStock.item == null || !item.descId.equals(shopStock.item.descId)) continue;
            // The stock's canonical item and configured price define one purchase; a caller's
            // UnitItem object is merely a lookup request, never authority for a cheaper price.
            UnitItem offered = shopStock.item;
            int price = shopStock.buyCost;
            if (price < 0) return;
            if (!shopStock.isAvailable()) {
                HudClientboundPacket.showTempMessageI18n(unit.getOwnerName(), Component.translatable("itemshop.reignofnether.error.out_of_stock", shopStock.getTicksToNextRestock() / 20).getString());
                return;
            }
            ItemStack purchase = offered.getNewItemStack();
            if (!inv.canFullyAdd(purchase)) {
                HudClientboundPacket.showTempMessageI18n(unit.getOwnerName(), "itemshop.reignofnether.error.full_inventory");
                return;
            }
            Resources account = com.solegendary.reignofnether.resources.ExtendedResourcesSync.account(unit.getOwnerName());
            if (account == null || account.emerald < price || account.emerald < 0) {
                HudClientboundPacket.showTempMessageI18n(unit.getOwnerName(), "itemshop.reignofnether.error.cant_afford");
                return;
            }
            if (price != offered.buyCost) purchase.getOrCreateTag().putInt("sellValue", price / 2);
            // canFullyAdd and tryAdding run in the same main-thread call with no external callback
            // in between. Do not materialize/discard the remainder as a fake pickup animation.
            if (inv.tryAdding(purchase) && purchase.isEmpty()) {
                shopStock.recordPurchase();
                ResourcesServerEvents.addSubtractResources(Resources.emeralds(unit.getOwnerName(), -price));
                SoundClientboundPacket.playSoundAtPos(SoundAction.SELL_ITEM, living.getOnPos());
                ItemShopClientboundPacket.syncItemShopStock(bpl.originPos, bpl.getStockedItems());
            } else {
                HudClientboundPacket.showTempMessageI18n(unit.getOwnerName(), "itemshop.reignofnether.error.failed_other");
            }
            return;
        }
        // Unknown/unstocked IDs are a no-op, including valid products sold by some other shop.
    }
}
