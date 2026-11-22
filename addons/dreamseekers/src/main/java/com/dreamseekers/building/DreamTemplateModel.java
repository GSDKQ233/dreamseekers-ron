// SPDX-License-Identifier: GPL-3.0-only
package com.dreamseekers.building;

import com.dreamseekers.economy.DreamPassiveEconomy;
import com.solegendary.reignofnether.building.BuildingBlock;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.building.data.DataStorage;
import com.solegendary.reignofnether.building.data.DataType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** The nine ordinary buildings. A stable building registry key and a revision select exactly one
 * packaged geometry. No save tag, packet or resource pack supplies a loadable resource path. */
public enum DreamTemplateModel {
    GARDEN("reverie_garden", "reverie_garden_rework_v1", 314, 314),
    SANCTUARY("beetle_sanctuary", "beetle_sanctuary_rework_v1", 253, 251),
    DEPOT("dream_depot", "dream_depot_rework_v1", 332, 332),
    MINE("shadow_mine", "shadow_mine_rework_v1", 342, 342),
    FURNACE("shadow_furnace", "shadow_furnace_rework_v1", 477, 477),
    HALL("dream_hall", "dream_hall_rework_v1", 453, 427),
    LABORATORY("resonance_laboratory", "resonance_laboratory_rework_v1", 441, 404),
    ECOLOGY("contract_ecology", "contract_ecology_rework_v1", 428, 400),
    DAIS("resonance_dais", "resonance_dais_rework_v1", 133, 137);

    // Revision 2 was used by a different, unreleased design. Never alias it to this art.
    public enum Revision {
        ORIGINAL(1), REVIEWED(3);
        final int id;
        Revision(int id) { this.id = id; }
        static Revision fromId(int id) { return id == 1 ? ORIGINAL : id == 3 ? REVIEWED : null; }
    }

    private final String original, reviewed;
    private final int originalCount, reviewedCount;
    DreamTemplateModel(String original, String reviewed, int originalCount, int reviewedCount) {
        this.original = original; this.reviewed = reviewed;
        this.originalCount = originalCount; this.reviewedCount = reviewedCount;
    }
    public static DreamTemplateModel forBuilding(String stableId) {
        for (var model : values()) if (model.original.equals(stableId)) return model;
        throw new IllegalArgumentException("No ordinary template for " + stableId);
    }
    public String resource(Revision revision) {
        return switch (revision) { case ORIGINAL -> original; case REVIEWED -> reviewed; };
    }
    public int count(Revision revision) {
        return switch (revision) { case ORIGINAL -> originalCount; case REVIEWED -> reviewedCount; };
    }

    /** Returns a fresh mutable list. Both old and new garden NBTs retain their 16 original
     * wheat markers; preview, construction and client restore must apply the same substitution. */
    public ArrayList<BuildingBlock> blocks(Revision revision) {
        var result = DreamBuildingSupport.load(resource(revision));
        if (this == GARDEN) {
            for (var block : result) if (block.getBlockState().is(Blocks.WHEAT))
                block.setBlockState(DreamPassiveEconomy.DECORATIVE_WHEAT.get().defaultBlockState());
        } else if (this == SANCTUARY && revision == Revision.REVIEWED) {
            // These two original flower beds are now buried under solid quartz. Grass decays to dirt,
            // which RoN would otherwise count as damage. Leave all visible and all legacy soil alone.
            for (var block : result) {
                var p = block.getBlockPos();
                if (block.getBlockState().is(Blocks.GRASS_BLOCK) && p.getY() == 0
                        && ((p.getX() == 1 && p.getZ() == 8) || (p.getX() == 2 && p.getZ() == 9)))
                    block.setBlockState(Blocks.DIRT.defaultBlockState());
            }
        }
        return result;
    }

