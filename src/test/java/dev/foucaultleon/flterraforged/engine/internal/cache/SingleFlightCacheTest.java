package dev.foucaultleon.flterraforged.engine.internal.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class SingleFlightCacheTest {

    @Test
    void concurrentCallersShareOneExactKeyLoad() throws Exception {
        SingleFlightCache<Object> cache = new SingleFlightCache<>("test", 8);
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(12);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(12);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int worker = 0; worker < 12; worker++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return cache.get(42L, ignored -> {
                        loads.incrementAndGet();
                        return new Object();
                    });
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            Object first = futures.get(0).get(10, TimeUnit.SECONDS);
            for (Future<Object> future : futures) {
                assertSame(first, future.get(10, TimeUnit.SECONDS));
            }
            assertEquals(1, loads.get());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void unrelatedKeysNeverCollideOnLockStripes() throws Exception {
        SingleFlightCache<Long> cache = new SingleFlightCache<>("test", 8);
        CountDownLatch bothLoaders = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Long> first = executor.submit(() -> cache.get(0L, key -> {
                awaitBoth(bothLoaders);
                return key;
            }));
            Future<Long> second = executor.submit(() -> cache.get(64L, key -> {
                awaitBoth(bothLoaders);
                return key;
            }));
            assertEquals(0L, first.get(10, TimeUnit.SECONDS));
            assertEquals(64L, second.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void failedLoadIsSharedAndCanBeRetried() {
        SingleFlightCache<String> cache = new SingleFlightCache<>("test", 8);
        assertThrows(IllegalStateException.class, () -> cache.get(7L, ignored -> {
            throw new IllegalStateException("expected");
        }));
        assertEquals("retry", cache.get(7L, ignored -> "retry"));
    }

    @Test
    void recursiveMissFailsInsteadOfWaitingOnACycle() {
        SingleFlightCache<String> cache = new SingleFlightCache<>("test", 8);
        assertThrows(IllegalStateException.class, () ->
                cache.get(1L, ignored -> cache.get(2L, nested -> "unreachable")));
    }

    @Test
    void completedValuesRemainBoundedByLruCapacity() {
        SingleFlightCache<Long> cache = new SingleFlightCache<>("test", 2);
        cache.get(1L, key -> key);
        cache.get(2L, key -> key);
        cache.get(3L, key -> key);
        assertEquals(2, cache.size());
    }

    private static void awaitBoth(CountDownLatch latch) {
        latch.countDown();
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Independent loaders were serialized");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while testing independent loads", exception);
        }
    }
}
