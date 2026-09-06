package dev.foucaultleon.flterraforged.engine.river;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.river.RiverSample;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import dev.foucaultleon.flterraforged.engine.cell.CellLookup;
import dev.foucaultleon.flterraforged.engine.internal.BoundedConcurrentCache;
import dev.foucaultleon.flterraforged.engine.internal.Maths;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe hydrology facade backed by cached immutable river maps.
 *
 * <p>Linear channels and depression-filled inland water are resolved together. Completed maps are
 * held in a bounded concurrent cache whose hit path does not acquire a global monitor. Concurrent
 * cold misses for the same hydrology region use exact-key single-flight ownership while unrelated
 * region keys remain independent.</p>
 *
 * <p>R47 centers the internal hydrology ownership lattice around the world origin. R50 keeps the
 * single canonical map hit path for ordinary lake samples, but reconciles overlapping padded lake
 * basins through their stable basin anchor. A lake crossing a map boundary therefore receives one
 * canonical water level without restoring the former eight-neighbor probe on every column.</p>
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
    private final BoundedConcurrentCache<Long, Rivermap> cache;
    private final BoundedConcurrentCache<Long, Double> canonicalLakeLevels;
    private final ConcurrentMap<Long, CompletableFuture<Rivermap>> inFlight = new ConcurrentHashMap<>();
    private final ThreadLocal<Set<Long>> ownedMapKeys = ThreadLocal.withInitial(HashSet::new);

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
        CellLookup drainage = centeredGeneratorLookup(
                Objects.requireNonNull(drainageTerrain, "drainageTerrain"));
        CellLookup climate = drainageClimate == null ? null : centeredGeneratorLookup(drainageClimate);
        this.generator = new RivermapGenerator(seed, world, drainage, climate, settings);
        this.cache = new BoundedConcurrentCache<>(settings.cacheSize());
        this.canonicalLakeLevels = new BoundedConcurrentCache<>(Math.max(64, settings.cacheSize() * 64));
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
        int hydrologyX = toHydrologyCoordinate(x);
        int hydrologyZ = toHydrologyCoordinate(z);
        int regionX = Math.floorDiv(hydrologyX, settings.regionSize());
        int regionZ = Math.floorDiv(hydrologyZ, settings.regionSize());
        RiverHit nearest = nearestInMap(
                map(regionX, regionZ), hydrologyX, hydrologyZ, terrainHeight, alternativeRange);
        int localX = Math.floorMod(hydrologyX, settings.regionSize());
        int localZ = Math.floorMod(hydrologyZ, settings.regionSize());
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
                        hydrologyX,
                        hydrologyZ,
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
        Rivermap completed = cache.get(key);
        return completed == null ? loadSingleFlight(key, regionX, regionZ) : completed;
    }

    private Rivermap loadSingleFlight(long key, int regionX, int regionZ) {
        Set<Long> localOwnership = ownedMapKeys.get();
        CompletableFuture<Rivermap> owned = new CompletableFuture<>();
        CompletableFuture<Rivermap> existing = inFlight.putIfAbsent(key, owned);
        if (existing != null) {
            if (localOwnership.contains(key)) {
                throw new IllegalStateException(
                        "Recursive river-map load detected for region " + regionX + ',' + regionZ);
            }
            return await(existing);
        }
        if (!localOwnership.add(key)) {
            inFlight.remove(key, owned);
            throw new IllegalStateException(
                    "Recursive river-map ownership detected for region " + regionX + ',' + regionZ);
        }
        try {
            Rivermap generated = generator.generate(regionX, regionZ);
            Rivermap retained = cache.putIfAbsent(key, generated);
            owned.complete(retained);
            return retained;
        } catch (Throwable throwable) {
            owned.completeExceptionally(throwable);
            throw propagate(throwable);
        } finally {
            localOwnership.remove(key);
            if (localOwnership.isEmpty()) {
                ownedMapKeys.remove();
            }
            inFlight.remove(key, owned);
        }
    }

    private static Rivermap await(CompletableFuture<Rivermap> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            throw propagate(cause == null ? exception : cause);
        }
    }

    private static RuntimeException propagate(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("River-map generation failed", throwable);
    }

    int inFlightMaps() {
        return inFlight.size();
    }

    int cachedMaps() {
        return cache.size();
    }

    private LakeHit nearestLake(int x, int z) {
        int hydrologyX = toHydrologyCoordinate(x);
        int hydrologyZ = toHydrologyCoordinate(z);
        int regionX = Math.floorDiv(hydrologyX, settings.regionSize());
        int regionZ = Math.floorDiv(hydrologyZ, settings.regionSize());
        LakeHit local = map(regionX, regionZ).lake(hydrologyX, hydrologyZ);
        if (!local.present() || !local.hasBasinKey()) {
            return local;
        }

        Double cachedLevel = canonicalLakeLevels.get(local.basinKey());
        if (cachedLevel != null) {
            return local.withWaterSurfaceHeight(cachedLevel);
        }

        int anchorX = (int) (local.basinKey() >> 32);
        int anchorZ = (int) local.basinKey();
        int ownerRegionX = Math.floorDiv(anchorX, settings.regionSize());
        int ownerRegionZ = Math.floorDiv(anchorZ, settings.regionSize());
        LakeHit owner = map(ownerRegionX, ownerRegionZ).lake(anchorX, anchorZ);
        double canonicalLevel = owner.present() && owner.basinKey() == local.basinKey()
                ? owner.waterSurfaceHeight()
                : local.waterSurfaceHeight();
        Double retained = canonicalLakeLevels.putIfAbsent(local.basinKey(), canonicalLevel);
        return local.withWaterSurfaceHeight(retained == null ? canonicalLevel : retained);
    }

    private int toHydrologyCoordinate(int worldCoordinate) {
        return Math.addExact(worldCoordinate, settings.regionSize() / 2);
    }

    private CellLookup centeredGeneratorLookup(CellLookup delegate) {
        int offset = settings.regionSize() / 2;
        return (x, z, target) -> delegate.lookup(
                Math.subtractExact(x, offset),
                Math.subtractExact(z, offset),
                target);
    }
}
