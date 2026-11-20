package com.solegendary.reignofnether.building.data;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class DataStorage {
    Object2ObjectMap<DataType<?>, Object> data = new Object2ObjectArrayMap<>();
    private final ListTag unavailable = new ListTag();
    private final Map<DataType<?>, CompoundTag> envelopes = new Object2ObjectArrayMap<>();

    public static DataStorage read(ListTag dataStorageTag, MinecraftServer server) {
        DataStorage dataStorage = new DataStorage();
        for (Tag tag : dataStorageTag) {
            if (!(tag instanceof CompoundTag element))
                throw new IllegalArgumentException("Unsupported building data envelope");
            ResourceLocation rl = ResourceLocation.tryParse(element.getString("key"));
            DataType<?> type = rl == null ? null : ReignOfNetherRegistries.DATA_TYPE.get(rl);
            if (type == null) {
                dataStorage.unavailable.add(element.copy());
                continue;
            }
            if (!element.contains("data", Tag.TAG_COMPOUND) || dataStorage.data.containsKey(type))
                throw new IllegalArgumentException("Incompatible or duplicate registered building data: " + rl);
            // A known codec owns its payload (including deleting fields); don't merge stale gameplay
            // values back into it. Unknown outer envelope metadata belongs to the placement.
            Object value = type.decode.apply(element.getCompound("data").copy(), server);
            dataStorage.data.put(type, value);
            CompoundTag extra = new CompoundTag();
            for (String key : element.getAllKeys())
                if (!key.equals("key") && !key.equals("data")) extra.put(key, element.get(key).copy());
            if (!extra.isEmpty()) dataStorage.envelopes.put(type, extra);
        }
        return dataStorage;
    }

    public ListTag write() {
        ListTag dataStorageTag = unavailable.copy();
        for (Map.Entry<DataType<?>, Object> dataTypeObjectEntry : data.entrySet()) {
            DataType<Object> type = (DataType<Object>) dataTypeObjectEntry.getKey();
            ResourceLocation rl = ReignOfNetherRegistries.DATA_TYPE.getKey(type);
            CompoundTag extra = envelopes.get(type);
            CompoundTag element = extra == null ? new CompoundTag() : extra.copy();
            element.putString("key", rl.toString());
            element.put("data", type.encode.apply(dataTypeObjectEntry.getValue()));
            dataStorageTag.add(element);
        }
        return dataStorageTag;
    }

    @Nullable
    public <T> T getData(DataType<T> type) {
        ensureRegistered(type);

        return (T) data.computeIfAbsent(type, (unused) -> type.getDefaultValue());
    }

    public <T> void setData(DataType<T> type, T data) {
        ensureRegistered(type);

        this.data.put(type, data);
    }

    private static void ensureRegistered(DataType<?> type) {
        if (ReignOfNetherRegistries.DATA_TYPE.getId(type) == -1) {
            throw new IllegalArgumentException("Used Datatype is not registered");
        }
    }
}
