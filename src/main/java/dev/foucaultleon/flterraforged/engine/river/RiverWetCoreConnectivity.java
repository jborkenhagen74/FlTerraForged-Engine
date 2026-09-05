package dev.foucaultleon.flterraforged.engine.river;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import dev.foucaultleon.flterraforged.engine.cell.CellLookup;
import dev.foucaultleon.flterraforged.engine.internal.Maths;
import java.util.Objects;

/**
 * Preserves topological continuity of the materialized wet core of refined river paths.
 *
 * <p>{@link RiverModel} deliberately refuses implausibly deep local incisions when a projected
 * channel crosses a residual ridge. At block resolution that safety valve can leave a one- or
 * two-column dry barrier across an otherwise continuous refined river path. This wrapper keeps the
 * wider bank/fringe safety behavior but guarantees a narrow hydraulic core along the winning
 * centerline. The correction is applied in Engine space before Minecraft materialization, so every
 * platform adapter and block provider receives the same connected watercourse semantics.</p>
 */
public final class RiverWetCoreConnectivity implements CellLookup {

    private static final double WET_CHANNEL_RADIUS = 0.78D;
    private static final double GUARANTEED_CORE_FRACTION = 0.30D;
    private static final double MINIMUM_CORE_RADIUS = 1.25D;
    private static final double FRINGE_MAXIMUM_CORRECTION = 8.0D;
    private static final double MINIMUM_WATER_DEPTH = 1.10D;
    private static final double CHANNEL_MATCH_EPSILON = 1.0E-6D;

    private final EngineContext world;
    private final RiverModel delegate;

    /**
     * Creates a connectivity-preserving river lookup.
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

        if (target.lake || target.lakeShore || Double.isFinite(target.riverWaterSurfaceHeight)) {
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
    }

    private static boolean matchesSelectedChannel(RiverHit hit, Cell target) {
        // RiverModel already selected the surface-aligned winner and stores its horizontal
        // properties even when the local ridge guard keeps that column dry. The ordinary nearest
        // query is used only to recover the water-surface value that was intentionally suppressed
        // from the dry Cell. Requiring the same projected distance/width/flow prevents a deeper
        // crossing channel from being resurrected merely because it is geometrically closer in X/Z.
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
}
