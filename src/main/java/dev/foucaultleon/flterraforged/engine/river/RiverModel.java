package dev.foucaultleon.flterraforged.engine.river;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.river.RiverSample;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import dev.foucaultleon.flterraforged.engine.cell.CellLookup;
import dev.foucaultleon.flterraforged.engine.internal.InlineSingleFlightCache;
import dev.foucaultleon.flterraforged.engine.internal.Maths;
import java.util.Objects;

/**
 * Thread-safe hydrology facade backed by cached immutable river maps.
 *
 * <p>Linear channels and depression-filled inland water are resolved together. Missing river maps
 * use synchronous single-flight ownership, so concurrent world-generation threads requesting the
 * same region share one direct computation instead of generating duplicate drainage graphs.</p>
 */
public final class RiverModel implements CellLookup {

    private static final double WET_CHANNEL_RADIUS = 0.78D;
    private static final double EDGE_WATER_DEPTH = 1.10D;
    private static final double MAXIMUM_BED_GRADE = 0.50D;
    private static final double MAXIMUM_RIDGE_CORRECTION = 3.0D;
    private static final double MINIMUM_BANK_TRANSITION = 8.0D;
    private static final double MAXIMUM_BANK_FLOW_EXTRA = 4.0D;
    private static final double BANK_FLOW_SCALE = 0.90D;
    private static final double MAXIMUM_BANK_GRADE = 0.55D;
    private static final double MAXIMUM_BANK_RISE_GRADE = 0.45D;
    private static final double BANK_WATER_GRADE_GUARD = 0.50D;

    private final EngineContext world;
    private final CellLookup erodedTerrain;
    private final RiverSettings settings;
    private final RivermapGenerator generator;
    private final InlineSingleFlightCache<Long, Rivermap> cache;

    /**
     * Creates a river model.
     *
     * @param seed hydrology seed
     * @param world immutable world context
     * @param erodedTerrain terrain stage after erosion and before river/lake incision
     * @param drainageTerrain terrain lookup used to build the coarse drainage topology
     * @param settings river settings
     */
    public RiverModel(
            long seed,
            EngineContext world,
            CellLookup erodedTerrain,
            CellLookup drainageTerrain,
            RiverSettings settings) {
        this(seed, world, erodedTerrain, drainageTerrain, null, settings);
    }

    /**
     * Creates a river model with a pre-hydrology climate lookup for runoff weighting.
     *
     * <p>The climate lookup must not depend on this river model. It is sampled only while building
     * drainage maps and allows dry catchments to contribute less runoff while preserving large
     * through-flowing rivers that originated in wetter terrain.</p>
     *
     * @param seed hydrology seed
     * @param world immutable world context
     * @param erodedTerrain terrain stage after erosion and before river/lake incision
     * @param drainageTerrain terrain lookup used to build the coarse drainage topology
     * @param drainageClimate pre-river climate lookup used to weight local runoff, or {@code null}
     * @param settings river settings
     */
    public RiverModel(
            long seed,
            EngineContext world,
            CellLookup erodedTerrain,
            CellLookup drainageTerrain,
            CellLookup drainageClimate,
            RiverSettings settings) {
        this.world = Objects.requireNonNull(world, "world");
        this.erodedTerrain = Objects.requireNonNull(erodedTerrain, "erodedTerrain");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.generator = new RivermapGenerator(
                seed,
                world,
                Objects.requireNonNull(drainageTerrain, "drainageTerrain"),
                drainageClimate,
                settings);
        this.cache = new InlineSingleFlightCache<>(settings.cacheSize());
    }

    /**
     * Creates a river model using the same lookup for drainage topology and final pre-river terrain.
     *
     * @param seed hydrology seed
     * @param world immutable world context
     * @param terrain terrain lookup before hydrology incision
     * @param settings river settings
     */
    public RiverModel(long seed, EngineContext world, CellLookup terrain, RiverSettings settings) {
        this(seed, world, terrain, terrain, settings);
    }

