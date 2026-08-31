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
    private static final double EDGE_NORMALIZER = 0.45D;
    private static final int NEIGHBORHOOD_DIAMETER = 5;
    private static final int NEIGHBORHOOD_SIZE = NEIGHBORHOOD_DIAMETER * NEIGHBORHOOD_DIAMETER;
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
        double first = Double.POSITIVE_INFINITY;
        double second = Double.POSITIVE_INFINITY;
        int ownerX = 0;
        int ownerZ = 0;
        int neighborX = 0;
        int neighborZ = 0;
        for (int cz = zi - 1; cz <= zi + 1; cz++) {
            for (int cx = xi - 1; cx <= xi + 1; cx++) {
                double distance = distanceSq(x, z, pointX(cx, cz), pointZ(cx, cz));
                if (distance < first) {
                    second = first;
                    neighborX = ownerX;
                    neighborZ = ownerZ;
                    first = distance;
                    ownerX = cx;
                    ownerZ = cz;
                } else if (distance < second) {
                    second = distance;
                    neighborX = cx;
                    neighborZ = cz;
                }
            }
        }
        return new TerrainRegionSample(
                selector(ownerX, ownerZ),
                selector(neighborX, neighborZ),
                normalizedEdge(first, second),
                ownerX,
                ownerZ);
    }

    /**
     * Resolves a continuous multi-region terrain blend for one world position.
     *
     * <p>Every point in the local 3x3 Voronoi neighborhood is considered. A neighbor contributes
     * only while its distance difference to the owner lies inside the configured blend band. Its
     * contribution reaches zero smoothly at that band edge. Consequently a neighbor can enter or
     * leave the active set without changing the generated height discontinuously.</p>
     *
     * @param worldX world X coordinate
     * @param worldZ world Z coordinate
     * @param blendWidth normalized boundary blend width in {@code (0, 1]}
     * @return deterministic multi-region blend sample
     */
    public TerrainRegionBlendSample sampleBlend(double worldX, double worldZ, double blendWidth) {
        return sampleBlend(worldX, worldZ, blendWidth, sample(worldX, worldZ));
    }

    /**
     * Resolves a continuous multi-region blend from an already known owning-region sample.
     *
     * <p>This overload is intended for hot paths that first check {@link TerrainRegionSample#edge()}
     * and only request the more expensive neighborhood blend close to a region boundary.</p>
     *
     * @param worldX world X coordinate
     * @param worldZ world Z coordinate
     * @param blendWidth normalized boundary blend width in {@code (0, 1]}
     * @param primary already resolved owning-region sample for the same coordinate
     * @return deterministic multi-region blend sample
     */
    public TerrainRegionBlendSample sampleBlend(
            double worldX,
            double worldZ,
            double blendWidth,
            TerrainRegionSample primary) {
        if (!Double.isFinite(blendWidth) || blendWidth <= 0.0D || blendWidth > 1.0D) {
            throw new IllegalArgumentException("blendWidth must be finite and in (0, 1]");
        }
        if (primary == null) {
            throw new NullPointerException("primary");
        }
        double x = worldX * frequency;
        double z = worldZ * frequency;
        int ownerX = primary.cellX();
        int ownerZ = primary.cellZ();
        double ownerDistance = distanceSq(x, z, pointX(ownerX, ownerZ), pointZ(ownerX, ownerZ));

        double[] compactIds = new double[NEIGHBORHOOD_SIZE];
        double[] compactWeights = new double[NEIGHBORHOOD_SIZE];
        int active = 0;
        double sum = 0.0D;

        // Anchor the influence neighborhood to the owning Voronoi cell instead of the block-space
        // grid cell. A two-cell guard band keeps entering/leaving candidates at zero influence when
        // ownership changes, avoiding seams at triple junctions and ordinary grid boundaries.
        for (int cz = ownerZ - 2; cz <= ownerZ + 2; cz++) {
            for (int cx = ownerX - 2; cx <= ownerX + 2; cx++) {
                double id = selector(cx, cz);
                double score;
                if (cx == ownerX && cz == ownerZ) {
                    score = 1.0D;
                } else {
                    double distance = distanceSq(x, z, pointX(cx, cz), pointZ(cx, cz));
                    score = neighborScore(ownerDistance, distance, blendWidth);
                }
                if (score <= 0.0D) {
                    continue;
                }
                compactIds[active] = id;
                compactWeights[active] = score;
                sum += score;
                active++;
            }
        }
        if (active == 0) {
            throw new IllegalStateException("terrain blend did not retain its owning region");
        }
        for (int index = 0; index < active; index++) {
            compactWeights[index] /= sum;
        }
        return new TerrainRegionBlendSample(primary, compactIds, compactWeights, active);
    }

    private double neighborScore(double ownerDistance, double neighborDistance, double blendWidth) {
        double edge = normalizedEdge(ownerDistance, neighborDistance);
        if (edge >= blendWidth) {
            return 0.0D;
        }
        double alpha = Maths.smooth(Maths.clamp(edge / blendWidth, 0.0D, 1.0D));
        return 1.0D - alpha;
    }

    private static double normalizedEdge(double ownerDistance, double neighborDistance) {
        double delta = Math.max(0.0D, Math.sqrt(neighborDistance) - Math.sqrt(ownerDistance));
        return Maths.clamp(delta / EDGE_NORMALIZER, 0.0D, 1.0D);
    }

    private double pointX(int cellX, int cellZ) {
        return cellX + 0.5D + (unitValue(POINT_X_SALT, cellX, cellZ) - 0.5D) * jitter;
    }

    private double pointZ(int cellX, int cellZ) {
        return cellZ + 0.5D + (unitValue(POINT_Z_SALT, cellX, cellZ) - 0.5D) * jitter;
    }

    private double selector(int cellX, int cellZ) {
        return unitValue(ID_SALT, cellX, cellZ);
    }

    private double unitValue(long salt, int cellX, int cellZ) {
        return 0.5D + NoiseMath.value(seed ^ salt, cellX, cellZ) * 0.5D;
    }

    private static double distanceSq(double x, double z, double px, double pz) {
        double dx = x - px;
        double dz = z - pz;
        return dx * dx + dz * dz;
    }
}
