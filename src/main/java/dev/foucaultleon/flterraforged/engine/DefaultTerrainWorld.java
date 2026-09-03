package dev.foucaultleon.flterraforged.engine;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.TerrainWorld;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;
import dev.foucaultleon.flterraforged.engine.internal.cache.SingleFlightCache;
import dev.foucaultleon.flterraforged.engine.pipeline.WorldgenPipeline;
import java.util.Objects;

/** Seed-bound, deterministic and thread-safe world sampler with shared final-sample caching. */
public final class DefaultTerrainWorld implements TerrainWorld {

    private static final int MARINE_DEPTH_CACHE_SIZE = 8192;

    private final EngineContext context;
    private final WorldgenPipeline pipeline;
    private final WorldSampleCache sampleCache;
    private final SingleFlightCache<Double> marineDepthCache;

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
        this.marineDepthCache = new SingleFlightCache<>("marine depth point", MARINE_DEPTH_CACHE_SIZE);
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
    public boolean isMarine(int x, int z, double minimumDepth) {
        if (!Double.isFinite(minimumDepth) || minimumDepth < 0.0D) {
            throw new IllegalArgumentException("minimumDepth must be finite and >= 0");
        }
        long key = (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
        double availableDepth = marineDepthCache.get(
                key,
                ignored -> pipeline.conservativeMarineDepth(x, z));
        return availableDepth >= minimumDepth;
    }

    /** Releases world-scoped cached terrain tiles. */
    @Override
    public void close() {
        sampleCache.clear();
        marineDepthCache.clear();
    }
}
