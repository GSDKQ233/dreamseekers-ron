package com.solegendary.reignofnether.fogofwar;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import java.util.*;

/** Original neutral locations, not a live enemy-position cache or a chunk snapshot. */
public final class NeutralFogSaveData extends SavedData {
    static final int FORMAT_VERSION = 1;
    // Unknown schemas stay opaque, including on save; downgrade must never erase newer match data.
    private CompoundTag opaqueData;
    static final int MAX_MARKERS = 16384;
    static final int MAX_VIEWERS_PER_MARKER = 1024;
    private boolean captured;
    private final Map<UUID, Marker> markers = new LinkedHashMap<>();

    public static final class Marker {
        public final UUID id;
        public final AABB bounds;
        private boolean destroyed;
        private final Set<UUID> removedFor = new HashSet<>();
        private Marker(UUID id, AABB bounds) { this.id = id; this.bounds = bounds; }
        public boolean isRemovedFor(UUID viewer) { return removedFor.contains(viewer); }
        public boolean isDestroyed() { return destroyed; }
    }

    public static NeutralFogSaveData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(NeutralFogSaveData::load,
                NeutralFogSaveData::new, "saved-neutral-fog-data");
    }

    public void reset() { if (!isSupported()) return; markers.clear(); captured = false; setDirty(); }
    public boolean isSupported() { return opaqueData == null; }
    public boolean isCaptured() { return !isSupported() || captured; }
    public Collection<Marker> markers() { return Collections.unmodifiableCollection(markers.values()); }
    public void finishCapture() { if (!isSupported()) return; captured = true; setDirty(); }
    public void capture(UUID id, AABB bounds) {
        if (!isSupported() || captured || markers.size() >= MAX_MARKERS || !validBounds(bounds)) return;
        if (markers.putIfAbsent(id, new Marker(id, bounds)) == null) setDirty();
    }
    public void markDestroyed(UUID id) {
        if (!isSupported()) return;
        Marker marker = markers.get(id);
        if (marker != null && !marker.destroyed) { marker.destroyed = true; setDirty(); }
    }
    // Observation is viewer-specific: a death in darkness must not remove another player's marker.
    public boolean observeDestroyed(UUID id, UUID viewer, boolean visible) {
        if (!isSupported()) return false;
        Marker marker = markers.get(id);
        if (marker == null || !marker.destroyed || !visible || marker.isRemovedFor(viewer)) return false;
        // At the bounded history limit, leave additional viewers' markers unchanged. Never send a
        // removal we cannot remember: that would resurrect the marker on their next login.
        if (marker.removedFor.size() >= MAX_VIEWERS_PER_MARKER) return false;
        marker.removedFor.add(viewer);
        setDirty();
        return true;
    }

    private static boolean validBounds(AABB b) {
        return Double.isFinite(b.minX) && Double.isFinite(b.minY) && Double.isFinite(b.minZ)
                && Double.isFinite(b.maxX) && Double.isFinite(b.maxY) && Double.isFinite(b.maxZ)
                && Math.abs(b.minX) <= 30000000 && Math.abs(b.maxX) <= 30000000
                && Math.abs(b.minZ) <= 30000000 && Math.abs(b.maxZ) <= 30000000
                && Math.abs(b.minY) <= 30000000 && Math.abs(b.maxY) <= 30000000
                && b.getXsize() <= 256 && b.getYsize() <= 256 && b.getZsize() <= 256;
    }
    private static boolean hasBounds(CompoundTag tag) {
        for (String key : new String[] {"MinX", "MinY", "MinZ", "MaxX", "MaxY", "MaxZ"})
            if (!tag.contains(key, Tag.TAG_ANY_NUMERIC)) return false;
        return true;
    }
    public static NeutralFogSaveData load(CompoundTag tag) {
        NeutralFogSaveData data = new NeutralFogSaveData();
        if (!tag.contains("FormatVersion", Tag.TAG_INT) || tag.getInt("FormatVersion") != FORMAT_VERSION) {
            data.opaqueData = tag.copy();
            return data;
        }
        data.captured = tag.getBoolean("Captured");
        ListTag entries = tag.getList("Markers", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(entries.size(), MAX_MARKERS); i++) {
            CompoundTag entry = entries.getCompound(i);
            if (!entry.hasUUID("Id") || !hasBounds(entry)) continue;
            AABB bounds = new AABB(entry.getDouble("MinX"), entry.getDouble("MinY"), entry.getDouble("MinZ"),
                    entry.getDouble("MaxX"), entry.getDouble("MaxY"), entry.getDouble("MaxZ"));
            if (!validBounds(bounds)) continue;
            Marker marker = new Marker(entry.getUUID("Id"), bounds);
            marker.destroyed = entry.getBoolean("Destroyed");
            ListTag viewers = entry.getList("RemovedFor", Tag.TAG_COMPOUND);
            if (marker.destroyed) for (int v = 0; v < Math.min(viewers.size(), MAX_VIEWERS_PER_MARKER); v++) {
                CompoundTag viewer = viewers.getCompound(v);
                if (viewer.hasUUID("Id")) marker.removedFor.add(viewer.getUUID("Id"));
            }
            data.markers.putIfAbsent(marker.id, marker);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag) {
        if (!isSupported()) return opaqueData.copy();
        tag.putInt("FormatVersion", FORMAT_VERSION);
        tag.putBoolean("Captured", captured);
        ListTag entries = new ListTag();
        for (Marker marker : markers.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", marker.id);
            AABB b = marker.bounds;
            entry.putDouble("MinX", b.minX); entry.putDouble("MinY", b.minY); entry.putDouble("MinZ", b.minZ);
            entry.putDouble("MaxX", b.maxX); entry.putDouble("MaxY", b.maxY); entry.putDouble("MaxZ", b.maxZ);
            entry.putBoolean("Destroyed", marker.destroyed);
            ListTag viewers = new ListTag();
            for (UUID id : marker.removedFor) {
                CompoundTag viewer = new CompoundTag(); viewer.putUUID("Id", id); viewers.add(viewer);
            }
            entry.put("RemovedFor", viewers); entries.add(entry);
        }
        tag.put("Markers", entries);
        return tag;
    }
}
