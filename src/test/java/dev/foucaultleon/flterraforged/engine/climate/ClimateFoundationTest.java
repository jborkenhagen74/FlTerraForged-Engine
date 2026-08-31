package dev.foucaultleon.flterraforged.engine.climate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import dev.foucaultleon.flterraforged.engine.cell.CellLookup;
import dev.foucaultleon.flterraforged.engine.noise.Noise2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

final class ClimateFoundationTest {

    @Test
    void climateRegionsAreDeterministicAndBounded() {
        ClimateRegionSampler first = new ClimateRegionSampler(1234L, 0.00025D, 0.85D);
        ClimateRegionSampler second = new ClimateRegionSampler(1234L, 0.00025D, 0.85D);
        ClimateRegionSample a = first.sample(2048, -1536);
        ClimateRegionSample b = second.sample(2048, -1536);

        assertEquals(a, b);
        assertTrue(a.id() >= 0.0D && a.id() <= 1.0D);
        assertTrue(a.edge() >= 0.0D && a.edge() <= 1.0D);
        assertTrue(a.temperature() >= 0.0D && a.temperature() <= 1.0D);
        assertTrue(a.moisture() >= 0.0D && a.moisture() <= 1.0D);
    }

    @Test
    void altitudeCoolsClimate() {
        ClimateModel low = model(91L, terrain(63.0D, 1.0D, 0.8D));
        ClimateModel high = model(91L, terrain(319.0D, 1.0D, 0.8D));

        double lowTemperature = low.sample(1200, -900).temperature();
        double highTemperature = high.sample(1200, -900).temperature();
        assertTrue(highTemperature < lowTemperature);
    }

    @Test
    void riversIncreaseLocalMoisture() {
        ClimateModel dry = model(77L, terrain(80.0D, 1.0D, 0.9D));
        ClimateModel river = model(77L, terrain(80.0D, 0.0D, 0.9D));

        double dryMoisture = dry.sample(333, 444).moisture();
        double riverMoisture = river.sample(333, 444).moisture();
        assertTrue(riverMoisture > dryMoisture);
    }

    @Test
    void climateWritesSemanticCellSignals() {
        ClimateModel climate = model(8080L, terrain(95.0D, 0.7D, 0.45D));
        Cell cell = climate.lookup(-771, 1209);

        assertTrue(cell.temperature >= 0.0D && cell.temperature <= 1.0D);
        assertTrue(cell.moisture >= 0.0D && cell.moisture <= 1.0D);
        assertTrue(cell.regionTemperature >= 0.0D && cell.regionTemperature <= 1.0D);
        assertTrue(cell.regionMoisture >= 0.0D && cell.regionMoisture <= 1.0D);
        assertTrue(cell.biomeRegionId >= 0.0D && cell.biomeRegionId <= 1.0D);
        assertTrue(cell.biomeRegionEdge >= 0.0D && cell.biomeRegionEdge <= 1.0D);
        assertTrue(cell.macroBiomeId >= 0.0D && cell.macroBiomeId <= 1.0D);
    }

    @Test
    void differentSeedsChangeClimateRegions() {
        ClimateRegionSample first = new ClimateRegionSampler(1L, 0.00025D, 0.85D).sample(4096, 4096);
        ClimateRegionSample second = new ClimateRegionSampler(2L, 0.00025D, 0.85D).sample(4096, 4096);
        assertNotEquals(first, second);
    }

    @Test
    void concurrentClimateSamplingIsStable() throws Exception {
        ClimateModel climate = model(445566L, terrain(120.0D, 0.6D, 0.7D));
        var expected = climate.sample(1700, -2200);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<dev.foucaultleon.flterraforged.engine.api.climate.ClimateSample>> futures = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                futures.add(executor.submit(() -> climate.sample(1700, -2200)));
            }
            for (Future<dev.foucaultleon.flterraforged.engine.api.climate.ClimateSample> future : futures) {
                assertEquals(expected, future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static ClimateModel model(long seed, CellLookup terrain) {
        EngineContext context = new EngineContext(seed, -64, 384, 63);
        Noise2D temperature = (x, z) -> Math.sin(x * 0.73D + z * 0.11D);
        Noise2D moisture = (x, z) -> Math.cos(x * 0.17D - z * 0.61D);
        ClimateSettings settings = new ClimateSettings(
                0.0008D,
                0.00025D,
                0.85D,
                0.34D,
                0.80D,
                0.18D,
                0.55D,
                0.22D,
                0.55D,
                ClimateLayout.RANDOMIZED,
                0.0D,
                24000.0D,
                0.78D,
                0.18D,
                0.80D,
                0.60D,
                0.36D);
        ClimateRegionSampler regions = new ClimateRegionSampler(seed ^ 0x1234ABCDL, settings.regionScale(), settings.regionJitter());
        return new ClimateModel(context, terrain, temperature, moisture, regions, settings);
    }

    private static CellLookup terrain(double height, double riverMask, double continentEdge) {
        return (x, z, target) -> {
            target.reset();
            target.height = height;
            target.heightErosion = height;
            target.riverMask = riverMask;
            target.continentEdge = continentEdge;
        };
    }
}
