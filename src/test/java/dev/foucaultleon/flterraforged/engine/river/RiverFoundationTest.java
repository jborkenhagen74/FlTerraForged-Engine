package dev.foucaultleon.flterraforged.engine.river;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.river.RiverSample;
import dev.foucaultleon.flterraforged.engine.cell.CellLookup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

final class RiverFoundationTest {

    @Test
    void drainageGraphCreatesDeterministicConnectedChannels() {
        RiverModel first = model(12345L);
        RiverModel second = model(12345L);
        Rivermap a = first.map(0, 0);
        Rivermap b = second.map(0, 0);
        assertFalse(a.segments().isEmpty(), "expected drainage segments");
        assertEquals(a, b);
        assertTrue(a.segments().stream().anyMatch(segment -> segment.flow() >= 12.0D));
    }

    @Test
    void centerlineIncisesPostErosionHeightAndWritesRiverMask() {
        RiverModel river = model(9988L);
        RiverSegment segment = river.map(0, 0).segments().stream()
                .max(Comparator.comparingDouble(RiverSegment::flow))
                .orElseThrow();
        int x = (segment.startX() + segment.endX()) / 2;
        int z = (segment.startZ() + segment.endZ()) / 2;
        RiverSample sample = river.sample(x, z);
        var cell = river.lookup(x, z);

        assertTrue(sample.distance() < sample.width() * 0.5D);
        assertTrue(sample.depth() > 0.0D);
        assertTrue(cell.height < cell.heightErosion);
        assertTrue(cell.riverMask >= 0.0D && cell.riverMask < 1.0D);
    }

    @Test
    void neighboringMapOwnershipKeepsBoundarySegmentsQueryable() {
        RiverModel river = model(42L);
        int boundaryX = settings().regionSize();
        for (int z = 0; z < settings().regionSize(); z += settings().gridSpacing()) {
            RiverSample left = river.sample(boundaryX - 1, z);
            RiverSample right = river.sample(boundaryX, z);
            assertTrue(Double.isFinite(left.distance()));
            assertTrue(Double.isFinite(right.distance()));
            assertTrue(left.width() > 0.0D);
            assertTrue(right.width() > 0.0D);
        }
    }

    @Test
    void concurrentRivermapSamplingIsStable() throws Exception {
        RiverModel river = model(775533L);
        RiverSample expected = river.sample(96, 96);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<RiverSample>> futures = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                futures.add(executor.submit(() -> river.sample(96, 96)));
            }
            for (Future<RiverSample> future : futures) {
                assertEquals(expected, future.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static RiverModel model(long seed) {
        EngineContext context = new EngineContext(seed, -64, 320, 63);
        CellLookup terrain = syntheticDrainageSurface();
        return new RiverModel(seed, context, terrain, terrain, settings());
    }

    private static CellLookup syntheticDrainageSurface() {
        return (x, z, target) -> {
            target.reset();
            double valley = Math.abs(x - 96) * 0.12D;
            double downstream = -z * 0.08D;
            double detail = Math.sin(z * 0.03D) * 0.8D;
            target.height = 150.0D + valley + downstream + detail;
            target.heightErosion = target.height;
            target.continentEdge = 0.85D;
        };
    }

    private static RiverSettings settings() {
        return new RiverSettings(
                192,
                24,
                6,
                4.0D,
                3.0D,
                14.0D,
                7.0D,
                1.55D,
                1.10D,
                16);
    }
}
