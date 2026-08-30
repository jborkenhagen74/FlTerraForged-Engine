package dev.foucaultleon.flterraforged.engine.climate;

import dev.foucaultleon.flterraforged.engine.internal.Maths;
import dev.foucaultleon.flterraforged.engine.noise.NoiseMath;

/**
 * Thread-safe jittered Voronoi sampler for broad climate and biome-region hints.
 *
 * <p>The sampler intentionally produces semantic climate-region signals only; the engine does not
 * assign Minecraft biomes.</p>
 */
public final class ClimateRegionSampler {

    private static final long POINT_X_SALT = 0x243F6A8885A308D3L;
    private static final long POINT_Z_SALT = 0x13198A2E03707344L;
    private static final long ID_SALT = 0xA4093822299F31D0L;
    private static final long TEMPERATURE_SALT = 0x082EFA98EC4E6C89L;
    private static final long MOISTURE_SALT = 0x452821E638D01377L;

    private final long seed;
    private final double frequency;
    private final double jitter;

    /**
     * Creates a climate-region sampler.
     *
     * @param seed world-derived climate-region seed
     * @param scale climate-region frequency in world blocks
     * @param jitter region-point jitter in {@code [0, 1]}
     */
    public ClimateRegionSampler(long seed, double scale, double jitter) {
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
     * Samples climate-region ownership, region edge and climate anchors.
     *
     * @param worldX world X coordinate
     * @param worldZ world Z coordinate
     * @return deterministic climate-region sample
     */
    public ClimateRegionSample sample(double worldX, double worldZ) {
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
            throw new IllegalStateException("climate region ownership could not be resolved");
        }
        double delta = Math.max(0.0D, Math.sqrt(second) - Math.sqrt(first));
        double edge = Maths.clamp(delta / 0.45D, 0.0D, 1.0D);
        return new ClimateRegionSample(
                unitValue(ID_SALT, owner.cellX, owner.cellZ),
                unitValue(ID_SALT, neighbor.cellX, neighbor.cellZ),
                edge,
                climateAnchor(TEMPERATURE_SALT, owner.cellX, owner.cellZ),
                climateAnchor(MOISTURE_SALT, owner.cellX, owner.cellZ),
                climateAnchor(TEMPERATURE_SALT, neighbor.cellX, neighbor.cellZ),
                climateAnchor(MOISTURE_SALT, neighbor.cellX, neighbor.cellZ));
    }

    private Point point(int cellX, int cellZ) {
        double x = cellX + 0.5D + (unitValue(POINT_X_SALT, cellX, cellZ) - 0.5D) * jitter;
        double z = cellZ + 0.5D + (unitValue(POINT_Z_SALT, cellX, cellZ) - 0.5D) * jitter;
        return new Point(cellX, cellZ, x, z);
    }

    private double climateAnchor(long salt, int cellX, int cellZ) {
        double primary = unitValue(salt, cellX, cellZ);
        double secondary = unitValue(salt ^ 0x9E3779B97F4A7C15L, cellX >> 1, cellZ >> 1);
        return Maths.clamp(primary * 0.70D + secondary * 0.30D, 0.0D, 1.0D);
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
