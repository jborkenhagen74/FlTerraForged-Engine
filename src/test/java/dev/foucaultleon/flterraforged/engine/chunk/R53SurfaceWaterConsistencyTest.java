package dev.foucaultleon.flterraforged.engine.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.chunk.ColumnSnapshot;
import dev.foucaultleon.flterraforged.engine.api.chunk.NaturalMaterial;
import dev.foucaultleon.flterraforged.engine.api.climate.ClimateSample;
import dev.foucaultleon.flterraforged.engine.api.river.RiverSample;
import dev.foucaultleon.flterraforged.engine.api.terrain.StandardTerrainTypes;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Regression coverage for the R53 single-authority surface-water quantization contract. */
final class R53SurfaceWaterConsistencyTest {

    private static final EngineContext CONTEXT = new EngineContext(0x53A7E12L, 0, 96, 63);

    @Test
    void inlandRiverAboveSeaLevelKeepsItsHydrologySurface() {
        TerrainSample river = sample(
                70.8D,
                StandardTerrainTypes.RIVER,
                new RiverSample(0.0D, 12.0D, 1.2D, 72.0D, 18.0D));
        EngineChunkSnapshot snapshot = generate(river);
        ColumnSnapshot column = snapshot.column(0, 0);

        assertEquals(70, column.solidSurfaceY());
        assertEquals(73, column.waterTopExclusive());
        assertTrue(column.hasSurfaceWater());
        assertEquals(NaturalMaterial.WATER, snapshot.materialAt(0, 71, 0));
        assertEquals(NaturalMaterial.WATER, snapshot.materialAt(0, 72, 0));
    }

    @Test
    void nearSeaInlandRiverIsNotClampedBySubsurfacePass() {
        TerrainSample river = sample(
                64.25D,
                StandardTerrainTypes.RIVER,
                new RiverSample(0.0D, 10.0D, 2.5D, 67.0D, 15.0D));
        ColumnSnapshot column = generate(river).column(0, 0);

        assertEquals(64, column.solidSurfaceY());
        assertEquals(68, column.waterTopExclusive(),
                "Subsurface generation must preserve the already-stabilized hydrology level");
    }

    @Test
    void inlandLakeAboveSeaLevelStaysCompletelyFilled() {
        TerrainSample lake = sample(
                74.2D,
                StandardTerrainTypes.LAKE,
                new RiverSample(0.0D, 20.0D, 3.5D, 78.0D, 8.0D));
        EngineChunkSnapshot snapshot = generate(lake);
        ColumnSnapshot column = snapshot.column(8, 8);

        assertEquals(79, column.waterTopExclusive());
        assertTrue(column.hasSurfaceWater());
        for (int y = column.solidSurfaceY() + 1; y < column.waterTopExclusive(); y++) {
            assertEquals(NaturalMaterial.WATER, snapshot.materialAt(8, y, 8));
        }
    }

    @Test
    void authoritativeWetHydrologyAlwaysMaterializesAtLeastOneWaterBlock() {
        TerrainSample shallow = sample(
                70.95D,
                StandardTerrainTypes.RIVER,
                new RiverSample(0.0D, 5.0D, 0.06D, 70.96D, 5.0D));
        EngineChunkSnapshot snapshot = generate(shallow);
        ColumnSnapshot column = snapshot.column(0, 0);

        assertTrue(column.hasSurfaceWater());
        assertEquals(column.solidSurfaceY() + 2, column.waterTopExclusive());
        assertEquals(NaturalMaterial.WATER,
                snapshot.materialAt(0, column.solidSurfaceY() + 1, 0));
    }

    @Test
    void dryLakeShoreDoesNotBecomeWaterFromFiniteReferenceLevel() {
        TerrainSample shore = sample(
                76.0D,
                StandardTerrainTypes.LAKE_SHORE,
                new RiverSample(4.0D, 16.0D, 0.0D, 78.0D, 4.0D));
        ColumnSnapshot column = generate(shore).column(0, 0);

        assertFalse(column.hasSurfaceWater());
        assertEquals(column.solidSurfaceY() + 1, column.waterTopExclusive());
    }

    @Test
    void oceanUsesCanonicalSeaLevelEvenWhenHydrologyReferenceIsHigher() {
        TerrainSample ocean = sample(
                51.0D,
                StandardTerrainTypes.OCEAN,
                new RiverSample(0.0D, 18.0D, 8.0D, 70.0D, 30.0D));
        ColumnSnapshot column = generate(ocean).column(0, 0);

        assertEquals(CONTEXT.seaLevel() + 1, column.waterTopExclusive());
    }

    private static EngineChunkSnapshot generate(TerrainSample sample) {
        TerrainSample[] samples = new TerrainSample[256];
        Arrays.fill(samples, sample);
        return new SubsurfaceGenerator(CONTEXT).generate(0, 0, samples);
    }

    private static TerrainSample sample(
            double surfaceHeight,
            dev.foucaultleon.flterraforged.engine.api.terrain.TerrainType terrainType,
            RiverSample river) {
        return new TerrainSample(
                surfaceHeight,
                0.1D,
                0.0D,
                0.25D,
                terrainType,
                ClimateSample.UNAVAILABLE,
                river);
    }
}
