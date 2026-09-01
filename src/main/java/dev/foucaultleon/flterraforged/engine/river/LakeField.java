package dev.foucaultleon.flterraforged.engine.river;

import dev.foucaultleon.flterraforged.engine.internal.Maths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Immutable basin-aware depression field used to materialize irregular ponds and lakes.
 *
 * <p>Priority-flood spill elevations are first grouped into connected basins. Each basin owns one
 * constant water level. Continuous terrain height and deterministic edge noise shape only the
 * shoreline; they never interpolate or tilt the lake surface itself.</p>
 */
public final class LakeField {

    private static final double BASIN_EPSILON = 1.0E-6D;
    private static final double WATER_LEVEL_OFFSET = 0.25D;
    private static final int[] NEIGHBOR_X = {-1, 0, 1, -1, 1, -1, 0, 1};
    private static final int[] NEIGHBOR_Z = {-1, -1, -1, 0, 0, 1, 1, 1};

    private final long seed;
    private final int originX;
    private final int originZ;
    private final int spacing;
    private final int width;
    private final double[] originalHeight;
    private final double[] filledHeight;
    private final int[] basinIds;
    private final double[] basinWaterLevels;
    private final int[] basinNodeCounts;
    private final double minimumDepth;
    private final double shoreBlend;
    private final int seaLevel;

    /**
     * Creates an immutable lake field.
     *
     * @param seed hydrology seed
     * @param originX grid origin X
     * @param originZ grid origin Z
     * @param spacing grid spacing
     * @param width node count per axis
     * @param originalHeight original drainage-surface heights
     * @param filledHeight depression-filled hydrology heights
     * @param minimumDepth minimum fill depth for inland water
     * @param shoreBlend shore-softening depth interval
     * @param seaLevel world sea level
     */
    public LakeField(
            long seed,
            int originX,
            int originZ,
            int spacing,
            int width,
            double[] originalHeight,
            double[] filledHeight,
            double minimumDepth,
            double shoreBlend,
            int seaLevel) {
        this.seed = seed;
        this.originX = originX;
        this.originZ = originZ;
        this.spacing = spacing;
        this.width = width;
        this.originalHeight = Objects.requireNonNull(originalHeight, "originalHeight").clone();
        this.filledHeight = Objects.requireNonNull(filledHeight, "filledHeight").clone();
        this.minimumDepth = minimumDepth;
        this.shoreBlend = shoreBlend;
        this.seaLevel = seaLevel;
        if (this.originalHeight.length != width * width || this.filledHeight.length != width * width) {
            throw new IllegalArgumentException("Lake-field arrays do not match grid width");
        }
        BasinData basins = identifyBasins();
        this.basinIds = basins.ids();
        this.basinWaterLevels = basins.waterLevels();
        this.basinNodeCounts = basins.nodeCounts();
    }

