package com.dreamseekers.economy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Immutable save value. The interpreted version is never the encoding for an unknown payload. */
public final class GardenDecorationState {
    private final CompoundTag payload;

    private GardenDecorationState(CompoundTag ownedPayload) {
        payload = ownedPayload;
    }

    static GardenDecorationState read(CompoundTag tag) {
        return new GardenDecorationState(tag.copy());
    }

    static GardenDecorationState legacy() { return versioned(0); }
    static GardenDecorationState current() { return versioned(1); }

    private static GardenDecorationState versioned(int version) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", version);
        return new GardenDecorationState(tag);
    }

    public int knownVersion() {
        if (!payload.contains("version", Tag.TAG_INT)) return -1;
        int version = payload.getInt("version");
        return version == 0 || version == 1 ? version : -1;
    }

    CompoundTag write() { return payload.copy(); }

    GardenDecorationState migrated() {
        if (knownVersion() != 0) throw new IllegalStateException("Only legacy garden decorations can migrate");
        CompoundTag next = write();
        next.putInt("version", 1);
        return new GardenDecorationState(next);
    }
}
