package dev.foucaultleon.flterraforged.engine.river;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import dev.foucaultleon.flterraforged.engine.cell.CellLookup;
import dev.foucaultleon.flterraforged.engine.internal.Maths;
import java.util.Objects;

/**
 * Preserves topological continuity of the materialized wet core of refined river paths.
 *
 * <p>The wrapper closes one- or two-column dry barriers that can remain when the ordinary river
 * model refuses an implausibly deep local incision. It also aligns an approaching river with a
 * nearby receiving-water level without making the approach bed deeper than the already-shaped river
 * channel. Final lake and ocean ownership is handled by {@link ReceivingWaterOverlay} after this
 * stage.</p>
 *
 * <p>R42 additionally resolves short confluence seams in Engine space. When neighboring wet-core
 * probes expose a higher-flow channel at a compatible level, that main stem owns the shared water
 * plane. Lake and ocean receivers still have higher priority. Receiver water levels switch
 * authoritatively, while the river bed is raised toward the receiving bed through a bounded mouth
 * blend. This prevents narrow trenches from continuing into lakes or seas without introducing a
 * post-generation Minecraft repair pass.</p>
 *
 * <p>All corrections happen before Minecraft materialization. No block-provider or platform-specific
 * information is required, so full-block and variable-height materializers see exactly the same
 * continuous hydraulic semantics.</p>
 */
public final class RiverWetCoreConnectivity implements CellLookup {

    private static final double WET_CHANNEL_RADIUS = 0.78D;
    private static final double GUARANTEED_CORE_FRACTION = 0.30D;
    private static final double MINIMUM_CORE_RADIUS = 1.25D;
    private static final double FRINGE_MAXIMUM_CORRECTION = 8.0D;
    private static final double MINIMUM_WATER_DEPTH = 1.10D;
    private static final double DRY_SHORE_MAXIMUM_DEPTH = 0.05D;
    private static final double CHANNEL_MATCH_EPSILON = 1.0E-6D;
    private static final double RECEIVER_SELECTION_EPSILON = 1.0E-6D;
    private static final double OPEN_WATER_CONTINENT_EDGE = 0.14D;
    private static final double OPEN_WATER_HEIGHT_MARGIN = 3.0D;
    private static final double WATERFALL_MINIMUM_WATER_DROP = 1.25D;
    private static final double WATERFALL_MINIMUM_TERRAIN_HEAD = 2.50D;
    private static final double MAIN_STEM_MAXIMUM_LEVEL_DELTA = 1.50D;
    private static final double MAIN_STEM_MINIMUM_FLOW_GAIN = 1.0E-6D;
    private static final double MOUTH_BLEND_DISTANCE = 12.0D;
    private static final int MAIN_STEM_MAX_PROBE = 2;
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
        if (target.lakeShore && target.riverDepth <= DRY_SHORE_MAXIMUM_DEPTH) {
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
        double receiverBed = Double.NaN;
        double receiverDistance = Double.POSITIVE_INFINITY;
        int receiverPriority = 0;
        double naturalBed = Double.isFinite(target.heightErosion)
                ? target.heightErosion
                : target.height;

        if (isGuaranteedWetCore(target) && Double.isFinite(target.riverFlow)) {
            MainStemReceiver mainStem = nearbyMainStemReceiver(x, z, target);
            if (Double.isFinite(mainStem.level())) {
                receiverLevel = mainStem.level();
                receiverBed = Math.min(
                        naturalBed,
                        mainStem.level() - minimumDepth(mainStem.level()));
                receiverDistance = mainStem.distance();
                receiverPriority = ResolvedWaterOwner.RIVER.priority();
            }
        }

        int lakeProbeLimit = target.lakeShore
                ? RECEIVER_PROBES[RECEIVER_PROBES.length - 1]
                : isGuaranteedWetCore(target) ? LAKE_BRIDGE_MAX_PROBE : 0;
        if (lakeProbeLimit > 0 && receiverPriority < ResolvedWaterOwner.LAKE.priority()) {
            LakeReceiver lake = nearbyLakeReceiver(x, z, currentLevel, lakeProbeLimit);
            boolean corroborated = target.lakeShore || lake.samples() >= 2;
            if (corroborated && Double.isFinite(lake.level())) {
                receiverLevel = lake.level();
                receiverBed = Math.min(
                        naturalBed,
                        lake.level() - Math.max(MINIMUM_WATER_DEPTH, lake.minimumDepth()));
                receiverDistance = lake.distance();
                receiverPriority = ResolvedWaterOwner.LAKE.priority();
            }
        }

        if (isOpenOceanReceiver(target)) {
            receiverLevel = world.seaLevel();
            // The approach may rise toward the natural marine floor, but it must never be forced
            // down to the ordinary deep-river profile. The final ocean overlay owns the interior.
            receiverBed = Math.min(
                    naturalBed,
                    receiverLevel - MINIMUM_WATER_DEPTH);
            receiverDistance = 0.0D;
            receiverPriority = ResolvedWaterOwner.OCEAN.priority();
        }

        if (receiverPriority == 0 || !Double.isFinite(receiverLevel)) {
            return;
        }
        if (preserveWaterfallApproach(target, currentLevel, receiverLevel)) {
            return;
        }

        double fallbackBed = receiverLevel - MINIMUM_WATER_DEPTH;
        double desiredBed = Double.isFinite(receiverBed) ? receiverBed : fallbackBed;
        double blend = mouthBlend(receiverDistance);
        double blendedBed = Maths.lerp(target.height, desiredBed, blend);
        // Receiver alignment may fill/raise an over-incised river mouth, never deepen it further.
        double bed = Maths.clamp(
                Math.max(target.height, blendedBed),
                world.minY() + 1.0D,
                world.maxYExclusive() - 2.0D);
        if (receiverLevel <= bed + 0.05D) {
            return;
        }
        target.height = bed;
        target.riverWaterSurfaceHeight = receiverLevel;
        target.riverDepth = receiverLevel - bed;
    }