    // Optional additive v2 field. Missing in all historical saves/snapshots = no completed local technology.
    private static final String PRODUCTION_METHODS = "productionMethods";
    private boolean resourceProducer() { return this == GARDEN || this == MINE || this == FURNACE; }
    private static boolean validTechTag(CompoundTag tag) {
        return !tag.contains(PRODUCTION_METHODS) || (tag.contains(PRODUCTION_METHODS, Tag.TAG_BYTE)
                && (tag.getByte(PRODUCTION_METHODS) == 0 || tag.getByte(PRODUCTION_METHODS) == 1));
    }
    private static final int SCHEMA = 2;
    private static final String KIND = "dream_ordinary_building";
    private static final class Storage {
        static final DataType<CompoundTag> MODEL = DataType.createRegistered(
                ResourceLocation.fromNamespaceAndPath("dreamseekers", "template_model"),
                (tag, server) -> decodeSavedTag(tag), CompoundTag::copy,
                () -> saveTag(Revision.ORIGINAL, UUID.randomUUID()));
    }
    private static CompoundTag saveTag(Revision revision, UUID instance) {
        var tag = new CompoundTag();
        tag.putInt("schema", SCHEMA); tag.putInt("revision", revision.id);
        tag.putUUID("instance", instance); tag.put("constructionPeak", new CompoundTag());
        return tag;
    }
    /** Register during building registration, before core BuildingSaveData decodes any rows. */
    public static void bootstrap() { java.util.Objects.requireNonNull(Storage.MODEL); }

