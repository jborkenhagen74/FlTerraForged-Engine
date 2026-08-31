package dev.foucaultleon.flterraforged.engine;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.TerrainWorld;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;
import dev.foucaultleon.flterraforged.engine.pipeline.WorldgenPipeline;
import java.util.Objects;

/** Seed-bound, deterministic and thread-safe world sampler with shared final-sample caching. */
public final class DefaultTerrainWorld implements TerrainWorld {

    private final EngineContext context;
    private final WorldgenPipeline pipeline;
    private final WorldSampleCache sampleCache;

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

    /** Releases world-scoped cached terrain tiles. */
    @Override
    public void close() {
        sampleCache.clear();
    }
}
