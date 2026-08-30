package dev.foucaultleon.flterraforged.engine.erosion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.terrain.StandardTerrainTypes;
import dev.foucaultleon.flterraforged.engine.cell.CellLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

final class ErosionFoundationTest {

    @Test
    void hydraulicAndThermalErosionModifySyntheticReliefDeterministically() {
        EngineContext world = new EngineContext(1234L, -64, 320, 63);
        ErosionSettings settings = settings();
        ErosionPipeline first = new ErosionPipeline(1234L, world, syntheticTerrain(), settings);
        ErosionPipeline second = new ErosionPipeline(1234L, world, syntheticTerrain(), settings);
        int changed = 0;
        int deposited = 0;
        for (int z = -64; z <= 64; z += 4) {
            for (int x = -64; x <= 64; x += 4) {
                ErosionSample a = first.sample(x, z);
                ErosionSample b = second.sample(x, z);
                assertEquals(a, b);
                assertTrue(Math.abs(a.heightDelta()) <= settings.maximumHeightChange() + 1.0E-9D);
                if (a.erosion() > 1.0E-6D) {
                    changed++;
                }
                if (a.sediment() > 1.0E-6D) {
                    deposited++;
                }
            }
        }
        assertTrue(changed > 0, "expected hydraulically or thermally eroded samples");
        assertTrue(deposited > 0, "expected sediment deposition");
    }

    @Test
    void erosionPipelineWritesCellStageSignals() {
        ErosionPipeline erosion = new ErosionPipeline(
                55L,
                new EngineContext(55L, -64, 320, 63),
                syntheticTerrain(),
                settings());
        var cell = erosion.lookup(12, -9);
        assertEquals(cell.heightErosion, cell.height);
        assertTrue(cell.gradient >= 0.0D);
        assertTrue(cell.erosion >= 0.0D && cell.erosion <= 1.0D);
        assertTrue(cell.sediment >= 0.0D);
    }

    @Test
    void concurrentErosionSamplingIsStable() throws Exception {
        ErosionPipeline erosion = new ErosionPipeline(
                9988L,
                new EngineContext(9988L, -64, 320, 63),
                syntheticTerrain(),
                settings());
        ErosionSample expected = erosion.sample(31, -17);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<ErosionSample>> futures = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                futures.add(executor.submit(() -> erosion.sample(31, -17)));
            }
            for (Future<ErosionSample> future : futures) {
                assertEquals(expected, future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void adjacentRegionBoundaryDoesNotIntroduceAnArtificialStep() {
        CellLookup terrain = syntheticTerrain();
        ErosionPipeline erosion = new ErosionPipeline(
                8765L,
                new EngineContext(8765L, -64, 320, 63),
                terrain,
                settings());
        ErosionSample left = erosion.sample(31, 7);
        ErosionSample right = erosion.sample(32, 7);
        double erodedStep = Math.abs(left.erodedHeight() - right.erodedHeight());
        double baseStep = Math.abs(left.baseHeight() - right.baseHeight());
        assertTrue(erodedStep <= baseStep + settings().maximumHeightChange() * 0.5D);
    }

    private static CellLookup syntheticTerrain() {
        return (x, z, target) -> {
            target.reset();
            double distance = Math.hypot(x, z);
            double dome = Math.max(0.0D, 36.0D - distance * 0.22D);
            double detail = Math.sin(x * 0.18D) * 3.0D + Math.cos(z * 0.16D) * 2.0D;
            target.height = 68.0D + dome + detail;
            target.heightErosion = target.height;
            target.terrain = StandardTerrainTypes.MOUNTAINS;
            target.continentEdge = 0.9D;
        };
    }

    private static ErosionSettings settings() {
        return new ErosionSettings(
                32,
                16,
                6,
                12,
                2,
                0.08D,
                3.5D,
                0.02D,
                0.24D,
                0.28D,
                0.025D,
                4.0D,
                0.72D,
                0.55D,
                0.16D,
                1.35D,
                5.0D,
                4);
    }
}
