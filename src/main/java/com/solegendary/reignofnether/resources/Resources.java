package com.solegendary.reignofnether.resources;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import java.util.List;

// RTS resources used for buildings, units, etc.
// usually tied to a ServerPlayer's NBT data so it is retained between relogs and server restarts
// but with a clientside copy too for the HUD
public class Resources {

    public String ownerName;

    // New economic actions use these fields; legacy resource deltas remain compatible.
    public final Map<ResourceLocation, Integer> extra = new LinkedHashMap<>();
    public UUID accountEpoch = UUID.randomUUID();
    public long revision;
    public final Map<String, Long> conversionRemainders = new LinkedHashMap<>();
    public final Map<UUID, ResourcePaidReceipt> paidReceipts = new LinkedHashMap<>();
    Tag opaqueExtendedState;
    public CompoundTag addonData=new CompoundTag();
    Tag opaqueAddonData;
    public boolean addonDataSupported(){return opaqueAddonData==null;}

    public boolean extendedStateSupported() { return opaqueExtendedState == null; }
    public void readExtended(CompoundTag tag) { ResourceAccountCodec.read(this,tag); }
    public void writeExtended(CompoundTag tag) { ResourceAccountCodec.write(this,tag); }
    /** New match/reset only. Reconnect and ordinary disk load must not call this. */
    public void resetExtendedAccount() {
        extra.clear(); conversionRemainders.clear(); paidReceipts.clear();
        addonData=new CompoundTag();opaqueAddonData=null;
        accountEpoch = UUID.randomUUID(); revision = 0; opaqueExtendedState = null;
    }
    /** Copy into a save projection without mutating the running account or sharing mutable maps. */
    public void copyExtendedFrom(Resources source) {
        if (source == this) return;
        extra.clear(); extra.putAll(source.extra);
        conversionRemainders.clear(); conversionRemainders.putAll(source.conversionRemainders);
        paidReceipts.clear(); paidReceipts.putAll(source.paidReceipts);
        addonData=source.addonData.copy();opaqueAddonData=source.opaqueAddonData==null?null:source.opaqueAddonData.copy();
        accountEpoch = source.accountEpoch; revision = source.revision;
        opaqueExtendedState = source.opaqueExtendedState == null ? null : source.opaqueExtendedState.copy();
    }


    // present amounts of each resource
    public int food;
    public int wood;
    public int ore;
    public int emerald;

    // balances of each resource to add (or remove if < 0)
    // shown as a +-X amount beside the resource on the HUD that is ticked in over time
    public int foodToAdd = 0;
    public int woodToAdd = 0;
    public int oreToAdd = 0;
    public int emeraldToAdd = 0;

    public Resources(String ownerName, int food, int wood, int ore) {
        this.ownerName = ownerName;
        this.food = food;
        this.wood = wood;
        this.ore = ore;
        this.emerald = 0;
    }

    public Resources(String ownerName, int food, int wood, int ore, int emerald) {
        this.ownerName = ownerName;
        this.food = food;
        this.wood = wood;
        this.ore = ore;
        this.emerald = emerald;
    }

    public static Resources emeralds(String ownerName, int emerald) {
        return new Resources(ownerName, 0,0,0, emerald);
    }

    public int getTotalValue() {
        return this.food + this.foodToAdd +
                this.wood + this.woodToAdd +
                this.ore + this.oreToAdd +
                this.emerald + this.emeraldToAdd;
    }

    // usually used clientside
    public void changeOverTime(int food, int wood, int ore, int emerald) {
        this.foodToAdd += food;
        this.woodToAdd += wood;
        this.oreToAdd += ore;
        this.emeraldToAdd += emerald;
    }

    // usually used serverside
    public void changeInstantly(int food, int wood, int ore, int emerald) {
        this.food += food;
        this.wood += wood;
        this.ore += ore;
        this.emerald += emerald;
    }

