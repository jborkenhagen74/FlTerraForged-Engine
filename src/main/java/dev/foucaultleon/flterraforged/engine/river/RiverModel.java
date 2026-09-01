package dev.foucaultleon.flterraforged.engine.river;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.river.RiverSample;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import dev.foucaultleon.flterraforged.engine.cell.CellLookup;
import dev.foucaultleon.flterraforged.engine.internal.Maths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Thread-safe hydrology facade backed by cached immutable river maps.
 *
 * <p>Linear channels and depression-filled inland water are resolved together. The map generation
 * itself remains immutable and happens outside the cache lock.</p>
 */
public final class RiverModel implements CellLookup {

    private final EngineContext world;
    private final CellLookup erodedTerrain;
    private final RiverSettings settings;
    private final RivermapGenerator generator;
    private final MapCache cache;

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
        this.cache = new MapCache(settings.cacheSize());
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
        RiverHit river = nearest(x, z);
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
            target.riverDistance = 0.0D;
            target.riverWidth = settings.gridSpacing() * (1.0D + lake.influence() * 5.0D);
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
        if (!river.present() || (lake.shore() && river.depth() <= 0.05D)) {
            target.riverMask = 1.0D;
            target.riverDistance = lake.shore() ? 0.0D : settings.regionSize() * 2.0D;
            target.riverWidth = lake.shore()
                    ? settings.gridSpacing() * (1.0D + lake.influence() * 5.0D)
                    : settings.minimumWidth();
            target.riverDepth = 0.0D;
            target.riverWaterSurfaceHeight = lake.shore()
                    ? lake.waterSurfaceHeight()
                    : Double.NaN;
            target.riverFlow = lake.shore() ? 0.0D : Double.NaN;
            target.height = target.heightErosion;
            return;
        }

        double halfWidth = Math.max(0.5D, river.width() * 0.5D);
        double normalizedDistance = Maths.clamp(river.distance() / halfWidth, 0.0D, 1.0D);
        double wetCore = 1.0D - Maths.smooth(Maths.clamp((normalizedDistance - 0.18D) / 0.70D, 0.0D, 1.0D));
        double desiredWaterDepth = minimumWaterDepth(river.waterSurfaceHeight()) * wetCore;
        double requiredIncision = river.depth();
        if (desiredWaterDepth > 0.05D && Double.isFinite(river.waterSurfaceHeight())) {
            requiredIncision = Math.max(
                    requiredIncision,
                    target.heightErosion - (river.waterSurfaceHeight() - desiredWaterDepth));
        }
        double localIncision = Maths.clamp(requiredIncision, 0.0D, settings.maximumDepth());

        target.riverMask = Maths.smooth(normalizedDistance);
        target.riverDistance = river.distance();
        target.riverWidth = river.width();
        target.riverDepth = localIncision;
        target.riverWaterSurfaceHeight = localIncision > 0.0D
                ? river.waterSurfaceHeight()
                : Double.NaN;
        target.riverFlow = river.flow();
        target.height = Maths.clamp(
                target.heightErosion - localIncision,
                world.minY() + 1.0D,
                world.maxYExclusive() - 2.0D);
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
        int regionX = Math.floorDiv(x, settings.regionSize());
        int regionZ = Math.floorDiv(z, settings.regionSize());
        RiverHit nearest = map(regionX, regionZ).nearest(x, z);
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
                RiverHit candidate = map(regionX + dx, regionZ + dz).nearest(x, z);
                if (candidate.distance() < nearest.distance()) {
                    nearest = candidate;
                }
            }
        }
        return nearest;
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
        Rivermap map;
        synchronized (cache) {
            map = cache.get(key);
        }
        if (map == null) {
            Rivermap generated = generator.generate(regionX, regionZ);
            synchronized (cache) {
                Rivermap existing = cache.get(key);
                if (existing == null) {
                    cache.put(key, generated);
                    map = generated;
                } else {
                    map = existing;
                }
            }
        }
        return map;
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

    private static final class MapCache extends LinkedHashMap<Long, Rivermap> {

        private static final long serialVersionUID = 1L;
        private final int maximumSize;

        MapCache(int maximumSize) {
            super(maximumSize + 1, 0.75F, true);
            this.maximumSize = maximumSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Rivermap> eldest) {
            return size() > maximumSize;
        }
    }
}