    /**
     * Samples the depression-filled inland-water field.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return lake/pond hit or {@link LakeHit#NONE}
     */
    public LakeHit sample(double x, double z) {
        double gridX = (x - originX) / spacing;
        double gridZ = (z - originZ) / spacing;
        int gx = (int) Math.floor(gridX);
        int gz = (int) Math.floor(gridZ);
        if (gx < 0 || gz < 0 || gx >= width - 1 || gz >= width - 1) {
            return LakeHit.NONE;
        }

        double tx = gridX - gx;
        double tz = gridZ - gz;
        int basinId = dominantBasin(gx, gz, tx, tz);
        if (basinId < 0) {
            return LakeHit.NONE;
        }

        double waterSurface = basinWaterLevels[basinId];
        if (waterSurface <= seaLevel + 0.50D) {
            return LakeHit.NONE;
        }

        double original = bilinear(originalHeight, gx, gz, tx, tz);
        double geometricDepth = waterSurface - original;
        double shorelineNoise = smoothValueNoise(x * 0.035D, z * 0.035D) * Math.min(0.32D, shoreBlend * 0.24D);
        double effectiveDepth = geometricDepth + shorelineNoise;
        double shoreReach = Math.max(0.30D, shoreBlend * 0.45D);
        if (effectiveDepth <= -shoreReach) {
            return LakeHit.NONE;
        }

        if (effectiveDepth < minimumDepth) {
            double shoreInfluence = Maths.smooth(Maths.clamp(
                    (effectiveDepth + shoreReach) / (minimumDepth + shoreReach),
                    0.0D,
                    1.0D));
            return new LakeHit(
                    LakeZone.SHORE,
                    shoreInfluence * 0.34D,
                    waterSurface,
                    0.0D);
        }

        double coreStart = minimumDepth + shoreBlend * 0.72D;
        double waterInfluence = Maths.smooth(Maths.clamp(
                (effectiveDepth - minimumDepth) / Math.max(0.001D, coreStart - minimumDepth),
                0.0D,
                1.0D));
        if (effectiveDepth < coreStart) {
            double naturalDepth = Math.max(0.0D, effectiveDepth * 0.72D);
            double desiredDepth = Math.max(
                    naturalDepth,
                    basinMinimumDepth(basinId, waterSurface));
            return new LakeHit(
                    LakeZone.SHALLOW,
                    0.35D + waterInfluence * 0.30D,
                    waterSurface,
                    desiredDepth);
        }

        double deepInfluence = Maths.smooth(Maths.clamp(
                (effectiveDepth - coreStart) / Math.max(0.001D, shoreBlend * 1.35D),
                0.0D,
                1.0D));
        // Core water should read as an actual lake rather than a uniformly shallow flooded
        // depression. Preserve naturally deep basins and allow the central bowl to cut several
        // additional blocks below the spill-controlled water level. Edge/shallow zones remain
        // deliberately gentle.
        double naturalDepth = Math.max(0.0D, geometricDepth);
        double targetCoreDepth = basinMinimumDepth(basinId, waterSurface)
                + 1.50D
                + deepInfluence * 7.00D;
        double desiredDepth = Math.min(14.0D, Math.max(naturalDepth, targetCoreDepth));
        return new LakeHit(
                LakeZone.CORE,
                0.65D + deepInfluence * 0.35D,
                waterSurface,
                desiredDepth);
    }

    private BasinData identifyBasins() {
        int[] ids = new int[originalHeight.length];
        Arrays.fill(ids, -1);
        List<Double> levels = new ArrayList<>();
        List<Integer> nodeCounts = new ArrayList<>();
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        for (int index = 0; index < originalHeight.length; index++) {
            if (ids[index] >= 0 || !isDepressionNode(index)) {
                continue;
            }
            int basinId = levels.size();
            double spillLevel = filledHeight[index];
            levels.add(spillLevel - WATER_LEVEL_OFFSET);
            ids[index] = basinId;
            queue.add(index);
            int nodeCount = 0;

            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                nodeCount++;
                int gx = current % width;
                int gz = current / width;
                for (int direction = 0; direction < NEIGHBOR_X.length; direction++) {
                    int nx = gx + NEIGHBOR_X[direction];
                    int nz = gz + NEIGHBOR_Z[direction];
                    if (nx < 0 || nz < 0 || nx >= width || nz >= width) {
                        continue;
                    }
                    int candidate = nz * width + nx;
                    if (ids[candidate] >= 0 || !isDepressionNode(candidate)) {
                        continue;
                    }
                    if (Math.abs(filledHeight[candidate] - spillLevel) > BASIN_EPSILON) {
                        continue;
                    }
                    ids[candidate] = basinId;
                    queue.addLast(candidate);
                }
            }
            nodeCounts.add(nodeCount);
        }

