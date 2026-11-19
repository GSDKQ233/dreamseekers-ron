package com.solegendary.reignofnether.fogofwar;

/** Pure, fail-closed preflight. Chunk coverage is half-open, including fractional/negative borders. */
public record FogSnapshotBounds(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {
    public static final int MAX_BORDER_SIZE = 1280;
    public static final long MAX_CHUNKS = 81L * 81L; // an unaligned 1280-block border touches 81 chunks

    public static FogSnapshotBounds checked(double size, double minX, double maxX, double minZ, double maxZ) {
        if (!Double.isFinite(size) || size <= 0 || size > MAX_BORDER_SIZE
                || !validAxis(minX, maxX) || !validAxis(minZ, maxZ)) return null;
        int x0 = (int) Math.floor(minX / 16.0);
        int x1 = (int) Math.ceil(maxX / 16.0) - 1;
        int z0 = (int) Math.floor(minZ / 16.0);
        int z1 = (int) Math.ceil(maxZ / 16.0) - 1;
        long width = (long) x1 - x0 + 1;
        long depth = (long) z1 - z0 + 1;
        if (width <= 0 || depth <= 0 || width > 81 || depth > 81 || width * depth > MAX_CHUNKS) return null;
        return new FogSnapshotBounds(x0, x1, z0, z1);
    }

    private static boolean validAxis(double min, double max) {
        return Double.isFinite(min) && Double.isFinite(max) && min >= -30_000_000
                && max <= 30_000_000 && max > min && max - min <= MAX_BORDER_SIZE;
    }

    public boolean contains(int x, int z) {
        return x >= minChunkX && x <= maxChunkX && z >= minChunkZ && z <= maxChunkZ;
    }
}
