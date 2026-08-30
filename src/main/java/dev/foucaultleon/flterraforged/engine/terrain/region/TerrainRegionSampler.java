package dev.foucaultleon.flterraforged.engine.terrain.region;

import dev.foucaultleon.flterraforged.engine.internal.Maths;
import dev.foucaultleon.flterraforged.engine.noise.NoiseMath;

/**
 * Thread-safe jittered Voronoi partition used to assign broad terrain landforms independently from
 * continent ownership.
 */
public final class TerrainRegionSampler {

    private static final long POINT_X_SALT = 0x6A09E667F3BCC909L;
    private static final long POINT_Z_SALT = 0xBB67AE8584CAA73BL;
    private static final long ID_SALT = 0x3C6EF372FE94F82BL;
    private final long seed;
    private final double frequency;
    private final double jitter;

    /**
     * Creates a terrain-region sampler.
     *
     * @param seed world-derived region seed
     * @param scale terrain-region frequency in world blocks
     * @param jitter region-point jitter in {@code [0, 1]}
     */
    public TerrainRegionSampler(long seed, double scale, double jitter) {
        if (!Double.isFinite(scale) || scale <= 0.0D) {
            throw new IllegalArgumentException("scale must be finite and > 0");
        }
        if (!Double.isFinite(jitter) || jitter < 0.0D || jitter > 1.0D) {
            throw new IllegalArgumentException("jitter must be finite and in [0, 1]");
        }
        this.seed = seed;
        this.frequency = scale;
        this.jitter = jitter;
    }

    /**
     * Resolves terrain-region ownership and nearest-neighbor boundary distance.
     *
     * @param worldX world X coordinate
     * @param worldZ world Z coordinate
     * @return deterministic region sample
     */
    public TerrainRegionSample sample(double worldX, double worldZ) {
        double x = worldX * frequency;
        double z = worldZ * frequency;
        int xi = NoiseMath.floor(x);
        int zi = NoiseMath.floor(z);
        Point owner = null;
        Point neighbor = null;
        double first = Double.POSITIVE_INFINITY;
        double second = Double.POSITIVE_INFINITY;
        for (int cz = zi - 1; cz <= zi + 1; cz++) {
            for (int cx = xi - 1; cx <= xi + 1; cx++) {
                Point point = point(cx, cz);
                double distance = distanceSq(x, z, point.x, point.z);
                if (distance < first) {
                    second = first;
                    neighbor = owner;
                    first = distance;
                    owner = point;
                } else if (distance < second) {
                    second = distance;
                    neighbor = point;
                }
            }
        }
        if (owner == null || neighbor == null) {
            throw new IllegalStateException("terrain region ownership could not be resolved");
        }
        double delta = Math.max(0.0D, Math.sqrt(second) - Math.sqrt(first));
        double edge = Maths.clamp(delta / 0.45D, 0.0D, 1.0D);
        return new TerrainRegionSample(
                unitValue(ID_SALT, owner.cellX, owner.cellZ),
                unitValue(ID_SALT, neighbor.cellX, neighbor.cellZ),
                edge,
                owner.cellX,
                owner.cellZ);
    }

    private Point point(int cellX, int cellZ) {
        double x = cellX + 0.5D + (unitValue(POINT_X_SALT, cellX, cellZ) - 0.5D) * jitter;
        double z = cellZ + 0.5D + (unitValue(POINT_Z_SALT, cellX, cellZ) - 0.5D) * jitter;
        return new Point(cellX, cellZ, x, z);
    }

    private double unitValue(long salt, int cellX, int cellZ) {
        return 0.5D + NoiseMath.value(seed ^ salt, cellX, cellZ) * 0.5D;
    }

    private static double distanceSq(double x, double z, double px, double pz) {
        double dx = x - px;
        double dz = z - pz;
        return dx * dx + dz * dz;
    }

    private record Point(int cellX, int cellZ, double x, double z) {
    }
}
