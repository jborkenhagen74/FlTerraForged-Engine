package dev.foucaultleon.flterraforged.engine;

import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;
import dev.foucaultleon.flterraforged.engine.pipeline.WorldgenPipeline;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * World-scoped adaptive cache of immutable final terrain samples.
 *
 * <p>Dense chunk generation is served by completed 16x16 tiles, while isolated lookups first use a
 * bounded exact-point cache. A tile is promoted only after several distinct cache misses address the
 * same tile. This avoids calculating a complete terrain tile for sparse structure/environment probes
 * while retaining the tile reuse that normal chunk generation needs.</p>
 *
 * <p>Cache misses never wait for another generating thread. Expensive pipeline work always runs
 * outside cache monitors. Concurrent cold callers may therefore calculate the same deterministic
 * value more than once, but only one completed value is retained. This bounded duplicate work is a
 * deliberate liveness guarantee for hosts such as Minecraft whose world-generation workers must not
 * form synchronous wait graphs through an external cache.</p>
 */
final class WorldSampleCache {

    static final int TILE_SIZE = 16;
    static final int DEFAULT_MAXIMUM_TILES = 256;
    static final int DEFAULT_MAXIMUM_POINTS = 8192;
    static final int DEFAULT_MAXIMUM_TOUCHES = 2048;
    static final int FULL_TILE_PROMOTION_THRESHOLD = 4;

    private final WorldgenPipeline pipeline;
    private final TileCache tiles;
    private final PointCache points;
    private final TouchCache touches;

    WorldSampleCache(WorldgenPipeline pipeline) {
        this(
                pipeline,
                DEFAULT_MAXIMUM_TILES,
                DEFAULT_MAXIMUM_POINTS,
                DEFAULT_MAXIMUM_TOUCHES);
    }

    WorldSampleCache(
            WorldgenPipeline pipeline,
            int maximumTiles,
            int maximumPoints,
            int maximumTouches) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        if (maximumTiles < 1) {
            throw new IllegalArgumentException("maximumTiles must be >= 1");
        }
        if (maximumPoints < 1) {
            throw new IllegalArgumentException("maximumPoints must be >= 1");
        }
        if (maximumTouches < 1) {
            throw new IllegalArgumentException("maximumTouches must be >= 1");
        }
        this.tiles = new TileCache(maximumTiles);
        this.points = new PointCache(maximumPoints);
        this.touches = new TouchCache(maximumTouches);
    }

    TerrainSample sample(int x, int z) {
        int tileX = Math.floorDiv(x, TILE_SIZE);
        int tileZ = Math.floorDiv(z, TILE_SIZE);
        long tileKey = key(tileX, tileZ);

        TerrainSampleTile tile;
        synchronized (tiles) {
            tile = tiles.get(tileKey);
        }
        if (tile != null) {
            return tile.sample(x, z);
        }

        long pointKey = key(x, z);
        TerrainSample point;
        synchronized (points) {
            point = points.get(pointKey);
        }
        if (point != null) {
            return point;
        }

        int touchCount;
        synchronized (touches) {
            touchCount = touches.record(tileKey);
        }
        if (touchCount >= FULL_TILE_PROMOTION_THRESHOLD) {
            TerrainSampleTile generated = generateTile(tileX, tileZ);
            synchronized (tiles) {
                TerrainSampleTile existing = tiles.get(tileKey);
                if (existing == null) {
                    tiles.put(tileKey, generated);
                    tile = generated;
                } else {
                    tile = existing;
                }
            }
            return tile.sample(x, z);
        }

        TerrainSample generated = pipeline.sample(x, z);

        // A racing dense caller may have promoted the tile while this sparse point was calculated.
        synchronized (tiles) {
            tile = tiles.get(tileKey);
        }
        if (tile != null) {
            return tile.sample(x, z);
        }

        synchronized (points) {
            TerrainSample existing = points.get(pointKey);
            if (existing == null) {
                points.put(pointKey, generated);
                point = generated;
            } else {
                point = existing;
            }
        }
        return point;
    }

    void clear() {
        synchronized (tiles) {
            tiles.clear();
        }
        synchronized (points) {
            points.clear();
        }
        synchronized (touches) {
            touches.clear();
        }
    }

    int cachedTiles() {
        synchronized (tiles) {
            return tiles.size();
        }
    }

    int cachedPoints() {
        synchronized (points) {
            return points.size();
        }
    }

    private TerrainSampleTile generateTile(int tileX, int tileZ) {
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

    private static final class TileCache extends LinkedHashMap<Long, TerrainSampleTile> {

        private static final long serialVersionUID = 1L;
        private final int maximumSize;

        TileCache(int maximumSize) {
            super(maximumSize + 1, 0.75F, true);
            this.maximumSize = maximumSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, TerrainSampleTile> eldest) {
            return size() > maximumSize;
        }
    }

    private static final class PointCache extends LinkedHashMap<Long, TerrainSample> {

        private static final long serialVersionUID = 1L;
        private final int maximumSize;

        PointCache(int maximumSize) {
            super(maximumSize + 1, 0.75F, true);
            this.maximumSize = maximumSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, TerrainSample> eldest) {
            return size() > maximumSize;
        }
    }

    private static final class TouchCache extends LinkedHashMap<Long, Integer> {

        private static final long serialVersionUID = 1L;
        private final int maximumSize;

        TouchCache(int maximumSize) {
            super(maximumSize + 1, 0.75F, true);
            this.maximumSize = maximumSize;
        }

        int record(long tileKey) {
            int next = Math.min(
                    FULL_TILE_PROMOTION_THRESHOLD,
                    getOrDefault(tileKey, 0) + 1);
            put(tileKey, next);
            return next;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Integer> eldest) {
            return size() > maximumSize;
        }
    }
}
