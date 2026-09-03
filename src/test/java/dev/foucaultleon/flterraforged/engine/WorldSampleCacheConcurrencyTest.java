package dev.foucaultleon.flterraforged.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.foucaultleon.flterraforged.engine.api.EngineConfig;
import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.TerrainEngine;
import dev.foucaultleon.flterraforged.engine.api.TerrainWorld;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Regression tests for parallel cold-cache sampling. */
public final class WorldSampleCacheConcurrencyTest {

    /**
     * Ensures concurrent cold callers remain live and deterministic without waiting for cache ownership.
     *
     * @throws Exception when a worker cannot complete within the liveness timeout
     */
    @Test
    void concurrentColdSamplingRemainsLiveAndDeterministic() throws Exception {
        try (TerrainEngine engine = new DefaultEngineProvider().create(EngineConfig.empty());
                TerrainWorld world = engine.openWorld(new EngineContext(246813579L, -64, 320, 63))) {
            int workerCount = 8;
            CountDownLatch ready = new CountDownLatch(workerCount);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(workerCount);
            try {
                List<Future<TerrainSample>> futures = new ArrayList<>();
                for (int index = 0; index < workerCount; index++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return world.sample(7, 11);
                    }));
                }

                assertTrue(
                        ready.await(10, TimeUnit.SECONDS),
                        "Cold-cache workers did not reach the start barrier");
                start.countDown();

                TerrainSample first = futures.get(0).get(60, TimeUnit.SECONDS);
                for (Future<TerrainSample> future : futures) {
                    assertEquals(
                            first,
                            future.get(60, TimeUnit.SECONDS),
                            "Concurrent cold misses must remain deterministic");
                }
            } finally {
                start.countDown();
                executor.shutdownNow();
            }
        }
    }
}
