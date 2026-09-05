package dev.foucaultleon.flterraforged.engine.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.foucaultleon.flterraforged.engine.EnginePreset;
import dev.foucaultleon.flterraforged.engine.EngineSettings;
import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainEnvironmentSample;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

final class WorldgenPipelineTest {

    @Test
    void bulkTileMatchesPointSampling() {
        EngineContext context = new EngineContext(445566L, -64, 320, 63);
        WorldgenPipeline pipeline = new WorldgenPipeline(context, EngineSettings.defaults());
        int originX = 32;
        int originZ = -48;
        int size = 4;
        TerrainSample[] tile = pipeline.sampleTile(originX, originZ, size);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                assertEquals(
                        pipeline.sample(originX + x, originZ + z),
                        tile[z * size + x],
                        "Bulk sample differs at " + x + "," + z);
            }
        }
    }

    @Test
    void completePipelineProducesCoherentStageSignals() {
        WorldgenPipeline pipeline = pipeline(123456789L, EnginePreset.BALANCED);
        Cell cell = pipeline.sampleCell(1320, -2440);

        assertTrue(Double.isFinite(cell.height));
        assertTrue(Double.isFinite(cell.heightErosion));
        assertTrue(cell.height <= cell.heightErosion + 1.0E-9D, "hydrology may lower but not raise terrain");
        assertTrue(cell.continentEdge >= 0.0D && cell.continentEdge <= 1.0D);
        assertTrue(cell.terrainRegionEdge >= 0.0D && cell.terrainRegionEdge <= 1.0D);
        assertTrue(cell.biomeRegionEdge >= 0.0D && cell.biomeRegionEdge <= 1.0D);
        assertTrue(cell.erosion >= 0.0D && cell.erosion <= 1.0D);
        assertTrue(cell.sediment >= 0.0D);
        assertTrue(cell.riverMask >= 0.0D && cell.riverMask <= 1.0D);
        assertTrue(!Double.isNaN(cell.riverDistance));
        assertTrue(cell.riverWidth >= 0.0D);
        assertTrue(cell.riverDepth >= 0.0D);
        if (cell.riverWidth == 0.0D) {
            // Receiver-owned ocean cells deliberately clear linear-river metadata. Infinity is the
            // canonical "no nearby channel" distance and avoids inventing a finite river start.
            assertEquals(Double.POSITIVE_INFINITY, cell.riverDistance);
            assertEquals(0.0D, cell.riverDepth);
            assertTrue(Double.isNaN(cell.riverWaterSurfaceHeight));
            assertTrue(Double.isNaN(cell.riverFlow));
        } else if (cell.riverDepth > 0.0D) {
            assertTrue(Double.isFinite(cell.riverWaterSurfaceHeight));
            assertTrue(Double.isFinite(cell.riverFlow));
        }
        assertTrue(cell.temperature >= 0.0D && cell.temperature <= 1.0D);
        assertTrue(cell.moisture >= 0.0D && cell.moisture <= 1.0D);
        assertTrue(Double.isFinite(cell.gradient) && cell.gradient >= 0.0D);
        if (cell.riverWidth > 0.0D) {
            assertEquals(cell.heightErosion - cell.height, cell.riverDepth, 1.0E-9D);
        }
    }

    @Test
    void apiSampleIsProjectionOfTheSameIntegratedCell() {
        WorldgenPipeline pipeline = pipeline(778899L, EnginePreset.BALANCED);
        Cell cell = pipeline.sampleCell(-915, 1207);
        TerrainSample sample = pipeline.sample(-915, 1207);

        assertEquals(cell.height, sample.surfaceHeight());
        assertEquals(cell.gradient, sample.slope());
        assertEquals(cell.erosion, sample.erosion());
        assertEquals(cell.continentEdge * 2.0D - 1.0D, sample.continentalness());
        assertEquals(cell.temperature, sample.climate().temperature());
        assertEquals(cell.moisture, sample.climate().moisture());
        assertEquals(cell.riverDistance, sample.river().distance());
        assertEquals(cell.riverWidth, sample.river().width());
        assertEquals(cell.riverDepth, sample.river().depth());
        assertEquals(cell.riverWaterSurfaceHeight, sample.river().waterSurfaceHeight());
        assertEquals(cell.riverFlow, sample.river().flow());
    }

    @Test
    void lightweightEnvironmentRetainsFinalSurfaceWithoutGradientWork() {
        WorldgenPipeline pipeline = pipeline(778899L, EnginePreset.BALANCED);
        int x = -915;
        int z = 1207;

        TerrainEnvironmentSample environment = pipeline.environment(x, z);
        TerrainSample full = pipeline.sample(x, z);

        assertEquals(full.surfaceHeight(), environment.surfaceHeight(), 1.0E-9D);
        assertEquals(environment, pipeline.environment(x, z));
        assertTrue(environment.hasWaterSurfaceHeight()
                || Double.isNaN(environment.waterSurfaceHeight()));
    }

    @Test
    void coordinatedPresetsCreateDifferentWorldSignatures() {
        TerrainSample gentle = pipeline(4567L, EnginePreset.GENTLE).sample(2500, -1750);
        TerrainSample rugged = pipeline(4567L, EnginePreset.RUGGED).sample(2500, -1750);
        assertTrue(
                gentle.surfaceHeight() != rugged.surfaceHeight()
                        || gentle.erosion() != rugged.erosion()
                        || gentle.river().depth() != rugged.river().depth());
    }

    @Test
    void concurrentIntegratedSamplingIsStable() throws Exception {
        WorldgenPipeline pipeline = pipeline(987654321L, EnginePreset.BALANCED);
        TerrainSample expected = pipeline.sample(714, -1138);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<TerrainSample>> futures = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                futures.add(executor.submit(() -> pipeline.sample(714, -1138)));
            }
            for (Future<TerrainSample> future : futures) {
                assertEquals(expected, future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static WorldgenPipeline pipeline(long seed, EnginePreset preset) {
        return new WorldgenPipeline(
                new EngineContext(seed, -64, 320, 63),
                EngineSettings.preset(preset));
    }
}
