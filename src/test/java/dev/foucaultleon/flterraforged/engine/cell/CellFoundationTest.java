package dev.foucaultleon.flterraforged.engine.cell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.foucaultleon.flterraforged.engine.noise.Interpolation;
import dev.foucaultleon.flterraforged.engine.noise.ValueNoise;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

final class CellFoundationTest {

    @Test
    void resetRestoresNeutralDefaults() {
        Cell cell = new Cell();
        cell.height = 99.0D;
        cell.erosion = 0.8D;
        cell.moisture = 0.1D;
        cell.riverMask = 0.0D;
        cell.reset();
        assertEquals(0.0D, cell.height);
        assertEquals(0.0D, cell.erosion);
        assertEquals(0.5D, cell.moisture);
        assertEquals(1.0D, cell.riverMask);
    }

    @Test
    void copyIsIndependent() {
        Cell source = new Cell();
        source.height = 72.25D;
        source.temperature = 0.7D;
        Cell copy = new Cell().copyFrom(source);
        assertNotSame(source, copy);
        assertEquals(source.height, copy.height);
        assertEquals(source.temperature, copy.temperature);
        source.height = -10.0D;
        assertEquals(72.25D, copy.height);
    }

    @Test
    void orderedPipelinePopulatesCallerOwnedCell() {
        CellField field = CellField.of(
                (cell, x, z) -> cell.height = x + z * 0.5D,
                new NoiseCellPopulator(
                        new ValueNoise(7L, 0.02D, Interpolation.QUINTIC),
                        42L,
                        1.0D,
                        NoiseCellPopulator.Channel.EROSION));
        Cell target = new Cell();
        field.lookup(20, -8, target);
        assertEquals(16.0D, target.height);
        assertTrue(target.erosion >= 0.0D && target.erosion <= 1.0D);
    }

    @Test
    void cellFieldHasNoSharedSamplingState() throws Exception {
        CellField field = CellField.of((cell, x, z) -> {
            cell.height = x * 0.25D + z * 0.75D;
            cell.continentId = x ^ z;
        });
        List<int[]> points = new ArrayList<>();
        for (int i = 0; i < 256; i++) {
            points.add(new int[] {i * 13 - 500, 900 - i * 7});
        }
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Cell>> tasks = points.stream()
                    .<Callable<Cell>>map(point -> () -> field.lookup(point[0], point[1]))
                    .toList();
            List<Future<Cell>> results = executor.invokeAll(tasks);
            for (int i = 0; i < points.size(); i++) {
                int[] point = points.get(i);
                Cell cell = results.get(i).get();
                assertEquals(point[0] * 0.25D + point[1] * 0.75D, cell.height);
                assertEquals((double) (point[0] ^ point[1]), cell.continentId);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