    /** {@inheritDoc} */
    @Override
    public void lookup(int x, int z, Cell target) {
        Objects.requireNonNull(target, "target");
        erodedTerrain.lookup(x, z, target);
        RiverHit river = nearestSurfaceAligned(x, z, target.heightErosion);
        LakeHit lake = nearestLake(x, z);

        double lakeIncision = 0.0D;
        double lakeBed = target.heightErosion;
        if (lake.materialWater()) {
            lakeBed = Math.min(target.heightErosion, lake.waterSurfaceHeight() - lake.minimumDepth());
            lakeBed = Maths.clamp(lakeBed, world.minY() + 1.0D, world.maxYExclusive() - 2.0D);
            lakeIncision = Math.max(0.0D, target.heightErosion - lakeBed);
        }

        if (lake.materialWater()) {
            target.lake = true;
            target.lakeShore = false;
            target.riverMask = 1.0D - lake.influence();
            target.riverDistance = -lake.shoreDistance();
            target.riverWidth = LakeField.SHORE_TRANSITION_WIDTH;
            target.riverDepth = Math.max(
                    lake.minimumDepth(),
                    Math.max(lakeIncision, lake.waterSurfaceHeight() - lakeBed));
            target.riverWaterSurfaceHeight = lake.waterSurfaceHeight();
            target.riverFlow = 0.0D;
            target.height = lakeBed;
            return;
        }

        target.lake = false;
        target.lakeShore = lake.shore();
        double baseHeight = lake.shore()
                ? lakeShoreHeight(lake, target.heightErosion)
                : target.heightErosion;
        if (!river.present() || (lake.shore() && river.depth() <= 0.05D)) {
            target.riverMask = 1.0D;
            target.riverDistance = lake.shore()
                    ? -lake.shoreDistance()
                    : settings.regionSize() * 2.0D;
            target.riverWidth = lake.shore()
                    ? LakeField.SHORE_TRANSITION_WIDTH
                    : settings.minimumWidth();
            target.riverDepth = 0.0D;
            target.riverWaterSurfaceHeight = lake.shore()
                    ? lake.waterSurfaceHeight()
                    : Double.NaN;
            target.riverFlow = lake.shore() ? 0.0D : Double.NaN;
            target.height = baseHeight;
            return;
        }

        double halfWidth = Math.max(0.5D, river.width() * 0.5D);
        double normalizedDistance = Maths.clamp(river.distance() / halfWidth, 0.0D, 1.0D);
        boolean wetChannel = normalizedDistance <= WET_CHANNEL_RADIUS
                && Double.isFinite(river.waterSurfaceHeight());
        double desiredWaterDepth = desiredWaterDepth(river, wetChannel);
        double localIncision;
        double finalHeight;
        boolean carveableWetChannel = false;
        if (wetChannel) {
            // A river bed is a hydraulic profile, not eroded terrain with an arbitrary depth
            // subtracted from it. Limiting depth growth by horizontal distance makes the bed
            // Lipschitz-continuous: together with the separately grade-limited water surface, two
            // neighboring wet columns cannot become a two-block cliff after integer quantization.
            double desiredBed = river.waterSurfaceHeight() - desiredWaterDepth;
            double requiredIncision = Math.max(0.0D, baseHeight - desiredBed);
            carveableWetChannel = requiredIncision
                    <= settings.maximumDepth() + MAXIMUM_RIDGE_CORRECTION;
            finalHeight = carveableWetChannel
                    ? Math.min(baseHeight, desiredBed)
                    : riverBankHeight(river, halfWidth, baseHeight);
            localIncision = Math.max(0.0D, baseHeight - finalHeight);
        } else {
            finalHeight = riverBankHeight(river, halfWidth, baseHeight);
            localIncision = Math.max(0.0D, baseHeight - finalHeight);
        }
        finalHeight = Maths.clamp(
                finalHeight,
                world.minY() + 1.0D,
                world.maxYExclusive() - 2.0D);
        boolean materialWater = carveableWetChannel
                && river.waterSurfaceHeight() > finalHeight + 0.05D;

        target.riverMask = Maths.smooth(normalizedDistance);
        target.riverDistance = river.distance();
        target.riverWidth = river.width();
        target.riverDepth = materialWater
                ? river.waterSurfaceHeight() - finalHeight
                : localIncision;
        target.riverWaterSurfaceHeight = materialWater
                ? river.waterSurfaceHeight()
                : Double.NaN;
        target.riverFlow = river.flow();
        target.height = finalHeight;
    }

