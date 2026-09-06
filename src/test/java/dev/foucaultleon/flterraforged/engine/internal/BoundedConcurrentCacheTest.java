package dev.foucaultleon.flterraforged.engine.internal;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

final class BoundedConcurrentCacheTest {

    @Test
    void cacheRemainsBoundedAfterManyInsertions() {
        BoundedConcurrentCache<Integer, Integer> cache = new BoundedConcurrentCache<>(32);
        for (int index = 0; index < 4096; index++) {
            cache.putIfAbsent(index, index);
        }
        assertTrue(cache.size() <= 32, "bounded cache must trim completed entries");
    }

    @Test
    void concurrentPublicationRetainsOneCanonicalValuePerKey() throws Exception {
        BoundedConcurrentCache<Integer, Object> cache = new BoundedConcurrentCache<>(32);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int index = 0; index < 64; index++) {
                futures.add(executor.submit(() -> cache.putIfAbsent(7, new Object())));
            }
            Object canonical = futures.get(0).get();
            for (Future<Object> future : futures) {
                assertSame(canonical, future.get());
            }
            assertSame(canonical, cache.get(7));
        } finally {
            executor.shutdownNow();
        }
    }
}
