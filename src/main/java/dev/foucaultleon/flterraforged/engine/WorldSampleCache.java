package dev.foucaultleon.flterraforged.engine;

import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;
import dev.foucaultleon.flterraforged.engine.internal.cache.SingleFlightCache;
import dev.foucaultleon.flterraforged.engine.pipeline.WorldgenPipeline;
import java.util.Arrays;
import java.util.Objects;

/**
 * World-scoped cache of immutable, chunk-aligned final terrain-sample tiles.
 *
 * <p>The cache sits above the complete world-generation pipeline, so biome lookup, density shaping,
 * height queries and surface passes can reuse exactly the same final X/Z samples. Completed tiles
 * are immutable and bounded by an access-ordered LRU. Expensive tile generation always happens
 * outside the cache lock. Exact-key single-flight loading coalesces a cold miss for the same tile
 * while unrelated tiles remain parallel and cannot collide on arbitrary lock stripes.</p>
 */
final class WorldSampleCache {

    static final int TILE_SIZE = 16;
    static final int DEFAULT_MAXIMUM_TILES = 1024;

    private final WorldgenPipeline pipeline;
    private final SingleFlightCache<TerrainSampleTile> cache;

    WorldSampleCache(WorldgenPipeline pipeline) {
        this(pipeline, DEFAULT_MAXIMUM_TILES);
    }

    WorldSampleCache(WorldgenPipeline pipeline, int maximumTiles) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        if (maximumTiles < 1) {
            throw new IllegalArgumentException("maximumTiles must be >= 1");
        }
        this.cache = new SingleFlightCache<>("final terrain tile", maximumTiles);
    }

    TerrainSample sample(int x, int z) {
        int tileX = Math.floorDiv(x, TILE_SIZE);
        int tileZ = Math.floorDiv(z, TILE_SIZE);
        long key = key(tileX, tileZ);

        TerrainSampleTile tile = cache.get(key, ignored -> generate(tileX, tileZ));
        return tile.sample(x, z);
    }

    void clear() {
        cache.clear();
    }

    int cachedTiles() {
        return cache.size();
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
    }

}
