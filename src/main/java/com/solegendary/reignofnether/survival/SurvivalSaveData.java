package com.solegendary.reignofnether.survival;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SurvivalSaveData extends SavedData {
    public boolean isEnabled;
    public int waveNumber = 1;
    public WaveDifficulty difficulty = WaveDifficulty.BEGINNER;
    public long randomSeed;
    SurvivalResult result = SurvivalResult.OPEN;
    int activeWaveNumber;
    boolean wavesFinished;
    final Set<UUID> activeEnemies = new HashSet<>();
    final Map<BlockPos, PortalState> activePortals = new HashMap<>();
    // Terminal cleanup tombstones are not outstanding waves or reward claims.
    final Set<UUID> retiredEnemies = new HashSet<>();
    final Set<BlockPos> retiredPortals = new HashSet<>();
    int portalSpawnTicks;
    @Nullable Vec3 lastKill;

    record PortalState(int wave, int remainingInitialPop) {}

    WaveCursor cursor() { return new WaveCursor(waveNumber, activeWaveNumber, wavesFinished); }
    void setCursor(WaveCursor cursor) {
        waveNumber = cursor.next();
        activeWaveNumber = cursor.active();
        wavesFinished = cursor.finished();
    }

    private static SurvivalSaveData create() { return new SurvivalSaveData(); }

    @Nonnull
    public static SurvivalSaveData getInstance(LevelAccessor level) {
        MinecraftServer server = level.getServer();
        if (server == null) return create();
        return server.overworld().getDataStorage()
                .computeIfAbsent(SurvivalSaveData::load, SurvivalSaveData::create, "saved-survival-data");
    }

    public static SurvivalSaveData load(CompoundTag tag) {
        SurvivalSaveData data = create();
        data.isEnabled = tag.getBoolean("isEnabled");
        // Old worlds only recorded waveNumber. It is not proof that this wave was cleared
        // (or even started); preserve that world's stored next wave and never invent rewards.
        data.setCursor(new WaveCursor(tag.getInt("waveNumber"), tag.getInt("activeWave"), tag.getBoolean("wavesFinished")));
        try { data.difficulty = WaveDifficulty.valueOf(tag.getString("difficulty")); }
        catch (IllegalArgumentException ignored) { /* use the new-world difficulty */ }
        data.randomSeed = tag.getLong("randomSeed");
        // Absence in an older save (including a cleared wave 30) does not prove a match result.
        if (data.isEnabled && tag.contains("matchResult", Tag.TAG_COMPOUND)) {
            CompoundTag result = tag.getCompound("matchResult");
            Set<String> winners = new HashSet<>();
            for (Tag name : result.getList("winners", Tag.TAG_STRING)) winners.add(name.getAsString());
            try {
                data.result = new SurvivalResult(SurvivalResult.Kind.valueOf(result.getString("kind")),
                        winners, result.getLong("elapsed"));
            } catch (IllegalArgumentException ignored) { /* unknown result, no invented winners */ }
        }
        if (data.result.terminal()) data.activeWaveNumber = 0;
        // Unloaded exact identities may arrive after an explicit new-match reset.
        for (Tag entry : tag.getList("retiredEnemies", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) entry;
            if (e.hasUUID("id")) data.retiredEnemies.add(e.getUUID("id"));
        }
        for (long pos : tag.getLongArray("retiredPortals")) data.retiredPortals.add(BlockPos.of(pos));
        if (data.activeWaveNumber > 0) {
            for (Tag entry : tag.getList("activeEnemies", Tag.TAG_COMPOUND)) {
                CompoundTag e = (CompoundTag) entry;
                if (e.hasUUID("id")) data.activeEnemies.add(e.getUUID("id"));
            }
            for (Tag entry : tag.getList("activePortals", Tag.TAG_COMPOUND)) {
                CompoundTag p = (CompoundTag) entry;
                if (p.contains("pos", Tag.TAG_LONG)) data.activePortals.put(BlockPos.of(p.getLong("pos")),
                        new PortalState(p.getInt("wave"), p.getInt("remaining")));
            }
            data.portalSpawnTicks = tag.getInt("portalTicks");
            if (tag.contains("lastKill", Tag.TAG_COMPOUND)) {
                CompoundTag pos = tag.getCompound("lastKill");
                double x = pos.getDouble("x"), y = pos.getDouble("y"), z = pos.getDouble("z");
                if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)) data.lastKill = new Vec3(x, y, z);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("isEnabled", isEnabled);
        tag.putInt("waveNumber", waveNumber);
        tag.putString("difficulty", difficulty.name());
        tag.putLong("randomSeed", randomSeed);
        if (result.terminal()) {
            CompoundTag end = new CompoundTag();
            end.putString("kind", result.kind().name());
            end.putLong("elapsed", result.elapsed());
            ListTag names = new ListTag();
            result.winners().stream().sorted().forEach(n -> names.add(net.minecraft.nbt.StringTag.valueOf(n)));
            end.put("winners", names);
            tag.put("matchResult", end);
        } else tag.remove("matchResult");
        ListTag retired = new ListTag();
        retiredEnemies.forEach(id -> { CompoundTag e = new CompoundTag(); e.putUUID("id", id); retired.add(e); });
        tag.put("retiredEnemies", retired);
        tag.putLongArray("retiredPortals", retiredPortals.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putInt("activeWave", activeWaveNumber);
        tag.putBoolean("wavesFinished", wavesFinished);
        if (activeWaveNumber > 0) {
            ListTag bodies = new ListTag();
            activeEnemies.forEach(id -> { CompoundTag e = new CompoundTag(); e.putUUID("id", id); bodies.add(e); });
            tag.put("activeEnemies", bodies);
            ListTag portals = new ListTag();
            activePortals.forEach((pos, state) -> {
                CompoundTag p = new CompoundTag();
                p.putLong("pos", pos.asLong()); p.putInt("wave", state.wave()); p.putInt("remaining", state.remainingInitialPop());
                portals.add(p);
            });
            tag.put("activePortals", portals);
            tag.putInt("portalTicks", portalSpawnTicks);
            if (lastKill != null) {
                CompoundTag pos = new CompoundTag();
                pos.putDouble("x", lastKill.x); pos.putDouble("y", lastKill.y); pos.putDouble("z", lastKill.z);
                tag.put("lastKill", pos);
            }
        }
        return tag;
    }

    public void save() { setDirty(); }
}
