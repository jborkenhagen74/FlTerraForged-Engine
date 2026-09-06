package dev.foucaultleon.flterraforged.engine.river;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.cell.CellLookup;
import org.junit.jupiter.api.Test;

final class R45WorldgenStallGuardTest {

    @Test
    void interiorTerrainLookupBuildsOnlyCanonicalHydrologyMap() {
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
        EngineContext context = new EngineContext(991337L, -64, 320, 63);
        CellLookup terrain = (x, z, target) -> {
            target.reset();
            target.height = 142.0D - z * 0.025D + Math.abs(x - 120.0D) * 0.01D;
            target.heightErosion = target.height;
            target.continentEdge = 0.85D;
        };
        RiverModel model = new RiverModel(991337L, context, terrain, terrain, settings);

        model.sample(120, 120);

        assertEquals(
                1,
                model.cachedMaps(),
                "one interior X/Z sample must not fan out into neighboring hydrology maps");
    }
}
