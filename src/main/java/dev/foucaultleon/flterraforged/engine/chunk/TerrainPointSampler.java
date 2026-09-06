package dev.foucaultleon.flterraforged.engine.chunk;

import dev.foucaultleon.flterraforged.engine.api.terrain.TerrainSample;

/**
 * Minimal internal bridge from complete chunk generation to the lower-level final terrain cache.
 *
 * <p>The single abstract point method keeps the interface lambda-compatible. R49 adds a default
 * aligned chunk bulk method that cache-aware implementations can override to avoid 256 repeated
 * cache lookups during complete snapshot generation.</p>
 */
@FunctionalInterface
public interface TerrainPointSampler {

    /**
     * Returns the canonical final terrain sample at a world position.
     *
     * @param x world X
     * @param z world Z
     * @return final terrain sample
     */
    TerrainSample sample(int x, int z);

    /**
     * Returns the 256 final samples for one chunk in local Z-major order.
     *
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     * @return array containing exactly 256 terrain samples
     */
    default TerrainSample[] sampleChunk(int chunkX, int chunkZ) {
        TerrainSample[] result = new TerrainSample[256];
        int originX = chunkX << 4;
        int originZ = chunkZ << 4;
        for (int localZ = 0; localZ < 16; localZ++) {
            for (int localX = 0; localX < 16; localX++) {
                result[localZ * 16 + localX] = sample(originX + localX, originZ + localZ);
            }
        }
        return result;
    }
}
