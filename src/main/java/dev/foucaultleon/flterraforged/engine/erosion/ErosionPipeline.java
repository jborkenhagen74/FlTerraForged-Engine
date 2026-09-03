package dev.foucaultleon.flterraforged.engine.erosion;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import dev.foucaultleon.flterraforged.engine.cell.CellLookup;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic hydraulic/thermal erosion stage wrapping the base terrain-cell lookup.
 *
 * <p>The stage keeps a bounded shared cache containing only immutable completed erosion regions.
 * Cache lookup/insertion is synchronized, while expensive region generation always happens outside
 * the cache lock. Key-striped generation locks coalesce concurrent misses for the same region
 * without serializing independent regions or introducing recursive {@code computeIfAbsent}-style
 * wait graphs.</p>
 */
public final class ErosionPipeline implements CellLookup {

    private static final int GENERATION_LOCK_COUNT = 64;

    private final CellLookup baseTerrain;
    private final ErosionSettings settings;
    private final ErosionTileGenerator generator;
    private final TileCache cache;
    private final Object[] generationLocks;

    /**
     * Creates an erosion pipeline.
     *
     * @param seed world seed used for deterministic droplet launches
     * @param world immutable world context
     * @param baseTerrain terrain lookup before physical erosion
     * @param settings erosion settings
     */
    public ErosionPipeline(long seed, EngineContext world, CellLookup baseTerrain, ErosionSettings settings) {
        this.baseTerrain = Objects.requireNonNull(baseTerrain, "baseTerrain");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.generator = new ErosionTileGenerator(seed, Objects.requireNonNull(world, "world"), baseTerrain, settings);
        this.cache = new TileCache(settings.cacheSize());
        this.generationLocks = createGenerationLocks();
    }

    /** {@inheritDoc} */
    @Override
    public void lookup(int x, int z, Cell target) {
        Objects.requireNonNull(target, "target");
        baseTerrain.lookup(x, z, target);
        ErosionSample sample = sample(x, z);
        target.heightErosion = sample.erodedHeight();
        target.height = sample.erodedHeight();
        target.gradient = sample.gradient();
        target.erosion = sample.erosion();
        target.sediment = sample.sediment();
        target.erosionMask = sample.erosion() > 1.0E-4D || sample.sediment() > 1.0E-4D;
    }

    /**
     * Samples erosion data without allocating a cell.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return erosion result
     */
    public ErosionSample sample(int x, int z) {
        int regionX = Math.floorDiv(x, settings.regionSize());
        int regionZ = Math.floorDiv(z, settings.regionSize());
        long key = (((long) regionX) << 32) ^ (regionZ & 0xFFFFFFFFL);
        ErosionTile tile;
        synchronized (cache) {
            tile = cache.get(key);
        }
        if (tile == null) {
            synchronized (generationLock(key)) {
                synchronized (cache) {
                    tile = cache.get(key);
                }
                if (tile == null) {
                    tile = generator.generate(regionX, regionZ);
                    synchronized (cache) {
                        cache.put(key, tile);
                    }
                }
            }
        }
        return tile.sample(x, z, settings.maximumHeightChange());
    }

    private Object generationLock(long key) {
        int index = (int) (key ^ (key >>> 32)) & (GENERATION_LOCK_COUNT - 1);
        return generationLocks[index];
    }

    private static Object[] createGenerationLocks() {
        Object[] locks = new Object[GENERATION_LOCK_COUNT];
        Arrays.setAll(locks, ignored -> new Object());
        return locks;
    }

    private static final class TileCache extends LinkedHashMap<Long, ErosionTile> {

        private static final long serialVersionUID = 1L;
        private final int maximumSize;

        TileCache(int maximumSize) {
            super(maximumSize + 1, 0.75F, true);
            this.maximumSize = maximumSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, ErosionTile> eldest) {
            return size() > maximumSize;
        }
    }
}
