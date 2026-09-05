package dev.foucaultleon.flterraforged.engine;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.foucaultleon.flterraforged.engine.api.EngineConfig;
import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.TerrainEngine;
import dev.foucaultleon.flterraforged.engine.api.TerrainWorld;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainEnvironmentSample;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public final class StartupLivenessTest {

    private static final EngineContext SPAWN_CONTEXT = new EngineContext(8675309L, -64, 320, 63);

    @Test
    void coldStructureEnvironmentProbeCompletesWithinStartupBudget() {
        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            try (TerrainEngine engine = new DefaultEngineProvider().create(EngineConfig.empty());
                 TerrainWorld world = engine.openWorld(SPAWN_CONTEXT)) {
                TerrainEnvironmentSample sample = world.environment(0, 0);
                assertNotNull(sample.terrainType());
                assertTrue(Double.isFinite(sample.surfaceHeight()));
            }
        });
    }

    @Test
    void coldSpawnTileCompletesWithinStartupBudget() {
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            try (TerrainEngine engine = new DefaultEngineProvider().create(EngineConfig.empty());
                 TerrainWorld world = engine.openWorld(SPAWN_CONTEXT)) {
                TerrainSample[] samples = world.sampleTile(-16, -16, 16);
                assertTrue(samples.length == 256);
                for (TerrainSample sample : samples) {
                    assertTrue(Double.isFinite(sample.surfaceHeight()));
                }
            }
        });
    }

    @Test
    void concurrentColdSameTileCompletesAndSharesCanonicalSamples() {
        assertTimeoutPreemptively(Duration.ofSeconds(30), () -> {
            try (TerrainEngine engine = new DefaultEngineProvider().create(EngineConfig.empty());
                 TerrainWorld world = engine.openWorld(SPAWN_CONTEXT)) {
                ExecutorService executor = Executors.newFixedThreadPool(8);
                CountDownLatch start = new CountDownLatch(1);
                try {
                    List<Future<TerrainSample>> futures = new ArrayList<>();
                    for (int index = 0; index < 8; index++) {
                        futures.add(executor.submit(() -> {
                            start.await();
                            return world.sample(0, 0);
                        }));
                    }
                    start.countDown();
                    TerrainSample canonical = futures.get(0).get(25, TimeUnit.SECONDS);
                    for (Future<TerrainSample> future : futures) {
                        assertSame(
                                canonical,
                                future.get(25, TimeUnit.SECONDS),
                                "cold concurrent callers must reuse one final tile");
                    }
                } finally {
                    executor.shutdownNow();
                }
            }
        });
    }
}
