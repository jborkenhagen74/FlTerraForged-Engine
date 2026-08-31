package dev.foucaultleon.flterraforged.engine.terrain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.foucaultleon.flterraforged.engine.DefaultTerrainWorld;
import dev.foucaultleon.flterraforged.engine.EngineSettings;
import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.river.RiverSample;
import dev.foucaultleon.flterraforged.engine.api.terrain.StandardTerrainTypes;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainType;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import dev.foucaultleon.flterraforged.engine.continent.Continent;
import dev.foucaultleon.flterraforged.engine.continent.ContinentCenter;
import dev.foucaultleon.flterraforged.engine.continent.ContinentSample;
import dev.foucaultleon.flterraforged.engine.noise.Noise2D;
import dev.foucaultleon.flterraforged.engine.terrain.populator.TerrainPopulator;
import dev.foucaultleon.flterraforged.engine.terrain.provider.DefaultTerrainProvider;
import dev.foucaultleon.flterraforged.engine.terrain.provider.TerrainProvider;
import dev.foucaultleon.flterraforged.engine.terrain.region.TerrainRegionSample;
import dev.foucaultleon.flterraforged.engine.terrain.region.TerrainRegionSampler;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

final class TerrainFoundationTest {

    @Test
    void terrainRegionsAreDeterministicAndBounded() {
        TerrainRegionSampler sampler = new TerrainRegionSampler(12345L, 0.00042D, 0.82D);
        TerrainRegionSample first = sampler.sample(1234.5D, -9876.25D);
        TerrainRegionSample second = sampler.sample(1234.5D, -9876.25D);
        assertEquals(first, second);
        assertTrue(first.id() >= 0.0D && first.id() <= 1.0D);
        assertTrue(first.neighborId() >= 0.0D && first.neighborId() <= 1.0D);
        assertTrue(first.edge() >= 0.0D && first.edge() <= 1.0D);
    }

    @Test
    void differentRegionSeedsChangeOwnership() {
        TerrainRegionSample first = new TerrainRegionSampler(1L, 0.00042D, 0.82D).sample(4200, -7200);
        TerrainRegionSample second = new TerrainRegionSampler(2L, 0.00042D, 0.82D).sample(4200, -7200);
        assertNotEquals(first, second);
    }

    @Test
    void defaultProviderExposesMultipleLandforms() {
        Noise2D rolling = (x, z) -> Math.sin(x * 0.01D) * Math.cos(z * 0.01D);
        Noise2D ridges = (x, z) -> Math.sin(x * 0.02D + z * 0.01D);
        Noise2D detail = (x, z) -> Math.sin(x * 0.08D) * 0.25D;
        TerrainProvider provider = new DefaultTerrainProvider(
                rolling, ridges, detail, 36.0D, 52.0D,
                0.24D, 0.23D, 0.16D, 0.17D, 0.20D);
        assertEquals(StandardTerrainTypes.PLAINS, provider.resolve(0.1D).type());
        assertEquals(StandardTerrainTypes.HILLS, provider.resolve(0.3D).type());
        assertEquals(StandardTerrainTypes.VALLEY, provider.resolve(0.55D).type());
        assertEquals(StandardTerrainTypes.PLATEAU, provider.resolve(0.7D).type());
        assertEquals(StandardTerrainTypes.MOUNTAINS, provider.resolve(0.95D).type());
    }

    @Test
    void blenderCreatesCompositeOnlyNearBoundaries() {
        Noise2D zero = (x, z) -> 0.0D;
        Terrain plains = new ConfiguredTerrain(
                StandardTerrainTypes.PLAINS,
                TerrainCategory.FLAT,
                zero,
                zero,
                4.0D,
                8.0D,
                0.0D,
                0.0D);
        Terrain hills = new ConfiguredTerrain(
                StandardTerrainTypes.HILLS,
                TerrainCategory.HILLS,
                zero,
                zero,
                8.0D,
                18.0D,
                0.0D,
                0.0D);
        assertInstanceOf(CompositeTerrain.class, Blender.blend(plains, hills, 0.0D, 0.3D));
        assertEquals(plains, Blender.blend(plains, hills, 0.4D, 0.3D));
    }