    private double lakeShoreHeight(LakeHit lake, double terrainHeight) {
        double outwardDistance = Math.max(0.0D, -lake.shoreDistance());
        double alpha = Maths.smooth(Maths.clamp(
                outwardDistance / LakeField.SHORE_TRANSITION_WIDTH,
                0.0D,
                1.0D));
        return Math.max(
                lake.waterSurfaceHeight(),
                Maths.lerp(lake.waterSurfaceHeight(), terrainHeight, alpha));
    }

    private double riverBankHeight(RiverHit river, double halfWidth, double terrainHeight) {
        if (!Double.isFinite(river.waterSurfaceHeight())) {
            return terrainHeight;
        }
        double wetRadius = halfWidth * WET_CHANNEL_RADIUS;
        double fringe = MINIMUM_BANK_TRANSITION
                + Math.min(
                        MAXIMUM_BANK_FLOW_EXTRA,
                        Math.sqrt(Math.max(0.0D, river.flow())) * BANK_FLOW_SCALE);
        double verticalTransition = Math.abs(terrainHeight - river.waterSurfaceHeight())
                / MAXIMUM_BANK_GRADE;
        double transitionWidth = Math.max(
                halfWidth - wetRadius + fringe,
                verticalTransition);
        double bankDistance = Math.max(0.0D, river.distance() - wetRadius);
        if (bankDistance >= transitionWidth) {
            return terrainHeight;
        }
        double alpha = Maths.smooth(Maths.clamp(bankDistance / transitionWidth, 0.0D, 1.0D));
        double guardedWaterline = river.waterSurfaceHeight() + BANK_WATER_GRADE_GUARD;
        double blendedHeight = Math.max(
                guardedWaterline,
                Maths.lerp(river.waterSurfaceHeight(), terrainHeight, alpha));
        double gradeCeiling = guardedWaterline + bankDistance * MAXIMUM_BANK_RISE_GRADE;
        return Math.min(blendedHeight, gradeCeiling);
    }

    private double desiredWaterDepth(RiverHit river, boolean wetChannel) {
        if (!wetChannel) {
            return 0.0D;
        }
        // Depth is a function of centerline distance, not of the winning segment width or flow.
        // At a confluence, adjacent columns can legitimately choose different source segments;
        // deriving the bed from width/flow there would create a discontinuous trench wall.
        double centerDepth = minimumWaterDepth(river.waterSurfaceHeight());
        double gradeLimitedDepth = centerDepth - river.distance() * MAXIMUM_BED_GRADE;
        return Maths.clamp(
                gradeLimitedDepth,
                EDGE_WATER_DEPTH,
                settings.maximumDepth());
    }

    private double minimumWaterDepth(double waterSurfaceHeight) {
        if (!Double.isFinite(waterSurfaceHeight)) {
            return settings.minimumWaterDepth();
        }

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
        return Math.max(settings.minimumWaterDepth(), target);
    }

    /**
     * Samples hydrology using the stable Engine API representation.
     *
     * <p>Lake samples reuse the API river container for water-surface compatibility and advertise
     * zero flow; the final terrain type separately identifies them as lakes.</p>
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return nearest active hydrology sample
     */
    public RiverSample sample(int x, int z) {
        Cell cell = new Cell();
        lookup(x, z, cell);
        return new RiverSample(
                cell.riverDistance,
                cell.riverWidth,
                cell.riverDepth,
                cell.riverWaterSurfaceHeight,
                cell.riverFlow);
    }

    /**
     * Returns the nearest refined linear channel.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return nearest river hit or {@link RiverHit#NONE}
     */
    public RiverHit nearest(int x, int z) {
        return nearestInternal(x, z, Double.NaN, Double.POSITIVE_INFINITY);
    }

