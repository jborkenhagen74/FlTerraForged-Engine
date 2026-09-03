package dev.foucaultleon.flterraforged.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.foucaultleon.flterraforged.engine.api.EngineCapability;
import dev.foucaultleon.flterraforged.engine.api.EngineConfig;
import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.EngineProvider;
import dev.foucaultleon.flterraforged.engine.api.TerrainEngine;
import dev.foucaultleon.flterraforged.engine.api.TerrainWorld;
import dev.foucaultleon.flterraforged.engine.api.terrain.StandardTerrainTypes;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public final class EngineSmokeTest {

    @Test
    void providerIsDiscoverable() {
        EngineProvider provider = ServiceLoader.load(EngineProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(candidate -> candidate.id().equals(DefaultEngineProvider.ID))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Default EngineProvider not discoverable"));

        assertEquals(DefaultEngineProvider.VERSION, provider.engineVersion());
    }

    @Test
    void deterministicAndSeeded() {
        TerrainSample first = sample(42L, 1200, -850);
        TerrainSample second = sample(42L, 1200, -850);
        TerrainSample differentSeed = sample(43L, 1200, -850);

        assertEquals(first, second, "Same seed/coordinate must produce identical sample");
        assertNotEquals(first, differentSeed, "Different seed should change the terrain sample");
        assertTrue(Double.isFinite(first.surfaceHeight()), "Surface height must be finite");
    }

    @Test
    void supportsDeclaredCapabilities() {
        try (TerrainEngine engine = new DefaultEngineProvider().create(EngineConfig.empty())) {
            for (EngineCapability capability : EngineCapability.values()) {
                assertTrue(
                        engine.capabilities().supports(capability),
                        () -> "Missing capability: " + capability
                );
            }
        }
    }

    @Test
    void repeatedWorldSamplesReuseImmutableCachedValue() {
        try (TerrainEngine engine = new DefaultEngineProvider().create(EngineConfig.empty());
             TerrainWorld world = engine.openWorld(new EngineContext(12345L, -64, 320, 63))) {
            TerrainSample first = world.sample(34, -19);
            TerrainSample second = world.sample(34, -19);
            assertSame(first, second, "Repeated final samples should come from the shared world cache");
        }
    }

    @Test
    void concurrentSamplingIsStable() throws Exception {
        try (TerrainEngine engine = new DefaultEngineProvider().create(EngineConfig.empty());
             TerrainWorld world = engine.openWorld(new EngineContext(987654321L, -64, 320, 63))) {
            List<int[]> coordinates = new ArrayList<>();
            for (int i = 0; i < 256; i++) {
                coordinates.add(new int[] {i * 17 - 2000, i * -31 + 4000});
            }

            List<TerrainSample> expected = coordinates.stream()
                    .map(point -> world.sample(point[0], point[1]))
                    .toList();

            ExecutorService executor = Executors.newFixedThreadPool(8);
            try {
                List<Callable<TerrainSample>> tasks = coordinates.stream()
                        .<Callable<TerrainSample>>map(
                                point -> () -> world.sample(point[0], point[1])
                        )
                        .toList();
                List<Future<TerrainSample>> futures = executor.invokeAll(tasks);

                for (int i = 0; i < futures.size(); i++) {
                    assertEquals(
                            expected.get(i),
                            futures.get(i).get(),
                            "Concurrent result differs at index " + i
                    );
                }
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    void concurrentColdSamplingCoalescesOneTileInstance() throws Exception {
        try (TerrainEngine engine = new DefaultEngineProvider().create(EngineConfig.empty());
             TerrainWorld world = engine.openWorld(new EngineContext(246813579L, -64, 320, 63))) {
            int workerCount = 8;
            CountDownLatch ready = new CountDownLatch(workerCount);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(workerCount);
            try {
                List<Future<TerrainSample>> futures = new ArrayList<>();
                for (int i = 0; i < workerCount; i++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return world.sample(7, 11);
                    }));
                }
                assertTrue(ready.await(10, TimeUnit.SECONDS),
                        "Cold-cache workers did not reach the start barrier");
                start.countDown();

                TerrainSample first = futures.get(0).get(60, TimeUnit.SECONDS);
                for (Future<TerrainSample> future : futures) {
                    assertSame(first, future.get(60, TimeUnit.SECONDS),
                            "Concurrent cold misses must return the one cached tile value");
                }
            } finally {
                start.countDown();
                executor.shutdownNow();
            }
        }
    }

    @Test
    void conservativeMarineFastPathNeverApprovesInlandOrShallowTerrain() {
        EngineConfig config = EngineConfig.of(Map.of("preset", "central_europe"));
        try (TerrainEngine engine = new DefaultEngineProvider().create(config);
             TerrainWorld world = engine.openWorld(new EngineContext(8675309L, -64, 320, 63))) {
            for (int z = -4; z <= 4; z++) {
                for (int x = -4; x <= 4; x++) {
                    int blockX = x * 32 + 8;
                    int blockZ = z * 32 + 8;
                    if (!world.isMarine(blockX, blockZ, 5.0D)) {
                        continue;
                    }
                    TerrainSample sample = world.sample(blockX, blockZ);
                    assertTrue(
                            StandardTerrainTypes.OCEAN.equals(sample.terrainType())
                                    || StandardTerrainTypes.COAST.equals(sample.terrainType()),
                            "Fast marine path approved non-marine terrain at "
                                    + blockX + "," + blockZ);
                    assertTrue(
                            world.context().seaLevel() - sample.surfaceHeight() >= 5.0D,
                            "Fast marine path approved shallow terrain at "
                                    + blockX + "," + blockZ);
                }
            }
        }
    }

    @Test
    void spawnSizedWorkingSetSurvivesTheNextGenerationStage() {
        try (TerrainEngine engine = new DefaultEngineProvider().create(EngineConfig.empty());
             TerrainWorld world = engine.openWorld(new EngineContext(135792468L, -64, 320, 63))) {
            TerrainSample first = null;
            for (int chunkZ = -8; chunkZ <= 8; chunkZ++) {
                for (int chunkX = -8; chunkX <= 8; chunkX++) {
                    TerrainSample sample = world.sample(chunkX * 16 + 8, chunkZ * 16 + 8);
                    if (first == null) {
                        first = sample;
                    }
                }
            }
            assertSame(first, world.sample(-8 * 16 + 8, -8 * 16 + 8),
                    "A 17x17 spawn working set must not be evicted between chunk stages");
        }
    }

    private static TerrainSample sample(long seed, int x, int z) {
        try (TerrainEngine engine = new DefaultEngineProvider().create(EngineConfig.empty());
             TerrainWorld world = engine.openWorld(new EngineContext(seed, -64, 320, 63))) {
            return world.sample(x, z);
        }
    }
}
