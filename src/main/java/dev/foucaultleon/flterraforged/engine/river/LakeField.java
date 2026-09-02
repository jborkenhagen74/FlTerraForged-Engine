package dev.foucaultleon.flterraforged.engine.river;

import dev.foucaultleon.flterraforged.engine.internal.Maths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Immutable basin-aware depression field used to materialize irregular ponds and lakes.
 *
 * <p>Priority-flood spill elevations are first grouped into connected basins. Each basin owns one
 * constant water level. Continuous terrain height and deterministic edge noise shape only the
 * shoreline; they never interpolate or tilt the lake surface itself.</p>
 */
public final class LakeField {

    /** Horizontal dry-to-wet transition used by materializers around lake basins. */
    public static final double SHORE_TRANSITION_WIDTH = 10.0D;

    private static final double BASIN_EPSILON = 1.0E-6D;
    private static final double WATER_LEVEL_OFFSET = 0.25D;
    private static final double WATER_EDGE_INSET = 0.75D;
    private static final double SHORE_REFERENCE_GRADE = 0.18D;
    private static final double CORE_DISTANCE = 5.75D;
    private static final double DEEP_DISTANCE = 24.0D;
    private static final double EDGE_WATER_DEPTH = 1.25D;
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
    private final double[] basinInteriorDistance;
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
        this.basinInteriorDistance = computeBasinInteriorDistances();
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
        double depthDistance = effectiveDepth / SHORE_REFERENCE_GRADE;
        double topologyDistance = bilinearBasinDistance(gx, gz, tx, tz, basinId);
        double shoreDistance = Math.min(depthDistance, topologyDistance);
        if (shoreDistance <= -SHORE_TRANSITION_WIDTH) {
            return LakeHit.NONE;
        }

        if (shoreDistance < WATER_EDGE_INSET) {
            double shoreInfluence = Maths.smooth(Maths.clamp(
                    (shoreDistance + SHORE_TRANSITION_WIDTH)
                            / (SHORE_TRANSITION_WIDTH + WATER_EDGE_INSET),
                    0.0D,
                    1.0D));
            return new LakeHit(
                    LakeZone.SHORE,
                    shoreInfluence * 0.34D,
                    waterSurface,
                    0.0D,
                    shoreDistance);
        }

        double waterDistance = shoreDistance - WATER_EDGE_INSET;
        double bodyInfluence = Maths.smooth(Maths.clamp(
                waterDistance / CORE_DISTANCE,
                0.0D,
                1.0D));
        double deepInfluence = Maths.smooth(Maths.clamp(
                (waterDistance - CORE_DISTANCE) / (DEEP_DISTANCE - CORE_DISTANCE),
                0.0D,
                1.0D));
        double naturalDepth = Math.max(0.0D, geometricDepth);
        double bodyDepth = Maths.lerp(
                EDGE_WATER_DEPTH,
                basinMinimumDepth(basinId, waterSurface),
                bodyInfluence);
        double targetDepth = bodyDepth + deepInfluence * 8.0D;
        double desiredDepth = Math.min(14.0D, Math.max(naturalDepth, targetDepth));
        return new LakeHit(
                waterDistance >= CORE_DISTANCE ? LakeZone.CORE : LakeZone.SHALLOW,
                0.35D + bodyInfluence * 0.30D + deepInfluence * 0.35D,
                waterSurface,
                desiredDepth,
                shoreDistance);
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

    private double[] computeBasinInteriorDistances() {
        double[] distances = new double[basinIds.length];
        Arrays.fill(distances, Double.POSITIVE_INFINITY);
        PriorityQueue<DistanceNode> queue = new PriorityQueue<>(Comparator
                .comparingDouble(DistanceNode::distance)
                .thenComparingInt(DistanceNode::index));
        double boundaryDistance = spacing * 0.5D;
        for (int index = 0; index < basinIds.length; index++) {
            if (basinIds[index] < 0 || !isBasinBoundary(index)) {
                continue;
            }
            distances[index] = boundaryDistance;
            queue.add(new DistanceNode(index, boundaryDistance));
        }

        while (!queue.isEmpty()) {
            DistanceNode current = queue.remove();
            if (current.distance() > distances[current.index()] + BASIN_EPSILON) {
                continue;
            }
            int gx = current.index() % width;
            int gz = current.index() / width;
            int basinId = basinIds[current.index()];
            for (int direction = 0; direction < NEIGHBOR_X.length; direction++) {
                int nx = gx + NEIGHBOR_X[direction];
                int nz = gz + NEIGHBOR_Z[direction];
                if (nx < 0 || nz < 0 || nx >= width || nz >= width) {
                    continue;
                }
                int candidate = nz * width + nx;
                if (basinIds[candidate] != basinId) {
                    continue;
                }
                double step = NEIGHBOR_X[direction] == 0 || NEIGHBOR_Z[direction] == 0
                        ? spacing
                        : spacing * Math.sqrt(2.0D);
                double candidateDistance = current.distance() + step;
                if (candidateDistance + BASIN_EPSILON < distances[candidate]) {
                    distances[candidate] = candidateDistance;
                    queue.add(new DistanceNode(candidate, candidateDistance));
                }
            }
        }
        return distances;
    }

    private boolean isBasinBoundary(int index) {
        int basinId = basinIds[index];
        int gx = index % width;
        int gz = index / width;
        if (gx == 0 || gz == 0 || gx == width - 1 || gz == width - 1) {
            return true;
        }
        for (int direction = 0; direction < NEIGHBOR_X.length; direction++) {
            int candidate = (gz + NEIGHBOR_Z[direction]) * width
                    + gx + NEIGHBOR_X[direction];
            if (basinIds[candidate] != basinId) {
                return true;
            }
        }
        return false;
    }

    private double bilinearBasinDistance(
            int gx,
            int gz,
            double tx,
            double tz,
            int basinId) {
        int a = gz * width + gx;
        int b = a + 1;
        int c = (gz + 1) * width + gx;
        int d = c + 1;
        double outside = spacing * -0.5D;
        double top = Maths.lerp(
                basinIds[a] == basinId ? basinInteriorDistance[a] : outside,
                basinIds[b] == basinId ? basinInteriorDistance[b] : outside,
                tx);
        double bottom = Maths.lerp(
                basinIds[c] == basinId ? basinInteriorDistance[c] : outside,
                basinIds[d] == basinId ? basinInteriorDistance[d] : outside,
                tx);
        return Maths.lerp(top, bottom, tz);
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

    private record DistanceNode(int index, double distance) {
    }
}
