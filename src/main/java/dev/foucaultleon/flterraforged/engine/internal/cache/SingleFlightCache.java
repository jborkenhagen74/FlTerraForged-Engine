package dev.foucaultleon.flterraforged.engine.internal.cache;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.function.LongFunction;

/**
 * Bounded completed-value cache with one exact-key computation shared by concurrent callers.
 *
 * <p>The loader never runs while a cache monitor is held. The first caller installs an in-flight
 * future and computes the value synchronously; concurrent callers for that key wait interruptibly
 * on the same future and receive the same immutable object. Unrelated keys never collide on an
 * artificial stripe monitor. Completed values use a bounded access-order LRU.</p>
 *
 * <p>A loader must not request an uncached key from the same cache. Such recursion could create a
 * future wait cycle between worker threads, so it fails immediately with a diagnostic exception.
 * Engine cache dependencies must instead remain an acyclic high-level-to-low-level graph.</p>
 *
 * @param <V> immutable cached value type
 */
public final class SingleFlightCache<V> {

    private final String name;
    private final int maximumSize;
    private final Map<Long, V> completed;
    private final ConcurrentMap<Long, CompletableFuture<V>> inFlight = new ConcurrentHashMap<>();
    private final ThreadLocal<Set<Long>> loadingKeys = ThreadLocal.withInitial(HashSet::new);

    /**
     * Creates a cache.
     *
     * @param name diagnostic dataset name
     * @param maximumSize maximum completed values retained by the LRU
     */
    public SingleFlightCache(String name, int maximumSize) {
        this.name = Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (maximumSize < 1) {
            throw new IllegalArgumentException("maximumSize must be >= 1");
        }
        this.maximumSize = maximumSize;
        this.completed = new LinkedHashMap<>(maximumSize + 1, 0.75F, true);
    }

    /**
     * Returns a completed value or loads it exactly once for the current concurrent miss wave.
     *
     * @param key exact dataset key
     * @param loader synchronous immutable-value loader
     * @return cached or newly loaded value
     */
    public V get(long key, LongFunction<? extends V> loader) {
        Objects.requireNonNull(loader, "loader");
        V ready = completedValue(key);
        if (ready != null) {
            return ready;
        }

        Set<Long> active = loadingKeys.get();
        if (!active.isEmpty()) {
            throw new IllegalStateException(
                    "Recursive miss in " + name + " cache while loading " + active + ": " + key);
        }

        CompletableFuture<V> owned = new CompletableFuture<>();
        CompletableFuture<V> shared = inFlight.putIfAbsent(key, owned);
        if (shared != null) {
            return await(key, shared);
        }

        active.add(key);
        try {
            V value = Objects.requireNonNull(loader.apply(key), "cache loader result");
            retain(key, value);
            owned.complete(value);
            return value;
        } catch (RuntimeException | Error failure) {
            owned.completeExceptionally(failure);
            throw failure;
        } finally {
            active.remove(key);
            if (active.isEmpty()) {
                loadingKeys.remove();
            }
            inFlight.remove(key, owned);
        }
    }

    /** Removes completed values without interrupting owners of currently loading datasets. */
    public void clear() {
        synchronized (completed) {
            completed.clear();
        }
    }

    /**
     * Returns the number of completed values currently retained.
     *
     * @return bounded completed-value count
     */
    public int size() {
        synchronized (completed) {
            return completed.size();
        }
    }

    private V completedValue(long key) {
        synchronized (completed) {
            return completed.get(key);
        }
    }

    private void retain(long key, V value) {
        synchronized (completed) {
            completed.put(key, value);
            while (completed.size() > maximumSize) {
                Iterator<Long> eldest = completed.keySet().iterator();
                eldest.next();
                eldest.remove();
            }
        }
    }

    private V await(long key, CompletableFuture<V> shared) {
        try {
            return shared.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Interrupted while waiting for " + name + " dataset " + key,
                    exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(
                    "Failed to load " + name + " dataset " + key,
                    cause);
        }
    }
}