    @Test
    void multiRegionBlenderRemovesSecondaryNeighborHeightSeams() {
        EngineContext world = context(1L);
        Continent continent = (x, z) -> new ContinentSample(
                0.5D, 0.8D, new ContinentCenter(0, 0));
        TerrainRegionSampler sampler = new TerrainRegionSampler(0x1234ABCDL, 0.004D, 0.78D);
        TerrainProvider provider = selector -> {
            if (selector < 0.24D) {
                return flatTerrain(StandardTerrainTypes.PLAINS, TerrainCategory.FLAT, 68.0D);
            }
            if (selector < 0.47D) {
                return flatTerrain(StandardTerrainTypes.HILLS, TerrainCategory.HILLS, 88.0D);
            }
            if (selector < 0.63D) {
                return flatTerrain(StandardTerrainTypes.VALLEY, TerrainCategory.VALLEY, 58.0D);
            }
            if (selector < 0.80D) {
                return flatTerrain(StandardTerrainTypes.PLATEAU, TerrainCategory.PLATEAU, 108.0D);
            }
            return flatTerrain(StandardTerrainTypes.MOUNTAINS, TerrainCategory.MOUNTAINS, 138.0D);
        };
        TerrainPopulator populator = new TerrainPopulator(world, continent, sampler, provider, 0.50D);

        Cell left = populator.populate(new Cell(), -4, 771);
        Cell right = populator.populate(new Cell(), -3, 771);

        assertTrue(
                Math.abs(left.height - right.height) < 1.0D,
                "multi-region blending must not create a secondary-neighbor cliff");
    }

    @Test
    void lakeShoreUsesCanonicalSemanticWithoutNewApiConstant() {
        TerrainClassifier classifier = new TerrainClassifier();
        TerrainType result = classifier.classify(
                StandardTerrainTypes.PLAINS,
                70.0D,
                63,
                0.1D,
                0.5D,
                RiverSample.UNAVAILABLE,
                false,
                true);
        assertEquals(TerrainType.of(StandardTerrainTypes.NAMESPACE, "lake_shore"), result);
    }

    @Test
    void defaultWorldUsesTerrainRegionsAndProducesFractionalHeights() {
        DefaultTerrainWorld world = new DefaultTerrainWorld(context(778899L), EngineSettings.defaults());
        Set<Object> types = new HashSet<>();
        boolean fractional = false;
        for (int z = -12000; z <= 12000; z += 1200) {
            for (int x = -12000; x <= 12000; x += 1200) {
                TerrainSample sample = world.sample(x, z);
                types.add(sample.terrainType());
                fractional |= Math.abs(sample.surfaceHeight() - Math.rint(sample.surfaceHeight())) > 1.0E-9D;
            }
        }
        assertTrue(types.size() >= 4, "expected several semantic terrain types");
        assertTrue(fractional, "expected continuous fractional terrain heights");
    }

    @Test
    void worldTerrainSamplingRemainsThreadSafe() throws Exception {
        DefaultTerrainWorld world = new DefaultTerrainWorld(context(998877L), EngineSettings.defaults());
        TerrainSample expected = world.sample(4321, -8765);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<TerrainSample>> futures = new ArrayList<>();
            for (int i = 0; i < 128; i++) {
                futures.add(executor.submit(() -> world.sample(4321, -8765)));
            }
            for (Future<TerrainSample> future : futures) {
                assertEquals(expected, future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static Terrain flatTerrain(
            TerrainType type,
            TerrainCategory category,
            double height) {
        return new Terrain() {
            @Override
            public TerrainType type() {
                return type;
            }

            @Override
            public TerrainCategory category() {
                return category;
            }

            @Override
            public double height(TerrainContext terrainContext) {
                return height;
            }
        };
    }

    private static EngineContext context(long seed) {
        return new EngineContext(seed, -64, 320, 63);
    }
}
