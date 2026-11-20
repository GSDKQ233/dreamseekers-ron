// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.building;

import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.data.DataStorage;
import com.solegendary.reignofnether.building.data.DataType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;

/**
 * Template revision of a placed building. New construction uses the PasterDream-styled template;
 * a saved placement without this tag keeps the original template it was built from, so its
 * existing blocks still count as placed. The restyled template keeps the original bounding box, ground plate
 * and approach corridor, but its walls and roofs are a new design.
 */
public final class DreamTemplateModel {
    /** Registered lazily so the pure revision rules stay loadable without a game registry. */
    private static final class Storage {
        static final DataType<CompoundTag> MODEL = DataType.createRegistered(
                ResourceLocation.fromNamespaceAndPath("dreamseekers", "template_model"),
                (tag, server) -> tag.copy(), CompoundTag::copy, CompoundTag::new);
    }
    public static final int LEGACY = 1, DYEDREAM = 2;
    private static final String SUFFIX = "_dyedream_v2", KIND = "dream_template_legacy";

    private DreamTemplateModel() {}
    /** Registers the persisted codec before ordinary world save-data decoding. */
    public static void bootstrap() { java.util.Objects.requireNonNull(Storage.MODEL); }
    public static void write(DataStorage storage, int revision) { storage.setData(Storage.MODEL, tag(revision)); }

    public static String template(String base, int revision) {
        if (revision != LEGACY && revision != DYEDREAM) throw new IllegalArgumentException("Unknown template revision");
        return revision == DYEDREAM ? base + SUFFIX : base;
    }
    public static ArrayList<BuildingBlock> blocks(String base, int revision) {
        return DreamBuildingSupport.load(template(base, revision));
    }
    /** Anything other than an exact current tag is the original template. */
    public static int restoredRevision(DataStorage saved) {
        if (saved == null) return LEGACY;
        CompoundTag tag = saved.getData(Storage.MODEL);
        return tag.contains("schema", 3) && tag.getInt("schema") == 1
                && tag.contains("revision", 3) && tag.getInt("revision") == DYEDREAM ? DYEDREAM : LEGACY;
    }
    public static CompoundTag tag(int revision) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 1);
        tag.putInt("revision", revision);
        return tag;
    }
    /** Clients build every placement from the current template; only an original-template placement says otherwise. */
    public static boolean validLegacySnapshot(CompoundTag tag) {
        return KIND.equals(tag.getString("kind")) && tag.contains("highest", 3) && tag.getInt("highest") >= 0
                && tag.contains("placed", 3) && tag.getInt("placed") >= 0 && tag.contains("partial", 6)
                && Double.isFinite(tag.getDouble("partial")) && tag.getDouble("partial") >= 0 && tag.getDouble("partial") < 1;
    }
    public static boolean acceptsInitialSnapshot(CompoundTag snapshot) {
        return snapshot.isEmpty() || validLegacySnapshot(snapshot);
    }

    /** Per-placement revision; the owning placement applies geometry through its own protected restore hook. */
    public static final class State {
        private final String base;
        private int revision;
        public State(String base, int revision) { this.base = base; this.revision = revision; template(base, revision); }
        public int revision() { return revision; }
        public ArrayList<BuildingBlock> blocks() { return DreamTemplateModel.blocks(base, revision); }
        public CompoundTag snapshot(boolean clientSide, int highest, int placed, double partial) {
            CompoundTag tag = new CompoundTag();
            if (clientSide || revision != LEGACY) return tag;
            tag.putString("kind", KIND);
            tag.putInt("highest", highest);
            tag.putInt("placed", placed);
            tag.putDouble("partial", partial);
            return tag;
        }
        public boolean accepts(CompoundTag snapshot) { return acceptsInitialSnapshot(snapshot); }
        /** True when the client object must swap its block list to the other revision. */
        public boolean switchFor(CompoundTag snapshot, boolean initial) {
            if (validLegacySnapshot(snapshot)) {
                if (revision == LEGACY) return false;
                revision = LEGACY;
                return true;
            }
            if (snapshot.isEmpty() && initial && revision == LEGACY) {
                revision = DYEDREAM;
                return true;
            }
            return false;
        }
    }
}
