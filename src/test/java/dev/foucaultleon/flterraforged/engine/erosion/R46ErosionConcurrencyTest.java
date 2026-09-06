package dev.foucaultleon.flterraforged.engine.erosion;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.cell.CellLookup;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Regression coverage for R46 independent erosion-region generation. */
final class R46ErosionConcurrencyTest {

    @Test
    void formerlyCollidingStripeKeysCanGenerateConcurrently() throws Exception {
        CountDownLatch enteredGeneration = new CountDownLatch(2);
        CountDownLatch releaseGeneration = new CountDownLatch(1);
        ThreadLocal<Boolean> firstLookup = ThreadLocal.withInitial(() -> true);
        CellLookup baseTerrain = (x, z, target) -> {
            target.reset();
            target.height = 96.0D;
            target.heightErosion = 96.0D;
            target.continentEdge = 0.75D;
            if (firstLookup.get()) {
                firstLookup.set(false);
                enteredGeneration.countDown();
                try {
                    if (!releaseGeneration.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release erosion generation");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while testing erosion generation", exception);
                }
            }
        };
        ErosionSettings settings = new ErosionSettings(
                32,
                4,
                4,
                2,
                1,
                0.08D,
                3.5D,
                0.02D,
                0.24D,
                0.28D,
                0.025D,
                4.0D,
                0.20D,
                0.12D,
                0.15D,
                1.35D,
                4.0D,
                16);
        ErosionPipeline pipeline = new ErosionPipeline(
                991337L,
                new EngineContext(991337L, -64, 320, 63),
                baseTerrain,
                settings);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<?>> futures = new ArrayList<>();
        try {
            futures.add(executor.submit(() -> pipeline.sample(0, 0)));
            // Region X=64 hashed to the same old 64-way generation stripe as region X=0.
            futures.add(executor.submit(() -> pipeline.sample(32 * 64, 0)));

            assertTrue(
                    enteredGeneration.await(5, TimeUnit.SECONDS),
                    "Independent erosion regions must enter generation concurrently");
            releaseGeneration.countDown();
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            releaseGeneration.countDown();
            executor.shutdownNow();
        }
    }
}