    private RiverHit nearestSurfaceAligned(int x, int z, double terrainHeight) {
        return nearestInternal(
                x,
                z,
                terrainHeight,
                MINIMUM_BANK_TRANSITION + MAXIMUM_BANK_FLOW_EXTRA);
    }

    private RiverHit nearestInternal(
            int x,
            int z,
            double terrainHeight,
            double alternativeRange) {
        int regionX = Math.floorDiv(x, settings.regionSize());
        int regionZ = Math.floorDiv(z, settings.regionSize());
        RiverHit nearest = nearestInMap(
                map(regionX, regionZ), x, z, terrainHeight, alternativeRange);
        int localX = Math.floorMod(x, settings.regionSize());
        int localZ = Math.floorMod(z, settings.regionSize());
        double boundaryRange = settings.gridSpacing() * 2.0D + settings.maximumWidth();
        int minDx = localX <= boundaryRange ? -1 : 0;
        int maxDx = settings.regionSize() - localX <= boundaryRange ? 1 : 0;
        int minDz = localZ <= boundaryRange ? -1 : 0;
        int maxDz = settings.regionSize() - localZ <= boundaryRange ? 1 : 0;
        for (int dz = minDz; dz <= maxDz; dz++) {
            for (int dx = minDx; dx <= maxDx; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                RiverHit candidate = nearestInMap(
                        map(regionX + dx, regionZ + dz),
                        x,
                        z,
                        terrainHeight,
                        alternativeRange);
                if (betterHit(candidate, nearest, terrainHeight)) {
                    nearest = candidate;
                }
            }
        }
        return nearest;
    }

    private static RiverHit nearestInMap(
            Rivermap map,
            int x,
            int z,
            double terrainHeight,
            double alternativeRange) {
        if (!Double.isFinite(terrainHeight)) {
            return map.nearest(x, z);
        }
        return map.nearestSurfaceAligned(x, z, terrainHeight, alternativeRange);
    }

    private static boolean betterHit(
            RiverHit candidate,
            RiverHit current,
            double terrainHeight) {
        if (!Double.isFinite(terrainHeight)) {
            return candidate.distance() < current.distance();
        }
        return candidate.surfaceAlignmentScore(terrainHeight)
                < current.surfaceAlignmentScore(terrainHeight);
    }

    /**
     * Returns one cached or newly generated immutable river map.
     *
     * @param regionX river-region X index
     * @param regionZ river-region Z index
     * @return completed map
     */
    public Rivermap map(int regionX, int regionZ) {
        long key = (((long) regionX) << 32) ^ (regionZ & 0xFFFFFFFFL);
        return cache.get(key, () -> generator.generate(regionX, regionZ));
    }

    /** Returns the nearest lake-only sample without re-running river selection. */
    LakeHit lake(int x, int z) {
        return nearestLake(x, z);
    }

    private LakeHit nearestLake(int x, int z) {
        int regionX = Math.floorDiv(x, settings.regionSize());
        int regionZ = Math.floorDiv(z, settings.regionSize());
        LakeHit best = map(regionX, regionZ).lake(x, z);
        int localX = Math.floorMod(x, settings.regionSize());
        int localZ = Math.floorMod(z, settings.regionSize());
        double boundaryRange = settings.gridSpacing() * (settings.paddingCells() - 1.0D);
        int minDx = localX <= boundaryRange ? -1 : 0;
        int maxDx = settings.regionSize() - localX <= boundaryRange ? 1 : 0;
        int minDz = localZ <= boundaryRange ? -1 : 0;
        int maxDz = settings.regionSize() - localZ <= boundaryRange ? 1 : 0;
        for (int dz = minDz; dz <= maxDz; dz++) {
            for (int dx = minDx; dx <= maxDx; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                LakeHit candidate = map(regionX + dx, regionZ + dz).lake(x, z);
                if (candidate.influence() > best.influence()) {
                    best = candidate;
                }
            }
        }
        return best;
    }
}
