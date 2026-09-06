package dev.foucaultleon.flterraforged.engine;

import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;
import dev.foucaultleon.flterraforged.engine.internal.BoundedConcurrentCache;
import dev.foucaultleon.flterraforged.engine.pipeline.WorldgenPipeline;
import java.util.Arrays;
import java.util.HashSet;
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
 * Completed tiles are stored in a bounded concurrent cache whose hit path does not acquire a global
 * monitor. Concurrent cold misses for the same tile are coalesced through a single-flight map: one
 * caller computes synchronously on its current worker while all other callers reuse that result. No
 * additional task is submitted to a world-generation executor.</p>
 *
 * <p>R46 restores 16x16 tiles after the R45 8x8 sparse-sampling experiment. Minecraft's dominant
 * generation path consumes complete 16x16 chunks: four 8x8 tiles each require their own two-block
 * pipeline halo and therefore evaluate 400 pipeline cells for one dense chunk, while one 16x16 tile
 * evaluates only an 18x18 envelope, or 324 cells. Structure-stage sparse sampling is reduced in the
 * Minecraft adapter instead of forcing the shared final-sample cache into a dense-path regression.
 * The bounded cache retains 1024 tiles, preserving the same 262144 final X/Z sample capacity.</p>
 *
 * <p>A thread-local ownership guard turns accidental recursive same-key requests into an immediate
 * diagnostic failure instead of letting a worker join its own unfinished future.</p>
 */
final class WorldSampleCache {

    static final int TILE_SIZE = 16;
    static final int DEFAULT_MAXIMUM_TILES = 1024;

    private final WorldgenPipeline pipeline;
    private final BoundedConcurrentCache<Long, TerrainSampleTile> cache;
    private final ConcurrentMap<Long, CompletableFuture<TerrainSampleTile>> inFlight =
            new ConcurrentHashMap<>();
    private final ThreadLocal<Set<Long>> ownedKeys = ThreadLocal.withInitial(HashSet::new);

    WorldSampleCache(WorldgenPipeline pipeline) {
        this(pipeline, DEFAULT_MAXIMUM_TILES);
    }

    WorldSampleCache(WorldgenPipeline pipeline, int maximumTiles) {
        this.pipeline = Objects.requireNonNull(pipeline, "pipeline");
        this.cache = new BoundedConcurrentCache<>(maximumTiles);
    }

    TerrainSample sample(int x, int z) {
        int tileX = Math.floorDiv(x, TILE_SIZE);
        int tileZ = Math.floorDiv(z, TILE_SIZE);
        long key = key(tileX, tileZ);

        TerrainSampleTile tile = cache.get(key);
        if (tile == null) {
            tile = loadSingleFlight(key, tileX, tileZ);
        }
        return tile.sample(x, z);
    }

    void clear() {
        cache.clear();
    }

    int cachedTiles() {
        return cache.size();
    }

    int inFlightTiles() {
        return inFlight.size();
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
            TerrainSampleTile retained = cache.putIfAbsent(key, generated);
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
}
