package dev.foucaultleon.flterraforged.engine.internal;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

/**
 * Small bounded concurrent cache with lock-free hit lookups and approximate FIFO eviction.
 *
 * <p>The cache is designed for immutable world-generation values. Reads never acquire a global
 * monitor. A newly retained value is appended to a concurrent insertion queue, and inserting
 * threads cooperatively trim entries until the configured bound is restored. Entry identity is
 * retained in both structures so a stale eviction token can never remove a newer value that reused
 * the same key after an earlier eviction.</p>
 *
 * @param <K> key type
 * @param <V> immutable cached value type
 */
public final class BoundedConcurrentCache<K, V> {

    private final int maximumSize;
    private final ConcurrentMap<K, Entry<K, V>> entries = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Entry<K, V>> insertionOrder = new ConcurrentLinkedQueue<>();

    /**
     * Creates a bounded cache.
     *
     * @param maximumSize maximum retained entry count
     */
    public BoundedConcurrentCache(int maximumSize) {
        if (maximumSize < 1) {
            throw new IllegalArgumentException("maximumSize must be >= 1");
        }
        this.maximumSize = maximumSize;
    }

    /**
     * Returns a completed value without acquiring a global cache lock.
     *
     * @param key lookup key
     * @return cached value, or {@code null} when absent
     */
    public V get(K key) {
        Entry<K, V> entry = entries.get(Objects.requireNonNull(key, "key"));
        return entry == null ? null : entry.value();
    }

    /**
     * Retains a value when the key is still absent and returns the canonical retained value.
     *
     * <p>The caller may have already coalesced expensive computation through a separate
     * single-flight map. This method performs only the cheap publication/eviction step.</p>
     *
     * @param key cache key
     * @param value immutable value to retain
     * @return {@code value} when inserted, otherwise the value already retained for {@code key}
     */
    public V putIfAbsent(K key, V value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Entry<K, V> candidate = new Entry<>(key, value);
        Entry<K, V> existing = entries.putIfAbsent(key, candidate);
        if (existing != null) {
            return existing.value();
        }
        insertionOrder.add(candidate);
        trim();
        return value;
    }

    /**
     * Returns the currently retained entry count.
     *
     * @return current cache size
     */
    public int size() {
        return entries.size();
    }

    /** Clears all retained values and stale insertion tokens. */
    public void clear() {
        entries.clear();
        insertionOrder.clear();
    }

    private void trim() {
        while (entries.size() > maximumSize) {
            Entry<K, V> eldest = insertionOrder.poll();
            if (eldest == null) {
                return;
            }
            entries.remove(eldest.key(), eldest);
        }
    }

    private record Entry<K, V>(K key, V value) {
    }
}
