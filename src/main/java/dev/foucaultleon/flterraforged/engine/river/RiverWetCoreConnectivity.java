package dev.foucaultleon.flterraforged.engine.river;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import dev.foucaultleon.flterraforged.engine.cell.CellLookup;
import dev.foucaultleon.flterraforged.engine.internal.Maths;
import java.util.Objects;

/**
 * Preserves topological continuity of the materialized wet core of refined river paths.
 *
 * <p>The wrapper has two responsibilities. First, it closes one- or two-column dry barriers that
 * can remain when the ordinary river model refuses an implausibly deep local incision. Second, it
 * gives receiving water bodies authority over the final water level at a river mouth. Open ocean
 * therefore wins over a river profile, and a material lake wins over the incoming river while a
 * genuine terrain-backed drop immediately upstream is retained as a waterfall.</p>
 *
 * <p>R39 extends receiver authority across narrow wet-core connectors even when the exact connector
 * column is not classified as {@code lake_shore}. Lake probes use the cached lake-only field instead
 * of recursively re-running river selection. Non-shore bridges require corroborating lake samples
 * in the same nearest probe ring, preventing a river that merely runs beside a lake from being
 * flattened to the lake surface.</p>
 *
 * <p>All corrections happen in Engine space before Minecraft materialization. No block-provider or
 * platform-specific information is required, so full-block and variable-height materializers see
 * exactly the same hydraulic semantics.</p>
 */
public final class RiverWetCoreConnectivity implements CellLookup {

    private static final double WET_CHANNEL_RADIUS = 0.78D;
    private static final double GUARANTEED_CORE_FRACTION = 0.30D;
    private static final double MINIMUM_CORE_RADIUS = 1.25D;
    private static final double FRINGE_MAXIMUM_CORRECTION = 8.0D;
    private static final double MINIMUM_WATER_DEPTH = 1.10D;
    private static final double CHANNEL_MATCH_EPSILON = 1.0E-6D;
    private static final double RECEIVER_SELECTION_EPSILON = 1.0E-6D;
    private static final double OPEN_WATER_CONTINENT_EDGE = 0.14D;
    private static final double OPEN_WATER_HEIGHT_MARGIN = 3.0D;
    private static final double WATERFALL_MINIMUM_WATER_DROP = 1.25D;
    private static final double WATERFALL_MINIMUM_TERRAIN_HEAD = 2.50D;
    private static final int LAKE_BRIDGE_MAX_PROBE = 4;
    private static final int[] RECEIVER_PROBES = {1, 2, 4, 8};
    private static final int[] PROBE_X = {-1, 1, 0, 0, -1, 1, -1, 1};
    private static final int[] PROBE_Z = {0, 0, -1, 1, -1, -1, 1, 1};

    private final EngineContext world;
    private final RiverModel delegate;

