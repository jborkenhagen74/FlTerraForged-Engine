package dev.foucaultleon.flterraforged.engine;

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

public final class EngineSmokeTest {

    private EngineSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        providerIsDiscoverable();
        deterministicAndSeeded();
        supportsDeclaredCapabilities();
        concurrentSamplingIsStable();
        System.out.println("FlTerraForged-Engine smoke tests passed");
    }

    private static void providerIsDiscoverable() {
        EngineProvider provider = ServiceLoader.load(EngineProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .filter(candidate -> candidate.id().equals(DefaultEngineProvider.ID))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Default EngineProvider not discoverable"));
        check(provider.engineVersion().equals(DefaultEngineProvider.VERSION), "Unexpected engine version");
    }

    private static void deterministicAndSeeded() {
        TerrainSample a = sample(42L, 1200, -850);
        TerrainSample b = sample(42L, 1200, -850);
        TerrainSample c = sample(43L, 1200, -850);
        check(a.equals(b), "Same seed/coordinate must produce identical sample");
        check(!a.equals(c), "Different seed should change the terrain sample");
        check(Double.isFinite(a.surfaceHeight()), "Surface height must be finite");
    }

    private static void supportsDeclaredCapabilities() {
        try (TerrainEngine engine = new DefaultEngineProvider().create(EngineConfig.empty())) {
            for (EngineCapability capability : EngineCapability.values()) {
                check(engine.capabilities().supports(capability), "Missing capability: " + capability);
            }
        }
    }

    private static void concurrentSamplingIsStable() throws Exception {
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
                        .<Callable<TerrainSample>>map(point -> () -> world.sample(point[0], point[1]))
                        .toList();
                List<Future<TerrainSample>> futures = executor.invokeAll(tasks);
                for (int i = 0; i < futures.size(); i++) {
                    check(expected.get(i).equals(futures.get(i).get()),
                            "Concurrent result differs at index " + i);
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

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
