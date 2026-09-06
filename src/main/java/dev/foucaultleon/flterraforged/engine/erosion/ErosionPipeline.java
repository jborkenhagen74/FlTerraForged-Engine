package dev.foucaultleon.flterraforged.engine.erosion;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import dev.foucaultleon.flterraforged.engine.cell.CellLookup;
import dev.foucaultleon.flterraforged.engine.internal.BoundedConcurrentCache;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Deterministic hydraulic/thermal erosion stage wrapping the base terrain-cell lookup.
 *
 * <p>Completed immutable erosion regions are retained in a bounded concurrent cache. Cold misses
 * use exact-key single-flight ownership: one caller generates a region synchronously on its current
 * thread and duplicate callers for that exact region reuse the same result. Independent regions do
 * not share a cache monitor or a hash-stripe generation lock, so parallel spawn generation cannot
 * serialize unrelated erosion work merely because two region keys collide on the same stripe.</p>
 *
 * <p>A same-thread recursive ownership guard fails immediately instead of allowing a worker to wait
 * on its own unfinished region. Region generation itself only reads the pre-erosion terrain lookup,
 * keeping the dependency graph acyclic.</p>
 */
public final class ErosionPipeline implements CellLookup {

    private final CellLookup baseTerrain;
    private final ErosionSettings settings;
    private final ErosionTileGenerator generator;
    private final BoundedConcurrentCache<Long, ErosionTile> cache;
    private final ConcurrentMap<Long, CompletableFuture<ErosionTile>> inFlight =
            new ConcurrentHashMap<>();
    private final ThreadLocal<Set<Long>> ownedRegionKeys = ThreadLocal.withInitial(HashSet::new);

    /**
     * Creates an erosion pipeline.
     *
     * @param seed world seed used for deterministic droplet launches
     * @param world immutable world context
     * @param baseTerrain terrain lookup before physical erosion
     * @param settings erosion settings
     */
    public ErosionPipeline(long seed, EngineContext world, CellLookup baseTerrain, ErosionSettings settings) {
        this.baseTerrain = Objects.requireNonNull(baseTerrain, "baseTerrain");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.generator = new ErosionTileGenerator(
                seed,
                Objects.requireNonNull(world, "world"),
                baseTerrain,
                settings);
        this.cache = new BoundedConcurrentCache<>(settings.cacheSize());
    }

    /** {@inheritDoc} */
    @Override
    public void lookup(int x, int z, Cell target) {
        Objects.requireNonNull(target, "target");
        baseTerrain.lookup(x, z, target);
        ErosionSample sample = sample(x, z);
        target.heightErosion = sample.erodedHeight();
        target.height = sample.erodedHeight();
        target.gradient = sample.gradient();
        target.erosion = sample.erosion();
        target.sediment = sample.sediment();
        target.erosionMask = sample.erosion() > 1.0E-4D || sample.sediment() > 1.0E-4D;
    }

    /**
     * Samples erosion data without allocating a cell.
     *
     * @param x world X coordinate
     * @param z world Z coordinate
     * @return erosion result
     */
    public ErosionSample sample(int x, int z) {
        int regionX = Math.floorDiv(x, settings.regionSize());
        int regionZ = Math.floorDiv(z, settings.regionSize());
        long key = key(regionX, regionZ);
        ErosionTile completed = cache.get(key);
        return completed == null
                ? loadSingleFlight(key, regionX, regionZ).sample(x, z, settings.maximumHeightChange())
                : completed.sample(x, z, settings.maximumHeightChange());
    }

    private ErosionTile loadSingleFlight(long key, int regionX, int regionZ) {
        Set<Long> localOwnership = ownedRegionKeys.get();
        CompletableFuture<ErosionTile> owned = new CompletableFuture<>();
        CompletableFuture<ErosionTile> existing = inFlight.putIfAbsent(key, owned);
        if (existing != null) {
            if (localOwnership.contains(key)) {
                throw new IllegalStateException(
                        "Recursive erosion-region load detected for region " + regionX + ',' + regionZ);
            }
            return await(existing);
        }

        if (!localOwnership.add(key)) {
            inFlight.remove(key, owned);
            throw new IllegalStateException(
                    "Recursive erosion-region ownership detected for region " + regionX + ',' + regionZ);
        }
        try {
            ErosionTile generated = generator.generate(regionX, regionZ);
            ErosionTile retained = cache.putIfAbsent(key, generated);
            owned.complete(retained);
            return retained;
        } catch (Throwable throwable) {
            owned.completeExceptionally(throwable);
            throw propagate(throwable);
        } finally {
            localOwnership.remove(key);
            if (localOwnership.isEmpty()) {
                ownedRegionKeys.remove();
            }
            inFlight.remove(key, owned);
        }
    }

    private static ErosionTile await(CompletableFuture<ErosionTile> future) {
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
        return new IllegalStateException("Erosion-region generation failed", throwable);
    }

    private static long key(int regionX, int regionZ) {
        return (((long) regionX) << 32) ^ (regionZ & 0xFFFFFFFFL);
    }
}
