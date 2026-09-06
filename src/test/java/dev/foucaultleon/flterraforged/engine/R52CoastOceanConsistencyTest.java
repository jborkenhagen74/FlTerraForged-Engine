package dev.foucaultleon.flterraforged.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.foucaultleon.flterraforged.engine.api.EngineConfig;
import dev.foucaultleon.flterraforged.engine.api.EngineContext;
import dev.foucaultleon.flterraforged.engine.api.TerrainEngine;
import dev.foucaultleon.flterraforged.engine.api.TerrainWorld;
import dev.foucaultleon.flterraforged.engine.api.chunk.ChunkSnapshot;
import dev.foucaultleon.flterraforged.engine.api.chunk.ColumnSnapshot;
import dev.foucaultleon.flterraforged.engine.api.chunk.NaturalMaterial;
import dev.foucaultleon.flterraforged.engine.api.terrain.StandardTerrainTypes;
import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;
import org.junit.jupiter.api.Test;

/** Sampling regressions for the R52 coast, ocean floor and natural water-column contract. */
public final class R52CoastOceanConsistencyTest {

    private static final int SEA_LEVEL = 63;

    @Test
    void sampledCoastUsesPhysicalHeightWithoutArtificialWall() {
        try (TerrainEngine engine = new DefaultEngineProvider().create(EngineConfig.empty());
                TerrainWorld world = engine.openWorld(new EngineContext(0x52C0A57L, -64, 320, SEA_LEVEL))) {
            int[] coast = findCoastalCandidate(world);
            assertNotNull(coast, "Expected to find a physical coastline in the deterministic scan window");

            double maximumLowCoastStep = 0.0D;
            int compared = 0;
            for (int z = coast[1] - 40; z <= coast[1] + 40; z++) {
                for (int x = coast[0] - 40; x <= coast[0] + 40; x++) {
                    TerrainSample center = world.sample(x, z);
                    assertMarineSemanticMatchesHeight(center);
                    if (x < coast[0] + 40) {
                        TerrainSample east = world.sample(x + 1, z);
                        if (isLowCoastalPair(center, east)) {
                            maximumLowCoastStep = Math.max(
                                    maximumLowCoastStep,
                                    Math.abs(center.surfaceHeight() - east.surfaceHeight()));
                            compared++;
                        }
                    }
                    if (z < coast[1] + 40) {
                        TerrainSample south = world.sample(x, z + 1);
                        if (isLowCoastalPair(center, south)) {
                            maximumLowCoastStep = Math.max(
                                    maximumLowCoastStep,
                                    Math.abs(center.surfaceHeight() - south.surfaceHeight()));
                            compared++;
                        }
                    }
                }
            }

            assertTrue(compared > 100, "Sampling window must contain enough low-coast neighbor pairs");
            assertTrue(
                    maximumLowCoastStep <= 3.25D,
                    () -> "Non-cliff shoreline contains an artificial step of " + maximumLowCoastStep + " blocks");
        }
    }

    @Test
    void chunkSnapshotsContainContiguousCanonicalOceanWater() {
        try (TerrainEngine engine = new DefaultEngineProvider().create(EngineConfig.empty());
                TerrainWorld world = engine.openWorld(new EngineContext(0x52C0A57L, -64, 320, SEA_LEVEL))) {
            int[] coast = findCoastalCandidate(world);
            assertNotNull(coast, "Expected to find a physical coastline in the deterministic scan window");
            int centerChunkX = Math.floorDiv(coast[0], 16);
            int centerChunkZ = Math.floorDiv(coast[1], 16);
            int oceanColumns = 0;
            int coastColumns = 0;

            for (int chunkZ = centerChunkZ - 3; chunkZ <= centerChunkZ + 3; chunkZ++) {
                for (int chunkX = centerChunkX - 3; chunkX <= centerChunkX + 3; chunkX++) {
                    ChunkSnapshot snapshot = world.chunkSnapshot(chunkX, chunkZ);
                    for (int localZ = 0; localZ < 16; localZ++) {
                        for (int localX = 0; localX < 16; localX++) {
                            ColumnSnapshot column = snapshot.column(localX, localZ);
                            TerrainSample terrain = column.terrain();
                            if (StandardTerrainTypes.OCEAN.equals(terrain.terrainType())) {
                                oceanColumns++;
                                assertTrue(column.solidSurfaceY() < SEA_LEVEL,
                                        "Ocean floor must remain below sea level");
                                assertEquals(SEA_LEVEL + 1, column.waterTopExclusive(),
                                        "Ocean water column must end immediately above canonical sea level");
                            }
                            if (StandardTerrainTypes.COAST.equals(terrain.terrainType())) {
                                coastColumns++;
                                assertFalse(column.hasSurfaceWater(),
                                        "Dry COAST semantic must not be flooded by ocean materialization");
                            }
                            if (column.hasSurfaceWater()) {
                                for (int y = column.solidSurfaceY() + 1;
                                        y < column.waterTopExclusive(); y++) {
                                    assertEquals(NaturalMaterial.WATER, snapshot.materialAt(localX, y, localZ),
                                            "Natural surface-water column must be contiguous");
                                }
                            }
                        }
                    }
                }
            }

            assertTrue(oceanColumns > 0, "Sampled coast area must include ocean columns");
            assertTrue(coastColumns > 0, "Sampled coast area must include dry coast columns");
        }
    }

    private static int[] findCoastalCandidate(TerrainWorld world) {
        for (int z = -4096; z <= 4096; z += 96) {
            for (int x = -4096; x <= 4096; x += 96) {
                TerrainSample sample = world.placementSample(x, z);
                if (sample.continentalness() >= -0.36D
                        && sample.continentalness() <= 0.10D
                        && Math.abs(sample.surfaceHeight() - SEA_LEVEL) <= 10.0D) {
                    return new int[] {x, z};
                }
            }
        }
        return null;
    }

    private static void assertMarineSemanticMatchesHeight(TerrainSample sample) {
        if (StandardTerrainTypes.OCEAN.equals(sample.terrainType())) {
            assertTrue(sample.surfaceHeight() < SEA_LEVEL,
                    "OCEAN semantic must describe a submerged physical surface");
        }
        if (StandardTerrainTypes.COAST.equals(sample.terrainType())) {
            assertTrue(sample.surfaceHeight() >= SEA_LEVEL - 0.05D,
                    "COAST semantic must not describe a submerged shelf");
            assertTrue(sample.surfaceHeight() <= SEA_LEVEL + 1.25D,
                    "COAST semantic must stay in the narrow dry shoreline band");
        }
    }

    private static boolean isLowCoastalPair(TerrainSample first, TerrainSample second) {
        boolean nearSea = Math.abs(first.surfaceHeight() - SEA_LEVEL) <= 10.0D
                && Math.abs(second.surfaceHeight() - SEA_LEVEL) <= 10.0D;
        boolean notCliff = first.slope() < 2.75D && second.slope() < 2.75D;
        boolean marineTransition = StandardTerrainTypes.OCEAN.equals(first.terrainType())
                || StandardTerrainTypes.OCEAN.equals(second.terrainType())
                || StandardTerrainTypes.COAST.equals(first.terrainType())
                || StandardTerrainTypes.COAST.equals(second.terrainType());
        return nearSea && notCliff && marineTransition;
    }
}
