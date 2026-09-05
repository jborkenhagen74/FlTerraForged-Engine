package dev.foucaultleon.flterraforged.engine;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    void alignedTileSamplingMatchesPointSampling() {
        try (TerrainEngine engine = new DefaultEngineProvider().create(EngineConfig.empty());
             TerrainWorld world = engine.openWorld(new EngineContext(24680L, -64, 320, 63))) {
            int originX = -32;
            int originZ = 48;
            TerrainSample[] tile = world.sampleTile(originX, originZ, 16);
            TerrainSample[] expected = new TerrainSample[256];
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    expected[localZ * 16 + localX] = world.sample(originX + localX, originZ + localZ);
                }
            }
            assertArrayEquals(expected, tile, "Bulk tile sampling must match canonical point samples");
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

    private static TerrainSample sample(long seed, int x, int z) {
        try (TerrainEngine engine = new DefaultEngineProvider().create(EngineConfig.empty());
             TerrainWorld world = engine.openWorld(new EngineContext(seed, -64, 320, 63))) {
            return world.sample(x, z);
        }
    }
}
