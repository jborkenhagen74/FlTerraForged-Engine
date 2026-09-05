package dev.foucaultleon.flterraforged.engine;

import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainEnvironmentSample;
import dev.foucaultleon.flterraforged.engine.internal.InlineSingleFlightCache;
import dev.foucaultleon.flterraforged.engine.pipeline.WorldgenPipeline;
import java.util.Objects;

/**
 * World-scoped cache for lightweight placement-time terrain and hydrology samples.
 *
 * <p>Placement probes are intentionally sparse, so this cache stores individual X/Z samples instead
 * of expanding a single miss into a complete terrain tile. Dry and wet samples are both retained,
 * which makes negative environment decisions reusable. A cold coordinate is computed inline by one
 * owner thread and concurrent duplicate callers reuse that exact result through synchronous
 * single-flight ownership. No executor work is submitted by the loader.</p>
 */
final class EnvironmentSampleCache {

    static final int DEFAULT_MAXIMUM_SAMPLES = 8192;

    private final WorldgenPipeline pipeline;
    private final InlineSingleFlightCache<Long, TerrainEnvironmentSample> cache;

    EnvironmentSampleCache(WorldgenPipeline pipeline) {
        this(pipeline, DEFAULT_MAXIMUM_SAMPLES);
    }

    EnvironmentSampleCache(WorldgenPipeline pipeline, int maximumSamples) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        if (maximumSamples < 1) {
            throw new IllegalArgumentException("maximumSamples must be >= 1");
        }
        this.cache = new InlineSingleFlightCache<>(maximumSamples);
    }

    TerrainEnvironmentSample sample(int x, int z) {
        long key = key(x, z);
        return cache.get(key, () -> pipeline.environment(x, z));
    }

    void clear() {
        cache.clear();
    }

    int cachedSamples() {
        return cache.completedSize();
    }

    private static long key(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }
}
