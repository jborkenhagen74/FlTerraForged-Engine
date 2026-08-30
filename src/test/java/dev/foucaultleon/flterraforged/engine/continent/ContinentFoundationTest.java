package dev.foucaultleon.flterraforged.engine.continent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.foucaultleon.flterraforged.engine.EngineSettings;
import dev.foucaultleon.flterraforged.engine.cell.Cell;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

final class ContinentFoundationTest {

    @Test
    void samplingIsDeterministicAndBounded() {
        Continent continent = continent(123456789L);
        ContinentSample first = continent.sample(1200.5D, -9876.25D);
        ContinentSample second = continent.sample(1200.5D, -9876.25D);
        assertEquals(first, second);
        assertTrue(first.id() >= 0.0D && first.id() <= 1.0D);
        assertTrue(first.edge() >= 0.0D && first.edge() <= 1.0D);
        assertTrue(first.continentalness() >= -1.0D && first.continentalness() <= 1.0D);
    }

    @Test
    void differentSeedsProduceDifferentPartitions() {
        ContinentSample first = continent(1L).sample(4800.0D, -3200.0D);
        ContinentSample second = continent(2L).sample(4800.0D, -3200.0D);
        assertNotEquals(first, second);
    }

    @Test
    void continentPopulatesCellSignals() {
        Continent continent = continent(42L);
        Cell cell = new Cell();
        continent.apply(cell, 2048, 3072);
        ContinentSample sample = continent.sample(2048.0D, 3072.0D);
        assertEquals(sample.id(), cell.continentId);
        assertEquals(sample.edge(), cell.continentEdge);
        assertEquals(sample.center().x(), cell.continentX);
        assertEquals(sample.center().z(), cell.continentZ);
    }

    @Test
    void partitionContainsInteriorAndOceanBoundaries() {
        Continent continent = continent(8675309L);
        double min = 1.0D;
        double max = 0.0D;
        for (int z = -16000; z <= 16000; z += 256) {
            for (int x = -16000; x <= 16000; x += 256) {
                double edge = continent.edgeValue(x, z);
                min = Math.min(min, edge);
                max = Math.max(max, edge);
            }
        }
        assertTrue(min < 0.10D, "expected ocean-producing continent boundaries");
        assertTrue(max > 0.85D, "expected deep continent interiors");
    }

    @Test
    void concurrentSamplingIsStable() throws Exception {
        Continent continent = continent(99887766L);
        List<double[]> points = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            points.add(new double[] {i * 137.0D - 9000.0D, 5000.0D - i * 83.0D});
        }
        List<ContinentSample> expected = points.stream()
                .map(point -> continent.sample(point[0], point[1]))
                .toList();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<ContinentSample>> tasks = points.stream()
                    .<Callable<ContinentSample>>map(point -> () -> continent.sample(point[0], point[1]))
                    .toList();
            List<Future<ContinentSample>> results = executor.invokeAll(tasks);
            for (int i = 0; i < results.size(); i++) {
                assertEquals(expected.get(i), results.get(i).get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static Continent continent(long seed) {
        return new AdvancedContinent(seed, ContinentSettings.from(EngineSettings.defaults()));
    }
}