    private MainStemReceiver nearbyMainStemReceiver(int x, int z, Cell target) {
        double currentLevel = target.riverWaterSurfaceHeight;
        double currentFlow = target.riverFlow;
        MainStemReceiver best = MainStemReceiver.NONE;
        for (int distance = 1; distance <= MAIN_STEM_MAX_PROBE; distance++) {
            for (int direction = 0; direction < PROBE_X.length; direction++) {
                RiverHit hit = delegate.nearest(
                        x + PROBE_X[direction] * distance,
                        z + PROBE_Z[direction] * distance);
                if (!hit.present()
                        || !Double.isFinite(hit.waterSurfaceHeight())
                        || !Double.isFinite(hit.flow())
                        || hit.flow() <= currentFlow + MAIN_STEM_MINIMUM_FLOW_GAIN) {
                    continue;
                }
                double halfWidth = Math.max(0.5D, hit.width() * 0.5D);
                if (hit.distance() > halfWidth * WET_CHANNEL_RADIUS + CHANNEL_MATCH_EPSILON) {
                    continue;
                }
                if (Math.abs(hit.waterSurfaceHeight() - currentLevel)
                        > MAIN_STEM_MAXIMUM_LEVEL_DELTA) {
                    continue;
                }
                if (betterMainStem(hit, distance, best)) {
                    best = new MainStemReceiver(
                            hit.waterSurfaceHeight(),
                            hit.flow(),
                            hit.width(),
                            distance);
                }
            }
            if (Double.isFinite(best.level())) {
                return best;
            }
        }
        return best;
    }

    private static boolean betterMainStem(
            RiverHit candidate,
            int distance,
            MainStemReceiver current) {
        if (!Double.isFinite(current.level())) {
            return true;
        }
        if (candidate.flow() > current.flow() + RECEIVER_SELECTION_EPSILON) {
            return true;
        }
        if (Math.abs(candidate.flow() - current.flow()) > RECEIVER_SELECTION_EPSILON) {
            return false;
        }
        if (candidate.width() > current.width() + RECEIVER_SELECTION_EPSILON) {
            return true;
        }
        if (Math.abs(candidate.width() - current.width()) > RECEIVER_SELECTION_EPSILON) {
            return false;
        }
        if (distance < current.distance()) {
            return true;
        }
        return distance == current.distance()
                && candidate.waterSurfaceHeight() < current.level();
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
            double bestMinimumDepth = Double.NaN;
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
                    bestMinimumDepth = hit.minimumDepth();
                    bestInterior = interior;
                    bestDelta = delta;
                }
            }
            if (samples > 0) {
                return new LakeReceiver(bestLevel, bestMinimumDepth, samples, distance);
            }
        }
        return LakeReceiver.NONE;
    }

    private static double mouthBlend(double receiverDistance) {
        if (!Double.isFinite(receiverDistance) || receiverDistance <= 0.0D) {
            return 1.0D;
        }
        return Maths.smooth(Maths.clamp(
                1.0D - receiverDistance / MOUTH_BLEND_DISTANCE,
                0.0D,
                1.0D));
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

    private record LakeReceiver(
            double level,
            double minimumDepth,
            int samples,
            int distance) {
        private static final LakeReceiver NONE = new LakeReceiver(
                Double.NaN,
                Double.NaN,
                0,
                Integer.MAX_VALUE);
    }

    private record MainStemReceiver(
            double level,
            double flow,
            double width,
            int distance) {
        private static final MainStemReceiver NONE = new MainStemReceiver(
                Double.NaN,
                Double.NaN,
                0.0D,
                Integer.MAX_VALUE);
    }
}