    /** Only a missing extension (the default, never this decoder) is an original untagged save.
     * Core parks a row that throws here, preserving its original bytes/fields without instantiating
     * a building or touching the world. In particular a present empty/unknown tag is not legacy. */
    static CompoundTag decodeSavedTag(CompoundTag tag) {
        if (!validTechTag(tag) || !tag.contains("schema", Tag.TAG_INT) || tag.getInt("schema") != SCHEMA
                || !tag.contains("revision", Tag.TAG_INT) || Revision.fromId(tag.getInt("revision")) == null
                || !tag.hasUUID("instance") || !tag.contains("constructionPeak", Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("Unsupported Dreamseekers ordinary building model");
        // Progress is keyed by the stable building id, never used to select geometry. Validate every
        // entry at row decode; a dais with a peak of 400 must be parked, not clamped or healed.
        var peaks = tag.getCompound("constructionPeak");
        var revision = Revision.fromId(tag.getInt("revision"));
        for (String id : peaks.getAllKeys()) {
            var model = forBuilding(id);
            if (!peaks.contains(id, Tag.TAG_INT) || peaks.getInt(id) < 2 || peaks.getInt(id) > model.count(revision))
                throw new IllegalArgumentException("Invalid construction peak for " + id);
        }
        return tag.copy();
    }

    public State fresh(Level level) {
        return new State(this, Revision.REVIEWED, level.isClientSide() ? null : UUID.randomUUID(), 2);
    }
    public State restore(DataStorage saved) {
        var tag = saved.getData(Storage.MODEL);
        // The outer row's registered building identifies the descriptor; geometry is not an editable path.
        var revision = Revision.fromId(tag.getInt("revision"));
        var peaks = tag.getCompound("constructionPeak");
        var state = new State(this, revision, tag.getUUID("instance"), peaks.contains(original) ? peaks.getInt(original) : 2);
        state.productionMethods = resourceProducer() && tag.getBoolean(PRODUCTION_METHODS);
        state.serverTag = tag.copy(); // Preserve additive payload fields, including for a stand-alone factory result.
        return state;
    }

    /** Also validates initial PLACE before it is allowed to replace a same-origin client object. */
    public boolean validSnapshot(CompoundTag tag) {
        if (!validTechTag(tag) || (!resourceProducer() && tag.contains(PRODUCTION_METHODS))
                || !KIND.equals(tag.getString("kind")) || !tag.contains("schema", Tag.TAG_INT) || tag.getInt("schema") != SCHEMA
                || !tag.contains("revision", Tag.TAG_INT) || !tag.hasUUID("instance")
                || !tag.contains("highest", Tag.TAG_INT) || !tag.contains("placed", Tag.TAG_INT)
                || !tag.contains("partial", Tag.TAG_DOUBLE)) return false;
        var revision = Revision.fromId(tag.getInt("revision"));
        int high = tag.getInt("highest"), placed = tag.getInt("placed");
        double partial = tag.getDouble("partial");
        return revision != null && resource(revision).equals(tag.getString("template")) && high >= 2
                && high <= count(revision) && placed >= 0 && placed <= high
                && Double.isFinite(partial) && partial >= 0 && partial < 1;
    }

    @FunctionalInterface public interface GeometryRestore {
        void replace(ArrayList<BuildingBlock> absolute, int highest, double partial);
    }

    /** One binding per physical placement. A client binds only on its authoritative initial PLACE.
     * Subsequent deltas (including duplicates) cannot switch models or attach to a replacement. */
    public static final class State {
        private final DreamTemplateModel model;
        private Revision revision;
        private UUID instance;
        private final int savedHighest;
        private CompoundTag serverTag;
        private DataStorage boundStorage;
        private boolean productionMethods;
        State(DreamTemplateModel model, Revision revision, UUID instance, int savedHighest) {
            this.model = model; this.revision = revision; this.instance = instance; this.savedHighest = savedHighest;
            if (instance != null) serverTag = saveTag(revision, instance);
        }
        public Revision revision() { return revision; }
        public UUID instance() { return instance; }
        public boolean hasProductionMethods() { return productionMethods; }
        /** Called only after the local server ledger actually consumed this placement's receipt. */
        public void commitProductionMethods(com.dreamseekers.economy.ResourceBuildingResearch.PaidCompletion proof) {
            if (proof==null || !proof.authorizes(this) || !model.resourceProducer() || serverTag == null)
                throw new IllegalStateException("No paid completion for this resource building");
            productionMethods = true;
            serverTag.putBoolean(PRODUCTION_METHODS, true);
        }
        public ArrayList<BuildingBlock> absolute(Level level, BlockPos origin, Rotation rotation) {
            return BuildingUtils.getAbsoluteBlockData(model.blocks(revision), level, origin, rotation);
        }
        public void save(BuildingPlacement placement, DataStorage storage) {
            if (placement.getLevel().isClientSide() || instance == null) return;
            if (boundStorage != storage) {
                // The native loader installs decoded storage after the constructor. Rebind once,
                // without recalculating a token or allocating new UUID tags on every lifecycle query.
                storage.setData(Storage.MODEL, serverTag);
                boundStorage = storage;
            }
            int high = restoredHighest(placement);
            var peaks = serverTag.getCompound("constructionPeak");
            if (peaks.getInt(model.original) != high) peaks.putInt(model.original, high);
        }
        public int restoredHighest(BuildingPlacement p) {
            return Math.min(model.count(revision), Math.max(p.getHighestBlockCountReached(),
                    p.isBuilt ? model.count(revision) : savedHighest));
        }
        public CompoundTag snapshot(BuildingPlacement p) {
            if (p.getLevel().isClientSide() || instance == null) return new CompoundTag();
            var tag = new CompoundTag();
            if (productionMethods) tag.putBoolean(PRODUCTION_METHODS, true);
            tag.putString("kind", KIND); tag.putInt("schema", SCHEMA);
            tag.putString("template", model.resource(revision)); tag.putInt("revision", revision.id);
            tag.putUUID("instance", instance); tag.putInt("highest", restoredHighest(p));
            tag.putInt("placed", p.getBlocksPlaced()); tag.putDouble("partial", p.partialBlocksDestroyed);
            return tag;
        }
        public boolean accepts(CompoundTag tag) {
            return model.validSnapshot(tag) && instance != null && instance.equals(tag.getUUID("instance"))
                    && revision.id == tag.getInt("revision");
        }
        boolean acceptAndBind(CompoundTag tag, boolean initial, Consumer<Revision> switchGeometry) {
            if (instance == null ? (!initial || !model.validSnapshot(tag)) : !accepts(tag)) return false;
            var next = Revision.fromId(tag.getInt("revision"));
            if (next != revision) switchGeometry.accept(next);
            revision = next; // Commit only after the geometry was replaced successfully.
            instance = tag.getUUID("instance");
            // The researched form is permanent for this UUID. An older pre-research snapshot is not a downgrade.
            productionMethods |= tag.getBoolean(PRODUCTION_METHODS);
            return true;
        }
        public void apply(BuildingPlacement p, CompoundTag tag, boolean initial,
                          GeometryRestore replace, IntConsumer setHighest) {
            if (!p.getLevel().isClientSide()) return;
            int highest = tag.getInt("highest");
            double partial = tag.getDouble("partial");
            if (!acceptAndBind(tag, initial, next -> replace.replace(BuildingUtils.getAbsoluteBlockData(
                    model.blocks(next), p.getLevel(), p.originPos, p.rotation), highest, partial))) return;
            p.setServerBlocksPlaced(tag.getInt("placed"));
            p.partialBlocksDestroyed = partial;
            setHighest.accept(highest); // A duplicate update never reloads the NBT or clears the queue.
        }
    }
}