        double[] waterLevels = new double[levels.size()];
        int[] counts = new int[nodeCounts.size()];
        for (int index = 0; index < levels.size(); index++) {
            waterLevels[index] = levels.get(index);
            counts[index] = nodeCounts.get(index);
        }
        return new BasinData(ids, waterLevels, counts);
    }

    private double basinMinimumDepth(int basinId, double waterSurface) {
        double size = Maths.smooth(Maths.clamp(
                (basinNodeCounts[basinId] - 1.0D) / 8.0D,
                0.0D,
                1.0D));
        double altitude;
        if (waterSurface <= seaLevel + 2.0D) {
            altitude = 3.50D;
        } else if (waterSurface <= 90.0D) {
            double alpha = Maths.smooth(Maths.clamp(
                    (waterSurface - seaLevel - 2.0D)
                            / Math.max(1.0D, 90.0D - seaLevel - 2.0D),
                    0.0D,
                    1.0D));
            altitude = Maths.lerp(3.50D, 2.50D, alpha);
        } else if (waterSurface <= 120.0D) {
            double alpha = Maths.smooth(Maths.clamp((waterSurface - 90.0D) / 30.0D, 0.0D, 1.0D));
            altitude = Maths.lerp(2.50D, 2.00D, alpha);
        } else {
            altitude = 1.75D;
        }
        return Maths.lerp(1.10D, altitude, size);
    }

    private boolean isDepressionNode(int index) {
        return filledHeight[index] > seaLevel + 0.75D
                && filledHeight[index] - originalHeight[index] > BASIN_EPSILON;
    }

    private int dominantBasin(int gx, int gz, double tx, double tz) {
        int[] candidates = {
            basinIds[gz * width + gx],
            basinIds[gz * width + gx + 1],
            basinIds[(gz + 1) * width + gx],
            basinIds[(gz + 1) * width + gx + 1]
        };
        double[] weights = {
            (1.0D - tx) * (1.0D - tz),
            tx * (1.0D - tz),
            (1.0D - tx) * tz,
            tx * tz
        };

        int best = -1;
        double bestWeight = 0.0D;
        for (int candidateIndex = 0; candidateIndex < candidates.length; candidateIndex++) {
            int candidate = candidates[candidateIndex];
            if (candidate < 0) {
                continue;
            }
            double weight = 0.0D;
            for (int index = 0; index < candidates.length; index++) {
                if (candidates[index] == candidate) {
                    weight += weights[index];
                }
            }
            if (weight > bestWeight) {
                best = candidate;
                bestWeight = weight;
            }
        }
        return best;
    }

    private double bilinear(double[] values, int gx, int gz, double tx, double tz) {
        double a = values[gz * width + gx];
        double b = values[gz * width + gx + 1];
        double c = values[(gz + 1) * width + gx];
        double d = values[(gz + 1) * width + gx + 1];
        double top = Maths.lerp(a, b, tx);
        double bottom = Maths.lerp(c, d, tx);
        return Maths.lerp(top, bottom, tz);
    }

    private double smoothValueNoise(double x, double z) {
        int xi = (int) Math.floor(x);
        int zi = (int) Math.floor(z);
        double tx = Maths.smooth(x - xi);
        double tz = Maths.smooth(z - zi);
        double a = hash(xi, zi);
        double b = hash(xi + 1, zi);
        double c = hash(xi, zi + 1);
        double d = hash(xi + 1, zi + 1);
        return Maths.lerp(Maths.lerp(a, b, tx), Maths.lerp(c, d, tx), tz);
    }

    private double hash(int x, int z) {
        long value = seed;
        value ^= (long) x * 0x9E3779B97F4A7C15L;
        value ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return ((value & 0x1FFFFFL) / (double) 0x1FFFFF) * 2.0D - 1.0D;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LakeField that)) {
            return false;
        }
        return seed == that.seed
                && originX == that.originX
                && originZ == that.originZ
                && spacing == that.spacing
                && width == that.width
                && Double.doubleToLongBits(minimumDepth) == Double.doubleToLongBits(that.minimumDepth)
                && Double.doubleToLongBits(shoreBlend) == Double.doubleToLongBits(that.shoreBlend)
                && seaLevel == that.seaLevel
                && Arrays.equals(originalHeight, that.originalHeight)
                && Arrays.equals(filledHeight, that.filledHeight);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        int result = Objects.hash(
                seed, originX, originZ, spacing, width, minimumDepth, shoreBlend, seaLevel);
        result = 31 * result + Arrays.hashCode(originalHeight);
        result = 31 * result + Arrays.hashCode(filledHeight);
        return result;
    }

    private record BasinData(int[] ids, double[] waterLevels, int[] nodeCounts) {
    }
}
