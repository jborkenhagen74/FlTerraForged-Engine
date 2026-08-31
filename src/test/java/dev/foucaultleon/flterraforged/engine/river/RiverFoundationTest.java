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
        RiverPathPoint center = segment.path().get(segment.path().size() / 2);
        int x = (int) Math.round(center.x());
        int z = (int) Math.round(center.z());
        RiverSample sample = river.sample(x, z);
        var cell = river.lookup(x, z);

        assertTrue(sample.distance() < sample.width() * 0.5D);
        assertTrue(sample.depth() > 0.0D);
        assertTrue(sample.hasWaterSurfaceHeight());
        assertTrue(sample.waterSurfaceHeight() > cell.height);
        assertTrue(sample.hasFlow());
        assertEquals(sample.waterSurfaceHeight(), cell.riverWaterSurfaceHeight);
        assertEquals(sample.flow(), cell.riverFlow);
        assertTrue(cell.height < cell.heightErosion);
        assertTrue(cell.riverMask >= 0.0D && cell.riverMask < 1.0D);
    }


    @Test
    void segmentWaterSurfaceNeverRisesAndStaysBelowRefinedTerrain() {
        RiverSegment segment = model(314159L).map(0, 0).segments().stream()
                .filter(candidate -> candidate.startWaterHeight() > candidate.endWaterHeight())
                .findFirst()
                .orElseThrow();

        double previousWater = Double.POSITIVE_INFINITY;
        for (RiverPathPoint point : segment.path()) {
            assertTrue(point.waterSurfaceHeight() <= previousWater + 1.0E-9D);
            assertTrue(point.waterSurfaceHeight() < point.terrainHeight());
            RiverHit hit = segment.hit(point.x(), point.z());
            assertEquals(point.waterSurfaceHeight(), hit.waterSurfaceHeight(), 1.0E-9D);
            previousWater = point.waterSurfaceHeight();
        }
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
    void visiblePathsAreTerrainRefinedInsteadOfRawD8Lines() {
        Rivermap map = model(224466L).map(0, 0);
        boolean curved = map.segments().stream().anyMatch(segment -> {
            double dx = segment.endX() - segment.startX();
            double dz = segment.endZ() - segment.startZ();
            double length = Math.max(1.0D, Math.hypot(dx, dz));
            return segment.path().stream().skip(1).limit(segment.path().size() - 2L).anyMatch(point -> {
                double distance = Math.abs(
                        dz * point.x()
                                - dx * point.z()
                                + segment.endX() * (double) segment.startZ()
                                - segment.endZ() * (double) segment.startX()) / length;
                return distance > 0.25D;
            });
        });
        assertTrue(curved, "expected at least one terrain-refined non-D8 centerline");
    }

    @Test
    void depressionFillCreatesInlandWaterInsteadOfDeadSink() {
        EngineContext context = new EngineContext(123L, -64, 320, 63);
        CellLookup basin = (x, z, target) -> {
            target.reset();
            double radius = Math.hypot(x - 100.0D, z - 100.0D);
            double bowl = Math.min(28.0D, radius * 0.10D);
            double pit = -20.0D * Math.exp(-(radius * radius) / (2.0D * 70.0D * 70.0D));
            target.height = 112.0D + bowl + pit - z * 0.02D;
            target.heightErosion = target.height;
            target.continentEdge = 0.85D;
        };
        RiverModel river = new RiverModel(123L, context, basin, basin, settings());
        Rivermap map = river.map(0, 0);
        boolean hasLake = false;
        for (int z = 0; z < settings().regionSize() && !hasLake; z += 8) {
            for (int x = 0; x < settings().regionSize(); x += 8) {
                if (map.lake(x, z).present()) {
                    hasLake = true;
                    break;
                }
            }
        }
        assertTrue(hasLake, "expected a depression-filled pond/lake");
    }

    @Test
    void centerlineMaintainsMaterialWaterDepth() {
        Rivermap map = model(991177L).map(0, 0);
        for (RiverSegment segment : map.segments()) {
            RiverPathPoint center = segment.path().get(segment.path().size() / 2);
            RiverHit hit = segment.hit(center.x(), center.z());
            assertTrue(
                    hit.waterSurfaceHeight() > hit.surfaceHeight() - hit.depth(),
                    "channel center must remain below its water surface");
        }
    }


    @Test
    void dryCatchmentsProduceFarFewerLocalChannelsThanWetCatchments() {
        EngineContext context = new EngineContext(12345L, -64, 320, 63);
        CellLookup terrain = syntheticDrainageSurface();
        CellLookup wetClimate = climate(0.55D, 0.82D, terrain);
        CellLookup dryClimate = climate(0.84D, 0.22D, terrain);
        RiverSettings settings = climateAwareSettings();

        RiverModel wet = new RiverModel(991L, context, terrain, terrain, wetClimate, settings);
        RiverModel dry = new RiverModel(991L, context, terrain, terrain, dryClimate, settings);

        int wetSegments = wet.map(0, 0).segments().size();
        int drySegments = dry.map(0, 0).segments().size();
        assertTrue(wetSegments > 0, "expected wet catchment channels");
        assertTrue(drySegments < wetSegments / 2, "dry catchment should suppress most local channels");
    }

    @Test
    void establishedChannelRemainsWetAcrossRegionBoundaryWithExpandedPadding() {
        EngineContext context = new EngineContext(12345L, -64, 320, 63);
        CellLookup terrain = syntheticDrainageSurface();
        RiverSettings settings = climateAwareSettings();
        RiverModel river = new RiverModel(991L, context, terrain, terrain, climate(0.55D, 0.82D, terrain), settings);
        int boundaryZ = settings.regionSize();

        RiverSample before = river.sample(96, boundaryZ - 8);
        RiverSample after = river.sample(96, boundaryZ + 8);
        assertTrue(before.depth() > 0.0D, "river should be wet before map boundary");
        assertTrue(after.depth() > 0.0D, "river should remain wet after map boundary");
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


    private static CellLookup climate(double temperature, double moisture, CellLookup terrain) {
        return (x, z, target) -> {
            terrain.lookup(x, z, target);
            target.temperature = temperature;
            target.moisture = moisture;
        };
    }

    private static RiverSettings climateAwareSettings() {
        return new RiverSettings(
                480,
                24,
                16,
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
    }

    private static RiverSettings settings() {
        return new RiverSettings(
                200,
                20,
                8,
                5.0D,
                2.5D,
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
    }
}
