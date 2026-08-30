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
 * Thread-safe hydrology facade backed by cached immutable rivermaps.
 *
 * <p>Each map is generated outside the cache lock. Sampling inspects the current region and its
 * eight neighbors so channel geometry remains available when a query lies near a region boundary.</p>
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
     * @param erodedTerrain terrain stage after erosion and before river incision
     * @param drainageTerrain terrain lookup used to build the coarse drainage topology
     * @param settings river settings
     */
    public RiverModel(
            long seed,
            EngineContext world,
            CellLookup erodedTerrain,
            CellLookup drainageTerrain,
            RiverSettings settings) {
        this.world = Objects.requireNonNull(world, "world");
        this.erodedTerrain = Objects.requireNonNull(erodedTerrain, "erodedTerrain");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.generator = new RivermapGenerator(
                seed, world, Objects.requireNonNull(drainageTerrain, "drainageTerrain"), settings);
        this.cache = new MapCache(settings.cacheSize());
    }

    /**
     * Creates a river model using the same lookup for drainage topology and final pre-river terrain.
     *
     * @param seed hydrology seed
     * @param world immutable world context
     * @param terrain terrain lookup before river incision
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
        RiverHit hit = nearest(x, z);
        if (!hit.present()) {
            target.riverMask = 1.0D;
            target.riverDistance = settings.regionSize() * 2.0D;
            target.riverWidth = settings.minimumWidth();
            target.riverDepth = 0.0D;
            target.riverWaterSurfaceHeight = Double.NaN;
            target.riverFlow = Double.NaN;
            target.height = target.heightErosion;
            return;
        }
        double halfWidth = Math.max(0.5D, hit.width() * 0.5D);
        target.riverMask = Maths.smooth(Maths.clamp(hit.distance() / halfWidth, 0.0D, 1.0D));
        target.riverDistance = hit.distance();
        target.riverWidth = hit.width();
        target.riverDepth = hit.depth();
        target.riverWaterSurfaceHeight = hit.depth() > 0.0D
                ? hit.waterSurfaceHeight()
                : Double.NaN;
        target.riverFlow = hit.flow();
        target.height = Maths.clamp(
                target.heightErosion - hit.depth(),
                world.minY() + 1.0D,
                world.maxYExclusive() - 2.0D);
    }

    /**
     * Samples the nearest river using the stable Engine API representation.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return nearest river sample; values remain finite even when no nearby segment exists
     */
    public RiverSample sample(int x, int z) {
        RiverHit hit = nearest(x, z);
        if (!hit.present()) {
            return new RiverSample(
                    settings.regionSize() * 2.0D,
                    settings.minimumWidth(),
                    0.0D,
                    Double.NaN,
                    Double.NaN);
        }
        return new RiverSample(
                hit.distance(),
                hit.width(),
                hit.depth(),
                hit.depth() > 0.0D ? hit.waterSurfaceHeight() : Double.NaN,
                hit.flow());
    }

    /**
     * Returns the internal nearest-channel representation.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return nearest hit or {@link RiverHit#NONE}
     */
    public RiverHit nearest(int x, int z) {
        int regionX = Math.floorDiv(x, settings.regionSize());
        int regionZ = Math.floorDiv(z, settings.regionSize());
        RiverHit nearest = map(regionX, regionZ).nearest(x, z);
        int localX = Math.floorMod(x, settings.regionSize());
        int localZ = Math.floorMod(z, settings.regionSize());
        double boundaryRange = settings.gridSpacing() + settings.maximumWidth();
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
     * Returns one cached or newly generated immutable rivermap.
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
