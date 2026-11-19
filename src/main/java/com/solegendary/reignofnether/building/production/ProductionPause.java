package com.solegendary.reignofnether.building.production;

import net.minecraft.network.FriendlyByteBuf;

/** Runtime-only display state for one public queue-entry UUID. A blank key means working. */
public record ProductionPause(long revision, String reasonKey) {
    public static final ProductionPause INITIAL = new ProductionPause(0, "");
    private static final int MAX_KEY = 96;

    public ProductionPause {
        if (revision < 0 || reasonKey == null || reasonKey.length() > MAX_KEY
                || (revision == 0 && !reasonKey.isEmpty()))
            throw new IllegalArgumentException("Invalid queue head state");
    }

    public boolean paused() { return !reasonKey.isEmpty(); }

    public ProductionPause observe(String actualReason) {
        return reasonKey.equals(actualReason) ? this : new ProductionPause(Math.incrementExact(revision), actualReason);
    }

    public void encode(FriendlyByteBuf out) {
        out.writeVarLong(revision);
        if (revision != 0) out.writeUtf(reasonKey, MAX_KEY);
    }

    public static ProductionPause decode(FriendlyByteBuf in) {
        long revision = in.readVarLong();
        return revision == 0 ? INITIAL : new ProductionPause(revision, in.readUtf(MAX_KEY));
    }
}
