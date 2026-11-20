package com.solegendary.reignofnether.resources;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Shared entity-side cargo codec. The resource snapshot owns priced stacks on a cold
 * load; on an admitted body an NBT edit is NOT a resource snapshot. */
public final class UnitCargoSaveData {
    private UnitCargoSaveData() {}

    public static boolean isLedgerValued(ItemStack stack) {
        if (stack.isEmpty()) return false;
        // Ask the same valuation used by saveResources, not just whether the item has a
        // resource source: a registered zero-value item (e.g. soul sand) is still cargo.
        Resources value = Resources.getTotalResourcesFromItems(List.of(stack));
        return value.food != 0 || value.wood != 0 || value.ore != 0 || value.emerald != 0;
    }

    private static boolean keep(ItemStack stack) {
        return !stack.isEmpty() && !isLedgerValued(stack);
    }

    public static ListTag write(List<ItemStack> cargo) {
        ListTag saved = new ListTag();
        for (ItemStack stack : cargo)
            if (keep(stack)) saved.add(stack.save(new CompoundTag()));
        // Never consume the running cargo. The account snapshot may be taken later.
        return saved;
    }

    public static void read(List<ItemStack> cargo, ListTag saved, boolean liveEntity) {
        List<ItemStack> restored = new ArrayList<>();
        for (int i = 0; i < saved.size(); i++) {
            ItemStack stack = ItemStack.of(saved.getCompound(i));
            // A stale or manually edited entity tag cannot reintroduce a priced stack.
            if (keep(stack)) restored.add(stack);
        }
        if (!liveEntity) {
            cargo.clear();
            cargo.addAll(restored);
            return;
        }

        // /data merge entity reloads the *existing* body, without an account save.
        // Replace only the slots this field owns. Keep valued stacks (and their order)
        // in place, rather than erasing them or moving them ahead of edible cargo.
        var remaining = restored.iterator();
        var slots = cargo.listIterator();
        while (slots.hasNext()) {
            ItemStack current = slots.next();
            if (current.isEmpty()) slots.remove();
            else if (keep(current)) {
                if (remaining.hasNext()) slots.set(remaining.next());
                else slots.remove();
            }
        }
        remaining.forEachRemaining(cargo::add);
    }
}
