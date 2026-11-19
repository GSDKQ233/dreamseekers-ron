// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.function;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import java.util.UUID;

/** Per-placement metadata only. No shadow balance, queue, tame target, network or effects are saved. */
public final class DreamFunctionState {
    public static final int SCHEMA_VERSION = 1;
    public record Snapshot(int schemaVersion, UUID instanceId, String owner, boolean built,
                           long ownerRevision, boolean supported) {}
    private UUID instanceId;
    private String owner = "";
    private boolean built;
    private long ownerRevision;
    private final CompoundTag unsupported;
    private final boolean restored;

    public DreamFunctionState() { this(null, false); }
    private DreamFunctionState(CompoundTag unsupported, boolean restored) {
        this.unsupported = unsupported;
        this.restored = restored;
    }
    public static DreamFunctionState read(CompoundTag tag) {
        boolean valid = tag.contains("schemaVersion", Tag.TAG_INT) && tag.getInt("schemaVersion") == SCHEMA_VERSION
                && tag.hasUUID("instanceId") && tag.contains("owner", Tag.TAG_STRING)
                && tag.contains("built", Tag.TAG_BYTE) && tag.contains("ownerRevision", Tag.TAG_LONG)
                && tag.getLong("ownerRevision") >= 0;
        DreamFunctionState state = new DreamFunctionState(valid ? null : tag.copy(), true);
        if (valid) {
            state.instanceId = tag.getUUID("instanceId");
            state.owner = normalize(tag.getString("owner"));
            state.built = tag.getBoolean("built");
            state.ownerRevision = tag.getLong("ownerRevision");
        }
        return state;
    }
    public CompoundTag write() {
        // Forward versions/invalid state are quarantined and byte-semantically retained, not silently reset.
        if (unsupported != null) return unsupported.copy();
        CompoundTag tag = new CompoundTag();
        tag.putInt("schemaVersion", SCHEMA_VERSION);
        if (instanceId != null) tag.putUUID("instanceId", instanceId);
        tag.putString("owner", owner);
        tag.putBoolean("built", built);
        tag.putLong("ownerRevision", ownerRevision);
        return tag;
    }
    public Snapshot snapshot() {
        return new Snapshot(unsupported == null ? SCHEMA_VERSION : unsupported.getInt("schemaVersion"),
                instanceId, owner, built, ownerRevision, supported());
    }
    boolean supported() { return unsupported == null; }
    boolean restored() { return restored; }
    void attach(String currentOwner, boolean currentBuilt) {
        if (!supported() || instanceId != null) return;
        instanceId = UUID.randomUUID();
        owner = normalize(currentOwner);
        built = currentBuilt;
    }
    boolean observeOwner(String currentOwner) {
        String normalized = normalize(currentOwner);
        if (!supported() || owner.equals(normalized)) return false;
        owner = normalized;
        if (ownerRevision < Long.MAX_VALUE) ownerRevision++;
        return true;
    }
    boolean observeBuilt(boolean currentBuilt) {
        if (!supported() || built == currentBuilt) return false;
        built = currentBuilt;
        return true;
    }
    private static String normalize(String owner) { return owner == null || owner.isBlank() ? "" : owner; }
}
