package dev.foucaultleon.flterraforged.engine;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.TerrainWorld;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainEnvironmentSample;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;
import dev.foucaultleon.flterraforged.engine.pipeline.WorldgenPipeline;
import java.util.Objects;

/** Seed-bound, deterministic and thread-safe world sampler with shared bounded caching. */
public final class DefaultTerrainWorld implements TerrainWorld {

    private final EngineContext context;
    private final WorldgenPipeline pipeline;
    private final WorldSampleCache sampleCache;
    private final EnvironmentSampleCache environmentCache;

    /**
     * Creates a deterministic terrain view for one world.
     *
     * @param context immutable world context
     * @param settings immutable engine settings
     */
    public DefaultTerrainWorld(EngineContext context, EngineSettings settings) {
        this.context = Objects.requireNonNull(context, "context");
        this.pipeline = new WorldgenPipeline(context, Objects.requireNonNull(settings, "settings"));
        this.sampleCache = new WorldSampleCache(pipeline);
        this.environmentCache = new EnvironmentSampleCache(pipeline);
    }

    /** {@inheritDoc} */
    @Override
    public EngineContext context() {
        return context;
    }

    /** {@inheritDoc} */
    @Override
    public TerrainSample sample(int x, int z) {
        return sampleCache.sample(x, z);
    }

    /** {@inheritDoc} */
    @Override
    public TerrainSample[] sampleTile(int originX, int originZ, int size) {
        return sampleCache.sampleTile(originX, originZ, size);
    }

    /**
     * Returns the lightweight placement environment through a sparse bounded single-flight cache.
     *
     * <p>The loader still calls only the dedicated lightweight pipeline path. It never enters the
     * final-sample tile cache, does not request biome data and does not schedule executor work.</p>
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return lightweight terrain and hydrology sample
     */
    @Override
    public TerrainEnvironmentSample environment(int x, int z) {
        return environmentCache.sample(x, z);
    }

    /** Releases all world-scoped terrain and environment caches. */
    @Override
    public void close() {
        environmentCache.clear();
        sampleCache.clear();
    }
}
