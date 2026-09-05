package dev.foucaultleon.flterraforged.engine.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public final class InlineSingleFlightCacheTest {

    @Test
    void concurrentColdMissRunsOneLoader() throws Exception {
        InlineSingleFlightCache<String, String> cache = new InlineSingleFlightCache<>(4);
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return cache.get("shared", () -> {
                        loads.incrementAndGet();
                        try {
                            Thread.sleep(100L);
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                        }
                        return "value";
                    });
                }));
            }
            start.countDown();
            for (Future<String> future : futures) {
                assertEquals("value", future.get(5, TimeUnit.SECONDS));
            }
            assertEquals(1, loads.get(), "one cold key must be generated exactly once");
            assertEquals(1, cache.completedSize());
            assertEquals(0, cache.inFlightSize());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void sameThreadRecursiveLoadFailsInsteadOfWaitingOnItself() {
        InlineSingleFlightCache<String, String> cache = new InlineSingleFlightCache<>(4);
        assertThrows(
                IllegalStateException.class,
                () -> cache.get("recursive", () -> cache.get("recursive", () -> "unreachable")));
        assertEquals(0, cache.inFlightSize());
    }
}
