package com.solegendary.reignofnether.resources;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Versioned extension payload saved inside the same account compound as F/W/O. */
public final class ResourceAccountCodec {
    public static final String KEY = "resourceExtensions";
    public static final int VERSION = 1;
    public static final int MAX_EXTRA = 64;
    public static final int MAX_REMAINDERS = 128;
    public static final int MAX_RECEIPTS = 4096;
    private ResourceAccountCodec() {}

    public static void read(Resources account, CompoundTag parent) {
        account.extra.clear(); account.conversionRemainders.clear(); account.paidReceipts.clear();
        account.addonData=new CompoundTag();account.opaqueAddonData=null;
        account.revision = 0;
        account.opaqueExtendedState = null;
        if (!parent.contains(KEY)) { account.accountEpoch = UUID.randomUUID(); return; }
        if (!parent.contains(KEY, Tag.TAG_COMPOUND)) {
            // Wrong outer type is not a valid empty account; retain it for diagnosis.
            account.opaqueExtendedState = parent.get(KEY).copy();
            return;
        }
        CompoundTag tag = parent.getCompound(KEY);
        if (!tag.contains("version", Tag.TAG_INT) || tag.getInt("version") != VERSION || !tag.hasUUID("epoch")) { quarantine(account, tag); return; }
        ResourceAddonData.read(account,tag);
        account.accountEpoch = tag.getUUID("epoch");
        account.revision = nonnegativeLong(tag, "revision");
        CompoundTag extra = tag.getCompound("extra");
        CompoundTag remainders = tag.getCompound("remainders");
        ListTag receipts = tag.getList("receipts", Tag.TAG_COMPOUND);
        if ((tag.contains("extra") && !tag.contains("extra", Tag.TAG_COMPOUND))
                || (tag.contains("remainders") && !tag.contains("remainders", Tag.TAG_COMPOUND))
                || (tag.contains("receipts") && !tag.contains("receipts", Tag.TAG_LIST))
                || extra.size() > MAX_EXTRA || remainders.size() > MAX_REMAINDERS || receipts.size() > MAX_RECEIPTS) {
            quarantine(account, tag); return;
        }
        // A non-compound list is not an empty receipt list: otherwise paid claims vanish.
        if (tag.get("receipts") instanceof ListTag raw && !raw.isEmpty() && raw.getElementType() != Tag.TAG_COMPOUND) {
            quarantine(account, tag); return;
        }
        for (String key : extra.getAllKeys()) {
            ResourceLocation id = validId(key);
            if (id == null || account.extra.containsKey(id)) { quarantine(account, tag); return; }
            account.extra.put(id, boundedInt(extra, key));
        }
        for (String key : remainders.getAllKeys()) {
            if (key.isBlank() || key.length() > 256) { quarantine(account, tag); return; }
            account.conversionRemainders.put(key, Math.min(9999L, nonnegativeLong(remainders, key)));
        }
        try {
            for (int i = 0; i < receipts.size(); i++) {
                CompoundTag receipt = receipts.getCompound(i);
                if (!receipt.hasUUID("id") || !receipt.hasUUID("payerEpoch")) throw new IllegalArgumentException("Receipt UUID");
                UUID id = receipt.getUUID("id");
                UUID epoch = receipt.getUUID("payerEpoch");
                if (!epoch.equals(account.accountEpoch) || account.paidReceipts.containsKey(id))
                    throw new IllegalArgumentException("Receipt identity");
                CompoundTag paidExtra = receipt.getCompound("extra");
                if (paidExtra.size() > MAX_EXTRA || (receipt.contains("extra") && !receipt.contains("extra",Tag.TAG_COMPOUND)))
                    throw new IllegalArgumentException("Receipt extras");
                Map<ResourceLocation,Integer> amounts = new LinkedHashMap<>();
                for (String key : paidExtra.getAllKeys()) {
                    ResourceLocation resource = validId(key);
                    if (resource == null || amounts.containsKey(resource)) throw new IllegalArgumentException("Receipt resource");
                    amounts.put(resource, exactNonnegativeInt(paidExtra, key));
                }
                ResourcePaidReceipt paid = new ResourcePaidReceipt(id, epoch, receipt.getString("purpose"),
                        exactNonnegativeInt(receipt,"food"),exactNonnegativeInt(receipt,"wood"),
                        exactNonnegativeInt(receipt,"ore"),exactNonnegativeInt(receipt,"emerald"),amounts);
                account.paidReceipts.put(id, paid);
            }
        } catch (IllegalArgumentException ex) { quarantine(account, tag); }
    }