    /**
     * Creates a connectivity- and receiver-aware river lookup.
     *
     * @param world immutable world context
     * @param delegate fully configured river model
     */
    public RiverWetCoreConnectivity(EngineContext world, RiverModel delegate) {
        this.world = Objects.requireNonNull(world, "world");
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /** {@inheritDoc} */
    @Override
    public void lookup(int x, int z, Cell target) {
        Objects.requireNonNull(target, "target");
        delegate.lookup(x, z, target);

        if (target.lake) {
            return;
        }
        if (Double.isFinite(target.riverWaterSurfaceHeight)) {
            applyReceivingWaterAuthority(x, z, target);
            return;
        }
        if (target.lakeShore) {
            return;
        }

        RiverHit hit = delegate.nearest(x, z);
        if (!hit.present()
                || !Double.isFinite(hit.waterSurfaceHeight())
                || !matchesSelectedChannel(hit, target)) {
            return;
        }

        double halfWidth = Math.max(0.5D, hit.width() * 0.5D);
        double wetRadius = halfWidth * WET_CHANNEL_RADIUS;
        if (hit.distance() > wetRadius) {
            return;
        }

        double coreRadius = Math.max(MINIMUM_CORE_RADIUS, halfWidth * GUARANTEED_CORE_FRACTION);
        double targetBed = hit.waterSurfaceHeight() - minimumDepth(hit.waterSurfaceHeight());
        double requiredCorrection = Math.max(0.0D, target.height - targetBed);
        if (hit.distance() > coreRadius && requiredCorrection > FRINGE_MAXIMUM_CORRECTION) {
            return;
        }

        double finalHeight = Maths.clamp(
                Math.min(target.height, targetBed),
                world.minY() + 1.0D,
                world.maxYExclusive() - 2.0D);
        if (hit.waterSurfaceHeight() <= finalHeight + 0.05D) {
            return;
        }

        target.height = finalHeight;
        target.riverDistance = hit.distance();
        target.riverWidth = hit.width();
        target.riverDepth = hit.waterSurfaceHeight() - finalHeight;
        target.riverWaterSurfaceHeight = hit.waterSurfaceHeight();
        target.riverFlow = hit.flow();
        target.riverMask = Maths.smooth(Maths.clamp(hit.distance() / halfWidth, 0.0D, 1.0D));
        applyReceivingWaterAuthority(x, z, target);
    }

    private void applyReceivingWaterAuthority(int x, int z, Cell target) {
        double currentLevel = target.riverWaterSurfaceHeight;
        if (!Double.isFinite(currentLevel)) {
            return;
        }

        double receiverLevel = Double.NaN;
        int receiverPriority = 0;

        if (isOpenOceanReceiver(target)) {
            receiverLevel = world.seaLevel();
            receiverPriority = 3;
        }

        int lakeProbeLimit = target.lakeShore
                ? RECEIVER_PROBES[RECEIVER_PROBES.length - 1]
                : isGuaranteedWetCore(target) ? LAKE_BRIDGE_MAX_PROBE : 0;
        if (lakeProbeLimit > 0 && receiverPriority < 2) {
            LakeReceiver lake = nearbyLakeReceiver(x, z, currentLevel, lakeProbeLimit);
            boolean corroborated = target.lakeShore || lake.samples() >= 2;
            if (corroborated && Double.isFinite(lake.level())) {
                receiverLevel = lake.level();
                receiverPriority = 2;
            }
        }

        if (receiverPriority == 0 || !Double.isFinite(receiverLevel)) {
            return;
        }
        if (preserveWaterfallApproach(target, currentLevel, receiverLevel)) {
            return;
        }

        double bed = Maths.clamp(
                Math.min(target.height, receiverLevel - minimumDepth(receiverLevel)),
                world.minY() + 1.0D,
                world.maxYExclusive() - 2.0D);
        target.height = bed;
        target.riverWaterSurfaceHeight = receiverLevel;
        target.riverDepth = Math.max(MINIMUM_WATER_DEPTH, receiverLevel - bed);
    }

    private boolean isOpenOceanReceiver(Cell target) {
        return target.continentEdge <= OPEN_WATER_CONTINENT_EDGE
                && target.heightErosion <= world.seaLevel() + OPEN_WATER_HEIGHT_MARGIN;
    }

    private static boolean isGuaranteedWetCore(Cell target) {
        if (!Double.isFinite(target.riverDistance) || !Double.isFinite(target.riverWidth)) {
            return false;
        }
        double halfWidth = Math.max(0.5D, target.riverWidth * 0.5D);
        double coreRadius = Math.max(MINIMUM_CORE_RADIUS, halfWidth * GUARANTEED_CORE_FRACTION);
        return target.riverDistance <= coreRadius + CHANNEL_MATCH_EPSILON;
    }

    private LakeReceiver nearbyLakeReceiver(
            int x,
            int z,
            double currentLevel,
            int maximumDistance) {
        for (int distance : RECEIVER_PROBES) {
            if (distance > maximumDistance) {
                break;
            }
            double bestLevel = Double.NaN;
            double bestInterior = Double.NEGATIVE_INFINITY;
            double bestDelta = Double.POSITIVE_INFINITY;
            int samples = 0;
            for (int direction = 0; direction < PROBE_X.length; direction++) {
                LakeHit hit = delegate.lake(
                        x + PROBE_X[direction] * distance,
                        z + PROBE_Z[direction] * distance);
                if (!hit.materialWater() || !Double.isFinite(hit.waterSurfaceHeight())) {
                    continue;
                }
                samples++;
                double interior = hit.shoreDistance();
                double delta = Math.abs(hit.waterSurfaceHeight() - currentLevel);
                boolean deeper = interior > bestInterior + RECEIVER_SELECTION_EPSILON;
                boolean sameInterior = Math.abs(interior - bestInterior) <= RECEIVER_SELECTION_EPSILON;
                boolean closer = delta < bestDelta - RECEIVER_SELECTION_EPSILON;
                boolean sameDelta = Math.abs(delta - bestDelta) <= RECEIVER_SELECTION_EPSILON;
                boolean lowerTie = !Double.isFinite(bestLevel)
                        || hit.waterSurfaceHeight() < bestLevel;
                if (deeper || (sameInterior && (closer || (sameDelta && lowerTie)))) {
                    bestLevel = hit.waterSurfaceHeight();
                    bestInterior = interior;
                    bestDelta = delta;
                }
            }
            if (samples > 0) {
                return new LakeReceiver(bestLevel, samples);
            }
        }
        return LakeReceiver.NONE;
    }

    private static boolean preserveWaterfallApproach(
            Cell target,
            double currentLevel,
            double receiverLevel) {
        return currentLevel - receiverLevel >= WATERFALL_MINIMUM_WATER_DROP
                && target.heightErosion - receiverLevel >= WATERFALL_MINIMUM_TERRAIN_HEAD;
    }

    private static boolean matchesSelectedChannel(RiverHit hit, Cell target) {
        return Double.isFinite(target.riverDistance)
                && Double.isFinite(target.riverWidth)
                && Double.isFinite(target.riverFlow)
                && nearlyEqual(hit.distance(), target.riverDistance)
                && nearlyEqual(hit.width(), target.riverWidth)
                && nearlyEqual(hit.flow(), target.riverFlow);
    }

    private static boolean nearlyEqual(double first, double second) {
        double scale = Math.max(1.0D, Math.max(Math.abs(first), Math.abs(second)));
        return Math.abs(first - second) <= CHANNEL_MATCH_EPSILON * scale;
    }

    private double minimumDepth(double waterSurfaceHeight) {
        double altitude = waterSurfaceHeight;
        double target;
        if (altitude <= world.seaLevel() + 1.0D) {
            target = 3.50D;
        } else if (altitude <= 90.0D) {
            double alpha = Maths.smooth(Maths.clamp(
                    (altitude - world.seaLevel() - 1.0D)
                            / Math.max(1.0D, 90.0D - world.seaLevel() - 1.0D),
                    0.0D,
                    1.0D));
            target = Maths.lerp(3.50D, 2.75D, alpha);
        } else if (altitude <= 120.0D) {
            double alpha = Maths.smooth(Maths.clamp((altitude - 90.0D) / 30.0D, 0.0D, 1.0D));
            target = Maths.lerp(2.75D, 2.25D, alpha);
        } else {
            double alpha = Maths.smooth(Maths.clamp((altitude - 120.0D) / 80.0D, 0.0D, 1.0D));
            target = Maths.lerp(2.25D, 1.75D, alpha);
        }
        return Math.max(MINIMUM_WATER_DEPTH, target);
    }

    private record LakeReceiver(double level, int samples) {
        private static final LakeReceiver NONE = new LakeReceiver(Double.NaN, 0);
    }
}
