package dev.foucaultleon.flterraforged.engine;

import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;
import dev.foucaultleon.flterraforged.engine.internal.InlineSingleFlightCache;
import dev.foucaultleon.flterraforged.engine.pipeline.WorldgenPipeline;
import java.util.Arrays;
import java.util.Objects;

/**
 * World-scoped cache of immutable, chunk-aligned final terrain-sample tiles.
 *
 * <p>The cache sits above the complete world-generation pipeline, so biome lookup, density shaping,
 * height queries and late reconciliation reuse exactly the same final X/Z samples. Completed tiles
 * are immutable and bounded. A cold miss is generated once by the thread that wins synchronous
 * single-flight ownership; concurrent callers for the same tile wait for and reuse that exact tile
 * instead of regenerating it.</p>
 */
final class WorldSampleCache {

    static final int TILE_SIZE = 16;
    static final int DEFAULT_MAXIMUM_TILES = 256;

    private final WorldgenPipeline pipeline;
    private final InlineSingleFlightCache<Long, TerrainSampleTile> cache;

    WorldSampleCache(WorldgenPipeline pipeline) {
        this(pipeline, DEFAULT_MAXIMUM_TILES);
    }

    WorldSampleCache(WorldgenPipeline pipeline, int maximumTiles) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        if (maximumTiles < 1) {
            throw new IllegalArgumentException("maximumTiles must be >= 1");
        }
        this.cache = new InlineSingleFlightCache<>(maximumTiles);
    }

    TerrainSample sample(int x, int z) {
        int tileX = Math.floorDiv(x, TILE_SIZE);
        int tileZ = Math.floorDiv(z, TILE_SIZE);
        return loadTile(tileX, tileZ).sample(x, z);
    }

    TerrainSample[] sampleTile(int originX, int originZ, int size) {
        if (size < 1) {
            throw new IllegalArgumentException("size must be >= 1");
        }
        if (size == TILE_SIZE
                && Math.floorMod(originX, TILE_SIZE) == 0
                && Math.floorMod(originZ, TILE_SIZE) == 0) {
            return loadTile(
                            Math.floorDiv(originX, TILE_SIZE),
                            Math.floorDiv(originZ, TILE_SIZE))
                    .copySamples();
        }

        TerrainSample[] samples = new TerrainSample[size * size];
        for (int localZ = 0; localZ < size; localZ++) {
            for (int localX = 0; localX < size; localX++) {
                samples[localZ * size + localX] = sample(originX + localX, originZ + localZ);
            }
        }
        return samples;
    }

    void clear() {
        cache.clear();
    }

    int cachedTiles() {
        return cache.completedSize();
    }

    private TerrainSampleTile loadTile(int tileX, int tileZ) {
        long key = key(tileX, tileZ);
        return cache.get(key, () -> generate(tileX, tileZ));
    }

    private TerrainSampleTile generate(int tileX, int tileZ) {
        int originX = tileX * TILE_SIZE;
        int originZ = tileZ * TILE_SIZE;
        TerrainSample[] samples = pipeline.sampleTile(originX, originZ, TILE_SIZE);
        return new TerrainSampleTile(originX, originZ, samples);
    }

    private static long key(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static final class TerrainSampleTile {

        private final int originX;
        private final int originZ;
        private final TerrainSample[] samples;

        TerrainSampleTile(int originX, int originZ, TerrainSample[] samples) {
            this.originX = originX;
            this.originZ = originZ;
            this.samples = Objects.requireNonNull(samples, "samples").clone();
            if (this.samples.length != TILE_SIZE * TILE_SIZE) {
                throw new IllegalArgumentException("Terrain sample tile has unexpected size");
            }
            if (Arrays.stream(this.samples).anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("Terrain sample tile contains null entries");
            }
        }

        TerrainSample sample(int x, int z) {
            int localX = x - originX;
            int localZ = z - originZ;
            if (localX < 0 || localZ < 0 || localX >= TILE_SIZE || localZ >= TILE_SIZE) {
                throw new IllegalArgumentException("Coordinate lies outside terrain sample tile");
            }
            return samples[localZ * TILE_SIZE + localX];
        }

        TerrainSample[] copySamples() {
            return samples.clone();
        }
    }
}
