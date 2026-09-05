package dev.foucaultleon.flterraforged.engine.erosion;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import dev.foucaultleon.flterraforged.engine.cell.CellLookup;
import dev.foucaultleon.flterraforged.engine.internal.InlineSingleFlightCache;
import java.util.Objects;

/**
 * Deterministic hydraulic/thermal erosion stage wrapping the base terrain-cell lookup.
 *
 * <p>The stage keeps a bounded shared cache containing only immutable completed erosion regions.
 * Cache misses are coalesced by synchronous single-flight ownership: one caller computes a missing
 * erosion region directly while concurrent callers for the same region reuse that result. Expensive
 * generation never runs while holding the completed-cache monitor and no executor work is submitted
 * by the cache.</p>
 */
public final class ErosionPipeline implements CellLookup {

    private final CellLookup baseTerrain;
    private final ErosionSettings settings;
    private final ErosionTileGenerator generator;
    private final InlineSingleFlightCache<Long, ErosionTile> cache;

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
        this.cache = new InlineSingleFlightCache<>(settings.cacheSize());
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
        ErosionTile tile = cache.get(key, () -> generator.generate(regionX, regionZ));
        return tile.sample(x, z, settings.maximumHeightChange());
    }
}
