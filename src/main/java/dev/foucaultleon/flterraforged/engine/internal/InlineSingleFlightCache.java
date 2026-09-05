package dev.foucaultleon.flterraforged.engine.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Bounded completed-value cache with synchronous single-flight miss coalescing.
 *
 * <p>The thread that wins a missing key computes that value directly on its current thread. No
 * executor task is submitted and no cache monitor is held while the loader runs. Concurrent callers
 * for the same key wait only for that owner's future, so one deterministic dataset is produced once
 * and then shared by every waiter.</p>
 *
 * <p>Loaders used with this cache must form an acyclic dependency graph. A same-thread recursive
 * request for a key it already owns is rejected immediately instead of waiting on itself. Engine
 * caches are deliberately layered so final-sample tiles may depend on hydrology maps, hydrology maps
 * may depend on erosion tiles, and erosion tiles may depend only on uncached base terrain.</p>
 *
 * @param <K> cache key type
 * @param <V> cached value type
 */
public final class InlineSingleFlightCache<K, V> {

    private final BoundedMap<K, V> completed;
    private final ConcurrentHashMap<K, Flight<V>> inFlight = new ConcurrentHashMap<>();

    /**
     * Creates a cache with a bounded completed-value LRU.
     *
     * @param maximumSize maximum number of completed values retained
     */
    public InlineSingleFlightCache(int maximumSize) {
        if (maximumSize < 1) {
            throw new IllegalArgumentException("maximumSize must be >= 1");
        }
        this.completed = new BoundedMap<>(maximumSize);
    }

    /**
     * Returns the cached value or synchronously computes the missing key exactly once.
     *
     * <p>The loader is never executed while holding the completed-cache monitor. Waiting callers do
     * not schedule work on, or consume additional work from, a world-generation executor.</p>
     *
     * @param key cache key
     * @param loader deterministic loader for a missing value
     * @return completed cached value
     */
    public V get(K key, Supplier<? extends V> loader) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(loader, "loader");

        V cached = completed(key);
        if (cached != null) {
            return cached;
        }

        Thread current = Thread.currentThread();
        Flight<V> mine = new Flight<>(current);
        Flight<V> existing = inFlight.putIfAbsent(key, mine);
        if (existing != null) {
            if (existing.owner == current) {
                throw new IllegalStateException("Recursive single-flight load for key " + key);
            }
            return await(existing.future);
        }

        try {
            // Close the small race between the first completed lookup and in-flight ownership.
            cached = completed(key);
            if (cached != null) {
                mine.future.complete(cached);
                return cached;
            }

            V loaded = Objects.requireNonNull(loader.get(), "cache loader returned null");
            V retained;
            synchronized (completed) {
                V secondLook = completed.get(key);
                if (secondLook == null) {
                    completed.put(key, loaded);
                    retained = loaded;
                } else {
                    retained = secondLook;
                }
            }
            mine.future.complete(retained);
            return retained;
        } catch (Throwable throwable) {
            mine.future.completeExceptionally(throwable);
            throw propagate(throwable);
        } finally {
            inFlight.remove(key, mine);
        }
    }

    /**
     * Clears completed values without interrupting computations already in progress.
     */
    public void clear() {
        synchronized (completed) {
            completed.clear();
        }
    }

    /**
     * Returns the number of retained completed values.
     *
     * @return completed cache size
     */
    public int completedSize() {
        synchronized (completed) {
            return completed.size();
        }
    }

    /**
     * Returns the number of keys currently owned by loader threads.
     *
     * @return number of active single-flight computations
     */
    public int inFlightSize() {
        return inFlight.size();
    }

    private V completed(K key) {
        synchronized (completed) {
            return completed.get(key);
        }
    }

    private static <V> V await(CompletableFuture<V> future) {
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
        return new IllegalStateException("Single-flight cache loader failed", throwable);
    }

    private static final class Flight<V> {

        private final Thread owner;
        private final CompletableFuture<V> future = new CompletableFuture<>();

        Flight(Thread owner) {
            this.owner = owner;
        }
    }

    private static final class BoundedMap<K, V> extends LinkedHashMap<K, V> {

        private static final long serialVersionUID = 1L;
        private final int maximumSize;

        BoundedMap(int maximumSize) {
            super(maximumSize + 1, 0.75F, true);
            this.maximumSize = maximumSize;
        }

        @Override
        protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
            return size() > maximumSize;
        }
    }
}
