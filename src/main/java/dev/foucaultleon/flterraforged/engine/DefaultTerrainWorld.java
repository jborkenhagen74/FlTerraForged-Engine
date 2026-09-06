package dev.foucaultleon.flterraforged.engine;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.TerrainWorld;
import dev.foucaultleon.flterraforged.engine.api.chunk.ChunkSnapshot;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;
import dev.foucaultleon.flterraforged.engine.chunk.ChunkSnapshotCache;
import dev.foucaultleon.flterraforged.engine.chunk.TerrainPointSampler;
import dev.foucaultleon.flterraforged.engine.pipeline.WorldgenPipeline;
import java.util.Objects;

/** Seed-bound deterministic world with shared terrain and complete natural-chunk caches. */
public final class DefaultTerrainWorld implements TerrainWorld {

    private final EngineContext context;
    private final WorldgenPipeline pipeline;
    private final WorldSampleCache sampleCache;
    private final ChunkSnapshotCache chunkCache;

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
        TerrainPointSampler sampler = new TerrainPointSampler() {
            @Override
            public TerrainSample sample(int x, int z) {
                return sampleCache.sample(x, z);
            }

            @Override
            public TerrainSample[] sampleChunk(int chunkX, int chunkZ) {
                // ChunkSnapshotCache only reads the immutable TerrainSample references. Reusing the
                // cache-owned array here avoids one redundant 256-reference clone per chunk.
                return sampleCache.sampleChunkShared(chunkX, chunkZ);
            }
        };
        this.chunkCache = new ChunkSnapshotCache(context, sampler);
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
    public TerrainSample placementSample(int x, int z) {
        return pipeline.placementSample(x, z);
    }

    /**
     * Returns one caller-owned copy of the chunk-aligned final terrain tile.
     *
     * <p>This is the canonical bridge used by the host biome stage and by complete snapshot
     * generation. Both therefore share the exact same completed 16x16 Engine cache entry.</p>
     */
    @Override
    public TerrainSample[] sampleChunk(int chunkX, int chunkZ) {
        return sampleCache.sampleChunk(chunkX, chunkZ);
    }

    /** {@inheritDoc} */
    @Override
    public ChunkSnapshot chunkSnapshot(int chunkX, int chunkZ) {
        return chunkCache.get(chunkX, chunkZ);
    }

    /** Releases world-scoped immutable caches. */
    @Override
    public void close() {
        chunkCache.clear();
        sampleCache.clear();
    }
}
