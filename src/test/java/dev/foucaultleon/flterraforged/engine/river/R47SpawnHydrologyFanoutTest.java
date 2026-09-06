package dev.foucaultleon.flterraforged.engine.river;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.cell.CellLookup;
import org.junit.jupiter.api.Test;

/** Regression coverage for the R47 centered hydrology ownership lattice. */
final class R47SpawnHydrologyFanoutTest {

    @Test
    void spawnOriginBuildsOnlyOneCanonicalHydrologyMap() {
        RiverSettings settings = new RiverSettings(
                480,
                24,
                8,
                5.5D,
                3.5D,
                2.0D,
                16.0D,
                7.0D,
                1.35D,
                1.05D,
                1.35D,
                4.25D,
                0.45D,
                0.48D,
                7,
                0.85D,
                1.35D,
                16);
        EngineContext context = new EngineContext(7331L, -64, 320, 63);
        CellLookup terrain = (x, z, target) -> {
            target.reset();
            target.height = 138.0D - z * 0.02D + Math.abs(x) * 0.008D;
            target.heightErosion = target.height;
            target.continentEdge = 0.88D;
        };
        RiverModel model = new RiverModel(7331L, context, terrain, terrain, settings);

        model.sample(0, 0);

        assertEquals(
                1,
                model.cachedMaps(),
                "spawn-origin hydrology must stay inside one canonical map on a cold cache");
        assertEquals(
                0,
                model.inFlightMaps(),
                "spawn-origin hydrology must leave no unfinished single-flight map loads");
    }
}
