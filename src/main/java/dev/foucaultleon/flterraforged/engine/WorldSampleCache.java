package dev.foucaultleon.flterraforged.engine;

import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;
import dev.foucaultleon.flterraforged.engine.pipeline.WorldgenPipeline;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * World-scoped cache of immutable final terrain-sample tiles.
 *
 * <p>The cache sits above the complete world-generation pipeline, so biome lookup, density shaping,
 * height queries, hydrology guards and surface passes can reuse exactly the same final X/Z samples.
 * Completed tiles are immutable and bounded by an access-ordered LRU. Concurrent cold misses for
 * the same tile are coalesced through a single-flight map: one caller computes synchronously on its
 * current worker while all other callers reuse that result. No additional task is submitted to a
 * world-generation executor and expensive pipeline work never runs while the LRU monitor is held.</p>
 *
 * <p>R45 uses 8x8 tiles instead of 16x16 tiles. A sparse Minecraft height, biome or structure
 * lookup therefore evaluates a 10x10 pipeline envelope rather than an 18x18 envelope, while dense
 * chunk passes still reuse neighboring completed tiles. The maximum tile count is increased to
 * 4096 so the total retained final-sample capacity remains 262144 X/Z samples, equal to 1024 16x16
 * tiles. A thread-local ownership guard also turns an accidental recursive same-key request into an
 * immediate diagnostic failure instead of letting a worker join its own unfinished future.</p>
 */
final class WorldSampleCache {

    static final int TILE_SIZE = 8;
    static final int DEFAULT_MAXIMUM_TILES = 4096;

    private final WorldgenPipeline pipeline;
    private final TileCache cache;
    private final ConcurrentMap<Long, CompletableFuture<TerrainSampleTile>> inFlight =
            new ConcurrentHashMap<>();
    private final ThreadLocal<Set<Long>> ownedKeys = ThreadLocal.withInitial(HashSet::new);

    WorldSampleCache(WorldgenPipeline pipeline) {
        this(pipeline, DEFAULT_MAXIMUM_TILES);
    }

    WorldSampleCache(WorldgenPipeline pipeline, int maximumTiles) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        if (maximumTiles < 1) {
            throw new IllegalArgumentException("maximumTiles must be >= 1");
        }
        this.cache = new TileCache(maximumTiles);
    }

    TerrainSample sample(int x, int z) {
        int tileX = Math.floorDiv(x, TILE_SIZE);
        int tileZ = Math.floorDiv(z, TILE_SIZE);
        long key = key(tileX, tileZ);

        TerrainSampleTile tile = completed(key);
        if (tile == null) {
            tile = loadSingleFlight(key, tileX, tileZ);
        }
        return tile.sample(x, z);
    }

    void clear() {
        synchronized (cache) {
            cache.clear();
        }
    }

    int cachedTiles() {
        synchronized (cache) {
            return cache.size();
        }
    }

    int inFlightTiles() {
        return inFlight.size();
    }

    private TerrainSampleTile completed(long key) {
        synchronized (cache) {
            return cache.get(key);
        }
    }

    private TerrainSampleTile loadSingleFlight(long key, int tileX, int tileZ) {
        Set<Long> localOwnership = ownedKeys.get();
        CompletableFuture<TerrainSampleTile> owned = new CompletableFuture<>();
        CompletableFuture<TerrainSampleTile> existing = inFlight.putIfAbsent(key, owned);
        if (existing != null) {
            if (localOwnership.contains(key)) {
                throw new IllegalStateException(
                        "Recursive terrain sample tile load detected for tile " + tileX + ',' + tileZ);
            }
            return await(existing);
        }

        if (!localOwnership.add(key)) {
            inFlight.remove(key, owned);
            throw new IllegalStateException(
                    "Recursive terrain sample tile ownership detected for tile " + tileX + ',' + tileZ);
        }
        try {
            TerrainSampleTile generated = generate(tileX, tileZ);
            TerrainSampleTile retained;
            synchronized (cache) {
                TerrainSampleTile cached = cache.get(key);
                if (cached == null) {
                    cache.put(key, generated);
                    retained = generated;
                } else {
                    retained = cached;
                }
            }
            owned.complete(retained);
            return retained;
        } catch (Throwable throwable) {
            owned.completeExceptionally(throwable);
            throw propagate(throwable);
        } finally {
            localOwnership.remove(key);
            if (localOwnership.isEmpty()) {
                ownedKeys.remove();
            }
            inFlight.remove(key, owned);
        }
    }

    private TerrainSampleTile generate(int tileX, int tileZ) {
        int originX = Math.multiplyExact(tileX, TILE_SIZE);
        int originZ = Math.multiplyExact(tileZ, TILE_SIZE);
        TerrainSample[] samples = pipeline.sampleTile(originX, originZ, TILE_SIZE);
        return new TerrainSampleTile(originX, originZ, samples);
    }

    private static TerrainSampleTile await(CompletableFuture<TerrainSampleTile> future) {
        try {
            return future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            throw propagate(cause == null ? exception : cause);
        }
    }

    private static RuntimeException propagate(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("Terrain sample tile generation failed", throwable);
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
}
