package dev.foucaultleon.flterraforged.engine.chunk;

import dev.foucaultleon.flterraforged.engine.WorldSampleCache;
import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.chunk.ChunkSnapshot;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;
import dev.foucaultleon.flterraforged.engine.internal.BoundedConcurrentCache;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CompletionException;

/**
 * Exact-key single-flight cache for immutable complete chunk snapshots.
 *
 * <p>Only one thread owns generation of a missing chunk key. Concurrent duplicate requests join
 * that exact result; independent chunk keys never share a lock. Snapshot generation may read the
 * lower-level final terrain-sample cache but never calls back into this cache.</p>
 */
public final class ChunkSnapshotCache {

    /** Conservative bound: a 384-high snapshot is roughly 100 KiB before object overhead. */
    static final int DEFAULT_MAXIMUM_SNAPSHOTS = 128;

    private final WorldSampleCache samples;
    private final SubsurfaceGenerator generator;
    private final BoundedConcurrentCache<Long, ChunkSnapshot> completed =
            new BoundedConcurrentCache<>(DEFAULT_MAXIMUM_SNAPSHOTS);
    private final ConcurrentMap<Long, CompletableFuture<ChunkSnapshot>> inFlight =
            new ConcurrentHashMap<>();
    private final ThreadLocal<Set<Long>> ownedKeys = ThreadLocal.withInitial(HashSet::new);

    /**
     * Creates a chunk snapshot cache over the shared final terrain-sample cache.
     *
     * @param context immutable world context
     * @param samples lower-level final terrain-sample cache
     */
    public ChunkSnapshotCache(EngineContext context, WorldSampleCache samples) {
        this.samples = samples;
        this.generator = new SubsurfaceGenerator(context);
    }

    /**
     * Returns the canonical immutable snapshot for a chunk.
     *
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     * @return complete natural-world snapshot
     */
    public ChunkSnapshot get(int chunkX, int chunkZ) {
        long key = key(chunkX, chunkZ);
        ChunkSnapshot cached = completed.get(key);
        if (cached != null) {
            return cached;
        }
        if (ownedKeys.get().contains(key)) {
            throw new IllegalStateException("recursive chunk snapshot generation for key " + key);
        }
        CompletableFuture<ChunkSnapshot> owned = new CompletableFuture<>();
        CompletableFuture<ChunkSnapshot> existing = inFlight.putIfAbsent(key, owned);
        if (existing != null) {
            return join(existing);
        }

        Set<Long> ownership = ownedKeys.get();
        ownership.add(key);
        try {
            TerrainSample[] terrain = sampleChunk(chunkX, chunkZ);
            ChunkSnapshot generated = generator.generate(chunkX, chunkZ, terrain);
            ChunkSnapshot canonical = completed.putIfAbsent(key, generated);
            owned.complete(canonical);
            return canonical;
        } catch (Throwable failure) {
            owned.completeExceptionally(failure);
            throw failure;
        } finally {
            ownership.remove(key);
            if (ownership.isEmpty()) {
                ownedKeys.remove();
            }
            inFlight.remove(key, owned);
        }
    }

    /** Clears completed snapshots; no in-flight generation is cancelled. */
    public void clear() {
        completed.clear();
    }

    /**
     * Returns the number of completed snapshots currently retained.
     *
     * @return retained snapshot count
     */
    public int size() {
        return completed.size();
    }

    private TerrainSample[] sampleChunk(int chunkX, int chunkZ) {
        TerrainSample[] result = new TerrainSample[256];
        int originX = chunkX << 4;
        int originZ = chunkZ << 4;
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                result[localZ * 16 + localX] = samples.sample(originX + localX, originZ + localZ);
            }
        }
        return result;
    }

    private static ChunkSnapshot join(CompletableFuture<ChunkSnapshot> future) {
        try {
            return future.join();
        } catch (CompletionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw failure;
        }
    }

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }
}