    // drain ToAdd fields into totals so that we get the appearance of change over time on the HUD
    public void tick() {
        this.food += getDrainPerTick(this.foodToAdd);
        this.foodToAdd -= getDrainPerTick(this.foodToAdd);
        this.wood += getDrainPerTick(this.woodToAdd);
        this.woodToAdd -= getDrainPerTick(this.woodToAdd);
        this.ore += getDrainPerTick(this.oreToAdd);
        this.oreToAdd -= getDrainPerTick(this.oreToAdd);
        this.emerald += getDrainPerTick(this.emeraldToAdd);
        this.emeraldToAdd -= getDrainPerTick(this.emeraldToAdd);
    }

    private int getDrainPerTick(int totalToAdd) {
        int absVal = Math.abs(totalToAdd);
        int retVal = 0;

        if (absVal > 10)
            retVal = Math.round((float) absVal / 10);
        else if (absVal > 0)
            retVal = 1;

        return (int) Math.signum(totalToAdd) * retVal;
    }

    private void addItemValue(ResourceName type, int value) {
        switch (type) {
            case FOOD -> food += value;
            case WOOD -> wood += value;
            case ORE -> ore += value;
            case EMERALD -> emerald += value;
            case NONE -> { }
        }
    }

    public static Resources getTotalResourcesFromItems(List<ItemStack> itemStacks) {
        Resources resources = new Resources("", 0,0,0,0);
        for (ItemStack stack : itemStacks) {
            ResourceSource source = ResourceSources.getFromItem(stack.getItem());
            if (source != null)
                resources.addItemValue(source.resourceName, source.resourceValue * stack.getCount());
        }
        return resources;
    }

    /**
     * Settle whole priced stacks on the server thread. No callback, registry lookup or slot
     * mutation intervenes between the valuation and the commit. An unknown, empty, NONE
     * or non-paying stack stays in place, including its count and item data. If the total
     * cannot be represented by the score or account, leave the entire delivery untouched.
     * Returns the actual credit for the scoreboard and client notifications, or null.
     */
    @javax.annotation.Nullable
    public Resources depositCarriedItems(List<ItemStack> cargo) {
        return depositItemSlots(cargo.size(), cargo::get, cargo::remove, true);
    }

    /** Fixed vanilla chest slots: clear only successfully priced slots and dirty the chest
     * once. Filtering does not compact rejected or empty slots. Portals do not buy emeralds. */
    @javax.annotation.Nullable
    public Resources depositChestItems(ChestBlockEntity chest, boolean acceptEmeralds) {
        Resources credit = depositItemSlots(chest.getContainerSize(), chest::getItem, chest::removeItemNoUpdate, acceptEmeralds);
        if (credit != null) chest.setChanged();
        return credit;
    }

    @javax.annotation.Nullable
    private Resources depositItemSlots(int slots, IntFunction<ItemStack> read, IntConsumer consume, boolean acceptEmeralds) {
        Resources credit = new Resources(ownerName, 0, 0, 0, 0);
        BitSet paidSlots = new BitSet();
        long total = 0;
        for (int i = 0; i < slots; i++) {
            ItemStack stack = read.apply(i);
            if (stack.isEmpty()) continue;
            ResourceSource source = ResourceSources.getFromItem(stack.getItem());
            if (source == null || source.resourceName == null || source.resourceName == ResourceName.NONE
                    || (!acceptEmeralds && source.resourceName == ResourceName.EMERALD) || source.resourceValue <= 0) continue;
            long value = (long) source.resourceValue * stack.getCount();
            total += value;
            if (total > Integer.MAX_VALUE) return null;
            credit.addItemValue(source.resourceName, (int) value);
            paidSlots.set(i);
        }
        if (paidSlots.isEmpty() || (long) food + credit.food > Integer.MAX_VALUE
                || (long) wood + credit.wood > Integer.MAX_VALUE || (long) ore + credit.ore > Integer.MAX_VALUE
                || (long) emerald + credit.emerald > Integer.MAX_VALUE) return null;

        changeInstantly(credit.food, credit.wood, credit.ore, credit.emerald);
        for (int i = paidSlots.length() - 1; i >= 0; i = paidSlots.previousSetBit(i - 1))
            consume.accept(i);
        return credit;
    }
}