    public static void write(Resources account, CompoundTag parent) {
        if (account.opaqueExtendedState != null) {
            parent.put(KEY, account.opaqueExtendedState.copy());
            return;
        }
        if (account.extra.size() > MAX_EXTRA || account.conversionRemainders.size() > MAX_REMAINDERS
                || account.paidReceipts.size() > MAX_RECEIPTS)
            throw new IllegalStateException("Account extension collection exceeds supported capacity");
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", VERSION);
        tag.putUUID("epoch", account.accountEpoch);
        tag.putLong("revision", Math.max(0L,account.revision));
        CompoundTag extra = new CompoundTag();
        account.extra.forEach((id,value) -> extra.putInt(id.toString(), Math.max(0,value)));
        tag.put("extra",extra);
        CompoundTag remainders = new CompoundTag();
        account.conversionRemainders.forEach((key,value) -> remainders.putLong(key, Math.max(0,Math.min(9999L,value))));
        tag.put("remainders",remainders);
        ListTag receipts = new ListTag();
        for (var entry : account.paidReceipts.entrySet()) {
            ResourcePaidReceipt paid = entry.getValue();
            if (!entry.getKey().equals(paid.id()) || !account.accountEpoch.equals(paid.payerEpoch()))
                throw new IllegalStateException("Receipt does not belong to this account");
            CompoundTag receipt = new CompoundTag();
            receipt.putUUID("id",paid.id()); receipt.putUUID("payerEpoch",paid.payerEpoch());
            receipt.putString("purpose",paid.purpose());
            receipt.putInt("food",paid.food()); receipt.putInt("wood",paid.wood());
            receipt.putInt("ore",paid.ore()); receipt.putInt("emerald",paid.emerald());
            CompoundTag amounts = new CompoundTag();
            paid.extra().forEach((id,value) -> amounts.putInt(id.toString(),value));
            receipt.put("extra",amounts); receipts.add(receipt);
        }
        tag.put("receipts",receipts); ResourceAddonData.write(account,tag); parent.put(KEY,tag);
    }
    private static void quarantine(Resources account, CompoundTag original) {
        account.extra.clear(); account.conversionRemainders.clear(); account.paidReceipts.clear();
        account.addonData=new CompoundTag();account.opaqueAddonData=null;
        account.opaqueExtendedState = original.copy();
    }
    private static ResourceLocation validId(String key) {
        return key.length() <= 256 ? ResourceLocation.tryParse(key) : null;
    }
    private static boolean integral(CompoundTag tag, String key) {
        Tag value = tag.get(key);
        return value != null && value.getId() >= Tag.TAG_BYTE && value.getId() <= Tag.TAG_LONG;
    }
    private static long nonnegativeLong(CompoundTag tag, String key) {
        return integral(tag,key) ? Math.max(0L,tag.getLong(key)) : 0L;
    }
    private static int boundedInt(CompoundTag tag, String key) {
        return (int)Math.min(Integer.MAX_VALUE,nonnegativeLong(tag,key));
    }
    private static int exactNonnegativeInt(CompoundTag tag, String key) {
        if (!integral(tag,key) || tag.getLong(key) < 0 || tag.getLong(key) > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Invalid receipt amount");
        return (int)tag.getLong(key);
    }
}
