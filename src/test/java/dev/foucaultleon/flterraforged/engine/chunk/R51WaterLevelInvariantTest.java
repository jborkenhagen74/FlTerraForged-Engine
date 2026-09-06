package dev.foucaultleon.flterraforged.engine.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.climate.ClimateSample;
import dev.foucaultleon.flterraforged.engine.api.river.RiverSample;
import dev.foucaultleon.flterraforged.engine.api.terrain.StandardTerrainTypes;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;
import org.junit.jupiter.api.Test;

final class R51WaterLevelInvariantTest {

    @Test
    void oceanUsesCanonicalSeaSurface() {
        EngineChunkSnapshot snapshot = snapshot(sample(
                57.5D,
                StandardTerrainTypes.OCEAN,
                RiverSample.UNAVAILABLE));

        assertEquals(64, snapshot.column(0, 0).waterTopExclusive());
    }

    @Test
    void lowRiverMouthCannotRemainAboveSeaSurface() {
        EngineChunkSnapshot snapshot = snapshot(sample(
                61.0D,
                StandardTerrainTypes.RIVER,
                new RiverSample(0.0D, 9.0D, 5.0D, 66.0D, 120.0D)));

        assertEquals(64, snapshot.column(0, 0).waterTopExclusive());
    }

    @Test
    void inlandRiverKeepsItsIndependentWaterSurface() {
        EngineChunkSnapshot snapshot = snapshot(sample(
                77.0D,
                StandardTerrainTypes.RIVER,
                new RiverSample(0.0D, 9.0D, 3.0D, 80.0D, 120.0D)));

        assertEquals(81, snapshot.column(0, 0).waterTopExclusive());
    }

    private static EngineChunkSnapshot snapshot(TerrainSample sample) {
        TerrainSample[] samples = new TerrainSample[256];
        java.util.Arrays.fill(samples, sample);
        return new SubsurfaceGenerator(new EngineContext(42L, -64, 320, 63))
                .generate(0, 0, samples);
    }

    private static TerrainSample sample(
            double surfaceHeight,
            dev.foucaultleon.flterraforged.engine.api.terrain.TerrainType terrainType,
            RiverSample river) {
        return new TerrainSample(
                surfaceHeight,
                0.2D,
                0.0D,
                0.0D,
                terrainType,
                new ClimateSample(0.5D, 0.5D),
                river);
    }
}
