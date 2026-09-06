package dev.foucaultleon.flterraforged.engine.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.foucaultleon.flterraforged.engine.DefaultTerrainWorld;
import dev.foucaultleon.flterraforged.engine.EngineSettings;
import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.chunk.ChunkSnapshot;
import dev.foucaultleon.flterraforged.engine.api.chunk.NaturalMaterial;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** Regression tests for R47 Engine-owned complete natural chunk snapshots. */
class R47ChunkSnapshotTest {

    @Test
    void repeatedAndConcurrentRequestsShareOneImmutableSnapshot() throws Exception {
        EngineContext context = new EngineContext(918273645L, -64, 320, 63);
        try (DefaultTerrainWorld world = new DefaultTerrainWorld(context, EngineSettings.defaults())) {
            ChunkSnapshot first = world.chunkSnapshot(3, -2);
            assertSame(first, world.chunkSnapshot(3, -2));

            ExecutorService pool = Executors.newFixedThreadPool(6);
            try {
                List<Callable<ChunkSnapshot>> tasks = new ArrayList<>();
                for (int index = 0; index < 12; index++) {
                    tasks.add(() -> world.chunkSnapshot(4, -2));
                }
                List<Future<ChunkSnapshot>> futures = pool.invokeAll(tasks);
                ChunkSnapshot canonical = futures.get(0).get();
                for (Future<ChunkSnapshot> future : futures) {
                    assertSame(canonical, future.get());
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void snapshotOwnsFullVerticalNaturalGeometry() {
        EngineContext context = new EngineContext(123456789L, -64, 320, 63);
        try (DefaultTerrainWorld world = new DefaultTerrainWorld(context, EngineSettings.defaults())) {
            ChunkSnapshot snapshot = world.chunkSnapshot(0, 0);
            assertEquals(-64, snapshot.minY());
            assertEquals(320, snapshot.maxYExclusive());
            assertEquals(NaturalMaterial.BEDROCK, snapshot.materialAt(0, -64, 0));
            assertNotNull(snapshot.column(8, 8).geology());

            boolean foundSubsurfaceVoidOrFluid = false;
            for (int z = 0; z < 16 && !foundSubsurfaceVoidOrFluid; z++) {
                for (int x = 0; x < 16 && !foundSubsurfaceVoidOrFluid; x++) {
                    int top = snapshot.column(x, z).solidSurfaceY();
                    for (int y = snapshot.minY() + 6; y < top - 8; y++) {
                        NaturalMaterial material = snapshot.materialAt(x, y, z);
                        if (material == NaturalMaterial.AIR
                                || material == NaturalMaterial.WATER
                                || material == NaturalMaterial.LAVA) {
                            foundSubsurfaceVoidOrFluid = true;
                            break;
                        }
                    }
                }
            }
            assertTrue(foundSubsurfaceVoidOrFluid, "R47 snapshot must contain Engine-owned subsurface geometry");
        }
    }
}
